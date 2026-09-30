package com.tomex777.relay.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF455CC7),
    secondary = Color(0xFF56618A),
    tertiary = Color(0xFF78547C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    secondary = Color(0xFFBEC5E8),
    tertiary = Color(0xFFE7B7E8),
)

@Composable
internal fun RelayTheme(
    mode: RelayThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        RelayThemeMode.SYSTEM -> isSystemInDarkTheme()
        RelayThemeMode.LIGHT -> false
        RelayThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
