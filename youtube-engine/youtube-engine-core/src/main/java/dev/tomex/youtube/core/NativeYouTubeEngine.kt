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
    private val strategies: List<ClientStrategy> = listOf(ClientStrategy("WEB", "auto", "Mozilla/5.0"))
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
        val root = player(videoId, bootstrap())
        val details = root.optJSONObject("videoDetails") ?: throw ResolverFailure.PlayerResponseFailure("No video details")
        return VideoDetails(videoId, details.optString("title"), details.optString("author"),
            details.optString("channelId"), details.optString("shortDescription"),
            details.optString("lengthSeconds").toLongOrNull(),
            details.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.strings("url") ?: emptyList(),
            subtitles = captionTracks(root))
    }

    override suspend fun resolve(videoId: String): PlaybackDescriptor {
        checkId(videoId)
        val diagnostics = mutableListOf<String>()
        val config = bootstrap()
        for (strategy in strategies.take(4).map { if (it.version == "auto" && it.name == "WEB") config.client else it }) {
            try {
                val root = post("player", JSONObject().put("context", context(strategy)).put("videoId", videoId)
                    .put("contentCheckOk", true).put("racyCheckOk", true), config.copy(client = strategy))
                val status = root.optJSONObject("playabilityStatus")
                if (status?.optString("status") != "OK") {
                    val reason = status?.optString("reason") ?: "Missing playability status"
                    diagnostics += "${strategy.name}: ${status?.optString("status")} $reason"
                    continue
                }
                val streaming = root.optJSONObject("streamingData")
                val expiry = streaming?.optLong("expiresInSeconds")?.takeIf { it > 0 }?.let { System.currentTimeMillis() / 1000 + it }
                val formats = listOf("formats", "adaptiveFormats").flatMap { name ->
                    val array = streaming?.optJSONArray(name) ?: JSONArray()
                    (0 until array.length()).mapNotNull { index -> parseFormat(array.optJSONObject(index), expiry, strategy) }
                }
                if (formats.isNotEmpty()) return PlaybackDescriptor(videoId, formats, strategy.name, diagnostics + "${strategy.name}: ${formats.size} URL formats", captionTracks(root))
                diagnostics += "${strategy.name}: no directly usable formats (cipher/SABR may be required)"
            } catch (e: ResolverFailure) { diagnostics += "${strategy.name}: ${e.javaClass.simpleName}: ${e.message}" }
        }
        throw ResolverFailure.NoPlayableFormats(diagnostics.joinToString("; ").take(800))
    }

    override suspend fun refreshMedia(videoId: String, stableFormatIdentity: String): MediaFormat =
        resolve(videoId).formats.firstOrNull { it.stableIdentity == stableFormatIdentity }
            ?: throw ResolverFailure.NoPlayableFormats("Format $stableFormatIdentity is no longer offered")

    override suspend fun probe(format: MediaFormat, byteLimit: Int): TransportProof = withContext(Dispatchers.IO) {
        require(byteLimit in 1..65536)
        val connection = (URL(format.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000; readTimeout = 12000; instanceFollowRedirects = true
            setRequestProperty("Range", "bytes=0-${byteLimit - 1}")
            format.requiredHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            val status = connection.responseCode
            if (status == 403 || status == 410) throw ResolverFailure.MediaUrlExpired("CDN returned $status")
            if (status !in 200..206) throw ResolverFailure.NetworkFailure("CDN returned $status")
            var count = 0
            connection.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (count < byteLimit) {
                    val read = input.read(buffer, 0, minOf(buffer.size, byteLimit - count))
                    if (read < 0) break
                    count += read
                }
            }
            if (count == 0) throw ResolverFailure.NetworkFailure("CDN returned zero bytes")
            TransportProof(connection.url.host, status, count, connection.getHeaderField("Content-Range"), connection.contentLengthLong.takeIf { it >= 0 })
        } finally { connection.disconnect() }
    }

    private suspend fun player(videoId: String, config: Bootstrap): JSONObject = post("player",
        JSONObject().put("context", context(config.client)).put("videoId", videoId).put("contentCheckOk", true).put("racyCheckOk", true), config)

    private fun context(strategy: ClientStrategy) = JSONObject().put("client", JSONObject()
        .put("clientName", strategy.name).put("clientVersion", strategy.version).put("hl", "en").put("gl", "US"))

    private data class Bootstrap(val key: String, val client: ClientStrategy)
    @Volatile private var cachedBootstrap: Bootstrap? = null
    private suspend fun bootstrap(): Bootstrap = cachedBootstrap ?: withContext(Dispatchers.IO) {
        val connection = (URL("https://www.youtube.com/").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 15000; setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        try {
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val key = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                ?: throw ResolverFailure.PlayerResponseFailure("Missing current Innertube key")
            val version = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                ?: throw ResolverFailure.PlayerResponseFailure("Missing current web client version")
            Bootstrap(key, ClientStrategy("WEB", version, "Mozilla/5.0")).also { cachedBootstrap = it }
        } finally { connection.disconnect() }
    }

    private suspend fun post(endpoint: String, body: JSONObject, config: Bootstrap): JSONObject = withContext(Dispatchers.IO) {
        val strategy = config.client
        val connection = (URL("https://www.youtube.com/youtubei/v1/$endpoint?key=${config.key}&prettyPrint=false").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 10000; readTimeout = 15000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", strategy.userAgent)
            setRequestProperty("X-YouTube-Client-Name", if (strategy.name == "ANDROID") "3" else "1")
            setRequestProperty("X-YouTube-Client-Version", strategy.version)
            session.visitorData()?.let { setRequestProperty("X-Goog-Visitor-Id", it) }
            session.requestHeaders().forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) throw ResolverFailure.NetworkFailure("Innertube HTTP $status")
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
        return MediaFormat("itag:$itag", itag, url, mime, codec, mime.substringAfter('/').substringBefore(';').ifBlank { null },
            value.optInt("width").takeIf { it > 0 }, value.optInt("height").takeIf { it > 0 }, value.optInt("fps").takeIf { it > 0 },
            value.optLong("bitrate").takeIf { it > 0 }, value.optString("contentLength").toLongOrNull(),
            value.optInt("audioChannels").takeIf { it > 0 }, value.optString("audioSampleRate").toIntOrNull(),
            video, audio, if (video && audio) Delivery.PROGRESSIVE else Delivery.ADAPTIVE,
            mapOf("User-Agent" to strategy.userAgent), expiry)
    }

    private fun captionTracks(root: JSONObject): List<SubtitleTrack> {
        val tracks = root.optJSONObject("captions")?.optJSONObject("playerCaptionsTracklistRenderer")?.optJSONArray("captionTracks") ?: return emptyList()
        return (0 until tracks.length()).mapNotNull { index -> tracks.optJSONObject(index)?.let {
            SubtitleTrack(it.optString("languageCode"), label(it.optJSONObject("name")), it.optString("baseUrl"), it.optString("kind") == "asr")
        } }
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
