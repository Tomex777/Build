package com.night.keyboard.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.night.keyboard.model.ClipboardItem
import com.night.keyboard.model.ClipboardKind
import com.night.keyboard.model.RetentionPreset
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardScreen(viewModel: ClipboardViewModel = hiltViewModel()) {
    val source by viewModel.items.collectAsState()
    val prefs by viewModel.prefs.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<ClipboardKind?>(null) }
    var pinnedOnly by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val visible = source.asSequence()
        .filter { !pinnedOnly || it.pinned }
        .filter { filter == null || it.kind == filter }
        .filter { query.isBlank() || it.text.contains(query, ignoreCase = true) }
        .let { seq ->
            if (prefs.keepPinnedAtTop) {
                seq.sortedWith(compareByDescending<ClipboardItem> { it.pinned }.thenBy { it.orderIndex })
            } else {
                seq.sortedBy { it.orderIndex }
            }
        }
        .toList()

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Clipboard", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                Text(
                    "Search, pin, reorder, set per-clip expiry, swipe to delete, and undo mistakes.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    label = { Text("Search clipboard") },
                    singleLine = true,
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    FilterChip(
                        selected = filter == null && !pinnedOnly,
                        onClick = { filter = null; pinnedOnly = false },
                        label = { Text("All") },
                    )
                    FilterChip(
                        selected = pinnedOnly,
                        onClick = { pinnedOnly = !pinnedOnly; if (pinnedOnly) filter = null },
                        label = { Text("Pinned") },
                    )
                    ClipboardFilterChip("Text", ClipboardKind.TEXT, filter) {
                        filter = it
                        pinnedOnly = false
                    }
                    ClipboardFilterChip("Links", ClipboardKind.LINK, filter) {
                        filter = it
                        pinnedOnly = false
                    }
                    ClipboardFilterChip("Numbers", ClipboardKind.PHONE, filter) {
                        filter = it
                        pinnedOnly = false
                    }
                    ClipboardFilterChip("Addresses", ClipboardKind.ADDRESS, filter) {
                        filter = it
                        pinnedOnly = false
                    }
                }
            }
            item {
                ClipboardSettings(
                    retentionName = prefs.defaultRetention,
                    maxHistory = prefs.maxHistory,
                    pinnedAtTop = prefs.keepPinnedAtTop,
                    canClear = source.any { !it.pinned },
                    viewModel = viewModel,
                    onClearUnpinned = { showClearConfirm = true },
                )
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        "No clips match this view.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 34.dp),
                    )
                }
            } else {
                items(visible, key = { it.id }) { item ->
                    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            viewModel.delete(item)
                            scope.launch {
                                val result = snackbar.showSnackbar(
                                    message = "Clip deleted",
                                    actionLabel = "Undo",
                                )
                                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                            }
                            true
                        } else {
                            false
                        }
                    })
                    SwipeToDismissBox(
                        state = state,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp))
                                    .padding(end = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        },
                    ) {
                        ClipboardRow(
                            item = item,
                            onPin = { viewModel.pin(item, !item.pinned) },
                            onCopy = {
                                context.getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("Keyboard clip", item.text))
                            },
                            onRetention = { viewModel.retention(item, it) },
                            onCustomRetention = { viewModel.customRetention(item, it) },
                            onMove = { viewModel.move(item, it) },
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear unpinned clips?") },
            text = { Text("Pinned clips stay. You can undo immediately after clearing.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        scope.launch {
                            val count = viewModel.clearUnpinnedForUndo()
                            if (count > 0) {
                                val result = snackbar.showSnackbar(
                                    message = "$count unpinned clip${if (count == 1) "" else "s"} cleared",
                                    actionLabel = "Undo",
                                )
                                if (result == SnackbarResult.ActionPerformed) viewModel.undoClear()
                            }
                        }
                    },
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ClipboardFilterChip(
    label: String,
    kind: ClipboardKind,
    current: ClipboardKind?,
    onFilter: (ClipboardKind?) -> Unit,
) {
    FilterChip(
        selected = current == kind,
        onClick = { onFilter(if (current == kind) null else kind) },
        label = { Text(label) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipboardSettings(
    retentionName: String,
    maxHistory: Int,
    pinnedAtTop: Boolean,
    canClear: Boolean,
    viewModel: ClipboardViewModel,
    onClearUnpinned: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = runCatching { RetentionPreset.valueOf(retentionName) }
        .getOrDefault(RetentionPreset.TWO_HOURS)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = current.pretty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Default retention") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                RetentionPreset.entries.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.pretty()) },
                        onClick = {
                            viewModel.setDefaultRetention(preset)
                            expanded = false
                        },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = pinnedAtTop,
                onClick = { viewModel.setPinnedAtTop(true) },
                label = { Text("Pinned at top") },
            )
            FilterChip(
                selected = !pinnedAtTop,
                onClick = { viewModel.setPinnedAtTop(false) },
                label = { Text("Free ordering") },
            )
            FilterChip(
                selected = maxHistory == 50,
                onClick = { viewModel.setMaxHistory(if (maxHistory == 50) 100 else 50) },
                label = { Text("History $maxHistory") },
            )
            OutlinedButton(
                enabled = canClear,
                onClick = onClearUnpinned,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Clear unpinned")
            }
        }
    }
}

private enum class CustomRetentionUnit(
    val label: String,
    val multiplierMinutes: Long,
) {
    MINUTES("Minutes", 1L),
    HOURS("Hours", 60L),
    DAYS("Days", 1_440L),
}

@Composable
private fun ClipboardRow(
    item: ClipboardItem,
    onPin: () -> Unit,
    onCopy: () -> Unit,
    onRetention: (RetentionPreset) -> Unit,
    onCustomRetention: (Long) -> Unit,
    onMove: (Int) -> Unit,
) {
    var retentionMenu by remember { mutableStateOf(false) }
    var showCustomRetention by remember(item.id) { mutableStateOf(false) }
    var customValue by remember(item.id) { mutableStateOf("30") }
    var customUnit by remember(item.id) { mutableStateOf(CustomRetentionUnit.MINUTES) }
    var dragTotal by remember { mutableFloatStateOf(0f) }

    val customMinutes = customValue.toLongOrNull()
        ?.takeIf { it > 0 }
        ?.let { value ->
            runCatching { Math.multiplyExact(value, customUnit.multiplierMinutes) }
                .getOrNull()
                ?.takeIf { it in 1L..525_600L }
        }

    Card(shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.DragHandle,
                contentDescription = "Hold and drag to reorder",
                modifier = Modifier
                    .padding(top = 8.dp)
                    .pointerInput(item.id) {
                        detectDragGesturesAfterLongPress(
                            onDragEnd = { dragTotal = 0f },
                            onDragCancel = { dragTotal = 0f },
                        ) { change, amount ->
                            change.consume()
                            dragTotal += amount.y
                            if (abs(dragTotal) > 58.dp.toPx()) {
                                onMove(if (dragTotal > 0) 1 else -1)
                                dragTotal = 0f
                            }
                        }
                    },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(item.text, maxLines = 4)
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.kind.name.lowercase().replaceFirstChar(Char::uppercase),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box {
                        FilterChip(
                            selected = item.pinned,
                            onClick = { retentionMenu = true },
                            label = { Text(if (item.pinned) "Pinned" else expiryLabel(item)) },
                        )
                        DropdownMenu(
                            expanded = retentionMenu,
                            onDismissRequest = { retentionMenu = false },
                        ) {
                            RetentionPreset.entries.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.pretty()) },
                                    onClick = {
                                        onRetention(preset)
                                        retentionMenu = false
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Custom…") },
                                onClick = {
                                    retentionMenu = false
                                    showCustomRetention = true
                                },
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onPin) {
                Icon(
                    Icons.Outlined.PushPin,
                    null,
                    tint = if (item.pinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy clip")
            }
        }
    }

    if (showCustomRetention) {
        AlertDialog(
            onDismissRequest = { showCustomRetention = false },
            title = { Text("Custom expiry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customValue,
                        onValueChange = { raw ->
                            customValue = raw.filter(Char::isDigit).take(6)
                        },
                        label = { Text("Duration") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = customValue.isNotBlank() && customMinutes == null,
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        CustomRetentionUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = customUnit == unit,
                                onClick = { customUnit = unit },
                                label = { Text(unit.label) },
                            )
                        }
                    }
                    Text(
                        "Maximum custom retention is 365 days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = customMinutes != null,
                    onClick = {
                        customMinutes?.let(onCustomRetention)
                        showCustomRetention = false
                    },
                ) { Text("Set expiry") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRetention = false }) { Text("Cancel") }
            },
        )
    }
}

private fun RetentionPreset.pretty(): String = when (this) {
    RetentionPreset.ONE_HOUR -> "1 hour"
    RetentionPreset.TWO_HOURS -> "2 hours"
    RetentionPreset.SIX_HOURS -> "6 hours"
    RetentionPreset.TWELVE_HOURS -> "12 hours"
    RetentionPreset.TWENTY_FOUR_HOURS -> "24 hours"
    RetentionPreset.END_OF_DAY -> "Until end of day"
    RetentionPreset.NEVER -> "Never expire"
}

private fun expiryLabel(item: ClipboardItem): String {
    val expiry = item.expiresAt ?: return "No expiry"
    val minutes = ((expiry - System.currentTimeMillis()).coerceAtLeast(0L) + 59_999L) / 60_000L
    return when {
        minutes < 60 -> "${minutes}m left"
        minutes < 1_440 -> "${(minutes + 59) / 60}h left"
        else -> "${(minutes + 1_439) / 1_440}d left"
    }
}
