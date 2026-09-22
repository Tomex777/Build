package eu.kanade.presentation.theme.colorscheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Torri's signature Night palette.
 *
 * Near-black charcoal surfaces and a vivid magenta-red accent give Torri a
 * distinct identity while keeping long reading sessions calm and legible.
 */
internal object NightColorScheme : BaseColorScheme() {

    override val darkScheme = darkColorScheme(
        primary = Color(0xFFFF5C8A),
        onPrimary = Color(0xFF520020),
        primaryContainer = Color(0xFF780033),
        onPrimaryContainer = Color(0xFFFFD9E2),
        secondary = Color(0xFFFFB1C8),
        onSecondary = Color(0xFF650033),
        secondaryContainer = Color(0xFF8D124D),
        onSecondaryContainer = Color(0xFFFFD9E2),
        tertiary = Color(0xFFE6B8FF),
        onTertiary = Color(0xFF46205C),
        tertiaryContainer = Color(0xFF5E3774),
        onTertiaryContainer = Color(0xFFF6D9FF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0D0B0E),
        onBackground = Color(0xFFEAE0E3),
        surface = Color(0xFF100D11),
        onSurface = Color(0xFFEAE0E3),
        surfaceVariant = Color(0xFF30262A),
        onSurfaceVariant = Color(0xFFD8C1C8),
        outline = Color(0xFFA48C93),
        outlineVariant = Color(0xFF514348),
        scrim = Color(0xFF000000),
        inverseSurface = Color(0xFFEAE0E3),
        inverseOnSurface = Color(0xFF332E30),
        inversePrimary = Color(0xFFA9004A),
        surfaceContainerLowest = Color(0xFF080609),
        surfaceDim = Color(0xFF0D0B0E),
        surfaceContainerLow = Color(0xFF171215),
        surfaceContainer = Color(0xFF1C171A),
        surfaceContainerHigh = Color(0xFF272124),
        surfaceContainerHighest = Color(0xFF332C2F),
        surfaceBright = Color(0xFF3B3437),
    )

    override val lightScheme = lightColorScheme(
        primary = Color(0xFFA9004A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFD9E2),
        onPrimaryContainer = Color(0xFF3E0017),
        inversePrimary = Color(0xFFFFB1C8),
        secondary = Color(0xFF9A2559),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFD9E2),
        onSecondaryContainer = Color(0xFF3E001E),
        tertiary = Color(0xFF76508B),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFF6D9FF),
        onTertiaryContainer = Color(0xFF2D0C43),
        background = Color(0xFFFFF8FA),
        onBackground = Color(0xFF211A1C),
        surface = Color(0xFFFFF8FA),
        onSurface = Color(0xFF211A1C),
        surfaceVariant = Color(0xFFF2DDE3),
        onSurfaceVariant = Color(0xFF514348),
        surfaceTint = Color(0xFFA9004A),
        inverseSurface = Color(0xFF362F31),
        inverseOnSurface = Color(0xFFFFEDF1),
        outline = Color(0xFF837379),
        outlineVariant = Color(0xFFD6C1C7),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        surfaceDim = Color(0xFFE5D7DB),
        surfaceBright = Color(0xFFFFF8FA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFF0F4),
        surfaceContainer = Color(0xFFFAEAEE),
        surfaceContainerHigh = Color(0xFFF4E4E8),
        surfaceContainerHighest = Color(0xFFEEDFDF),
    )
}
