package com.night.spotui.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.ForwardingPlayer
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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.night.spotui.MainActivity
import com.night.spotui.MusicSource
import com.night.spotui.ResolvedAudio
import com.night.spotui.Track
import com.night.spotui.TasteStore
import com.night.spotui.ExtensionMusicSource
import com.night.spotui.source.api.MusicSourceCallException
import com.night.spotui.source.api.MusicSourceContract
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SpotRepeatMode { OFF, ALL, ONE }

class SpotPlaybackController(
    context: Context,
    private val source: MusicSource,
    private val taste: TasteStore,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val streamHeaders = ConcurrentHashMap<String, Map<String, String>>()
    @Volatile private var activeStreamHeaders: Map<String, String> = emptyMap()
    private val upstream = DefaultDataSource.Factory(
        appContext,
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000),
    )
    private val resolving = ResolvingDataSource.Factory(
        upstream,
        object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val headers = streamHeaders[dataSpec.uri.toString()].orEmpty()
                    .ifEmpty { activeStreamHeaders }
                return if (headers.isEmpty()) dataSpec else dataSpec.withAdditionalHeaders(headers)
            }
        },
    )
    private val mediaDataSource = ChunkedDataSource.Factory(resolving)
    private val player = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(mediaDataSource))
        .build()
    private var requestSerial = 0L
    private var baseQueue: List<Track> = emptyList()
    private var activeCandidates: List<ResolvedAudio> = emptyList()
    private var activeCandidateIndex = -1
    private var refreshingAfterPlayerError = false

    var currentTrack by mutableStateOf<Track?>(null)
        private set
    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(-1)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var shuffleEnabled by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(SpotRepeatMode.OFF)
        private set
    var streamLabel by mutableStateOf("")
        private set

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlaybackStateChanged(state: Int) {
                isLoading = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) durationMs = player.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED) handleEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                val track = currentTrack
                val nextIndex = activeCandidateIndex + 1
                if (track != null && nextIndex in activeCandidates.indices) {
                    activeCandidateIndex = nextIndex
                    errorMessage = null
                    isLoading = true
                    prepareStream(track, activeCandidates[nextIndex], requestSerial)
                    return
                }
                if (track != null && !refreshingAfterPlayerError && error.errorCode in 2000..2999) {
                    refreshingAfterPlayerError = true
                    resolveAndPlay(track, preserveRefreshGuard = true)
                    return
                }
                isLoading = false
                errorMessage = "Playback couldn’t start. Tap play to retry."
            }
        })
        scope.launch {
            while (isActive) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                durationMs = player.duration.coerceAtLeast(0L)
                delay(350)
            }
        }
    }

    val sourceName: String
        get() = source.name

    fun sessionPlayer(): Player = player

    fun play(track: Track, sourceQueue: List<Track>) {
        SpotPlaybackService.ensureStarted(appContext)
        currentTrack?.takeIf { it.id != track.id }?.let(taste::recordSkip)
        taste.recordPlay(track)
        baseQueue = sourceQueue.distinctBy(Track::id).let { list ->
            if (list.any { it.id == track.id }) list else listOf(track) + list
        }
        queue = if (shuffleEnabled) shuffledAround(track, baseQueue) else baseQueue
        currentIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        resolveAndPlay(track)
    }

    fun selectQueueIndex(index: Int) {
        if (index !in queue.indices || index == currentIndex) return
        currentTrack?.let(taste::recordSkip)
        currentIndex = index
        taste.recordPlay(queue[index])
        resolveAndPlay(queue[index])
    }

    fun togglePlayPause() {
        val track = currentTrack ?: return
        if (errorMessage != null || player.mediaItemCount == 0) {
            resolveAndPlay(track)
            return
        }
        if (player.isPlaying || player.playWhenReady) player.pause() else player.play()
    }

    fun retryCurrent() {
        currentTrack?.let(::resolveAndPlay)
    }

    fun skipNext() {
        if (queue.isEmpty()) return
        val next = when {
            currentIndex < queue.lastIndex -> currentIndex + 1
            repeatMode == SpotRepeatMode.ALL -> 0
            else -> -1
        }
        if (next >= 0) {
            currentTrack?.let(taste::recordSkip)
            currentIndex = next
            taste.recordPlay(queue[next])
            resolveAndPlay(queue[next])
        }
    }

    fun skipPrevious() {
        if (queue.isEmpty()) return
        if (player.currentPosition > 3_000) {
            player.seekTo(0)
            return
        }
        val previous = when {
            currentIndex > 0 -> currentIndex - 1
            repeatMode == SpotRepeatMode.ALL -> queue.lastIndex
            else -> 0
        }
        currentTrack?.let(taste::recordSkip)
        currentIndex = previous
        taste.recordPlay(queue[previous])
        resolveAndPlay(queue[previous])
    }

    fun toggleShuffle() {
        val track = currentTrack ?: return
        shuffleEnabled = !shuffleEnabled
        if (baseQueue.isEmpty()) baseQueue = queue
        queue = if (shuffleEnabled) shuffledAround(track, baseQueue) else baseQueue
        currentIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            SpotRepeatMode.OFF -> SpotRepeatMode.ALL
            SpotRepeatMode.ALL -> SpotRepeatMode.ONE
            SpotRepeatMode.ONE -> SpotRepeatMode.OFF
        }
    }

    fun seekToFraction(value: Float) {
        if (durationMs <= 0) return
        player.seekTo((durationMs * value.coerceIn(0f, 1f)).toLong())
    }

    private fun handleEnded() {
        currentTrack?.let(taste::recordCompleted)
        when (repeatMode) {
            SpotRepeatMode.ONE -> {
                currentTrack?.let(taste::recordPlay)
                player.seekTo(0)
                player.play()
            }
            SpotRepeatMode.ALL -> {
                if (queue.isNotEmpty()) {
                    currentIndex = if (currentIndex < queue.lastIndex) currentIndex + 1 else 0
                    taste.recordPlay(queue[currentIndex])
                    resolveAndPlay(queue[currentIndex])
                }
            }
            SpotRepeatMode.OFF -> {
                if (currentIndex < queue.lastIndex) {
                    currentIndex += 1
                    taste.recordPlay(queue[currentIndex])
                    resolveAndPlay(queue[currentIndex])
                }
            }
        }
    }

    private fun shuffledAround(track: Track, values: List<Track>): List<Track> =
        listOf(track) + values.filterNot { it.id == track.id }.shuffled()

    private fun resolveAndPlay(track: Track, preserveRefreshGuard: Boolean = false) {
        val serial = ++requestSerial
        player.stop()
        player.clearMediaItems()
        streamHeaders.clear()
        activeStreamHeaders = emptyMap()
        activeCandidates = emptyList()
        activeCandidateIndex = -1
        if (!preserveRefreshGuard) refreshingAfterPlayerError = false
        currentTrack = track
        isPlaying = false
        isLoading = true
        errorMessage = null
        streamLabel = ""
        positionMs = 0
        durationMs = 0

        scope.launch {
            source.resolveCandidates(track)
                .onSuccess { streams ->
                    if (serial != requestSerial) return@onSuccess
                    activeCandidates = streams
                    activeCandidateIndex = 0
                    prepareStream(track, streams.first(), serial)
                }
                .onFailure { failure ->
                    if (serial != requestSerial) return@onFailure
                    isLoading = false
                    errorMessage = if (
                        (failure as? MusicSourceCallException)?.errorCode ==
                        MusicSourceContract.ERROR_CODE_SESSION_REQUIRED
                    ) {
                        "This music source needs a browser session on this network."
                    } else {
                        "Playback couldn’t start. Tap play to retry."
                    }
                }
        }
    }

    private fun prepareStream(track: Track, stream: ResolvedAudio, serial: Long) {
        if (serial != requestSerial || currentTrack?.id != track.id) return
        player.stop()
        player.clearMediaItems()
        streamHeaders.clear()
        activeStreamHeaders = stream.headers
        if (stream.headers.isNotEmpty()) streamHeaders[stream.url] = stream.headers
        streamLabel = stream.label

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .apply { track.artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
        val item = MediaItem.Builder()
            .setUri(stream.url)
            .setMediaId(track.id)
            .setMediaMetadata(metadata)
            .apply { stream.mimeType?.let(::setMimeType) }
            .build()

        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }
}

