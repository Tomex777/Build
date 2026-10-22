package com.nice.juicewrld

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FanPlayerState(
    val favorites: Set<String> = emptySet(),
    val attached: Set<String> = emptySet(),
    val memories: Map<String, String> = emptyMap(),
    val importing: Boolean = false,
    val downloadingSongId: String? = null,
    val importMessage: String? = null,
    val queueIds: List<String> = emptyList(),
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val activeSongId: String? = null,
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
    val durationMs: Int = 0,
    val positionMs: Int = 0,
    val error: String? = null,
)

class FanPlayer(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("juice-fan", Context.MODE_PRIVATE)
    private val attrs = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .build()
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    // Restore interrupted tracks after temporary focus loss; never restart one
    // that the listener intentionally paused. Duck under navigation prompts.
    private var resumeOnFocusGainSongId: String? = null
    private val headphonesUnplugged = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener(::onAudioFocusChange)
        .build()
    private val files = mutableMapOf<String, String>()
    private var mediaPlayer: MediaPlayer? = null
    private var generation = 0
    private val _state = MutableStateFlow(FanPlayerState(
        favorites = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()
    ))
    val state: StateFlow<FanPlayerState> = _state

    init {
        // Stop music when wired/Bluetooth headphones disconnect: do not blast
        // the next track through the phone speaker unexpectedly.
        ContextCompat.registerReceiver(
            app, headphonesUnplugged,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        for (song in FanSongs.all) {
            val uri = prefs.getString("file:" + song.id, null)
            if (uri != null) files[song.id] = uri
        }
        val memories = mutableMapOf<String, String>()
        for (song in FanSongs.all) {
            prefs.getString("memory:" + song.id, null)?.let { text ->
                if (text.isNotBlank()) memories[song.id] = text
            }
        }
        _state.update { it.copy(attached = files.keys.toSet(), memories = memories) }
        viewModelScope.launch {
            while (isActive) {
                delay(500L)
                val player = mediaPlayer ?: continue
                try {
                    if (!_state.value.isPreparing) {
                        val position = player.currentPosition
                        val duration = player.duration
                        _state.update {
                            it.copy(
                                positionMs = position.coerceAtLeast(0),
                                durationMs = duration.coerceAtLeast(0),
                                isPlaying = player.isPlaying,
                            )
                        }
                    }
                } catch (_: IllegalStateException) { }
            }
        }
    }

    fun toggleFavorite(id: String) {
        if (FanSongs.find(id) == null) return
        val ids = _state.value.favorites.toMutableSet()
        if (!ids.add(id)) ids.remove(id)
        prefs.edit().putStringSet("favorites", ids).apply()
        _state.update { it.copy(favorites = ids.toSet()) }
    }

    fun importMusicFolder(treeUri: Uri) {
        if (_state.value.importing) return
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            _state.update { it.copy(importMessage = "Couldn't access that folder. Try another.") }
            return
        }
        _state.update { it.copy(importing = true, importMessage = null) }
        viewModelScope.launch {
            val matches = try {
                withContext(Dispatchers.IO) { scanMusicTree(treeUri) }
            } catch (_: Exception) {
                _state.update { it.copy(importing = false,
                    importMessage = "Couldn't read the selected folder.") }
                return@launch
            }
            val unassigned = matches.filterKeys { it !in files }
            if (unassigned.isNotEmpty()) {
                val edit = prefs.edit()
                unassigned.forEach { (id, uri) ->
                    files[id] = uri
                    edit.putString("file:" + id, uri)
                }
                edit.apply()
            }
            _state.update { it.copy(
                attached = files.keys.toSet(),
                importing = false,
                importMessage = when {
                    unassigned.isNotEmpty() ->
                        "Found " + unassigned.size + " songs you can play."
                    matches.isNotEmpty() -> "These songs are already in your library."
                    else -> "No matching tracks found in that folder."
                }
            ) }
        }
    }

    private fun scanMusicTree(tree: Uri): Map<String, String> {
        val resolver = getApplication<Application>().contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(rootId to 0)
        val results = linkedMapOf<String, String>()
        var scanned = 0
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        while (queue.isNotEmpty() && scanned < 1200) {
            val (parentId, depth) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            resolver.query(children, columns, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(columns[0])
                val nameCol = cursor.getColumnIndexOrThrow(columns[1])
                val mimeCol = cursor.getColumnIndexOrThrow(columns[2])
                while (cursor.moveToNext() && scanned < 1200) {
                    scanned++
                    val id = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol) ?: continue
                    val mime = cursor.getString(mimeCol).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < 3) queue.add(id to depth + 1)
                        continue
                    }
                    val song = SongFileMatcher.matchFilename(name) ?: continue
                    if (!mime.startsWith("audio/") &&
                        mime != "application/octet-stream") continue
                    results.putIfAbsent(
                        song.id, DocumentsContract.buildDocumentUriUsingTree(tree, id).toString()
                    )
                }
            }
        }
        return results
    }

    fun dismissImportMessage() {
        _state.update { it.copy(importMessage = null) }
    }

    fun saveMemory(songId: String, note: String) {
        if (FanSongs.find(songId) == null) return
        val text = note.trim().take(600)
        val next = _state.value.memories.toMutableMap()
        if (text.isBlank()) {
            prefs.edit().remove("memory:" + songId).apply()
            next.remove(songId)
        } else {
            prefs.edit().putString("memory:" + songId, text).apply()
            next[songId] = text
        }
        _state.update { it.copy(memories = next.toMap()) }
    }

    fun attachFile(songId: String, uri: Uri) {
        if (FanSongs.find(songId) == null) return
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            files[songId] = uri.toString()
            prefs.edit().putString("file:" + songId, uri.toString()).apply()
            // Replacing an already-opened track must recreate the MediaPlayer.
            // Otherwise play(songId) treats the same song ID as a play/pause
            // toggle and keeps decoding the *previous* file URI.
            if (_state.value.activeSongId == songId) {
                generation++
                releaseCurrentPlayer()
                _state.update { it.copy(
                    activeSongId = null, isPreparing = false, isPlaying = false,
                    durationMs = 0, positionMs = 0
                ) }
            }
            _state.update { it.copy(attached = files.keys.toSet(), error = null) }
            play(songId)
        } catch (_: Exception) {
            _state.update { it.copy(error = "Couldn't read that audio file. Choose another.") }
        }
    }

    /**
     * Offline downloads are enabled only for tracks explicitly licensed in the
     * bundled, SHA-256-pinned catalog. No URL scraping or stream ripping.
     */
    fun downloadLicensedSong(songId: String) {
        if (FanSongs.find(songId) == null || _state.value.downloadingSongId != null) return
        val entry = LicensedAudioCatalog.find(getApplication(), songId)
        if (entry == null) {
            _state.update { it.copy(error = "No authorized download is available for this song.") }
            return
        }
        _state.update { it.copy(downloadingSongId = songId, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                LicensedAudioCatalog.download(getApplication(), entry)
            }
            result.onSuccess { location ->
                files[songId] = Uri.fromFile(location).toString()
                prefs.edit().putString("file:" + songId, files.getValue(songId)).apply()
                _state.update { it.copy(attached = files.keys.toSet(), downloadingSongId = null) }
                play(songId)
            }.onFailure {
                _state.update { it.copy(
                    downloadingSongId = null,
                    error = "Download failed. Please try again when this recording is available."
                ) }
            }
        }
    }

    /** Playback queue always follows the selected feeling, not the full unrelated catalog. */
    fun playMood(mood: Mood) {
        val ids = FanSongs.filter(mood, "", _state.value.favorites)
            .map { it.id }.filter { it in files }
        if (ids.isEmpty()) {
            _state.update { it.copy(error = "Import music for this feeling first.") }
            return
        }
        val first = ids.first()
        val queue = MusicQueue.build(ids, first, _state.value.shuffle)
        _state.update { it.copy(queueIds = queue) }
        // A button labeled "Play mood" must never pause an already playing
        // track merely because the user selected the same feeling twice.
        if (first != _state.value.activeSongId || !_state.value.isPlaying) play(first)
    }

    fun playInMood(songId: String, mood: Mood) {
        val ids = FanSongs.filter(mood, "", _state.value.favorites)
            .map { it.id }.filter { it in files }
        if (songId !in ids) {
            play(songId)
            return
        }
        if (songId != _state.value.activeSongId ||
            !MusicQueue.hasSameSongs(_state.value.queueIds, ids)) {
            _state.update { it.copy(
                queueIds = MusicQueue.build(ids, songId, _state.value.shuffle)
            ) }
        }
        play(songId)
    }

    fun toggleShuffle() {
        val now = _state.value
        val enabled = !now.shuffle
        val current = now.activeSongId
        val queue = if (current != null && current in now.queueIds) {
            if (enabled) MusicQueue.build(now.queueIds, current, true)
            else MusicQueue.build(
                FanSongs.all.map { it.id }.filter { it in now.queueIds }, current, false
            )
        } else now.queueIds
        _state.update { it.copy(shuffle = enabled, queueIds = queue) }
    }

    fun toggleRepeat() {
        _state.update { it.copy(repeat = it.repeat.next()) }
    }

    fun play(songId: String) {
        if (FanSongs.find(songId) == null) return
        val uriString = files[songId]
        if (uriString == null) {
            _state.update { it.copy(error = "Select an audio file for this song first.") }
            return
        }
        if (songId == _state.value.activeSongId && _state.value.isPreparing) return
        val existing = mediaPlayer
        if (songId == _state.value.activeSongId && existing != null && !_state.value.isPreparing) {
            try {
                if (existing.isPlaying) {
                    existing.pause()
                    _state.update { it.copy(isPlaying = false) }
                } else {
                    if (!requestFocus()) return
                    if (existing.duration > 0 &&
                        existing.currentPosition >= existing.duration - 250) {
                        existing.seekTo(0)
                    }
                    existing.start()
                    _state.update { it.copy(isPlaying = true, error = null) }
                }
                return
            } catch (_: IllegalStateException) {}
        }

        if (_state.value.queueIds.isEmpty() || songId !in _state.value.queueIds) {
            val ids = FanSongs.all.map { it.id }.filter { it in files }
            _state.update { it.copy(queueIds = MusicQueue.build(ids, songId, it.shuffle)) }
        }
        val token = ++generation
        releaseCurrentPlayer()
        _state.update { it.copy(
            activeSongId = songId, isPreparing = true, isPlaying = false,
            positionMs = 0, durationMs = 0, error = null
        ) }
        try {
            val newPlayer = MediaPlayer()
            mediaPlayer = newPlayer
            newPlayer.setAudioAttributes(attrs)
            newPlayer.setDataSource(getApplication(), Uri.parse(uriString))
            newPlayer.setOnPreparedListener { prepared ->
                if (token != generation) return@setOnPreparedListener
                if (!requestFocus()) {
                    _state.update { it.copy(isPreparing = false, error = "Audio focus unavailable.") }
                    return@setOnPreparedListener
                }
                try {
                    prepared.start()
                    _state.update { it.copy(
                        isPreparing = false, isPlaying = true,
                        durationMs = prepared.duration.coerceAtLeast(0), error = null
                    ) }
                } catch (_: Exception) {
                    playbackError(token)
                }
            }
            newPlayer.setOnCompletionListener { completed ->
                if (token != generation) return@setOnCompletionListener
                val current = _state.value
                if (current.repeat == RepeatMode.ONE) {
                    try {
                        completed.seekTo(0)
                        completed.start()
                        _state.update { it.copy(positionMs = 0, isPlaying = true) }
                    } catch (_: Exception) {
                        playbackError(token)
                    }
                } else {
                    val next = MusicQueue.step(
                        current.queueIds, songId, 1, current.repeat, completedNaturally = true
                    )
                    if (next == null) {
                        audioManager.abandonAudioFocusRequest(focusRequest)
                        _state.update { it.copy(
                            isPlaying = false, positionMs = it.durationMs
                        ) }
                    } else {
                        play(next)
                    }
                }
            }
            newPlayer.setOnErrorListener { _, _, _ ->
                playbackError(token)
                true
            }
            newPlayer.prepareAsync()
        } catch (_: Exception) {
            playbackError(token)
        }
    }

    private fun requestFocus(): Boolean =
        audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    private fun onAudioFocusChange(focus: Int) {
        when (focus) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                try { mediaPlayer?.setVolume(1f, 1f) } catch (_: IllegalStateException) {}
                val toResume = resumeOnFocusGainSongId
                resumeOnFocusGainSongId = null
                if (toResume != null && _state.value.activeSongId == toResume &&
                    !_state.value.isPlaying && !_state.value.isPreparing) {
                    try {
                        mediaPlayer?.start()
                        _state.update { it.copy(isPlaying = mediaPlayer?.isPlaying == true) }
                    } catch (_: IllegalStateException) {}
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                try { mediaPlayer?.setVolume(0.25f, 0.25f) }
                catch (_: IllegalStateException) {}
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGainSongId = if (_state.value.isPlaying)
                    _state.value.activeSongId else null
                pausePlayer()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGainSongId = null
                pausePlayer()
            }
        }
    }

    private fun pausePlayer() {
        try {
            mediaPlayer?.let { if (it.isPlaying) it.pause() }
        } catch (_: IllegalStateException) {}
        _state.update { it.copy(isPlaying = false) }
    }

    fun pause() {
        resumeOnFocusGainSongId = null
        pausePlayer()
    }

    fun seekTo(fraction: Float) {
        val player = mediaPlayer ?: return
        if (_state.value.isPreparing || _state.value.durationMs <= 0) return
        val target = (_state.value.durationMs * fraction.coerceIn(0f, 1f)).toInt()
        try {
            player.seekTo(target)
            _state.update { it.copy(positionMs = target) }
        } catch (_: Exception) {}
    }

    fun skip(direction: Int) {
        val current = _state.value.activeSongId ?: return
        val playing = _state.value
        val destination = MusicQueue.step(
            playing.queueIds, current, direction, playing.repeat
        ) ?: return
        if (destination == current) {
            seekTo(0f)
            return
        }
        play(destination)
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun playbackError(token: Int) {
        if (token != generation) return
        releaseCurrentPlayer()
        _state.update { it.copy(isPreparing = false, isPlaying = false,
            error = "Unable to play this file. Select a supported audio recording.") }
    }

    private fun releaseCurrentPlayer() {
        resumeOnFocusGainSongId = null
        val old = mediaPlayer
        mediaPlayer = null
        try { old?.reset() } catch (_: Exception) {}
        // Release must run even if reset() fails, otherwise decoders/audio
        // resources are leaked when skipping or replacing corrupt recordings.
        try { old?.release() } catch (_: Exception) {}
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    override fun onCleared() {
        generation++
        releaseCurrentPlayer()
        try {
            getApplication<Application>().unregisterReceiver(headphonesUnplugged)
        } catch (_: IllegalArgumentException) {}
        super.onCleared()
    }
}
