package com.example.whatsapp.presentation.chatscreen

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

data class NightChatAppearance(
    val userBubbleColor: Color = Color(0xFF7E112E),
    val aiBubbleColor: Color = Color(0xFF242625),
    val wallpaperTopColor: Color = Color(0xFF6A0011),
    val wallpaperMiddleColor: Color = Color(0xFF8F0018),
    val wallpaperBottomColor: Color = Color(0xFF4F000E),
    val accentColor: Color = Color(0xFFCF4A69),
    val fontFamilyKey: String = "system",
    val messageFontScale: Float = 1f,
) {
    val fontFamily: FontFamily
        get() = when (fontFamilyKey) {
            "serif" -> FontFamily.Serif
            "monospace" -> FontFamily.Monospace
            "cursive" -> FontFamily.Cursive
            else -> FontFamily.Default
        }
}
