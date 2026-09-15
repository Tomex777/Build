package com.night.keyboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val KeyboardBackground = Color(0xFF0B0E12)
val KeyboardSurface = Color(0xFF14191F)
val KeyboardSurfaceRaised = Color(0xFF1A2027)
val KeyboardLine = Color(0xFF29313A)
val KeyboardText = Color(0xFFF3F5F7)
val KeyboardMuted = Color(0xFF8E99A5)
val KeyboardAccent = Color(0xFF6EA8FF)
val KeyboardGreen = Color(0xFF78D69C)

private val Scheme = darkColorScheme(primary = KeyboardAccent, onPrimary = Color(0xFF07111C), background = KeyboardBackground, onBackground = KeyboardText, surface = KeyboardSurface, onSurface = KeyboardText, surfaceVariant = KeyboardSurfaceRaised, onSurfaceVariant = KeyboardMuted, outline = KeyboardLine)

@Composable fun KeyboardTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = Scheme, content = content) }
