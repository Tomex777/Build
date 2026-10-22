package com.nice.juicewrld

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
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
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener { focus ->
            if (focus <= 0) pause()
        }
        .build()
    private val files = mutableMapOf<String, String>()
    private var mediaPlayer: MediaPlayer? = null
    private var generation = 0
    private val _state = MutableStateFlow(FanPlayerState(
        favorites = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()
    ))
    val state: StateFlow<FanPlayerState> = _state

    init {
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
            _state.update { it.copy(attached = files.keys.toSet(), error = null) }
            play(songId)
        } catch (_: Exception) {
            _state.update { it.copy(error = "Couldn't read that audio file. Choose another.") }
        }
    }

    fun play(songId: String) {
        if (FanSongs.find(songId) == null) return
        val uriString = files[songId]
        if (uriString == null) {
            _state.update { it.copy(error = "Select an audio file for this song first.") }
            return
        }
        val existing = mediaPlayer
        if (songId == _state.value.activeSongId && existing != null && !_state.value.isPreparing) {
            try {
                if (existing.isPlaying) {
                    existing.pause()
                    _state.update { it.copy(isPlaying = false) }
                } else {
                    if (!requestFocus()) return
                    existing.start()
                    _state.update { it.copy(isPlaying = true, error = null) }
                }
                return
            } catch (_: IllegalStateException) {}
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
            newPlayer.setOnCompletionListener {
                if (token == generation) _state.update { it.copy(
                    isPlaying = false, positionMs = it.durationMs
                ) }
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

    fun pause() {
        try {
            mediaPlayer?.let { if (it.isPlaying) it.pause() }
            _state.update { it.copy(isPlaying = false) }
        } catch (_: IllegalStateException) {}
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
        val songs = FanSongs.all.filter { it.id in files }
        val i = songs.indexOfFirst { it.id == current }
        if (i < 0 || songs.isEmpty()) return
        play(songs[(i + direction + songs.size) % songs.size].id)
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
        val old = mediaPlayer
        mediaPlayer = null
        try { old?.reset(); old?.release() } catch (_: Exception) {}
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    override fun onCleared() {
        generation++
        releaseCurrentPlayer()
        super.onCleared()
    }
}
