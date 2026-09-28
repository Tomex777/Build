package dev.tomex.youtube.testapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tomex.youtube.api.SearchResult
import dev.tomex.youtube.api.ResolutionState
import dev.tomex.youtube.api.ResolverFailure
import dev.tomex.youtube.core.*
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
        val videoResults = results.items.filterIsInstance<SearchResult.Video>()
        assertTrue("Search returned no video results", videoResults.isNotEmpty())
        val selectedResult = videoResults.first()
        println("YT_PROOF search=${results.items.size} selected=${selectedResult.id} title=${selectedResult.title}")
        assertTrue("Search result title missing", selectedResult.title.isNotBlank())
        assertNotNull("Search did not return a continuation token", results.continuation)
        val continuation = requireNotNull(results.continuation)
        val next = engine.search("House MD", continuation)
        assertTrue("Search continuation returned no videos", next.items.any { it is SearchResult.Video })
        val winningClient = results.diagnostics.first { it.contains("recognizedResults=true") }.substringBefore(":")
        assertTrue("Continuation must stay bound to its successful Innertube client $winningClient",
            next.diagnostics.contains("continuation pinned to $winningClient"))
        println("YT_PROOF continuation=${next.items.size} diagnostics=${next.diagnostics}")

        try {
            val selectedDetails = engine.videoDetails(selectedResult.id)
            assertEquals(selectedResult.id, selectedDetails.id)
            assertTrue("Selected search result details title missing", selectedDetails.title.isNotBlank())
            println("YT_PROOF search-to-details id=${selectedDetails.id} title=${selectedDetails.title}")
        } catch (e: ResolverFailure.ChallengeRequired) {
            assertEquals(ResolutionState.CHALLENGED, PlayerResponseClassifier.state(e))
            println("YT_STATE search-result=CHALLENGED id=${selectedResult.id}")
        } catch (e: ResolverFailure.SignInRequired) {
            assertEquals(ResolutionState.CHALLENGED, PlayerResponseClassifier.state(e))
            println("YT_STATE search-result=CHALLENGED id=${selectedResult.id}")
        }

        val id = "dQw4w9WgXcQ" // Public 2160p video observed in the September 2026 live response.
        val details = engine.videoDetails(id)
        assertTrue("Video details title missing", details.title.isNotBlank())
        println("YT_PROOF details id=${details.id} title=${details.title}")
        assertTrue("Expected live caption tracks", details.subtitles.isNotEmpty())
        val expiredSubtitle = details.subtitles.first().copy(
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 - 1
        )
        val subtitleProof = engine.fetchSubtitleWithRefresh(id, expiredSubtitle)
        assertTrue("Subtitle endpoint returned no bytes", subtitleProof.bytesRead > 0)
        println("YT_PROOF subtitle-refresh identity=${expiredSubtitle.stableIdentity} lang=${expiredSubtitle.language} automatic=${expiredSubtitle.automatic} $subtitleProof")
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
        assertTrue("Resolver diagnostics must account for excluded ciphered formats",
            resolved.diagnostics.any { it.contains("excluded") && it.contains("ciphered formats") })
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

        // Simulate process recreation: a new engine instance receives only the durable checkpoint.
        val restartedEngine = NativeYouTubeEngine()
        val restartedFormat = restartedEngine.refreshMedia(checkpoint.videoId, checkpoint.stableFormatIdentity)
        assertEquals(checkpoint.stableFormatIdentity, restartedFormat.stableIdentity)
        val restartedChunk = restartedEngine.fetchChunkWithRefresh(
            checkpoint.videoId, restartedFormat, checkpoint.nextByteOffset, 1_048_576
        )
        assertEquals(checkpoint.nextByteOffset, restartedChunk.startByte)
        assertEquals("Restarted download did not return a full 1 MiB chunk", 1_048_576, restartedChunk.bytes.size)
        assertEquals(checkpoint.totalBytes, restartedChunk.totalBytes)
        assertTrue(restartedChunk.contentRange?.startsWith("bytes ${checkpoint.nextByteOffset}-") == true)
        val largeChunkCheckpoint = restartedChunk.checkpoint(checkpoint.videoId)
        assertEquals(checkpoint.nextByteOffset + 1_048_576, largeChunkCheckpoint.nextByteOffset)
        val boundaryChunk = restartedEngine.fetchChunkWithRefresh(
            checkpoint.videoId, restartedChunk.format, largeChunkCheckpoint.nextByteOffset, 1_048_576
        )
        assertEquals(1_048_576, boundaryChunk.bytes.size)
        assertEquals(largeChunkCheckpoint.nextByteOffset, boundaryChunk.startByte)
        assertTrue(boundaryChunk.contentRange?.startsWith("bytes ${largeChunkCheckpoint.nextByteOffset}-") == true)
        println("YT_PROOF process-restart-resume identity=${checkpoint.stableFormatIdentity} first=${restartedChunk.startByte}+${restartedChunk.bytes.size} boundary=${boundaryChunk.startByte}+${boundaryChunk.bytes.size}")
    }

    @Test fun failureStagesAreExplicit() = runBlocking {
        val challenged = PlayerResponseClassifier.failure(JSONObject("""{"status":"UNPLAYABLE","reason":"Sign in to confirm you're not a bot"}"""))
        val signIn = PlayerResponseClassifier.failure(JSONObject("""{"status":"LOGIN_REQUIRED","reason":"Sign in to confirm your age"}"""))
        val ciphered = PlayerResponseClassifier.deliveryFailure(JSONObject(), 1, 1)
        val sabr = PlayerResponseClassifier.deliveryFailure(JSONObject().put("serverAbrStreamingUrl", "https://example.invalid/sabr"), 12, 0)
        val dash = PlayerResponseClassifier.deliveryFailure(JSONObject().put("dashManifestUrl", "https://example.invalid/manifest.mpd"), 0, 0)
        val cipheredWithSabr = PlayerResponseClassifier.deliveryFailure(
            JSONObject().put("serverAbrStreamingUrl", "https://example.invalid/sabr"), 1, 1)
        val urlAndCipher = JSONObject().put("url", "https://media.example.invalid/direct")
            .put("signatureCipher", "s=not-deciphered&url=https%3A%2F%2Fmedia.example.invalid%2Fdirect")
        val expired = ResolverFailure.MediaUrlExpired("expired")
        val rateLimited = PlayerResponseClassifier.innertubeFailure(
            JSONObject().put("code", 429).put("message", "Too many requests"))
        val unsupported = ResolverFailure.UnsupportedDelivery("unknown")
        val malformed = ResolverFailure.MalformedResponse("missing playabilityStatus")
        val embeddedRateLimit = PlayerResponseClassifier.failure(
            JSONObject().put("status", "ERROR").put("reason", "Too many requests; try again later")
        )
        assertEquals(ResolutionState.CHALLENGED, PlayerResponseClassifier.state(challenged))
        assertTrue("Bot checks must not be mislabeled as ordinary sign-in", challenged is ResolverFailure.ChallengeRequired)
        assertTrue(signIn is ResolverFailure.SignInRequired)
        assertEquals(ResolutionState.CIPHERED, PlayerResponseClassifier.state(ciphered))
        assertEquals(ResolutionState.SABR_ONLY, PlayerResponseClassifier.state(sabr))
        assertEquals(ResolutionState.DASH_MANIFEST_ONLY, PlayerResponseClassifier.state(dash))
        assertEquals("Ciphered direct formats must not be mislabeled SABR-only", ResolutionState.CIPHERED,
            PlayerResponseClassifier.state(cipheredWithSabr))
        assertTrue("A URL field must not make a ciphered format appear directly usable",
            PlayerResponseClassifier.hasCipherParameters(urlAndCipher))
        assertFalse(PlayerResponseClassifier.hasCipherParameters(JSONObject().put("url", "https://media.example.invalid/direct")))
        assertTrue(PlayerResponseClassifier.hasNSigParameter("https://media.example.invalid/videoplayback?n=abc123&itag=313"))
        assertFalse(PlayerResponseClassifier.hasNSigParameter("https://media.example.invalid/videoplayback?expire=123&itag=313"))
        val rewrittenN = PlayerUrlTransforms.replaceN(
            "https://media.example.invalid/videoplayback?expire=123&n=abc%2D123&itag=313",
            "transformed/value"
        )
        assertEquals("abc-123", PlayerUrlTransforms.extractN("https://media.example.invalid/videoplayback?n=abc%2D123&itag=313"))
        assertEquals("transformed/value", PlayerUrlTransforms.extractN(rewrittenN))
        assertEquals(
            "https://www.youtube.com/s/player/a/base.js",
            PlayerUrlTransforms.normalizePlayerJavaScriptUrl("/s/player/a/base.js")
        )
        assertEquals(
            "https://www.youtube.com/s/player/a/base.js",
            PlayerUrlTransforms.normalizePlayerJavaScriptUrl("\\/s\\/player\\/a\\/base.js")
        )
        assertNull(PlayerUrlTransforms.normalizePlayerJavaScriptUrl("https://evil.example/base.js"))
        assertTrue(rewrittenN.contains("expire=123"))
        assertTrue(rewrittenN.contains("itag=313"))
        val nRequired = PlayerResponseClassifier.mediaHttpFailure(
            status = 403, nParameterNeedsTransform = true,
            expiresAtEpochSeconds = 2_000_000_000L, nowEpochSeconds = 1_900_000_000L
        ) ?: throw AssertionError("Expected explicit n-parameter failure")
        assertTrue(nRequired is ResolverFailure.NParameterTransformRequired)
        assertEquals(ResolutionState.N_PARAMETER_REQUIRED, PlayerResponseClassifier.state(nRequired))
        val ordinary403 = PlayerResponseClassifier.mediaHttpFailure(
            status = 403, nParameterNeedsTransform = false,
            expiresAtEpochSeconds = 2_000_000_000L, nowEpochSeconds = 1_900_000_000L
        ) ?: throw AssertionError("Expected expiry classification")
        assertTrue(ordinary403 is ResolverFailure.MediaUrlExpired)
        assertEquals(ResolutionState.EXPIRED, PlayerResponseClassifier.state(expired))
        assertTrue("Innertube 429 responses need a distinct failure", rateLimited is ResolverFailure.RateLimited)
        assertEquals(ResolutionState.RATE_LIMITED, PlayerResponseClassifier.state(rateLimited))
        assertEquals(ResolutionState.RATE_LIMITED, PlayerResponseClassifier.state(embeddedRateLimit))
        assertTrue(PlayerResponseClassifier.innertubeFailure(JSONObject().put("code", 500)) is ResolverFailure.PlayerResponseFailure)
        assertEquals(ResolutionState.MALFORMED_RESPONSE, PlayerResponseClassifier.state(malformed))
        assertEquals(ResolutionState.MALFORMED_RESPONSE,
            PlayerResponseClassifier.state(PlayerResponseClassifier.failure(null)))
        assertEquals(ResolutionState.UNSUPPORTED, PlayerResponseClassifier.state(unsupported))
        assertTrue(SessionRequestPolicy.allowsSessionOrigin("https://www.youtube.com/youtubei/v1/player"))
        assertTrue(SessionRequestPolicy.allowsSessionOrigin("https://music.youtube.com/"))
        assertFalse(SessionRequestPolicy.allowsSessionOrigin("http://www.youtube.com/"))
        assertFalse(SessionRequestPolicy.allowsSessionOrigin("https://youtube.com.evil.example/"))
        assertFalse(SessionRequestPolicy.allowsSessionOrigin("https://rr1---sn.example.googlevideo.com/videoplayback"))
        val sanitizedHeaders = SessionRequestPolicy.sanitize(
            "https://www.youtube.com/youtubei/v1/player",
            mapOf(
                "Cookie" to "SID=secret",
                "Authorization" to "Bearer host-owned",
                "User-Agent" to "override",
                "Range" to "bytes=0-1",
                "X-YouTube-Client-Version" to "override",
                "Bad\nHeader" to "nope"
            )
        )
        assertEquals(setOf("Cookie", "Authorization"), sanitizedHeaders.keys)
        assertTrue(SessionRequestPolicy.sanitize(
            "https://rr1---sn.example.googlevideo.com/videoplayback",
            mapOf("Cookie" to "SID=must-not-leak")
        ).isEmpty())
        println("YT_PROOF session-origin=https-youtube-only engine-owned-headers-protected")
        var transformCalls = 0
        val cachedTransformer = CachedNParameterTransformer(object : NParameterTransformer {
            override suspend fun transform(playerJavaScriptUrl: String, input: String): String {
                transformCalls++
                return "tx-$input"
            }
        }, maxEntries = 4)
        assertEquals("tx-abc", cachedTransformer.transform("https://www.youtube.com/s/player/a/base.js", "abc"))
        assertEquals("tx-abc", cachedTransformer.transform("https://www.youtube.com/s/player/a/base.js", "abc"))
        assertEquals(1, transformCalls)
        assertEquals("tx-abc", cachedTransformer.transform("https://www.youtube.com/s/player/b/base.js", "abc"))
        assertEquals(2, transformCalls)
        assertEquals("tx-abc", cachedTransformer.transform("https://www.youtube.com/s/player/a/base.js", "abc"))
        assertEquals(3, transformCalls)
        println("YT_PROOF states=SUPPORTED_AND_PROVEN,CHALLENGED,CIPHERED,N_PARAMETER_REQUIRED,SABR_ONLY,DASH_MANIFEST_ONLY,EXPIRED,RATE_LIMITED,MALFORMED_RESPONSE,UNSUPPORTED")
        println("YT_PROOF n-sig=bounded-transform-hook+player-js-cache-invalidation+explicit-403-classification")

        val videoIdentity = StableFormatIdentity.create(313, true, false, "webm", "vp9", 3840, 2160, 60, 12_000_000, null, null)
        assertEquals(videoIdentity, StableFormatIdentity.create(313, true, false, "WEBM", "VP9", 3840, 2160, 60, 12_000_000, null, null))
        assertNotEquals(videoIdentity, StableFormatIdentity.create(313, true, false, "webm", "vp9", 1920, 1080, 60, 12_000_000, null, null))
        assertFalse("Stable identity must exclude expiring URLs", videoIdentity.contains("https://"))
        println("YT_PROOF stable-identity=client-independent track-descriptor=v2")

        val chapters = DescriptionChapterParser.parse("""0:00 Intro
1:02 First part
12:34 Final part""")
        assertEquals(listOf(0L, 62_000L, 754_000L), chapters.map { it.startMs })
        assertTrue(DescriptionChapterParser.parse("1:00 Missing zero chapter\n2:00 Another").isEmpty())
        println("YT_PROOF chapters=${chapters.size} ordered=${chapters.zipWithNext().all { it.first.startMs < it.second.startMs }}")
    }
}
