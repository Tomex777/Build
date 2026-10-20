package com.night.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Persistent, device-local speed dials. These are explicitly selected shortcuts,
 * never scraped or synced browsing history.
 */
data class StartSite(val title: String, val url: String)

object StartSiteUrls {
    /**
     * Do not accidentally turn an invalid URL into a web search or accept script,
     * file, credential-bearing and other non-web URLs as saved shortcuts.
     */
    fun normalize(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank() || value.length > 2048 || value.any { it.isWhitespace() }) return null
        val candidate = if (value.startsWith("http://", true) ||
            value.startsWith("https://", true)) value else "https://$value"
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (uri.rawUserInfo != null || uri.host.isNullOrBlank()) return null
        val host = uri.host ?: return null
        if (host != "localhost" && !host.contains('.')) return null
        if (uri.port > 65535 || uri.port == 0) return null
        return uri.toASCIIString()
    }
}

class StartSiteStore(context: Context) {
    private val prefs = context.getSharedPreferences("night_start_v1", Context.MODE_PRIVATE)
    private val defaults = listOf(
        StartSite("Google", "https://www.google.com"),
        StartSite("YouTube", "https://www.youtube.com"),
        StartSite("Wikipedia", "https://www.wikipedia.org"),
        StartSite("GitHub", "https://github.com"),
        StartSite("Reddit", "https://www.reddit.com"),
        StartSite("DuckDuckGo", "https://duckduckgo.com")
    )

    var sites: List<StartSite> by mutableStateOf(load())
        private set

    private fun load(): List<StartSite> {
        val raw = prefs.getString("shortcuts", null) ?: return defaults
        return runCatching {
            val entries = JSONArray(raw)
            val seen = mutableSetOf<String>()
            (0 until entries.length()).mapNotNull { i ->
                val item = entries.optJSONObject(i) ?: return@mapNotNull null
                val url = StartSiteUrls.normalize(item.optString("url")) ?: return@mapNotNull null
                if (!seen.add(url.lowercase())) return@mapNotNull null
                StartSite(item.optString("title").trim().take(35).ifBlank { url }, url)
            }.take(16)
        }.getOrDefault(defaults)
    }

    fun save(title: String, rawUrl: String, editingUrl: String? = null): Boolean {
        val url = StartSiteUrls.normalize(rawUrl) ?: return false
        val label = title.trim().take(35).ifBlank {
            runCatching { URI(url).host }.getOrNull() ?: url
        }
        if (sites.any { it.url.equals(url, true) && it.url != editingUrl }) return false
        if (editingUrl == null && sites.size >= 16) return false
        sites = if (editingUrl == null) sites + StartSite(label, url)
        else {
            if (sites.none { it.url == editingUrl }) return false
            sites.map { if (it.url == editingUrl) StartSite(label, url) else it }
        }
        persist()
        return true
    }

    fun remove(url: String) {
        sites = sites.filterNot { it.url == url }
        persist()
    }

    private fun persist() {
        val result = JSONArray()
        sites.forEach { site ->
            result.put(JSONObject().put("title", site.title).put("url", site.url))
        }
        prefs.edit().putString("shortcuts", result.toString()).apply()
    }
}
