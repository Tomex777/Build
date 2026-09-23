package com.night.cortex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Matches the familiar Bot-Hosting/Pterodactyl dark palette the Cortex UI is based on.
val CortexBackground = Color(0xFF1F2933)
val CortexSurface = Color(0xFF33404C)
val CortexSurface2 = Color(0xFF3F4D5A)
val CortexLine = Color(0xFF515F6C)
val CortexText = Color(0xFFF5F7FA)
val CortexMuted = Color(0xFF9AA5B1)
val CortexAccent = Color(0xFF2563EB)
val CortexGood = Color(0xFF16A34A)
val CortexDanger = Color(0xFFEF4444)

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
