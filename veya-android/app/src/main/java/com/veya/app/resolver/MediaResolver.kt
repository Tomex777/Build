package com.veya.app.resolver

import com.veya.app.model.ResolvedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLConnection

sealed interface ResolveResult {
    data class Success(val options: List<ResolvedMedia>, val pageTitle: String? = null) : ResolveResult
    data class Error(val message: String) : ResolveResult
}

/**
 * Resolves media that a page exposes directly through normal HTTP metadata:
 * direct video/audio URLs, OpenGraph media, twitter:player:stream and HTML5
 * <video>/<audio>/<source> elements.
 *
 * It deliberately does not decipher protected player signatures, DRM, or hidden
 * service-specific playback APIs.
 */
class MediaResolver {
    suspend fun resolve(raw: String): ResolveResult = withContext(Dispatchers.IO) {
        val input = raw.trim()
        if (!input.startsWith("https://", ignoreCase = true)) {
            return@withContext ResolveResult.Error("Veya only accepts secure HTTPS links.")
        }

        runCatching {
            val probe = probe(input)
            if (isDownloadableMedia(probe.finalUrl, probe.contentType)) {
                return@withContext ResolveResult.Success(
                    listOf(toMedia(probe.finalUrl, probe, null))
                )
            }

            val mime = probe.contentType.substringBefore(';').lowercase()
            if (!mime.contains("html") && mime.isNotBlank()) {
                return@withContext ResolveResult.Error("This link does not expose a downloadable video or audio file.")
            }

            val page = fetchHtml(probe.finalUrl)
            val candidates = discoverMediaUrls(page.html, page.finalUrl)
            val options = candidates.take(MAX_CANDIDATES_TO_PROBE).mapNotNull { candidate ->
                runCatching {
                    val p = probe(candidate)
                    if (isDownloadableMedia(p.finalUrl, p.contentType)) toMedia(p.finalUrl, p, page.title) else null
                }.getOrNull()
            }.distinctBy { it.url }

            if (options.isEmpty()) {
                ResolveResult.Error(
                    "No ordinary downloadable media was exposed by this page. Protected/DRM or service-specific streams are intentionally not bypassed."
                )
            } else {
                ResolveResult.Success(options = options, pageTitle = page.title)
            }
        }.getOrElse { ResolveResult.Error(it.message ?: "Could not inspect this link.") }
    }

    private data class Probe(
        val finalUrl: String,
        val contentType: String,
        val length: Long,
        val disposition: String?,
    )

    private data class HtmlPage(val finalUrl: String, val html: String, val title: String?)

    private fun probe(url: String): Probe {
        fun connect(method: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Encoding", "identity")
            if (method == "GET") setRequestProperty("Range", "bytes=0-0")
        }

        var conn = connect("HEAD")
        var code = conn.responseCode
        if (code !in 200..399 || conn.contentType.isNullOrBlank()) {
            conn.disconnect()
            conn = connect("GET")
            code = conn.responseCode
        }
        if (code !in 200..399) {
            conn.disconnect()
            error("Server returned HTTP $code")
        }
        val totalFromRange = conn.getHeaderField("Content-Range")
            ?.substringAfter('/')?.toLongOrNull()
        val result = Probe(
            finalUrl = conn.url.toString(),
            contentType = conn.contentType.orEmpty(),
            length = totalFromRange ?: conn.contentLengthLong,
            disposition = conn.getHeaderField("Content-Disposition")
        )
        runCatching { conn.inputStream.close() }
        conn.disconnect()
        return result
    }

