@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.model.ActivitySignal
import com.night.sora.model.ContentType
import com.night.sora.model.DownloadEntry
import com.night.sora.model.DownloadStatus
import com.night.sora.model.LibraryEntry
import com.night.sora.model.ListeningSignal
import com.night.sora.ui.theme.*
import java.io.File
import kotlin.math.max

@Composable
fun MoreScreen(
    modifier: Modifier = Modifier,
    extensionCount: Int,
    activeDownloadCount: Int,
    completedDownloadCount: Int,
    onExtensions: () -> Unit,
    onDownloads: () -> Unit,
    onStatistics: () -> Unit,
    onDataStorage: () -> Unit,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 14.dp)) {
                Text("More", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Sora settings and system tools", color = SoraMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }

        item { MoreSectionLabel("LIBRARY & ACTIVITY") }
        item {
            MoreRow(
                title = "Downloads",
                subtitle = when {
                    activeDownloadCount > 0 -> "$activeDownloadCount active · $completedDownloadCount completed"
                    completedDownloadCount > 0 -> "$completedDownloadCount completed"
                    else -> "Offline media and download queue"
                },
                icon = Icons.Rounded.Download,
                onClick = onDownloads,
            )
        }
        item { MoreDivider() }
        item { MoreRow("Statistics", "Anime, Manga, Movies, TV and Music", Icons.Rounded.BarChart, onClick = onStatistics) }
        item { MoreDivider() }
        item { MoreRow("Data & storage", "Cache, offline files and local data", Icons.Rounded.Storage, onClick = onDataStorage) }

        item { MoreSectionLabel("SOURCES & PLAYBACK") }
        item { MoreRow("Extensions", "$extensionCount compatible installed", Icons.Rounded.Extension, onClick = onExtensions) }
        item { MoreDivider() }
        item { MoreRow("Player & reader", "Playback, subtitles and reading behavior", Icons.Rounded.Tune) }

        item { MoreSectionLabel("SORA") }
        item { MoreRow("AI & models", "Providers, voice and generated media", Icons.Rounded.AutoAwesome) }
        item { MoreDivider() }
        item { MoreRow("Appearance", "Theme, density and visual preferences", Icons.Rounded.Palette) }
        item { MoreDivider() }
        item { MoreRow("About & help", "Version, extension API and support information", Icons.Rounded.Info) }
    }
}

@Composable
private fun MoreSectionLabel(text: String) {
    Text(
        text,
        color = SoraAccent,
        fontSize = 9.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 7.dp),
    )
}

@Composable
private fun MoreDivider() {
    HorizontalDivider(color = Color.White.copy(alpha = .055f), modifier = Modifier.padding(start = 64.dp))
}

@Composable
private fun MoreRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = SoraMuted, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 20.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = SoraMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = SoraFaint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun DownloadsScreen(
    downloads: List<DownloadEntry>,
    onBack: () -> Unit,
    onStatus: (String, DownloadStatus) -> Unit,
    onRemove: (String) -> Unit,
    onClearCompleted: () -> Unit,
) {
    val active = downloads.filter { it.status != DownloadStatus.COMPLETED }.sortedByDescending { it.updatedAt }
    val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }.groupBy { it.title }.toList().sortedBy { it.first.lowercase() }

    Scaffold(
        containerColor = SoraBg,
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
                actions = {
                    if (completed.isNotEmpty()) TextButton(onClick = onClearCompleted) { Text("Clear") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SoraBg),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { DownloadSectionTitle("Active transfers", if (active.isEmpty()) "Nothing downloading right now" else "${active.size} active") }
            if (active.isEmpty()) {
                item { EmptyDownloadMessage("Downloads you start from a title will appear here while they are queued or transferring.") }
            } else {
                items(active, key = { it.id }) { entry ->
                    ActiveDownloadRow(entry, onStatus, onRemove)
                    MoreDivider()
                }
            }

            item { DownloadSectionTitle("Downloaded", if (completed.isEmpty()) "No completed files" else "${completed.sumOf { it.second.size }} files") }
            if (completed.isEmpty()) {
                item { EmptyDownloadMessage("Completed downloads stay grouped by title here for offline access.") }
            } else {
                completed.forEach { (title, entries) ->
                    item(key = "group-$title") { CompletedDownloadGroup(title, entries, onRemove) }
                }
            }
        }
    }
}

