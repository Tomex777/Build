package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.yomi.reader.core.ReaderScaleMode
import app.yomi.reader.core.ReadingMode
import kotlinx.coroutines.launch
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
) {
    val titles = listOf("Reading mode", "General", "Color filter")
    val pagerState = rememberPagerState { titles.size }
    val scope = rememberCoroutineScope()
    val height = LocalConfiguration.current.screenHeightDp.dp * 0.75f

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = height).windowInsetsPadding(WindowInsets.navigationBars),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column {
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    divider = {},
                ) {
                    titles.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(title) },
                        )
                    }
                }
                HorizontalDivider()
                HorizontalPager(state = pagerState, verticalAlignment = Alignment.Top) { tab ->
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp),
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
                                Text(
                                    "Based on Mihon's original ReaderContentOverlay. " +
                                        "The filter applies immediately without modifying the CBZ.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
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
