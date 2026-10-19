package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import android.graphics.Color as AndroidColor

/**
 * Torri's user-editable palette. Only valid opaque RGB colors are accepted;
 * malformed saved values safely fall back to the defaults.
 */
internal object CustomColorScheme {
    const val DEFAULT_ACCENT = "#FF5C8A"
    const val DEFAULT_DARK_BACKGROUND = "#0D0B0E"
    const val DEFAULT_LIGHT_BACKGROUND = "#FFF8FA"

    fun normalizeHex(input: String): String? {
        val rgb = input.trim().removePrefix("#")
        if (rgb.length != 6 || !rgb.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }) {
            return null
        }
        return "#${rgb.uppercase()}"
    }

    fun toColor(input: String, fallback: String): Color {
        val valid = normalizeHex(input) ?: fallback
        return Color(AndroidColor.parseColor(valid))
    }

    fun colorScheme(
        isDark: Boolean,
        isAmoled: Boolean,
        accentHex: String,
        darkBackgroundHex: String,
        lightBackgroundHex: String,
    ): ColorScheme {
        val accent = toColor(accentHex, DEFAULT_ACCENT)
        val savedBackground = toColor(
            if (isDark) darkBackgroundHex else lightBackgroundHex,
            if (isDark) DEFAULT_DARK_BACKGROUND else DEFAULT_LIGHT_BACKGROUND,
        )
        // AMOLED is always true black, even with a customized dark background.
        val background = if (isDark && isAmoled) Color.Black else savedBackground
        val contentColor = contrastingText(background)
        val secondary = lerp(accent, contentColor, 0.20f)
        val tertiary = shiftedHue(accent, 105f)
        val primaryContainer = lerp(background, accent, if (isDark) 0.36f else 0.20f)
        val secondaryContainer = lerp(background, secondary, if (isDark) 0.28f else 0.17f)
        val tertiaryContainer = lerp(background, tertiary, if (isDark) 0.28f else 0.17f)

        val low = lerp(background, contentColor, if (isDark) 0.045f else 0.025f)
        val medium = lerp(background, contentColor, if (isDark) 0.09f else 0.045f)
        val high = lerp(background, contentColor, if (isDark) 0.14f else 0.085f)
        val highest = lerp(background, contentColor, if (isDark) 0.19f else 0.125f)

        return (if (isDark) NightColorScheme.darkScheme else NightColorScheme.lightScheme).copy(
            primary = accent,
            onPrimary = contrastingText(accent),
            primaryContainer = primaryContainer,
            onPrimaryContainer = contrastingText(primaryContainer),
            inversePrimary = accent,
            secondary = secondary,
            onSecondary = contrastingText(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = contrastingText(secondaryContainer),
            tertiary = tertiary,
            onTertiary = contrastingText(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = contrastingText(tertiaryContainer),
            background = background,
            onBackground = contentColor,
            surface = background,
            onSurface = contentColor,
            surfaceVariant = high,
            onSurfaceVariant = contrastingText(high),
            outline = lerp(background, contentColor, 0.45f),
            outlineVariant = lerp(background, contentColor, 0.25f),
            surfaceDim = background,
            surfaceBright = lerp(background, contentColor, if (isDark) 0.24f else 0.01f),
            surfaceContainerLowest = if (isDark) background else Color.White,
            surfaceContainerLow = low,
            surfaceContainer = medium,
            surfaceContainerHigh = high,
            surfaceContainerHighest = highest,
            surfaceTint = accent,
            inverseSurface = contentColor,
            inverseOnSurface = background,
        )
    }

    private fun contrastingText(color: Color): Color {
        val red = color.red.toDouble()
        val green = color.green.toDouble()
        val blue = color.blue.toDouble()
        fun linear(channel: Double) = if (channel <= 0.04045) {
            channel / 12.92
        } else {
            Math.pow((channel + 0.055) / 1.055, 2.4)
        }
        val brightness = 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
        return if (brightness > 0.179) Color(0xFF161217) else Color.White
    }

    private fun shiftedHue(color: Color, degrees: Float): Color {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(
            AndroidColor.rgb(
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt(),
            ),
            hsv,
        )
        hsv[0] = (hsv[0] + degrees) % 360f
        return Color(AndroidColor.HSVToColor(hsv))
    }
}
