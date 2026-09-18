package com.example.whatsapp.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = WhatsAppGreen,
    secondary = WhatsAppMintGreen,
    tertiary = Pink80,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = DarkSecondaryText
)

private val LightColorScheme = lightColorScheme(
    primary = WhatsAppGreen,
    secondary = WhatsAppMintGreen,
    tertiary = Pink40,
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F2F5),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    onSurfaceVariant = Color.Gray
)

val LocalThemePreference = compositionLocalOf<Boolean> { false }

@Composable
fun WhatsappTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // Use the darkTheme parameter directly (passed from MainActivity observing ThemeViewModel)
    val isDark = darkTheme
    
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalThemePreference provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}