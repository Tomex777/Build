package com.night.extensionchat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NightBackground = Color(0xFF0A0C0F)
val NightSurface = Color(0xFF111419)
val NightSurfaceRaised = Color(0xFF171B21)
val NightSurfaceSoft = Color(0xFF1D222A)
val NightStroke = Color(0xFF2A313B)
val NightText = Color(0xFFF2F5F7)
val NightMuted = Color(0xFF9BA6B2)
val NightAccent = Color(0xFF7EB8FF)
val NightAccentSoft = Color(0xFF162943)
val NightSuccess = Color(0xFF7BD6B2)
val NightUserBubble = Color(0xFF17345A)

private val NightColors = darkColorScheme(
    primary = NightAccent,
    onPrimary = Color(0xFF07111E),
    primaryContainer = NightAccentSoft,
    onPrimaryContainer = Color(0xFFDCEAFF),
    background = NightBackground,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightSurfaceRaised,
    onSurfaceVariant = NightMuted,
    outline = NightStroke
)

@Composable
fun ExtensionChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightColors,
        content = content
    )
}
