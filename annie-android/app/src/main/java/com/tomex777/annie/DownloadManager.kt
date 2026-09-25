package com.tomex777.annie

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

internal enum class DownloadMediaKind(val label: String, val filter: String) {
    MANGA("Manga", "Manga"),
    ANIME("Anime", "Anime"),
    MOVIE("Movie", "Movies"),
    TV("TV series", "TV Series"),
    MUSIC("Music", "Music"),
}

internal enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    COMPLETE,
    FAILED,
    PAUSED,
}

internal data class DownloadItem(
    val id: String,
    val canonicalTitleId: String,
    val sourceId: String,
    val sourceName: String,
    val kind: DownloadMediaKind,
    val title: String,
    val artworkUrl: String = "",
    val unitTitle: String,
    val unitNumber: String = "",
    val state: DownloadState,
    val progress: Float = 0f,
    val bytesDone: Long = 0L,
    val bytesTotal: Long = 0L,
    val batchTotal: Int? = null,
    val catalogTotal: Int? = null,
    val localPath: String = "",
    val failureReason: String = "",
    val quality: String = "",
    val sourceUrl: String = "",
    val headersJson: String = "{}",
    val sourceMimeType: String? = null,
    val browserSessionId: String? = null,
)

internal data class ChapterBatch(val first: Int, val last: Int) {
    val count: Int get() = last - first + 1
    val cumulativeTarget: Int get() = last
}

internal fun nextChapterBatch(previousTarget: Int, batchSize: Int = 20, knownTotal: Int? = null): ChapterBatch? {
    if (batchSize <= 0) return null
    val first = previousTarget.coerceAtLeast(0) + 1
    if (knownTotal != null && first > knownTotal) return null
    val last = (first + batchSize - 1).let { if (knownTotal == null) it else minOf(it, knownTotal) }
    return ChapterBatch(first, last)
}

internal fun matchSourceTitle(canonicalAliases: List<String>, sourceAliases: List<String>): Int {
    fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    val canonical = canonicalAliases.map(::normalize).filter(String::isNotBlank).toSet()
    val candidate = sourceAliases.map(::normalize).filter(String::isNotBlank).toSet()
    if (canonical.isEmpty() || candidate.isEmpty()) return 0
    if (canonical.intersect(candidate).isNotEmpty()) return 100
    val best = canonical.maxOfOrNull { left ->
        candidate.maxOfOrNull { right ->
            val a = left.split(' ').toSet()
            val b = right.split(' ').toSet()
            val overlap = a.intersect(b).size.toFloat()
            val denominator = maxOf(a.size, b.size).coerceAtLeast(1)
            ((overlap / denominator) * 80f).toInt()
        } ?: 0
    } ?: 0
    return best
}

internal object DownloadStore {
    private const val PREFS = "annie_downloads_v1"
    private const val KEY_ITEMS = "items"

