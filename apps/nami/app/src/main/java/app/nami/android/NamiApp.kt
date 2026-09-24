@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.nami.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nami.data.local.NamiDatabase
import app.nami.data.local.StoredLibraryEntry
import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
import app.nami.domain.AnimeSearchResult
import app.nami.runtime.AnimeSearchItemResult
import app.nami.runtime.GlobalAnimeSearch
import app.nami.runtime.GlobalSearchSection
import app.nami.runtime.GlobalSearchState
import app.nami.runtime.NamiSourceRegistry
import app.nami.source.NamiAnimeSource
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface NamiRoute {
    data class Source(
        val source: NamiAnimeSource,
        val query: String,
    ) : NamiRoute

    data class Details(
        val source: NamiAnimeSource,
        val item: AnimeSearchResult,
    ) : NamiRoute

    data object Downloads : NamiRoute
    data object Settings : NamiRoute

    data class Browser(
        val title: String,
        val url: String,
    ) : NamiRoute
}

@Composable
fun NamiApp(
    sourceRegistry: NamiSourceRegistry,
    database: NamiDatabase,
    downloadManager: NamiDownloadManager,
) {
    var rootTab by rememberSaveable { mutableIntStateOf(0) }
    var libraryRevision by remember { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<NamiRoute>() }

    BackHandler(enabled = stack.isNotEmpty()) {
        stack.removeAt(stack.lastIndex)
    }

    MaterialTheme {
        Scaffold(
            bottomBar = {
                if (stack.isEmpty()) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = rootTab == 0,
                            onClick = { rootTab = 0 },
                            icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            label = { Text("Search") },
                        )
                        NavigationBarItem(
                            selected = rootTab == 1,
                            onClick = { rootTab = 1 },
                            icon = { Icon(Icons.Outlined.VideoLibrary, contentDescription = null) },
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
                            GlobalSearchHome(
                                sourceRegistry = sourceRegistry,
                                onOpenSource = { source, query ->
                                    stack += NamiRoute.Source(source, query)
                                },
                                onOpenAnime = { source, item ->
                                    stack += NamiRoute.Details(source, item)
                                },
                            )
                        } else {
                            LibraryScreen(
                                database = database,
                                sourceRegistry = sourceRegistry,
                                revision = libraryRevision,
                                onDownloads = { stack += NamiRoute.Downloads },
                                onSettings = { stack += NamiRoute.Settings },
                                onOpenAnime = { source, item ->
                                    stack += NamiRoute.Details(source, item)
                                },
                            )
                        }
                    }

                    is NamiRoute.Source -> {
                        SourceSearchScreen(
                            route = current,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onOpenWeb = { url ->
                                stack += NamiRoute.Browser(current.source.metadata.name, url)
                            },
                            onOpenAnime = { item ->
                                stack += NamiRoute.Details(current.source, item)
                            },
                        )
                    }

                    is NamiRoute.Details -> {
                        NamiAnimeDetailsScreen(
                            database = database,
                            source = current.source,
                            item = current.item,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onLibraryChanged = { libraryRevision++ },
                            onOpenWeb = { title, url ->
                                stack += NamiRoute.Browser(title, url)
                            },
                            downloadManager = downloadManager,
                        )
                    }

                    NamiRoute.Downloads -> {
                        NamiDownloadsScreen(
                            downloadManager = downloadManager,
                            onBack = { stack.removeAt(stack.lastIndex) },
                        )
                    }

                    NamiRoute.Settings -> {
                        SimpleDestination(
                            title = "Settings",
                            body = "Nami settings live inside Library. Source, download, and player settings will be added here.",
                            onBack = { stack.removeAt(stack.lastIndex) },
                        )
                    }

                    is NamiRoute.Browser -> {
                        NamiWebViewScreen(
                            title = current.title,
                            url = current.url,
                            onClose = { stack.removeAt(stack.lastIndex) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalSearchHome(
    sourceRegistry: NamiSourceRegistry,
    onOpenSource: (NamiAnimeSource, String) -> Unit,
    onOpenAnime: (NamiAnimeSource, AnimeSearchResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val searcher = remember(sourceRegistry) { GlobalAnimeSearch(sourceRegistry) }

    var query by rememberSaveable { mutableStateOf("") }
    var searchState by remember { mutableStateOf(GlobalSearchState()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var hasSearched by rememberSaveable { mutableStateOf(false) }
    var onlyHasResults by rememberSaveable { mutableStateOf(false) }
    var sourceCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(sourceRegistry) {
        sourceCount = runCatching { sourceRegistry.installedSources().size }.getOrNull()
    }

    fun submitSearch() {
        val submitted = query.trim()
        if (submitted.isEmpty()) return
        focusManager.clearFocus()
        hasSearched = true
        searchJob?.cancel()
        searchJob = scope.launch {
            searcher.searchFlow(submitted).collect {
                searchState = it
            }
        }
    }

    val shownSections = remember(searchState, onlyHasResults) {
        if (!onlyHasResults) {
            searchState.sections
        } else {
            searchState.sections.filter {
                val result = it.result as? AnimeSearchItemResult.Success
                result != null && result.result.isNotEmpty()
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        placeholder = { Text("Search anime") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                    )
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.DoneAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            label = { Text("All") },
                        )
                        FilterChip(
                            selected = onlyHasResults,
                            onClick = { onlyHasResults = !onlyHasResults },
                            label = { Text("Has results") },
                        )
                    }
                    if (searchState.total > 0 && searchState.progress in 1 until searchState.total) {
                        LinearProgressIndicator(
                            progress = { searchState.progress / searchState.total.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HorizontalDivider()
                }
            }
        },
    ) { padding ->
        when {
            shownSections.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                ) {
                    lazyItems(
                        items = shownSections,
                        key = { it.source.metadata.id },
                    ) { section ->
                        GlobalSearchSourceSection(
                            section = section,
                            query = query.trim(),
                            onOpenSource = onOpenSource,
                            onOpenAnime = onOpenAnime,
                        )
                    }
                }
            }

            !hasSearched -> {
                EmptyCenter(
                    modifier = Modifier.padding(padding),
                    text = when (sourceCount) {
                        0 -> "No anime extensions are available yet."
                        null -> "Search across your anime extensions."
                        else -> "Search across " + sourceCount + " anime extension" +
                            if (sourceCount == 1) "." else "s."
                    },
                )
            }

            searchState.total == 0 -> {
                EmptyCenter(
                    modifier = Modifier.padding(padding),
                    text = "No enabled anime extensions were found.",
                )
            }

            else -> {
                EmptyCenter(
                    modifier = Modifier.padding(padding),
                    text = "No results found.",
                )
            }
        }
    }
}

@Composable
private fun GlobalSearchSourceSection(
    section: GlobalSearchSection,
    query: String,
    onOpenSource: (NamiAnimeSource, String) -> Unit,
    onOpenAnime: (NamiAnimeSource, AnimeSearchResult) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSource(section.source, query) }
                .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.source.metadata.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                section.source.metadata.language?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            IconButton(onClick = { onOpenSource(section.source, query) }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
            }
        }

        when (val result = section.result) {
            AnimeSearchItemResult.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }

            is AnimeSearchItemResult.Error -> {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(result.throwable.message ?: "Unknown error")
                }
            }

            is AnimeSearchItemResult.Success -> {
                if (result.result.isEmpty()) {
                    Text(
                        text = "No results found",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        lazyItems(
                            items = result.result,
                            key = { it.ref.sourceId + "|" + it.ref.sourceAnimeId },
                        ) { item ->
                            AnimeCard(
                                item = item,
                                onClick = { onOpenAnime(section.source, item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeCard(
    item: AnimeSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(96.dp)
            .clickable(onClick = onClick),
    ) {
        Cover(
            url = item.coverUrl,
            contentDescription = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        )
        Text(
            text = item.title,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun Cover(
    url: String?,
    contentDescription: String?,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    if (url.isNullOrBlank()) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.VideoLibrary, contentDescription = null)
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    }
}

@Composable
private fun SourceSearchScreen(
    route: NamiRoute.Source,
    onBack: () -> Unit,
    onOpenWeb: (String) -> Unit,
    onOpenAnime: (AnimeSearchResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable(route.source.metadata.id) { mutableStateOf(route.query) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<AnimeSearchResult>>(emptyList()) }

    fun submitSearch() {
        val submitted = query.trim()
        if (submitted.isEmpty()) return
        focusManager.clearFocus()
        scope.launch {
            loading = true
            error = null
            runCatching { route.source.search(submitted).items }
                .onSuccess { results = it }
                .onFailure { error = it.message ?: "Unknown error" }
            loading = false
        }
    }

    LaunchedEffect(route.source.metadata.id, route.query) {
        if (route.query.isNotBlank()) submitSearch()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(route.source.metadata.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    route.source.metadata.homeUrl?.let { homeUrl ->
                        IconButton(onClick = { onOpenWeb(homeUrl) }) {
                            Icon(Icons.Outlined.Public, contentDescription = "Web view")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                placeholder = { Text("Search " + route.source.metadata.name) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
            )

            when {
                loading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                error != null -> {
                    EmptyCenter(text = error ?: "Unknown error")
                }

                results.isEmpty() && query.isNotBlank() -> {
                    EmptyCenter(text = "No results found.")
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        gridItems(
                            items = results,
                            key = { it.ref.sourceId + "|" + it.ref.sourceAnimeId },
                        ) { item ->
                            AnimeCard(
                                item = item,
                                onClick = { onOpenAnime(item) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeDetailsScreen(
    route: NamiRoute.Details,
    database: NamiDatabase,
    onBack: () -> Unit,
    onLibraryChanged: () -> Unit,
    onOpenWeb: (String, String) -> Unit,
) {
    var details by remember { mutableStateOf<AnimeDetails?>(null) }
    var episodes by remember { mutableStateOf<List<AnimeEpisode>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var inLibrary by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(route.item.ref) {
        loading = true
        error = null
        runCatching {
            val loadedDetails = route.source.details(route.item.ref)
            val loadedEpisodes = route.source.episodes(route.item.ref)
            Triple(
                loadedDetails,
                loadedEpisodes,
                withContext(Dispatchers.IO) { database.isInLibrary(route.item.ref) },
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
                title = { Text(details?.title ?: route.item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            error != null -> {
                EmptyCenter(
                    modifier = Modifier.padding(padding),
                    text = error ?: "Unknown error",
                )
            }

            details != null -> {
                val anime = details!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Cover(
                                url = anime.coverUrl ?: route.item.coverUrl,
                                contentDescription = anime.title,
                                modifier = Modifier
                                    .width(120.dp)
                                    .aspectRatio(2f / 3f),
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(anime.title, style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.height(8.dp))
                                anime.metadata.values
                                    .filter { it.isNotBlank() }
                                    .take(4)
                                    .forEach {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(bottom = 2.dp),
                                        )
                                    }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            AssistChip(
                                onClick = {
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
                                label = { Text(if (inLibrary) "In library" else "Add to library") },
                                leadingIcon = {
                                    Icon(
                                        if (inLibrary) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                            anime.webUrl?.let { webUrl ->
                                AssistChip(
                                    onClick = { onOpenWeb(anime.title, webUrl) },
                                    label = { Text("Web view") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Public,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                )
                            }
                        }

                        anime.description?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it.trim(),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        if (anime.genres.isNotEmpty()) {
                            Text(
                                text = anime.genres.joinToString(" • "),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                        Text(
                            text = "Episodes",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }

                    if (episodes.isEmpty()) {
                        item {
                            Text(
                                text = "No episodes found.",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        lazyItems(
                            items = episodes,
                            key = {
                                it.ref.sourceId + "|" + it.ref.sourceAnimeId + "|" + it.ref.sourceEpisodeId
                            },
                        ) { episode ->
                            EpisodeRow(episode)
                            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: AnimeEpisode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            episode.number?.let {
                Text(
                    text = "Episode " + if (it % 1.0 == 0.0) it.toInt().toString() else it.toString(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Icon(Icons.Outlined.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LibraryScreen(
    database: NamiDatabase,
    sourceRegistry: NamiSourceRegistry,
    revision: Int,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    onOpenAnime: (NamiAnimeSource, AnimeSearchResult) -> Unit,
) {
    var entries by remember { mutableStateOf<List<StoredLibraryEntry>>(emptyList()) }
    var sources by remember { mutableStateOf<List<NamiAnimeSource>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(revision, sourceRegistry) {
        entries = withContext(Dispatchers.IO) { database.getLibraryEntries() }
        sources = runCatching { sourceRegistry.installedSources() }.getOrDefault(emptyList())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = onDownloads) {
                        Icon(Icons.Outlined.Download, contentDescription = "Downloads")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyCenter(
                modifier = Modifier.padding(padding),
                text = "Your anime library is empty.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    end = 12.dp,
                    bottom = padding.calculateBottomPadding() + 12.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(entries, key = { it.id }) { entry ->
                    AnimeCard(
                        item = AnimeSearchResult(
                            ref = entry.ref,
                            title = entry.title,
                            coverUrl = entry.coverUrl,
                        ),
                        onClick = {
                            val source = sources.firstOrNull { it.metadata.id == entry.ref.sourceId }
                            if (source != null) {
                                onOpenAnime(
                                    source,
                                    AnimeSearchResult(
                                        ref = entry.ref,
                                        title = entry.title,
                                        coverUrl = entry.coverUrl,
                                    ),
                                )
                            } else {
                                scope.launch {
                                    sources = runCatching { sourceRegistry.installedSources() }
                                        .getOrDefault(emptyList())
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleDestination(
    title: String,
    body: String,
    onBack: () -> Unit,
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
        EmptyCenter(
            modifier = Modifier.padding(padding),
            text = body,
        )
    }
}

@Composable
private fun EmptyCenter(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