@Composable
private fun DownloadSectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 8.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = SoraMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun EmptyDownloadMessage(text: String) {
    Text(text, color = SoraMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp))
}

@Composable
private fun ActiveDownloadRow(entry: DownloadEntry, onStatus: (String, DownloadStatus) -> Unit, onRemove: (String) -> Unit) {
    val progress = if (entry.totalBytes > 0L) (entry.bytesDownloaded.toFloat() / entry.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 11.dp, end = 8.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        DownloadTypeIcon(entry.contentType)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(entry.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.itemLabel.ifBlank { entry.contentType.label }, color = SoraMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (entry.totalBytes > 0L) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp), color = SoraAccent, trackColor = SoraSurfaceHigh)
            }
            Text(
                when (entry.status) {
                    DownloadStatus.QUEUED -> "Queued"
                    DownloadStatus.DOWNLOADING -> if (entry.totalBytes > 0L) "${formatBytes(entry.bytesDownloaded)} of ${formatBytes(entry.totalBytes)}" else "Downloading"
                    DownloadStatus.PAUSED -> "Paused"
                    DownloadStatus.FAILED -> "Failed"
                    DownloadStatus.COMPLETED -> "Completed"
                },
                color = if (entry.status == DownloadStatus.FAILED) SoraDanger else SoraMuted,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        when (entry.status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> IconButton(onClick = { onStatus(entry.id, DownloadStatus.PAUSED) }) { Icon(Icons.Rounded.Pause, "Pause") }
            DownloadStatus.PAUSED, DownloadStatus.FAILED -> IconButton(onClick = { onStatus(entry.id, DownloadStatus.QUEUED) }) { Icon(Icons.Rounded.PlayArrow, "Resume") }
            DownloadStatus.COMPLETED -> Unit
        }
        IconButton(onClick = { onRemove(entry.id) }) { Icon(Icons.Rounded.Close, "Remove", tint = SoraMuted) }
    }
}

@Composable
private fun CompletedDownloadGroup(title: String, entries: List<DownloadEntry>, onRemove: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("${entries.size} file${if (entries.size == 1) "" else "s"} · ${formatBytes(entries.sumOf { max(it.totalBytes, it.bytesDownloaded) })}", color = SoraMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp, bottom = 5.dp))
        entries.forEach { entry ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.OfflinePin, null, tint = SoraAccent, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(entry.itemLabel.ifBlank { entry.contentType.label }, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (entry.sourceName.isNotBlank()) Text(entry.sourceName, color = SoraMuted, fontSize = 9.sp)
                }
                IconButton(onClick = { onRemove(entry.id) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.DeleteOutline, "Delete download", tint = SoraMuted, modifier = Modifier.size(18.dp)) }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = .055f))
    }
}

