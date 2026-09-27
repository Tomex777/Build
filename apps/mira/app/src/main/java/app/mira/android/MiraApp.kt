package app.mira.android

import android.view.TextureView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.mira.domain.ContentDetails
import app.mira.domain.ContentKind
import app.mira.domain.ContentSearchResult
import app.mira.domain.ResolvedMedia
import app.mira.domain.TvEpisode
import app.mira.domain.TvSeason
import app.mira.runtime.GlobalMediaSearch
import app.mira.source.MiraSource
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private enum class MiraTab {
    HOME,
    SEARCH,
    LIBRARY,
    DOWNLOADS,
    SOURCES,
}

@Composable
fun MiraApp() {
    val context = LocalContext.current
    val application = context.applicationContext as MiraApplication
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(MiraTab.HOME) }
    var detailItem by remember { mutableStateOf<ContentSearchResult?>(null) }
    var playing by remember { mutableStateOf<Pair<String, ResolvedMedia>?>(null) }

    playing?.let { (title, media) ->
        MiraPlayerScreen(
            title = title,
            media = media,
            onBack = { playing = null },
        )
        return
    }

    detailItem?.let { item ->
        MiraDetailsScreen(
            item = item,
            sources = application.sources,
            downloadManager = application.downloadManager,
            onBack = { detailItem = null },
            onPlay = { title, media -> playing = title to media },
        )
        return
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == MiraTab.HOME,
                    onClick = { tab = MiraTab.HOME },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = tab == MiraTab.SEARCH,
                    onClick = { tab = MiraTab.SEARCH },
                    icon = { Icon(Icons.Default.Search, null) },
                    label = { Text("Search") },
                )
                NavigationBarItem(
                    selected = tab == MiraTab.LIBRARY,
                    onClick = { tab = MiraTab.LIBRARY },
                    icon = { Icon(Icons.Default.LibraryBooks, null) },
                    label = { Text("Library") },
                )
                NavigationBarItem(
                    selected = tab == MiraTab.DOWNLOADS,
                    onClick = { tab = MiraTab.DOWNLOADS },
                    icon = { Icon(Icons.Default.Download, null) },
                    label = { Text("Downloads") },
                )
                NavigationBarItem(
                    selected = tab == MiraTab.SOURCES,
                    onClick = { tab = MiraTab.SOURCES },
                    icon = { Icon(Icons.Default.Source, null) },
                    label = { Text("Sources") },
                )
            }
        },
    ) { padding ->
        when (tab) {
            MiraTab.HOME -> MiraHomeScreen(
                modifier = Modifier.padding(padding),
                sources = application.sources,
                onOpen = { detailItem = it },
            )
            MiraTab.SEARCH -> MiraSearchScreen(
                modifier = Modifier.padding(padding),
                search = GlobalMediaSearch(application.sourceRegistry),
                onOpen = { detailItem = it },
            )
            MiraTab.LIBRARY -> MiraLibraryScreen(Modifier.padding(padding))
            MiraTab.DOWNLOADS -> MiraDownloadsScreen(
                modifier = Modifier.padding(padding),
                manager = application.downloadManager,
                onPlay = { status ->
                    application.downloadManager.completedMedia(status)?.let { media ->
                        playing = status.title to media
                    }
                },
            )
            MiraTab.SOURCES -> MiraSourcesScreen(
                modifier = Modifier.padding(padding),
                sources = application.sources,
            )
        }
    }
}

