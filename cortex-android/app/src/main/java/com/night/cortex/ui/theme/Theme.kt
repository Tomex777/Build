package com.night.cortex.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Near-black surfaces, readable contrast and restrained accents throughout Cortex.
val CortexHeader = Color(0xFF0B1018)
val CortexBackground = Color(0xFF06090F)
val CortexSurface = Color(0xFF121A26)
val CortexSurface2 = Color(0xFF1A2535)
val CortexLine = Color(0xFF2C3B4F)
val CortexText = Color(0xFFF1F5FC)
val CortexMuted = Color(0xFFB5C3D5)
val CortexAccent = Color(0xFF6B9CFF)
val CortexGood = Color(0xFF34D399)
val CortexDanger = Color(0xFFFF7186)

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
        // MaterialTheme sets typography and the palette, but a composable
        // rendered directly inside a Box/Column may otherwise inherit black
        // content from its parent (notably Settings and Files).
        content = {
            CompositionLocalProvider(LocalContentColor provides CortexText) {
                content()
            }
        },
    )
}