    fun read(context: Context): List<DownloadItem> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val kind = runCatching { DownloadMediaKind.valueOf(json.optString("kind")) }.getOrNull() ?: continue
                val state = runCatching { DownloadState.valueOf(json.optString("state")) }.getOrNull() ?: continue
                add(
                    DownloadItem(
                        id = json.optString("id"),
                        canonicalTitleId = json.optString("canonicalTitleId"),
                        sourceId = json.optString("sourceId"),
                        sourceName = json.optString("sourceName"),
                        kind = kind,
                        title = json.optString("title"),
                        artworkUrl = json.optString("artworkUrl"),
                        unitTitle = json.optString("unitTitle"),
                        unitNumber = json.optString("unitNumber"),
                        state = state,
                        progress = json.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
                        bytesDone = json.optLong("bytesDone"),
                        bytesTotal = json.optLong("bytesTotal"),
                        batchTotal = json.optInt("batchTotal").takeIf { it > 0 },
                        catalogTotal = json.optInt("catalogTotal").takeIf { it > 0 },
                        localPath = json.optString("localPath"),
                        failureReason = json.optString("failureReason"),
                        quality = json.optString("quality"),
                        sourceUrl = json.optString("sourceUrl"),
                        headersJson = json.optString("headersJson", "{}"),
                        sourceMimeType = json.optString("sourceMimeType").takeIf { it.isNotBlank() },
                        browserSessionId = json.optString("browserSessionId").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun write(context: Context, items: List<DownloadItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("canonicalTitleId", item.canonicalTitleId)
                    .put("sourceId", item.sourceId)
                    .put("sourceName", item.sourceName)
                    .put("kind", item.kind.name)
                    .put("title", item.title)
                    .put("artworkUrl", item.artworkUrl)
                    .put("unitTitle", item.unitTitle)
                    .put("unitNumber", item.unitNumber)
                    .put("state", item.state.name)
                    .put("progress", item.progress.toDouble())
                    .put("bytesDone", item.bytesDone)
                    .put("bytesTotal", item.bytesTotal)
                    .put("batchTotal", item.batchTotal ?: 0)
                    .put("catalogTotal", item.catalogTotal ?: 0)
                    .put("localPath", item.localPath)
                    .put("failureReason", item.failureReason)
                    .put("quality", item.quality)
                    .put("sourceUrl", item.sourceUrl)
                    .put("headersJson", item.headersJson)
                    .put("sourceMimeType", item.sourceMimeType ?: "")
                    .put("browserSessionId", item.browserSessionId ?: "")
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }
}

private val DownloadsBg = Color(0xFF07111E)
private val DownloadsPanel = Color(0xFF102238)
private val DownloadsRow = Color(0xFF122A43)
private val DownloadsBlue = Color(0xFF168EEA)
private val DownloadsCyan = Color(0xFF42B9F5)
private val DownloadsText = Color(0xFFEEF5FF)
private val DownloadsMuted = Color(0xFF9CB2CC)
private val DownloadsGreen = Color(0xFF54D6AE)
private val DownloadsRed = Color(0xFFFF737E)

private data class DownloadGroup(
    val key: String,
    val kind: DownloadMediaKind,
    val title: String,
    val sourceName: String,
    val artworkUrl: String,
    val items: List<DownloadItem>,
)

@Composable
internal fun DownloadsManagerContent(
    items: List<DownloadItem>,
    onRemove: (DownloadItem) -> Unit,
    onStateChange: (DownloadItem, DownloadState) -> Unit,
    onPlay: (DownloadItem) -> Unit = {},
    initialMediaFilter: String = "All",
    modifier: Modifier = Modifier,
) {
    val filters = listOf("All", "Manga", "Anime", "Movies", "TV Series", "Music")
    val statusFilters = listOf("All", "Downloading", "Downloaded", "Paused", "Failed")
    var selectedFilter by remember(initialMediaFilter) { mutableStateOf(initialMediaFilter) }
    var selectedStatus by remember { mutableStateOf("All") }
    val visibleItems = items
        .filter { selectedFilter == "All" || it.kind.filter == selectedFilter }
        .filter { item ->
            when (selectedStatus) {
                "Downloading" -> item.state == DownloadState.QUEUED || item.state == DownloadState.DOWNLOADING
                "Downloaded" -> item.state == DownloadState.COMPLETE
                "Paused" -> item.state == DownloadState.PAUSED
                "Failed" -> item.state == DownloadState.FAILED
                else -> true
            }
        }
    val groups = visibleItems
        .groupBy { "${it.canonicalTitleId}|${it.sourceId}" }
        .map { (key, groupItems) ->
            val first = groupItems.first()
            DownloadGroup(
                key = key,
                kind = first.kind,
                title = first.title,
                sourceName = first.sourceName,
                artworkUrl = first.artworkUrl,
                items = groupItems.sortedWith(compareBy<DownloadItem> { stateOrder(it.state) }.thenBy { it.unitNumber }),
            )
        }
        .sortedWith(compareBy<DownloadGroup> { if (it.items.any { item -> item.state == DownloadState.DOWNLOADING || item.state == DownloadState.QUEUED }) 0 else 1 }.thenBy { it.title.lowercase() })

    val expanded = remember { mutableStateListOf<String>() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.94f)
            .background(DownloadsBg)
            .padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Downloads", color = DownloadsText, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filters.forEach { filter ->
                    val selected = selectedFilter == filter
                    Surface(
                        color = if (selected) DownloadsBlue else DownloadsRow,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, if (selected) DownloadsBlue else Color(0xFF294562)),
                        modifier = Modifier
                            .testTag("download_filter_${filter.replace(" ", "_")}")
                            .clickable { selectedFilter = filter },
                    ) {
                        Text(filter, color = DownloadsText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                statusFilters.forEach { filter ->
                    val selected = selectedStatus == filter
                    Surface(
                        color = if (selected) Color(0xFF24415F) else DownloadsBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (selected) DownloadsCyan else Color(0xFF294562)),
                        modifier = Modifier.testTag("download_status_${filter.replace(" ", "_")}")
                            .clickable { selectedStatus = filter },
                    ) {
                        Text(filter, color = if (selected) DownloadsCyan else DownloadsMuted, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
            }
        }

        if (groups.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 56.dp, start = 28.dp, end = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (items.isEmpty()) "No downloads yet" else "No ${selectedStatus.lowercase()} downloads", color = DownloadsText, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("download_groups"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(groups, key = { it.key }) { group ->
                    val open = group.key in expanded
                    DownloadGroupCard(
                        group = group,
                        expanded = open,
                        onToggle = {
                            if (open) expanded.remove(group.key) else expanded.add(group.key)
                        },
                        onRemove = onRemove,
                        onStateChange = onStateChange,
                        onPlay = onPlay,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadGroupCard(
    group: DownloadGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRemove: (DownloadItem) -> Unit,
    onStateChange: (DownloadItem, DownloadState) -> Unit,
    onPlay: (DownloadItem) -> Unit,
) {
    val completed = group.items.count { it.state == DownloadState.COMPLETE }
    val active = group.items.firstOrNull { it.state == DownloadState.DOWNLOADING }
    val queued = group.items.count { it.state == DownloadState.QUEUED }
    val failed = group.items.count { it.state == DownloadState.FAILED }
    val paused = group.items.count { it.state == DownloadState.PAUSED }
    val total = group.items.firstNotNullOfOrNull { it.catalogTotal }
    val batchTotal = group.items.firstNotNullOfOrNull { it.batchTotal }
    val progressText = when {
        active != null -> "${completed} of ${batchTotal ?: group.items.size} ${group.kind.unitLabel} · Downloading"
        queued > 0 -> "${completed} of ${batchTotal ?: group.items.size} ${group.kind.unitLabel} · $queued queued"
        paused > 0 -> "${completed} of ${batchTotal ?: group.items.size} ${group.kind.unitLabel} · $paused paused"
        completed > 0 && total != null -> "$completed of $total ${group.kind.unitLabel} available offline"
        completed > 0 -> "$completed ${group.kind.unitLabel} available offline"
        failed > 0 -> "$failed ${group.kind.unitLabel} failed"
        else -> "${group.items.size} ${group.kind.unitLabel}"
    }
    Surface(
        color = DownloadsPanel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF294562)),
        modifier = Modifier.fillMaxWidth().testTag("download_group_${group.kind.name}").clickable(onClick = onToggle),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Surface(color = Color(0xFF1A3654), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(54.dp)) {
                    BoxPlaceholder(group.kind)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(group.title, color = DownloadsText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${group.kind.label} · ${group.sourceName}", color = DownloadsMuted, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(progressText, color = if (active != null || queued > 0) DownloadsCyan else DownloadsMuted,
                        fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(if (expanded) "⌃" else "⌄", color = DownloadsMuted, fontSize = 20.sp,
                    modifier = Modifier.testTag("download_group_toggle_${group.kind.name}"))
            }
            if (active != null) {
                LinearProgressIndicator(
                    progress = { active.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = DownloadsBlue,
                    trackColor = Color(0xFF263D59),
                )
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.items.forEach { item ->
                        DownloadUnitRow(item = item, onRemove = { onRemove(item) }, onStateChange = { state -> onStateChange(item, state) }, onPlay = { onPlay(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadUnitRow(item: DownloadItem, onRemove: () -> Unit, onStateChange: (DownloadState) -> Unit, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DownloadsRow).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            when (item.state) {
                DownloadState.COMPLETE -> "✓"
                DownloadState.DOWNLOADING -> "●"
                DownloadState.QUEUED -> "◷"
                DownloadState.PAUSED -> "Ⅱ"
                DownloadState.FAILED -> "!"
            },
            color = when (item.state) {
                DownloadState.COMPLETE -> DownloadsGreen
                DownloadState.DOWNLOADING -> DownloadsCyan
                DownloadState.FAILED -> DownloadsRed
                else -> DownloadsMuted
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                listOf(item.unitNumber.takeIf(String::isNotBlank), item.unitTitle).filterNotNull().joinToString(" · "),
                color = DownloadsText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (item.state) {
                DownloadState.DOWNLOADING -> {
                    Text("Downloading · ${(item.progress.coerceIn(0f, 1f) * 100).toInt()}%", color = DownloadsCyan, fontSize = 11.sp)
                    LinearProgressIndicator(
                        progress = { item.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                        color = DownloadsBlue,
                        trackColor = Color(0xFF263D59),
                    )
                }
                DownloadState.QUEUED -> Text("Queued", color = DownloadsMuted, fontSize = 11.sp)
                DownloadState.PAUSED -> Text("Paused", color = DownloadsMuted, fontSize = 11.sp)
                DownloadState.FAILED -> Text(
                    "Failed · ${item.failureReason.ifBlank { "Retry available" }}",
                    color = DownloadsRed, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                DownloadState.COMPLETE -> {
                    if (item.bytesTotal > 0) Text(formatDownloadSize(item.bytesTotal), color = DownloadsMuted, fontSize = 10.sp)
                    if (item.quality.isNotBlank()) Text(item.quality, color = DownloadsMuted, fontSize = 10.sp)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
            when (item.state) {
                DownloadState.DOWNLOADING -> {
                    DownloadAction("Pause") { onStateChange(DownloadState.PAUSED) }
                    DownloadAction("Cancel", destructive = true, onClick = onRemove)
                }
                DownloadState.QUEUED -> DownloadAction("Cancel", destructive = true, onClick = onRemove)
                DownloadState.PAUSED -> {
                    DownloadAction("Resume") { onStateChange(DownloadState.QUEUED) }
                    DownloadAction("Cancel", destructive = true, onClick = onRemove)
                }
                DownloadState.FAILED -> {
                    DownloadAction("Retry") { onStateChange(DownloadState.QUEUED) }
                    DownloadAction("Remove", destructive = true, onClick = onRemove)
                }
                DownloadState.COMPLETE -> {
                    if (item.localPath.isNotBlank() && item.kind in setOf(DownloadMediaKind.ANIME, DownloadMediaKind.MOVIE, DownloadMediaKind.TV)) {
                        DownloadAction("Play", onClick = onPlay)
                    }
                    DownloadAction("Delete", destructive = true, onClick = onRemove)
                }
            }
        }
    }
}

@Composable
private fun DownloadAction(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.testTag("download_action_${label.lowercase()}").clickable(onClick = onClick).padding(horizontal = 5.dp, vertical = 2.dp),
        color = if (destructive) DownloadsRed else DownloadsCyan,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun BoxPlaceholder(kind: DownloadMediaKind) {
    androidx.compose.foundation.layout.Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
        Text(
            when (kind) {
                DownloadMediaKind.MANGA -> "▤"
                DownloadMediaKind.ANIME, DownloadMediaKind.TV, DownloadMediaKind.MOVIE -> "▶"
                DownloadMediaKind.MUSIC -> "♫"
            },
            color = DownloadsCyan,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private val DownloadMediaKind.unitLabel: String
    get() = when (this) {
        DownloadMediaKind.MANGA -> "chapters"
        DownloadMediaKind.ANIME, DownloadMediaKind.TV -> "episodes"
        DownloadMediaKind.MOVIE -> "files"
        DownloadMediaKind.MUSIC -> "tracks"
    }

private fun stateOrder(state: DownloadState): Int = when (state) {
    DownloadState.DOWNLOADING -> 0
    DownloadState.QUEUED -> 1
    DownloadState.PAUSED -> 2
    DownloadState.FAILED -> 3
    DownloadState.COMPLETE -> 4
}

private fun formatDownloadSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}
