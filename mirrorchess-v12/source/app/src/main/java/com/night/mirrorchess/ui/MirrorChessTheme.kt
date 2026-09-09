package com.night.mirrorchess.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DisplayFamily = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.Normal),
)

private val BodyFamily = FontFamily(
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.Normal),
)

private val MirrorTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val MirrorDark = darkColorScheme(
    primary = Color(0xFFB7D986),
    onPrimary = Color(0xFF16200D),
    primaryContainer = Color(0xFF2B3921),
    onPrimaryContainer = Color(0xFFE3F3CC),
    background = Color(0xFF101210),
    onBackground = Color(0xFFF4F4EE),
    surface = Color(0xFF191B19),
    onSurface = Color(0xFFF4F4EE),
    surfaceVariant = Color(0xFF252825),
    onSurfaceVariant = Color(0xFFC1C5BC),
    outline = Color(0xFF3B3F3A),
    outlineVariant = Color(0xFF2B2E2A),
    error = Color(0xFFE2A29B),
    onError = Color(0xFF2D0805),
)

@Composable
fun MirrorChessTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MirrorDark,
        typography = MirrorTypography,
        content = content,
    )
}
