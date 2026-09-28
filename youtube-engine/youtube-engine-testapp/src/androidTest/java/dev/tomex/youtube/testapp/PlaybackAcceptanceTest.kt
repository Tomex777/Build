package dev.tomex.youtube.testapp

import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.tomex.youtube.api.AdaptivePlaybackSelection
import dev.tomex.youtube.api.MediaFormat
import dev.tomex.youtube.api.PlaybackDescriptor
import dev.tomex.youtube.core.NativeYouTubeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Live Android playback gate.
 *
 * HTTP byte probes remain necessary, but they are not sufficient: this test hands the exact
 * engine-produced URLs and required headers to Media3, requires decoded video to reach a real
 * Surface, requires a selected/initialized audio format while playback time advances, and seeks
 * well outside the initial startup window.
 *
 * The 4K transport proof lives in [RealTransportTest]. This gate deliberately uses decoder-friendly
 * live tracks (muxed AVC/AAC when available, plus a <=720p AVC/AAC adaptive pair) so an emulator's
 * high-end codec limits do not get confused with YouTube transport correctness.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun liveMuxedAndAdaptiveMediaAreConsumableByAndroidPlayer() = runBlocking {
        val engine = NativeYouTubeEngine()
        val videoId = "dQw4w9WgXcQ"
        val descriptor = engine.resolve(videoId)

        val muxed = selectMuxedForPlayer(descriptor)
            ?: throw AssertionError("Live descriptor exposed no transport-ready muxed stream")
        val muxedProof = engine.probe(muxed)
        assertTrue("Muxed stream did not return real media bytes before playback", muxedProof.bytesRead >= 512)
        playAndSeek(
            label = "muxed",
            mediaSource = progressiveSource(muxed),
            expectedVideoItag = muxed.itag,
            expectedAudioItag = muxed.itag
        )

        val adaptive = selectAdaptiveForPlayer(descriptor)
            ?: throw AssertionError("Live descriptor exposed no decoder-friendly adaptive video/audio pair")
        val videoProof = engine.probe(adaptive.video)
        val audioProof = engine.probe(adaptive.audio)
        assertTrue("Adaptive video did not return real media bytes before playback", videoProof.bytesRead >= 512)
        assertTrue("Adaptive audio did not return real media bytes before playback", audioProof.bytesRead >= 512)

        println(
            "YT_PROOF player-adaptive-selection videoItag=${adaptive.video.itag} " +
                "height=${adaptive.video.height} videoCodec=${adaptive.video.codecs} " +
                "audioItag=${adaptive.audio.itag} audioCodec=${adaptive.audio.codecs}"
        )
        playAndSeekWithOneNoErrorBufferingRetry(
            label = "adaptive",
            mediaSourceFactory = {
                MergingMediaSource(
                    progressiveSource(adaptive.video),
                    progressiveSource(adaptive.audio)
                )
            },
            expectedVideoItag = adaptive.video.itag,
            expectedAudioItag = adaptive.audio.itag
        )
    }

    private fun selectMuxedForPlayer(descriptor: PlaybackDescriptor): MediaFormat? {
        val ready = descriptor.progressive.filter { it.transportReady }
        return ready
            .filter {
                it.container.equals("mp4", ignoreCase = true) &&
                    it.codecs.orEmpty().contains("avc1", ignoreCase = true) &&
                    it.codecs.orEmpty().contains("mp4a", ignoreCase = true)
            }
            .maxWithOrNull(compareBy<MediaFormat> { it.height ?: 0 }.thenBy { it.bitrate ?: 0 })
            ?: ready.maxWithOrNull(compareBy<MediaFormat> { it.height ?: 0 }.thenBy { it.bitrate ?: 0 })
    }

    private fun selectAdaptiveForPlayer(descriptor: PlaybackDescriptor): AdaptivePlaybackSelection? {
        val readyVideos = descriptor.videoOnly.filter { it.transportReady }
        val video = readyVideos
            .filter {
                it.container.equals("mp4", ignoreCase = true) &&
                    it.codecs.orEmpty().contains("avc1", ignoreCase = true) &&
                    (it.height ?: 0) in 360..720
            }
            .maxWithOrNull(compareBy<MediaFormat> { it.height ?: 0 }.thenBy { it.bitrate ?: 0 })
            ?: readyVideos
                .filter {
                    it.container.equals("mp4", ignoreCase = true) &&
                        it.codecs.orEmpty().contains("avc1", ignoreCase = true) &&
                        (it.height ?: 0) in 360..1080
                }
                .maxWithOrNull(compareBy<MediaFormat> { it.height ?: 0 }.thenBy { it.bitrate ?: 0 })
            ?: readyVideos
                .filter { (it.height ?: 0) in 360..720 }
                .maxWithOrNull(compareBy<MediaFormat> { it.height ?: 0 }.thenBy { it.bitrate ?: 0 })
            ?: return null

        val readyAudio = descriptor.audioOnly.filter { it.transportReady }
        val sameContainer = readyAudio.filter {
            it.container.equals(video.container, ignoreCase = true)
        }
        val audio = sameContainer
            .filter {
                !video.container.equals("mp4", ignoreCase = true) ||
                    it.codecs.orEmpty().contains("mp4a", ignoreCase = true)
            }
            .maxByOrNull { it.bitrate ?: 0 }
            ?: sameContainer.maxByOrNull { it.bitrate ?: 0 }
            ?: readyAudio.maxByOrNull { it.bitrate ?: 0 }
            ?: return null

        return AdaptivePlaybackSelection(video, audio)
    }

    private fun progressiveSource(format: MediaFormat): MediaSource {
        val dataSource = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(12_000)
            .setDefaultRequestProperties(format.requiredHeaders)
        return ProgressiveMediaSource.Factory(dataSource)
            .createMediaSource(MediaItem.fromUri(format.url))
    }

    private fun playAndSeekWithOneNoErrorBufferingRetry(
        label: String,
        mediaSourceFactory: () -> MediaSource,
        expectedVideoItag: Int,
        expectedAudioItag: Int
    ) {
        try {
            playAndSeek(label, mediaSourceFactory(), expectedVideoItag, expectedAudioItag)
        } catch (failure: AssertionError) {
            val message = failure.message.orEmpty()
            val noErrorBufferingStall =
                "playbackState=2" in message &&
                    "positionMs=0" in message &&
                    "error=null" in message
            if (!noErrorBufferingStall) throw failure
            println("YT_PROOF android-player=$label retry=no-error-buffering-stall")
            Thread.sleep(1_500)
            playAndSeek(label, mediaSourceFactory(), expectedVideoItag, expectedAudioItag)
        }
    }

    private fun playAndSeek(
        label: String,
        mediaSource: MediaSource,
        expectedVideoItag: Int,
        expectedAudioItag: Int
    ) {
        val renderedFrames = AtomicInteger(0)
        val firstFrame = AtomicInteger(0)
        val reader = ImageReader.newInstance(640, 360, ImageFormat.PRIVATE, 4)
        reader.setOnImageAvailableListener({ imageReader ->
            runCatching { imageReader.acquireLatestImage() }.getOrNull()?.use {
                renderedFrames.incrementAndGet()
            }
        }, Handler(Looper.getMainLooper()))

        val player = onMain {
            ExoPlayer.Builder(context).build().also { exo ->
                exo.volume = 0f
                exo.setVideoSurface(reader.surface)
                exo.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        firstFrame.incrementAndGet()
                    }
                })
                exo.setMediaSource(mediaSource)
                exo.prepare()
                exo.playWhenReady = true
            }
        }

        try {
            val started = waitUntil(30_000) {
                val state = snapshot(player)
                state.error == null &&
                    state.playbackState == Player.STATE_READY &&
                    state.isPlaying &&
                    state.positionMs >= 1_000 &&
                    state.selectedVideo &&
                    state.selectedAudio &&
                    state.videoMime != null &&
                    state.audioMime != null &&
                    (firstFrame.get() > 0 || renderedFrames.get() > 0)
            }
            val beforeSeek = snapshot(player)
            if (!started) {
                fail("Android player did not establish decoded video+audio for $label: $beforeSeek")
            }
            assertTrue(
                "Expected a real audio session for $label playback",
                beforeSeek.audioSessionId != C.AUDIO_SESSION_ID_UNSET
            )
            assertTrue(
                "Expected live media to be long enough for a remote seek in $label playback",
                beforeSeek.durationMs == C.TIME_UNSET || beforeSeek.durationMs > 45_000
            )

            val frameCountBeforeSeek = renderedFrames.get()
            val seekTargetMs = 30_000L
            onMain {
                player.seekTo(seekTargetMs)
                Unit
            }
            val seeked = waitUntil(30_000) {
                val state = snapshot(player)
                state.error == null &&
                    state.playbackState == Player.STATE_READY &&
                    state.positionMs >= seekTargetMs - 1_500 &&
                    state.isPlaying &&
                    renderedFrames.get() > frameCountBeforeSeek
            }
            val afterSeek = snapshot(player)
            if (!seeked) {
                fail("Android player did not resume rendered playback after seek for $label: $afterSeek")
            }

            println(
                "YT_PROOF android-player=$label SUPPORTED_AND_PROVEN " +
                    "videoItag=$expectedVideoItag audioItag=$expectedAudioItag " +
                    "videoMime=${afterSeek.videoMime} audioMime=${afterSeek.audioMime} " +
                    "audioSession=${afterSeek.audioSessionId} frames=${renderedFrames.get()} " +
                    "positionBeforeSeek=${beforeSeek.positionMs} positionAfterSeek=${afterSeek.positionMs}"
            )
        } finally {
            onMain {
                player.stop()
                player.clearVideoSurface()
                player.release()
                Unit
            }
            reader.close()
        }
    }

    private data class PlaybackSnapshot(
        val playbackState: Int,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val selectedVideo: Boolean,
        val selectedAudio: Boolean,
        val videoMime: String?,
        val audioMime: String?,
        val audioSessionId: Int,
        val error: String?
    )

    private fun snapshot(player: ExoPlayer): PlaybackSnapshot = onMain {
        val tracks = player.currentTracks
        PlaybackSnapshot(
            playbackState = player.playbackState,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition,
            durationMs = player.duration,
            selectedVideo = tracks.groups.any { group ->
                group.type == C.TRACK_TYPE_VIDEO &&
                    (0 until group.length).any { group.isTrackSelected(it) }
            },
            selectedAudio = tracks.groups.any { group ->
                group.type == C.TRACK_TYPE_AUDIO &&
                    (0 until group.length).any { group.isTrackSelected(it) }
            },
            videoMime = player.videoFormat?.sampleMimeType,
            audioMime = player.audioFormat?.sampleMimeType,
            audioSessionId = player.audioSessionId,
            error = player.playerError?.let { "${it.errorCodeName}: ${it.message}" }
        )
    }

    private fun waitUntil(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return true
            Thread.sleep(100)
        }
        return predicate()
    }

    private fun <T : Any> onMain(block: () -> T): T {
        val result = AtomicReference<T>()
        val failure = AtomicReference<Throwable>()
        instrumentation.runOnMainSync {
            try {
                result.set(block())
            } catch (t: Throwable) {
                failure.set(t)
            }
        }
        failure.get()?.let { throw it }
        return result.get() ?: throw IllegalStateException("Main-thread operation returned no result")
    }
}