@Composable
private fun MiraHomeScreen(
    modifier: Modifier,
    sources: List<MiraSource>,
    onOpen: (ContentSearchResult) -> Unit,
) {
    var movies by remember { mutableStateOf<List<ContentSearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val movieSource = sources.firstOrNull {
            it.metadata.capabilities.movies && it.metadata.capabilities.browsable
        }
        if (movieSource != null) {
            runCatching { movieSource.popular(1).items }
                .onSuccess { movies = it.take(20) }
                .onFailure { error = it.message }
        }
        loading = false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(20.dp)) {
                Text("Mira", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Movies and TV, powered by independent sources.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Movie, null)
                            Text("Movies", style = MaterialTheme.typography.titleMedium)
                            Text("Search, play and download")
                        }
                    }
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Tv, null)
                            Text("TV Series", style = MaterialTheme.typography.titleMedium)
                            Text("Seasons and episodes")
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("Popular movies", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        error?.let {
            item { Text(it, modifier = Modifier.padding(horizontal = 20.dp)) }
        }
        items(movies, key = { it.ref.sourceId + it.ref.sourceContentId }) { item ->
            ContentRow(item = item, onClick = { onOpen(item) })
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun MiraSearchScreen(
    modifier: Modifier,
    search: GlobalMediaSearch,
    onOpen: (ContentSearchResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ContentSearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Movies or TV series") },
            )
            IconButton(
                onClick = {
                    if (query.isBlank()) return@IconButton
                    loading = true
                    message = null
                    scope.launch {
                        val result = search.search(query)
                        results = result.resultsBySource.values.flatten()
                        message = when {
                            results.isEmpty() && result.failures.isNotEmpty() ->
                                result.failures.joinToString { it.sourceName + ": " + it.cause.message }
                            results.isEmpty() -> "No results"
                            result.failures.isNotEmpty() ->
                                "Some sources failed; available results are shown."
                            else -> null
                        }
                        loading = false
                    }
                },
            ) {
                Icon(Icons.Default.Search, "Search")
            }
        }
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        message?.let { Text(it, modifier = Modifier.padding(vertical = 8.dp)) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.ref.sourceId + it.ref.sourceContentId }) { item ->
                ContentRow(item = item, onClick = { onOpen(item) })
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ContentRow(
    item: ContentSearchResult,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                modifier = Modifier.size(width = 72.dp, height = 104.dp),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        append(if (item.ref.kind == ContentKind.MOVIE) "Movie" else "TV Series")
                        item.year?.let { append(" · "); append(it) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                item.description?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, maxLines = 3, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MiraDetailsScreen(
    item: ContentSearchResult,
    sources: List<MiraSource>,
    downloadManager: MiraDownloadManager,
    onBack: () -> Unit,
    onPlay: (String, ResolvedMedia) -> Unit,
) {
    val source = remember(item.ref.sourceId) {
        sources.first { it.metadata.id == item.ref.sourceId }
    }
    var details by remember { mutableStateOf<ContentDetails?>(null) }
    var seasons by remember { mutableStateOf<List<TvSeason>>(emptyList()) }
    var selectedSeason by remember { mutableStateOf<TvSeason?>(null) }
    var episodes by remember { mutableStateOf<List<TvEpisode>>(emptyList()) }
    var streams by remember { mutableStateOf<List<ResolvedMedia>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(item.ref) {
        loading = true
        runCatching {
            val loadedDetails = source.details(item.ref, item.sourceState)
            val loadedSeasons = if (item.ref.kind == ContentKind.SERIES) {
                source.seasons(item.ref, loadedDetails.sourceState)
            } else {
                emptyList()
            }
            val loadedStreams = if (
                item.ref.kind == ContentKind.MOVIE &&
                source.metadata.capabilities.movieStreaming
            ) {
                source.resolveMovie(item.ref, loadedDetails.sourceState)
            } else {
                emptyList()
            }
            Triple(loadedDetails, loadedSeasons, loadedStreams)
        }.onSuccess { (loadedDetails, loadedSeasons, loadedStreams) ->
            details = loadedDetails
            seasons = loadedSeasons
            streams = loadedStreams
        }.onFailure {
            error = it.message ?: it.javaClass.simpleName
        }
        loading = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Text(
                    text = source.metadata.name,
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        error?.let { item { Text(it, Modifier.padding(16.dp)) } }

        details?.let { loaded ->
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    AsyncImage(
                        model = loaded.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Text(loaded.title, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        buildString {
                            append(if (loaded.ref.kind == ContentKind.MOVIE) "Movie" else "TV Series")
                            loaded.year?.let { append(" · "); append(it) }
                        },
                    )
                    if (loaded.genres.isNotEmpty()) {
                        Text(loaded.genres.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                    loaded.description?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it)
                    }
                }
            }

            if (loaded.ref.kind == ContentKind.MOVIE) {
                if (streams.isEmpty()) {
                    item {
                        Text(
                            "No playable files were returned by this source.",
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    item {
                        Text(
                            "Available streams",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    items(streams.take(8), key = { it.url }) { media ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(media.quality ?: media.mimeType ?: "Video")
                                    media.hosterName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                                Button(onClick = { onPlay(loaded.title, media) }) {
                                    Icon(Icons.Default.PlayArrow, null)
                                    Text("Play")
                                }
                                if (source.metadata.capabilities.downloadable) {
                                    IconButton(
                                        onClick = {
                                            downloadManager.enqueue(
                                                sourceId = source.metadata.id,
                                                contentId = loaded.ref.sourceContentId,
                                                title = loaded.title,
                                                media = media,
                                            )
                                        },
                                    ) {
                                        Icon(Icons.Default.Download, "Download")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "Seasons",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        seasons.forEach { season ->
                            FilledTonalButton(
                                onClick = {
                                    selectedSeason = season
                                    episodes = emptyList()
                                    scope.launch {
                                        runCatching {
                                            source.episodes(season.ref, season.sourceState)
                                        }.onSuccess { episodes = it }
                                            .onFailure { error = it.message }
                                    }
                                },
                            ) {
                                Text(season.title)
                            }
                        }
                    }
                }
                selectedSeason?.let {
                    item {
                        Text(
                            it.title,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                if (!source.metadata.capabilities.episodeStreaming) {
                    item {
                        Text(
                            "This TV source currently supplies catalog, season and episode metadata only; Mira does not show fake playback controls.",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                items(episodes, key = { it.ref.sourceEpisodeId }) { episode ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                buildString {
                                    episode.episodeNumber?.let { append("E$it · ") }
                                    append(episode.title)
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            episode.airDate?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            episode.description?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun MiraLibraryScreen(modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text("Your saved Movies and TV Series will live here independently from Nami.")
    }
}

@Composable
private fun MiraDownloadsScreen(
    modifier: Modifier,
    manager: MiraDownloadManager,
    onPlay: (MiraDownloadStatus) -> Unit,
) {
    val statuses by manager.statuses.collectAsState()
    val paused by manager.globalPaused.collectAsState()
    val list = statuses.values.sortedBy { it.title }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Downloads", style = MaterialTheme.typography.headlineMedium)
                    Text("Persistent, resumable and offline-ready")
                }
                OutlinedButton(onClick = { if (paused) manager.resumeAll() else manager.pauseAll() }) {
                    Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, null)
                    Text(if (paused) "Resume all" else "Pause all")
                }
            }
        }
        if (list.isEmpty()) {
            item { Text("No downloads yet.", Modifier.padding(16.dp)) }
        }
        items(list, key = { it.id }) { status ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(status.title, style = MaterialTheme.typography.titleMedium)
                    status.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(status.state.name.replace('_', ' '))
                    LinearProgressIndicator(
                        progress = { status.progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                    status.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (status.state) {
                            MiraDownloadState.DOWNLOADING,
                            MiraDownloadState.QUEUED,
                            MiraDownloadState.WAITING_FOR_NETWORK,
                            -> OutlinedButton(onClick = { manager.pause(status) }) {
                                Icon(Icons.Default.Pause, null)
                                Text("Pause")
                            }
                            MiraDownloadState.PAUSED -> Button(onClick = { manager.resume(status) }) {
                                Icon(Icons.Default.PlayArrow, null)
                                Text("Continue")
                            }
                            MiraDownloadState.ERROR -> Button(onClick = { manager.retry(status) }) {
                                Icon(Icons.Default.Refresh, null)
                                Text("Retry")
                            }
                            MiraDownloadState.DOWNLOADED -> Button(onClick = { onPlay(status) }) {
                                Icon(Icons.Default.PlayArrow, null)
                                Text("Play")
                            }
                        }
                        OutlinedButton(onClick = { manager.remove(status) }) {
                            Icon(Icons.Default.Stop, null)
                            Text(if (status.state == MiraDownloadState.DOWNLOADED) "Delete" else "Cancel")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun MiraSourcesScreen(
    modifier: Modifier,
    sources: List<MiraSource>,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("Sources", style = MaterialTheme.typography.headlineMedium)
                Text("Content-specific logic stays behind Mira's source boundary.")
            }
        }
        items(sources, key = { it.metadata.id }) { source ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(source.metadata.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildList {
                            if (source.metadata.capabilities.movies) add("Movies")
                            if (source.metadata.capabilities.series) add("TV Series")
                            if (source.metadata.capabilities.movieStreaming) add("Movie playback")
                            if (source.metadata.capabilities.episodeStreaming) add("Episode playback")
                            if (source.metadata.capabilities.downloadable) add("Downloads")
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiraPlayerScreen(
    title: String,
    media: ResolvedMedia,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember { MiraVlcPlayer(context) }
    val state by player.state.collectAsState()

    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LaunchedEffect(media.url) {
        player.play(media)
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text(title, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { TextureView(it).also(player::attach) },
                modifier = Modifier.fillMaxSize(),
            )
            if (state.isBuffering) CircularProgressIndicator()
            state.error?.let { Text(it) }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = player::toggle) {
                Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                Text(if (state.isPlaying) "Pause" else "Play")
            }
            Text(
                text = "  ${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val minutes = seconds / 60L
    val remaining = seconds % 60L
    return "%d:%02d".format(minutes, remaining)
}
