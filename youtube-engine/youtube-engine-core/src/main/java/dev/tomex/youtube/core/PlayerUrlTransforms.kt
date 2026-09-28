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
 * Narrow boundary for signature-cipher recovery.
 *
 * A decipherer receives only the current player script identity and encrypted signature token.
 * The engine, not the decipherer, owns the media URL and applies the returned signature.
 */
interface SignatureCipherDecipherer {
    suspend fun decipher(playerJavaScriptUrl: String, encryptedSignature: String): String?
}

object NoSignatureCipherDecipherer : SignatureCipherDecipherer {
    override suspend fun decipher(playerJavaScriptUrl: String, encryptedSignature: String): String? = null
}

class CachedSignatureCipherDecipherer(
    private val delegate: SignatureCipherDecipherer,
    private val maxEntries: Int = 128
) : SignatureCipherDecipherer {
    init { require(maxEntries in 1..4096) }

    private data class Key(val playerJavaScriptUrl: String, val encryptedSignature: String)
    private val lock = Any()
    private var activePlayerJavaScriptUrl: String? = null
    private val cache = object : LinkedHashMap<Key, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, String>?): Boolean = size > maxEntries
    }

    override suspend fun decipher(playerJavaScriptUrl: String, encryptedSignature: String): String? {
        require(playerJavaScriptUrl.startsWith("https://www.youtube.com/")) { "Unexpected player JavaScript origin" }
        require(encryptedSignature.isNotBlank() && encryptedSignature.length <= 8192) { "Invalid encrypted signature" }
        val key = Key(playerJavaScriptUrl, encryptedSignature)
        synchronized(lock) {
            if (activePlayerJavaScriptUrl != playerJavaScriptUrl) {
                cache.clear()
                activePlayerJavaScriptUrl = playerJavaScriptUrl
            }
            cache[key]?.let { return it }
        }
        val deciphered = delegate.decipher(playerJavaScriptUrl, encryptedSignature)
            ?.takeIf { it.isNotBlank() && it != encryptedSignature }
            ?: return null
        require(deciphered.length <= 8192) { "Deciphered signature is too large" }
        synchronized(lock) {
            if (activePlayerJavaScriptUrl == playerJavaScriptUrl) cache[key] = deciphered
        }
        return deciphered
    }
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

data class CipherParameters(
    val mediaUrl: String,
    val encryptedSignature: String,
    val signatureParameter: String,
    val nParameter: String?
)

object PlayerUrlTransforms {
    private val nParameter = Regex("([?&])n=([^&#]*)", RegexOption.IGNORE_CASE)
    private val signatureParameterName = Regex("[A-Za-z0-9_-]{1,64}")

    fun cipherParameters(format: JSONObject?): CipherParameters? {
        if (format == null) return null
        val raw = sequenceOf("signatureCipher", "cipher")
            .mapNotNull { key -> format.optString(key).takeIf { it.isNotBlank() } }
            .firstOrNull() ?: return null
        val values = linkedMapOf<String, String>()
        for (part in raw.split('&')) {
            val separator = part.indexOf('=')
            if (separator <= 0) continue
            val key = decodeQueryComponent(part.substring(0, separator)) ?: return null
            val value = decodeQueryComponent(part.substring(separator + 1)) ?: return null
            values[key] = value
        }
        val mediaUrl = values["url"]?.takeIf { it.startsWith("https://") } ?: return null
        val signature = values["s"]?.takeIf { it.isNotBlank() && it.length <= 8192 } ?: return null
        val parameter = values["sp"]?.ifBlank { "signature" } ?: "signature"
        if (!signatureParameterName.matches(parameter)) return null
        return CipherParameters(mediaUrl, signature, parameter, extractN(mediaUrl))
    }

    private fun decodeQueryComponent(value: String): String? =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrNull()

    fun extractN(url: String): String? {
        val raw = nParameter.find(url)?.groupValues?.get(2)?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun replaceN(url: String, transformed: String): String {
        require(url.startsWith("https://")) { "Media URL must be HTTPS" }
        require(transformed.isNotBlank() && transformed.length <= 4096) { "Invalid transformed n parameter" }
        require(nParameter.containsMatchIn(url)) { "Media URL has no n parameter" }
        val encoded = URLEncoder.encode(transformed, Charsets.UTF_8.name()).replace("+", "%20")
        val match = nParameter.find(url) ?: error("Media URL has no n parameter")
        return url.replaceRange(match.range, "${match.groupValues[1]}n=$encoded")
    }

    fun applySignature(url: String, parameter: String, decipheredSignature: String): String? {
        if (!url.startsWith("https://")) return null
        if (!signatureParameterName.matches(parameter)) return null
        if (decipheredSignature.isBlank() || decipheredSignature.length > 8192) return null
        val fragmentAt = url.indexOf('#')
        val head = if (fragmentAt >= 0) url.substring(0, fragmentAt) else url
        val fragment = if (fragmentAt >= 0) url.substring(fragmentAt) else ""
        val parameterPattern = Regex("([?&])${Regex.escape(parameter)}=([^&#]*)")
        val matches = parameterPattern.findAll(head).toList()
        if (matches.size > 1) return null
        val encoded = URLEncoder.encode(decipheredSignature, Charsets.UTF_8.name()).replace("+", "%20")
        val signed = matches.firstOrNull()?.let { match ->
            head.replaceRange(match.range, "${match.groupValues[1]}$parameter=$encoded")
        } ?: run {
            val separator = if ('?' in head) '&' else '?'
            "$head$separator$parameter=$encoded"
        }
        return signed + fragment
    }

    fun normalizePlayerJavaScriptUrl(raw: String): String? {
        val value = raw.replace("\\/", "/").trim()
        return when {
            value.startsWith("https://www.youtube.com/") -> value
            value.startsWith("//www.youtube.com/") -> "https:$value"
            value.startsWith("/") -> "https://www.youtube.com$value"
            else -> null
        }
    }

    fun playerJavaScriptUrl(playerResponse: JSONObject): String? {
        val raw = playerResponse.optJSONObject("assets")?.optString("js")?.takeIf { it.isNotBlank() } ?: return null
        return normalizePlayerJavaScriptUrl(raw)
    }
}
