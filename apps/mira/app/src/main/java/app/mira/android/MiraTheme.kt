package app.mira.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MiraColors = darkColorScheme(
    primary = Color(0xFF9DA7FF),
    secondary = Color(0xFFC4C7D7),
    background = Color(0xFF101014),
    surface = Color(0xFF17171D),
    surfaceVariant = Color(0xFF22222A),
    onBackground = Color(0xFFF1F1F5),
    onSurface = Color(0xFFF1F1F5),
)

@Composable
fun MiraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MiraColors,
        content = content,
    )
}
