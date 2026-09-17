package com.night.cortex.data

import android.content.Context

class ConnectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("cortex_connection", Context.MODE_PRIVATE)

    fun loadBaseUrl(): String = prefs.getString("base_url", "") ?: ""

    fun saveBaseUrl(baseUrl: String) {
        prefs.edit().putString("base_url", baseUrl.trim().removeSuffix("/")).apply()
    }
}