    private fun fetchHtml(url: String): HtmlPage {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }
        val code = conn.responseCode
        if (code !in 200..399) {
            conn.disconnect()
            error("Page returned HTTP $code")
        }
        val bytes = ByteArrayOutputStream().use { out ->
            conn.inputStream.buffered().use { input ->
                val buffer = ByteArray(32 * 1024)
                var remaining = MAX_HTML_BYTES
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    remaining -= read
                }
            }
            out.toByteArray()
        }
        val charset = conn.contentType
            ?.substringAfter("charset=", "")
            ?.trim()?.takeIf { it.isNotBlank() }
            ?.let { runCatching { charset(it) }.getOrNull() }
            ?: Charsets.UTF_8
        val finalUrl = conn.url.toString()
        conn.disconnect()
        val html = bytes.toString(charset)
        return HtmlPage(finalUrl, html, extractPageTitle(html))
    }

    private fun discoverMediaUrls(html: String, baseUrl: String): List<String> {
        val found = linkedSetOf<String>()
        val tags = Regex("<(?:meta|video|audio|source)\\b[^>]*>", RegexOption.IGNORE_CASE)
        for (match in tags.findAll(html)) {
            val tag = match.value
            val attrs = parseAttributes(tag)
            val marker = (attrs["property"] ?: attrs["name"]).orEmpty().lowercase()
            val raw = when {
                marker in MEDIA_META_KEYS -> attrs["content"]
                tag.startsWith("<video", true) || tag.startsWith("<audio", true) || tag.startsWith("<source", true) -> attrs["src"]
                else -> null
            } ?: continue
            resolveUrl(baseUrl, decodeEntities(raw))?.let(found::add)
        }
        return found.toList()
    }

    private fun parseAttributes(tag: String): Map<String, String> {
        val attrs = linkedMapOf<String, String>()
        val regex = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*([\"'])(.*?)\\2", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        regex.findAll(tag).forEach { m -> attrs[m.groupValues[1].lowercase()] = m.groupValues[3] }
        return attrs
    }

    private fun extractPageTitle(html: String): String? {
        val metaTags = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        for (m in metaTags.findAll(html)) {
            val attrs = parseAttributes(m.value)
            if ((attrs["property"] ?: attrs["name"]).orEmpty().equals("og:title", true)) {
                return attrs["content"]?.let(::decodeEntities)?.trim()?.take(160)
            }
        }
        return Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")
            ?.let(::decodeEntities)?.trim()?.take(160)
    }

    private fun resolveUrl(base: String, raw: String): String? = runCatching {
        val resolved = URI(base).resolve(raw.trim()).toString()
        resolved.takeIf { it.startsWith("https://", true) }
    }.getOrNull()

    private fun decodeEntities(value: String): String = value
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)

    private fun isDownloadableMedia(url: String, contentType: String): Boolean {
        val mime = contentType.substringBefore(';').lowercase()
        val path = runCatching { URI(url).path.orEmpty() }.getOrDefault("")
        val extensionMime = URLConnection.guessContentTypeFromName(path).orEmpty().lowercase()
        return mime.startsWith("video/") || mime.startsWith("audio/") ||
            extensionMime.startsWith("video/") || extensionMime.startsWith("audio/") ||
            MEDIA_EXTENSIONS.any { path.endsWith(it, true) }
    }

    private fun toMedia(url: String, probe: Probe, pageTitle: String?): ResolvedMedia {
        val mime = probe.contentType.substringBefore(';').lowercase()
        val fileName = pickFileName(url, probe.disposition, mime)
        val fallbackTitle = fileName.substringBeforeLast('.', fileName)
        return ResolvedMedia(
            url = url,
            title = pageTitle?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            fileName = fileName,
            mimeType = mime.ifBlank {
                URLConnection.guessContentTypeFromName(fileName).orEmpty().ifBlank { "application/octet-stream" }
            },
            sizeBytes = probe.length,
            host = URI(url).host.orEmpty().removePrefix("www."),
        )
    }

    private fun pickFileName(url: String, disposition: String?, mime: String): String {
        val utf8 = disposition?.let {
            Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1)
        }?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        val simple = disposition
            ?.substringAfter("filename=", "")
            ?.substringBefore(';')
            ?.trim()?.trim('"', '\'')
            ?.takeIf { it.isNotBlank() }
        val fromPath = runCatching { URI(url).path.substringAfterLast('/') }.getOrNull()
            ?.substringBefore('?')?.takeIf { it.contains('.') }
        val base = utf8 ?: simple ?: fromPath ?: "veya_${System.currentTimeMillis()}.${extensionFor(mime)}"
        return sanitize(base)
    }

    private fun extensionFor(mime: String) = when {
        mime.contains("mp4") -> "mp4"
        mime.contains("webm") -> "webm"
        mime.contains("mpeg") -> "mp3"
        mime.contains("m4a") || mime.contains("mp4a") -> "m4a"
        mime.contains("quicktime") -> "mov"
        else -> "bin"
    }

    private fun sanitize(name: String): String = name
        .replace(Regex("[\\/:*?\"<>|]"), "_")
        .take(180)
        .ifBlank { "veya_download" }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Veya/0.1"
        private const val MAX_HTML_BYTES = 2 * 1024 * 1024
        private const val MAX_CANDIDATES_TO_PROBE = 10
        private val MEDIA_META_KEYS = setOf(
            "og:video", "og:video:url", "og:video:secure_url",
            "og:audio", "og:audio:url", "og:audio:secure_url",
            "twitter:player:stream"
        )
        private val MEDIA_EXTENSIONS = setOf(".mp4", ".m4a", ".webm", ".mp3", ".mov", ".mkv", ".aac", ".ogg", ".wav")
    }
}
