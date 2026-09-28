package dev.tomex.youtube.core

import dev.tomex.youtube.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL

/** Original on-device Innertube implementation. Client strategies may be replaced independently. */
class NativeYouTubeEngine(
    private val session: SessionProvider = AnonymousSession,
    private val strategies: List<ClientStrategy> = listOf(
        ClientStrategy("ANDROID_VR", "1.60.19", "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 9; en_US; Oculus Quest) gzip"),
        ClientStrategy("IOS", "21.37.2", "com.google.ios.youtube/21.37.2 (iPhone16,2; iOS 18.0; en_US)"),
        ClientStrategy("WEB", "auto", "Mozilla/5.0")
    )
) : YouTubeEngine {
    override suspend fun search(query: String, continuation: String?): Page<SearchResult> {
        require(query.isNotBlank())
        var config = bootstrap()
        var root: JSONObject? = null
        var lastFailure: ResolverFailure? = null
        val diagnostics = mutableListOf<String>()
        for (candidate in searchStrategies()) {
            val strategy = if (candidate.name == "WEB" && candidate.version == "auto") config.client else candidate
            try {
                val body = JSONObject().put("context", context(strategy))
                if (continuation == null) body.put("query", query) else body.put("continuation", continuation)
                val candidateResponse = post("search", body, config.copy(client = strategy))
                if (candidateResponse.has("error")) {
                    val error = candidateResponse.optJSONObject("error")
                    throw ResolverFailure.PlayerResponseFailure("${strategy.name} search error ${error?.optInt("code")}: ${error?.optString("message")?.take(160)}")
                }
                root = candidateResponse
                val recognized = hasRecognizedSearchResult(candidateResponse)
                diagnostics += "${strategy.name}: recognizedResults=$recognized"
                if (recognized) break
                lastFailure = ResolverFailure.PlayerResponseFailure("${strategy.name} search response contained no recognized result renderers")
            } catch (e: ResolverFailure) {
                lastFailure = e
                diagnostics += "${strategy.name}: ${e.javaClass.simpleName}: ${e.message}"
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
        var next: String? = null
        walk(response) { node ->
            if (next == null) next = node.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }
        }
        if (output.isEmpty() && lastFailure != null) diagnostics += "all attempted clients returned no parseable results"
        return Page(output.distinctBy { it.toString() }, next, diagnostics)
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
                }
            } catch (e: ResolverFailure) {
                failures += e
                errors += "${strategy.name}: ${e.javaClass.simpleName}"
                if (e is ResolverFailure.NetworkFailure && isBootstrapStale(e))
                    runCatching { bootstrap(force = true) }.getOrNull()?.let { config = it }
            }
        }
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
            else -> ResolverFailure.PlayerResponseFailure(summary)
        }
    }

    private fun videoDetailsFrom(videoId: String, details: JSONObject, root: JSONObject) = VideoDetails(
        videoId, details.optString("title"), details.optString("author"), details.optString("channelId"),
        details.optString("shortDescription"), details.optString("lengthSeconds").toLongOrNull(),
        details.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.strings("url") ?: emptyList(),
        chapters = DescriptionChapterParser.parse(details.optString("shortDescription")), subtitles = captionTracks(root)
    )

    private suspend fun watchPageDetails(videoId: String): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL("https://www.youtube.com/watch?v=$videoId").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 15000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0")
            session.visitorData()?.let { setRequestProperty("X-Goog-Visitor-Id", it) }
            session.requestHeaders(url.toString()).forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            val status = connection.responseCode
            storeSessionCookies(connection)
            if (status == 429) throw ResolverFailure.RateLimited("Watch page HTTP 429")
            if (status !in 200..299) throw ResolverFailure.NetworkFailure("Watch page HTTP $status")
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val marker = Regex("ytInitialPlayerResponse\\s*=\\s*").find(html)
                ?: throw ResolverFailure.PlayerResponseFailure("Watch page has no initial player response")
            val value = JSONTokener(html.substring(marker.range.last + 1)).nextValue()
            value as? JSONObject ?: throw ResolverFailure.PlayerResponseFailure("Watch page player response is not an object")
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
                    continue
                }
                val streaming = root.optJSONObject("streamingData")
                val expiry = streaming?.optLong("expiresInSeconds")?.takeIf { it > 0 }?.let { System.currentTimeMillis() / 1000 + it }
                val all = listOf("formats", "adaptiveFormats").sumOf { streaming?.optJSONArray(it)?.length() ?: 0 }
                val ciphered = listOf("formats", "adaptiveFormats").sumOf { name ->
                    val array = streaming?.optJSONArray(name) ?: JSONArray()
                    (0 until array.length()).count { PlayerResponseClassifier.hasCipherParameters(array.optJSONObject(it)) }
                }
                val formats = listOf("formats", "adaptiveFormats").flatMap { name ->
                    val array = streaming?.optJSONArray(name) ?: JSONArray()
                    (0 until array.length()).mapNotNull { index -> parseFormat(array.optJSONObject(index), expiry, strategy) }
                }
                if (formats.isNotEmpty()) return PlaybackDescriptor(videoId, formats, strategy.name,
                    diagnostics + "${strategy.name}: ${formats.size} URL formats; excluded $ciphered ciphered formats", captionTracks(root))
                val failure = PlayerResponseClassifier.deliveryFailure(streaming, all, ciphered)
                failures += failure
                diagnostics += "${strategy.name}: ${failure.javaClass.simpleName}: ${failure.message}"
            } catch (e: ResolverFailure) {
                failures += e
                diagnostics += "${strategy.name}: ${e.javaClass.simpleName}: ${e.message}"
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
            failures.any { it is ResolverFailure.Ciphered } -> ResolverFailure.Ciphered(summary)
            failures.all { it is ResolverFailure.VideoUnavailable } && failures.isNotEmpty() -> ResolverFailure.VideoUnavailable(summary)
            else -> ResolverFailure.NoPlayableFormats(summary)
        }
    }

    override suspend fun resolveVerified(videoId: String, minimumHeight: Int): VerifiedPlayback {
        val descriptor = resolve(videoId)
        val selection = descriptor.selectAdaptive(minimumHeight)
            ?: throw ResolverFailure.NoPlayableFormats("No container-compatible adaptive pair at ${minimumHeight}p+")
        val videoProof = probe(selection.video)
        val audioProof = probe(selection.audio)
        return VerifiedPlayback(descriptor, selection, videoProof, audioProof)
    }

    override suspend fun fetchSubtitle(track: SubtitleTrack, byteLimit: Int): SubtitleProof = withContext(Dispatchers.IO) {
        require(byteLimit in 1..1_000_000)
        val connection = (URL(track.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 15000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", strategies.first().userAgent)
            session.requestHeaders(url.toString()).forEach { (key, value) -> setRequestProperty(key, value) }
        }
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
        } finally { connection.disconnect() }
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

    override suspend fun probeRange(format: MediaFormat, startByte: Long, byteLimit: Int): TransportProof =
        readMediaRange(format, startByte, byteLimit).proof

    private data class RangeRead(val proof: TransportProof, val bytes: ByteArray, val totalBytes: Long?)

    private suspend fun readMediaRange(format: MediaFormat, startByte: Long, byteLimit: Int, minimumBytes: Int = 512): RangeRead = withContext(Dispatchers.IO) {
        require(byteLimit in 1..4_194_304)
        require(minimumBytes in 1..512)
        require(startByte >= 0 && startByte <= Long.MAX_VALUE - byteLimit)
        val expiry = format.expiresAtEpochSeconds
        if (expiry != null && expiry <= System.currentTimeMillis() / 1000 + 30)
            throw ResolverFailure.MediaUrlExpired("Descriptor expired; refresh by stableIdentity")
        val connection = (URL(format.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000; readTimeout = 12000; instanceFollowRedirects = true
            setRequestProperty("Range", "bytes=$startByte-${startByte + byteLimit - 1}")
            format.requiredHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            val status = connection.responseCode
            storeSessionCookies(connection)
            if (status == 429) throw ResolverFailure.RateLimited("CDN returned HTTP 429")
            if (status == 403 || status == 410) throw ResolverFailure.MediaUrlExpired("CDN returned $status")
            if (status != 200 && status != 206) throw ResolverFailure.NetworkFailure("CDN returned $status")
            if (startByte > 0 && status != 206) throw ResolverFailure.UnsupportedDelivery("CDN ignored resume byte range")
            val range = connection.getHeaderField("Content-Range")
            val rangeMatch = if (status == 206) Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(range.orEmpty()) else null
            if (status == 206 && (rangeMatch == null || rangeMatch.groupValues[1].toLongOrNull() != startByte))
                throw ResolverFailure.UnsupportedDelivery("CDN returned wrong resume range: $range")
            val output = java.io.ByteArrayOutputStream(minOf(byteLimit, 65536))
            connection.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (output.size() < byteLimit) {
                    val read = input.read(buffer, 0, minOf(buffer.size, byteLimit - output.size()))
                    if (read < 0) break
                    if (read > 0) output.write(buffer, 0, read)
                }
            }
            val bytes = output.toByteArray()
            val contentType = connection.contentType ?: ""
            if (bytes.size < minimumBytes || contentType.startsWith("text/") || contentType.contains("html", ignoreCase = true))
                throw ResolverFailure.NetworkFailure("CDN returned non-media data: HTTP $status, type=$contentType, bytes=${bytes.size}")
            val rangeEnd = rangeMatch?.groupValues?.get(2)?.toLongOrNull()
            if (rangeEnd != null && bytes.size.toLong() != rangeEnd - startByte + 1)
                throw ResolverFailure.NetworkFailure("CDN range body was truncated: header ends at $rangeEnd, received ${bytes.size} bytes")
            val total = rangeMatch?.groupValues?.get(3)?.toLongOrNull()
                ?: format.contentLength?.takeIf { status == 200 }
            if (total != null && startByte + bytes.size > total)
                throw ResolverFailure.UnsupportedDelivery("CDN chunk extends beyond declared media length")
            val proof = TransportProof(connection.url.host, status, bytes.size, range, connection.contentLengthLong.takeIf { it >= 0 }, startByte)
            RangeRead(proof, bytes, total)
        } finally { connection.disconnect() }
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

    private data class Bootstrap(val key: String, val client: ClientStrategy)
    @Volatile private var cachedBootstrap: Bootstrap? = null
    @Volatile private var bootstrapAtMs: Long = 0
    private suspend fun bootstrap(force: Boolean = false): Bootstrap {
        if (!force) cachedBootstrap?.takeIf { System.currentTimeMillis() - bootstrapAtMs < 15 * 60_000 }?.let { return it }
        return withContext(Dispatchers.IO) {
        val connection = (URL("https://www.youtube.com/").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 15000; setRequestProperty("User-Agent", "Mozilla/5.0")
            session.visitorData()?.let { setRequestProperty("X-Goog-Visitor-Id", it) }
            session.requestHeaders(url.toString()).forEach { (key, value) -> setRequestProperty(key, value) }
        }
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
            Bootstrap(key, ClientStrategy("WEB", version, "Mozilla/5.0")).also { cachedBootstrap = it; bootstrapAtMs = System.currentTimeMillis() }
        } finally { connection.disconnect() }
        }
    }

    private fun isBootstrapStale(failure: ResolverFailure.NetworkFailure): Boolean =
        failure.message?.let { "Innertube HTTP 400" in it || "Innertube HTTP 403" in it } == true

    private fun searchStrategies(): List<ClientStrategy> = strategies.take(4).sortedBy {
        when (it.name) { "WEB" -> 0; "IOS" -> 1; "ANDROID_VR" -> 2; else -> 3 }
    }

    private suspend fun post(endpoint: String, body: JSONObject, config: Bootstrap): JSONObject = withContext(Dispatchers.IO) {
        val strategy = config.client
        val connection = (URL("https://www.youtube.com/youtubei/v1/$endpoint?key=${config.key}&prettyPrint=false").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 10000; readTimeout = 15000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", strategy.userAgent)
            setRequestProperty("X-YouTube-Client-Name", when (strategy.name) { "ANDROID_VR" -> "28"; "IOS" -> "5"; else -> "1" })
            setRequestProperty("X-YouTube-Client-Version", strategy.version)
            session.visitorData()?.let { setRequestProperty("X-Goog-Visitor-Id", it) }
            session.requestHeaders(url.toString()).forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            storeSessionCookies(connection)
            if (status == 429) throw ResolverFailure.RateLimited("Innertube HTTP 429")
            if (status !in 200..299) {
                if (status == 400 || status == 403) cachedBootstrap = null
                throw ResolverFailure.NetworkFailure("Innertube HTTP $status")
            }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (e: java.io.IOException) {
            throw ResolverFailure.NetworkFailure(e.javaClass.simpleName + ": " + (e.message ?: "I/O failure").take(160))
        } finally { connection.disconnect() }
    }

    private suspend fun storeSessionCookies(connection: HttpURLConnection) {
        val cookies = connection.headerFields.entries
            .filter { (name, _) -> name?.equals("Set-Cookie", ignoreCase = true) == true }
            .flatMap { (_, values) -> values.orEmpty() }
        if (cookies.isNotEmpty()) session.storeResponseCookies(connection.url.toString(), cookies)
    }
    private fun parseFormat(value: JSONObject?, expiry: Long?, strategy: ClientStrategy): MediaFormat? {
        if (value == null) return null
        // Ciphered formats require a separate player-JS transformer. Never report them as playable.
        if (PlayerResponseClassifier.hasCipherParameters(value)) return null
        val url = value.optString("url").takeIf { it.startsWith("https://") } ?: return null
        val mime = value.optString("mimeType")
        val video = mime.startsWith("video/")
        val audio = mime.startsWith("audio/") || value.has("audioQuality")
        val itag = value.optInt("itag", -1)
        if (itag < 0) return null
        val codec = mime.substringAfter("codecs=\"", "").substringBefore('"').ifBlank { null }
        val container = mime.substringAfter('/').substringBefore(';').ifBlank { null }
        val identity = "itag:$itag:${container ?: "unknown"}:${codec ?: "unknown"}"
        val urlExpiry = Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
        return MediaFormat(identity, itag, url, mime, codec, container,
            value.optInt("width").takeIf { it > 0 }, value.optInt("height").takeIf { it > 0 }, value.optInt("fps").takeIf { it > 0 },
            value.optLong("bitrate").takeIf { it > 0 }, value.optString("contentLength").toLongOrNull(),
            value.optInt("audioChannels").takeIf { it > 0 }, value.optString("audioSampleRate").toIntOrNull(),
            video, audio, if (video && audio) Delivery.PROGRESSIVE else Delivery.ADAPTIVE,
            mapOf("User-Agent" to strategy.userAgent), listOfNotNull(expiry, urlExpiry).minOrNull())
    }

    private fun captionTracks(root: JSONObject): List<SubtitleTrack> {
        val tracks = root.optJSONObject("captions")?.optJSONObject("playerCaptionsTracklistRenderer")?.optJSONArray("captionTracks") ?: return emptyList()
        return (0 until tracks.length()).mapNotNull { index -> tracks.optJSONObject(index)?.let {
            SubtitleTrack(it.optString("languageCode"), label(it.optJSONObject("name")), it.optString("baseUrl"), it.optString("kind") == "asr")
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

data class ClientStrategy(val name: String, val version: String, val userAgent: String)

/** Pure classification of observed response stages; no URL is marked proven here. */
object PlayerResponseClassifier {
    /** Cipher metadata takes precedence even if a response also contains an unsigned URL field. */
    fun hasCipherParameters(format: JSONObject?): Boolean =
        format?.has("signatureCipher") == true || format?.has("cipher") == true

    fun failure(status: JSONObject?): ResolverFailure {
        val code = status?.optString("status") ?: "UNKNOWN"
        val reason = status?.optString("reason")?.take(180) ?: "Player returned $code"
        val lower = reason.lowercase()
        return when {
            "bot" in lower || "captcha" in lower || "challenge" in lower || "reload" in lower -> ResolverFailure.ChallengeRequired(reason)
            code == "LOGIN_REQUIRED" || "sign in" in lower || "age" in lower -> ResolverFailure.SignInRequired(reason)
            code == "UNPLAYABLE" || code == "ERROR" -> ResolverFailure.VideoUnavailable(reason)
            else -> ResolverFailure.PlayerResponseFailure("$code: $reason")
        }
    }
    fun deliveryFailure(streaming: JSONObject?, advertised: Int, ciphered: Int): ResolverFailure = when {
        ciphered > 0 -> ResolverFailure.Ciphered("$ciphered formats require signature deciphering")
        streaming?.optString("serverAbrStreamingUrl")?.isNotBlank() == true ->
            ResolverFailure.SabrOnly("SABR delivery; $advertised advertised formats lack direct media URLs")
        else -> ResolverFailure.NoPlayableFormats("No usable URL formats among $advertised advertised")
    }
    fun state(failure: ResolverFailure): ResolutionState = when (failure) {
        is ResolverFailure.ChallengeRequired, is ResolverFailure.SignInRequired -> ResolutionState.CHALLENGED
        is ResolverFailure.Ciphered -> ResolutionState.CIPHERED
        is ResolverFailure.SabrOnly -> ResolutionState.SABR_ONLY
        is ResolverFailure.MediaUrlExpired -> ResolutionState.EXPIRED
        is ResolverFailure.RateLimited -> ResolutionState.RATE_LIMITED
        else -> ResolutionState.UNSUPPORTED
    }
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
