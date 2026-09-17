package com.night.homira.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val HomiraBackground = Color(0xFF0A0E13)
val HomiraSurface = Color(0xFF111821)
val HomiraSurfaceRaised = Color(0xFF18212C)
val HomiraLine = Color(0xFF24303D)
val HomiraText = Color(0xFFF4F7FA)
val HomiraMuted = Color(0xFF95A2B2)
val HomiraGreen = Color(0xFF25D47A)
val HomiraBlue = Color(0xFF72A9FF)
val HomiraPink = Color(0xFFFF8DB5)
val HomiraDanger = Color(0xFFFF5F6D)

private val homiraDarkScheme = darkColorScheme(
    primary = HomiraGreen,
    onPrimary = Color(0xFF03130B),
    background = HomiraBackground,
    onBackground = HomiraText,
    surface = HomiraSurface,
    onSurface = HomiraText,
    surfaceVariant = HomiraSurfaceRaised,
    onSurfaceVariant = HomiraMuted,
    error = HomiraDanger
)

@Composable
fun HomiraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = homiraDarkScheme,
        content = content
    )
}
