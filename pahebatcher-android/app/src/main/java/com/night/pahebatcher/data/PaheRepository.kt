package com.night.pahebatcher.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class VerificationKind { ANIMEPAHE, KWIK }

class VerificationRequired(
    val kind: VerificationKind,
    message: String,
) : IOException(message)

data class AnimeSearchResult(
    val session: String,
    val title: String,
    val poster: String,
    val type: String,
    val episodes: Int,
    val status: String,
    val animeId: Int? = null,
)

data class EpisodeInfo(
    val number: Double,
    val session: String,
    val title: String,
    val fansub: String,
    val audio: String,
    val playUrl: String,
) {
    val epLabel: String
        get() = if (number == number.toInt().toDouble()) number.toInt().toString() else number.toString()
}

data class AnimeDetails(
    val result: AnimeSearchResult,
    val host: String,
    val episodes: List<EpisodeInfo>,
)

data class StreamInfo(
    val url: String,
    val cookie: String,
    val userAgent: String,
    val referer: String,
    val quality: Int,
    val audio: String,
    val fansub: String,
)

private data class ReleaseOption(
    val resolution: Int,
    val url: String,
    val isDub: Boolean,
    val fansub: String,
)

private data class Segment(
    val url: String,
    val keyUrl: String?,
    val iv: ByteArray?,
)

