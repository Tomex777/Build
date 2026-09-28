package dev.tomex.youtube.testapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomex.youtube.api.SearchResult
import dev.tomex.youtube.api.ResolutionState
import dev.tomex.youtube.api.ResolverFailure
import dev.tomex.youtube.core.PlayerResponseClassifier
import dev.tomex.youtube.core.DescriptionChapterParser
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
        println("YT_PROOF search diagnostics=${results.diagnostics}")
        val selectedResult = results.items.filterIsInstance<SearchResult.Video>().firstOrNull()
            ?: fail("Search returned no video results")
        println("YT_PROOF search=${results.items.size} selected=${selectedResult.id} title=${selectedResult.title}")
        assertTrue("Search result title missing", selectedResult.title.isNotBlank())
        val continuation = results.continuation ?: fail("Search did not return a continuation token")
        val next = engine.search("House MD", continuation)
        assertTrue("Search continuation returned no videos", next.items.any { it is SearchResult.Video })
        println("YT_PROOF continuation=${next.items.size}")

        val selectedDetails = engine.videoDetails(selectedResult.id)
        assertEquals(selectedResult.id, selectedDetails.id)
        assertTrue("Selected search result details title missing", selectedDetails.title.isNotBlank())
        println("YT_PROOF search-to-details id=${selectedDetails.id} title=${selectedDetails.title}")

        val id = "dQw4w9WgXcQ" // Public 2160p video observed in the September 2026 live response.
        val details = engine.videoDetails(id)
        assertTrue("Video details title missing", details.title.isNotBlank())
        println("YT_PROOF details id=${details.id} title=${details.title}")
        assertTrue("Expected live caption tracks", details.subtitles.isNotEmpty())
        val subtitleProof = engine.fetchSubtitle(details.subtitles.first())
        assertTrue("Subtitle endpoint returned no bytes", subtitleProof.bytesRead > 0)
        println("YT_PROOF subtitle lang=${details.subtitles.first().language} automatic=${details.subtitles.first().automatic} $subtitleProof")
        try {
            val chaptered = engine.videoDetails("dyAiNCF2J3A")
            assertTrue("Live chaptered video returned no chapters", chaptered.chapters.size >= 2)
            assertEquals(0L, chaptered.chapters.first().startMs)
            assertTrue(chaptered.chapters.zipWithNext().all { (a, b) -> a.startMs < b.startMs })
            println("YT_PROOF live-chapters id=${chaptered.id} count=${chaptered.chapters.size} first=${chaptered.chapters.first()}")
        } catch (e: ResolverFailure.ChallengeRequired) {
            assertEquals(ResolutionState.CHALLENGED, PlayerResponseClassifier.state(e))
            println("YT_STATE live-chapters=CHALLENGED")
        }
        val verified = engine.resolveVerified(id, 2160)
        val resolved = verified.descriptor
        println("YT_PROOF player=${resolved.client} formats=${resolved.formats.size} diagnostics=${resolved.diagnostics}")
        val video = verified.selection.video
        val audio = verified.selection.audio
        val videoProof = verified.videoProof
        val audioProof = verified.audioProof
        assertEquals(ResolutionState.SUPPORTED_AND_PROVEN, verified.state)
        assertTrue("2160p format is not available", (video.height ?: 0) >= 2160)
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
        assertTrue("Refreshed descriptor is already expired", (refreshed.expiresAtEpochSeconds ?: 0) > System.currentTimeMillis() / 1000)
        val refreshProof = engine.probe(refreshed)
        println("YT_PROOF refresh itag=${refreshed.itag} $refreshProof")
        assertTrue(refreshProof.bytesRead >= 512)
        try {
            engine.probe(refreshed.copy(expiresAtEpochSeconds = System.currentTimeMillis() / 1000 - 1))
            fail("Expired descriptor was allowed to reach the CDN")
        } catch (_: ResolverFailure.MediaUrlExpired) {
            println("YT_PROOF expiry=EXPIRED before request")
        }

        // Force the descriptor's local expiry guard so the real test exercises the
        // recovery path while preserving the same format identity and byte offset.
        val expired = video.copy(expiresAtEpochSeconds = System.currentTimeMillis() / 1000 - 1)
        val resumedAfterRefresh = engine.fetchChunkWithRefresh(id, expired, 8192, 4096)
        assertTrue("Expired media URL did not refresh", resumedAfterRefresh.refreshed)
        assertEquals(video.stableIdentity, resumedAfterRefresh.format.stableIdentity)
        assertEquals(8192L, resumedAfterRefresh.startByte)
        assertTrue(resumedAfterRefresh.contentRange?.startsWith("bytes 8192-") == true)
        assertTrue(resumedAfterRefresh.bytes.size >= 512)
        assertEquals(8192L + resumedAfterRefresh.bytes.size - 1,
            resumedAfterRefresh.contentRange?.substringAfter("bytes 8192-")?.substringBefore('/')?.toLongOrNull())
        val checkpoint = resumedAfterRefresh.checkpoint(id)
        assertEquals(video.stableIdentity, checkpoint.stableFormatIdentity)
        assertEquals(8192L + resumedAfterRefresh.bytes.size, checkpoint.nextByteOffset)
        assertEquals(resumedAfterRefresh.totalBytes, checkpoint.totalBytes)
        val nextChunk = engine.fetchChunkWithRefresh(id, resumedAfterRefresh.format, checkpoint.nextByteOffset, 4096)
        assertFalse("Valid refreshed URL should continue without another refresh", nextChunk.refreshed)
        assertEquals(checkpoint.nextByteOffset, nextChunk.startByte)
        assertTrue(nextChunk.bytes.size >= 512)
        assertTrue(nextChunk.contentRange?.startsWith("bytes ${checkpoint.nextByteOffset}-") == true)
        println("YT_PROOF expiry-refresh identity=${resumedAfterRefresh.format.stableIdentity} start=${resumedAfterRefresh.startByte} bytes=${resumedAfterRefresh.bytes.size} checkpoint=$checkpoint next=${nextChunk.startByte}+${nextChunk.bytes.size}")
    }

    @Test fun failureStagesAreExplicit() = runBlocking {
        val challenged = PlayerResponseClassifier.failure(JSONObject("""{"status":"UNPLAYABLE","reason":"Sign in to confirm you're not a bot"}"""))
        val signIn = PlayerResponseClassifier.failure(JSONObject("""{"status":"LOGIN_REQUIRED","reason":"Sign in to confirm your age"}"""))
        val ciphered = PlayerResponseClassifier.deliveryFailure(JSONObject(), 1, 1)
        val sabr = PlayerResponseClassifier.deliveryFailure(JSONObject().put("serverAbrStreamingUrl", "https://example.invalid/sabr"), 12, 0)
        val expired = ResolverFailure.MediaUrlExpired("expired")
        val unsupported = ResolverFailure.UnsupportedDelivery("unknown")
        assertEquals(ResolutionState.CHALLENGED, PlayerResponseClassifier.state(challenged))
        assertTrue("Bot checks must not be mislabeled as ordinary sign-in", challenged is ResolverFailure.ChallengeRequired)
        assertTrue(signIn is ResolverFailure.SignInRequired)
        assertEquals(ResolutionState.CIPHERED, PlayerResponseClassifier.state(ciphered))
        assertEquals(ResolutionState.SABR_ONLY, PlayerResponseClassifier.state(sabr))
        assertEquals(ResolutionState.EXPIRED, PlayerResponseClassifier.state(expired))
        assertEquals(ResolutionState.UNSUPPORTED, PlayerResponseClassifier.state(unsupported))
        println("YT_PROOF states=SUPPORTED_AND_PROVEN,CHALLENGED,CIPHERED,SABR_ONLY,EXPIRED,UNSUPPORTED")

        val chapters = DescriptionChapterParser.parse("""0:00 Intro
1:02 First part
12:34 Final part""")
        assertEquals(listOf(0L, 62_000L, 754_000L), chapters.map { it.startMs })
        assertTrue(DescriptionChapterParser.parse("1:00 Missing zero chapter\n2:00 Another").isEmpty())
        println("YT_PROOF chapters=${chapters.size} ordered=${chapters.zipWithNext().all { it.first.startMs < it.second.startMs }}")
    }
}
