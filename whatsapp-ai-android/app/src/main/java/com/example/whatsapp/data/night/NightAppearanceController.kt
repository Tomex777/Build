package com.example.whatsapp.data.night

import android.graphics.Color

class NightAppearanceController(
    private val repository: NightRepository,
) {
    suspend fun handleNaturalRequest(request: String): String? {
        val q = request.lowercase()
        val current = repository.ensureAppearance()

        val parsedColor = parseColor(q)

        return when {
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
                    "smaller" in q || "small" in q -> 0.90f
                    "larger" in q || "large" in q || "bigger" in q -> 1.15f
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
                    "mono" in q -> "monospace"
                    "serif" in q -> "serif"
                    "cursive" in q -> "cursive"
                    "system" in q || "default" in q || "sans" in q -> "system"
                    else -> null
                }
                if (family == null) null else {
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
