@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.nami.android

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nami.data.local.NamiDatabase
import app.nami.data.local.StoredCategory
import app.nami.data.local.StoredWatchProgress
import app.nami.domain.AnimeRef
import app.nami.domain.AnimeSearchResult
import app.nami.runtime.NamiSourceRegistry
import app.nami.source.NamiAnimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun NamiMoreScreen(
    downloadedOnly: Boolean,
    onDownloadedOnlyChanged: (Boolean) -> Unit,
    incognitoEnabled: Boolean,
    onIncognitoChanged: (Boolean) -> Unit,
    onDownloads: () -> Unit,
    onCategories: () -> Unit,
    onHistory: () -> Unit,
    onStatistics: () -> Unit,
    onDataStorage: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("More") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                MoreToggleRow(
                    icon = Icons.Outlined.Download,
                    title = "Downloaded only",
                    checked = downloadedOnly,
                    onCheckedChange = onDownloadedOnlyChanged,
                )
            }
            item {
                MoreToggleRow(
                    icon = Icons.Outlined.VisibilityOff,
                    title = "Incognito",
                    subtitle = "Do not save new watch activity while enabled",
                    checked = incognitoEnabled,
                    onCheckedChange = onIncognitoChanged,
                    modifier = Modifier.testTag("incognito-toggle"),
                )
                HorizontalDivider()
            }
            item { MoreDestinationRow(Icons.Outlined.Download, "Downloads", onDownloads) }
            item { MoreDestinationRow(Icons.Outlined.Category, "Categories", onCategories) }
            item { MoreDestinationRow(Icons.Outlined.History, "History", onHistory) }
            item { MoreDestinationRow(Icons.Outlined.Insights, "Statistics", onStatistics) }
            item { MoreDestinationRow(Icons.Outlined.Storage, "Data & storage", onDataStorage) }
            item { MoreDestinationRow(Icons.Outlined.Settings, "Settings", onSettings) }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            item { MoreDestinationRow(Icons.Outlined.Info, "About Nami", onAbout) }
            item { MoreDestinationRow(Icons.Outlined.HelpOutline, "Help", onHelp) }
        }
    }
}

@Composable
private fun MoreToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = if (subtitle.isNotBlank()) {
            { Text(subtitle) }
        } else {
            null
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
        modifier = modifier.clickable { onCheckedChange(!checked) },
    )
}

