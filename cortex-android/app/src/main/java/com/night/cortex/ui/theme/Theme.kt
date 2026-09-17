package com.night.cortex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CortexBackground = Color(0xFF0C0D0F)
val CortexSurface = Color(0xFF14161A)
val CortexSurface2 = Color(0xFF191C21)
val CortexLine = Color(0xFF2A2E35)
val CortexText = Color(0xFFF4F5F7)
val CortexMuted = Color(0xFF9DA3AD)
val CortexAccent = Color(0xFFECECEA)
val CortexGood = Color(0xFF66D69E)
val CortexDanger = Color(0xFFFF6B6B)

private val CortexColors = darkColorScheme(
    primary = CortexAccent,
    onPrimary = Color(0xFF111214),
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
