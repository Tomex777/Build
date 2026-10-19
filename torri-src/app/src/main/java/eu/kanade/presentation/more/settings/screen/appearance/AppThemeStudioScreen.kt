package eu.kanade.presentation.more.settings.screen.appearance

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.theme.colorscheme.CustomColorScheme
import eu.kanade.presentation.util.Screen
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import android.graphics.Color as AndroidColor

/**
 * Torri color studio: local gesture picker and custom HEX field, real Material color-role preview.
 * Nothing here modifies the user's Library grid, navigation icons or reader.
 */
class AppThemeStudioScreen : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val preferences = remember { context.appGraph.uiPreferences }

        var accent by remember { mutableStateOf(preferences.customThemeAccent.get()) }
        var darkBackground by remember { mutableStateOf(preferences.customThemeDarkBackground.get()) }
        var lightBackground by remember { mutableStateOf(preferences.customThemeLightBackground.get()) }
        var editing by remember { mutableIntStateOf(0) }
        var previewDark by remember { mutableStateOf(true) }

        val values = listOf(accent, darkBackground, lightBackground)
        val selected = values[editing]
        val validSelected = CustomColorScheme.normalizeHex(selected)
        val valid = values.all { CustomColorScheme.normalizeHex(it) != null }

        fun updateSelected(value: String) {
            when (editing) {
                0 -> accent = value
                1 -> darkBackground = value
                else -> lightBackground = value
            }
        }

        val hsv = FloatArray(3)
        val previewColor = CustomColorScheme.toColor(
            selected,
            when (editing) {
                0 -> CustomColorScheme.DEFAULT_ACCENT
                1 -> CustomColorScheme.DEFAULT_DARK_BACKGROUND
                else -> CustomColorScheme.DEFAULT_LIGHT_BACKGROUND
            },
        )
        AndroidColor.colorToHSV(previewColor.toArgb(), hsv)

        fun updateHsv(hue: Float, saturation: Float, brightness: Float) {
            val rgb = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xFFFFFF
            updateSelected("#${rgb.toString(16).padStart(6, '0').uppercase()}")
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.torri_theme_studio),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(MR.strings.torri_theme_preview),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TorriStudioSegments(
                        titles = listOf(
                            stringResource(MR.strings.torri_theme_edit_dark),
                            stringResource(MR.strings.torri_theme_edit_light),
                        ),
                        selected = if (previewDark) 0 else 1,
                        onSelect = { previewDark = it == 0 },
                        modifier = Modifier.width(160.dp),
                    )
                }

                MaterialExpressiveTheme(
                    colorScheme = CustomColorScheme.colorScheme(
                        isDark = previewDark,
                        isAmoled = false,
                        accentHex = accent,
                        darkBackgroundHex = darkBackground,
                        lightBackgroundHex = lightBackground,
                    ),
                ) {
                    TorriThemeLivePreview(modifier = Modifier.fillMaxWidth())
                }

                Text(
                    text = stringResource(MR.strings.torri_theme_customize),
                    style = MaterialTheme.typography.titleMedium,
                )

                TorriStudioSegments(
                    titles = listOf(
                        stringResource(MR.strings.torri_theme_accent),
                        stringResource(MR.strings.torri_theme_edit_dark),
                        stringResource(MR.strings.torri_theme_edit_light),
                    ),
                    selected = editing,
                    onSelect = { index ->
                        editing = index
                        if (index != 0) previewDark = index == 1
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                TorriHexField(
                    value = selected,
                    onValueChange = ::updateSelected,
                    isValid = validSelected != null,
                    previewColor = previewColor,
                    modifier = Modifier.fillMaxWidth(),
                )

                TorriColorPicker(
                    hue = hsv[0],
                    saturation = hsv[1],
                    brightness = hsv[2],
                    onHsvChanged = ::updateHsv,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    listOf(
                        "#FF5C8A", "#E8C56C", "#8BE9FD", "#BD93F9", "#9CCFD8",
                        "#3D8D72", "#15151B", "#282A36", "#FFF8FA", "#FDF6E3",
                    ).forEach { hex ->
                        val swatch = CustomColorScheme.toColor(hex, hex)
                        Spacer(
                            modifier = Modifier
                                .size(42.dp)
                                .border(
                                    width = if (selected.equals(hex, ignoreCase = true)) 2.5.dp else 1.dp,
                                    color = if (selected.equals(hex, ignoreCase = true)) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape,
                                )
                                .padding(4.dp)
                                .background(swatch, CircleShape)
                                .clickable { updateSelected(hex) },
                        )
                    }
                }

                Button(
                    onClick = {
                        preferences.customThemeAccent.set(CustomColorScheme.normalizeHex(accent)!!)
                        preferences.customThemeDarkBackground.set(CustomColorScheme.normalizeHex(darkBackground)!!)
                        preferences.customThemeLightBackground.set(CustomColorScheme.normalizeHex(lightBackground)!!)
                        preferences.appTheme.set(AppTheme.CUSTOM)
                        navigator.pop()
                        (context as? Activity)?.let(ActivityCompat::recreate)
                    },
                    enabled = valid,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(stringResource(MR.strings.torri_theme_apply))
                }

                TextButton(
                    onClick = {
                        accent = CustomColorScheme.DEFAULT_ACCENT
                        darkBackground = CustomColorScheme.DEFAULT_DARK_BACKGROUND
                        lightBackground = CustomColorScheme.DEFAULT_LIGHT_BACKGROUND
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(MR.strings.torri_theme_reset))
                }
            }
        }
    }
}
