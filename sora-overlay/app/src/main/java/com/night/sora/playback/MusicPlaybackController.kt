package com.night.sora.playback

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.night.sora.extension.ExtensionManager
import com.night.sora.extension.InstalledExtension
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.model.PlaybackStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class MusicRepeatMode { OFF, ALL, ONE }

class MusicPlaybackController(
    context: Context,
    private val manager: ExtensionManager,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val streamHeaders = ConcurrentHashMap<String, Map<String, String>>()
    private val upstreamDataSourceFactory = DefaultDataSource.Factory(
        appContext,
        DefaultHttpDataSource.Factory(),
    )
    private val resolvingDataSourceFactory = ResolvingDataSource.Factory(
        upstreamDataSourceFactory,
        object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val headers = streamHeaders[dataSpec.uri.toString()].orEmpty()
                return if (headers.isEmpty()) dataSpec else dataSpec.withAdditionalHeaders(headers)
            }
        },
    )
    private val player = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingDataSourceFactory))
        .build()
    private var requestSerial = 0L
    private var baseQueue: List<ExtensionMediaSelection> = emptyList()
    private var extensions: List<InstalledExtension> = emptyList()

    var currentTrack by mutableStateOf<ExtensionMediaSelection?>(null)
        private set
    var queue by mutableStateOf<List<ExtensionMediaSelection>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(-1)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var shuffleEnabled by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(MusicRepeatMode.OFF)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var sourceName by mutableStateOf("")
        private set
    var streamLabel by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var lyricsText by mutableStateOf<String?>(null)
        private set
    var lyricsLoading by mutableStateOf(false)
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
                if (playbackState == Player.STATE_ENDED) handleEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                isLoading = false
                errorMessage = error.message ?: "Playback failed"
            }
        })
        scope.launch {
            while (isActive) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.coerceAtLeast(0L)
                delay(350L)
            }
        }
    }

    fun updateExtensions(value: List<InstalledExtension>) {
        extensions = value
    }

    fun sessionPlayer(): Player = player

    fun play(
        track: ExtensionMediaSelection,
        sourceQueue: List<ExtensionMediaSelection>,
        availableExtensions: List<InstalledExtension> = extensions,
    ) {
        MusicPlaybackService.ensureStarted(appContext)
        updateExtensions(availableExtensions)
        baseQueue = normalizeQueue(sourceQueue, track)
        queue = if (shuffleEnabled) shuffledAround(track, baseQueue) else baseQueue
        val index = queue.indexOfFirst { it.sameTrack(track) }.takeIf { it >= 0 } ?: 0
        playQueueIndex(index)
    }

    fun playQueueIndex(index: Int) {
        if (index !in queue.indices) return
        currentIndex = index
        val track = queue[index]
        currentTrack = track
        positionMs = 0L
        durationMs = 0L
        errorMessage = null
        lyricsText = null
        resolveAndPlay(track)
    }

    fun togglePlayPause() {
        if (currentTrack == null) return
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
        if (player.isPlaying || player.playWhenReady) player.pause() else player.play()
    }

    fun skipNext() {
        if (queue.isEmpty()) return
        val next = when {
            currentIndex < queue.lastIndex -> currentIndex + 1
            repeatMode == MusicRepeatMode.ALL -> 0
            else -> -1
        }
        if (next >= 0) playQueueIndex(next)
    }

    fun skipPrevious() {
        if (queue.isEmpty()) return
        if (player.currentPosition > 3_000L) {
            player.seekTo(0L)
            return
        }
        val previous = when {
            currentIndex > 0 -> currentIndex - 1
            repeatMode == MusicRepeatMode.ALL -> queue.lastIndex
            else -> 0
        }
        playQueueIndex(previous)
    }

    fun seekToFraction(fraction: Float) {
        if (durationMs <= 0L) return
        player.seekTo((durationMs * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun seekTo(position: Long) {
        player.seekTo(position.coerceIn(0L, durationMs.coerceAtLeast(0L)))
    }

    fun toggleShuffle() {
        val track = currentTrack
        shuffleEnabled = !shuffleEnabled
        if (baseQueue.isEmpty()) baseQueue = queue
        queue = if (shuffleEnabled && track != null) {
            shuffledAround(track, baseQueue)
        } else {
            baseQueue
        }
        currentIndex = track?.let { current -> queue.indexOfFirst { it.sameTrack(current) } } ?: -1
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            MusicRepeatMode.OFF -> MusicRepeatMode.ALL
            MusicRepeatMode.ALL -> MusicRepeatMode.ONE
            MusicRepeatMode.ONE -> MusicRepeatMode.OFF
        }
    }

    fun release() {
        requestSerial++
        scope.cancel()
        player.release()
    }

    private fun resolveAndPlay(track: ExtensionMediaSelection) {
        val extension = extensions.firstOrNull { it.packageName == track.extensionPackage }
        if (extension == null) {
            isLoading = false
            errorMessage = "The music source that supplied this track is no longer installed."
            return
        }
        sourceName = extension.descriptor?.sources?.firstOrNull { it.id == track.sourceId }?.name
            ?: extension.declaredName
        isLoading = true
        val requestId = ++requestSerial
        val payload = JSONObject().put("sourceId", track.sourceId).put("id", track.id).toString()
        manager.call(extension, ExtensionContract.Method.STREAMS, payload) { result ->
            scope.launch {
                if (requestId != requestSerial) return@launch
                val streams = result.getOrNull()?.let(::parseStreams).orEmpty()
                val stream = streams.firstOrNull()
                if (stream == null) {
                    isLoading = false
                    errorMessage = result.exceptionOrNull()?.message
                        ?: "${sourceName.ifBlank { "This source" }} returned no playable audio stream."
                    return@launch
                }
                streamLabel = stream.label
                streamHeaders.clear()
                if (stream.headers.isNotEmpty()) {
                    streamHeaders[stream.url] = stream.headers
                }
                val metadata = MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artistName())
                    .apply { track.artworkUrl?.takeIf(String::isNotBlank)?.let { setArtworkUri(Uri.parse(it)) } }
                    .build()
                val itemBuilder = MediaItem.Builder()
                    .setUri(stream.url)
                    .setMediaId(track.identityKey())
                    .setMediaMetadata(metadata)
                stream.mimeType?.let(itemBuilder::setMimeType)
                player.setMediaItem(itemBuilder.build())
                player.prepare()
                player.playWhenReady = true
                loadLyrics(extension, track, requestId)
            }
        }
    }

    private fun loadLyrics(extension: InstalledExtension, track: ExtensionMediaSelection, requestId: Long) {
        val supportsLyrics = extension.descriptor?.sources
            ?.firstOrNull { it.id == track.sourceId }
            ?.capabilities
            ?.contains("lyrics") == true
        if (!supportsLyrics) {
            lyricsText = null
            lyricsLoading = false
            return
        }
        lyricsLoading = true
        manager.call(
            extension,
            ExtensionContract.Method.LYRICS,
            JSONObject().put("sourceId", track.sourceId).put("id", track.id).toString(),
        ) { result ->
            scope.launch {
                if (requestId != requestSerial) return@launch
                lyricsLoading = false
                lyricsText = result.getOrNull()?.let(::parseLyrics)?.takeIf(String::isNotBlank)
            }
        }
    }

    private fun handleEnded() {
        when (repeatMode) {
            MusicRepeatMode.ONE -> {
                player.seekTo(0L)
                player.play()
            }
            MusicRepeatMode.ALL -> {
                if (queue.isNotEmpty()) playQueueIndex(if (currentIndex < queue.lastIndex) currentIndex + 1 else 0)
            }
            MusicRepeatMode.OFF -> {
                if (currentIndex < queue.lastIndex) playQueueIndex(currentIndex + 1)
            }
        }
    }

    private fun parseStreams(raw: String): List<PlaybackStream> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = item.optString("url")
                if (!url.startsWith("http://") && !url.startsWith("https://")) continue
                val headers = buildMap {
                    val objectHeaders = item.optJSONObject("headers")
                    if (objectHeaders != null) {
                        val keys = objectHeaders.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            objectHeaders.optString(key).takeIf(String::isNotBlank)?.let { put(key, it) }
                        }
                    }
                }
                add(
                    PlaybackStream(
                        label = item.optString("label", "Audio"),
                        url = url,
                        headers = headers,
                        mimeType = item.optString("mimeType").takeIf(String::isNotBlank),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun parseLyrics(raw: String): String = runCatching {
        val obj = JSONObject(raw)
        obj.optString("text").takeIf(String::isNotBlank) ?: buildString {
            val lines = obj.optJSONArray("lines") ?: JSONArray()
            for (i in 0 until lines.length()) {
                val line = lines.optJSONObject(i)?.optString("text") ?: lines.optString(i)
                if (line.isNotBlank()) appendLine(line)
            }
        }.trim()
    }.getOrElse { raw }

    private fun normalizeQueue(
        sourceQueue: List<ExtensionMediaSelection>,
        track: ExtensionMediaSelection,
    ): List<ExtensionMediaSelection> {
        val clean = sourceQueue.filter { it.type == track.type }.distinctBy { it.identityKey() }
        return if (clean.any { it.sameTrack(track) }) clean else listOf(track) + clean
    }

    private fun shuffledAround(
        track: ExtensionMediaSelection,
        values: List<ExtensionMediaSelection>,
    ): List<ExtensionMediaSelection> = listOf(track) + values.filterNot { it.sameTrack(track) }.shuffled()

    private fun ExtensionMediaSelection.sameTrack(other: ExtensionMediaSelection): Boolean = identityKey() == other.identityKey()
    private fun ExtensionMediaSelection.identityKey(): String = "$extensionPackage|$sourceId|$id"
    private fun ExtensionMediaSelection.artistName(): String = subtitle.substringBefore(" · ").ifBlank { title }
}
