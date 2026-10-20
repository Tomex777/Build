package com.night.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.IDN
import java.net.URI
import java.util.Locale

/**
 * Exact scheme + host + effective port, not suffix matching. A rule for
 * https://example.com can never affect https://login.example.com or an HTTP page.
 */
object SiteOrigins {
    fun fromUrl(pageUrl: String): String? {
        return try {
        val uri = URI(pageUrl.trim())
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "https" && scheme != "http") return null
        if (uri.rawUserInfo != null) return null
        val rawHost = uri.host?.trimEnd('.')?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() } ?: return null
        val host = if (rawHost.contains(':')) {
            // IPv6 literals are bracketed in the serialized origin.
            "[${rawHost.removePrefix("[").removeSuffix("]")}]"
        } else {
            IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES)
                .lowercase(Locale.ROOT)
        }
        val port = uri.port
        if (port < -1 || port > 65535 || port == 0) return null
        val defaultPort = if (scheme == "https") 443 else 80
        "$scheme://$host" + if (port == -1 || port == defaultPort) "" else ":$port"
        } catch (_: Exception) {
            null
        }
    }

    fun matches(pageUrl: String, origin: String): Boolean =
        fromUrl(pageUrl) != null && fromUrl(pageUrl) == origin
}

data class SiteRule(
    val origin: String,
    val css: String = "",
    val javascript: String = "",
    val cssEnabled: Boolean = false,
    val javascriptEnabled: Boolean = false
)

/**
 * Local, explicit, default-off site modifications. No page content, scripts,
 * credentials or visited URLs are transmitted to an external service.
 * Preview drafts are held only in RAM by SiteStudioBridge, never in this store.
 */
class SiteStudioStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("night_site_studio_v1", Context.MODE_PRIVATE)
    private val key = "exact_origin_rules"

    @Synchronized
    fun all(): List<SiteRule> = load()

    @Synchronized
    fun get(pageUrl: String): SiteRule? {
        val origin = SiteOrigins.fromUrl(pageUrl) ?: return null
        return load().firstOrNull { it.origin == origin }
    }

    @Synchronized
    fun save(rule: SiteRule): Boolean {
        val origin = SiteOrigins.fromUrl(rule.origin) ?: return false
        // Guard against oversized payloads and accidental broad-origin injection.
        if (origin != rule.origin || rule.css.length > MAX_CSS ||
            rule.javascript.length > MAX_JS) return false
        val all = load()
        val previousRule = all.firstOrNull { it.origin == origin }
        if (previousRule == rule) return true
        val others = all.filterNot { it.origin == origin }
        if (others.size >= MAX_SITES) return false
        return persist(others + rule, origin, previousRule)
    }

    @Synchronized
    fun remove(pageUrl: String): Boolean {
        val origin = SiteOrigins.fromUrl(pageUrl) ?: return false
        val all = load()
        val old = all.firstOrNull { it.origin == origin } ?: return true
        return persist(all.filterNot { it.origin == origin }, origin, old)
    }

    @Synchronized
    fun canUndo(pageUrl: String): Boolean {
        val origin = SiteOrigins.fromUrl(pageUrl) ?: return false
        return preferences.contains("undo:$origin")
    }

    /** One-step durable undo of the most recent save or reset for this origin. */
    @Synchronized
    fun undo(pageUrl: String): Boolean {
        val origin = SiteOrigins.fromUrl(pageUrl) ?: return false
        val undoKey = "undo:$origin"
        if (!preferences.contains(undoKey)) return false
        val previous = preferences.getString(undoKey, "") ?: ""
        val restored = if (previous.isBlank()) null else runCatching {
            val obj = JSONObject(previous)
            SiteRule(
                origin = obj.getString("origin"),
                css = obj.optString("css"),
                javascript = obj.optString("js"),
                cssEnabled = obj.optBoolean("cssEnabled"),
                javascriptEnabled = obj.optBoolean("jsEnabled")
            ).takeIf { it.origin == origin &&
                it.css.length <= MAX_CSS && it.javascript.length <= MAX_JS }
        }.getOrNull() ?: if (previous.isBlank()) null else return false
        val without = load().filterNot { it.origin == origin }
        if (restored != null && without.size >= MAX_SITES) return false
        if (!persist(without + listOfNotNull(restored))) return false
        return preferences.edit().remove(undoKey).commit()
    }

    private fun load(): List<SiteRule> = runCatching {
        val array = JSONArray(preferences.getString(key, "[]") ?: "[]")
        (0 until minOf(array.length(), MAX_SITES)).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val origin = item.optString("origin")
            val css = item.optString("css")
            val js = item.optString("js")
            if (SiteOrigins.fromUrl(origin) != origin ||
                css.length > MAX_CSS || js.length > MAX_JS) return@mapNotNull null
            SiteRule(origin, css, js,
                item.optBoolean("cssEnabled", false),
                item.optBoolean("jsEnabled", false))
        }.distinctBy { it.origin }
    }.getOrDefault(emptyList())

    private fun persist(
        rules: List<SiteRule>,
        undoOrigin: String? = null,
        undoPrevious: SiteRule? = null
    ): Boolean = runCatching {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(JSONObject()
                .put("origin", rule.origin)
                .put("css", rule.css)
                .put("js", rule.javascript)
                .put("cssEnabled", rule.cssEnabled)
                .put("jsEnabled", rule.javascriptEnabled))
        }
        val changes = preferences.edit().putString(key, array.toString())
        if (undoOrigin != null) {
            val previous = undoPrevious?.let { prior ->
                JSONObject().put("origin", prior.origin).put("css", prior.css)
                    .put("js", prior.javascript).put("cssEnabled", prior.cssEnabled)
                    .put("jsEnabled", prior.javascriptEnabled).toString()
            } ?: ""
            changes.putString("undo:$undoOrigin", previous)
        }
        changes.commit()
    }.getOrDefault(false)

    companion object {
        const val MAX_SITES = 64
        const val MAX_CSS = 32_768
        const val MAX_JS = 16_384
    }
}