class PaheRepository(
    private val context: Context,
    val sessions: SessionStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val animeHosts: List<String>
        get() = buildList {
            sessions.animeHost().takeIf { it.isNotBlank() }?.let(::add)
            addAll(listOf("animepahe.pw", "animepahe.com", "animepahe.org"))
        }.distinct()

    suspend fun search(query: String): List<AnimeSearchResult> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()

        var lastError: Exception? = null
        var verificationError: VerificationRequired? = null
        for (host in animeHosts) {
            try {
                val results = searchOnHost(host, clean)
                sessions.rememberAnimeHost(host)
                return@withContext results
            } catch (e: VerificationRequired) {
                verificationError = verificationError ?: e
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
        }
        verificationError?.let { throw it }
        throw lastError ?: IOException("No AnimePahe host responded")
    }

    suspend fun loadAnime(result: AnimeSearchResult): AnimeDetails = withContext(Dispatchers.IO) {
        val host = sessions.animeHost().ifBlank { animeHosts.first() }
        val normalizedTitle = normalizeTitle(result.title)

        // AnimePahe rotates the session UUID used in /anime/{session}. Refresh it from
        // the search API before loading episodes, using the stable anime ID when available.
        val variants = runCatching { searchOnHost(host, result.title) }.getOrDefault(emptyList())
        val refreshed = variants.firstOrNull { candidate ->
            result.animeId != null && candidate.animeId == result.animeId
        } ?: variants.firstOrNull { normalizeTitle(it.title) == normalizedTitle }

        val currentResult = refreshed ?: result
        val sessionIds = linkedSetOf(currentResult.session)

        variants.asSequence()
            .filter { normalizeTitle(it.title) == normalizedTitle }
            .map { it.session }
            .filter { it.isNotBlank() }
            .forEach(sessionIds::add)

        if (result.session.isNotBlank()) sessionIds.add(result.session)

        val unique = linkedMapOf<Pair<Double, String>, EpisodeInfo>()
        var firstVerificationError: VerificationRequired? = null
        var lastFailure: Exception? = null

        for (animeSession in sessionIds) {
            try {
                var page = 1
                while (true) {
                    val data = releasePage(host, animeSession, page)
                    val rows = data.optJSONArray("data") ?: break
                    for (index in 0 until rows.length()) {
                        val item = rows.optJSONObject(index) ?: continue
                        val epSession = item.optString("session")
                        if (epSession.isBlank()) continue
                        var audio = item.optString("audio", "jpn").trim().lowercase(Locale.US)
                        val title = item.optString("title").let { if (it == "?") "" else it.trim() }
                        if (audio == "jpn" && title.contains("dub", true)) audio = "eng"
                        if (audio == "eng" && title.contains("sub", true)) audio = "jpn"
                        val number = item.optDouble("episode", 0.0)
                        val ep = EpisodeInfo(
                            number = number,
                            session = epSession,
                            title = title,
                            fansub = item.optString("fansub").trim(),
                            audio = audio,
                            playUrl = "https://$host/play/$animeSession/$epSession",
                        )
                        unique.putIfAbsent(number to audio, ep)
                    }
                    val lastPage = data.optInt("last_page", 1).coerceAtLeast(1)
                    if (page >= lastPage) break
                    page++
                }
            } catch (e: VerificationRequired) {
                if (firstVerificationError == null) firstVerificationError = e
                lastFailure = e
            } catch (e: Exception) {
                lastFailure = e
            }

            // One good current session is enough. Stale session UUIDs should not poison
            // the whole details screen.
            if (unique.isNotEmpty() && animeSession == currentResult.session) break
        }

        if (unique.isEmpty()) {
            firstVerificationError?.let { throw it }
            lastFailure?.let { throw it }
        }

        AnimeDetails(
            result = currentResult,
            host = host,
            episodes = unique.values.sortedWith(compareBy<EpisodeInfo> { it.number }.thenBy { it.audio }),
        )
    }

    suspend fun bootstrapKwikUrl(): String = withContext(Dispatchers.IO) {
        val seed = search("One Piece").firstOrNull()
            ?: search("Naruto").firstOrNull()
            ?: throw IOException("Could not find an anime to open the second verification")
        val host = sessions.animeHost().ifBlank { animeHosts.first() }
        val page = releasePage(host, seed.session, 1)
        val rows = page.optJSONArray("data")
            ?: throw IOException("AnimePahe returned no episodes for verification")
        var last: Exception? = null
        for (index in 0 until minOf(rows.length(), 5)) {
            val epSession = rows.optJSONObject(index)?.optString("session").orEmpty()
            if (epSession.isBlank()) continue
            val playUrl = "https://$host/play/${seed.session}/$epSession"
            try {
                val html = requestText(playUrl, referer = "https://$host/")
                val options = parseReleaseOptions(html)
                options.firstOrNull()?.url?.let { return@withContext it }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Could not locate Kwik from an AnimePahe episode")
    }

    suspend fun resolveStream(
        episode: EpisodeInfo,
        requestedQuality: Int,
        requestedAudio: String,
    ): StreamInfo = withContext(Dispatchers.IO) {
        val host = URI(episode.playUrl).host.orEmpty()
        val playHtml = requestText(episode.playUrl, referer = "https://$host/")
        val options = parseReleaseOptions(playHtml)
        if (options.isEmpty()) throw IOException("No Kwik release was found on this episode")

        val wantsDub = requestedAudio == "eng"
        val matchingAudio = options.filter { it.isDub == wantsDub }.ifEmpty { options }
        val sorted = matchingAudio.sortedByDescending { it.resolution }
        val chosen = sorted.firstOrNull { it.resolution <= requestedQuality } ?: sorted.last()

        resolveKwik(chosen.url, chosen)
            ?: throw IOException("Kwik loaded, but no HLS stream URL could be extracted")
    }

    suspend fun downloadStream(
        stream: StreamInfo,
        animeTitle: String,
        episode: EpisodeInfo,
        onProgress: (Float) -> Unit,
    ): Uri = withContext(Dispatchers.IO) {
        val headers = linkedMapOf(
            "User-Agent" to stream.userAgent,
            "Referer" to stream.referer,
        )
        if (stream.cookie.isNotBlank()) headers["Cookie"] = stream.cookie

        var manifestUrl = stream.url
        var manifest = requestBytes(manifestUrl, headers).toString(Charsets.UTF_8)
        val variants = masterVariants(manifest, manifestUrl)
        if (variants.isNotEmpty()) {
            manifestUrl = variants.last()
            manifest = requestBytes(manifestUrl, headers).toString(Charsets.UTF_8)
        }

        val segments = parseSegments(manifest, manifestUrl)
        if (segments.isEmpty()) throw IOException("The HLS playlist did not contain any media segments")

        val tempDir = File(context.cacheDir, "pahe_hls_${System.currentTimeMillis()}_${SecureRandom().nextInt(9999)}")
        tempDir.mkdirs()
        val keyCache = segments
            .mapNotNull { it.keyUrl }
            .distinct()
            .associateWith { keyUrl -> requestBytes(keyUrl, headers) }

        try {
            val limiter = Semaphore(12)
            val completed = AtomicInteger(0)
            coroutineScope {
                segments.mapIndexed { index, segment ->
                    async {
                        limiter.withPermit {
                            var bytes = requestBytes(segment.url, headers)
                            if (segment.keyUrl != null && segment.iv != null) {
                                val key = keyCache[segment.keyUrl]
                                    ?: throw IOException("Missing AES key for HLS segment")
                                bytes = decryptAes128(bytes, key, segment.iv)
                            }
                            File(tempDir, "%06d.ts".format(index)).writeBytes(bytes)
                            val done = completed.incrementAndGet()
                            onProgress(done.toFloat() / segments.size.toFloat())
                        }
                    }
                }.awaitAll()
            }

            val joinedTs = File(tempDir, "joined.ts")
            joinedTs.outputStream().use { out ->
                tempDir.listFiles()
                    ?.filter { it.extension == "ts" && it.name != joinedTs.name }
                    ?.sortedBy { it.name }
                    ?.forEach { file -> file.inputStream().use { it.copyTo(out) } }
            }

            val remuxedMp4 = File(tempDir, "episode.mp4")
            val mp4Ready = remuxTsToMp4(joinedTs, remuxedMp4)
            val sourceFile = if (mp4Ready) remuxedMp4 else joinedTs
            val extension = if (mp4Ready) "mp4" else "ts"
            val mime = if (mp4Ready) "video/mp4" else "video/mp2t"

            val safeTitle = sanitize(animeTitle).ifBlank { "Anime" }
            val safeEpisode = episode.epLabel.replace(".", "_")
            val suffix = if (stream.audio == "eng") "_DUB" else ""
            val displayName = "$safeTitle - Ep $safeEpisode$suffix - ${stream.quality}p.$extension"

            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PaheBatcher")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Android could not create the output file")

            try {
                resolver.openOutputStream(uri, "w")?.use { out ->
                    sourceFile.inputStream().use { it.copyTo(out) }
                } ?: throw IOException("Android could not open the output file")

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun searchOnHost(host: String, query: String): List<AnimeSearchResult> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val body = requestText("https://$host/api?m=search&q=$encoded", referer = "https://$host/")
        val root = JSONObject(body)
        val rows = root.optJSONArray("data") ?: return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val session = item.optString("session")
                val title = item.optString("title")
                if (session.isBlank() || title.isBlank()) continue
                add(
                    AnimeSearchResult(
                        session = session,
                        title = title,
                        poster = normalizePoster(item.optString("poster"), host),
                        type = item.optString("type", "Anime"),
                        episodes = item.optInt("episodes", 0),
                        status = item.optString("status"),
                        animeId = item.optInt("id", 0).takeIf { it > 0 },
                    )
                )
            }
        }
    }

    private fun releasePage(host: String, session: String, page: Int): JSONObject {
        val primary =
            "https://$host/api?m=release&id=$session&sort=episode_asc&page=$page"
        return try {
            JSONObject(requestText(primary, referer = "https://$host/"))
        } catch (first: Exception) {
            val alternate =
                "https://$host/api/$session/releases?sort=episode_asc&page=$page"
            JSONObject(requestText(alternate, referer = "https://$host/"))
        }
    }

    private fun parseReleaseOptions(html: String): List<ReleaseOption> {
        val doc = Jsoup.parse(html)
        val options = mutableListOf<ReleaseOption>()
        val buttons = doc.select("#resolutionMenu button[data-src], button[data-src]")
        for (button in buttons) {
            val url = button.attr("data-src").trim()
            if (!url.contains("kwik.", true)) continue
            val resolution = button.attr("data-resolution").toIntOrNull()
                ?: Regex("""(\d+)\s*p""", RegexOption.IGNORE_CASE)
                    .find(button.text())?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: continue
            val attrs = button.outerHtml()
            val isDub =
                button.attr("data-audio").equals("eng", true) ||
                button.attr("class").contains("eng", true) ||
                attrs.contains("dub", true)
            val fansub = button.attr("data-fansub").ifBlank {
                button.text().substringBefore("·").trim()
            }
            options += ReleaseOption(resolution, url, isDub, fansub)
        }

        if (options.isNotEmpty()) return options.distinctBy { it.resolution to it.url }

        val fallback = Regex(
            """(?:href|data-src)=["']([^"']*kwik\.[^"']+)["'][^>]*>\s*(?:\S+\s+)?(\d+)p""",
            RegexOption.IGNORE_CASE,
        )
        return fallback.findAll(html).mapNotNull { match ->
            val url = match.groupValues.getOrNull(1).orEmpty()
            val res = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            ReleaseOption(res, url, url.contains("dub", true) || url.contains("eng", true), "")
        }.toList()
    }

    private fun resolveKwik(
        initialUrl: String,
        chosen: ReleaseOption,
    ): StreamInfo? {
        val candidates = buildList {
            add(initialUrl)
            val currentTld = runCatching { URI(initialUrl).host.orEmpty().substringAfterLast(".") }.getOrDefault("")
            val verifiedTld = sessions.kwikHost().substringAfterLast(".", missingDelimiterValue = "")
            if (verifiedTld.isNotBlank() && verifiedTld != currentTld) {
                swapKwikDomain(initialUrl, verifiedTld)?.let(::add)
            }
            for (tld in listOf("cx", "gg", "si", "me", "net", "in", "cc")) {
                if (tld != currentTld) swapKwikDomain(initialUrl, tld)?.let(::add)
            }
        }.distinct()

        var verificationError: VerificationRequired? = null
        for (url in candidates) {
            try {
                val html = requestText(url, referer = animeReferer())
                val hls = extractM3u8(html) ?: continue
                return StreamInfo(
                    url = hls,
                    cookie = sessions.kwikCookie(),
                    userAgent = sessions.userAgentFor(url),
                    referer = url,
                    quality = chosen.resolution,
                    audio = if (chosen.isDub) "eng" else "jpn",
                    fansub = chosen.fansub,
                )
            } catch (e: VerificationRequired) {
                verificationError = e
            } catch (_: Exception) {
                // Try another provider mirror.
            }
        }
        verificationError?.let { throw it }
        return null
    }

    private fun requestText(url: String, referer: String? = null): String {
        return requestBytes(url, buildMap {
            put("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            referer?.let { put("Referer", it) }
        }).toString(Charsets.UTF_8)
    }

    private fun requestBytes(url: String, extraHeaders: Map<String, String> = emptyMap()): ByteArray {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", sessions.userAgentFor(url))
            .header("Accept-Language", "en-US,en;q=0.9")
        extraHeaders.forEach { (key, value) -> builder.header(key, value) }
        if ("Cookie" !in extraHeaders) {
            sessions.cookieFor(url).takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
        }

        client.newCall(builder.build()).execute().use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            val preview = bytes.toString(Charsets.UTF_8)
            val verificationKind = kindFor(url)
            if (
                verificationKind != null &&
                (response.code == 403 || response.code == 503 || looksLikeChallenge(preview))
            ) {
                throw VerificationRequired(
                    verificationKind,
                    if (verificationKind == VerificationKind.KWIK)
                        "Kwik needs browser verification"
                    else
                        "AnimePahe needs browser verification",
                )
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from ${URI(url).host}")
            }
            return bytes
        }
    }

    private fun looksLikeChallenge(body: String): Boolean {
        if (body.length > 500_000) return false
        return body.contains("cf-chl-", true) ||
            body.contains("Just a moment", true) ||
            body.contains("challenge-platform", true)
    }

    private fun kindFor(url: String): VerificationKind? {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.US) }.getOrDefault("")
        return when {
            host.startsWith("kwik.") || host.contains(".kwik.") -> VerificationKind.KWIK
            host.contains("animepahe") || host == "pahe.win" -> VerificationKind.ANIMEPAHE
            else -> null
        }
    }

    private fun animeReferer(): String {
        val host = sessions.animeHost().ifBlank { animeHosts.first() }
        return "https://$host/"
    }

    private fun extractM3u8(html: String): String? {
        val direct = M3U8_REGEX.find(html)?.groupValues?.getOrNull(1)
        if (!direct.isNullOrBlank()) return cleanJsUrl(direct)

        val doc = Jsoup.parse(html)
        val scripts = doc.select("script")
            .map { script -> script.data().ifBlank { script.html() } }
            .sortedByDescending { it.length }

        for (script in scripts) {
            var current = script
            repeat(6) {
                val inner = INNER_EVAL_REGEX.find(current)?.groupValues?.getOrNull(1)
                if (!inner.isNullOrBlank()) {
                    current = decodeJsEscapes(inner)
                } else {
                    val unpacked = unpackJs(current)
                    if (unpacked != current) current = unpacked
                }
            }
            M3U8_REGEX.find(current)?.groupValues?.getOrNull(1)?.let {
                return cleanJsUrl(it)
            }
        }

        val source = Regex(
            """<source[^>]+src=["']([^"']+\.m3u8[^"']*)["']""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.getOrNull(1)
        return source?.let(::cleanJsUrl)
    }

    private fun unpackJs(packed: String): String {
        val match = PACKER_REGEX.find(packed) ?: return packed
        val payload = match.groupValues[1]
        val base = match.groupValues[2].toIntOrNull() ?: return packed
        val count = match.groupValues[3].toIntOrNull() ?: return packed
        val mapping = match.groupValues[4].split("|")
        val digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        if (base !in 2..digits.length) return packed

        fun encode(value: Int): String {
            return if (value < base) {
                digits[value].toString()
            } else {
                encode(value / base) + digits[value % base]
            }
        }

        val lookup = HashMap<String, String>()
        for (index in 0 until count) {
            val encoded = encode(index)
            lookup[encoded] = mapping.getOrNull(index)?.takeIf { it.isNotEmpty() } ?: encoded
        }
        return Regex("""\b\w+\b""").replace(payload) { word ->
            lookup[word.value] ?: word.value
        }
    }

    private fun decodeJsEscapes(value: String): String {
        var result = value
            .replace("\\/", "/")
            .replace("\\'", "'")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")

        result = Regex("""\\u([0-9a-fA-F]{4})""").replace(result) {
            it.groupValues[1].toInt(16).toChar().toString()
        }
        result = Regex("""\\x([0-9a-fA-F]{2})""").replace(result) {
            it.groupValues[1].toInt(16).toChar().toString()
        }
        return result
    }

    private fun cleanJsUrl(url: String): String =
        url.replace("\\/", "/").trimEnd('\\')

    private fun masterVariants(content: String, baseUrl: String): List<String> {
        val lines = content.lines()
        val out = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            if (line.trim().startsWith("#EXT-X-STREAM-INF") && index + 1 < lines.size) {
                val next = lines[index + 1].trim()
                if (next.isNotBlank() && !next.startsWith("#")) out += resolveUrl(baseUrl, next)
            }
        }
        return out
    }

    private fun parseSegments(content: String, baseUrl: String): List<Segment> {
        val out = mutableListOf<Segment>()
        var keyUrl: String? = null
        var explicitIv: ByteArray? = null
        var sequence = 0L
        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    sequence = line.substringAfter(":").toLongOrNull() ?: sequence
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    if (line.contains("METHOD=AES-128", true)) {
                        val uri = Regex("""URI="([^"]+)"""").find(line)?.groupValues?.getOrNull(1)
                        keyUrl = uri?.let { resolveUrl(baseUrl, it) }
                        val ivHex = Regex("""IV=0x([0-9a-fA-F]+)""").find(line)?.groupValues?.getOrNull(1)
                        explicitIv = ivHex?.let(::hexIv)
                    } else {
                        keyUrl = null
                        explicitIv = null
                    }
                }
                line.isNotBlank() && !line.startsWith("#") -> {
                    val iv = if (keyUrl != null) explicitIv ?: sequenceIv(sequence) else null
                    out += Segment(resolveUrl(baseUrl, line), keyUrl, iv)
                    sequence++
                }
            }
        }
        return out
    }

    private fun remuxTsToMp4(input: File, output: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        return try {
            extractor.setDataSource(input.absolutePath)
            if (extractor.trackCount == 0) return false

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackMap = mutableMapOf<Int, Int>()
            for (track in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(track)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                trackMap[track] = muxer.addTrack(format)
                extractor.selectTrack(track)
            }
            if (trackMap.isEmpty()) return false

            muxer.start()
            muxerStarted = true
            val buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()

            while (true) {
                val sourceTrack = extractor.sampleTrackIndex
                if (sourceTrack < 0) break
                val targetTrack = trackMap[sourceTrack]
                if (targetTrack == null) {
                    extractor.advance()
                    continue
                }
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime.coerceAtLeast(0L), extractor.sampleFlags)
                muxer.writeSampleData(targetTrack, buffer, info)
                extractor.advance()
            }

            muxer.stop()
            muxerStarted = false
            output.exists() && output.length() > 0L
        } catch (_: Exception) {
            false
        } finally {
            runCatching { extractor.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            if (!output.exists() || output.length() == 0L) output.delete()
        }
    }

    private fun decryptAes128(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun sequenceIv(sequence: Long): ByteArray {
        val iv = ByteArray(16)
        var value = sequence
        for (index in 15 downTo 0) {
            iv[index] = (value and 0xFF).toByte()
            value = value ushr 8
        }
        return iv
    }

    private fun hexIv(hex: String): ByteArray {
        val padded = hex.padStart(32, '0').takeLast(32)
        return ByteArray(16) { index ->
            padded.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun resolveUrl(base: String, child: String): String =
        URI(base).resolve(child).toString()

    private fun swapKwikDomain(url: String, tld: String): String? {
        return runCatching {
            val uri = URI(url)
            val host = uri.host ?: return@runCatching null
            val parts = host.split(".").toMutableList()
            if (parts.size < 2) return@runCatching null
            parts[parts.lastIndex] = tld
            URI(uri.scheme, uri.userInfo, parts.joinToString("."), uri.port, uri.path, uri.query, uri.fragment)
                .toString()
        }.getOrNull()
    }

    private fun normalizePoster(value: String, host: String): String = when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://$host$value"
        value.startsWith("http") -> value
        value.isBlank() -> ""
        else -> "https://$host/$value"
    }

    private fun normalizeTitle(value: String): String =
        value.lowercase(Locale.US)
            .replace(Regex("""[^\p{L}\p{N}\s]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun sanitize(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(120)

    companion object {
        private val M3U8_REGEX = Regex(
            """(https?://[^\s'"\\>]+(?:uwu\.m3u8|\.m3u8)[^\s'"\\>]*)""",
            setOf(RegexOption.IGNORE_CASE),
        )
        private val INNER_EVAL_REGEX = Regex(
            """eval\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        private val PACKER_REGEX = Regex(
            """\}\s*\(\s*'(.*)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'(.*?)'\.split\('\|'\)""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
    }
}
