package app.mira.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.TextureView
import app.mira.domain.ResolvedMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

data class MiraVlcState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val ended: Boolean = false,
    val error: String? = null,
)

class MiraVlcPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val libVlc = LibVLC(
        appContext,
        arrayListOf("--audio-time-stretch", "--network-caching=2500"),
    )
    private val player = MediaPlayer(libVlc)
    private val mutableState = MutableStateFlow(MiraVlcState())
    val state: StateFlow<MiraVlcState> = mutableState.asStateFlow()
    private var descriptor: ParcelFileDescriptor? = null
    private var pendingSeekMs: Long? = null

    init {
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> mutableState.value =
                    mutableState.value.copy(isBuffering = true, ended = false, error = null)
                MediaPlayer.Event.Buffering -> mutableState.value =
                    mutableState.value.copy(isBuffering = event.buffering < 100f)
                MediaPlayer.Event.Playing -> {
                    pendingSeekMs?.takeIf { it > 0L }?.let(player::setTime)
                    pendingSeekMs = null
                    mutableState.value =
                        mutableState.value.copy(
                            isPlaying = true,
                            isBuffering = false,
                            ended = false,
                            error = null,
                        )
                }
                MediaPlayer.Event.Paused -> mutableState.value =
                    mutableState.value.copy(isPlaying = false)
                MediaPlayer.Event.TimeChanged -> mutableState.value =
                    mutableState.value.copy(positionMs = event.timeChanged.coerceAtLeast(0L))
                MediaPlayer.Event.LengthChanged -> mutableState.value =
                    mutableState.value.copy(durationMs = event.lengthChanged.coerceAtLeast(0L))
                MediaPlayer.Event.EndReached -> mutableState.value =
                    mutableState.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        ended = true,
                        positionMs = mutableState.value.durationMs,
                    )
                MediaPlayer.Event.EncounteredError -> mutableState.value =
                    mutableState.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        error = "VLC could not play this stream.",
                    )
            }
        }
    }

    fun attach(view: TextureView) {
        if (player.vlcVout.areViewsAttached()) player.vlcVout.detachViews()
        player.vlcVout.setVideoView(view)
        player.vlcVout.attachViews()
    }

    fun detach() {
        if (player.vlcVout.areViewsAttached()) player.vlcVout.detachViews()
    }

    fun play(
        media: ResolvedMedia,
        startPositionMs: Long = 0L,
    ) {
        pendingSeekMs = startPositionMs.takeIf { it > 0L }
        mutableState.value = MiraVlcState(
            isBuffering = true,
            positionMs = startPositionMs.coerceAtLeast(0L),
        )
        closeDescriptor()
        val uri = Uri.parse(media.url)
        val vlcMedia = if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val opened = appContext.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Mira could not open the downloaded file.")
            descriptor = opened
            Media(libVlc, opened.fileDescriptor)
        } else {
            Media(libVlc, uri)
        }.apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=2500")
            media.headers.forEach { (name, value) ->
                if (name.equals("User-Agent", true)) {
                    addOption(":http-user-agent=$value")
                } else if (name.equals("Referer", true)) {
                    addOption(":http-referrer=$value")
                }
            }
        }
        player.setMedia(vlcMedia)
        vlcMedia.release()
        player.play()
    }

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.setTime(positionMs.coerceAtLeast(0L))
    }

    fun release() {
        runCatching { player.stop() }
        runCatching { detach() }
        runCatching { player.release() }
        closeDescriptor()
        runCatching { libVlc.release() }
    }

    private fun closeDescriptor() {
        runCatching { descriptor?.close() }
        descriptor = null
    }
}
