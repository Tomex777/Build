package com.night.sora.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val SoraBg = Color(0xFF10100F)
val SoraSurface = Color(0xFF181816)
val SoraSurfaceHigh = Color(0xFF20201D)
val SoraSurfaceRaised = Color(0xFF292925)
val SoraText = Color(0xFFF3F1EA)
val SoraMuted = Color(0xFFA4A198)
val SoraFaint = Color(0xFF737168)
val SoraAccent = Color(0xFFE9B949)
val SoraAccentInk = Color(0xFF17140A)
val SoraDanger = Color(0xFFE98573)

private val DarkColors = darkColorScheme(
    primary = SoraAccent,
    onPrimary = SoraAccentInk,
    secondary = SoraAccent,
    onSecondary = SoraAccentInk,
    background = SoraBg,
    onBackground = SoraText,
    surface = SoraSurface,
    onSurface = SoraText,
    surfaceVariant = SoraSurfaceHigh,
    onSurfaceVariant = SoraMuted,
    outline = Color(0xFF34322C),
    error = SoraDanger,
)

@Composable
fun SoraTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.statusBarColor = SoraBg.toArgb()
        window.navigationBarColor = SoraBg.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
    }
    MaterialTheme(
        colorScheme = DarkColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
