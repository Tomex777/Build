package app.nami.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NamiDarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF06285A),
    primaryContainer = Color(0xFF173A67),
    onPrimaryContainer = Color(0xFFD7E7FF),
    secondary = Color(0xFFA8C7FA),
    onSecondary = Color(0xFF0E305B),
    secondaryContainer = Color(0xFF213E63),
    onSecondaryContainer = Color(0xFFD8E8FF),
    tertiary = Color(0xFFC4C7FF),
    onTertiary = Color(0xFF2B2D64),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE5EAF0),
    surface = Color(0xFF10161D),
    onSurface = Color(0xFFE5EAF0),
    surfaceVariant = Color(0xFF18212C),
    onSurfaceVariant = Color(0xFFBBC5D0),
    outline = Color(0xFF66717E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
internal fun NamiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NamiDarkColors,
        content = content,
    )
}
