package dev.tomex.youtube.testapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomex.youtube.api.ResolutionState
import dev.tomex.youtube.core.YouTubeEngineFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real host-integration proof for Lyra's audio-focused boundary.
 *
 * The test uses only the public engine factory plus [LyraYouTubeHost]. It proves real CDN bytes,
 * stable-identity refresh, a durable checkpoint, and resume through a brand-new engine instance.
 */
@RunWith(AndroidJUnit4::class)
class LyraHostIntegrationTest {
    @Test
    fun audioRefreshAndCheckpointResumeStayBehindStableHostApi() = runBlocking {
        val videoId = "dQw4w9WgXcQ"
        val host = LyraYouTubeHost(YouTubeEngineFactory.create())

        val source = host.prepareAudio(videoId, preferredContainer = "mp4")
        assertTrue(source.proof.bytesRead >= 512)
        assertTrue(source.format.transportReady)
        assertTrue(source.format.hasAudio && !source.format.hasVideo)

        val first = host.read(source, startByte = 0, byteLimit = 64 * 1024)
        assertTrue(first.bytes.size >= 512)
        assertEquals(source.format.stableIdentity, first.format.stableIdentity)
        val checkpoint = first.checkpoint(videoId)
        assertEquals(first.nextByteOffset, checkpoint.nextByteOffset)

        val refreshed = host.refresh(source)
        assertEquals(source.format.stableIdentity, refreshed.format.stableIdentity)
        assertTrue(refreshed.proof.bytesRead >= 512)

        // Simulate host/process recreation: Lyra retains only its durable checkpoint and creates
        // a fresh engine instance. The engine must rediscover/refresh the exact stable format.
        val restartedHost = LyraYouTubeHost(YouTubeEngineFactory.create())
        val resumed = restartedHost.resume(checkpoint, byteLimit = 64 * 1024)
        assertTrue(resumed.refreshed)
        assertEquals(checkpoint.stableFormatIdentity, resumed.format.stableIdentity)
        assertEquals(checkpoint.nextByteOffset, resumed.startByte)
        assertTrue(resumed.bytes.size >= 512)
        assertEquals(checkpoint.totalBytes, resumed.totalBytes)

        println(
            "YT_PROOF lyra-host=SUPPORTED_AND_PROVEN " +
                "state=" + ResolutionState.SUPPORTED_AND_PROVEN +
                " identity=" + source.format.stableIdentity +
                " itag=" + source.format.itag +
                " first=" + first.startByte + "+" + first.bytes.size +
                " resume=" + resumed.startByte + "+" + resumed.bytes.size
        )
    }
}