object SpotRuntime {
    @Volatile private var sourceInstance: ExtensionMusicSource? = null
    @Volatile private var tasteInstance: TasteStore? = null
    @Volatile private var playerInstance: SpotPlaybackController? = null

    fun source(context: Context): ExtensionMusicSource {
        sourceInstance?.let { return it }
        return synchronized(this) {
            sourceInstance ?: ExtensionMusicSource(context.applicationContext).also { sourceInstance = it }
        }
    }

    fun taste(context: Context): TasteStore {
        tasteInstance?.let { return it }
        return synchronized(this) {
            tasteInstance ?: TasteStore(context.applicationContext).also { tasteInstance = it }
        }
    }

    fun player(context: Context): SpotPlaybackController {
        playerInstance?.let { return it }
        return synchronized(this) {
            playerInstance ?: SpotPlaybackController(
                context.applicationContext,
                source(context),
                taste(context),
            ).also {
                playerInstance = it
            }
        }
    }
}

class SpotPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val playback = SpotRuntime.player(this)
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val sessionPlayer = object : ForwardingPlayer(playback.sessionPlayer()) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()

            override fun isCommandAvailable(command: Int): Boolean = when (command) {
                COMMAND_SEEK_TO_NEXT,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS,
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                else -> super.isCommandAvailable(command)
            }

            override fun hasNextMediaItem(): Boolean = playback.queue.size > 1
            override fun hasPreviousMediaItem(): Boolean = playback.queue.size > 1
            override fun seekToNext() = playback.skipNext()
            override fun seekToNextMediaItem() = playback.skipNext()
            override fun seekToPrevious() = playback.skipPrevious()
            override fun seekToPreviousMediaItem() = playback.skipPrevious()
        }

        session = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        fun ensureStarted(context: Context) {
            context.applicationContext.startService(
                Intent(context.applicationContext, SpotPlaybackService::class.java)
            )
        }
    }
}
