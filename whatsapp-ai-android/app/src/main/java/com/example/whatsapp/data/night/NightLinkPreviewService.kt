package com.example.whatsapp.data.night

import android.content.Context
import android.text.Html
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class NightLinkPreview(
    val url: String,
    val title: String,
    val description: String,
    val site: String,
    val imageUrl: String?,
)

class NightLinkPreviewService private constructor(
    private val http: OkHttpClient,
) {
    suspend fun resolveFromText(text: String): NightLinkPreview? =
        withContext(Dispatchers.IO) {
            val url = URL_REGEX.find(text)?.value?.trimEnd('.', ',', ';', ')', ']')
                ?: return@withContext null
            resolve(url)
        }

    private fun resolve(url: String): NightLinkPreview? {
        if (!url.startsWith("https://", ignoreCase = true) &&
            !url.startsWith("http://", ignoreCase = true)
        ) {
            return null
        }

        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Night/1.0"
            )
            .header("Accept", "text/html,application/xhtml+xml")
            .build()

        return runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null

                val contentType = response.header("Content-Type").orEmpty()
                if (!contentType.contains("text/html", ignoreCase = true) &&
                    !contentType.contains("application/xhtml", ignoreCase = true)
                ) {
                    return@use null
                }

                val html = response.peekBody(MAX_HTML_BYTES).string()
                if (html.isBlank()) return@use null

                val metas = parseMeta(html)
                val uri = URI(url)
                val domain = uri.host
                    ?.removePrefix("www.")
                    ?.takeIf { it.isNotBlank() }
                    ?: url

                val title = (
                    metas["og:title"]
                        ?: metas["twitter:title"]
                        ?: TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)
                        ?: domain
                    )
                    .decodeHtml()
                    .trim()
                    .take(180)

                val description = (
                    metas["og:description"]
                        ?: metas["twitter:description"]
                        ?: metas["description"]
                        ?: ""
                    )
                    .decodeHtml()
                    .trim()
                    .replace(Regex("\\s+"), " ")
                    .take(320)

                val rawImage = metas["og:image"]
                    ?: metas["twitter:image"]
                    ?: metas["twitter:image:src"]

                val image = rawImage
                    ?.decodeHtml()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { imageUrl ->
                        runCatching { uri.resolve(imageUrl).toString() }
                            .getOrDefault(imageUrl)
                    }

                NightLinkPreview(
                    url = url,
                    title = title.ifBlank { domain },
                    description = description,
                    site = (metas["og:site_name"]?.decodeHtml()?.trim())
                        ?.takeIf { it.isNotBlank() }
                        ?: domain,
                    imageUrl = image,
                )
            }
        }.getOrNull()
    }

    private fun parseMeta(html: String): Map<String, String> {
        val values = linkedMapOf<String, String>()

        META_TAG_REGEX.findAll(html).forEach { match ->
            val tag = match.value
            val attributes = linkedMapOf<String, String>()

            ATTRIBUTE_REGEX.findAll(tag).forEach { attribute ->
                val key = attribute.groupValues[1].lowercase()
                val value = attribute.groupValues[3]
                attributes[key] = value
            }

            val name = (
                attributes["property"]
                    ?: attributes["name"]
                )
                ?.lowercase()
                ?.trim()

            val value = attributes["content"]
            if (!name.isNullOrBlank() && !value.isNullOrBlank()) {
                values.putIfAbsent(name, value)
            }
        }

        return values
    }

    @Suppress("DEPRECATION")
    private fun String.decodeHtml(): String =
        Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString()

    companion object {
        private const val MAX_HTML_BYTES = 512L * 1024L

        private val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
        private val TITLE_REGEX = Regex(
            """<title[^>]*>(.*?)</title>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        private val META_TAG_REGEX = Regex(
            """<meta\b[^>]*>""",
            RegexOption.IGNORE_CASE,
        )
        private val ATTRIBUTE_REGEX = Regex(
            """([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(["'])(.*?)\2""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

        @Volatile private var instance: NightLinkPreviewService? = null

        fun get(context: Context): NightLinkPreviewService =
            instance ?: synchronized(this) {
                instance ?: NightLinkPreviewService(
                    http = OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build(),
                ).also { instance = it }
            }
    }
}