@Composable
private fun MoreDestinationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
internal fun NamiCategoriesScreen(
    database: NamiDatabase,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    var categories by remember { mutableStateOf<List<StoredCategory>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<StoredCategory?>(null) }
    var creating by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            categories = withContext(Dispatchers.IO) { database.getCategories() }
        }
    }

    LaunchedEffect(revision) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            draft = ""
                            creating = true
                        },
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Create category")
                    }
                },
            )
        },
    ) { padding ->
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No categories yet. Create one to organize your library.",
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(categories, key = { it.id }) { category ->
                    var menuExpanded by remember(category.id) { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(category.name) },
                        trailingContent = {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        Icons.Outlined.MoreVert,
                                        contentDescription = "Category actions",
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Move up") },
                                        onClick = {
                                            menuExpanded = false
                                            scope.launch(Dispatchers.IO) {
                                                database.moveCategory(category.id, -1)
                                                withContext(Dispatchers.Main) {
                                                    revision++
                                                    onChanged()
                                                }
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Move down") },
                                        onClick = {
                                            menuExpanded = false
                                            scope.launch(Dispatchers.IO) {
                                                database.moveCategory(category.id, 1)
                                                withContext(Dispatchers.Main) {
                                                    revision++
                                                    onChanged()
                                                }
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            menuExpanded = false
                                            editing = category
                                            draft = category.name
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            menuExpanded = false
                                            scope.launch(Dispatchers.IO) {
                                                database.deleteCategory(category.id)
                                                withContext(Dispatchers.Main) {
                                                    revision++
                                                    onChanged()
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (creating || editing != null) {
        val editTarget = editing
        AlertDialog(
            onDismissRequest = {
                creating = false
                editing = null
            },
            title = { Text(if (editTarget == null) "Create category" else "Rename category") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            if (editTarget == null) {
                                database.createCategory(draft)
                            } else {
                                database.renameCategory(editTarget.id, draft)
                            }
                            withContext(Dispatchers.Main) {
                                creating = false
                                editing = null
                                revision++
                                onChanged()
                            }
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        creating = false
                        editing = null
                    },
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
internal fun NamiHistoryScreen(
    database: NamiDatabase,
    sourceRegistry: NamiSourceRegistry,
    onBack: () -> Unit,
    onOpenAnime: (NamiAnimeSource, AnimeSearchResult) -> Unit,
) {
    var history by remember { mutableStateOf<List<StoredWatchProgress>?>(null) }
    var sources by remember { mutableStateOf<List<NamiAnimeSource>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sourceRegistry, revision) {
        history = withContext(Dispatchers.IO) { database.getWatchHistory() }
        sources = runCatching { sourceRegistry.installedSources() }.getOrDefault(emptyList())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val historyItems = history
        when {
            historyItems == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Loading history…") }

            historyItems.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("No watch history yet.") }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(
                    items = historyItems,
                    key = { it.sourceId + "|" + it.sourceEpisodeId },
                ) { progress ->
                    ListItem(
                        headlineContent = {
                            Text(
                                progress.animeTitle ?: "Anime",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    buildString {
                                        append(progress.episodeTitle ?: "Episode")
                                        append(" · ")
                                        append(formatHistoryDate(progress.lastWatchedAtEpochMillis))
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (progress.durationMs > 0L) {
                                    LinearProgressIndicator(
                                        progress = {
                                            (progress.positionMs.toFloat() /
                                                progress.durationMs.toFloat()).coerceIn(0f, 1f)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            Icon(Icons.Outlined.History, contentDescription = null)
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        database.deleteWatchProgress(
                                            progress.sourceId,
                                            progress.sourceEpisodeId,
                                        )
                                        withContext(Dispatchers.Main) { revision++ }
                                    }
                                },
                            ) {
                                Icon(Icons.Outlined.Delete, "Remove from history")
                            }
                        },
                        modifier = Modifier.clickable {
                            val animeId = progress.sourceAnimeId ?: return@clickable
                            val source = sources.firstOrNull {
                                it.metadata.id == progress.sourceId
                            } ?: return@clickable
                            onOpenAnime(
                                source,
                                AnimeSearchResult(
                                    ref = AnimeRef(progress.sourceId, animeId),
                                    title = progress.animeTitle ?: "Anime",
                                    sourceState = progress.animeSourceState,
                                ),
                            )
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
internal fun NamiStatisticsScreen(
    database: NamiDatabase,
    onBack: () -> Unit,
) {
    var stats by remember { mutableStateOf<List<Pair<String, String>>?>(null) }

    LaunchedEffect(database) {
        stats = withContext(Dispatchers.IO) {
            val library = database.getLibraryEntries()
            val history = database.getWatchHistory()
            val downloads = database.getDownloads()
            val watched = history.count { it.completed }
            val watchedMillis = history.sumOf {
                if (it.completed && it.durationMs > 0L) it.durationMs else it.positionMs
            }
            val downloaded = downloads.count { it.state == NamiDownloadState.DOWNLOADED.name }
            val sources = (library.map { it.ref.sourceId } + history.map { it.sourceId })
                .distinct()
                .size
            listOf(
                "Anime in library" to library.size.toString(),
                "Episodes watched" to watched.toString(),
                "Watch time" to formatWatchDuration(watchedMillis),
                "Downloaded episodes" to downloaded.toString(),
                "Sources used" to sources.toString(),
            )
        }
    }

    SimpleMoreScreen(title = "Statistics", onBack = onBack) {
        stats?.forEach { (label, value) ->
            ListItem(
                headlineContent = { Text(label) },
                trailingContent = {
                    Text(value, color = MaterialTheme.colorScheme.primary)
                },
            )
            HorizontalDivider()
        } ?: Text("Loading statistics…", modifier = Modifier.padding(24.dp))
    }
}

@Composable
internal fun NamiDataStorageScreen(
    database: NamiDatabase,
    onBack: () -> Unit,
    onDownloads: () -> Unit,
) {
    var summary by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    LaunchedEffect(database) {
        summary = withContext(Dispatchers.IO) {
            val downloads = database.getDownloads()
            downloads.count { it.state == NamiDownloadState.DOWNLOADED.name } to
                downloads.sumOf { it.totalBytes ?: it.bytesDownloaded }
        }
    }
    SimpleMoreScreen(title = "Data & storage", onBack = onBack) {
        ListItem(
            headlineContent = { Text("Download location") },
            supportingContent = { Text("Movies/Nami/Anime") },
            leadingContent = { Icon(Icons.Outlined.Storage, null) },
        )
        HorizontalDivider()
        val value = summary
        ListItem(
            headlineContent = { Text("Downloaded media") },
            supportingContent = {
                Text(
                    if (value == null) "Calculating…"
                    else value.first.toString() + " episodes · " + formatBytes(value.second),
                )
            },
            trailingContent = {
                TextButton(onClick = onDownloads) { Text("Manage") }
            },
        )
    }
}

@Composable
internal fun NamiAboutScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    var selectedNotice by remember { mutableStateOf<BundledNotice?>(null) }

    SimpleMoreScreen(title = "About Nami", onBack = onBack) {
        ListItem(
            headlineContent = { Text("Version") },
            trailingContent = { Text(version.ifBlank { "Unknown" }) },
        )
        ListItem(
            headlineContent = { Text("libVLC license") },
            supportingContent = {
                Text("LGPL-2.1-or-later")
            },
            trailingContent = {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
            },
            modifier = Modifier.clickable {
                selectedNotice = BundledNotice(
                    title = "libVLC license",
                    assetPath = "licenses/LIBVLC-LGPL-2.1.txt",
                )
            },
        )
        ListItem(
            headlineContent = { Text("Third-party notices") },
            trailingContent = {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
            },
            modifier = Modifier.clickable {
                selectedNotice = BundledNotice(
                    title = "Third-party notices",
                    assetPath = "THIRD_PARTY_NOTICES.txt",
                )
            },
        )
    }

    selectedNotice?.let { notice ->
        val noticeText = remember(context, notice) {
            runCatching {
                context.assets.open(notice.assetPath).bufferedReader().use { it.readText() }
            }.getOrElse {
                "This bundled notice could not be opened."
            }
        }
        ModalBottomSheet(
            onDismissRequest = { selectedNotice = null },
            modifier = Modifier.testTag("nami-third-party-notice-sheet"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag("nami-third-party-notice-content"),
            ) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item {
                        Text(
                            text = noticeText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private data class BundledNotice(
    val title: String,
    val assetPath: String,
)

@Composable
internal fun NamiHelpScreen(onBack: () -> Unit) {
    SimpleMoreScreen(title = "Help", onBack = onBack) {
        HelpBlock(
            "Source verification",
            "If a source asks for browser verification, open it, complete the challenge, then return to Nami.",
        )
        HelpBlock(
            "Playback",
            "If playback fails, retry or choose another available stream or quality.",
        )
        HelpBlock(
            "Downloads",
            "Downloads continue in the background and can wait for the network, pause, resume or retry.",
        )
    }
}

@Composable
private fun SimpleMoreScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            content = content,
        )
    }
}

@Composable
private fun HelpBlock(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

private fun formatHistoryDate(epochMillis: Long): String =
    SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        .format(Date(epochMillis))

private fun formatWatchDuration(valueMs: Long): String {
    val minutes = valueMs.coerceAtLeast(0L) / 60_000L
    val hours = minutes / 60L
    val remainder = minutes % 60L
    return if (hours > 0L) "${hours}h ${remainder}m" else "${minutes}m"
}

private fun formatBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L)
    val gb = 1024L * 1024L * 1024L
    val mb = 1024L * 1024L
    return when {
        value >= gb -> String.format(Locale.US, "%.1f GB", value.toDouble() / gb)
        value >= mb -> String.format(Locale.US, "%.1f MB", value.toDouble() / mb)
        else -> String.format(Locale.US, "%.1f KB", value.toDouble() / 1024L)
    }
}
