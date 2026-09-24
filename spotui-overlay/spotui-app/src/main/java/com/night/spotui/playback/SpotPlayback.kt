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
import com.night.spotui.Track
import com.night.spotui.YouTubeMusicSource
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SpotPlaybackController(
    context: Context,
    private val source: MusicSource,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val streamHeaders = ConcurrentHashMap<String, Map<String, String>>()
    private val upstream = DefaultDataSource.Factory(appContext, DefaultHttpDataSource.Factory())
    private val resolving = ResolvingDataSource.Factory(
        upstream,
        object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val headers = streamHeaders[dataSpec.uri.toString()].orEmpty()
                return if (headers.isEmpty()) dataSpec else dataSpec.withAdditionalHeaders(headers)
            }
        },
    )
    private val player = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
        .build()
    private var requestSerial = 0L

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
                if (state == Player.STATE_ENDED) skipNext()
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
                delay(350)
            }
        }
    }

    fun sessionPlayer(): Player = player

    fun play(track: Track, sourceQueue: List<Track>) {
        SpotPlaybackService.ensureStarted(appContext)
        queue = sourceQueue.distinctBy(Track::id).let { list ->
            if (list.any { it.id == track.id }) list else listOf(track) + list
        }
        currentIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        resolveAndPlay(track)
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
        val next = if (currentIndex < queue.lastIndex) currentIndex + 1 else 0
        currentIndex = next
        resolveAndPlay(queue[next])
    }

    fun skipPrevious() {
        if (queue.isEmpty()) return
        if (player.currentPosition > 3_000) {
            player.seekTo(0)
            return
        }
        val previous = if (currentIndex > 0) currentIndex - 1 else queue.lastIndex
        currentIndex = previous
        resolveAndPlay(queue[previous])
    }

    fun seekToFraction(value: Float) {
        if (durationMs <= 0) return
        player.seekTo((durationMs * value.coerceIn(0f, 1f)).toLong())
    }

    private fun resolveAndPlay(track: Track) {
        currentTrack = track
        isLoading = true
        errorMessage = null
        positionMs = 0
        durationMs = 0
        val serial = ++requestSerial
        scope.launch {
            source.resolve(track)
                .onSuccess { stream ->
                    if (serial != requestSerial) return@onSuccess
                    streamHeaders.clear()
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
                .onFailure { error ->
                    if (serial != requestSerial) return@onFailure
                    isLoading = false
                    errorMessage = error.message ?: "Could not play this track"
                }
        }
    }
}

object SpotRuntime {
    @Volatile private var sourceInstance: YouTubeMusicSource? = null
    @Volatile private var playerInstance: SpotPlaybackController? = null

    fun source(context: Context): YouTubeMusicSource {
        sourceInstance?.let { return it }
        return synchronized(this) {
            sourceInstance ?: YouTubeMusicSource(context.applicationContext).also { sourceInstance = it }
        }
    }

    fun player(context: Context): SpotPlaybackController {
        playerInstance?.let { return it }
        return synchronized(this) {
            playerInstance ?: SpotPlaybackController(context.applicationContext, source(context)).also {
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
        session = MediaSession.Builder(this, playback.sessionPlayer())
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
