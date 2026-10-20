package com.night.browser

import android.app.DownloadManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val LibBg = Color(0xFF0C1017)
private val LibSurface = Color(0xFF171D27)
private val LibBorder = Color(0xFF2B3441)
private val LibText = Color(0xFFF3F5F9)
private val LibMuted = Color(0xFF9BA6B7)
private val LibAccent = Color(0xFFB9BEFF)

private enum class LibSection(val label: String) {
    BOOKMARKS("Bookmarks"), HISTORY("History"), DOWNLOADS("Downloads"), READING("Reading")
}

/** Library surfaces stay independent of the shared MainActivity and GeckoView delegates. */
@Composable
fun NightLibraryScreen(browser: BrowserController, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember(browser) { LibraryRepository(context.applicationContext, browser.store) }
    val downloads = remember { DownloadLibrary.shared(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(LibSection.BOOKMARKS) }
    var query by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<LibraryBookmark?>(null) }
    var addLink by remember { mutableStateOf(false) }
    var export by remember { mutableStateOf<LibraryDownload?>(null) }
    fun notify(message: String?) {
        Toast.makeText(context, message ?: "Operation failed", Toast.LENGTH_LONG).show()
    }
    val chooseExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val row = export
        export = null
        if (uri != null && row != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { downloads.export(row, uri) }
                notify(if (result.isSuccess) "File exported" else result.exceptionOrNull()?.message)
            }
        }
    }
    LaunchedEffect(section) {
        if (section == LibSection.DOWNLOADS) {
            while (true) {
                downloads.refresh()
                delay(1400)
            }
        }
    }
    Column(Modifier.fillMaxSize().background(LibBg)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp,
            top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Library", modifier = Modifier.weight(1f), color = LibText,
                fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            val tab = browser.active
            when (section) {
                LibSection.BOOKMARKS -> TextButton(
                    enabled = LibraryRules.validWebUrl(tab.url),
                    onClick = {
                        edit = repo.bookmarks.firstOrNull { it.url == tab.url }
                            ?: LibraryBookmark("", tab.url, tab.title)
                    }
                ) { Text("Save page") }
                LibSection.READING -> TextButton(
                    enabled = LibraryRules.validWebUrl(tab.url),
                    onClick = { repo.toggleReading(tab.url, tab.title) }
                ) { Text(if (repo.isReading(tab.url)) "Remove page" else "Save page") }
                LibSection.DOWNLOADS -> TextButton(onClick = { addLink = true }) {
                    Text("Add link")
                }
                else -> Unit
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LibSection.entries.forEach { item ->
                val active = item == section
                Box(Modifier.background(if (active) LibAccent else LibSurface,
                    RoundedCornerShape(12.dp)).clickable { section = item; query = "" }
                    .padding(horizontal = 14.dp, vertical = 11.dp)) {
                    Text(item.label, color = if (active) LibBg else LibMuted,
                        fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp).height(46.dp)
            .background(LibSurface, RoundedCornerShape(13.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = LibMuted)
            Spacer(Modifier.width(10.dp))
            LibInput(query, "Search " + section.label.lowercase(), Modifier.weight(1f)) {
                query = it
            }
        }
        HorizontalDivider(color = LibBorder)
        when (section) {
            LibSection.BOOKMARKS -> {
                val marks = repo.bookmarks.filter {
                    LibraryRules.matches(query, it.title, it.url, it.folder)
                }
                if (marks.isEmpty()) LibEmpty("No bookmarks found.")
                else LazyColumn(contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    marks.groupBy { it.folder.ifBlank { "Unsorted" } }.forEach { (folder, rows) ->
                        item(key = "folder:" + folder) {
                            Text(folder, modifier = Modifier.padding(6.dp),
                                color = LibMuted, fontSize = 12.sp)
                        }
                        items(rows, key = { it.url }) { row ->
                            LibRow(row.title, row.url, { onOpen(row.url) }) {
                                IconButton(onClick = { edit = row }) {
                                    Icon(Icons.Rounded.Edit, "Edit", tint = LibMuted)
                                }
                            }
                        }
                    }
                }
            }
            LibSection.HISTORY -> {
                val visits = repo.history.filter {
                    LibraryRules.matches(query, it.title, it.url)
                }
                if (visits.isEmpty()) LibEmpty("No recorded visits.")
                else LazyColumn(contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(visits, key = { it.journeyId + it.id }) { v ->
                        LibRow(v.title.ifBlank { v.url },
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(v.time)) + " · " + v.url,
                            { onOpen(v.url) }) {
                            Icon(Icons.Rounded.History, null, tint = LibMuted)
                        }
                    }
                }
            }
            LibSection.READING -> {
                val rows = repo.readingList.filter {
                    LibraryRules.matches(query, it.title, it.url)
                }
                if (rows.isEmpty()) LibEmpty("Save a page to read later.")
                else LazyColumn(contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(rows, key = { it.url }) { row ->
                        LibRow(row.title, row.url, { onOpen(row.url) }) {
                            IconButton(onClick = { repo.toggleReading(row.url, row.title) }) {
                                Icon(Icons.Rounded.Close, "Remove", tint = LibMuted)
                            }
                        }
                    }
                }
            }
            LibSection.DOWNLOADS -> {
                val rows = downloads.items.filter {
                    LibraryRules.matches(query, it.fileName, it.url, downloads.statusText(it))
                }
                if (rows.isEmpty()) LibEmpty("Your downloaded files appear here.")
                else LazyColumn(contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(rows, key = { it.id }) { row ->
                        Column(Modifier.fillMaxWidth().background(LibSurface,
                            RoundedCornerShape(16.dp)).padding(14.dp)) {
                            Text(row.fileName, color = LibText, fontSize = 14.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(downloads.statusText(row), color = LibMuted, fontSize = 12.sp)
                            if (row.status == DownloadManager.STATUS_RUNNING && row.totalBytes > 0) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { (row.bytes.toFloat() / row.totalBytes).coerceIn(0f, 1f) },
                                    color = LibAccent, trackColor = LibBorder,
                                    modifier = Modifier.fillMaxWidth())
                            }
                            if (row.bytes > 0) Text(formatDownloadBytes(row.bytes) +
                                (if (row.totalBytes > 0) " / " + formatDownloadBytes(row.totalBytes)
                                else ""), color = LibMuted, fontSize = 11.sp)
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically) {
                                if (row.complete) {
                                    TextButton(onClick = {
                                        downloads.open(row).exceptionOrNull()?.let { notify(it.message) }
                                    }) { Text("Open") }
                                    TextButton(onClick = {
                                        export = row
                                        chooseExport.launch(row.fileName)
                                    }) { Text("Export") }
                                } else if (row.status == DownloadManager.STATUS_FAILED && row.localFile.isBlank()) {
                                    TextButton(onClick = {
                                        downloads.retry(row).exceptionOrNull()?.let { notify(it.message) }
                                    }) { Text("Retry") }
                                }
                                TextButton(onClick = {
                                    downloads.remove(row).exceptionOrNull()?.let { notify(it.message) }
                                }) {
                                    Text(if (row.complete) "Delete" else "Cancel", color = LibMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    edit?.let { original ->
        var title by remember(original.url) { mutableStateOf(original.title) }
        var folder by remember(original.url) { mutableStateOf(original.folder) }
        AlertDialog(onDismissRequest = { edit = null },
            title = { Text("Bookmark") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LibInput(title, "Title", Modifier.fillMaxWidth()
                        .background(LibBg, RoundedCornerShape(12.dp)).padding(14.dp)) { title = it }
                    LibInput(folder, "Folder", Modifier.fillMaxWidth()
                        .background(LibBg, RoundedCornerShape(12.dp)).padding(14.dp)) { folder = it }
                    Text(original.url, color = LibMuted, fontSize = 12.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.saveBookmark(original.url, title, folder); edit = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (original.id.isNotBlank())
                        TextButton(onClick = { repo.removeBookmark(original.url); edit = null }) {
                            Text("Delete")
                        }
                    TextButton(onClick = { edit = null }) { Text("Cancel") }
                }
            }, containerColor = LibSurface)
    }
    if (addLink) {
        var url by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { addLink = false }, title = { Text("Download file") },
            text = {
                Column {
                    LibInput(url, "Direct HTTP(S) file link", Modifier.fillMaxWidth()
                        .background(LibBg, RoundedCornerShape(12.dp)).padding(14.dp)) { url = it }
                    Spacer(Modifier.height(8.dp))
                    Text("Android manages file transfers. Webpage URLs are not offline archives.",
                        color = LibMuted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val result = downloads.enqueue(url.trim())
                    if (result.isSuccess) addLink = false
                    else notify(result.exceptionOrNull()?.message)
                }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { addLink = false }) { Text("Cancel") } },
            containerColor = LibSurface)
    }
}

@Composable
private fun LibInput(value: String, placeholder: String, modifier: Modifier, change: (String) -> Unit) {
    BasicTextField(value, onValueChange = change, modifier = modifier, singleLine = true,
        textStyle = TextStyle(color = LibText, fontSize = 14.sp),
        cursorBrush = SolidColor(LibAccent),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = LibMuted, fontSize = 14.sp)
            inner()
        })
}

@Composable
private fun LibEmpty(label: String) {
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Text(label, color = LibMuted, fontSize = 14.sp)
    }
}

@Composable
private fun LibRow(title: String, subtitle: String, open: () -> Unit, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().background(LibSurface, RoundedCornerShape(15.dp))
        .clickable(onClick = open).padding(start = 14.dp, top = 9.dp, bottom = 9.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = LibText, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = LibMuted, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        action()
    }
}

private fun formatDownloadBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) { value /= 1024; index++ }
    return String.format(Locale.US, "%.1f %s", value, units[index])
}
