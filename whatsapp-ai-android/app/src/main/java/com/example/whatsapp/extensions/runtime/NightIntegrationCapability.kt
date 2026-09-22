package com.example.whatsapp.extensions.runtime

import java.util.Locale

enum class NightIntegrationCapability(val wireName: String) {
    Anime("anime"),
    Manga("manga"),
    Music("music"),
    Browser("browser"),
    Files("files"),
    Productivity("productivity"),
    Search("search"),
    GeneralTool("tool");

    companion object {
        fun fromWireName(value: String): NightIntegrationCapability? {
            val normalized = value.trim().lowercase(Locale.US)
            if (normalized.isBlank()) return null
            return entries.firstOrNull { it.wireName == normalized }
        }
    }
}
