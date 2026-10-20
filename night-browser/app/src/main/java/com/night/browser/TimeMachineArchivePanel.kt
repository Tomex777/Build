package com.night.browser

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import java.text.DateFormat
import java.util.Date

private val PageInk = Color(0xFF0C1017)
private val PageRaised = Color(0xFF171D27)
private val PageBorder = Color(0xFF2B3441)
private val PageBright = Color(0xFFF3F5F9)
private val PageMuted = Color(0xFF8F9BAC)
private val PageAccent = Color(0xFFB9BEFF)

/**
 * A version browser for PDFs, not a replay of the live website.
 * Exports use Android's document picker; no broad storage permission is required.
 */
@Composable
fun TimeMachineArchivePanel(
    archive: TimeMachineArchive,
    openSaved: (TimeMachineVersion) -> Unit,
    onNotice: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<TimeMachineVersion?>(null) }
    var pendingExport by remember { mutableStateOf<TimeMachineVersion?>(null) }
    var oldestFirst by rememberSaveable { mutableStateOf(false) }
    var pendingCompare by remember { mutableStateOf<Pair<TimeMachineVersion, TimeMachineVersion>?>(null) }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        val version = pendingExport
        pendingExport = null
        if (uri != null && version != null) archive.exportToUri(version, uri, onNotice)
    }

    val filtered = archive.versions.filter {
        query.isBlank() ||
            it.title.contains(query.trim(), ignoreCase = true) ||
            it.url.contains(query.trim(), ignoreCase = true)
    }
    val groups = filtered.groupBy {
        // Recovered orphan copies do not have a known URL; never group unrelated files.
        TimeMachineLimits.pageKey(it.url).ifBlank { "recovered:" + it.id }
    }.entries.sortedWith(compareBy { entry ->
        val newest = entry.value.maxOfOrNull { it.capturedAt } ?: 0L
        if (oldestFirst) newest else -newest
    })
    val used = archive.usedBytes

    Column(Modifier.fillMaxSize().background(PageInk)) {
        Text(
            "Time Machine", fontSize = 27.sp, fontWeight = FontWeight.SemiBold,
            color = PageBright, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp)
        )
        Text(
            "PDF copies only · not interactive offline webpages",
            color = PageMuted, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                .background(PageRaised, RoundedCornerShape(16.dp)).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Storage", color = PageBright, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    formatArchiveBytes(used) + " / 150 MB · " + archive.versions.size + "/120 copies",
                    color = PageMuted, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (used.toFloat() / TimeMachineLimits.MAX_BYTES).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = PageAccent,
                trackColor = PageBorder
            )
            Text(
                "Each copy is saved separately. Old copies are never deleted automatically.",
                color = PageMuted, fontSize = 11.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { oldestFirst = !oldestFirst }) {
                    Text(
                        if (oldestFirst) "Oldest first" else "Newest first",
                        fontSize = 12.sp, color = PageAccent
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .background(PageRaised, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = PageMuted)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query, onValueChange = { query = it },
                singleLine = true, cursorBrush = SolidColor(PageAccent),
                textStyle = TextStyle(color = PageBright, fontSize = 14.sp),
                modifier = Modifier.weight(1f),
                decorationBox = { field ->
                    if (query.isBlank()) Text(
                        "Search saved page titles or addresses",
                        color = PageMuted, fontSize = 13.sp
                    )
                    field()
                }
            )
        }

        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(25.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) "Save a PDF copy from any open webpage."
                    else "No PDF versions match your search.",
                    color = PageMuted, fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groups, key = { it.key }) { entry ->
                    val chronology = entry.value.sortedBy { it.capturedAt }
                    val pages = if (oldestFirst) chronology else chronology.asReversed()
                    val latest = chronology.last()
                    val isExpanded = expanded == entry.key || (expanded.isBlank() && groups.size == 1)
                    Column(
                        Modifier.fillMaxWidth()
                            .background(PageRaised, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                expanded = if (isExpanded) "_collapsed" else entry.key
                            }.padding(start = 16.dp, end = 9.dp, top = 13.dp, bottom = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (archive.health(latest) == ArchiveFileHealth.READY) {
                                TimeMachinePdfPreview(archive, latest, 43.dp, 62.dp)
                            } else {
                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = PageAccent)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    latest.title.ifBlank { latest.url },
                                    color = PageBright, fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    latest.url,
                                    color = PageMuted, fontSize = 11.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${pages.size} ${if (pages.size == 1) "version" else "versions"}",
                                    color = PageAccent, fontSize = 11.sp
                                )
                            }
                            Icon(
                                if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse versions" else "Show versions",
                                tint = PageMuted
                            )
                        }
                        if (isExpanded) {
                            pages.forEach { item ->
                                val older = chronology.indexOfFirst { it.id == item.id }
                                    .let { i -> if (i > 0) chronology[i - 1] else null }
                                HorizontalDivider(color = PageBorder)
                                TimeMachineVersionRow(
                                    item = item,
                                    health = archive.health(item),
                                    onOpen = { openSaved(item) },
                                    onExport = {
                                        pendingExport = item
                                        exportPicker.launch(archive.suggestedFileName(item))
                                    },
                                    onDelete = { pendingDelete = item },
                                    onCompare = if (older != null &&
                                        archive.health(item) == ArchiveFileHealth.READY &&
                                        archive.health(older) == ArchiveFileHealth.READY
                                    ) ({ pendingCompare = item to older }) else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingCompare?.let { (newer, older) ->
        AlertDialog(
            onDismissRequest = { pendingCompare = null },
            title = { Text("Compare PDF versions") },
            text = {
                Column {
                    Text("First-page visual comparison · PDF copies, not live page history",
                        color = PageMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(newer, older).forEachIndexed { i, item ->
                            Column(Modifier.weight(1f)) {
                                Text(if (i == 0) "Selected" else "Previous",
                                    color = PageBright, fontSize = 12.sp)
                                Text(versionDate(item.capturedAt),
                                    color = PageMuted, fontSize = 10.sp)
                                Spacer(Modifier.height(8.dp))
                                TimeMachinePdfPreview(
                                    archive = archive, version = item,
                                    width = 116.dp, height = 160.dp
                                )
                                Text(formatArchiveBytes(item.sizeBytes),
                                    color = PageMuted, fontSize = 11.sp)
                            }
                        }
                    }
                    val difference = newer.sizeBytes - older.sizeBytes
                    Text(
                        "Size change: " + when {
                            difference > 0 -> "+" + formatArchiveBytes(difference)
                            difference < 0 -> "-" + formatArchiveBytes(-difference)
                            else -> "No size change"
                        },
                        color = PageMuted, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingCompare = null }) { Text("Close") }
            },
            containerColor = PageRaised
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this PDF version?") },
            text = {
                Text(
                    "The local PDF file and its Time Machine record will be deleted. " +
                        "Other versions of this page remain."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onNotice(
                        if (archive.delete(item)) "PDF version deleted."
                        else "Couldn't fully remove this version. Check the archive."
                    )
                }) { Text("Delete", color = Color(0xFFFFA9AE)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
            containerColor = PageRaised
        )
    }
}

@Composable
private fun TimeMachineVersionRow(
    item: TimeMachineVersion,
    health: ArchiveFileHealth,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onCompare: (() -> Unit)?
) {
    Column {
    Row(
        Modifier.fillMaxWidth().padding(start = 17.dp, end = 7.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(versionDate(item.capturedAt), color = PageBright, fontSize = 13.sp)
            Text(
                when (health) {
                    ArchiveFileHealth.READY -> "PDF · " + formatArchiveBytes(item.sizeBytes)
                    ArchiveFileHealth.MISSING -> "PDF file missing"
                    ArchiveFileHealth.CORRUPT -> "PDF file damaged"
                },
                color = if (health == ArchiveFileHealth.READY) PageMuted else Color(0xFFFFA9AE),
                fontSize = 11.sp
            )
        }
        val ready = health == ArchiveFileHealth.READY
        IconButton(onClick = onOpen, enabled = ready, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Rounded.FolderOpen, "Open PDF version", tint = if (ready) PageAccent else PageMuted)
        }
        IconButton(onClick = onExport, enabled = ready, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Rounded.FileDownload, "Export PDF to a folder", tint = if (ready) PageAccent else PageMuted)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Rounded.DeleteOutline, "Delete PDF version", tint = PageMuted)
        }
    }
    if (onCompare != null) {
        TextButton(onClick = onCompare,
            modifier = Modifier.align(Alignment.End).padding(end = 12.dp)) {
            Text("Compare with previous PDF", color = PageAccent, fontSize = 11.sp)
        }
    }
    } // Version row column
}

private fun versionDate(capturedAt: Long): String =
    if (capturedAt > 0) DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM, DateFormat.SHORT
    ).format(Date(capturedAt)) else "Unknown capture date"

private fun formatArchiveBytes(value: Long): String =
    if (value < 1024 * 1024) (value / 1024).coerceAtLeast(0L).toString() + " KB"
    else String.format(java.util.Locale.getDefault(), "%.1f MB", value / (1024.0 * 1024))
