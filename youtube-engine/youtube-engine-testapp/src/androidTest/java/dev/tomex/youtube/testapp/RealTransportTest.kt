package dev.tomex.youtube.testapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomex.youtube.api.SearchResult
import dev.tomex.youtube.api.ResolutionState
import dev.tomex.youtube.api.ResolverFailure
import dev.tomex.youtube.core.PlayerResponseClassifier
import dev.tomex.youtube.core.NativeYouTubeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

/** A network integration gate: metadata and URL strings alone cannot pass. */
@RunWith(AndroidJUnit4::class)
class RealTransportTest {
    @Test fun searchPlayerAdaptiveBytesAndRefresh() = runBlocking {
        val engine = NativeYouTubeEngine()
        val results = engine.search("House MD")
        assertTrue("Search returned no videos", results.items.any { it is SearchResult.Video })
        println("YT_PROOF search=${results.items.size} first=${results.items.filterIsInstance<SearchResult.Video>().first().id}")
        results.continuation?.let { token ->
            val next = engine.search("House MD", token)
            assertTrue("Search continuation returned no videos", next.items.any { it is SearchResult.Video })
            println("YT_PROOF continuation=${next.items.size}")
        }

        val id = "dQw4w9WgXcQ" // Public 2160p video observed in the September 2026 live response.
        val details = engine.videoDetails(id)
        assertTrue("Video details title missing", details.title.isNotBlank())
        println("YT_PROOF details id=${details.id} title=${details.title}")
        val verified = engine.resolveVerified(id, 1080)
        val resolved = verified.descriptor
        println("YT_PROOF player=${resolved.client} formats=${resolved.formats.size} diagnostics=${resolved.diagnostics}")
        val video = verified.selection.video
        val audio = verified.selection.audio
        val videoProof = verified.videoProof
        val audioProof = verified.audioProof
        assertEquals(ResolutionState.SUPPORTED_AND_PROVEN, verified.state)
        println("YT_PROOF video itag=${video.itag} height=${video.height} $videoProof")
        println("YT_PROOF audio itag=${audio.itag} codec=${audio.codecs} $audioProof")
        assertTrue(videoProof.bytesRead >= 512)
        assertTrue(audioProof.bytesRead >= 512)
        val resumed = engine.probeRange(video, 8192)
        assertEquals(8192L, resumed.startByte)
        assertTrue(resumed.contentRange?.startsWith("bytes 8192-") == true)
        println("YT_PROOF resume itag=${video.itag} $resumed")

        val refreshed = engine.refreshMedia(id, video.stableIdentity)
        assertEquals(video.stableIdentity, refreshed.stableIdentity)
        val refreshProof = engine.probe(refreshed)
        println("YT_PROOF refresh itag=${refreshed.itag} $refreshProof")
        assertTrue(refreshProof.bytesRead >= 512)
    }

    @Test fun failureStagesAreExplicit() = runBlocking {
        val challenged = PlayerResponseClassifier.failure(JSONObject("""{"status":"UNPLAYABLE","reason":"Sign in to confirm you're not a bot"}"""))
        val ciphered = PlayerResponseClassifier.deliveryFailure(JSONObject(), 1, 1)
        val sabr = PlayerResponseClassifier.deliveryFailure(JSONObject().put("serverAbrStreamingUrl", "https://example.invalid/sabr"), 12, 0)
        val expired = ResolverFailure.MediaUrlExpired("expired")
        val unsupported = ResolverFailure.UnsupportedDelivery("unknown")
        assertEquals(ResolutionState.CHALLENGED, PlayerResponseClassifier.state(challenged))
        assertEquals(ResolutionState.CIPHERED, PlayerResponseClassifier.state(ciphered))
        assertEquals(ResolutionState.SABR_ONLY, PlayerResponseClassifier.state(sabr))
        assertEquals(ResolutionState.EXPIRED, PlayerResponseClassifier.state(expired))
        assertEquals(ResolutionState.UNSUPPORTED, PlayerResponseClassifier.state(unsupported))
        println("YT_PROOF states=SUPPORTED_AND_PROVEN,CHALLENGED,CIPHERED,SABR_ONLY,EXPIRED,UNSUPPORTED")
    }
}
