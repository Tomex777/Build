package dev.tomex.youtube.testapp

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomex.youtube.api.ResolutionState
import dev.tomex.youtube.api.SearchResult
import dev.tomex.youtube.core.YouTubeEngineFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * API 26 transport gate kept intentionally separate from QuickJS/player-script execution so the
 * old emulator never has to retain Media3, multi-megabyte player JS and large transport buffers
 * in one instrumentation process.
 */
@RunWith(AndroidJUnit4::class)
class Api26TransportAcceptanceTest {
    @Test
    fun search4kRefreshAndCheckpointResume() = runBlocking {
        assumeTrue("API 26-specific memory-domain gate", Build.VERSION.SDK_INT <= 26)

        val engine = YouTubeEngineFactory.create()
        val search = engine.search("House MD")
        assertTrue(search.items.any { it is SearchResult.Video })

        val videoId = "dQw4w9WgXcQ"
        val details = engine.videoDetails(videoId)
        assertEquals(videoId, details.id)

        val verified = engine.resolveVerified(videoId, minimumHeight = 2160)
        assertEquals(ResolutionState.SUPPORTED_AND_PROVEN, verified.state)
        assertTrue((verified.selection.video.height ?: 0) >= 2160)
        assertTrue(verified.videoProof.bytesRead >= 512)
        assertTrue(verified.audioProof.bytesRead >= 512)

        val expired = verified.selection.video.copy(
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 - 1
        )
        val first = engine.fetchChunkWithRefresh(
            videoId = videoId,
            format = expired,
            startByte = 8192,
            byteLimit = 64 * 1024
        )
        assertTrue(first.refreshed)
        assertTrue(first.bytes.size >= 512)
        assertEquals(8192L, first.startByte)

        val checkpoint = first.checkpoint(videoId)
        val restarted = YouTubeEngineFactory.create()
        val resumed = restarted.fetchChunkFromCheckpoint(checkpoint, byteLimit = 64 * 1024)
        assertTrue(resumed.refreshed)
        assertEquals(checkpoint.stableFormatIdentity, resumed.format.stableIdentity)
        assertEquals(checkpoint.nextByteOffset, resumed.startByte)
        assertEquals(checkpoint.totalBytes, resumed.totalBytes)
        assertTrue(resumed.bytes.size >= 512)

        println(
            "YT_PROOF api26-transport=SUPPORTED_AND_PROVEN " +
                "videoItag=" + verified.selection.video.itag +
                " height=" + verified.selection.video.height +
                " audioItag=" + verified.selection.audio.itag +
                " checkpoint=" + checkpoint.nextByteOffset +
                " resume=" + resumed.startByte + "+" + resumed.bytes.size
        )
    }
}
