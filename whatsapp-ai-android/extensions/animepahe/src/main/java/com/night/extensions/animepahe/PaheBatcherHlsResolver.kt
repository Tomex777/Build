package com.night.extensions.animepahe

import java.io.IOException
import java.net.URI
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Night port of the HLS resolver already proven in the PaheBatcher Android app.
 *
 * AnimePahe browser verification is handled before this stage. Kwik is resolved
 * automatically from the /e/ source URL and never introduces a second manual
 * verification step.
 */
class PaheBatcherHlsResolver(
    private val session: AnimePaheSession,
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build(),
) {
    fun resolve(
        source: AnimePaheSource,
        playUrl: String,
    ): AnimePaheResolvedMedia {
        val initialUrl = source.kwikUrl.trim()
        require(initialUrl.startsWith("http")) {
            "AnimePahe did not provide a valid Kwik stream URL."
        }

        var lastError: Exception? = null
        for (url in kwikCandidates(initialUrl)) {
            try {
                val html = requestText(
                    url = url,
                    referer = playUrl,
                )
                val hls = extractM3u8(html)
                    ?: throw IOException(
                        "Kwik page loaded, but no HLS URL could be extracted."
                    )
                return AnimePaheResolvedMedia(
                    url = hls,
                    mimeType = HLS_MIME_TYPE,
                    headers =
                        buildMap {
                            put("User-Agent", session.userAgent())
                            put("Referer", url)
                            session.cookieForUrl(hls)
                                .takeIf { it.isNotBlank() }
                                ?.let { put("Cookie", it) }
                        },
                )
            } catch (error: Exception) {
                lastError = error
            }
        }

        throw lastError
            ?: IOException("Kwik stream resolver could not extract an HLS source.")
    }

    private fun requestText(
        url: String,
        referer: String,
    ): String {
        val builder =
            Request.Builder()
                .url(url)
                .header("User-Agent", session.userAgent())
                .header("Accept-Language", "en-US,en;q=0.9")
                .header(
                    "Accept",
                    "text/html,application/vnd.apple.mpegurl,application/json",
                )
                .header("Referer", referer)

        session.cookieForUrl(url)
            .takeIf { it.isNotBlank() }
            ?.let { builder.header("Cookie", it) }

        client.newCall(builder.build()).execute().use { response ->
            session.absorbResponseCookies(response)
            if (!response.isSuccessful) {
                throw IOException(
                    "Kwik returned HTTP ${response.code} from ${response.request.url.host}."
                )
            }
            return response.body?.string().orEmpty()
        }
    }

    internal fun extractM3u8(html: String): String? {
        val direct = M3U8_REGEX.find(html)?.groupValues?.getOrNull(1)
        if (!direct.isNullOrBlank()) return cleanJsUrl(direct)

        val scripts =
            Jsoup.parse(html)
                .select("script")
                .map { script -> script.data().ifBlank { script.html() } }
                .sortedByDescending { it.length }

        for (script in scripts) {
            var current = script
            repeat(6) {
                val inner =
                    INNER_EVAL_REGEX.find(current)
                        ?.groupValues
                        ?.getOrNull(1)
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

        return Regex(
            """<source[^>]+src=["']([^"']+\.m3u8[^"']*)["']""",
            RegexOption.IGNORE_CASE,
        ).find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::cleanJsUrl)
    }

    internal fun unpackJs(packed: String): String {
        val match = PACKER_REGEX.find(packed) ?: return packed
        val payload = match.groupValues[1]
        val base = match.groupValues[2].toIntOrNull() ?: return packed
        val count = match.groupValues[3].toIntOrNull() ?: return packed
        val mapping = match.groupValues[4].split("|")
        val digits =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        if (base !in 2..digits.length) return packed

        fun encode(value: Int): String =
            if (value < base) {
                digits[value].toString()
            } else {
                encode(value / base) + digits[value % base]
            }

        val lookup = HashMap<String, String>()
        for (index in 0 until count) {
            val encoded = encode(index)
            lookup[encoded] =
                mapping.getOrNull(index)
                    ?.takeIf { it.isNotEmpty() }
                    ?: encoded
        }

        return Regex("""\b\w+\b""").replace(payload) { word ->
            lookup[word.value] ?: word.value
        }
    }

    private fun decodeJsEscapes(value: String): String {
        var result =
            value
                .replace("\\/", "/")
                .replace("\\'", "'")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")

        result =
            Regex("""\\u([0-9a-fA-F]{4})""").replace(result) {
                it.groupValues[1].toInt(16).toChar().toString()
            }
        result =
            Regex("""\\x([0-9a-fA-F]{2})""").replace(result) {
                it.groupValues[1].toInt(16).toChar().toString()
            }
        return result
    }

    private fun cleanJsUrl(url: String): String =
        url.replace("\\/", "/").trimEnd('\\')

    private fun kwikCandidates(initialUrl: String): List<String> {
        val currentTld =
            runCatching {
                URI(initialUrl).host.orEmpty().substringAfterLast(".")
            }.getOrDefault("")

        return buildList {
            add(initialUrl)
            for (tld in KWIK_TLDS) {
                if (tld != currentTld) {
                    swapKwikDomain(initialUrl, tld)?.let(::add)
                }
            }
        }.distinct()
    }

    private fun swapKwikDomain(
        url: String,
        tld: String,
    ): String? =
        runCatching {
            val uri = URI(url)
            val host = uri.host ?: return@runCatching null
            val parts = host.split(".").toMutableList()
            if (parts.size < 2) return@runCatching null
            parts[parts.lastIndex] = tld
            URI(
                uri.scheme,
                uri.userInfo,
                parts.joinToString("."),
                uri.port,
                uri.path,
                uri.query,
                uri.fragment,
            ).toString()
        }.getOrNull()

    companion object {
        const val HLS_MIME_TYPE = "application/vnd.apple.mpegurl"

        private val KWIK_TLDS =
            listOf("cx", "gg", "si", "me", "net", "in", "cc")

        private val M3U8_REGEX =
            Regex(
                """(https?://[^\s'"\\>]+(?:uwu\.m3u8|\.m3u8)[^\s'"\\>]*)""",
                RegexOption.IGNORE_CASE,
            )

        private val INNER_EVAL_REGEX =
            Regex(
                """eval\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)""",
                RegexOption.DOT_MATCHES_ALL,
            )

        private val PACKER_REGEX =
            Regex(
                """\}\s*\(\s*'(.*)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'(.*?)'\.split\('\|'\)""",
                RegexOption.DOT_MATCHES_ALL,
            )
    }
}
