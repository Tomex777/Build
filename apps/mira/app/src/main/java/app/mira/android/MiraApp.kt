@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.mira.android

import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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

private sealed interface MiraRoute {
    data class Details(val item: ContentSearchResult) : MiraRoute
    data object Downloads : MiraRoute
    data object Sources : MiraRoute
    data class Player(
        val title: String,
        val media: ResolvedMedia,
        val identity: MiraPlaybackIdentity,
    ) : MiraRoute
}

@Composable
fun MiraApp() {
    val context = LocalContext.current
    val application = context.applicationContext as MiraApplication
    val disabledSourceIds by application.sourceEnablementStore.disabledIds.collectAsState()
    val extensions by application.installedExtensions.collectAsState()
    val allSources = remember(extensions) { application.sources }
    val enabledSources = allSources.filter {
        it.metadata.id !in disabledSourceIds
    }
    var rootTab by rememberSaveable { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<MiraRoute>() }

    BackHandler(enabled = stack.isNotEmpty()) {
        stack.removeAt(stack.lastIndex)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (stack.isEmpty()) {
                NavigationBar {
                    NavigationBarItem(
                        selected = rootTab == 0,
                        onClick = { rootTab = 0 },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Search") },
                    )
                    NavigationBarItem(
                        selected = rootTab == 1,
                        onClick = { rootTab = 1 },
                        icon = { Icon(Icons.Default.LibraryBooks, contentDescription = null) },
                        label = { Text("Library") },
                    )
                }
            }
        },
    ) { outerPadding ->
        val current = stack.lastOrNull()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (stack.isEmpty()) outerPadding else PaddingValues(0.dp)),
        ) {
            when (current) {
                null -> {
                    if (rootTab == 0) {
                        MiraSearchHome(
                            sources = enabledSources,
                            search = GlobalMediaSearch(application.sourceRegistry),
                            onOpen = { stack += MiraRoute.Details(it) },
                        )
                    } else {
                        MiraLibraryScreen(
                            libraryStore = application.libraryStore,
                            watchProgressStore = application.watchProgressStore,
                            onOpen = { stack += MiraRoute.Details(it) },
                            onDownloads = { stack += MiraRoute.Downloads },
                            onSources = { stack += MiraRoute.Sources },
                        )
                    }
                }

                is MiraRoute.Details -> {
                    MiraDetailsScreen(
                        item = current.item,
                        sources = allSources,
                        libraryStore = application.libraryStore,
                        watchProgressStore = application.watchProgressStore,
                        downloadManager = application.downloadManager,
                        onBack = { stack.removeAt(stack.lastIndex) },
                        onPlay = { identity, media ->
                            stack += MiraRoute.Player(identity.title, media, identity)
                        },
                    )
                }

                MiraRoute.Downloads -> {
                    MiraDownloadsScreen(
                        manager = application.downloadManager,
                        onBack = { stack.removeAt(stack.lastIndex) },
                        onPlay = { status ->
                            application.downloadManager.completedMedia(status)?.let { media ->
                                val identity = MiraPlaybackIdentity(
                                    sourceId = status.sourceId,
                                    contentId = status.contentId,
                                    episodeId = status.episodeId,
                                    kind = if (status.episodeId == null) {
                                        ContentKind.MOVIE
                                    } else {
                                        ContentKind.SERIES
                                    },
                                    title = status.title,
                                    subtitle = status.subtitle,
                                )
                                stack += MiraRoute.Player(status.title, media, identity)
                            }
                        },
                    )
                }

                MiraRoute.Sources -> {
                    MiraSourcesScreen(
                        sources = allSources,
                        enablementStore = application.sourceEnablementStore,
                        onBack = { stack.removeAt(stack.lastIndex) },
                    )
                }

                is MiraRoute.Player -> {
                    MiraPlayerScreen(
                        title = current.title,
                        media = current.media,
                        identity = current.identity,
                        watchProgressStore = application.watchProgressStore,
                        onBack = { stack.removeAt(stack.lastIndex) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiraSearchHome(
    sources: List<MiraSource>,
    search: GlobalMediaSearch,
    onOpen: (ContentSearchResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var resultsBySource by remember {
        mutableStateOf<Map<String, List<ContentSearchResult>>>(emptyMap())
    }
    var loading by remember { mutableStateOf(false) }
    var hasSearched by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        loading = true
        hasSearched = true
        message = null
        scope.launch {
            val result = search.search(normalized)
            resultsBySource = result.resultsBySource
            message = when {
                result.resultsBySource.values.all { it.isEmpty() } && result.failures.isNotEmpty() ->
                    result.failures.joinToString(" · ") {
                        it.sourceName + ": " + (it.cause.message ?: "failed")
                    }
                result.resultsBySource.values.all { it.isEmpty() } -> "No results"
                result.failures.isNotEmpty() -> "Some sources failed; available results are shown."
                else -> null
            }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            if (it.isBlank()) {
                                hasSearched = false
                                resultsBySource = emptyMap()
                                message = null
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        placeholder = { Text("Search movies and TV") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = ::submit) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        },
                        singleLine = true,
                    )
                    if (loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    HorizontalDivider()
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!hasSearched) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text("Sources", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Search across installed Movie and TV sources.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(sources, key = { it.metadata.id }) { source ->
                    SourceSummaryRow(source)
                }
            } else {
                message?.let {
                    item {
                        Text(
                            it,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                sources.forEach { source ->
                    val results = resultsBySource[source.metadata.id].orEmpty()
                    if (results.isNotEmpty()) {
                        item(key = "header:${source.metadata.id}") {
                            Column(Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp)) {
                                Text(
                                    source.metadata.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    sourceCapabilityLabel(source),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(
                            items = results,
                            key = { it.ref.sourceId + "\u0000" + it.ref.sourceContentId },
                        ) { item ->
                            ContentRow(item = item, onClick = { onOpen(item) })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SourceSummaryRow(
    source: MiraSource,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (source.metadata.capabilities.movies) {
                Icons.Default.Movie
            } else {
                Icons.Default.Tv
            },
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(source.metadata.name, style = MaterialTheme.typography.titleMedium)
            Text(
                sourceCapabilityLabel(source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}

private fun sourceCapabilityLabel(source: MiraSource): String = buildList {
    if (source.metadata.capabilities.movies) add("Movies")
    if (source.metadata.capabilities.series) add("TV Series")
    if (source.metadata.capabilities.movieStreaming) add("Playback")
    if (source.metadata.capabilities.episodeStreaming) add("Episode playback")
    if (source.metadata.capabilities.downloadable) add("Downloads")
}.joinToString(" · ")

@Composable
private fun ContentRow(
    item: ContentSearchResult,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            modifier = Modifier.size(width = 72.dp, height = 104.dp),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f),
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(if (item.ref.kind == ContentKind.MOVIE) "Movie" else "TV Series")
                    item.year?.let { append(" · "); append(it) }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MiraLibraryScreen(
    libraryStore: MiraLibraryStore,
    watchProgressStore: MiraWatchProgressStore,
    onOpen: (ContentSearchResult) -> Unit,
    onDownloads: () -> Unit,
    onSources: () -> Unit,
) {
    val library by libraryStore.items.collectAsState()
    val watched by watchProgressStore.entries.collectAsState()
    val continueWatching = remember(watched) {
        watched
            .filter { !it.completed && it.positionMs > 0L }
            .sortedByDescending { it.lastWatchedAtEpochMillis }
            .take(20)
    }
    val movies = remember(library) { library.filter { it.ref.kind == ContentKind.MOVIE } }
    val series = remember(library) { library.filter { it.ref.kind == ContentKind.SERIES } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = onDownloads) {
                        Icon(Icons.Default.Download, contentDescription = "Downloads")
                    }
                    IconButton(onClick = onSources) {
                        Icon(Icons.Default.Source, contentDescription = "Sources")
                    }
                },
            )
        },
    ) { padding ->
        if (library.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LibraryBooks,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Your library is empty", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add a Movie or TV Series from its details page.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (continueWatching.isNotEmpty()) {
                    item { LibraryHeader("Continue Watching", continueWatching.size) }
                    items(
                        continueWatching,
                        key = { it.identity.stableKey },
                    ) { progress ->
                        ContinueWatchingRow(
                            progress = progress,
                            onClick = { onOpen(progress.identity.asSearchResult()) },
                        )
                    }
                }
                if (movies.isNotEmpty()) {
                    item { LibraryHeader("Movies", movies.size) }
                    items(
                        movies,
                        key = { it.ref.sourceId + "\u0000" + it.ref.sourceContentId },
                    ) { item ->
                        ContentRow(item = item, onClick = { onOpen(item) })
                    }
                }
                if (series.isNotEmpty()) {
                    item { LibraryHeader("TV Series", series.size) }
                    items(
                        series,
                        key = { it.ref.sourceId + "\u0000" + it.ref.sourceContentId },
                    ) { item ->
                        ContentRow(item = item, onClick = { onOpen(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    progress: MiraWatchProgress,
    onClick: () -> Unit,
) {
    val fraction = if (progress.durationMs > 0L) {
        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = progress.identity.posterUrl,
            contentDescription = progress.identity.title,
            modifier = Modifier.size(width = 72.dp, height = 104.dp),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f),
        ) {
            Text(
                progress.identity.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            progress.identity.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Text(
                if (progress.durationMs > 0L) {
                    "${formatTime(progress.positionMs)} / ${formatTime(progress.durationMs)}"
                } else {
                    "Continue at ${formatTime(progress.positionMs)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryHeader(title: String, count: Int) {
    Text(
        "$title ($count)",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun MiraDetailsScreen(
    item: ContentSearchResult,
    sources: List<MiraSource>,
    libraryStore: MiraLibraryStore,
    watchProgressStore: MiraWatchProgressStore,
    downloadManager: MiraDownloadManager,
    onBack: () -> Unit,
    onPlay: (MiraPlaybackIdentity, ResolvedMedia) -> Unit,
) {
    val source = remember(item.ref.sourceId) {
        sources.first { it.metadata.id == item.ref.sourceId }
    }
    val library by libraryStore.items.collectAsState()
    val watched by watchProgressStore.entries.collectAsState()
    val inLibrary = remember(library, item.ref) {
        library.any { it.ref == item.ref }
    }
    val currentMovieProgress = remember(watched, item.ref) {
        watched.firstOrNull {
            it.identity.sourceId == item.ref.sourceId &&
                it.identity.contentId == item.ref.sourceContentId &&
                it.identity.episodeId == null &&
                !it.completed
        }
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
        error = null
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val stored = details?.let {
                                item.copy(
                                    title = it.title,
                                    posterUrl = it.posterUrl ?: item.posterUrl,
                                    year = it.year ?: item.year,
                                    description = it.description ?: item.description,
                                    sourceState = it.sourceState ?: item.sourceState,
                                )
                            } ?: item
                            libraryStore.toggle(stored)
                        },
                    ) {
                        Text(if (inLibrary) "In library" else "Add to library")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            error?.let {
                item {
                    Text(
                        it,
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            details?.let { loaded ->
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = loaded.posterUrl,
                            contentDescription = loaded.title,
                            modifier = Modifier.size(width = 110.dp, height = 165.dp),
                            contentScale = ContentScale.Crop,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(loaded.title, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                buildString {
                                    append(if (loaded.ref.kind == ContentKind.MOVIE) "Movie" else "TV Series")
                                    loaded.year?.let { append(" · "); append(it) }
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (loaded.genres.isNotEmpty()) {
                                Text(
                                    loaded.genres.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                source.metadata.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                loaded.description?.takeIf { it.isNotBlank() }?.let { description ->
                    item {
                        Text(
                            description,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (loaded.ref.kind == ContentKind.MOVIE) {
                    item {
                        Text(
                            "Play",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    if (streams.isEmpty()) {
                        item {
                            Text(
                                "No playable files were returned by this source.",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(streams.take(8), key = { it.url }) { media ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(media.quality ?: media.mimeType ?: "Video")
                                    media.hosterName?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                val identity = MiraPlaybackIdentity(
                                    sourceId = loaded.ref.sourceId,
                                    contentId = loaded.ref.sourceContentId,
                                    kind = loaded.ref.kind,
                                    title = loaded.title,
                                    posterUrl = loaded.posterUrl,
                                    sourceState = loaded.sourceState,
                                )
                                Button(onClick = { onPlay(identity, media) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Text(
                                        if (currentMovieProgress != null) {
                                            "Resume"
                                        } else {
                                            "Play"
                                        },
                                    )
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
                                        Icon(Icons.Default.Download, contentDescription = "Download")
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
                                "This source provides series, seasons and episode metadata but no playable episode streams yet.",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    items(episodes, key = { it.ref.sourceEpisodeId }) { episode ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                buildString {
                                    episode.episodeNumber?.let { append("E$it · ") }
                                    append(episode.title)
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            episode.airDate?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            episode.description?.let {
                                Text(
                                    it,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MiraDownloadsScreen(
    manager: MiraDownloadManager,
    onBack: () -> Unit,
    onPlay: (MiraDownloadStatus) -> Unit,
) {
    val statuses by manager.statuses.collectAsState()
    val paused by manager.globalPaused.collectAsState()
    val list = statuses.values.sortedWith(
        compareBy<MiraDownloadStatus> { it.title.lowercase() }
            .thenBy { it.subtitle.orEmpty().lowercase() },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (list.any {
                            it.state == MiraDownloadState.QUEUED ||
                                it.state == MiraDownloadState.DOWNLOADING ||
                                it.state == MiraDownloadState.WAITING_FOR_NETWORK ||
                                it.state == MiraDownloadState.PAUSED
                        }
                    ) {
                        IconButton(onClick = { if (paused) manager.resumeAll() else manager.pauseAll() }) {
                            Icon(
                                if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (paused) "Resume all" else "Pause all",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (list.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No downloads")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(list, key = { it.id }) { status ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = status.state == MiraDownloadState.DOWNLOADED,
                                onClick = { onPlay(status) },
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                status.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            status.subtitle?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                status.state.name.replace('_', ' ').lowercase()
                                    .replaceFirstChar { it.titlecase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (status.state == MiraDownloadState.ERROR) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            LinearProgressIndicator(
                                progress = { status.progress.coerceIn(0, 100) / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                            status.errorMessage?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            when (status.state) {
                                MiraDownloadState.DOWNLOADING,
                                MiraDownloadState.QUEUED,
                                MiraDownloadState.WAITING_FOR_NETWORK,
                                -> IconButton(onClick = { manager.pause(status) }) {
                                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                                }
                                MiraDownloadState.PAUSED -> IconButton(onClick = { manager.resume(status) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Continue")
                                }
                                MiraDownloadState.ERROR -> IconButton(onClick = { manager.retry(status) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Retry")
                                }
                                MiraDownloadState.DOWNLOADED -> IconButton(onClick = { onPlay(status) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                }
                            }
                            IconButton(onClick = { manager.remove(status) }) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = if (status.state == MiraDownloadState.DOWNLOADED) {
                                        "Delete"
                                    } else {
                                        "Cancel"
                                    },
                                )
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun MiraSourcesScreen(
    sources: List<MiraSource>,
    enablementStore: MiraSourceEnablementStore,
    onBack: () -> Unit,
) {
    val disabledIds by enablementStore.disabledIds.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sources") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Installed sources", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Choose which sources appear in Browse and search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
            items(sources, key = { it.metadata.id }) { source ->
                SourceSummaryRow(
                    source = source,
                    trailing = {
                        Switch(
                            checked = source.metadata.id !in disabledIds,
                            onCheckedChange = { enabled ->
                                enablementStore.setEnabled(source.metadata.id, enabled)
                            },
                        )
                    },
                )
                HorizontalDivider(Modifier.padding(start = 58.dp))
            }
        }
    }
}

@Composable
private fun MiraPlayerScreen(
    title: String,
    media: ResolvedMedia,
    identity: MiraPlaybackIdentity,
    watchProgressStore: MiraWatchProgressStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember { MiraVlcPlayer(context) }
    val state by player.state.collectAsState()

    fun saveProgress(
        positionMs: Long = player.state.value.positionMs,
        durationMs: Long = player.state.value.durationMs,
    ) {
        watchProgressStore.save(
            identity = identity,
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }

    BackHandler {
        saveProgress()
        onBack()
    }

    DisposableEffect(player, identity.stableKey) {
        onDispose {
            saveProgress()
            player.release()
        }
    }
    LaunchedEffect(media.url, identity.stableKey) {
        val saved = watchProgressStore.get(identity)
        val startPosition = saved
            ?.takeUnless { it.completed }
            ?.positionMs
            ?: 0L
        player.play(media, startPosition)
    }
    LaunchedEffect(state.positionMs / 5_000L) {
        if (state.positionMs > 0L) saveProgress()
    }
    LaunchedEffect(state.ended) {
        if (state.ended && state.durationMs > 0L) {
            saveProgress(state.durationMs, state.durationMs)
        }
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
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                title,
                Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    val minutes = seconds / 60L
    val remaining = seconds % 60L
    return "%d:%02d".format(minutes, remaining)
}
