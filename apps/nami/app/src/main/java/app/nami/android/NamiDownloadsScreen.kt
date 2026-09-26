package app.nami.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class DownloadSort {
    DEFAULT,
    EPISODE_ASC,
    EPISODE_DESC,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NamiDownloadsScreen(
    downloadManager: NamiDownloadManager,
    onBack: () -> Unit,
    onPlayDownloaded: (NamiDownloadStatus) -> Unit,
) {
    val statuses by downloadManager.statuses.collectAsState()
    val globallyPaused by downloadManager.globalPaused.collectAsState()
    var sort by remember { mutableStateOf(DownloadSort.DEFAULT) }
    var sortExpanded by remember { mutableStateOf(false) }
    var actionsExpanded by remember { mutableStateOf(false) }

    val downloads = remember(statuses, sort) {
        val base = statuses.values.toList()
        when (sort) {
            DownloadSort.DEFAULT -> base.sortedWith(
                compareBy<NamiDownloadStatus>(
                    { it.animeTitle.lowercase() },
                    { episodeNumber(it) },
                    { it.episodeTitle.lowercase() },
                ),
            )
            DownloadSort.EPISODE_ASC -> base.sortedWith(
                compareBy<NamiDownloadStatus>(
                    { it.animeTitle.lowercase() },
                    { episodeNumber(it) },
                    { it.episodeTitle.lowercase() },
                ),
            )
            DownloadSort.EPISODE_DESC -> base.sortedWith(
                compareBy<NamiDownloadStatus>(
                    { it.animeTitle.lowercase() },
                    { -episodeNumber(it) },
                    { it.episodeTitle.lowercase() },
                ),
            )
        }
    }

    val groupedDownloads = remember(downloads) {
        downloads.groupBy { status ->
            status.sourceId + "\u0000" + status.sourceAnimeId
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Downloads",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (downloads.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.padding(start = 6.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                            ) {
                                Text(
                                    text = downloads.size.toString(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (downloads.isNotEmpty()) {
                        val hasQueueWork = downloads.any {
                            it.state == NamiDownloadState.QUEUED ||
                                it.state == NamiDownloadState.DOWNLOADING ||
                                it.state == NamiDownloadState.WAITING_FOR_NETWORK ||
                                (
                                    it.state == NamiDownloadState.PAUSED &&
                                        it.pauseReason == NamiPauseReason.GLOBAL
                                )
                        }
                        if (hasQueueWork) {
                            IconButton(
                                onClick = {
                                    if (globallyPaused) {
                                        downloadManager.resumeAll()
                                    } else {
                                        downloadManager.pauseAll()
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = if (globallyPaused) {
                                        Icons.Outlined.PlayArrow
                                    } else {
                                        Icons.Outlined.Pause
                                    },
                                    contentDescription = if (globallyPaused) {
                                        "Resume all downloads"
                                    } else {
                                        "Pause all downloads"
                                    },
                                )
                            }
                        }

                        Box {
                            IconButton(onClick = { sortExpanded = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                                    contentDescription = "Sort downloads",
                                )
                            }
                            DropdownMenu(
                                expanded = sortExpanded,
                                onDismissRequest = { sortExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Episode number · Ascending") },
                                    onClick = {
                                        sort = DownloadSort.EPISODE_ASC
                                        sortExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Episode number · Descending") },
                                    onClick = {
                                        sort = DownloadSort.EPISODE_DESC
                                        sortExpanded = false
                                    },
                                )
                            }
                        }

                        Box {
                            IconButton(onClick = { actionsExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "Download actions",
                                )
                            }
                            DropdownMenu(
                                expanded = actionsExpanded,
                                onDismissRequest = { actionsExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Cancel all") },
                                    enabled = downloads.any {
                                        it.state == NamiDownloadState.QUEUED ||
                                            it.state == NamiDownloadState.DOWNLOADING ||
                                            it.state == NamiDownloadState.WAITING_FOR_NETWORK ||
                                            it.state == NamiDownloadState.PAUSED
                                    },
                                    onClick = {
                                        downloads
                                            .filter {
                                                it.state == NamiDownloadState.QUEUED ||
                                                    it.state == NamiDownloadState.DOWNLOADING ||
                                                    it.state == NamiDownloadState.WAITING_FOR_NETWORK ||
                                                    it.state == NamiDownloadState.PAUSED
                                            }
                                            .forEach(downloadManager::cancel)
                                        actionsExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No downloads",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                groupedDownloads.forEach { (groupKey, group) ->
                    val first = group.first()
                    item(key = "header:$groupKey") {
                        DownloadGroupHeader(
                            title = first.animeTitle,
                            count = group.size,
                        )
                    }

                    items(
                        items = group,
                        key = { downloadManager.key(it.sourceId, it.sourceEpisodeId) },
                    ) { status ->
                        AniyomiStyleDownloadRow(
                            status = status,
                            onPlayDownloaded = onPlayDownloaded,
                            onPause = { downloadManager.pause(status) },
                            onResume = { downloadManager.resume(status) },
                            onCancel = { downloadManager.cancel(status) },
                            onRetry = { downloadManager.retry(status) },
                            onRemove = { downloadManager.remove(status) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadGroupHeader(
    title: String,
    count: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$title ($count)",
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AniyomiStyleDownloadRow(
    status: NamiDownloadStatus,
    onPlayDownloaded: (NamiDownloadStatus) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember(status.sourceId, status.sourceEpisodeId) {
        mutableStateOf(false)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = status.state == NamiDownloadState.DOWNLOADED,
                onClick = { onPlayDownloaded(status) },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = status.animeTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = downloadProgressLabel(status),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .widthIn(min = 36.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = if (status.state == NamiDownloadState.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }

            Text(
                text = status.episodeTitle,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            when {
                status.state == NamiDownloadState.DOWNLOADING && status.progress <= 0 -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                else -> {
                    LinearProgressIndicator(
                        progress = { progressFraction(status) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                    )
                }
            }
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.semantics {
                    contentDescription = "Download menu for ${status.episodeTitle}"
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = null,
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                when (status.state) {
                    NamiDownloadState.QUEUED,
                    NamiDownloadState.DOWNLOADING,
                    NamiDownloadState.WAITING_FOR_NETWORK,
                    -> {
                        DropdownMenuItem(
                            text = { Text("Pause") },
                            onClick = {
                                menuExpanded = false
                                onPause()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Cancel") },
                            onClick = {
                                menuExpanded = false
                                onCancel()
                            },
                        )
                    }

                    NamiDownloadState.PAUSED -> {
                        DropdownMenuItem(
                            text = { Text("Resume") },
                            onClick = {
                                menuExpanded = false
                                onResume()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            },
                        )
                    }

                    NamiDownloadState.ERROR -> {
                        DropdownMenuItem(
                            text = { Text("Retry") },
                            onClick = {
                                menuExpanded = false
                                onRetry()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            },
                        )
                    }

                    NamiDownloadState.DOWNLOADED -> {
                        DropdownMenuItem(
                            text = { Text("Play") },
                            onClick = {
                                menuExpanded = false
                                onPlayDownloaded(status)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun progressFraction(status: NamiDownloadStatus): Float = when (status.state) {
    NamiDownloadState.QUEUED -> 0f
    NamiDownloadState.DOWNLOADING,
    NamiDownloadState.PAUSED,
    NamiDownloadState.WAITING_FOR_NETWORK,
    -> status.progress.coerceIn(0, 100) / 100f
    NamiDownloadState.DOWNLOADED -> 1f
    NamiDownloadState.ERROR -> status.progress.coerceIn(0, 100) / 100f
}

private fun downloadProgressLabel(status: NamiDownloadStatus): String = when (status.state) {
    NamiDownloadState.QUEUED -> "Queued"
    NamiDownloadState.DOWNLOADING ->
        if (status.progress <= 0) "Downloading" else "${status.progress.coerceIn(0, 100)}%"
    NamiDownloadState.PAUSED -> "Paused"
    NamiDownloadState.WAITING_FOR_NETWORK -> {
        if (status.errorMessage?.startsWith("Retrying", ignoreCase = true) == true) {
            "Retrying…"
        } else {
            "Waiting"
        }
    }
    NamiDownloadState.DOWNLOADED -> "100%"
    NamiDownloadState.ERROR -> "Error"
}

private fun episodeNumber(status: NamiDownloadStatus): Double {
    val match = Regex(
        """(?i)\b(?:episode|ep)\s*([0-9]+(?:\.[0-9]+)?)""",
    ).find(status.episodeTitle)
    return match?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: Double.MAX_VALUE
}
