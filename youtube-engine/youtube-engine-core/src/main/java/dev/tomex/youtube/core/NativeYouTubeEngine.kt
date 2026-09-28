package dev.tomex.youtube.core

import dev.tomex.youtube.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/** Original on-device Innertube implementation. Client strategies may be replaced independently. */
class NativeYouTubeEngine(
    private val session: SessionProvider = AnonymousSession,
    private val playerScriptSource: PlayerScriptSource = CachedPlayerScriptSource(HttpPlayerScriptSource()),
    private val playerUrlTransformer: PlayerUrlTransformer =
        PlayerScriptUrlTransformer(playerScriptSource),
    private val nParameterTransformer: NParameterTransformer =
        CachedNParameterTransformer(PlayerScriptNParameterTransformer(playerScriptSource)),
    private val strategies: List<ClientStrategy> = listOf(
        ClientStrategy("ANDROID_VR", "1.60.19", "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 9; en_US; Oculus Quest) gzip"),
        ClientStrategy("IOS", "21.37.2", "com.google.ios.youtube/21.37.2 (iPhone16,2; iOS 18.0; en_US)"),
        ClientStrategy("WEB", "auto", "Mozilla/5.0")
    ),
    private val signatureCipherDecipherer: SignatureCipherDecipherer =
        CachedSignatureCipherDecipherer(PlayerScriptSignatureDecipherer(playerScriptSource))
) : YouTubeEngine {
    suspend fun currentPlayerScriptDiagnostics(): CurrentPlayerScriptDiagnostics {
        val config = bootstrap()
        val playerJavaScriptUrl = config.playerJavaScriptUrl
            ?: throw ResolverFailure.MalformedResponse("Bootstrap omitted the current player JavaScript URL")
        val script = playerScriptSource.load(playerJavaScriptUrl)
            ?: throw ResolverFailure.NetworkFailure("Current player JavaScript could not be loaded")
        return CurrentPlayerScriptDiagnostics(
            playerJavaScriptUrl = playerJavaScriptUrl,
            scriptBytes = script.toByteArray(Charsets.UTF_8).size,
            nParameter = PlayerScriptNParameterParser.inspect(script),
            signaturePlanAvailable = PlayerScriptSignatureParser.parse(script) != null,
            signatureTimestamp = PlayerScriptMetadataParser.signatureTimestamp(script)
        )
    }

    override suspend fun search(query: String, continuation: String?): Page<SearchResult> {
        require(query.isNotBlank())
        val decodedContinuation = decodeContinuation(continuation)
        var config = bootstrap()
        var root: JSONObject? = null
        var responseStrategy: ClientStrategy? = null
        var lastFailure: ResolverFailure? = null
        val diagnostics = mutableListOf<String>()
        decodedContinuation?.strategy?.let { diagnostics += "continuation pinned to ${it.name}/${it.version}" }
        val candidates = decodedContinuation?.strategy?.let { listOf(it) } ?: searchStrategies()
        for (candidate in candidates) {
            val strategy = if (candidate.name == "WEB" && candidate.version == "auto") config.client else candidate
            try {
                val body = JSONObject().put("context", context(strategy))
                if (decodedContinuation == null) body.put("query", query) else body.put("continuation", decodedContinuation.token)
                val candidateResponse = post("search", body, config.copy(client = strategy))
                if (candidateResponse.has("error")) {
                    val error = candidateResponse.optJSONObject("error")
                    throw ResolverFailure.PlayerResponseFailure("${strategy.name} search error ${error?.optInt("code")}: ${error?.optString("message")?.take(160)}")
                }
                root = candidateResponse
                responseStrategy = strategy
                val recognized = hasRecognizedSearchResult(candidateResponse)
                diagnostics += "${strategy.name}/${strategy.version}: recognizedResults=$recognized"
                if (recognized) break
                lastFailure = ResolverFailure.MalformedResponse("${strategy.name} search response contained no recognized result renderers")
            } catch (e: ResolverFailure) {
                lastFailure = e
                diagnostics += "${strategy.name}: ${e.javaClass.simpleName}: ${e.message}"
                // A 429 is normally scoped to the visitor/IP/session, not an individual client.
                // Rotating identities immediately only adds load and can deepen the throttle.
                if (e is ResolverFailure.RateLimited) break
                if (e is ResolverFailure.NetworkFailure && isBootstrapStale(e)) {
                    runCatching { bootstrap(force = true) }.getOrNull()?.let { config = it }
                }
            }
        }
        val response = root ?: throw (lastFailure ?: ResolverFailure.PlayerResponseFailure("No search client returned a response"))
        val output = mutableListOf<SearchResult>()
        walk(response) { node ->
            node.optJSONObject("videoRenderer")?.let { video ->
                val id = video.optString("videoId")
                if (id.isNotBlank()) output += SearchResult.Video(id, label(video.optJSONObject("title")),
                    label(video.optJSONObject("ownerText")), thumb(video), duration(video.optJSONObject("lengthText")))
            }
            node.optJSONObject("channelRenderer")?.let { channel ->
                val id = channel.optString("channelId")
                if (id.isNotBlank()) output += SearchResult.Channel(id, label(channel.optJSONObject("title")), thumb(channel))
            }
            node.optJSONObject("playlistRenderer")?.let { playlist ->
                val id = playlist.optString("playlistId")
                if (id.isNotBlank()) output += SearchResult.Playlist(id, label(playlist.optJSONObject("title")), thumb(playlist))
            }
        }
        var nextToken: String? = null
        walk(response) { node ->
            if (nextToken == null) nextToken = node.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }
        }
        if (output.isEmpty() && lastFailure is ResolverFailure.RateLimited) throw lastFailure
        if (output.isEmpty() && lastFailure != null) diagnostics += "all attempted clients returned no parseable results"
        val next = nextToken?.let { token -> encodeContinuation(token, responseStrategy ?: config.client) }
        return Page(output.distinctBy { result ->
            when (result) {
                is SearchResult.Video -> "video:${result.id}"
                is SearchResult.Channel -> "channel:${result.id}"
                is SearchResult.Playlist -> "playlist:${result.id}"
            }
        }, next, diagnostics)
    }

    override suspend fun videoDetails(videoId: String): VideoDetails {
        checkId(videoId)
        var config = bootstrap()
        val errors = mutableListOf<String>()
        val failures = mutableListOf<ResolverFailure>()
        for (candidate in strategies.take(4)) {
            val strategy = if (candidate.name == "WEB" && candidate.version == "auto") config.client else candidate
            try {
                val root = player(videoId, config.copy(client = strategy))
                val details = root.optJSONObject("videoDetails")
                if (details != null) return videoDetailsFrom(videoId, details, root)
                errors += "${strategy.name}: no videoDetails"
                val status = root.optJSONObject("playabilityStatus")
                if (status != null) {
                    val failure = PlayerResponseClassifier.failure(status)
                    failures += failure
                    errors += "${failure.javaClass.simpleName}: ${failure.message.orEmpty().take(120)}"
                    if (failure is ResolverFailure.RateLimited) break
                }
            } catch (e: ResolverFailure) {
                failures += e
                errors += "${strategy.name}: ${e.javaClass.simpleName}"
                if (e is ResolverFailure.RateLimited) break
                if (e is ResolverFailure.NetworkFailure && isBootstrapStale(e))
                    runCatching { bootstrap(force = true) }.getOrNull()?.let { config = it }
            }
        }
        if (failures.any { it is ResolverFailure.RateLimited })
            throw ResolverFailure.RateLimited(errors.joinToString("; ").take(700))
        try {
            val root = watchPageDetails(videoId)
            root.optJSONObject("videoDetails")?.let { return videoDetailsFrom(videoId, it, root) }
        } catch (e: ResolverFailure) {
            failures += e
            errors += "watch-page: ${e.javaClass.simpleName}"
        }
        val summary = errors.joinToString("; ").take(700)
        if (failures.any { it is ResolverFailure.ChallengeRequired }) throw ResolverFailure.ChallengeRequired(summary)
        if (failures.any { it is ResolverFailure.SignInRequired }) throw ResolverFailure.SignInRequired(summary)
        if (failures.any { it is ResolverFailure.RateLimited }) throw ResolverFailure.RateLimited(summary)
        throw when {
            failures.all { it is ResolverFailure.VideoUnavailable } && failures.isNotEmpty() -> ResolverFailure.VideoUnavailable(summary)
            failures.all { it is ResolverFailure.MalformedResponse } && failures.isNotEmpty() -> ResolverFailure.MalformedResponse(summary)
            else -> ResolverFailure.PlayerResponseFailure(summary)
        }
    }

    private fun videoDetailsFrom(videoId: String, details: JSONObject, root: JSONObject) = VideoDetails(
        videoId, details.optString("title"), details.optString("author"), details.optString("channelId"),
        details.optString("shortDescription"), details.optString("lengthSeconds").toLongOrNull(),
        details.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.strings("url") ?: emptyList(),
        chapters = DescriptionChapterParser.parse(details.optString("shortDescription")), subtitles = captionTracks(root, videoId)
    )

    private suspend fun watchPageDetails(videoId: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL("https://www.youtube.com/watch?v=$videoId").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 15000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        applySessionContext(connection)
        try {
            val status = connection.responseCode
            storeSessionCookies(connection)
            if (status == 429) throw ResolverFailure.RateLimited("Watch page HTTP 429")
            if (status !in 200..299) throw ResolverFailure.NetworkFailure("Watch page HTTP $status")
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val marker = Regex("ytInitialPlayerResponse\\s*=\\s*").find(html)
                ?: throw ResolverFailure.PlayerResponseFailure("Watch page has no initial player response")
            val value = try {
                JSONTokener(html.substring(marker.range.last + 1)).nextValue()
            } catch (e: JSONException) {
                throw ResolverFailure.PlayerResponseFailure("Watch page player response is malformed")
            }
            value as? JSONObject ?: throw ResolverFailure.PlayerResponseFailure("Watch page player response is not an object")
        } catch (e: java.io.IOException) {
            throw ResolverFailure.NetworkFailure("Watch page I/O: " + (e.message ?: "read failure").take(160))
        } finally { connection.disconnect() }
    }

    override suspend fun resolve(videoId: String): PlaybackDescriptor {
        checkId(videoId)
        val diagnostics = mutableListOf<String>()
        val failures = mutableListOf<ResolverFailure>()
        var config = bootstrap()
        for (candidate in strategies.take(4)) {
            val strategy = if (candidate.version == "auto" && candidate.name == "WEB") config.client else candidate
            try {
                val root = post("player", JSONObject().put("context", context(strategy)).put("videoId", videoId)
                    .put("contentCheckOk", true).put("racyCheckOk", true), config.copy(client = strategy))
                val status = root.optJSONObject("playabilityStatus")
                if (status?.optString("status") != "OK") {
                    val reason = status?.optString("reason") ?: "Missing playability status"
                    diagnostics += "${strategy.name}: ${status?.optString("status")} $reason"
                    failures += PlayerResponseClassifier.failure(status)
                    if (failures.last() is ResolverFailure.RateLimited) break
                    continue
                }
                val streaming = root.optJSONObject("streamingData")
                val expiry = streaming?.optLong("expiresInSeconds")?.takeIf { it > 0 }?.let { System.currentTimeMillis() / 1000 + it }
                val all = listOf("formats", "adaptiveFormats").sumOf { streaming?.optJSONArray(it)?.length() ?: 0 }
                var ciphered = 0
                var validCiphered = 0
                for (name in listOf("formats", "adaptiveFormats")) {
                    val array = streaming?.optJSONArray(name) ?: JSONArray()
                    for (index in 0 until array.length()) {
                        val format = array.optJSONObject(index)
                        if (PlayerResponseClassifier.hasCipherParameters(format)) {
                            ciphered++
                            if (PlayerUrlTransforms.cipherParameters(format) != null) validCiphered++
                        }
                    }
                }
                val malformedCiphered = ciphered - validCiphered
                // A bootstrap script is the WEB player's script. Never assume it governs another
                // client; non-WEB responses must advertise their own player JavaScript identity.
                val playerJavaScriptUrl = PlayerUrlTransforms.playerJavaScriptUrl(root)
                    ?: config.playerJavaScriptUrl.takeIf { strategy.name == "WEB" }
                val formats = mutableListOf<MediaFormat>()
                for (name in listOf("formats", "adaptiveFormats")) {
                    val array = streaming?.optJSONArray(name) ?: JSONArray()
                    for (index in 0 until array.length()) {
                        parseFormat(array.optJSONObject(index), expiry, strategy, playerJavaScriptUrl)?.let(formats::add)
                    }
                }
                val recoveredCiphered = formats.count { it.signatureDeciphered }
                val pendingN = formats.count { it.nParameterNeedsTransform }
                val transformedN = formats.count { it.nSigTransformed }
                if (formats.isNotEmpty()) return PlaybackDescriptor(videoId, formats, strategy.name,
                    diagnostics + "${strategy.name}: ${formats.size} URL formats; excluded ciphered=${ciphered - recoveredCiphered} recovered=$recoveredCiphered valid=$validCiphered malformed=$malformedCiphered; n pending=$pendingN transformed=$transformedN playerJs=${playerJavaScriptUrl != null}",
                    captionTracks(root, videoId))
                val failure = when {
                    malformedCiphered > 0 && validCiphered == 0 ->
                        ResolverFailure.MalformedResponse("$malformedCiphered ciphered formats had malformed signature metadata")
                    else -> PlayerResponseClassifier.deliveryFailure(streaming, all, validCiphered)
                }
                failures += failure
                diagnostics += "${strategy.name}: ${failure.javaClass.simpleName}: ${failure.message}"
            } catch (e: ResolverFailure) {
                failures += e
                diagnostics += "${strategy.name}: ${e.javaClass.simpleName}: ${e.message}"
                if (e is ResolverFailure.RateLimited) break
                if (e is ResolverFailure.NetworkFailure && isBootstrapStale(e)) {
                    runCatching { bootstrap(force = true) }.getOrNull()?.let { config = it }
                }
            }
        }
        val summary = diagnostics.joinToString("; ").take(800)
        throw when {
            failures.any { it is ResolverFailure.ChallengeRequired } -> ResolverFailure.ChallengeRequired(summary)
            failures.any { it is ResolverFailure.SignInRequired } -> ResolverFailure.SignInRequired(summary)
            failures.any { it is ResolverFailure.RateLimited } -> ResolverFailure.RateLimited(summary)
            failures.any { it is ResolverFailure.SabrOnly } -> ResolverFailure.SabrOnly(summary)
            failures.any { it is ResolverFailure.DashManifestOnly } -> ResolverFailure.DashManifestOnly(summary)
            failures.any { it is ResolverFailure.Ciphered } -> ResolverFailure.Ciphered(summary)
            failures.all { it is ResolverFailure.VideoUnavailable } && failures.isNotEmpty() -> ResolverFailure.VideoUnavailable(summary)
            failures.all { it is ResolverFailure.MalformedResponse } && failures.isNotEmpty() -> ResolverFailure.MalformedResponse(summary)
            else -> ResolverFailure.NoPlayableFormats(summary)
        }
    }

    override suspend fun resolveVerified(videoId: String, minimumHeight: Int): VerifiedPlayback {
        val descriptor = resolve(videoId)
        val selection = descriptor.selectAdaptive(minimumHeight) ?: run {
            val blocked = descriptor.formats.firstNotNullOfOrNull(TransportReadiness::failure)
            if (blocked != null) throw blocked
            throw ResolverFailure.NoPlayableFormats("No transport-ready container-compatible adaptive pair at ${minimumHeight}p+")
        }
        val videoProof = probe(selection.video)
        val audioProof = probe(selection.audio)
        return VerifiedPlayback(descriptor, selection, videoProof, audioProof)
    }

    override suspend fun fetchSubtitle(track: SubtitleTrack, byteLimit: Int): SubtitleProof = withContext(Dispatchers.IO) {
        require(byteLimit in 1..1_000_000)
        track.expiresAtEpochSeconds?.takeIf { it <= System.currentTimeMillis() / 1000 + 5 }?.let {
            throw ResolverFailure.MediaUrlExpired("Subtitle URL expired; refresh by stableIdentity")
        }
        val connection = (URL(track.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 15000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", strategies.first().userAgent)
        }
        applySessionContext(connection, includeVisitorData = false)
        try {
            val status = connection.responseCode
            storeSessionCookies(connection)
            if (status == 429) throw ResolverFailure.RateLimited("Subtitle request returned HTTP 429")
            if (status == 403 || status == 410) throw ResolverFailure.MediaUrlExpired("Subtitle request returned $status")
            if (status !in 200..299) throw ResolverFailure.NetworkFailure("Subtitle request returned HTTP $status")
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (output.size() < byteLimit) {
                    val count = input.read(buffer, 0, minOf(buffer.size, byteLimit - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val contentType = connection.contentType
            if (bytes.isEmpty() || contentType?.contains("html", true) == true || bytes.toString(Charsets.UTF_8).trimStart().startsWith("<html", true))
                throw ResolverFailure.NetworkFailure("Subtitle endpoint returned no caption data")
            SubtitleProof(status, bytes.size, contentType)
        } catch (e: java.io.IOException) {
            throw ResolverFailure.NetworkFailure("Subtitle I/O: " + (e.message ?: "read failure").take(160))
        } finally { connection.disconnect() }
    }

    override suspend fun fetchSubtitleWithRefresh(
        videoId: String, track: SubtitleTrack, byteLimit: Int
    ): SubtitleProof {
        checkId(videoId)
        return try {
            fetchSubtitle(track, byteLimit)
        } catch (_: ResolverFailure.MediaUrlExpired) {
            val refreshed = videoDetails(videoId).subtitles.firstOrNull { it.stableIdentity == track.stableIdentity }
                ?: throw ResolverFailure.NoPlayableFormats("Subtitle ${track.stableIdentity} is no longer offered")
            fetchSubtitle(refreshed, byteLimit)
        }
    }

    override suspend fun refreshMedia(videoId: String, stableFormatIdentity: String): MediaFormat =
        resolve(videoId).formats.firstOrNull { it.stableIdentity == stableFormatIdentity }
            ?: throw ResolverFailure.NoPlayableFormats("Format $stableFormatIdentity is no longer offered")

    override suspend fun probe(format: MediaFormat, byteLimit: Int): TransportProof = probeRange(format, 0, byteLimit)

    /** Resumes the same stable representation after its signed CDN URL expires. */
    override suspend fun probeRangeWithRefresh(
        videoId: String,
        format: MediaFormat,
        startByte: Long,
        byteLimit: Int
    ): RefreshedTransportProof {
        require(format.stableIdentity.isNotBlank())
        return try {
            RefreshedTransportProof(format, probeRange(format, startByte, byteLimit), refreshed = false)
        } catch (_: ResolverFailure.MediaUrlExpired) {
            val refreshed = refreshMedia(videoId, format.stableIdentity)
            RefreshedTransportProof(refreshed, probeRange(refreshed, startByte, byteLimit), refreshed = true)
        }
    }

    override suspend fun fetchChunkWithRefresh(
        videoId: String,
        format: MediaFormat,
        startByte: Long,
        byteLimit: Int
    ): MediaChunk {
        require(format.stableIdentity.isNotBlank())
        return try {
            val chunk = readMediaRange(format, startByte, byteLimit, minimumBytes = 1)
            MediaChunk(format, startByte, chunk.bytes, chunk.totalBytes, chunk.proof.contentRange, refreshed = false)
        } catch (_: ResolverFailure.MediaUrlExpired) {
            val refreshed = refreshMedia(videoId, format.stableIdentity)
            val chunk = readMediaRange(refreshed, startByte, byteLimit, minimumBytes = 1)
            MediaChunk(refreshed, startByte, chunk.bytes, chunk.totalBytes, chunk.proof.contentRange, refreshed = true)
        }
    }

    override suspend fun fetchChunkFromCheckpoint(
        checkpoint: MediaTransferCheckpoint,
        byteLimit: Int
    ): MediaChunk {
        checkId(checkpoint.videoId)
        require(checkpoint.stableFormatIdentity.isNotBlank())
        require(checkpoint.nextByteOffset >= 0)
        checkpoint.totalBytes?.let {
            require(it > 0)
            if (checkpoint.nextByteOffset >= it)
                throw ResolverFailure.UnsupportedDelivery("Checkpoint is already at or beyond end of media")
        }
        val refreshed = refreshMedia(checkpoint.videoId, checkpoint.stableFormatIdentity)
        ResumeIntegrity.failure(
            checkpointTotalBytes = checkpoint.totalBytes,
            descriptorContentLength = refreshed.contentLength,
            responseTotalBytes = null,
            startByte = checkpoint.nextByteOffset,
            responseTotalRequired = false
        )?.let { throw it }
        val chunk = readMediaRange(
            refreshed,
            checkpoint.nextByteOffset,
            byteLimit,
            minimumBytes = 1,
            expectedTotalBytes = checkpoint.totalBytes
        )
        return MediaChunk(
            refreshed,
            checkpoint.nextByteOffset,
            chunk.bytes,
            chunk.totalBytes,
            chunk.proof.contentRange,
            refreshed = true
        )
    }

    override suspend fun probeRange(format: MediaFormat, startByte: Long, byteLimit: Int): TransportProof =
        readMediaRange(format, startByte, byteLimit).proof

    private data class RangeRead(val proof: TransportProof, val bytes: ByteArray, val totalBytes: Long?)

    private suspend fun readMediaRange(
        format: MediaFormat,
        startByte: Long,
        byteLimit: Int,
        minimumBytes: Int = 512,
        expectedTotalBytes: Long? = null
    ): RangeRead {
        var lastFailure: ResolverFailure.TransientNetworkFailure? = null
        for (attempt in 1..3) {
            try {
                return readMediaRangeOnce(format, startByte, byteLimit, minimumBytes, expectedTotalBytes)
            } catch (e: ResolverFailure.TransientNetworkFailure) {
                lastFailure = e
                if (attempt == 3) throw e
                delay(150L * attempt)
            }
        }
        throw lastFailure ?: ResolverFailure.TransientNetworkFailure("CDN retry budget exhausted")
    }

    private suspend fun readMediaRangeOnce(
        format: MediaFormat,
        startByte: Long,
        byteLimit: Int,
        minimumBytes: Int,
        expectedTotalBytes: Long?
    ): RangeRead = withContext(Dispatchers.IO) {
        require(byteLimit in 1..4_194_304)
        require(minimumBytes in 1..512)
        require(startByte >= 0 && startByte <= Long.MAX_VALUE - byteLimit)
        expectedTotalBytes?.let { require(it > 0) }
        val coroutineContext = currentCoroutineContext()
        coroutineContext.ensureActive()
        TransportReadiness.failure(format)?.let { throw it }
        val expiry = format.expiresAtEpochSeconds
        if (expiry != null && expiry <= System.currentTimeMillis() / 1000 + 30)
            throw ResolverFailure.MediaUrlExpired("Descriptor expired; refresh by stableIdentity")
        var currentUrl = format.url
        var connection: HttpURLConnection? = null
        var redirectCount = 0
        try {
            while (true) {
                coroutineContext.ensureActive()
                val candidate = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 12000
                    instanceFollowRedirects = false
                    setRequestProperty("Range", "bytes=$startByte-${startByte + byteLimit - 1}")
                    format.requiredHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
                }
                connection = candidate
                val candidateStatus = candidate.responseCode
                if (!MediaRedirectPolicy.isRedirect(candidateStatus)) break
                val location = candidate.getHeaderField("Location")
                val next = MediaRedirectPolicy.nextUrl(currentUrl, location)
                    ?: throw ResolverFailure.RedirectFailure("Unsafe or malformed CDN redirect")
                candidate.disconnect()
                connection = null
                redirectCount++
                if (redirectCount > 5) throw ResolverFailure.RedirectFailure("CDN redirect limit exceeded")
                currentUrl = next
            }
            val activeConnection = connection ?: throw ResolverFailure.NetworkFailure("CDN connection was not established")
            val status = activeConnection.responseCode
            storeSessionCookies(activeConnection)
            PlayerResponseClassifier.mediaHttpFailure(
                status = status,
                nParameterNeedsTransform = format.nParameterNeedsTransform,
                expiresAtEpochSeconds = format.expiresAtEpochSeconds,
                nowEpochSeconds = System.currentTimeMillis() / 1000
            )?.let { throw it }
            if (startByte > 0 && status != 206) throw ResolverFailure.UnsupportedDelivery("CDN ignored resume byte range")
            val range = activeConnection.getHeaderField("Content-Range")
            val rangeMatch = if (status == 206) Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(range.orEmpty()) else null
            if (status == 206 && (rangeMatch == null || rangeMatch.groupValues[1].toLongOrNull() != startByte))
                throw ResolverFailure.UnsupportedDelivery("CDN returned wrong resume range: $range")
            val output = java.io.ByteArrayOutputStream(minOf(byteLimit, 65536))
            activeConnection.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (output.size() < byteLimit) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer, 0, minOf(buffer.size, byteLimit - output.size()))
                    if (read < 0) break
                    if (read > 0) output.write(buffer, 0, read)
                }
            }
            val bytes = output.toByteArray()
            val contentType = activeConnection.contentType ?: ""
            if (contentType.startsWith("text/") || contentType.contains("html", ignoreCase = true))
                throw ResolverFailure.NetworkFailure("CDN returned non-media data: HTTP $status, type=$contentType, bytes=${bytes.size}")
            if (bytes.size < minimumBytes)
                throw ResolverFailure.TransientNetworkFailure("CDN returned a short media body: HTTP $status, bytes=${bytes.size}")
            val rangeEnd = rangeMatch?.groupValues?.get(2)?.toLongOrNull()
            if (rangeEnd != null && bytes.size.toLong() != rangeEnd - startByte + 1)
                throw ResolverFailure.TransientNetworkFailure("CDN range body was truncated: header ends at $rangeEnd, received ${bytes.size} bytes")
            val responseTotal = rangeMatch?.groupValues?.get(3)?.toLongOrNull()
            ResumeIntegrity.failure(
                checkpointTotalBytes = expectedTotalBytes,
                descriptorContentLength = format.contentLength,
                responseTotalBytes = responseTotal,
                startByte = startByte,
                responseTotalRequired = expectedTotalBytes != null && startByte > 0
            )?.let { throw it }
            val total = responseTotal ?: format.contentLength?.takeIf { status == 200 }
            if (total != null && startByte + bytes.size > total)
                throw ResolverFailure.UnsupportedDelivery("CDN chunk extends beyond declared media length")
            val proof = TransportProof(activeConnection.url.host, status, bytes.size, range, activeConnection.contentLengthLong.takeIf { it >= 0 }, startByte)
            RangeRead(proof, bytes, total)
        } catch (e: java.io.IOException) {
            throw ResolverFailure.TransientNetworkFailure("CDN I/O: " + (e.message ?: "read failure").take(160))
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun player(videoId: String, config: Bootstrap): JSONObject = post("player",
        JSONObject().put("context", context(config.client)).put("videoId", videoId).put("contentCheckOk", true).put("racyCheckOk", true), config)

    private fun context(strategy: ClientStrategy): JSONObject {
        val client = JSONObject().put("clientName", strategy.name).put("clientVersion", strategy.version).put("hl", "en").put("gl", "US")
        when (strategy.name) {
            "ANDROID_VR" -> client.put("androidSdkVersion", 28).put("osName", "Android").put("osVersion", "9")
            "IOS" -> client.put("deviceMake", "Apple").put("deviceModel", "iPhone16,2").put("osName", "iOS").put("osVersion", "18.0")
        }
        return JSONObject().put("client", client)
    }

    private data class Bootstrap(val key: String, val client: ClientStrategy, val playerJavaScriptUrl: String?)
    @Volatile private var cachedBootstrap: Bootstrap? = null
    @Volatile private var bootstrapAtMs: Long = 0
    private suspend fun bootstrap(force: Boolean = false): Bootstrap {
        if (!force) cachedBootstrap?.takeIf { System.currentTimeMillis() - bootstrapAtMs < 15 * 60_000 }?.let { return it }
        return withContext(Dispatchers.IO) {
        val connection = (URL("https://www.youtube.com/").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 15000; setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        applySessionContext(connection)
        try {
            val status = connection.responseCode
            storeSessionCookies(connection)
            if (status == 429) throw ResolverFailure.RateLimited("YouTube bootstrap HTTP 429")
            if (status !in 200..299) throw ResolverFailure.NetworkFailure("Bootstrap HTTP $status")
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val key = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                ?: throw ResolverFailure.PlayerResponseFailure("Missing current Innertube key")
            val version = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                ?: throw ResolverFailure.PlayerResponseFailure("Missing current web client version")
            val rawPlayerJavaScriptUrl = sequenceOf(
                Regex("\"jsUrl\":\"([^\"]+)\""),
                Regex("\"PLAYER_JS_URL\":\"([^\"]+)\"")
            ).mapNotNull { it.find(html)?.groupValues?.get(1) }.firstOrNull()
            val playerJavaScriptUrl = rawPlayerJavaScriptUrl?.let(PlayerUrlTransforms::normalizePlayerJavaScriptUrl)
            Bootstrap(key, ClientStrategy("WEB", version, "Mozilla/5.0"), playerJavaScriptUrl)
                .also { cachedBootstrap = it; bootstrapAtMs = System.currentTimeMillis() }
        } catch (e: java.io.IOException) {
            cachedBootstrap = null
            throw ResolverFailure.NetworkFailure("Bootstrap I/O: " + (e.message ?: "read failure").take(160))
        } finally { connection.disconnect() }
        }
    }

    private fun isBootstrapStale(failure: ResolverFailure.NetworkFailure): Boolean =
        failure.message?.let { "Innertube HTTP 400" in it || "Innertube HTTP 403" in it } == true

    private fun searchStrategies(): List<ClientStrategy> = strategies.take(4).sortedBy {
        when (it.name) { "WEB" -> 0; "IOS" -> 1; "ANDROID_VR" -> 2; else -> 3 }
    }

    private data class DecodedContinuation(val token: String, val strategy: ClientStrategy?)

    private fun encodeContinuation(token: String, strategy: ClientStrategy): String {
        val payload = JSONObject()
            .put("token", token)
            .put("client", strategy.name)
            .put("version", strategy.version)
            .put("userAgent", strategy.userAgent)
            .toString()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
        return "ytc1:$encoded"
    }

    private fun decodeContinuation(value: String?): DecodedContinuation? {
        if (value == null) return null
        if (!value.startsWith("ytc1:")) return DecodedContinuation(value, null)
        return try {
            val payload = String(Base64.getUrlDecoder().decode(value.removePrefix("ytc1:")), Charsets.UTF_8)
            val json = JSONObject(payload)
            val token = json.optString("token")
            val name = json.optString("client")
            val version = json.optString("version")
            val userAgent = json.optString("userAgent")
            if (token.isBlank() || name.isBlank() || version.isBlank() || userAgent.isBlank())
                throw ResolverFailure.PlayerResponseFailure("Malformed engine continuation token")
            DecodedContinuation(token, ClientStrategy(name, version, userAgent))
        } catch (e: ResolverFailure) {
            throw e
        } catch (_: Exception) {
            throw ResolverFailure.PlayerResponseFailure("Malformed engine continuation token")
        }
    }

    private suspend fun post(endpoint: String, body: JSONObject, config: Bootstrap): JSONObject = withContext(Dispatchers.IO) {
        val strategy = config.client
        val connection = (URL("https://www.youtube.com/youtubei/v1/$endpoint?key=${config.key}&prettyPrint=false").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 10000; readTimeout = 15000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", strategy.userAgent)
            setRequestProperty("X-YouTube-Client-Name", when (strategy.name) { "ANDROID_VR" -> "28"; "IOS" -> "5"; else -> "1" })
            setRequestProperty("X-YouTube-Client-Version", strategy.version)
        }
        applySessionContext(connection)
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            storeSessionCookies(connection)
            if (status == 429) throw ResolverFailure.RateLimited("Innertube HTTP 429")
            if (status !in 200..299) {
                if (status == 400 || status == 403) cachedBootstrap = null
                throw ResolverFailure.NetworkFailure("Innertube HTTP $status")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val response = try {
                JSONObject(body)
            } catch (e: JSONException) {
                throw ResolverFailure.PlayerResponseFailure("Innertube $endpoint response is malformed JSON")
            }
            response.optJSONObject("error")?.let { throw PlayerResponseClassifier.innertubeFailure(it) }
            response
        } catch (e: java.io.IOException) {
            throw ResolverFailure.NetworkFailure(e.javaClass.simpleName + ": " + (e.message ?: "I/O failure").take(160))
        } finally { connection.disconnect() }
    }

    private suspend fun applySessionContext(connection: HttpURLConnection, includeVisitorData: Boolean = true) {
        val requestUrl = connection.url.toString()
        if (!SessionRequestPolicy.allowsSessionOrigin(requestUrl)) return
        if (includeVisitorData) {
            session.visitorData()?.takeIf(SessionRequestPolicy::isSafeHeaderValue)?.let {
                connection.setRequestProperty("X-Goog-Visitor-Id", it)
            }
        }
        val scoped = SessionRequestPolicy.sanitize(requestUrl, session.requestHeaders(requestUrl))
        scoped.forEach { (key, value) -> connection.setRequestProperty(key, value) }
    }

    private suspend fun storeSessionCookies(connection: HttpURLConnection) {
        val cookies = connection.headerFields.entries
            .filter { (name, _) -> name?.equals("Set-Cookie", ignoreCase = true) == true }
            .flatMap { (_, values) -> values.orEmpty() }
        if (cookies.isNotEmpty()) session.storeResponseCookies(connection.url.toString(), cookies)
    }
    private suspend fun parseFormat(
        value: JSONObject?,
        expiry: Long?,
        strategy: ClientStrategy,
        playerJavaScriptUrl: String?
    ): MediaFormat? {
        if (value == null) return null
        val signatureCipherPresent = PlayerResponseClassifier.hasCipherParameters(value)
        val cipher = if (signatureCipherPresent) PlayerUrlTransforms.cipherParameters(value) ?: return null else null
        val baseUrl = cipher?.mediaUrl
            ?: value.optString("url").takeIf { it.startsWith("https://") }
            ?: return null
        val originalN = PlayerUrlTransforms.extractN(baseUrl)

        // Current player revisions can couple signature and n rewriting inside one URL-builder path.
        // Try that bounded path first, then retain the legacy independent parsers as a fail-closed
        // fallback for player revisions whose transforms are still statically recognizable.
        val unified = if (playerJavaScriptUrl != null && (cipher != null || originalN != null)) {
            try {
                playerUrlTransformer.transform(
                    playerJavaScriptUrl = playerJavaScriptUrl,
                    mediaUrl = baseUrl,
                    signatureParameter = cipher?.signatureParameter,
                    encryptedSignature = cipher?.encryptedSignature
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } else null

        val unifiedSignature = cipher != null && unified?.signatureApplied == true
        val decipheredSignature = if (cipher != null && !unifiedSignature && playerJavaScriptUrl != null) {
            try {
                signatureCipherDecipherer.decipher(playerJavaScriptUrl, cipher.encryptedSignature)
                    ?.takeIf { it.isNotBlank() && it != cipher.encryptedSignature }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } else null

        val signatureReadyUrl = when {
            cipher != null && unifiedSignature -> unified?.url ?: return null
            cipher != null && decipheredSignature != null ->
                PlayerUrlTransforms.applySignature(cipher.mediaUrl, cipher.signatureParameter, decipheredSignature)
                    ?: return null
            cipher != null -> return null
            unified?.nTransformed == true -> unified.url
            else -> baseUrl
        }

        val currentN = PlayerUrlTransforms.extractN(signatureReadyUrl)
        val transformedN = if (
            currentN != null &&
            unified?.nTransformed != true &&
            playerJavaScriptUrl != null
        ) {
            try {
                nParameterTransformer.transform(playerJavaScriptUrl, currentN)
                    ?.takeIf { it.isNotBlank() && it != currentN }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } else null
        val url = transformedN?.let { PlayerUrlTransforms.replaceN(signatureReadyUrl, it) } ?: signatureReadyUrl
        val nTransformed = originalN != null && (unified?.nTransformed == true || transformedN != null)
        val signatureDeciphered = cipher != null && (unifiedSignature || decipheredSignature != null)
        val mime = value.optString("mimeType")
        val video = mime.startsWith("video/")
        val audio = mime.startsWith("audio/") || value.has("audioQuality")
        val itag = value.optInt("itag", -1)
        if (itag < 0) return null
        val codec = mime.substringAfter("codecs=\"", "").substringBefore('"').ifBlank { null }
        val container = mime.substringAfter('/').substringBefore(';').ifBlank { null }
        val width = value.optInt("width").takeIf { it > 0 }
        val height = value.optInt("height").takeIf { it > 0 }
        val fps = value.optInt("fps").takeIf { it > 0 }
        val bitrate = value.optLong("bitrate").takeIf { it > 0 }
        val audioChannels = value.optInt("audioChannels").takeIf { it > 0 }
        val audioSampleRate = value.optString("audioSampleRate").toIntOrNull()
        val identity = StableFormatIdentity.create(
            itag, video, audio, container, codec, width, height, fps, bitrate, audioChannels, audioSampleRate
        )
        val urlExpiry = Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
        return MediaFormat(identity, itag, url, mime, codec, container,
            width, height, fps, bitrate, value.optString("contentLength").toLongOrNull(),
            audioChannels, audioSampleRate,
            video, audio, if (video && audio) Delivery.PROGRESSIVE else Delivery.ADAPTIVE,
            mapOf("User-Agent" to strategy.userAgent), listOfNotNull(expiry, urlExpiry).minOrNull(),
            nSigParameterPresent = originalN != null,
            nSigTransformed = nTransformed,
            signatureCipherPresent = signatureCipherPresent,
            signatureDeciphered = signatureDeciphered)
    }

    private fun captionTracks(root: JSONObject, videoId: String): List<SubtitleTrack> {
        val tracks = root.optJSONObject("captions")?.optJSONObject("playerCaptionsTracklistRenderer")?.optJSONArray("captionTracks") ?: return emptyList()
        return (0 until tracks.length()).mapNotNull { index -> tracks.optJSONObject(index)?.let {
            val url = it.optString("baseUrl")
            val language = it.optString("languageCode")
            val name = label(it.optJSONObject("name"))
            val automatic = it.optString("kind") == "asr"
            val trackId = it.optString("vssId").takeIf(String::isNotBlank)
            val expiry = Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
            SubtitleTrack(language, name, url, automatic, videoId, trackId, expiry)
        } }
    }

    private fun checkId(videoId: String) { require(Regex("[a-zA-Z0-9_-]{11}").matches(videoId)) { "Invalid video ID" } }
    private fun hasRecognizedSearchResult(root: JSONObject): Boolean {
        var found = false
        walk(root) { node ->
            if (node.optJSONObject("videoRenderer")?.optString("videoId")?.isNotBlank() == true ||
                node.optJSONObject("channelRenderer")?.optString("channelId")?.isNotBlank() == true ||
                node.optJSONObject("playlistRenderer")?.optString("playlistId")?.isNotBlank() == true) found = true
        }
        return found
    }
    private fun label(value: JSONObject?): String = value?.optJSONArray("runs")?.let { runs ->
        (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text") ?: "" }
    }?.ifBlank { value.optString("simpleText") } ?: value?.optString("simpleText") ?: ""
    private fun thumb(value: JSONObject): String? = value.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
    private fun duration(value: JSONObject?): Int? = label(value).split(':').mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }?.fold(0) { total, part -> total * 60 + part }
    private fun JSONArray.strings(key: String) = (0 until length()).mapNotNull { optJSONObject(it)?.optString(key) }
    private fun walk(value: Any?, visit: (JSONObject) -> Unit) {
        when (value) {
            is JSONObject -> { visit(value); value.keys().forEach { walk(value.opt(it), visit) } }
            is JSONArray -> (0 until value.length()).forEach { walk(value.opt(it), visit) }
        }
    }
}

data class CurrentPlayerScriptDiagnostics(
    val playerJavaScriptUrl: String,
    val scriptBytes: Int,
    val nParameter: NParameterParserDiagnostics,
    val signaturePlanAvailable: Boolean,
    val signatureTimestamp: Int?
)

data class ClientStrategy(val name: String, val version: String, val userAgent: String)

/** Stable across signed URL refreshes and client fallback; never includes transient transport data. */
object StableFormatIdentity {
    fun create(
        itag: Int, hasVideo: Boolean, hasAudio: Boolean, container: String?, codecs: String?,
        width: Int?, height: Int?, fps: Int?, bitrate: Long?, audioChannels: Int?, audioSampleRate: Int?
    ): String {
        val kind = when {
            hasVideo && hasAudio -> "muxed"
            hasVideo -> "video"
            hasAudio -> "audio"
            else -> "unknown"
        }
        fun value(raw: Any?) = raw?.toString()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "-"
        return listOf(
            "v2", "itag=$itag", "kind=$kind", "container=${value(container)}", "codecs=${value(codecs)}",
            "width=${value(width)}", "height=${value(height)}", "fps=${value(fps)}", "bitrate=${value(bitrate)}",
            "channels=${value(audioChannels)}", "sampleRate=${value(audioSampleRate)}"
        ).joinToString("|")
    }
}

/** Pure classification of observed response stages; no URL is marked proven here. */
object TransportReadiness {
    fun failure(format: MediaFormat): ResolverFailure? = when {
        format.signatureCipherNeedsDecipher ->
            ResolverFailure.Ciphered("Format ${format.stableIdentity} still requires signature deciphering")
        format.nParameterNeedsTransform ->
            ResolverFailure.NParameterTransformRequired("Format ${format.stableIdentity} still requires n-parameter transformation")
        else -> null
    }
}

object MediaRedirectPolicy {
    private val statuses = setOf(301, 302, 303, 307, 308)

    fun isRedirect(status: Int): Boolean = status in statuses

    fun nextUrl(currentUrl: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        val current = runCatching { URL(currentUrl) }.getOrNull() ?: return null
        val next = runCatching { URL(current, location) }.getOrNull() ?: return null
        if (!next.protocol.equals("https", ignoreCase = true)) return null
        if (next.userInfo != null) return null
        return next.toString()
    }
}

object ResumeIntegrity {
    fun failure(
        checkpointTotalBytes: Long?,
        descriptorContentLength: Long?,
        responseTotalBytes: Long?,
        startByte: Long,
        responseTotalRequired: Boolean = false
    ): ResolverFailure? {
        if (startByte <= 0) return null
        if (checkpointTotalBytes != null && descriptorContentLength != null &&
            checkpointTotalBytes != descriptorContentLength
        ) {
            return ResolverFailure.ContentLengthChanged(
                "Resolved format length changed from $checkpointTotalBytes to $descriptorContentLength; refusing resume"
            )
        }
        val expected = checkpointTotalBytes ?: descriptorContentLength
        if (responseTotalRequired && expected != null && responseTotalBytes == null) {
            return ResolverFailure.UnsupportedDelivery(
                "CDN resume response omitted total media length; refusing unverified resume"
            )
        }
        if (expected != null && responseTotalBytes != null && expected != responseTotalBytes) {
            return ResolverFailure.ContentLengthChanged(
                "CDN media length changed from $expected to $responseTotalBytes; refusing resume"
            )
        }
        return null
    }
}

object PlayerResponseClassifier {
    /** Cipher metadata takes precedence even if a response also contains an unsigned URL field. */
    fun hasCipherParameters(format: JSONObject?): Boolean =
        format?.has("signatureCipher") == true || format?.has("cipher") == true

    /** Detects the URL throttling parameter; no transform is claimed or silently applied. */
    fun hasNSigParameter(url: String): Boolean = Regex("(?:[?&])n=[^&#]+", RegexOption.IGNORE_CASE).containsMatchIn(url)

    fun innertubeFailure(error: JSONObject): ResolverFailure {
        val code = error.optInt("code", -1)
        val message = error.optString("message").take(160).ifBlank { "No error message" }
        val lower = message.lowercase()
        return when {
            code == 429 || isRateLimitMessage(lower) -> ResolverFailure.RateLimited("Innertube error $code: $message")
            isChallengeMessage(lower) -> ResolverFailure.ChallengeRequired("Innertube error $code: $message")
            else -> ResolverFailure.PlayerResponseFailure("Innertube error $code: $message")
        }
    }

    fun failure(status: JSONObject?): ResolverFailure {
        if (status == null) return ResolverFailure.MalformedResponse("Player response omitted playabilityStatus")
        val code = status?.optString("status") ?: "UNKNOWN"
        val reason = status?.optString("reason")?.take(180) ?: "Player returned $code"
        val lower = reason.lowercase()
        return when {
            isChallengeMessage(lower) -> ResolverFailure.ChallengeRequired(reason)
            isRateLimitMessage(lower) -> ResolverFailure.RateLimited(reason)
            code == "LOGIN_REQUIRED" || "sign in" in lower || "age" in lower -> ResolverFailure.SignInRequired(reason)
            code == "UNPLAYABLE" || code == "ERROR" -> ResolverFailure.VideoUnavailable(reason)
            else -> ResolverFailure.PlayerResponseFailure("$code: $reason")
        }
    }
    fun deliveryFailure(streaming: JSONObject?, advertised: Int, ciphered: Int): ResolverFailure = when {
        ciphered > 0 -> ResolverFailure.Ciphered("$ciphered formats require signature deciphering")
        streaming?.optString("serverAbrStreamingUrl")?.isNotBlank() == true ->
            ResolverFailure.SabrOnly("SABR delivery; $advertised advertised formats lack direct media URLs")
        streaming?.optString("dashManifestUrl")?.startsWith("https://") == true ->
            ResolverFailure.DashManifestOnly("DASH manifest advertised; manifest transport is not implemented")
        else -> ResolverFailure.NoPlayableFormats("No usable URL formats among $advertised advertised")
    }
    fun mediaHttpFailure(
        status: Int,
        nParameterNeedsTransform: Boolean,
        expiresAtEpochSeconds: Long?,
        nowEpochSeconds: Long
    ): ResolverFailure? = when {
        status == 429 -> ResolverFailure.RateLimited("CDN returned HTTP 429")
        status == 408 || status == 425 || status in 500..599 ->
            ResolverFailure.TransientNetworkFailure("CDN returned transient HTTP $status")
        status == 403 && nParameterNeedsTransform &&
            (expiresAtEpochSeconds == null || expiresAtEpochSeconds > nowEpochSeconds + 30) ->
            ResolverFailure.NParameterTransformRequired("CDN returned HTTP 403 while n is still untransformed")
        status == 403 || status == 410 -> ResolverFailure.MediaUrlExpired("CDN returned $status")
        status != 200 && status != 206 -> ResolverFailure.NetworkFailure("CDN returned $status")
        else -> null
    }

    fun state(failure: ResolverFailure): ResolutionState = when (failure) {
        is ResolverFailure.ChallengeRequired, is ResolverFailure.SignInRequired -> ResolutionState.CHALLENGED
        is ResolverFailure.Ciphered -> ResolutionState.CIPHERED
        is ResolverFailure.NParameterTransformRequired -> ResolutionState.N_PARAMETER_REQUIRED
        is ResolverFailure.SabrOnly -> ResolutionState.SABR_ONLY
        is ResolverFailure.DashManifestOnly -> ResolutionState.DASH_MANIFEST_ONLY
        is ResolverFailure.MediaUrlExpired -> ResolutionState.EXPIRED
        is ResolverFailure.RateLimited -> ResolutionState.RATE_LIMITED
        is ResolverFailure.TransientNetworkFailure -> ResolutionState.TRANSIENT_NETWORK
        is ResolverFailure.RedirectFailure -> ResolutionState.REDIRECT_FAILED
        is ResolverFailure.ContentLengthChanged -> ResolutionState.CONTENT_LENGTH_CHANGED
        is ResolverFailure.MalformedResponse -> ResolutionState.MALFORMED_RESPONSE
        else -> ResolutionState.UNSUPPORTED
    }

    private fun isChallengeMessage(lower: String) =
        "bot" in lower || "captcha" in lower || "challenge" in lower || "reload" in lower

    private fun isRateLimitMessage(lower: String) =
        "too many requests" in lower || "rate limit" in lower || "rate-limit" in lower ||
            "quota exceeded" in lower || "temporarily blocked" in lower
}

object DescriptionChapterParser {
    fun parse(description: String): List<Chapter> {
        val pattern = Regex("^\\s*(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})\\s+[-–—]?\\s*(.+)$")
        val chapters = description.lineSequence().mapNotNull { line ->
            val match = pattern.matchEntire(line.trim()) ?: return@mapNotNull null
            val hours = match.groupValues[1].toLongOrNull() ?: 0L
            val minutes = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val seconds = match.groupValues[3].toLongOrNull() ?: return@mapNotNull null
            if ((hours > 0 && minutes > 59) || seconds > 59) return@mapNotNull null
            Chapter(match.groupValues[4].trim(), ((hours * 60 + minutes) * 60 + seconds) * 1000)
        }.toList()
        return chapters.takeIf { it.size >= 2 && it.first().startMs == 0L && it.zipWithNext().all { (a, b) -> a.startMs < b.startMs } }
            ?: emptyList()
    }
}
