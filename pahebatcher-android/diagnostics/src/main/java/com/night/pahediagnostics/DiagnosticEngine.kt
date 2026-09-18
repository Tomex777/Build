package com.night.pahediagnostics

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class Outcome { PASS, FAIL, INFO }

data class DiagnosticCheck(
    val label: String,
    val outcome: Outcome,
    val detail: String,
)

data class DiagnosticResult(
    val checks: List<DiagnosticCheck>,
    val report: String,
    val kwikUrl: String? = null,
)

private data class RawResponse(
    val code: Int,
    val finalUrl: String,
    val contentType: String,
    val bytes: ByteArray,
    val challenged: Boolean,
) {
    val text: String
        get() = bytes.toString(Charsets.UTF_8)

    val ok: Boolean
        get() = code in 200..299 && !challenged
}

class DiagnosticEngine(
    private val sessions: DiagnosticSessionStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun runAnimePahe(): DiagnosticResult = withContext(Dispatchers.IO) {
        val checks = mutableListOf<DiagnosticCheck>()
        val savedHost = sessions.animeHost()
        val hosts = buildList {
            if (savedHost.isNotBlank()) add(savedHost)
            add("animepahe.pw")
            add("animepahe.com")
            add("animepahe.org")
        }.distinct()

        var chosenHost: String? = null
        var chosenItem: JSONObject? = null

        for (host in hosts) {
            val rootUrl = "https://$host/"
            val root = request(rootUrl)
            checks += DiagnosticCheck(
                label = "Domain - $host",
                outcome = if (root.ok) Outcome.PASS else Outcome.FAIL,
                detail = describe(root, rootUrl),
            )

            val searchUrl = "https://$host/api?m=search&q=" +
                URLEncoder.encode("bleach", StandardCharsets.UTF_8.toString())
            val search = request(searchUrl, referer = rootUrl)
            if (!search.ok) {
                checks += DiagnosticCheck(
                    label = "Search - $host",
                    outcome = Outcome.FAIL,
                    detail = describe(search, searchUrl),
                )
                continue
            }

            val parsed = runCatching { JSONObject(search.text) }.getOrNull()
            val rows = parsed?.optJSONArray("data")
            if (rows == null) {
                checks += DiagnosticCheck(
                    label = "Search - $host",
                    outcome = Outcome.FAIL,
                    detail = describe(search, searchUrl) + " - response was not the expected JSON data array",
                )
                continue
            }

            var exact: JSONObject? = null
            var first: JSONObject? = null
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                if (first == null) first = item
                if (item.optString("title").equals("Bleach", ignoreCase = true)) {
                    exact = item
                    break
                }
            }

            val item = exact ?: first
            if (item == null || item.optString("session").isBlank()) {
                checks += DiagnosticCheck(
                    label = "Search - $host",
                    outcome = Outcome.FAIL,
                    detail = "HTTP ${search.code} JSON was valid, but no usable anime session was returned",
                )
                continue
            }

            chosenHost = host
            chosenItem = item
            checks += DiagnosticCheck(
                label = "Search - $host",
                outcome = Outcome.PASS,
                detail = "HTTP ${search.code} - results=${rows.length()} - selected=${item.optString("title")} - id=${item.optInt("id", 0)} - session=${shortToken(item.optString("session"))}",
            )
            break
        }

        val host = chosenHost
        val item = chosenItem
        if (host == null || item == null) {
            checks += DiagnosticCheck(
                "AnimePahe pipeline",
                Outcome.FAIL,
                "No official AnimePahe host returned usable search JSON on this device/session.",
            )
            return@withContext result(checks, null)
        }

        val poster = normalizePoster(item.optString("poster"), host)
        if (poster.isBlank()) {
            checks += DiagnosticCheck("Poster", Outcome.FAIL, "Search JSON did not contain a poster URL")
        } else {
            val posterResponse = request(poster, referer = "https://$host/")
            val imageType = posterResponse.contentType.lowercase(Locale.US).startsWith("image/")
            checks += DiagnosticCheck(
                "Poster",
                if (posterResponse.ok && imageType) Outcome.PASS else Outcome.FAIL,
                describe(posterResponse, poster) + " - imageContentType=$imageType",
            )
        }

        val animeSession = item.optString("session")
        val releaseUrls = listOf(
            "https://$host/api?m=release&id=$animeSession&sort=episode_asc&page=1",
            "https://$host/api/$animeSession/releases?sort=episode_asc&page=1",
        )

        var episodeSession: String? = null
        var releaseLabel = ""
        for ((index, url) in releaseUrls.withIndex()) {
            val response = request(url, referer = "https://$host/")
            if (!response.ok) {
                checks += DiagnosticCheck(
                    if (index == 0) "Release API" else "Legacy release API",
                    Outcome.FAIL,
                    describe(response, url),
                )
                continue
            }

            val parsed = runCatching { JSONObject(response.text) }.getOrNull()
            val rows = parsed?.optJSONArray("data")
            if (rows == null) {
                checks += DiagnosticCheck(
                    if (index == 0) "Release API" else "Legacy release API",
                    Outcome.FAIL,
                    describe(response, url) + " - expected JSON data array missing",
                )
                continue
            }

            for (rowIndex in 0 until rows.length()) {
                val row = rows.optJSONObject(rowIndex) ?: continue
                val candidate = row.optString("session")
                if (candidate.isNotBlank()) {
                    episodeSession = candidate
                    releaseLabel = row.optDouble("episode", 0.0).toString()
                    break
                }
            }

            if (episodeSession != null) {
                checks += DiagnosticCheck(
                    if (index == 0) "Release API" else "Legacy release API",
                    Outcome.PASS,
                    "HTTP ${response.code} - rows=${rows.length()} - episode=$releaseLabel - session=${shortToken(episodeSession.orEmpty())}",
                )
                break
            }
        }

        val epSession = episodeSession
        if (epSession == null) {
            checks += DiagnosticCheck(
                "AnimePahe pipeline",
                Outcome.FAIL,
                "Search worked, but neither release endpoint returned a usable episode session.",
            )
            return@withContext result(checks, null)
        }

        val playUrl = "https://$host/play/$animeSession/$epSession"
        val play = request(playUrl, referer = "https://$host/")
        if (!play.ok) {
            checks += DiagnosticCheck("Play page", Outcome.FAIL, describe(play, playUrl))
            return@withContext result(checks, null)
        }

        val kwikUrl = extractKwikUrl(play.text)
        if (kwikUrl.isNullOrBlank()) {
            checks += DiagnosticCheck(
                "Play page",
                Outcome.FAIL,
                describe(play, playUrl) + " - no Kwik release URL found in HTML",
            )
            return@withContext result(checks, null)
        }

        sessions.saveLastKwikUrl(kwikUrl)
        checks += DiagnosticCheck(
            "Play page",
            Outcome.PASS,
            describe(play, playUrl) + " - Kwik=" + safeUrl(kwikUrl),
        )
        checks += DiagnosticCheck(
            "AnimePahe pipeline",
            Outcome.PASS,
            "Search -> release -> play completed on $host.",
        )
        result(checks, kwikUrl)
    }

    suspend fun runKwik(urlOverride: String? = null): DiagnosticResult = withContext(Dispatchers.IO) {
        val checks = mutableListOf<DiagnosticCheck>()
        val initial = urlOverride?.takeIf { it.isNotBlank() } ?: sessions.lastKwikUrl()
        if (initial.isBlank()) {
            checks += DiagnosticCheck(
                "Kwik pipeline",
                Outcome.FAIL,
                "No Kwik release URL is cached yet. Run the AnimePahe test first.",
            )
            return@withContext result(checks, null)
        }

        val candidates = kwikCandidates(initial)
        var lastUsableUrl: String? = null

        for (url in candidates) {
            val response = request(
                url = url,
                referer = animeReferer(),
                userAgent = sessions.kwikUserAgent(),
            )

            if (!response.ok) {
                checks += DiagnosticCheck(
                    "Kwik - ${hostOf(url)}",
                    Outcome.FAIL,
                    describe(response, url),
                )
                continue
            }

            val hls = extractM3u8(response.text)
            if (hls.isNullOrBlank()) {
                checks += DiagnosticCheck(
                    "Kwik - ${hostOf(url)}",
                    Outcome.FAIL,
                    describe(response, url) + " - page loaded but no HLS URL was extracted",
                )
                continue
            }

            lastUsableUrl = url
            checks += DiagnosticCheck(
                "Kwik - ${hostOf(url)}",
                Outcome.PASS,
                describe(response, url) + " - HLS extracted",
            )

            val manifest = request(
                url = hls,
                referer = url,
                userAgent = sessions.kwikUserAgent(),
                cookieOverride = "",
            )
            val isManifest = manifest.ok && manifest.text.trimStart().startsWith("#EXTM3U")
            checks += DiagnosticCheck(
                "HLS manifest",
                if (isManifest) Outcome.PASS else Outcome.FAIL,
                describe(manifest, hls) + " - extm3u=$isManifest",
            )

            if (isManifest) {
                checks += DiagnosticCheck(
                    "Kwik pipeline",
                    Outcome.PASS,
                    "Kwik page -> unpacked stream URL -> HLS manifest completed.",
                )
                return@withContext result(checks, lastUsableUrl)
            }
        }

        checks += DiagnosticCheck(
            "Kwik pipeline",
            Outcome.FAIL,
            "No tested Kwik domain produced a readable HLS manifest.",
        )
        result(checks, lastUsableUrl)
    }

    suspend fun runFullPipeline(): DiagnosticResult = withContext(Dispatchers.IO) {
        val anime = runAnimePahe()
        val kwikUrl = anime.kwikUrl
        if (kwikUrl.isNullOrBlank()) {
            return@withContext anime
        }
        val kwik = runKwik(kwikUrl)
        val combined = anime.checks + kwik.checks
        result(combined, kwikUrl)
    }

    private fun request(
        url: String,
        referer: String? = null,
        userAgent: String = sessions.userAgentFor(url),
        cookieOverride: String? = null,
    ): RawResponse {
        return try {
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept", "text/html,application/json,application/vnd.apple.mpegurl")

            if (!referer.isNullOrBlank()) builder.header("Referer", referer)
            val cookie = cookieOverride ?: sessions.cookieFor(url)
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
        val type = response.contentType.ifBlank { "unknown" }
        val redirect = if (safeUrl(response.finalUrl) != safeUrl(requestedUrl)) {
            " - final=" + safeUrl(response.finalUrl)
        } else {
            ""
        }
        val networkError = if (response.code < 0) " - " + response.text.take(180) else ""
        return "$status - type=$type - bytes=${response.bytes.size}$challenge$redirect$networkError"
    }

    private fun result(checks: List<DiagnosticCheck>, kwikUrl: String?): DiagnosticResult =
        DiagnosticResult(checks = checks, report = buildReport(checks), kwikUrl = kwikUrl)

    private fun buildReport(checks: List<DiagnosticCheck>): String {
        val snapshot = sessions.snapshot()
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val body = checks.joinToString("\n") { check ->
            "[${check.outcome}] ${check.label}: ${check.detail}"
        }

        return """
PAHE DIAGNOSTICS 0.1
Time: $utc
Device: ${Build.MANUFACTURER} ${Build.MODEL}
Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
Tor packaged in APK: NO

AnimePahe host: ${snapshot.animeHost.ifBlank { "none" }}
AnimePahe cookies: ${snapshot.animeCookieSummary}
Kwik host: ${snapshot.kwikHost.ifBlank { "none" }}
Kwik cookies: ${snapshot.kwikCookieSummary}
Anime UA: ${redactUa(sessions.animeUserAgent())}
Kwik UA: ${redactUa(sessions.kwikUserAgent())}

$body
""".trim()
    }

    private fun extractKwikUrl(html: String): String? {
        val doc = Jsoup.parse(html)
        doc.select("#resolutionMenu button[data-src], button[data-src], a[href]")
            .forEach { element ->
                val candidate = element.attr("data-src").ifBlank { element.attr("href") }.trim()
                if (candidate.contains("kwik.", ignoreCase = true)) return candidate
            }

        return Regex(
            """https?://(?:[^"'\s]+\.)?kwik\.[a-zA-Z]{2,}/[^"'\s<]+""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.value
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
            return if (value < base) digits[value].toString()
            else encode(value / base) + digits[value % base]
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

    private fun looksLikeChallenge(body: String): Boolean {
        if (body.length > 750_000) return false
        return body.contains("cf-chl-", true) ||
            body.contains("Just a moment", true) ||
            body.contains("challenge-platform", true) ||
            body.contains("Checking your browser", true) ||
            body.contains("cf-turnstile", true)
    }

    private fun kwikCandidates(initialUrl: String): List<String> {
        val currentTld = runCatching { URI(initialUrl).host.orEmpty().substringAfterLast(".") }.getOrDefault("")
        val verifiedTld = sessions.kwikHost().substringAfterLast(".", "")
        return buildList {
            add(initialUrl)
            if (verifiedTld.isNotBlank() && verifiedTld != currentTld) {
                swapKwikDomain(initialUrl, verifiedTld)?.let(::add)
            }
            for (tld in listOf("cx", "gg", "si", "me", "net", "in", "cc")) {
                if (tld != currentTld) swapKwikDomain(initialUrl, tld)?.let(::add)
            }
        }.distinct()
    }

    private fun swapKwikDomain(url: String, tld: String): String? =
        runCatching {
            val uri = URI(url)
            val host = uri.host ?: return@runCatching null
            val parts = host.split(".").toMutableList()
            if (parts.size < 2) return@runCatching null
            parts[parts.lastIndex] = tld
            URI(uri.scheme, uri.userInfo, parts.joinToString("."), uri.port, uri.path, uri.query, uri.fragment)
                .toString()
        }.getOrNull()

    private fun normalizePoster(value: String, host: String): String = when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://$host$value"
        value.startsWith("http") -> value
        value.isBlank() -> ""
        else -> "https://$host/$value"
    }

    private fun animeReferer(): String {
        val host = sessions.animeHost().ifBlank { "animepahe.pw" }
        return "https://$host/"
    }

    private fun hostOf(url: String): String =
        runCatching { URI(url).host.orEmpty() }.getOrDefault("unknown")

    private fun safeUrl(url: String): String {
        return runCatching {
            val uri = URI(url)
            val query = if (uri.query.isNullOrBlank()) null else "<redacted>"
            URI(uri.scheme, uri.userInfo, uri.host, uri.port, uri.path, query, null).toString()
        }.getOrDefault(url.substringBefore("?") + if (url.contains("?")) "?<redacted>" else "")
    }

    private fun shortToken(value: String): String {
        if (value.length <= 12) return value
        return value.take(6) + "..." + value.takeLast(4)
    }

    private fun redactUa(value: String): String = value.take(220)

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
