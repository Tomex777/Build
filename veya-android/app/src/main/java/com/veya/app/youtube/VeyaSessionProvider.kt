package com.veya.app.youtube

import android.content.Context
import dev.tomex.youtube.api.SessionProvider
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class VeyaSessionProvider(context: Context) : SessionProvider {
    private val prefs = context.getSharedPreferences("veya_youtube_session", Context.MODE_PRIVATE)
    private val cookies = ConcurrentHashMap<String, StoredCookie>()

    init {
        loadPersisted()
    }

    override suspend fun requestHeaders(url: String): Map<String, String> {
        val uri = parseHttps(url) ?: return emptyMap()
        val host = uri.host?.lowercase().orEmpty()
        if (!isAllowedHost(host)) return emptyMap()

        val now = System.currentTimeMillis()
        val path = uri.rawPath?.ifBlank { "/" } ?: "/"
        val matching = cookies.values
            .asSequence()
            .filter { !it.isExpired(now) }
            .filter { domainMatches(host, it.domain) }
            .filter { path.startsWith(it.path) }
            .filter { !it.secure || uri.scheme.equals("https", true) }
            .sortedByDescending { it.path.length }
            .toList()

        if (matching.isEmpty()) return emptyMap()
        return mapOf("Cookie" to matching.joinToString("; ") { "${it.name}=${it.value}" })
    }

    override suspend fun storeResponseCookies(url: String, setCookieHeaders: List<String>) {
        val uri = parseHttps(url) ?: return
        val requestHost = uri.host?.lowercase().orEmpty()
        if (!isAllowedHost(requestHost)) return

        var changed = false
        for (header in setCookieHeaders) {
            val parsed = parseSetCookie(requestHost, header) ?: continue
            if (parsed.delete) {
                changed = cookies.remove(parsed.key) != null || changed
            } else {
                cookies[parsed.key] = parsed.cookie
                changed = true
            }
        }
        if (changed) persist()
    }

    private fun parseSetCookie(requestHost: String, header: String): ParsedCookie? {
        val parts = header.split(';').map(String::trim).filter(String::isNotEmpty)
        if (parts.isEmpty()) return null
        val first = parts.first()
        val equals = first.indexOf('=')
        if (equals <= 0) return null

        val name = first.substring(0, equals).trim()
        val value = first.substring(equals + 1).trim()
        if (!COOKIE_NAME.matches(name)) return null

        var domain = requestHost
        var path = "/"
        var secure = false
        var maxAgeSeconds: Long? = null

        for (attribute in parts.drop(1)) {
            val attrName = attribute.substringBefore('=').trim().lowercase()
            val attrValue = attribute.substringAfter('=', "").trim()
            when (attrName) {
                "domain" -> {
                    val candidate = attrValue.removePrefix(".").lowercase()
                    if (candidate.isNotBlank() &&
                        domainMatches(requestHost, candidate) &&
                        isAllowedHost(candidate)
                    ) {
                        domain = candidate
                    }
                }
                "path" -> if (attrValue.startsWith('/')) path = attrValue
                "secure" -> secure = true
                "max-age" -> maxAgeSeconds = attrValue.toLongOrNull()
            }
        }

        val key = "$domain|$path|$name"
        if (maxAgeSeconds != null && maxAgeSeconds <= 0L) {
            return ParsedCookie(
                key,
                StoredCookie(name, "", domain, path, secure, 0L),
                delete = true
            )
        }

        val expiresAt = maxAgeSeconds
            ?.coerceAtMost(MAX_COOKIE_AGE_SECONDS)
            ?.let { System.currentTimeMillis() + it * 1000L }

        return ParsedCookie(
            key = key,
            cookie = StoredCookie(name, value, domain, path, secure, expiresAt),
            delete = false
        )
    }

    private fun loadPersisted() {
        val text = prefs.getString(PREF_KEY, null) ?: return
        runCatching {
            val array = JSONArray(text)
            val now = System.currentTimeMillis()
            for (index in 0 until array.length()) {
                val obj = array.getJSONObject(index)
                val cookie = StoredCookie(
                    name = obj.getString("name"),
                    value = obj.getString("value"),
                    domain = obj.getString("domain"),
                    path = obj.optString("path", "/"),
                    secure = obj.optBoolean("secure", true),
                    expiresAt = obj.optLong("expiresAt").takeIf { it > 0L }
                )
                if (!cookie.isExpired(now) && isAllowedHost(cookie.domain)) {
                    cookies[cookie.key] = cookie
                }
            }
        }
    }

    private fun persist() {
        val now = System.currentTimeMillis()
        val array = JSONArray()
        cookies.values
            .filterNot { it.isExpired(now) }
            .forEach { cookie ->
                array.put(JSONObject().apply {
                    put("name", cookie.name)
                    put("value", cookie.value)
                    put("domain", cookie.domain)
                    put("path", cookie.path)
                    put("secure", cookie.secure)
                    put("expiresAt", cookie.expiresAt ?: 0L)
                })
            }
        prefs.edit().putString(PREF_KEY, array.toString()).apply()
    }

    private fun parseHttps(url: String): URI? = runCatching {
        URI(url).takeIf {
            it.scheme.equals("https", true) &&
                it.userInfo == null &&
                !it.host.isNullOrBlank()
        }
    }.getOrNull()

    private fun isAllowedHost(host: String): Boolean =
        host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host == "youtu.be" ||
            host == "googlevideo.com" ||
            host.endsWith(".googlevideo.com")

    private fun domainMatches(host: String, domain: String): Boolean =
        host == domain || host.endsWith(".$domain")

    private data class ParsedCookie(
        val key: String,
        val cookie: StoredCookie,
        val delete: Boolean
    )

    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val expiresAt: Long?
    ) {
        val key: String get() = "$domain|$path|$name"
        fun isExpired(now: Long): Boolean = expiresAt?.let { now >= it } == true
    }

    companion object {
        private const val PREF_KEY = "cookies"
        private const val MAX_COOKIE_AGE_SECONDS = 60L * 60L * 24L * 400L
        private val COOKIE_NAME = Regex("[!#$%&'*+.^_\\x60|~0-9A-Za-z-]{1,128}")
    }
}
