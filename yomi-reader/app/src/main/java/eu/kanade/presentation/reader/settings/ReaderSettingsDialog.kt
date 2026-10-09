package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import app.yomi.reader.core.ReaderScaleMode
import app.yomi.reader.core.ReadingMode
import kotlin.math.roundToInt

/**
 * Port of Mihon's tabbed ReaderSettingsDialog presentation from v0.19.9.
 *
 * The original upstream source remains verbatim in mihon-upstream/.
 * This standalone adapter maps the options Yomi currently persists to the
 * Mihon reading-mode/general/filter tab layout without using manga/source DBs.
 */
@Composable
fun ReaderSettingsDialog(
    onDismissRequest: () -> Unit,
    readingMode: ReadingMode,
    onReadingModeChange: (ReadingMode) -> Unit,
    orientation: String,
    onOrientationChange: (String) -> Unit,
    scaleMode: ReaderScaleMode,
    onScaleModeChange: (ReaderScaleMode) -> Unit,
    cropEnabled: Boolean,
    onCropChange: (Boolean) -> Unit,
    showPageNumber: Boolean,
    onShowPageNumberChange: (Boolean) -> Unit,
    volumeKeys: Boolean,
    onVolumeKeysChange: (Boolean) -> Unit,
    background: String,
    onBackgroundChange: (String) -> Unit,
    dimPercent: Int,
    onDimPercentChange: (Int) -> Unit,
    colorTint: Int,
    onColorTintChange: (Int) -> Unit,
    filterBlendMode: Int,
    onFilterBlendModeChange: (Int) -> Unit,
    grayscale: Boolean,
    onGrayscaleChange: (Boolean) -> Unit,
    invertedColors: Boolean,
    onInvertedColorsChange: (Boolean) -> Unit,
) {
    val titles = listOf("Reading mode", "General", "Color filter")
    val pagerState = rememberPagerState { titles.size }
    val height = LocalConfiguration.current.screenHeightDp.dp * 0.75f

    // Mihon's original tabbed, swipeable bottom-sheet structure.
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = titles,
        pagerState = pagerState,
        modifier = Modifier.heightIn(max = height),
    ) { tab ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TabbedDialogPaddings.Horizontal, vertical = TabbedDialogPaddings.Vertical),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                            0 -> {
                                SettingHeading("For this series")
                                SettingHeading("Reading mode")
                                SettingChoices(
                                    options = listOf(
                                        "Left to right" to ReadingMode.LTR_PAGED,
                                        "Right to left" to ReadingMode.RTL_PAGED,
                                        "Vertical" to ReadingMode.VERTICAL_PAGED,
                                        "Webtoon" to ReadingMode.WEBTOON,
                                    ),
                                    value = readingMode,
                                    onChange = onReadingModeChange,
                                )
                                SettingHeading("Orientation")
                                SettingChoices(
                                    options = listOf(
                                        "Free" to "auto",
                                        "Portrait" to "portrait",
                                        "Landscape" to "landscape",
                                    ),
                                    value = orientation,
                                    onChange = onOrientationChange,
                                )
                                SettingHeading("Image scale type")
                                SettingChoices(
                                    options = listOf(
                                        "Fit screen" to ReaderScaleMode.FIT_SCREEN,
                                        "Fit width" to ReaderScaleMode.FIT_WIDTH,
                                        "Fit height" to ReaderScaleMode.FIT_HEIGHT,
                                        "Stretch" to ReaderScaleMode.STRETCH,
                                        "Original size" to ReaderScaleMode.ORIGINAL,
                                        "Smart fit" to ReaderScaleMode.SMART_FIT,
                                    ),
                                    value = scaleMode,
                                    onChange = onScaleModeChange,
                                )
                                SettingSwitch("Crop borders", cropEnabled, onCropChange)
                                SettingSwitch("Volume key navigation", volumeKeys, onVolumeKeysChange)
                            }
                            1 -> {
                                SettingHeading("Reader theme")
                                SettingChoices(
                                    options = listOf(
                                        "Black" to "black",
                                        "Gray" to "dark",
                                        "White" to "light",
                                    ),
                                    value = background,
                                    onChange = onBackgroundChange,
                                )
                                SettingSwitch("Show page number", showPageNumber, onShowPageNumberChange)
                            }
                            2 -> {
                                SettingHeading("Custom brightness")
                                Text(
                                    "Dim pages: $dimPercent%",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Slider(
                                    value = dimPercent.toFloat(),
                                    onValueChange = { onDimPercentChange(it.roundToInt()) },
                                    valueRange = 0f..90f,
                                    steps = 8,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                SettingHeading("Color filter")
                                SettingChoices(
                                    options = listOf(
                                        "Off" to 0,
                                        "Warm" to 0x55FFB567.toInt(),
                                        "Cool" to 0x5588BDFD.toInt(),
                                        "Sepia" to 0x55D0A070.toInt(),
                                        "Night" to 0x668BA7C4.toInt(),
                                    ),
                                    value = colorTint,
                                    onChange = onColorTintChange,
                                )
                                if (colorTint != 0) {
                                    SettingHeading("Custom color (RGBA)")
                                    ReaderChannelSlider("Red", android.graphics.Color.red(colorTint)) {
                                        onColorTintChange(replaceColorChannel(colorTint, it, 16))
                                    }
                                    ReaderChannelSlider("Green", android.graphics.Color.green(colorTint)) {
                                        onColorTintChange(replaceColorChannel(colorTint, it, 8))
                                    }
                                    ReaderChannelSlider("Blue", android.graphics.Color.blue(colorTint)) {
                                        onColorTintChange(replaceColorChannel(colorTint, it, 0))
                                    }
                                    ReaderChannelSlider("Opacity", android.graphics.Color.alpha(colorTint)) {
                                        onColorTintChange(replaceColorChannel(colorTint, it, 24))
                                    }
                                    SettingHeading("Filter blending mode")
                                    SettingChoices(
                                        options = listOf(
                                            "Normal" to 0,
                                            "Multiply" to 1,
                                            "Screen" to 2,
                                            "Overlay" to 3,
                                            "Lighten" to 4,
                                            "Darken" to 5,
                                        ),
                                        value = filterBlendMode,
                                        onChange = onFilterBlendModeChange,
                                    )
                                }
                                SettingSwitch("Grayscale", grayscale, onGrayscaleChange)
                                SettingSwitch("Invert colors", invertedColors, onInvertedColorsChange)
                                Text(
                                    "Mihon's reader filters apply to the displayed pages only; " +
                                        "your original CBZ stays unchanged.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
            }
        }
    }
}

@Composable
private fun SettingHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun <T> SettingChoices(
    options: List<Pair<String, T>>,
    value: T,
    onChange: (T) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (label, option) ->
            FilterChip(
                selected = option == value,
                onClick = { onChange(option) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SettingSwitch(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = onChange)
    }
}

/** Match Mihon's independent RGBA channel sliders while preserving the other channels. */
private fun replaceColorChannel(current: Int, value: Int, shift: Int): Int {
    val mask = 0xFF shl shift
    return (current and mask.inv()) or (value.coerceIn(0, 255) shl shift)
}

@Composable
private fun ReaderChannelSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Text("$label: $value", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = 0f..255f,
        modifier = Modifier.fillMaxWidth(),
    )
}
