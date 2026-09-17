package com.night.cortex.hosting

import android.content.Context

class HostingConnectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("cortex_hosting", Context.MODE_PRIVATE)

    fun identifier(provider: HostingProviderId): String = when (provider) {
        HostingProviderId.BOT_HOSTING -> prefs.getString("bot_hosting_deployment_id", "") ?: ""
        HostingProviderId.AZURE -> prefs.getString("azure_agent_url", "") ?: ""
    }

    fun saveIdentifier(provider: HostingProviderId, value: String) {
        val clean = when (provider) {
            HostingProviderId.BOT_HOSTING -> value.trim()
            HostingProviderId.AZURE -> value.trim().removeSuffix("/")
        }
        val key = when (provider) {
            HostingProviderId.BOT_HOSTING -> "bot_hosting_deployment_id"
            HostingProviderId.AZURE -> "azure_agent_url"
        }
        prefs.edit().putString(key, clean).apply()
    }
}
