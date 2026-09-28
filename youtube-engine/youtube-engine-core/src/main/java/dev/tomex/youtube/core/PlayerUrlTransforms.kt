package dev.tomex.youtube.core

import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.LinkedHashMap

/**
 * Narrow boundary for future player-JavaScript n transforms.
 *
 * Implementations receive only the player script identity and the extracted n token.
 * They cannot replace a media URL; the engine owns the bounded query-parameter rewrite.
 */
interface NParameterTransformer {
    suspend fun transform(playerJavaScriptUrl: String, input: String): String?
}

object NoNParameterTransformer : NParameterTransformer {
    override suspend fun transform(playerJavaScriptUrl: String, input: String): String? = null
}

/**
 * Small thread-safe cache. A player script change invalidates every cached transform because
 * YouTube can change the transform program independently of the media URL.
 */
class CachedNParameterTransformer(
    private val delegate: NParameterTransformer,
    private val maxEntries: Int = 128
) : NParameterTransformer {
    init { require(maxEntries in 1..4096) }

    private data class Key(val playerJavaScriptUrl: String, val input: String)
    private val lock = Any()
    private var activePlayerJavaScriptUrl: String? = null
    private val cache = object : LinkedHashMap<Key, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, String>?): Boolean = size > maxEntries
    }

    override suspend fun transform(playerJavaScriptUrl: String, input: String): String? {
        require(playerJavaScriptUrl.startsWith("https://www.youtube.com/")) { "Unexpected player JavaScript origin" }
        require(input.isNotBlank() && input.length <= 4096) { "Invalid n parameter" }
        val key = Key(playerJavaScriptUrl, input)
        synchronized(lock) {
            if (activePlayerJavaScriptUrl != playerJavaScriptUrl) {
                cache.clear()
                activePlayerJavaScriptUrl = playerJavaScriptUrl
            }
            cache[key]?.let { return it }
        }
        val transformed = delegate.transform(playerJavaScriptUrl, input)
            ?.takeIf { it.isNotBlank() && it != input }
            ?: return null
        require(transformed.length <= 4096) { "Transformed n parameter is too large" }
        synchronized(lock) {
            if (activePlayerJavaScriptUrl == playerJavaScriptUrl) cache[key] = transformed
        }
        return transformed
    }
}

object PlayerUrlTransforms {
    private val nParameter = Regex("([?&])n=([^&#]*)", RegexOption.IGNORE_CASE)

    fun extractN(url: String): String? {
        val raw = nParameter.find(url)?.groupValues?.get(2)?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun replaceN(url: String, transformed: String): String {
        require(url.startsWith("https://")) { "Media URL must be HTTPS" }
        require(transformed.isNotBlank() && transformed.length <= 4096) { "Invalid transformed n parameter" }
        require(nParameter.containsMatchIn(url)) { "Media URL has no n parameter" }
        val encoded = URLEncoder.encode(transformed, Charsets.UTF_8.name()).replace("+", "%20")
        return nParameter.replaceFirst(url) { match -> "${match.groupValues[1]}n=$encoded" }
    }

    fun playerJavaScriptUrl(playerResponse: JSONObject): String? {
        val raw = playerResponse.optJSONObject("assets")?.optString("js")?.takeIf { it.isNotBlank() } ?: return null
        return when {
            raw.startsWith("https://www.youtube.com/") -> raw
            raw.startsWith("//www.youtube.com/") -> "https:$raw"
            raw.startsWith("/") -> "https://www.youtube.com$raw"
            else -> null
        }
    }
}
