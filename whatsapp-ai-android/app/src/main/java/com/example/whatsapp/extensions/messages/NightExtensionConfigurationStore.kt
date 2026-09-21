package com.example.whatsapp.extensions.messages

import android.content.Context
import org.json.JSONObject

object NightExtensionConfigurationStore {
    private const val PREFS = "night_extension_configuration"

    fun save(
        context: Context,
        extensionId: String,
        configurationId: String,
        values: JSONObject,
    ) {
        if (extensionId.isBlank() || configurationId.isBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(extensionId, configurationId), values.toString())
            .apply()
    }

    fun read(
        context: Context,
        extensionId: String,
        configurationId: String,
    ): JSONObject? {
        if (extensionId.isBlank() || configurationId.isBlank()) return null
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(extensionId, configurationId), null)
            ?: return null

        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun clear(
        context: Context,
        extensionId: String,
        configurationId: String,
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(key(extensionId, configurationId))
            .apply()
    }

    private fun key(extensionId: String, configurationId: String): String =
        extensionId.trim() + "::" + configurationId.trim()
}
