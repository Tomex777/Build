package app.nami.android

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import app.nami.domain.ResolvedMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

internal data class VlcTrackOption(val id: Int, val name: String)

internal data class NamiVlcState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val bufferPercent: Float = 0f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val seekable: Boolean = false,
    val ended: Boolean = false,
    val error: String? = null,
    val videoOutputCount: Int = 0,
    val subtitleTracks: List<VlcTrackOption> = emptyList(),
    val audioTracks: List<VlcTrackOption> = emptyList(),
    val selectedSubtitleTrack: Int = -1,
    val selectedAudioTrack: Int = -1,
    val rate: Float = 1f,
)

internal class NamiVlcPlayer(context: Context) {
    private companion object {
        // libVLC media-slave ABI: subtitle=0, generic/audio=1.
        const val SLAVE_TYPE_SUBTITLE = 0
        const val SLAVE_TYPE_AUDIO = 1
    }

    private val libVlc = LibVLC(
        context.applicationContext,
        arrayListOf("--audio-time-stretch", "--network-caching=2500"),
    )
    private val player = MediaPlayer(libVlc)
    private val mutableState = MutableStateFlow(NamiVlcState())
    val state: StateFlow<NamiVlcState> = mutableState.asStateFlow()
    private var pendingSeekMs: Long? = null
    private var attachedSurface: SurfaceView? = null

    init {
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> mutableState.value = mutableState.value.copy(
                    isBuffering = true,
                    ended = false,
                    error = null,
                )
                MediaPlayer.Event.Buffering -> {
                    val percent = event.buffering.coerceIn(0f, 100f)
                    mutableState.value = mutableState.value.copy(
                        isBuffering = percent < 100f,
                        bufferPercent = percent,
                    )
                }
                MediaPlayer.Event.Playing -> {
                    pendingSeekMs?.takeIf { it > 0L }?.let(player::setTime)
                    pendingSeekMs = null
                    refreshTracks()
                    mutableState.value = mutableState.value.copy(
                        isPlaying = true,
                        isBuffering = false,
                        ended = false,
                        error = null,
                        seekable = player.isSeekable,
                    )
                }
                MediaPlayer.Event.Paused -> mutableState.value =
                    mutableState.value.copy(isPlaying = false)
                MediaPlayer.Event.Stopped -> mutableState.value = mutableState.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                )
                MediaPlayer.Event.EndReached -> mutableState.value = mutableState.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    ended = true,
                    positionMs = mutableState.value.durationMs,
                )
                MediaPlayer.Event.EncounteredError -> mutableState.value = mutableState.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    error = "VLC could not play this stream.",
                )
                MediaPlayer.Event.TimeChanged -> mutableState.value = mutableState.value.copy(
                    positionMs = event.timeChanged.coerceAtLeast(0L),
                )
                MediaPlayer.Event.LengthChanged -> mutableState.value = mutableState.value.copy(
                    durationMs = event.lengthChanged.coerceAtLeast(0L),
                )
                MediaPlayer.Event.SeekableChanged -> mutableState.value =
                    mutableState.value.copy(seekable = event.seekable)
                MediaPlayer.Event.Vout -> mutableState.value = mutableState.value.copy(
                    videoOutputCount = event.voutCount.coerceAtLeast(0),
                )
                MediaPlayer.Event.ESAdded,
                MediaPlayer.Event.ESDeleted,
                MediaPlayer.Event.ESSelected -> refreshTracks()
            }
        }
    }

    fun attach(surfaceView: SurfaceView) {
        if (attachedSurface === surfaceView && player.vlcVout.areViewsAttached()) return
        detach()
        attachedSurface = surfaceView
        player.vlcVout.setVideoView(surfaceView)
        player.vlcVout.attachViews()
    }

    fun detach() {
        if (player.vlcVout.areViewsAttached()) player.vlcVout.detachViews()
        attachedSurface = null
    }

    fun play(media: ResolvedMedia, startPositionMs: Long = 0L) {
        pendingSeekMs = startPositionMs.takeIf { it > 0L }
        mutableState.value = NamiVlcState(
            isBuffering = true,
            positionMs = startPositionMs.coerceAtLeast(0L),
            rate = mutableState.value.rate,
        )
        val vlcMedia = Media(libVlc, Uri.parse(media.url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=2500")
            media.headers.forEach { (rawName, rawValue) ->
                val value = rawValue.replace("\r", "").replace("\n", "")
                if (value.isBlank()) return@forEach
                when (rawName.lowercase()) {
                    "referer", "referrer" -> addOption(":http-referrer=$value")
                    "user-agent" -> addOption(":http-user-agent=$value")
                    "origin" -> addOption(":http-origin=$value")
                    "cookie" -> addOption(":http-cookie=$value")
                    else -> {
                        val name = rawName.replace("\r", "").replace("\n", "")
                        if (name.isNotBlank()) addOption(":http-header=$name: $value")
                    }
                }
            }
        }
        player.setMedia(vlcMedia)
        vlcMedia.release()
        player.play()
    }

    fun pause() { if (player.isPlaying) player.pause() }
    fun resume() { if (!player.isPlaying) player.play() }
    fun togglePlayPause() { if (player.isPlaying) pause() else resume() }

    fun seekTo(positionMs: Long) {
        if (!player.isSeekable) return
        val duration = state.value.durationMs
        val clamped = if (duration > 0L) {
            positionMs.coerceIn(0L, duration)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        player.setTime(clamped)
        mutableState.value = mutableState.value.copy(positionMs = clamped)
    }

    fun seekBy(deltaMs: Long) = seekTo(state.value.positionMs + deltaMs)

    fun setRate(rate: Float) {
        player.setRate(rate)
        mutableState.value = mutableState.value.copy(rate = rate)
    }

    fun selectSubtitleTrack(id: Int) {
        player.setSpuTrack(id)
        mutableState.value = mutableState.value.copy(selectedSubtitleTrack = id)
    }

    fun addExternalSubtitle(uri: String): Boolean {
        if (uri.isBlank()) return false
        val added = player.addSlave(SLAVE_TYPE_SUBTITLE, Uri.parse(uri), true)
        if (added) refreshTracks()
        return added
    }

    fun selectAudioTrack(id: Int) {
        player.setAudioTrack(id)
        mutableState.value = mutableState.value.copy(selectedAudioTrack = id)
    }

    fun addExternalAudio(uri: String): Boolean {
        if (uri.isBlank()) return false
        val added = player.addSlave(SLAVE_TYPE_AUDIO, Uri.parse(uri), true)
        if (added) refreshTracks()
        return added
    }

    fun release() {
        runCatching { player.setEventListener(null) }
        runCatching { player.stop() }
        runCatching { detach() }
        runCatching { player.release() }
        runCatching { libVlc.release() }
    }

    private fun refreshTracks() {
        val subtitles = runCatching {
            player.spuTracks?.map {
                VlcTrackOption(it.id, it.name ?: "Subtitle \${it.id}")
            }.orEmpty()
        }.getOrDefault(emptyList())
        val audio = runCatching {
            player.audioTracks?.map {
                VlcTrackOption(it.id, it.name ?: "Audio \${it.id}")
            }.orEmpty()
        }.getOrDefault(emptyList())
        mutableState.value = mutableState.value.copy(
            subtitleTracks = subtitles,
            audioTracks = audio,
            selectedSubtitleTrack = runCatching { player.spuTrack }.getOrDefault(-1),
            selectedAudioTrack = runCatching { player.audioTrack }.getOrDefault(-1),
        )
    }
}
