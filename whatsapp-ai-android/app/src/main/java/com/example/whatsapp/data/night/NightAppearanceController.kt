package com.example.whatsapp.data.night

import android.graphics.Color

class NightAppearanceController(
    private val repository: NightRepository,
) {
    suspend fun applyToolAction(setting: String, value: String): String {
        val key = setting.trim().lowercase()
        val requested = value.trim()
        require(requested.isNotBlank()) { "An appearance value is required." }
        val current = repository.ensureAppearance()

        return when (key) {
            "accent_color", "user_bubble_color", "ai_bubble_color", "wallpaper_color" -> {
                val color = parseColor(requested.lowercase())
                    ?: error("Use a supported color name or a valid #RRGGBB / #AARRGGBB value.")
                val updated = when (key) {
                    "user_bubble_color" -> current.copy(userBubbleColor = color)
                    "ai_bubble_color" -> current.copy(aiBubbleColor = color)
                    "wallpaper_color" -> current.copy(
                        wallpaperTopColor = color,
                        wallpaperMiddleColor = color,
                        wallpaperBottomColor = color,
                    )
                    else -> current.copy(accentColor = color)
                }
                repository.setAppearance(updated)
                when (key) {
                    "user_bubble_color" -> "Changed your message bubble color."
                    "ai_bubble_color" -> "Changed Night's message bubble color."
                    "wallpaper_color" -> "Changed the chat wallpaper color."
                    else -> "Changed Night's app accent."
                }
            }

            "font_family" -> {
                val font = NightAppearanceFontRegistry.find(requested)
                    ?: NightAppearanceFontRegistry.available().firstOrNull {
                        it.label.equals(requested, ignoreCase = true)
                    }
                    ?: error("That font is not registered on this device.")
                repository.setAppearance(current.copy(fontFamilyKey = font.key))
                "Changed the chat font to ${font.label}."
            }

            "font_size" -> {
                val scale = requested.removeSuffix("%").toFloatOrNull()?.let {
                    if (requested.endsWith("%")) it / 100f else it
                } ?: error("Use a font size from 0.85 to 1.30, or 85% to 130%.")
                require(scale in 0.85f..1.30f) {
                    "Font size must be between 85% and 130%."
                }
                repository.setAppearance(current.copy(messageFontScale = scale))
                "Changed the chat font size to ${(scale * 100).toInt()}%."
            }

            "theme" -> {
                require(requested.equals("dark", ignoreCase = true)) {
                    "Only Night's dark theme is available right now."
                }
                "Night is already using its dark appearance."
            }

            else -> error("Unsupported appearance setting: $setting")
        }
    }

    suspend fun handleNaturalRequest(request: String): String? {
        val q = request.lowercase()
        val current = repository.ensureAppearance()

        val parsedColor = parseColor(q)
        val isAccentRequest =
            "theme" in q ||
                "accent" in q ||
                "app color" in q ||
                "app colour" in q ||
                Regex("\\b(make|set|change|switch)\\s+(the\\s+)?night\\b").containsMatchIn(q)

        return when {
            isAccentRequest && parsedColor != null -> {
                repository.setAppearance(
                    current.copy(
                        accentColor = parsedColor,
                    )
                )
                "Changed Night's app accent."
            }

            ("dark" in q && ("ui" in q || "theme" in q)) ->
                "Night is already using its dark appearance."

            ("my bubble" in q || "my bubbles" in q || "user bubble" in q) && parsedColor != null -> {
                repository.setAppearance(current.copy(userBubbleColor = parsedColor))
                "Changed your message bubble color."
            }

            ("ai bubble" in q || "night bubble" in q || "your bubble" in q) && parsedColor != null -> {
                repository.setAppearance(current.copy(aiBubbleColor = parsedColor))
                "Changed Night's message bubble color."
            }

            ("wallpaper" in q || "chat background" in q) && parsedColor != null -> {
                repository.setAppearance(
                    current.copy(
                        wallpaperTopColor = parsedColor,
                        wallpaperMiddleColor = parsedColor,
                        wallpaperBottomColor = parsedColor,
                    )
                )
                "Changed the chat wallpaper color."
            }

            "font size" in q || "text size" in q -> {
                val scale = when {
                    "smaller" in q || "small" in q || "decrease" in q || "reduce" in q -> 0.90f
                    "larger" in q || "large" in q || "bigger" in q || "increase" in q -> 1.15f
                    "huge" in q -> 1.30f
                    "default" in q || "normal" in q -> 1.0f
                    else -> null
                }
                if (scale == null) null else {
                    repository.setAppearance(current.copy(messageFontScale = scale))
                    "Changed the chat font size."
                }
            }

            "font" in q -> {
                val family = when {
                    "default" in q || "system" in q || "sans" in q -> "system"
                    else -> NightAppearanceFontRegistry.available()
                        .firstOrNull { it.label.lowercase() in q }
                        ?.key
                }
                if (family == null || NightAppearanceFontRegistry.find(family) == null) null else {
                    repository.setAppearance(current.copy(fontFamilyKey = family))
                    "Changed the chat font."
                }
            }

            ("reset" in q && "appearance" in q) || ("reset" in q && "theme" in q) -> {
                repository.setAppearance(NightAppearanceEntity())
                "Reset Night's chat appearance."
            }

            else -> null
        }
    }

    private fun parseColor(text: String): Long? {
        val named = linkedMapOf(
            "purple" to "#6D3CC3",
            "violet" to "#7C3AED",
            "pink" to "#B83280",
            "red" to "#7E112E",
            "blue" to "#2457C5",
            "green" to "#176B4D",
            "teal" to "#0F766E",
            "orange" to "#A34E12",
            "black" to "#111111",
            "grey" to "#4B5563",
            "gray" to "#4B5563",
            "white" to "#F4F4F4",
        )

        val hex = Regex("#[0-9a-fA-F]{6,8}").find(text)?.value
            ?: named.entries.firstOrNull { it.key in text }?.value
            ?: return null

        return runCatching {
            Color.parseColor(hex).toLong() and 0xFFFFFFFFL
        }.getOrNull()
    }
}
