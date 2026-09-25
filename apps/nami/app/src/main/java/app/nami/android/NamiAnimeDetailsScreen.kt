@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package app.nami.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import app.nami.data.local.NamiDatabase
import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Nami's phone details presentation intentionally follows the Aniyomi anime details layout:
 * backdrop + compact cover/info header, action row, expandable summary/tags, then episodes.
 */
@Composable
fun NamiAnimeDetailsScreen(
    database: NamiDatabase,
    source: app.nami.source.NamiAnimeSource,
    item: app.nami.domain.AnimeSearchResult,
    onBack: () -> Unit,
    onLibraryChanged: () -> Unit,
    onOpenWeb: (String, String) -> Unit,
    downloadManager: NamiDownloadManager,
) {
    var details by remember { mutableStateOf<AnimeDetails?>(null) }
    var episodes by remember { mutableStateOf<List<AnimeEpisode>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var inLibrary by remember { mutableStateOf(false) }
    var resolvingEpisodeId by remember { mutableStateOf<String?>(null) }
    var pendingLegacyDownload by remember {
        mutableStateOf<Pair<AnimeDetails, AnimeEpisode>?>(null)
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val downloadStatuses by downloadManager.statuses.collectAsState()
    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingLegacyDownload
        pendingLegacyDownload = null

        if (granted && pending != null) {
            downloadManager.enqueue(source, pending.first, pending.second)
        } else if (!granted) {
            Toast.makeText(
                context,
                "Storage permission is required to save downloads on Android 8 and 9.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val listState = rememberLazyListState()
    val showToolbarTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 220
        }
    }

    LaunchedEffect(item.ref, item.sourceState) {
        loading = true
        error = null
        runCatching {
            val loadedDetails = source.details(item.ref, item.sourceState)
            val loadedEpisodes = source.episodes(
                loadedDetails.ref,
                loadedDetails.sourceState ?: item.sourceState,
            )
            Triple(
                loadedDetails,
                loadedEpisodes,
                withContext(Dispatchers.IO) {
                    database.isInLibrary(item.ref) ||
                        (loadedDetails.ref != item.ref && database.isInLibrary(loadedDetails.ref))
                },
            )
        }.onSuccess {
            details = it.first
            episodes = it.second
            inLibrary = it.third
        }.onFailure {
            error = it.message ?: "Unknown error"
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (showToolbarTitle) details?.title ?: item.title else "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (showToolbarTitle) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.Transparent
                    },
                ),
            )
        },
    ) { padding ->
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            error != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(error ?: "Unknown error", modifier = Modifier.padding(24.dp))
            }

            details != null -> {
                val anime = details!!
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                ) {
                    item(key = "info") {
                        AnimeInfoBox(
                            anime = anime,
                            sourceName = source.metadata.name,
                            topPadding = padding.calculateTopPadding(),
                        )
                        AnimeActionRow(
                            inLibrary = inLibrary,
                            hasWebView = anime.webUrl != null,
                            onLibraryClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        if (inLibrary) {
                                            database.removeFromLibrary(anime.ref)
                                        } else {
                                            database.addToLibrary(anime)
                                        }
                                    }
                                    inLibrary = !inLibrary
                                    onLibraryChanged()
                                }
                            },
                            onWebViewClick = {
                                anime.webUrl?.let { onOpenWeb(anime.title, it) }
                            },
                        )
                        ExpandableDescription(
                            description = anime.description,
                            genres = anime.genres,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Episodes",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    if (episodes.isEmpty()) {
                        item(key = "empty-episodes") {
                            Text(
                                text = "No episodes found.",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(
                            items = episodes,
                            key = {
                                it.ref.sourceId + "|" + it.ref.sourceAnimeId + "|" + it.ref.sourceEpisodeId
                            },
                        ) { episode ->
                            val downloadStatus = downloadStatuses[
                                downloadManager.key(episode.ref.sourceId, episode.ref.sourceEpisodeId)
                            ]
                            AniyomiEpisodeRow(
                                episode = episode,
                                status = downloadStatus,
                                downloadEnabled = source.metadata.capabilities.downloadable,
                                playEnabled = source.metadata.capabilities.streamable,
                                playLoading = resolvingEpisodeId == episode.ref.sourceEpisodeId,
                                onPlay = {
                                    if (resolvingEpisodeId == null) {
                                        resolvingEpisodeId = episode.ref.sourceEpisodeId
                                        scope.launch {
                                            runCatching {
                                                source.resolve(episode.ref, episode.sourceState)
                                                    .firstOrNull { it.url.isNotBlank() }
                                                    ?: error("This source did not return a playable video.")
                                            }.onSuccess { media ->
                                                runCatching {
                                                    ExternalPlayerLauncher.open(context, media)
                                                }.onFailure { failure ->
                                                    Toast.makeText(
                                                        context,
                                                        failure.message ?: "No compatible player was found.",
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                                }
                                            }.onFailure { failure ->
                                                Toast.makeText(
                                                    context,
                                                    failure.message ?: "Could not resolve this episode.",
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                            resolvingEpisodeId = null
                                        }
                                    }
                                },
                                onDownload = {
                                    val permissionGranted =
                                        Build.VERSION.SDK_INT > 28 ||
                                            context.checkSelfPermission(
                                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                            ) == PackageManager.PERMISSION_GRANTED

                                    if (
                                        DownloadStoragePolicy.requiresLegacyWritePermission(
                                            sdkInt = Build.VERSION.SDK_INT,
                                            permissionGranted = permissionGranted,
                                        )
                                    ) {
                                        pendingLegacyDownload = anime to episode
                                        legacyStoragePermissionLauncher.launch(
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                        )
                                    } else {
                                        downloadManager.enqueue(source, anime, episode)
                                    }
                                },
                                onOpen = {
                                    downloadStatus?.let { downloadManager.openDownloaded(context, it) }
                                },
                                onCancel = {
                                    downloadStatus?.let { downloadManager.cancel(it) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeInfoBox(
    anime: AnimeDetails,
    sourceName: String,
    topPadding: androidx.compose.ui.unit.Dp,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    Box(modifier = Modifier.fillMaxWidth()) {
        val backdrop = anime.bannerUrl ?: anime.coverUrl
        if (!backdrop.isNullOrBlank()) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    backgroundColor,
                                ),
                            ),
                        )
                    }
                    .blur(4.dp)
                    .alpha(0.2f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = topPadding + 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(
                url = anime.coverUrl,
                contentDescription = anime.title,
                modifier = Modifier
                    .sizeIn(maxWidth = 100.dp)
                    .width(100.dp)
                    .aspectRatio(2f / 3f),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = anime.title.ifBlank { "Unknown title" },
                    style = MaterialTheme.typography.titleLarge,
                )

                val author = anime.metadata["Author"]
                InfoLine(
                    icon = Icons.Filled.PersonOutline,
                    text = author?.takeIf { it.isNotBlank() } ?: "Unknown author",
                )

                val artist = anime.metadata["Artist"]
                if (!artist.isNullOrBlank() && artist != author) {
                    InfoLine(
                        icon = Icons.Filled.Brush,
                        text = artist,
                    )
                }

                val status = anime.metadata["Status"] ?: "Unknown"
                Row(
                    modifier = Modifier.alpha(0.78f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = statusIcon(status),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = sourceName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier.alpha(0.78f),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AnimeActionRow(
    inLibrary: Boolean,
    hasWebView: Boolean,
    onLibraryClick: () -> Unit,
    onWebViewClick: () -> Unit,
) {
    val defaultColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
    ) {
        ActionButton(
            title = if (inLibrary) "In library" else "Add to library",
            icon = if (inLibrary) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            color = if (inLibrary) MaterialTheme.colorScheme.primary else defaultColor,
            onClick = onLibraryClick,
        )
        ActionButton(
            title = "N/A",
            icon = Icons.Filled.HourglassEmpty,
            color = defaultColor,
            onClick = {},
        )
        if (hasWebView) {
            ActionButton(
                title = "Web view",
                icon = Icons.Outlined.Public,
                color = defaultColor,
                onClick = onWebViewClick,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                color = color,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ExpandableDescription(
    description: String?,
    genres: List<String>,
) {
    var expanded by remember { mutableStateOf(false) }
    val cleaned = description?.trim()?.takeIf { it.isNotBlank() } ?: "No description."

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .animateContentSize(),
        ) {
            Column {
                Text(
                    text = cleaned,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Outlined.KeyboardArrowUp
                        } else {
                            Icons.Outlined.KeyboardArrowDown
                        },
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }
        }

        if (genres.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                genres.forEach { genre ->
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AniyomiEpisodeRow(
    episode: AnimeEpisode,
    status: NamiDownloadStatus?,
    downloadEnabled: Boolean,
    playEnabled: Boolean,
    playLoading: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
) {
    val downloaded = status?.state == NamiDownloadState.DOWNLOADED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = downloaded, onClick = onOpen)
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val date = episode.uploadedAtEpochMillis?.let(::formatEpisodeDate)
            val number = episode.number?.let {
                if (it % 1.0 == 0.0) "Episode " + it.toInt() else "Episode " + it
            }
            val subtitle = listOfNotNull(number, date).joinToString(" • ")
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (playEnabled) {
            IconButton(
                onClick = onPlay,
                enabled = !playLoading,
            ) {
                if (playLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (downloaded || downloadEnabled || status != null) {
            val active = status?.state == NamiDownloadState.QUEUED ||
                status?.state == NamiDownloadState.DOWNLOADING
            val action = when {
                downloaded -> onOpen
                active -> onCancel
                else -> onDownload
            }
            IconButton(
                onClick = action,
                enabled = downloaded || active || downloadEnabled,
            ) {
                when (status?.state) {
                    NamiDownloadState.QUEUED -> Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                        )
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cancel queued download",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    NamiDownloadState.DOWNLOADING -> Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            progress = { status.progress / 100f },
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                        )
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Cancel download",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    NamiDownloadState.DOWNLOADED -> Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Downloaded",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NamiDownloadState.ERROR -> Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = status.errorMessage ?: "Download failed",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    null -> Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun statusIcon(status: String): androidx.compose.ui.graphics.vector.ImageVector = when (status.lowercase()) {
    "ongoing" -> Icons.Outlined.Schedule
    "completed" -> Icons.Outlined.DoneAll
    "finished" -> Icons.Outlined.Done
    "cancelled" -> Icons.Outlined.Close
    "on hiatus" -> Icons.Outlined.Pause
    else -> Icons.Outlined.Block
}

private fun formatEpisodeDate(epochMillis: Long): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
}
