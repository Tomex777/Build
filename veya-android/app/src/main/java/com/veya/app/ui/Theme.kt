package com.veya.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF6558D3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E0FF),
    onPrimaryContainer = Color(0xFF20145F),
    secondary = Color(0xFF2E746B),
    surface = Color(0xFFFCF9FF),
    surfaceContainer = Color(0xFFF3F0F7),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC9BFFF),
    onPrimary = Color(0xFF352877),
    primaryContainer = Color(0xFF4C4094),
    secondary = Color(0xFF8FD5CA),
    surface = Color(0xFF121217),
    surfaceContainer = Color(0xFF1E1D24),
)

@androidx.compose.runtime.Composable
fun VeyaTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) Dark else Light
    MaterialTheme(colorScheme = colors, content = content)
}
