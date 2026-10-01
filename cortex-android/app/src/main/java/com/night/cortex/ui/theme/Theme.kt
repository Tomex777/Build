package com.night.cortex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CortexHeader = Color(0xFF0D131A)
val CortexBackground = Color(0xFF090E13)
val CortexSurface = Color(0xFF111923)
val CortexSurface2 = Color(0xFF18232E)
val CortexLine = Color(0xFF263442)
val CortexText = Color(0xFFE5EAF0)
val CortexMuted = Color(0xFF8A98A7)
val CortexAccent = Color(0xFF6B94B8)
val CortexGood = Color(0xFF5C9B79)
val CortexDanger = Color(0xFFC9666B)

private val CortexColors = darkColorScheme(
    primary = CortexAccent,
    onPrimary = Color.White,
    background = CortexBackground,
    onBackground = CortexText,
    surface = CortexSurface,
    onSurface = CortexText,
    surfaceVariant = CortexSurface2,
    onSurfaceVariant = CortexMuted,
    outline = CortexLine,
    error = CortexDanger,
)

@Composable
fun CortexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CortexColors,
        content = content,
    )
}