@Composable
private fun DownloadTypeIcon(type: ContentType) {
    val icon = when (type) {
        ContentType.ANIME, ContentType.TV, ContentType.MOVIE -> Icons.Rounded.Movie
        ContentType.MANGA -> Icons.Rounded.MenuBook
        ContentType.MUSIC -> Icons.Rounded.MusicNote
        ContentType.MEME -> Icons.Rounded.Image
    }
    Box(Modifier.size(38.dp).background(SoraSurfaceHigh, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = SoraMuted, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun StatisticsScreen(
    library: List<LibraryEntry>,
    activity: List<ActivitySignal>,
    listeningSignals: List<ListeningSignal>,
    onBack: () -> Unit,
) {
    val tracked = listOf(ContentType.ANIME, ContentType.MANGA, ContentType.MOVIE, ContentType.TV, ContentType.MUSIC)
    val activityTotal = activity.count { it.contentType in tracked }
    val savedTotal = library.count { it.contentType in tracked }
    val musicPlays = listeningSignals.sumOf { it.plays }
    val maxActivity = tracked.maxOfOrNull { type -> activity.count { it.contentType == type } }?.coerceAtLeast(1) ?: 1

    Scaffold(
        containerColor = SoraBg,
        topBar = { TopAppBar(title = { Text("Statistics") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = SoraBg)) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatNumber("Saved", savedTotal.toString(), Modifier.weight(1f))
                    StatNumber("Activity", activityTotal.toString(), Modifier.weight(1f))
                    StatNumber("Music plays", musicPlays.toString(), Modifier.weight(1f))
                }
            }
            item { MoreSectionLabel("BY MEDIA TYPE") }
            tracked.forEach { type ->
                item(key = type.name) {
                    val saved = library.count { it.contentType == type }
                    val events = activity.count { it.contentType == type }
                    MediaStatRow(type, saved, events, events.toFloat() / maxActivity.toFloat())
                }
            }
            item { MoreSectionLabel("RECENT ACTIVITY") }
            if (activity.isEmpty()) {
                item { EmptyDownloadMessage("Sora starts building these statistics as you open and play media. Nothing is fabricated from sample data.") }
            } else {
                items(activity.take(12), key = { it.id }) { signal ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        DownloadTypeIcon(signal.contentType)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(signal.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${signal.contentType.label} · ${signal.action}", color = SoraMuted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatNumber(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(SoraSurface, RoundedCornerShape(12.dp)).padding(13.dp)) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, color = SoraMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun MediaStatRow(type: ContentType, saved: Int, events: Int, fraction: Float) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(type.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("$saved saved · $events opened", color = SoraMuted, fontSize = 10.sp)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).padding(top = 2.dp).background(SoraSurfaceHigh, RoundedCornerShape(99.dp))) {
            if (events > 0) Box(Modifier.fillMaxWidth(fraction.coerceIn(.05f, 1f)).fillMaxHeight().background(SoraAccent, RoundedCornerShape(99.dp)))
        }
    }
}

@Composable
fun DataStorageScreen(
    downloads: List<DownloadEntry>,
    onClearCatalogCache: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var refreshEpoch by remember { mutableIntStateOf(0) }
    val snapshot = remember(refreshEpoch, downloads) { storageSnapshot(context, downloads) }

    Scaffold(
        containerColor = SoraBg,
        topBar = { TopAppBar(title = { Text("Data & storage") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = SoraBg)) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { MoreSectionLabel("ON THIS DEVICE") }
            item { StorageRow("Sora app data", "Private app files, preferences and caches", snapshot.appDataBytes, Icons.Rounded.PhoneAndroid) }
            item { MoreDivider() }
            item { StorageRow("Cache", "Temporary catalog and image/network cache", snapshot.cacheBytes, Icons.Rounded.Cached) }
            item { MoreDivider() }
            item { StorageRow("Downloaded media", "Completed offline files tracked by Sora", snapshot.downloadBytes, Icons.Rounded.DownloadDone) }

            item { MoreSectionLabel("MANAGE") }
            item {
                Row(Modifier.fillMaxWidth().clickable {
                    onClearCatalogCache()
                    refreshEpoch++
                }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DeleteSweep, null, tint = SoraMuted)
                    Column(Modifier.weight(1f).padding(start = 20.dp)) {
                        Text("Clear catalog cache", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Removes cached browse/search metadata. Your library and downloads stay intact.", color = SoraMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            item { MoreDivider() }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Security, null, tint = SoraMuted)
                    Column(Modifier.weight(1f).padding(start = 20.dp)) {
                        Text("Private by default", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Library, statistics and queue state stay in Sora Core. Source extensions do not own this data.", color = SoraMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

private data class StorageSnapshot(val appDataBytes: Long, val cacheBytes: Long, val downloadBytes: Long)

private fun storageSnapshot(context: Context, downloads: List<DownloadEntry>): StorageSnapshot {
    val dataRoot = runCatching { File(context.applicationInfo.dataDir) }.getOrNull()
    val cacheBytes = safeSize(context.cacheDir)
    val appData = dataRoot?.let(::safeSize) ?: cacheBytes
    val trackedDownloads = downloads.filter { it.status == DownloadStatus.COMPLETED }.sumOf { entry ->
        val fileBytes = entry.filePath?.let { path -> runCatching { File(path).takeIf(File::isFile)?.length() ?: 0L }.getOrDefault(0L) } ?: 0L
        max(fileBytes, max(entry.totalBytes, entry.bytesDownloaded))
    }
    return StorageSnapshot(appData, cacheBytes, trackedDownloads)
}

private fun safeSize(root: File): Long = runCatching {
    if (!root.exists()) 0L else if (root.isFile) root.length() else root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.1f MB", mb)
    return String.format("%.1f GB", mb / 1024.0)
}

@Composable
private fun StorageRow(title: String, subtitle: String, bytes: Long, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = SoraMuted, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 20.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = SoraMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text(formatBytes(bytes), color = SoraMuted, fontSize = 11.sp)
    }
}
