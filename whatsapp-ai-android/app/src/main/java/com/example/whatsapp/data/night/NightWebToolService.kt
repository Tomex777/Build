package com.example.whatsapp.data.night

import android.text.Html
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

data class NightWebSearchResult(
    val title: String,
    val url: String,
    val snippet: String = "",
)

class NightWebToolService private constructor(
    private val http: OkHttpClient,
) {
    suspend fun search(
        query: String,
        maxResults: Int = 6,
    ): Result<List<NightWebSearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            require(query.isNotBlank()) { "Search query cannot be empty." }

            val form = FormBody.Builder()
                .add("q", query.trim())
                .build()
            val request = Request.Builder()
                .url("https://html.duckduckgo.com/html/")
                .post(form)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36",
                )
                .header("Accept-Language", "en-US,en;q=0.8")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Web search failed (" + response.code + ").")
                }
                val html = response.body?.string().orEmpty()
                parseDuckDuckGo(html)
                    .distinctBy { it.url }
                    .take(maxResults.coerceIn(1, 10))
            }
        }
    }

    suspend fun fetchPage(
        url: String,
        maxChars: Int = 16_000,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safeUrl = validateUrl(url)
            val request = Request.Builder()
                .url(safeUrl)
                .get()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36",
                )
                .header("Accept", "text/html,text/plain,application/json;q=0.9,*/*;q=0.5")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Page request failed (" + response.code + ").")
                }
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val body = response.body ?: error("The page returned no content.")
                val source = body.source()
                val byteLimit = (maxChars.coerceAtLeast(4_000) * 8L).coerceAtMost(512_000L)
                source.request(byteLimit)
                val readable = minOf(source.buffer.size, byteLimit)
                val raw = source.readUtf8(readable)

                val text = when {
                    "html" in contentType || raw.contains("<html", ignoreCase = true) -> htmlToText(raw)
                    else -> raw
                }
                    .replace(Regex("[ \\t]{2,}"), " ")
                    .replace(Regex("\\n{3,}"), "\n\n")
                    .trim()

                text.take(maxChars.coerceIn(2_000, 40_000))
            }
        }
    }

    private fun parseDuckDuckGo(html: String): List<NightWebSearchResult> {
        val linkPattern = Regex(
            """<a[^>]*class=["'][^"']*result__a[^"']*["'][^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val snippetPattern = Regex(
            """<(?:a|div)[^>]*class=["'][^"']*result__snippet[^"']*["'][^>]*>(.*?)</(?:a|div)>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val snippets = snippetPattern.findAll(html)
            .map { htmlToText(it.groupValues[1]) }
            .toList()

        return linkPattern.findAll(html).mapIndexed { index, match ->
            val rawUrl = decodeDuckDuckGoUrl(match.groupValues[1])
            NightWebSearchResult(
                title = htmlToText(match.groupValues[2]).ifBlank { rawUrl },
                url = rawUrl,
                snippet = snippets.getOrNull(index).orEmpty(),
            )
        }
            .filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
            .toList()
    }

    private fun decodeDuckDuckGoUrl(value: String): String {
        val decoded = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
        return runCatching {
            val uri = URI(decoded)
            if (uri.host?.contains("duckduckgo.com") == true) {
                val query = uri.rawQuery.orEmpty()
                val uddg = query.split("&")
                    .firstOrNull { it.startsWith("uddg=") }
                    ?.substringAfter("=")
                if (!uddg.isNullOrBlank()) {
                    URLDecoder.decode(uddg, StandardCharsets.UTF_8.name())
                } else {
                    decoded
                }
            } else {
                decoded
            }
        }.getOrDefault(decoded)
    }

    private fun validateUrl(value: String): String {
        val trimmed = value.trim()
        val uri = URI(trimmed)
        require(uri.scheme == "http" || uri.scheme == "https") {
            "Only HTTP and HTTPS pages can be fetched."
        }
        val host = uri.host?.lowercase().orEmpty()
        require(host.isNotBlank()) { "The URL has no valid host." }
        require(host != "localhost" && !host.endsWith(".local")) {
            "Local network addresses cannot be fetched."
        }

        val addresses = runCatching { InetAddress.getAllByName(host).toList() }
            .getOrElse { error("The page host could not be resolved.") }
        require(addresses.isNotEmpty() && addresses.none { address ->
            address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isMulticastAddress
        }) {
            "Private or local network addresses cannot be fetched."
        }
        return trimmed
    }

    private fun htmlToText(html: String): String {
        val cleaned = html
            .replace(
                Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE),
                " ",
            )
            .replace(
                Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE),
                " ",
            )
            .replace(
                Regex("<noscript[\\s\\S]*?</noscript>", RegexOption.IGNORE_CASE),
                " ",
            )
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</div\\s*>", RegexOption.IGNORE_CASE), "\n")

        return Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    companion object {
        @Volatile private var instance: NightWebToolService? = null

        fun get(): NightWebToolService =
            instance ?: synchronized(this) {
                instance ?: NightWebToolService(
                    OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build(),
                ).also { instance = it }
            }
    }
}
