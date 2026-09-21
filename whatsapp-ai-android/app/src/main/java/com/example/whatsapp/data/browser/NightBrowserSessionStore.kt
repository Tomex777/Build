package com.example.whatsapp.data.browser

import android.content.Context

object NightBrowserSessionStore {
    private const val PREFS = "night_browser_sessions"

    fun currentUrl(
        context: Context,
        spec: NightBrowserSpec,
    ): String {
        val safe = spec.sanitized()
        val stored = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(urlKey(safe.sessionId), null)
            ?.trim()
            .orEmpty()

        return stored
            .takeIf(safe::isAllowedUrl)
            ?: safe.initialUrl
    }

    fun saveCurrentUrl(
        context: Context,
        spec: NightBrowserSpec,
        url: String,
    ) {
        val safe = spec.sanitized()
        if (!safe.isAllowedUrl(url)) return

        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(urlKey(safe.sessionId), url.trim().take(4096))
            .apply()
    }

    fun clear(
        context: Context,
        sessionId: String,
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(urlKey(sessionId))
            .apply()
    }

    private fun urlKey(sessionId: String): String =
        "url::" + sessionId.trim()
}
