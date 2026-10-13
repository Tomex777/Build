package eu.kanade.presentation.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.yomi.reader.R
import app.yomi.reader.core.ReadingMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation

/**
 * Local-host ports of Mihon's v0.19.9 ReadingModeSelectDialog,
 * OrientationSelectDialog and ModeSelectionDialog presentation.
 * Mihon's manga screen model and resources are replaced by Yomi's local values,
 * while the icon grid and explicit Apply/default footer are retained.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingModeSelectDialog(
    current: ReadingMode,
    onDismissRequest: () -> Unit,
    onApply: (ReadingMode) -> Unit,
) {
    var selected by remember(current) { mutableStateOf(current) }
    val modes = listOf(
        Triple("Left to right", ReadingMode.LTR_PAGED, R.drawable.ic_reader_ltr_24dp),
        Triple("Right to left", ReadingMode.RTL_PAGED, R.drawable.ic_reader_rtl_24dp),
        Triple("Vertical", ReadingMode.VERTICAL_PAGED, R.drawable.ic_reader_vertical_24dp),
        Triple("Webtoon", ReadingMode.WEBTOON, R.drawable.ic_reader_webtoon_24dp),
    )
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                "Reading mode",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(modes) { (label, mode, icon) ->
                    SelectionTile(
                        title = label,
                        selected = selected == mode,
                        onClick = { selected = mode },
                    ) {
                        Icon(painter = painterResource(icon), contentDescription = null)
                    }
                }
            }
            SelectionFooter(
                onUseDefault = { onApply(ReadingMode.LTR_PAGED) }.takeIf {
                    current != ReadingMode.LTR_PAGED
                },
                onApply = { onApply(selected) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrientationSelectDialog(
    current: String,
    onDismissRequest: () -> Unit,
    onApply: (String) -> Unit,
) {
    var selected by remember(current) { mutableStateOf(current) }
    val orientations = listOf(
        Triple("Free", "auto", ReaderOrientation.DEFAULT.icon),
        Triple("Portrait", "portrait", ReaderOrientation.PORTRAIT.icon),
        Triple("Landscape", "landscape", ReaderOrientation.LANDSCAPE.icon),
    )
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                "Orientation",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(orientations) { (label, orientation, icon) ->
                    SelectionTile(
                        title = label,
                        selected = selected == orientation,
                        onClick = { selected = orientation },
                    ) {
                        Icon(imageVector = icon, contentDescription = null)
                    }
                }
            }
            SelectionFooter(
                onUseDefault = { onApply("auto") }.takeIf { current != "auto" },
                onApply = { onApply(selected) },
            )
        }
    }
}

@Composable
private fun SelectionTile(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
        ) {
            icon()
            Text(title, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SelectionFooter(onUseDefault: (() -> Unit)?, onApply: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        onUseDefault?.let { action ->
            OutlinedButton(onClick = action) { Text("Use default") }
        }
        Spacer(modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = onApply) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(imageVector = Icons.Outlined.Check, contentDescription = null)
                Text("Apply")
            }
        }
    }
}
