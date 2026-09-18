package com.night.kwikmanual

import android.content.Context
import java.net.URI
import java.security.MessageDigest
import java.util.Locale

data class ManualSessionSnapshot(
    val kwikHost: String,
    val cookieSaved: Boolean,
    val cookieSummary: String,
)

class ManualSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("kwik_manual_sessions", Context.MODE_PRIVATE)

    fun kwikHost(): String = prefs.getString(KEY_HOST, "").orEmpty()
    fun kwikCookie(): String = prefs.getString(KEY_COOKIE, "").orEmpty()
    fun userAgent(): String = prefs.getString(KEY_UA, DEFAULT_UA) ?: DEFAULT_UA
    fun lastUrl(): String = prefs.getString(KEY_URL, "").orEmpty()
    fun referer(): String = prefs.getString(KEY_REFERER, "https://animepahe.pw/").orEmpty()

    fun saveUrl(url: String, referer: String) {
        prefs.edit()
            .putString(KEY_URL, url)
            .putString(KEY_REFERER, referer)
            .apply()
    }

    fun saveSession(url: String, cookie: String, userAgent: String) {
        val host = runCatching { URI(url).host.orEmpty().lowercase(Locale.US) }.getOrDefault("")
        prefs.edit()
            .putString(KEY_HOST, host)
            .putString(KEY_COOKIE, cookie)
            .putString(KEY_UA, userAgent.ifBlank { DEFAULT_UA })
            .putString(KEY_URL, url)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun snapshot(): ManualSessionSnapshot = ManualSessionSnapshot(
        kwikHost = kwikHost(),
        cookieSaved = kwikCookie().isNotBlank(),
        cookieSummary = summarizeCookie(kwikCookie()),
    )

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
        private const val KEY_HOST = "kwik_host"
        private const val KEY_COOKIE = "kwik_cookie"
        private const val KEY_UA = "kwik_ua"
        private const val KEY_URL = "kwik_url"
        private const val KEY_REFERER = "referer"

        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
    }
}
