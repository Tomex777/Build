package com.night.pahebatcher.data

import android.content.Context
import java.net.URI

data class SessionSnapshot(
    val animeCookieSaved: Boolean,
    val kwikCookieSaved: Boolean,
    val animeHost: String,
    val kwikHost: String,
    val animeUpdatedAt: Long,
    val kwikUpdatedAt: Long,
)

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("pahe_sessions", Context.MODE_PRIVATE)

    fun animeUserAgent(): String =
        prefs.getString(KEY_ANIME_UA, prefs.getString(KEY_LEGACY_UA, DEFAULT_UA)) ?: DEFAULT_UA
    fun kwikUserAgent(): String =
        prefs.getString(KEY_KWIK_UA, prefs.getString(KEY_LEGACY_UA, DEFAULT_UA)) ?: DEFAULT_UA
    fun animeCookie(): String = prefs.getString(KEY_ANIME_COOKIE, "").orEmpty()
    fun kwikCookie(): String = prefs.getString(KEY_KWIK_COOKIE, "").orEmpty()
    fun animeHost(): String = prefs.getString(KEY_ANIME_HOST, "").orEmpty()
    fun kwikHost(): String = prefs.getString(KEY_KWIK_HOST, "").orEmpty()

    fun cookieFor(url: String): String {
        val host = hostOf(url)
        return when {
            hostMatches(host, animeHost()) -> animeCookie()
            hostMatches(host, kwikHost()) -> kwikCookie()
            else -> ""
        }
    }

    fun userAgentFor(url: String): String {
        val host = hostOf(url)
        return when {
            hostMatches(host, kwikHost()) || isKwikHost(host) -> kwikUserAgent()
            hostMatches(host, animeHost()) || isAnimePaheHost(host) -> animeUserAgent()
            else -> animeUserAgent()
        }
    }

    private fun hostOf(url: String): String =
        runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")

    private fun hostMatches(actual: String, saved: String): Boolean {
        if (actual.isBlank() || saved.isBlank()) return false
        val normalizedSaved = saved.lowercase()
        return actual == normalizedSaved || actual.endsWith(".$normalizedSaved")
    }

    private fun isAnimePaheHost(host: String): Boolean =
        host.contains("animepahe") || host == "pahe.win"

    private fun isKwikHost(host: String): Boolean =
        host.startsWith("kwik.") || host.contains(".kwik.")

    fun saveAnime(cookie: String, host: String, userAgent: String) {
        prefs.edit()
            .putString(KEY_ANIME_COOKIE, cookie)
            .putString(KEY_ANIME_HOST, host)
            .putString(KEY_ANIME_UA, userAgent.ifBlank { DEFAULT_UA })
            .putLong(KEY_ANIME_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun rememberAnimeHost(host: String) {
        if (host.isBlank()) return
        prefs.edit().putString(KEY_ANIME_HOST, host).apply()
    }

    fun saveKwik(cookie: String, host: String, userAgent: String) {
        prefs.edit()
            .putString(KEY_KWIK_COOKIE, cookie)
            .putString(KEY_KWIK_HOST, host)
            .putString(KEY_KWIK_UA, userAgent.ifBlank { DEFAULT_UA })
            .putLong(KEY_KWIK_UPDATED, System.currentTimeMillis())
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun snapshot(): SessionSnapshot = SessionSnapshot(
        animeCookieSaved = animeCookie().isNotBlank(),
        kwikCookieSaved = kwikCookie().isNotBlank(),
        animeHost = animeHost(),
        kwikHost = kwikHost(),
        animeUpdatedAt = prefs.getLong(KEY_ANIME_UPDATED, 0L),
        kwikUpdatedAt = prefs.getLong(KEY_KWIK_UPDATED, 0L),
    )

    companion object {
        private const val KEY_LEGACY_UA = "user_agent"
        private const val KEY_ANIME_UA = "anime_user_agent"
        private const val KEY_KWIK_UA = "kwik_user_agent"
        private const val KEY_ANIME_COOKIE = "anime_cookie"
        private const val KEY_KWIK_COOKIE = "kwik_cookie"
        private const val KEY_ANIME_HOST = "anime_host"
        private const val KEY_KWIK_HOST = "kwik_host"
        private const val KEY_ANIME_UPDATED = "anime_updated"
        private const val KEY_KWIK_UPDATED = "kwik_updated"

        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
    }
}
