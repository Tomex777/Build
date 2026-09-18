package com.night.kwikmanual

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class ManualOutcome { PASS, FAIL, INFO }

data class ManualCheck(
    val label: String,
    val outcome: ManualOutcome,
    val detail: String,
)

data class ManualResult(
    val checks: List<ManualCheck>,
    val report: String,
)

private data class RawResponse(
    val code: Int,
    val finalUrl: String,
    val contentType: String,
    val bytes: ByteArray,
    val challenged: Boolean,
) {
    val text: String get() = bytes.toString(Charsets.UTF_8)
    val ok: Boolean get() = code in 200..299 && !challenged
}

class KwikManualEngine(
    private val sessions: ManualSessionStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun test(url: String, referer: String): ManualResult = withContext(Dispatchers.IO) {
        val checks = mutableListOf<ManualCheck>()
        val host = hostOf(url)

        if (!isKwikHost(host)) {
            checks += ManualCheck(
                "Input URL",
                ManualOutcome.FAIL,
                "Host '$host' does not look like a Kwik host.",
            )
            return@withContext result(checks, url, referer)
        }

        checks += ManualCheck(
            "Input URL",
            ManualOutcome.PASS,
            "Host=$host - path=${safePath(url)}",
        )

        val page = request(
            url = url,
            referer = referer,
            cookie = sessions.kwikCookie(),
            userAgent = sessions.userAgent(),
        )

        checks += ManualCheck(
            "Kwik page",
            if (page.ok) ManualOutcome.PASS else ManualOutcome.FAIL,
            describe(page, url),
        )

        if (!page.ok) {
            return@withContext result(checks, url, referer)
        }

        val hls = extractM3u8(page.text)
        if (hls.isNullOrBlank()) {
            checks += ManualCheck(
                "Stream extraction",
                ManualOutcome.FAIL,
                "Kwik page loaded, but no HLS URL could be extracted from the HTML/scripts.",
            )
            return@withContext result(checks, url, referer)
        }

        checks += ManualCheck(
            "Stream extraction",
            ManualOutcome.PASS,
            "Extracted an HLS URL from the Kwik page.",
        )

        val manifest = request(
            url = hls,
            referer = url,
            cookie = "",
            userAgent = sessions.userAgent(),
        )

        val isManifest = manifest.ok && manifest.text.trimStart().startsWith("#EXTM3U")
        checks += ManualCheck(
            "HLS manifest",
            if (isManifest) ManualOutcome.PASS else ManualOutcome.FAIL,
            describe(manifest, hls) + " - extm3u=$isManifest",
        )

        if (isManifest) {
            val variantCount = masterVariants(manifest.text).size
            checks += ManualCheck(
                "Downstream pipeline",
                ManualOutcome.PASS,
                "Kwik page -> unpacking -> HLS manifest succeeded. masterVariants=$variantCount",
            )
        } else {
            checks += ManualCheck(
                "Downstream pipeline",
                ManualOutcome.FAIL,
                "The stream URL was extracted, but the manifest could not be read successfully.",
            )
        }

        result(checks, url, referer)
    }

    private fun request(
        url: String,
        referer: String,
        cookie: String,
        userAgent: String,
    ): RawResponse {
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept", "text/html,application/vnd.apple.mpegurl,application/json")

            if (referer.isNotBlank()) builder.header("Referer", referer)
            if (cookie.isNotBlank()) builder.header("Cookie", cookie)

            client.newCall(builder.build()).execute().use { response ->
                val bytes = response.body?.bytes() ?: ByteArray(0)
                val preview = bytes.toString(Charsets.UTF_8)
                RawResponse(
                    code = response.code,
                    finalUrl = response.request.url.toString(),
                    contentType = response.header("Content-Type").orEmpty(),
                    bytes = bytes,
                    challenged = looksLikeChallenge(preview),
                )
            }
        } catch (error: Exception) {
            RawResponse(
                code = -1,
                finalUrl = url,
                contentType = "",
                bytes = (error.javaClass.simpleName + ": " + (error.message ?: "request failed")).toByteArray(),
                challenged = false,
            )
        }
    }

    private fun describe(response: RawResponse, requestedUrl: String): String {
        val status = if (response.code < 0) "network-error" else "HTTP ${response.code}"
        val challenge = if (response.challenged) " - Cloudflare/challenge detected" else ""
        val redirect = if (response.finalUrl != requestedUrl) {
            " - finalHost=${hostOf(response.finalUrl)}"
        } else {
            ""
        }
        val error = if (response.code < 0) " - ${response.text.take(180)}" else ""
        return "$status - type=${response.contentType.ifBlank { "unknown" }} - bytes=${response.bytes.size}$challenge$redirect$error"
    }

    private fun result(
        checks: List<ManualCheck>,
        url: String,
        referer: String,
    ): ManualResult {
        val snapshot = sessions.snapshot()
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val body = checks.joinToString("\n") { check ->
            "[${check.outcome}] ${check.label}: ${check.detail}"
        }

        val report = """
KWIK MANUAL DIAGNOSTICS 0.1
Time: $utc
Device: ${Build.MANUFACTURER} ${Build.MODEL}
Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
Tor packaged in APK: NO

Kwik host: ${snapshot.kwikHost.ifBlank { "none" }}
Kwik cookies: ${snapshot.cookieSummary}
Input host: ${hostOf(url)}
Input path: ${safePath(url)}
Referer host: ${hostOf(referer)}
User-Agent: ${sessions.userAgent().take(220)}

$body
""".trim()

        return ManualResult(checks, report)
    }

    private fun extractM3u8(html: String): String? {
        val direct = M3U8_REGEX.find(html)?.groupValues?.getOrNull(1)
        if (!direct.isNullOrBlank()) return cleanJsUrl(direct)

        val scripts = Jsoup.parse(html).select("script")
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

        return Regex(
            """<source[^>]+src=["']([^"']+\.m3u8[^"']*)["']""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.getOrNull(1)?.let(::cleanJsUrl)
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

    private fun masterVariants(content: String): List<String> {
        val lines = content.lines()
        val out = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            if (line.trim().startsWith("#EXT-X-STREAM-INF") && index + 1 < lines.size) {
                val next = lines[index + 1].trim()
                if (next.isNotBlank() && !next.startsWith("#")) out += next
            }
        }
        return out
    }

    private fun looksLikeChallenge(body: String): Boolean {
        if (body.length > 750_000) return false
        return body.contains("cf-chl-", true) ||
            body.contains("Just a moment", true) ||
            body.contains("challenge-platform", true) ||
            body.contains("Checking your browser", true) ||
            body.contains("cf-turnstile", true)
    }

    private fun isKwikHost(host: String): Boolean =
        host.startsWith("kwik.") || host.contains(".kwik.")

    private fun hostOf(url: String): String =
        runCatching { URI(url).host.orEmpty().lowercase(Locale.US) }.getOrDefault("")

    private fun safePath(url: String): String =
        runCatching { URI(url).path.orEmpty().take(180) }.getOrDefault("")

    companion object {
        private val M3U8_REGEX = Regex(
            """(https?://[^\s'"\\>]+(?:uwu\.m3u8|\.m3u8)[^\s'"\\>]*)""",
            RegexOption.IGNORE_CASE,
        )
        private val INNER_EVAL_REGEX = Regex(
            """eval\s*\(\s*"((?:[^"\\]|\\.)*)"\s*\)""",
            RegexOption.DOT_MATCHES_ALL,
        )
        private val PACKER_REGEX = Regex(
            """\}\s*\(\s*'(.*)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'(.*?)'\.split\('\|'\)""",
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}
