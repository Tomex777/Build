package dev.tomex.youtube.core

import dev.tomex.youtube.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
        val config = bootstrap()
        val body = JSONObject().put("context", context(config.client))
        if (continuation == null) body.put("query", query) else body.put("continuation", continuation)
        val root = post("search", body, config)
        val output = mutableListOf<SearchResult>()
        walk(root) { node ->
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
        walk(root) { node ->
            if (next == null) next = node.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }
        }
        return Page(output.distinctBy { it.toString() }, next)
    }

    override suspend fun videoDetails(videoId: String): VideoDetails {
        checkId(videoId)
        val descriptor = resolve(videoId)
        val root = player(videoId, bootstrap().copy(client = strategies.first { it.name == descriptor.client }))
        val details = root.optJSONObject("videoDetails") ?: throw ResolverFailure.PlayerResponseFailure("No video details")
        return VideoDetails(videoId, details.optString("title"), details.optString("author"),
            details.optString("channelId"), details.optString("shortDescription"),
            details.optString("lengthSeconds").toLongOrNull(),
            details.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.strings("url") ?: emptyList(),
            chapters = descriptionChapters(details.optString("shortDescription")), subtitles = captionTracks(root))
    }

    override suspend fun resolve(videoId: String): PlaybackDescriptor {
        checkId(videoId)
        val diagnostics = mutableListOf<String>()
        val failures = mutableListOf<ResolverFailure>()
        val config = bootstrap()
        for (strategy in strategies.take(4).map { if (it.version == "auto" && it.name == "WEB") config.client else it }) {
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
                val formats = listOf("formats", "adaptiveFormats").flatMap { name ->
                    val array = streaming?.optJSONArray(name) ?: JSONArray()
                    (0 until array.length()).mapNotNull { index -> parseFormat(array.optJSONObject(index), expiry, strategy) }
                }
                if (formats.isNotEmpty()) return PlaybackDescriptor(videoId, formats, strategy.name, diagnostics + "${strategy.name}: ${formats.size} URL formats", captionTracks(root))
                val all = listOf("formats", "adaptiveFormats").sumOf { streaming?.optJSONArray(it)?.length() ?: 0 }
                val ciphered = listOf("formats", "adaptiveFormats").sumOf { name ->
                    val array = streaming?.optJSONArray(name) ?: JSONArray()
                    (0 until array.length()).count { array.optJSONObject(it)?.has("signatureCipher") == true || array.optJSONObject(it)?.has("cipher") == true }
                }
                val failure = PlayerResponseClassifier.deliveryFailure(streaming, all, ciphered)
                failures += failure
                diagnostics += "${strategy.name}: ${failure.javaClass.simpleName}: ${failure.message}"
            } catch (e: ResolverFailure) { failures += e; diagnostics += "${strategy.name}: ${e.javaClass.simpleName}: ${e.message}" }
        }
        val summary = diagnostics.joinToString("; ").take(800)
        throw when {
            failures.any { it is ResolverFailure.ChallengeRequired } -> ResolverFailure.ChallengeRequired(summary)
            failures.any { it is ResolverFailure.SignInRequired } -> ResolverFailure.SignInRequired(summary)
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

    override suspend fun refreshMedia(videoId: String, stableFormatIdentity: String): MediaFormat =
        resolve(videoId).formats.firstOrNull { it.stableIdentity == stableFormatIdentity }
            ?: throw ResolverFailure.NoPlayableFormats("Format $stableFormatIdentity is no longer offered")

    override suspend fun probe(format: MediaFormat, byteLimit: Int): TransportProof = probeRange(format, 0, byteLimit)

    override suspend fun probeRange(format: MediaFormat, startByte: Long, byteLimit: Int): TransportProof = withContext(Dispatchers.IO) {
        require(byteLimit in 1..65536)
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
            if (status == 403 || status == 410) throw ResolverFailure.MediaUrlExpired("CDN returned $status")
            if (status !in 200..206) throw ResolverFailure.NetworkFailure("CDN returned $status")
            if (startByte > 0 && status != 206) throw ResolverFailure.UnsupportedDelivery("CDN ignored resume byte range")
            val range = connection.getHeaderField("Content-Range")
            if (startByte > 0 && range?.startsWith("bytes $startByte-") != true)
                throw ResolverFailure.UnsupportedDelivery("CDN returned wrong resume range")
            var count = 0
            connection.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (count < byteLimit) {
                    val read = input.read(buffer, 0, minOf(buffer.size, byteLimit - count))
                    if (read < 0) break
                    count += read
                }
            }
            val contentType = connection.contentType ?: ""
            if (count < 512 || contentType.startsWith("text/") || contentType.contains("html", ignoreCase = true))
                throw ResolverFailure.NetworkFailure("CDN returned non-media data: HTTP $status, type=$contentType, bytes=$count")
            TransportProof(connection.url.host, status, count, range, connection.contentLengthLong.takeIf { it >= 0 }, startByte)
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
    private suspend fun bootstrap(): Bootstrap {
        cachedBootstrap?.takeIf { System.currentTimeMillis() - bootstrapAtMs < 15 * 60_000 }?.let { return it }
        return withContext(Dispatchers.IO) {
        val connection = (URL("https://www.youtube.com/").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 15000; setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        try {
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val key = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                ?: throw ResolverFailure.PlayerResponseFailure("Missing current Innertube key")
            val version = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                ?: throw ResolverFailure.PlayerResponseFailure("Missing current web client version")
            Bootstrap(key, ClientStrategy("WEB", version, "Mozilla/5.0")).also { cachedBootstrap = it; bootstrapAtMs = System.currentTimeMillis() }
        } finally { connection.disconnect() }
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
            session.visitorData()?.let { setRequestProperty("X-Goog-Visitor-Id", it) }
            session.requestHeaders().forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                if (status == 400 || status == 403) cachedBootstrap = null
                throw ResolverFailure.NetworkFailure("Innertube HTTP $status")
            }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (e: java.io.IOException) {
            throw ResolverFailure.NetworkFailure(e.javaClass.simpleName + ": " + (e.message ?: "I/O failure").take(160))
        } finally { connection.disconnect() }
    }

    private fun parseFormat(value: JSONObject?, expiry: Long?, strategy: ClientStrategy): MediaFormat? {
        if (value == null) return null
        // Ciphered formats require a separate player-JS transformer. Never report them as playable.
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

    private fun descriptionChapters(description: String): List<Chapter> {
        val pattern = Regex("^\\s*(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})\\s+[-–—]?\\s*(.+)$")
        val chapters = description.lineSequence().mapNotNull { line ->
            val match = pattern.matchEntire(line.trim()) ?: return@mapNotNull null
            val hours = match.groupValues[1].toLongOrNull() ?: 0L
            val minutes = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val seconds = match.groupValues[3].toLongOrNull() ?: return@mapNotNull null
            if (minutes > 59 && hours > 0 || seconds > 59) return@mapNotNull null
            Chapter(match.groupValues[4].trim(), ((hours * 60 + minutes) * 60 + seconds) * 1000)
        }.toList()
        return chapters.takeIf { it.size >= 2 && it.first().startMs == 0L && it.zipWithNext().all { (a, b) -> a.startMs < b.startMs } }
            ?: emptyList()
    }

    private fun checkId(videoId: String) { require(Regex("[a-zA-Z0-9_-]{11}").matches(videoId)) { "Invalid video ID" } }
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
    fun failure(status: JSONObject?): ResolverFailure {
        val code = status?.optString("status") ?: "UNKNOWN"
        val reason = status?.optString("reason")?.take(180) ?: "Player returned $code"
        val lower = reason.lowercase()
        return when {
            code == "LOGIN_REQUIRED" || "sign in" in lower || "age" in lower -> ResolverFailure.SignInRequired(reason)
            "bot" in lower || "captcha" in lower || "challenge" in lower || "reload" in lower -> ResolverFailure.ChallengeRequired(reason)
            code == "UNPLAYABLE" || code == "ERROR" -> ResolverFailure.VideoUnavailable(reason)
            else -> ResolverFailure.PlayerResponseFailure("$code: $reason")
        }
    }
    fun deliveryFailure(streaming: JSONObject?, advertised: Int, ciphered: Int): ResolverFailure = when {
        streaming?.optString("serverAbrStreamingUrl")?.isNotBlank() == true ->
            ResolverFailure.SabrOnly("SABR delivery; $advertised advertised formats lack direct media URLs")
        ciphered > 0 -> ResolverFailure.Ciphered("$ciphered formats require signature deciphering")
        else -> ResolverFailure.NoPlayableFormats("No usable URL formats among $advertised advertised")
    }
    fun state(failure: ResolverFailure): ResolutionState = when (failure) {
        is ResolverFailure.ChallengeRequired, is ResolverFailure.SignInRequired -> ResolutionState.CHALLENGED
        is ResolverFailure.Ciphered -> ResolutionState.CIPHERED
        is ResolverFailure.SabrOnly -> ResolutionState.SABR_ONLY
        is ResolverFailure.MediaUrlExpired -> ResolutionState.EXPIRED
        else -> ResolutionState.UNSUPPORTED
    }
}
