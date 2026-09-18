package com.night.pahediagnostics

import android.content.Context
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

data class SessionSnapshot(
    val animeHost: String,
    val kwikHost: String,
    val animeCookieSaved: Boolean,
    val kwikCookieSaved: Boolean,
    val animeCookieSummary: String,
    val kwikCookieSummary: String,
    val lastKwikUrl: String,
)

class DiagnosticSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("pahe_diagnostics_sessions", Context.MODE_PRIVATE)

    fun animeHost(): String = prefs.getString(KEY_ANIME_HOST, "").orEmpty()
    fun kwikHost(): String = prefs.getString(KEY_KWIK_HOST, "").orEmpty()
    fun animeCookie(): String = prefs.getString(KEY_ANIME_COOKIE, "").orEmpty()
    fun kwikCookie(): String = prefs.getString(KEY_KWIK_COOKIE, "").orEmpty()
    fun animeUserAgent(): String = prefs.getString(KEY_ANIME_UA, DEFAULT_UA) ?: DEFAULT_UA
    fun kwikUserAgent(): String = prefs.getString(KEY_KWIK_UA, DEFAULT_UA) ?: DEFAULT_UA
    fun lastKwikUrl(): String = prefs.getString(KEY_LAST_KWIK_URL, "").orEmpty()

    fun saveAnime(cookie: String, host: String, userAgent: String) {
        prefs.edit()
            .putString(KEY_ANIME_COOKIE, cookie)
            .putString(KEY_ANIME_HOST, host.lowercase(Locale.US))
            .putString(KEY_ANIME_UA, userAgent.ifBlank { DEFAULT_UA })
            .apply()
    }

    fun saveKwik(cookie: String, host: String, userAgent: String) {
        prefs.edit()
            .putString(KEY_KWIK_COOKIE, cookie)
            .putString(KEY_KWIK_HOST, host.lowercase(Locale.US))
            .putString(KEY_KWIK_UA, userAgent.ifBlank { DEFAULT_UA })
            .apply()
    }

    fun saveLastKwikUrl(url: String) {
        prefs.edit().putString(KEY_LAST_KWIK_URL, url).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

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
            isKwikHost(host) || hostMatches(host, kwikHost()) -> kwikUserAgent()
            isAnimeHost(host) || hostMatches(host, animeHost()) -> animeUserAgent()
            else -> animeUserAgent()
        }
    }

    fun snapshot(): SessionSnapshot = SessionSnapshot(
        animeHost = animeHost(),
        kwikHost = kwikHost(),
        animeCookieSaved = animeCookie().isNotBlank(),
        kwikCookieSaved = kwikCookie().isNotBlank(),
        animeCookieSummary = summarizeCookie(animeCookie()),
        kwikCookieSummary = summarizeCookie(kwikCookie()),
        lastKwikUrl = lastKwikUrl(),
    )

    private fun hostOf(url: String): String =
        runCatching { URI(url).host.orEmpty().lowercase(Locale.US) }.getOrDefault("")

    private fun hostMatches(actual: String, saved: String): Boolean {
        if (actual.isBlank() || saved.isBlank()) return false
        return actual == saved || actual.endsWith(".$saved")
    }

    private fun isAnimeHost(host: String): Boolean =
        host.contains("animepahe") || host == "pahe.win"

    private fun isKwikHost(host: String): Boolean =
        host.startsWith("kwik.") || host.contains(".kwik.")

    private fun summarizeCookie(cookie: String): String {
        if (cookie.isBlank()) return "none"
        return cookie.split(";")
            .mapNotNull { part ->
                val name = part.substringBefore("=", "").trim()
                val value = part.substringAfter("=", "").trim()
                if (name.isBlank()) null else {
                    val digest = MessageDigest.getInstance("SHA-256")
                        .digest(value.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                        .take(8)
                    "$name(len=${value.length},sha=$digest)"
                }
            }
            .joinToString(", ")
            .ifBlank { "present" }
    }

    companion object {
        private const val KEY_ANIME_HOST = "anime_host"
        private const val KEY_KWIK_HOST = "kwik_host"
        private const val KEY_ANIME_COOKIE = "anime_cookie"
        private const val KEY_KWIK_COOKIE = "kwik_cookie"
        private const val KEY_ANIME_UA = "anime_ua"
        private const val KEY_KWIK_UA = "kwik_ua"
        private const val KEY_LAST_KWIK_URL = "last_kwik_url"

        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
    }
}
