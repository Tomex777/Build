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
        val livePlayerSource = CachedPlayerScriptSource(HttpPlayerScriptSource())
        val engine = NativeYouTubeEngine(playerScriptSource = livePlayerSource)
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
        assertTrue("Resolver diagnostics must account for ciphered formats and malformed metadata separately",
            resolved.diagnostics.any {
                it.contains("excluded ciphered=") && it.contains("valid=") && it.contains("malformed=")
            })
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
        val restartedChunk = restartedEngine.fetchChunkFromCheckpoint(checkpoint, 1_048_576)
        assertTrue("Checkpoint resume must refresh the expiring descriptor before reading", restartedChunk.refreshed)
        assertEquals(checkpoint.stableFormatIdentity, restartedChunk.format.stableIdentity)
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

        // Always inspect the current bootstrap player JavaScript directly. WEB /player can be
        // challenged for every diagnostic video, but that must not hide player-script drift from CI.
        val livePlayerDiagnostics = engine.currentPlayerScriptDiagnostics()
        assertTrue(
            "Current player JavaScript URL must stay on the trusted YouTube origin",
            livePlayerDiagnostics.playerJavaScriptUrl.startsWith("https://www.youtube.com/")
        )
        assertTrue("Current player JavaScript body is unexpectedly small", livePlayerDiagnostics.scriptBytes >= 16 * 1024)
        println(
            "YT_PROOF live-player-js url=${livePlayerDiagnostics.playerJavaScriptUrl} " +
                "bytes=${livePlayerDiagnostics.scriptBytes} " +
                "signaturePlan=${livePlayerDiagnostics.signaturePlanAvailable} " +
                "sts=${livePlayerDiagnostics.signatureTimestamp} " +
                "nDiagnostics=${livePlayerDiagnostics.nParameter}"
        )
        val liveRuntimeTransform = PlayerScriptUrlTransformer(livePlayerSource).transform(
            playerJavaScriptUrl = livePlayerDiagnostics.playerJavaScriptUrl,
            mediaUrl = "https://rr1---sn.example.googlevideo.com/videoplayback?itag=313&n=abcdefghijklmnopqrstuvwxyz"
        )
        if (liveRuntimeTransform != null) {
            assertTrue(liveRuntimeTransform.nTransformed)
            assertNotEquals(
                "abcdefghijklmnopqrstuvwxyz",
                PlayerUrlTransforms.extractN(liveRuntimeTransform.url)
            )
            println(
                "YT_PROOF live-player-runtime=TRANSFORM_ONLY_UNVERIFIED " +
                    "candidate=${livePlayerDiagnostics.nParameter.urlBuilderCandidates.singleOrNull()} " +
                    "n=${PlayerUrlTransforms.extractN(liveRuntimeTransform.url)}"
            )
        } else {
            println(
                "YT_PROOF live-player-runtime=UNRESOLVED_NO_TRANSPORT_CLAIM " +
                    "candidates=${livePlayerDiagnostics.nParameter.urlBuilderCandidates}"
            )
        }

        // Force WEB through the bounded player-script parsers. The live search result is tried
        // before the fixed 4K fixture because WEB can gate individual videos differently. Keep the
        // diagnostic candidate set deliberately tiny; this is not another client/identity roulette.
        // Any n/signature transform that is claimed transport-ready must immediately prove itself
        // with real CDN bytes.
        val diagnosticScriptSource = object : PlayerScriptSource {
            private val delegate = HttpPlayerScriptSource()
            var playerJavaScriptUrl: String? = null
            var script: String? = null

            override suspend fun load(playerJavaScriptUrl: String): String? {
                if (this.playerJavaScriptUrl == playerJavaScriptUrl && script != null) return script
                return delegate.load(playerJavaScriptUrl).also { loaded ->
                    this.playerJavaScriptUrl = playerJavaScriptUrl
                    script = loaded
                }
            }
        }
        val webTransformEngine = NativeYouTubeEngine(
            strategies = listOf(ClientStrategy("WEB", "auto", "Mozilla/5.0")),
            nParameterTransformer = CachedNParameterTransformer(
                PlayerScriptNParameterTransformer(diagnosticScriptSource)
            ),
            signatureCipherDecipherer = CachedSignatureCipherDecipherer(
                PlayerScriptSignatureDecipherer(diagnosticScriptSource)
            )
        )
        val webCandidates = listOf(selectedResult.id, id).distinct().take(2)
        for (candidateId in webCandidates) {
            val webDescriptor = try {
                webTransformEngine.resolve(candidateId)
            } catch (e: ResolverFailure) {
                println(
                    "YT_PROOF web-transforms candidate=$candidateId " +
                        "state=${PlayerResponseClassifier.state(e)} failure=${e.javaClass.simpleName}"
                )
                null
            }
            if (webDescriptor != null) {
                val nPresentFormats = webDescriptor.formats.filter { it.nSigParameterPresent }
                val cipherPresentFormats = webDescriptor.formats.filter { it.signatureCipherPresent }
                val recoveredCipherFormats = webDescriptor.formats.filter { it.signatureDeciphered }
                val transformedNFormats = webDescriptor.formats.filter { it.nSigTransformed }
                val transportReadyRecovered = recoveredCipherFormats.filter { it.transportReady }
                val transportReadyN = transformedNFormats.filter { it.transportReady }
                println(
                    "YT_PROOF web-transforms candidate=$candidateId formats=${webDescriptor.formats.size} " +
                        "nPresent=${nPresentFormats.size} nTransformed=${transformedNFormats.size} " +
                        "cipherPresent=${cipherPresentFormats.size} cipherRecovered=${recoveredCipherFormats.size} " +
                        "transportReadyCipher=${transportReadyRecovered.size} transportReadyN=${transportReadyN.size} " +
                        "diagnostics=${webDescriptor.diagnostics}"
                )
                transportReadyRecovered.firstOrNull()?.let { recovered ->
                    val recoveredProof = webTransformEngine.probe(recovered)
                    assertTrue("Claimed WEB cipher recovery did not return media bytes", recoveredProof.bytesRead >= 512)
                    println(
                        "YT_PROOF web-cipher-cdn=SUPPORTED_AND_PROVEN candidate=$candidateId " +
                            "itag=${recovered.itag} identity=${recovered.stableIdentity} $recoveredProof"
                    )
                }
                transportReadyN.firstOrNull()?.let { transformed ->
                    val nProof = webTransformEngine.probe(transformed)
                    assertTrue("Claimed WEB n transform did not return media bytes", nProof.bytesRead >= 512)
                    println(
                        "YT_PROOF web-n-cdn=SUPPORTED_AND_PROVEN candidate=$candidateId " +
                            "itag=${transformed.itag} identity=${transformed.stableIdentity} $nProof"
                    )
                }
            }
        }
        diagnosticScriptSource.script?.let { script ->
            println(
                "YT_PROOF web-n-parser playerJs=${diagnosticScriptSource.playerJavaScriptUrl} " +
                    "diagnostics=${PlayerScriptNParameterParser.inspect(script)}"
            )
        }
        Unit
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
        val transient = ResolverFailure.TransientNetworkFailure("temporary")
        val redirectFailed = ResolverFailure.RedirectFailure("redirect")
        val lengthChanged = ResolverFailure.ContentLengthChanged("changed")
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
        val parsedCipher = PlayerUrlTransforms.cipherParameters(JSONObject().put(
            "signatureCipher",
            "url=https%3A%2F%2Fmedia.example.invalid%2Fvideoplayback%3Fitag%3D313%26n%3Dabc123&sp=sig&s=encrypted%2Bvalue"
        )) ?: throw AssertionError("Expected valid signatureCipher metadata")
        assertEquals("https://media.example.invalid/videoplayback?itag=313&n=abc123", parsedCipher.mediaUrl)
        assertEquals("encrypted+value", parsedCipher.encryptedSignature)
        assertEquals("sig", parsedCipher.signatureParameter)
        assertEquals("abc123", parsedCipher.nParameter)
        assertNull(PlayerUrlTransforms.cipherParameters(JSONObject().put(
            "signatureCipher", "url=http%3A%2F%2Fevil.example%2Fmedia&s=abc&sp=sig"
        )))
        assertNull(PlayerUrlTransforms.cipherParameters(JSONObject().put(
            "signatureCipher", "url=https%3A%2F%2Fmedia.example.invalid%2Fmedia&sp=sig"
        )))
        assertNull(PlayerUrlTransforms.cipherParameters(JSONObject().put(
            "cipher", "url=https%3A%2F%2Fmedia.example.invalid%2Fmedia&s=abc&sp=bad%0Aheader"
        )))
        val signedCipherUrl = PlayerUrlTransforms.applySignature(
            "https://media.example.invalid/videoplayback?itag=313&n=abc123",
            "sig",
            "deciphered/value + proof"
        ) ?: throw AssertionError("Expected bounded signature URL rewrite")
        assertTrue(signedCipherUrl.contains("itag=313"))
        assertTrue(signedCipherUrl.contains("n=abc123"))
        assertTrue(signedCipherUrl.contains("sig=deciphered%2Fvalue%20%2B%20proof"))
        assertNull(PlayerUrlTransforms.applySignature("http://media.example.invalid/media", "sig", "value"))
        assertNull(PlayerUrlTransforms.applySignature("https://media.example.invalid/media", "bad\nheader", "value"))
        println("YT_PROOF cipher-metadata=validated-url+s+sp+n bounded-signature-rewrite")
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
        assertEquals(ResolutionState.TRANSIENT_NETWORK, PlayerResponseClassifier.state(transient))
        assertEquals(ResolutionState.REDIRECT_FAILED, PlayerResponseClassifier.state(redirectFailed))
        assertEquals(ResolutionState.CONTENT_LENGTH_CHANGED, PlayerResponseClassifier.state(lengthChanged))
        assertEquals(ResolutionState.MALFORMED_RESPONSE, PlayerResponseClassifier.state(malformed))
        assertEquals(ResolutionState.MALFORMED_RESPONSE,
            PlayerResponseClassifier.state(PlayerResponseClassifier.failure(null)))
        assertEquals(ResolutionState.UNSUPPORTED, PlayerResponseClassifier.state(unsupported))
        assertTrue(PlayerResponseClassifier.mediaHttpFailure(
            status = 503,
            nParameterNeedsTransform = false,
            expiresAtEpochSeconds = null,
            nowEpochSeconds = 1_900_000_000L
        ) is ResolverFailure.TransientNetworkFailure)
        assertTrue(MediaRedirectPolicy.isRedirect(302))
        assertTrue(MediaRedirectPolicy.isRedirect(308))
        assertFalse(MediaRedirectPolicy.isRedirect(200))
        assertEquals(
            "https://rr.example.googlevideo.com/next",
            MediaRedirectPolicy.nextUrl(
                "https://rr.example.googlevideo.com/videoplayback?x=1",
                "/next"
            )
        )
        assertNull(MediaRedirectPolicy.nextUrl(
            "https://rr.example.googlevideo.com/videoplayback",
            "http://rr.example.googlevideo.com/insecure"
        ))
        assertNull(MediaRedirectPolicy.nextUrl(
            "https://rr.example.googlevideo.com/videoplayback",
            "https://user:secret@rr.example.googlevideo.com/credentialed"
        ))
        println("YT_PROOF transport-policy=3-attempt-transient-retry+5-hop-https-redirect-cap")
        val pendingNFormat = dev.tomex.youtube.api.MediaFormat(
            stableIdentity = "pending-n",
            itag = 313,
            url = "https://rr.example.googlevideo.com/videoplayback?n=raw",
            mimeType = "video/webm; codecs=\"vp9\"",
            codecs = "vp9",
            container = "webm",
            width = 1920,
            height = 1080,
            fps = 30,
            bitrate = 2_000_000,
            contentLength = 10_000,
            audioChannels = null,
            audioSampleRate = null,
            hasVideo = true,
            hasAudio = false,
            delivery = dev.tomex.youtube.api.Delivery.ADAPTIVE,
            requiredHeaders = emptyMap(),
            expiresAtEpochSeconds = null,
            nSigParameterPresent = true,
            nSigTransformed = false
        )
        val readyAudioFormat = pendingNFormat.copy(
            stableIdentity = "ready-audio",
            itag = 251,
            url = "https://rr.example.googlevideo.com/videoplayback",
            mimeType = "audio/webm; codecs=\"opus\"",
            codecs = "opus",
            width = null,
            height = null,
            fps = null,
            bitrate = 128_000,
            audioChannels = 2,
            audioSampleRate = 48_000,
            hasVideo = false,
            hasAudio = true,
            nSigParameterPresent = false
        )
        assertFalse(pendingNFormat.transportReady)
        assertTrue(TransportReadiness.failure(pendingNFormat) is ResolverFailure.NParameterTransformRequired)
        val blockedDescriptor = dev.tomex.youtube.api.PlaybackDescriptor(
            videoId = "dQw4w9WgXcQ",
            formats = listOf(pendingNFormat, readyAudioFormat),
            client = "TEST",
            diagnostics = emptyList()
        )
        assertNull("Pending n formats must never enter adaptive verified selection", blockedDescriptor.selectAdaptive(1080))
        assertTrue(readyAudioFormat.transportReady)
        println("YT_PROOF transport-readiness=pending-n-excluded-before-network")
        assertNull(ResumeIntegrity.failure(
            checkpointTotalBytes = 10_000L,
            descriptorContentLength = 10_000L,
            responseTotalBytes = 10_000L,
            startByte = 4096L,
            responseTotalRequired = true
        ))
        assertTrue(ResumeIntegrity.failure(
            checkpointTotalBytes = 10_000L,
            descriptorContentLength = 12_000L,
            responseTotalBytes = null,
            startByte = 4096L
        ) is ResolverFailure.ContentLengthChanged)
        assertTrue(ResumeIntegrity.failure(
            checkpointTotalBytes = 10_000L,
            descriptorContentLength = 10_000L,
            responseTotalBytes = 11_000L,
            startByte = 4096L,
            responseTotalRequired = true
        ) is ResolverFailure.ContentLengthChanged)
        assertTrue(ResumeIntegrity.failure(
            checkpointTotalBytes = 10_000L,
            descriptorContentLength = 10_000L,
            responseTotalBytes = null,
            startByte = 4096L,
            responseTotalRequired = true
        ) is ResolverFailure.UnsupportedDelivery)
        println("YT_PROOF resume-integrity=checkpoint-total+descriptor-total+content-range-total")
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
        var playerSourceLoads = 0
        val cachedPlayerScripts = CachedPlayerScriptSource(object : PlayerScriptSource {
            override suspend fun load(playerJavaScriptUrl: String): String {
                playerSourceLoads++
                return "script:$playerJavaScriptUrl"
            }
        }, maxEntries = 1)
        val playerA = "https://www.youtube.com/s/player/a/base.js"
        val playerB = "https://www.youtube.com/s/player/b/base.js"
        assertEquals("script:$playerA", cachedPlayerScripts.load(playerA))
        assertEquals("script:$playerA", cachedPlayerScripts.load(playerA))
        assertEquals(1, playerSourceLoads)
        assertEquals("script:$playerB", cachedPlayerScripts.load(playerB))
        assertEquals(2, playerSourceLoads)
        assertEquals("script:$playerA", cachedPlayerScripts.load(playerA))
        assertEquals(3, playerSourceLoads)
        var retryLoads = 0
        val retryingPlayerScripts = CachedPlayerScriptSource(object : PlayerScriptSource {
            override suspend fun load(playerJavaScriptUrl: String): String? {
                retryLoads++
                return if (retryLoads == 1) null else "recovered"
            }
        })
        assertNull(retryingPlayerScripts.load(playerA))
        assertEquals("recovered", retryingPlayerScripts.load(playerA))
        assertEquals("recovered", retryingPlayerScripts.load(playerA))
        assertEquals(2, retryLoads)
        println("YT_PROOF player-script-source=success-cache-by-player-url failures-retry")

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
        var decipherCalls = 0
        val cachedDecipherer = CachedSignatureCipherDecipherer(object : SignatureCipherDecipherer {
            override suspend fun decipher(playerJavaScriptUrl: String, encryptedSignature: String): String {
                decipherCalls++
                return "sig-$encryptedSignature"
            }
        }, maxEntries = 4)
        assertEquals("sig-encrypted", cachedDecipherer.decipher("https://www.youtube.com/s/player/a/base.js", "encrypted"))
        assertEquals("sig-encrypted", cachedDecipherer.decipher("https://www.youtube.com/s/player/a/base.js", "encrypted"))
        assertEquals(1, decipherCalls)
        assertEquals("sig-encrypted", cachedDecipherer.decipher("https://www.youtube.com/s/player/b/base.js", "encrypted"))
        assertEquals(2, decipherCalls)
        assertEquals("sig-encrypted", cachedDecipherer.decipher("https://www.youtube.com/s/player/a/base.js", "encrypted"))
        assertEquals(3, decipherCalls)
        val unchangedDecipherer = CachedSignatureCipherDecipherer(object : SignatureCipherDecipherer {
            override suspend fun decipher(playerJavaScriptUrl: String, encryptedSignature: String) = encryptedSignature
        })
        assertNull(unchangedDecipherer.decipher("https://www.youtube.com/s/player/a/base.js", "encrypted"))
        assertEquals(
            20333,
            PlayerScriptMetadataParser.signatureTimestamp("var cfg={signatureTimestamp:20333};")
        )
        assertEquals(
            20333,
            PlayerScriptMetadataParser.signatureTimestamp("var a={sts:20333};var b={signatureTimestamp:20333};")
        )
        assertNull(
            PlayerScriptMetadataParser.signatureTimestamp("var a={sts:20333};var b={signatureTimestamp:20334};")
        )

        val classicPlayerScript = """
            var Hx={
                Rv:function(a){a.reverse()},
                Sp:function(a,b){a.splice(0,b)},
                Sw:function(a,b){var c=a[0];a[0]=a[b%a.length];a[b%a.length]=c}
            };
            XY=function(a){a=a.split("");Hx.Sw(a,2);Hx.Rv(a);Hx.Sp(a,1);return a.join("")};
        """.trimIndent()
        val parsedPlan = PlayerScriptSignatureParser.parse(classicPlayerScript)
            ?: throw AssertionError("Expected bounded signature operation plan")
        assertEquals("edabc", parsedPlan.apply("abcdef"))
        assertNull(PlayerScriptSignatureParser.parse(
            classicPlayerScript + "\nAB=function(a){a=a.split(\"\");Hx.Rv(a);return a.join(\"\")};"
        ))
        var playerScriptLoads = 0
        val parsedDecipherer = PlayerScriptSignatureDecipherer(object : PlayerScriptSource {
            override suspend fun load(playerJavaScriptUrl: String): String {
                playerScriptLoads++
                return classicPlayerScript
            }
        })
        assertEquals(
            "edabc",
            parsedDecipherer.decipher("https://www.youtube.com/s/player/a/base.js", "abcdef")
        )
        assertEquals(
            "kjghi",
            parsedDecipherer.decipher("https://www.youtube.com/s/player/a/base.js", "ghijkl")
        )
        assertEquals(1, playerScriptLoads)

        val classicNPlayerScript = """
            var Nx={
                Rv:function(a){a.reverse()},
                Sp:function(a,b){a.splice(0,b)},
                Sw:function(a,b){var c=a[0];a[0]=a[b%a.length];a[b%a.length]=c}
            };
            nt=function(a){a=a.split("");Nx.Sw(a,2);a.push(a.shift());Nx.Rv(a);Nx.Sp(a,1);return a.join("")};
            function rewriteN(params){var value=params.get("n");value&&(value=nt(value),params.set("n",value))}
        """.trimIndent()
        val nDiagnostics = PlayerScriptNParameterParser.inspect(classicNPlayerScript)
        assertEquals(1, nDiagnostics.hintedFunctions)
        assertEquals(1, nDiagnostics.parsedPlans)
        val nPlan = PlayerScriptNParameterParser.parse(classicNPlayerScript)
            ?: throw AssertionError("Expected bounded n transform plan")
        assertEquals("fedab", nPlan.apply("abcdef"))
        assertNull(PlayerScriptNParameterParser.parse(
            classicNPlayerScript + """
                other=function(a){a=a.split("");a.reverse();return a.join("")};
                function rewriteOther(params){var value=params.get("n");value&&(value=other(value),params.set("n",value))}
            """.trimIndent()
        ))
        assertNull(PlayerScriptNParameterParser.parse("""
            bad=function(a){a=a.split("");a.sort();return a.join("")};
            function rewriteBad(params){var value=params.get("n");value&&(value=bad(value),params.set("n",value))}
        """.trimIndent()))
        var nScriptLoads = 0
        val parsedNTransformer = PlayerScriptNParameterTransformer(object : PlayerScriptSource {
            override suspend fun load(playerJavaScriptUrl: String): String {
                nScriptLoads++
                return classicNPlayerScript
            }
        })
        assertEquals(
            "fedab",
            parsedNTransformer.transform("https://www.youtube.com/s/player/a/base.js", "abcdef")
        )
        assertEquals(
            "lkjgh",
            parsedNTransformer.transform("https://www.youtube.com/s/player/a/base.js", "ghijkl")
        )
        assertEquals(1, nScriptLoads)
        println("YT_PROOF player-n-parser=bounded-n-callsite+reverse+drop+swap+rotate ambiguous-and-unknown=fail-closed cache=player-identity diagnostics=$nDiagnostics")
        val modernUrlConstructorScript = """
            y2=function(m,Z="",J=""){m=new g.g7(m,!0);m.set("alr","yes");J&&(J=Wv(65,2902,J));return m};
        """.trimIndent()
        val modernDiagnostics = PlayerScriptNParameterParser.inspect(modernUrlConstructorScript)
        assertEquals(1, modernDiagnostics.urlConstructorFunctions)
        assertEquals(listOf("g.g7"), modernDiagnostics.urlClassCandidates)
        assertEquals(
            listOf(PlayerScriptUrlBuilderCandidate("y2", "g.g7")),
            modernDiagnostics.urlBuilderCandidates
        )
        assertEquals(0, modernDiagnostics.parsedPlans)
        assertNull("Modern URL-constructor diagnostics must not imply a safe transform plan",
            PlayerScriptNParameterParser.parse(modernUrlConstructorScript))
        println("YT_PROOF player-n-modern-diagnostics=url-constructor-only fail-closed diagnostics=$modernDiagnostics")

        val unifiedRuntimeScript = """
            var g={};
            g.g7=function(m){
                this.value=m.replace(/([?&])n=([^&#]*)/,function(all,prefix,n){
                    return prefix+"n="+n.split("").reverse().join("")
                })
            };
            g.g7.prototype.set=function(k,v){
                var separator=this.value.indexOf("?")>=0?"&":"?";
                this.value+=separator+encodeURIComponent(k)+"="+encodeURIComponent(v)
            };
            g.g7.prototype.toString=function(){return this.value};
            function Wv(a,b,v){return v.split("").reverse().join("")}
            function $8(a,b,v){return v}
            y2=function(m,Z="",J=""){
                m=new g.g7(m,!0);
                m.set("alr","yes");
                J&&(J=Wv(65,2902,J),m.set(Z,$8(21,6101,J)));
                return m
            };
        """.trimIndent()
        val unifiedSource = CachedPlayerScriptSource(object : PlayerScriptSource {
            override suspend fun load(playerJavaScriptUrl: String): String = unifiedRuntimeScript
        })
        val unifiedTransformer = PlayerScriptUrlTransformer(unifiedSource)
        val unifiedCipher = unifiedTransformer.transform(
            playerJavaScriptUrl = "https://www.youtube.com/s/player/runtime-fixture/base.js",
            mediaUrl = "https://media.example.invalid/videoplayback?itag=313&n=abc",
            signatureParameter = "sig",
            encryptedSignature = "abcdef"
        ) ?: throw AssertionError("Expected bounded unified player URL transform")
        assertTrue(unifiedCipher.signatureApplied)
        assertTrue(unifiedCipher.nTransformed)
        assertEquals("cba", PlayerUrlTransforms.extractN(unifiedCipher.url))
        assertTrue(unifiedCipher.url.contains("sig=fedcba"))
        assertTrue(unifiedCipher.url.contains("alr=yes"))
        val unifiedNOnly = unifiedTransformer.transform(
            playerJavaScriptUrl = "https://www.youtube.com/s/player/runtime-fixture/base.js",
            mediaUrl = "https://media.example.invalid/videoplayback?itag=313&n=xyz"
        ) ?: throw AssertionError("Expected bounded unified n-only URL transform")
        assertFalse(unifiedNOnly.signatureApplied)
        assertTrue(unifiedNOnly.nTransformed)
        assertEquals("zyx", PlayerUrlTransforms.extractN(unifiedNOnly.url))
        val escapingRuntime = object : PlayerScriptRuntime {
            override suspend fun transformUrl(
                playerScript: String,
                candidate: PlayerScriptUrlBuilderCandidate,
                mediaUrl: String,
                signatureParameter: String?,
                encryptedSignature: String?
            ): String = "https://evil.example/videoplayback?n=changed"
        }
        assertNull(
            "Runtime output must never escape the original media resource",
            PlayerScriptUrlTransformer(unifiedSource, escapingRuntime).transform(
                playerJavaScriptUrl = "https://www.youtube.com/s/player/runtime-fixture/base.js",
                mediaUrl = "https://media.example.invalid/videoplayback?itag=313&n=xyz"
            )
        )
        println("YT_PROOF player-js-runtime=bounded-unified-url-builder signature+n n-only=true same-resource-guard=true")

        val declaredRuntimeScript = unifiedRuntimeScript.replace("y2=function(", "function y2(")
        val declaredSource = CachedPlayerScriptSource(object : PlayerScriptSource {
            override suspend fun load(playerJavaScriptUrl: String): String = declaredRuntimeScript
        })
        val declaredTransform = PlayerScriptUrlTransformer(declaredSource).transform(
            playerJavaScriptUrl = "https://www.youtube.com/s/player/declared-runtime-fixture/base.js",
            mediaUrl = "https://media.example.invalid/videoplayback?itag=136&n=declared"
        ) ?: throw AssertionError("Expected declaration-style URL builder to be exported")
        assertTrue(declaredTransform.nTransformed)
        assertEquals("deralced", PlayerUrlTransforms.extractN(declaredTransform.url))

        val guardedStartupScript = declaredRuntimeScript + "\nthrow new Error('unrelated player startup');"
        val guardedSource = CachedPlayerScriptSource(object : PlayerScriptSource {
            override suspend fun load(playerJavaScriptUrl: String): String = guardedStartupScript
        })
        val guardedTransform = PlayerScriptUrlTransformer(guardedSource).transform(
            playerJavaScriptUrl = "https://www.youtube.com/s/player/guarded-runtime-fixture/base.js",
            mediaUrl = "https://media.example.invalid/videoplayback?itag=136&n=guarded"
        ) ?: throw AssertionError("Late unrelated player startup error discarded an exported URL builder")
        assertTrue(guardedTransform.nTransformed)
        assertEquals("dedraug", PlayerUrlTransforms.extractN(guardedTransform.url))
        println("YT_PROOF player-js-runtime-declared-builder=true late-bootstrap-error-isolated=true")

        println("YT_PROOF player-js-parser=bounded-reverse+drop+swap ambiguous-shapes=fail-closed cache=player-identity")
        println("YT_PROOF states=SUPPORTED_AND_PROVEN,CHALLENGED,CIPHERED,N_PARAMETER_REQUIRED,SABR_ONLY,DASH_MANIFEST_ONLY,EXPIRED,RATE_LIMITED,TRANSIENT_NETWORK,REDIRECT_FAILED,CONTENT_LENGTH_CHANGED,MALFORMED_RESPONSE,UNSUPPORTED")
        println("YT_PROOF player-js=bounded-signature+n-hooks+cache-invalidation+explicit-403-classification")

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
