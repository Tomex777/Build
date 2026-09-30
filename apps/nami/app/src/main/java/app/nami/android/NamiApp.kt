@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.nami.android

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nami.compat.aniyomi.AniyomiBrowserSourceHandle
import app.nami.data.local.NamiDatabase
import app.nami.data.local.StoredCategory
import app.nami.data.local.StoredLibraryEntry
import app.nami.data.local.StoredWatchProgress
import app.nami.domain.AnimeRef
import app.nami.domain.AnimeSearchResult
import app.nami.runtime.AnimeSearchItemResult
import app.nami.runtime.GlobalAnimeSearch
import app.nami.runtime.GlobalSearchSection
import app.nami.runtime.GlobalSearchState
import app.nami.runtime.NamiSourceRegistry
import app.nami.runtime.SourceListing
import app.nami.runtime.SourceListingPager
import app.nami.runtime.SourceListingState
import app.nami.runtime.SourceEnablementStore
import app.nami.source.NamiAnimeSource
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LibrarySort {
    TITLE,
    DATE_ADDED,
    LAST_WATCHED,
}

private data class LibrarySnapshot(
    val entries: List<StoredLibraryEntry>,
    val continueWatching: List<StoredWatchProgress>,
    val watchHistory: List<StoredWatchProgress>,
    val categories: List<StoredCategory>,
)

private sealed interface NamiRoute {
    data class Source(
        val source: NamiAnimeSource,
        val listing: SourceListing,
    ) : NamiRoute

    data class Details(
        val source: NamiAnimeSource,
        val item: AnimeSearchResult,
    ) : NamiRoute

    data object Downloads : NamiRoute
    data object Categories : NamiRoute
    data object History : NamiRoute
    data object Statistics : NamiRoute
    data object DataStorage : NamiRoute
    data object Settings : NamiRoute
    data object About : NamiRoute
    data object Help : NamiRoute

    data class Player(
        val session: NamiPlaybackSession,
    ) : NamiRoute

    data class Browser(
        val title: String,
        val url: String,
        val sourceId: String?,
        val headers: Map<String, String>,
    ) : NamiRoute
}

@Composable
fun NamiApp(
    sourceRegistry: NamiSourceRegistry,
    installedSourceRegistry: NamiSourceRegistry,
    sourceEnablementStore: SourceEnablementStore,
    database: NamiDatabase,
    downloadManager: NamiDownloadManager,
) {
    // Library is Nami's home. Browse and More retain their root state while nested
    // destinations live on the shared stack below.
    var rootTab by rememberSaveable { mutableIntStateOf(0) }
    var libraryRevision by remember { mutableIntStateOf(0) }
    var downloadedOnly by rememberSaveable { mutableStateOf(false) }
    var incognitoEnabled by rememberSaveable { mutableStateOf(false) }
    val stack = remember { mutableStateListOf<NamiRoute>() }

    BackHandler(
        enabled = stack.isNotEmpty() && stack.lastOrNull() !is NamiRoute.Player,
    ) {
        stack.removeAt(stack.lastIndex)
    }

    NamiTheme {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (stack.isEmpty()) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = rootTab == 0,
                            onClick = { rootTab = 0 },
                            icon = {
                                Icon(
                                    Icons.Outlined.VideoLibrary,
                                    contentDescription = "Library tab",
                                )
                            },
                            label = { Text("Library") },
                        )
                        NavigationBarItem(
                            selected = rootTab == 1,
                            onClick = { rootTab = 1 },
                            icon = {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = "Browse tab",
                                )
                            },
                            label = { Text("Browse") },
                        )
                        NavigationBarItem(
                            selected = rootTab == 2,
                            onClick = { rootTab = 2 },
                            icon = {
                                Icon(
                                    Icons.Outlined.MoreHoriz,
                                    contentDescription = "More tab",
                                )
                            },
                            label = { Text("More") },
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
                        when (rootTab) {
                            0 -> LibraryScreen(
                                database = database,
                                sourceRegistry = sourceRegistry,
                                downloadManager = downloadManager,
                                revision = libraryRevision,
                                downloadedOnly = downloadedOnly,
                                onOpenAnime = { source, item ->
                                    stack += NamiRoute.Details(source, item)
                                },
                            )

                            1 -> GlobalSearchHome(
                                sourceRegistry = sourceRegistry,
                                onOpenSource = { source, listing ->
                                    stack += NamiRoute.Source(source, listing)
                                },
                                onOpenAnime = { source, item ->
                                    stack += NamiRoute.Details(source, item)
                                },
                            )

                            else -> NamiMoreScreen(
                                downloadedOnly = downloadedOnly,
                                onDownloadedOnlyChanged = { downloadedOnly = it },
                                incognitoEnabled = incognitoEnabled,
                                onIncognitoChanged = { incognitoEnabled = it },
                                onDownloads = { stack += NamiRoute.Downloads },
                                onCategories = { stack += NamiRoute.Categories },
                                onHistory = { stack += NamiRoute.History },
                                onStatistics = { stack += NamiRoute.Statistics },
                                onDataStorage = { stack += NamiRoute.DataStorage },
                                onSettings = { stack += NamiRoute.Settings },
                                onAbout = { stack += NamiRoute.About },
                                onHelp = { stack += NamiRoute.Help },
                            )
                        }
                    }

                    is NamiRoute.Source -> {
                        SourceBrowseScreen(
                            route = current,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onOpenWeb = { url ->
                                stack += browserRoute(
                                    source = current.source,
                                    title = current.source.metadata.name,
                                    url = url,
                                )
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
                                stack += browserRoute(
                                    source = current.source,
                                    title = title,
                                    url = url,
                                )
                            },
                            downloadManager = downloadManager,
                            onPlayEpisode = { anime, episodes, index ->
                                stack += NamiRoute.Player(
                                    NamiPlaybackSession.Streaming(
                                        source = current.source,
                                        anime = anime,
                                        episodes = episodes,
                                        initialEpisodeIndex = index,
                                    ),
                                )
                            },
                            onPlayDownloaded = { status ->
                                stack += NamiRoute.Player(
                                    downloadedPlaybackSession(downloadManager, status),
                                )
                            },
                        )
                    }

                    NamiRoute.Downloads -> {
                        NamiDownloadsScreen(
                            downloadManager = downloadManager,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onPlayDownloaded = { status ->
                                stack += NamiRoute.Player(
                                    downloadedPlaybackSession(downloadManager, status),
                                )
                            },
                        )
                    }

                    NamiRoute.Categories -> {
                        NamiCategoriesScreen(
                            database = database,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onChanged = { libraryRevision++ },
                        )
                    }

                    NamiRoute.History -> {
                        NamiHistoryScreen(
                            database = database,
                            sourceRegistry = sourceRegistry,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onOpenAnime = { source, item ->
                                stack += NamiRoute.Details(source, item)
                            },
                        )
                    }

                    NamiRoute.Statistics -> {
                        NamiStatisticsScreen(
                            database = database,
                            onBack = { stack.removeAt(stack.lastIndex) },
                        )
                    }

                    NamiRoute.DataStorage -> {
                        NamiDataStorageScreen(
                            database = database,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onDownloads = { stack += NamiRoute.Downloads },
                        )
                    }

                    NamiRoute.Settings -> {
                        NamiSettingsScreen(
                            installedSourceRegistry = installedSourceRegistry,
                            sourceEnablementStore = sourceEnablementStore,
                            onBack = { stack.removeAt(stack.lastIndex) },
                        )
                    }

                    NamiRoute.About -> {
                        NamiAboutScreen(
                            onBack = { stack.removeAt(stack.lastIndex) },
                        )
                    }

                    NamiRoute.Help -> {
                        NamiHelpScreen(
                            onBack = { stack.removeAt(stack.lastIndex) },
                        )
                    }

                    is NamiRoute.Player -> {
                        NamiPlayerScreen(
                            session = current.session,
                            database = database,
                            persistWatchActivity = !incognitoEnabled,
                            onRedownloadDownloaded = { status ->
                                downloadManager.redownload(status)
                            },
                            onBack = { stack.removeAt(stack.lastIndex) },
                        )
                    }

                    is NamiRoute.Browser -> {
                        NamiWebViewScreen(
                            title = current.title,
                            url = current.url,
                            sourceId = current.sourceId,
                            headers = current.headers,
                            onClose = { stack.removeAt(stack.lastIndex) },
                        )
                    }
                }
            }
        }
    }
}

private fun browserRoute(
    source: NamiAnimeSource,
    title: String,
    url: String,
): NamiRoute.Browser {
    val headers = (source as? AniyomiBrowserSourceHandle)
        ?.browserHeaders(url)
        .orEmpty()
    return NamiRoute.Browser(
        title = title,
        url = url,
        sourceId = source.metadata.id,
        headers = headers,
    )
}

private fun downloadedPlaybackSession(
    downloadManager: NamiDownloadManager,
    selected: NamiDownloadStatus,
): NamiPlaybackSession.Downloaded {
    val episodeNumber = Regex("""(\d+(?:\.\d+)?)""")
    val items = downloadManager.statuses.value.values
        .filter {
            it.state == NamiDownloadState.DOWNLOADED &&
                !it.contentUri.isNullOrBlank() &&
                it.sourceId == selected.sourceId &&
                it.sourceAnimeId == selected.sourceAnimeId
        }
        .sortedWith(
            compareBy<NamiDownloadStatus> {
                episodeNumber.find(it.episodeTitle)?.value?.toDoubleOrNull()
                    ?: Double.MAX_VALUE
            }.thenBy { it.episodeTitle.lowercase() },
        )
        .ifEmpty { listOf(selected) }
    val index = items.indexOfFirst {
        it.sourceId == selected.sourceId &&
            it.sourceEpisodeId == selected.sourceEpisodeId
    }.coerceAtLeast(0)
    return NamiPlaybackSession.Downloaded(items, index)
}

@Composable
internal fun GlobalSearchHome(
    sourceRegistry: NamiSourceRegistry,
    onOpenSource: (NamiAnimeSource, SourceListing) -> Unit,
    onOpenAnime: (NamiAnimeSource, AnimeSearchResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hostView = LocalView.current
    val searcher = remember(sourceRegistry) { GlobalAnimeSearch(sourceRegistry) }

    fun dismissSearchKeyboard() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(hostView.windowToken, 0)
    }

    var query by rememberSaveable { mutableStateOf("") }
    var searchState by remember { mutableStateOf(GlobalSearchState()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var hasSearched by rememberSaveable { mutableStateOf(false) }
    var enabledSources by remember { mutableStateOf<List<NamiAnimeSource>?>(null) }

    LaunchedEffect(sourceRegistry) {
        enabledSources = runCatching {
            sourceRegistry.installedSources()
                .filter { source -> source.metadata.capabilities.searchable }
                .sortedWith(
                    compareBy<NamiAnimeSource> { it.metadata.name.lowercase() }
                        .thenBy { it.metadata.language.orEmpty() },
                )
        }.getOrDefault(emptyList())
    }

    fun submitSearch() {
        val submitted = query.trim()
        if (submitted.isEmpty()) return
        dismissSearchKeyboard()
        hasSearched = true
        searchJob?.cancel()
        searchJob = scope.launch {
            searcher.searchFlow(submitted).collect {
                searchState = it
            }
        }
    }

    val shownSections = searchState.sections
    val resultListState = rememberLazyListState()
    val shownOrder = shownSections.map { it.source.metadata.id }

    LaunchedEffect(shownOrder) {
        // LazyColumn preserves the first visible keyed item when items move. During live
        // global search that can hide a newly completed source above the anchored row.
        // If the user is still at the top of the results, keep the viewport pinned to
        // position zero so each completion-order transition is immediately visible.
        if (resultListState.firstVisibleItemIndex <= 1) {
            resultListState.scrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.statusBarsPadding(),
            ) {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { value ->
                            query = value
                            if (value.isBlank()) {
                                searchJob?.cancel()
                                searchJob = null
                                searchState = GlobalSearchState()
                                hasSearched = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("global-search-field"),
                        shape = RoundedCornerShape(28.dp),
                        placeholder = { Text("Search anime") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                    )
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
                    state = resultListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("global-search-results"),
                    contentPadding = padding,
                ) {
                    lazyItems(
                        items = shownSections,
                        key = { it.source.metadata.id },
                    ) { section ->
                        Box(modifier = Modifier.animateItem()) {
                            GlobalSearchSourceSection(
                                section = section,
                                query = query.trim(),
                                onRetry = ::submitSearch,
                                onOpenSource = { source, listing ->
                                    dismissSearchKeyboard()
                                    onOpenSource(source, listing)
                                },
                                onOpenAnime = { source, item ->
                                    dismissSearchKeyboard()
                                    onOpenAnime(source, item)
                                },
                            )
                        }
                    }
                }
            }

            !hasSearched -> {
                val browsable = enabledSources
                    ?.filter { it.metadata.capabilities.popular }

                when {
                    enabledSources == null -> {
                        EmptyCenter(
                            modifier = Modifier.padding(padding),
                            text = "Loading sources…",
                        )
                    }

                    browsable.isNullOrEmpty() -> {
                        EmptyCenter(
                            modifier = Modifier.padding(padding),
                            text = if (enabledSources.orEmpty().isEmpty()) {
                                "No sources are enabled."
                            } else {
                                "Search your enabled sources."
                            },
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = padding,
                        ) {
                            item(key = "source-header") {
                                Text(
                                    text = "Sources",
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        top = 14.dp,
                                        end = 16.dp,
                                        bottom = 8.dp,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            lazyItems(
                                items = browsable,
                                key = { it.metadata.id },
                            ) { source ->
                                SourceHomeRow(
                                    source = source,
                                    onPopular = {
                                        onOpenSource(source, SourceListing.Popular)
                                    },
                                    onLatest = if (source.metadata.capabilities.latest) {
                                        {
                                            onOpenSource(source, SourceListing.Latest)
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }

            searchState.total == 0 -> {
                EmptyCenter(
                    modifier = Modifier.padding(padding),
                    text = "No sources are enabled.",
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
private fun SourceHomeRow(
    source: NamiAnimeSource,
    onPopular: () -> Unit,
    onLatest: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPopular)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.metadata.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            source.metadata.language?.takeIf { it.isNotBlank() }?.let { language ->
                Text(
                    text = language,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (onLatest != null) {
            TextButton(onClick = onLatest) {
                Text("Latest")
            }
        }

        IconButton(onClick = onPopular) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = "Popular",
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun GlobalSearchSourceSection(
    section: GlobalSearchSection,
    query: String,
    onRetry: () -> Unit,
    onOpenSource: (NamiAnimeSource, SourceListing) -> Unit,
    onOpenAnime: (NamiAnimeSource, AnimeSearchResult) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Global search source: " + section.source.metadata.name
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenSource(section.source, SourceListing.Search(query)) }
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
            IconButton(onClick = { onOpenSource(section.source, SourceListing.Search(query)) }) {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = searchFailureMessage(result.throwable),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
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
                                modifier = Modifier.width(128.dp),
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
    Surface(
        modifier = modifier
            .semantics {
                contentDescription = "Open anime: " + item.title
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
    ) {
        Column {
            Cover(
                url = item.coverUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            )
            Text(
                text = item.title,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun searchFailureMessage(failure: Throwable): String =
    sourceFailureMessage(failure)

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
private fun SourceBrowseScreen(
    route: NamiRoute.Source,
    onBack: () -> Unit,
    onOpenWeb: (String) -> Unit,
    onOpenAnime: (AnimeSearchResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val pager = remember(route.source) { SourceListingPager(route.source) }

    var query by remember(route.source.metadata.id, route.listing) {
        mutableStateOf((route.listing as? SourceListing.Search)?.query.orEmpty())
    }
    var listingState by remember(route.source.metadata.id) {
        mutableStateOf(SourceListingState())
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var verificationRequired by remember { mutableStateOf(false) }

    fun loadListing(listing: SourceListing) {
        loading = true
        error = null
        verificationRequired = false
        listingState = SourceListingState(listing = listing)

        scope.launch {
            runCatching { pager.load(listing) }
                .onSuccess { listingState = it }
                .onFailure { failure ->
                    logSourceFailure("source browse", failure)
                    verificationRequired = sourceFailureRequiresVerification(failure)
                    error = sourceFailureMessage(
                        failure,
                        fallback = "Unable to load this source. Try again.",
                    )
                }
            loading = false
        }
    }

    fun submitSearch() {
        val submitted = query.trim()
        if (submitted.isEmpty()) return
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        loadListing(SourceListing.Search(submitted))
    }

    fun loadNextPage() {
        if (loading || !listingState.hasNextPage) return

        loading = true
        error = null
        verificationRequired = false
        scope.launch {
            runCatching { pager.next(listingState) }
                .onSuccess { listingState = it }
                .onFailure { failure ->
                    logSourceFailure("source browse next page", failure)
                    verificationRequired = sourceFailureRequiresVerification(failure)
                    error = sourceFailureMessage(
                        failure,
                        fallback = "Unable to load more. Try again.",
                    )
                }
            loading = false
        }
    }

    LaunchedEffect(route.source.metadata.id, route.listing) {
        query = (route.listing as? SourceListing.Search)?.query.orEmpty()
        loadListing(route.listing)
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
                    if (route.source.metadata.capabilities.configurable) {
                        IconButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        context,
                                        AniyomiSourcePreferencesActivity::class.java,
                                    ).putExtra(
                                        AniyomiSourcePreferencesActivity.EXTRA_SOURCE_ID,
                                        route.source.metadata.id,
                                    ),
                                )
                            },
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Source settings")
                        }
                    }
                    route.source.metadata.homeUrl?.let { homeUrl ->
                        IconButton(onClick = { onOpenWeb(homeUrl) }) {
                            Icon(Icons.Outlined.Public, contentDescription = "Open website")
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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Search " + route.source.metadata.name) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
            )

            val listingLabel = when (listingState.listing) {
                SourceListing.Popular -> "Popular"
                SourceListing.Latest -> "Latest"
                is SourceListing.Search, null -> null
            }
            if (listingLabel != null) {
                Text(
                    text = listingLabel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            when {
                loading && listingState.items.isEmpty() -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                error != null && listingState.items.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = error ?: "Unknown error",
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(
                            onClick = { loadListing(listingState.listing ?: route.listing) },
                        ) {
                            Text("Retry")
                        }
                        if (verificationRequired) {
                            route.source.metadata.homeUrl
                                ?.takeIf { it.isNotBlank() }
                                ?.let { homeUrl ->
                                    TextButton(onClick = { onOpenWeb(homeUrl) }) {
                                        Text("Verify in browser")
                                    }
                                }
                        }
                    }
                }

                listingState.items.isEmpty() && !loading -> {
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
                            items = listingState.items,
                            key = { it.ref.sourceId + "|" + it.ref.sourceAnimeId },
                        ) { item ->
                            AnimeCard(
                                item = item,
                                onClick = { onOpenAnime(item) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (listingState.hasNextPage || loading || error != null) {
                            item(
                                key = "source-listing-footer-" + listingState.loadedPage,
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when {
                                        loading -> CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                        )

                                        error != null -> Text(
                                            text = "Load more failed — tap to retry",
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier
                                                .clickable {
                                                    error = null
                                                    loadNextPage()
                                                }
                                                .padding(8.dp),
                                        )

                                        listingState.hasNextPage -> {
                                            LaunchedEffect(
                                                listingState.loadedPage,
                                                listingState.items.size,
                                            ) {
                                                loadNextPage()
                                            }
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    database: NamiDatabase,
    sourceRegistry: NamiSourceRegistry,
    downloadManager: NamiDownloadManager,
    revision: Int,
    downloadedOnly: Boolean,
    onOpenAnime: (NamiAnimeSource, AnimeSearchResult) -> Unit,
) {
    var entries by remember { mutableStateOf<List<StoredLibraryEntry>>(emptyList()) }
    var continueWatching by remember {
        mutableStateOf<List<StoredWatchProgress>>(emptyList())
    }
    var sources by remember { mutableStateOf<List<NamiAnimeSource>>(emptyList()) }
    var watchHistory by remember { mutableStateOf<List<StoredWatchProgress>>(emptyList()) }
    var categories by remember { mutableStateOf<List<StoredCategory>>(emptyList()) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryEntryIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var searchingLibrary by rememberSaveable { mutableStateOf(false) }
    var librarySort by remember { mutableStateOf(LibrarySort.TITLE) }
    var sortExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val downloadStatuses by downloadManager.statuses.collectAsState()

    LaunchedEffect(revision, sourceRegistry) {
        val local = withContext(Dispatchers.IO) {
            LibrarySnapshot(
                entries = database.getLibraryEntries(),
                continueWatching = database.getContinueWatching(),
                watchHistory = database.getWatchHistory(),
                categories = database.getCategories(),
            )
        }
        entries = local.entries
        continueWatching = local.continueWatching
        watchHistory = local.watchHistory
        categories = local.categories
        if (
            selectedCategoryId != null &&
            local.categories.none { it.id == selectedCategoryId }
        ) {
            selectedCategoryId = null
        }
        sources = runCatching { sourceRegistry.installedSources() }.getOrDefault(emptyList())
    }

    LaunchedEffect(revision, selectedCategoryId) {
        categoryEntryIds = withContext(Dispatchers.IO) {
            selectedCategoryId
                ?.let(database::getLibraryEntryIdsForCategory)
                .orEmpty()
        }
    }

    fun openProgress(progress: StoredWatchProgress) {
        val animeId = progress.sourceAnimeId ?: return
        val source = sources.firstOrNull { it.metadata.id == progress.sourceId }
        if (source != null) {
            onOpenAnime(
                source,
                AnimeSearchResult(
                    ref = AnimeRef(progress.sourceId, animeId),
                    title = progress.animeTitle ?: "Anime",
                    sourceState = progress.animeSourceState,
                ),
            )
        } else {
            scope.launch {
                sources = runCatching { sourceRegistry.installedSources() }
                    .getOrDefault(emptyList())
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                modifier = Modifier.testTag("library-top-bar"),
                title = {
                    if (searchingLibrary) {
                        OutlinedTextField(
                            value = libraryQuery,
                            onValueChange = { libraryQuery = it },
                            placeholder = { Text("Search library") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(if (downloadedOnly) "Library · Downloaded" else "Library")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            searchingLibrary = !searchingLibrary
                            if (!searchingLibrary) libraryQuery = ""
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = if (searchingLibrary) {
                                "Close library search"
                            } else {
                                "Search library"
                            },
                        )
                    }
                    Box {
                        IconButton(onClick = { sortExpanded = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = "Sort library")
                        }
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Title") },
                                onClick = {
                                    librarySort = LibrarySort.TITLE
                                    sortExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Date added") },
                                onClick = {
                                    librarySort = LibrarySort.DATE_ADDED
                                    sortExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Last watched") },
                                onClick = {
                                    librarySort = LibrarySort.LAST_WATCHED
                                    sortExpanded = false
                                },
                            )
                        }
                    }
                },
                )
                if (!searchingLibrary && categories.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "all-categories") {
                            FilterChip(
                                selected = selectedCategoryId == null,
                                onClick = { selectedCategoryId = null },
                                label = { Text("All") },
                            )
                        }
                        lazyItems(categories, key = { it.id }) { category ->
                            FilterChip(
                                selected = selectedCategoryId == category.id,
                                onClick = { selectedCategoryId = category.id },
                                label = { Text(category.name) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        val localAnime = downloadStatuses.values
            .asSequence()
            .filter { it.state == NamiDownloadState.DOWNLOADED }
            .map { it.sourceId to it.sourceAnimeId }
            .toSet()
        val normalizedQuery = libraryQuery.trim()

        val lastWatchedByAnime = watchHistory
            .mapNotNull { progress ->
                val animeId = progress.sourceAnimeId ?: return@mapNotNull null
                (progress.sourceId to animeId) to progress.lastWatchedAtEpochMillis
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.maxOrNull() ?: 0L }

        val filteredEntries = entries
            .asSequence()
            .filter { selectedCategoryId == null || it.id in categoryEntryIds }
            .filter { !downloadedOnly || (it.ref.sourceId to it.ref.sourceAnimeId) in localAnime }
            .filter {
                normalizedQuery.isBlank() ||
                    it.title.contains(normalizedQuery, ignoreCase = true)
            }
            .toList()

        val shownEntries = when (librarySort) {
            LibrarySort.TITLE -> filteredEntries.sortedBy { it.title.lowercase() }
            LibrarySort.DATE_ADDED -> filteredEntries.sortedByDescending { it.addedAtEpochMillis }
            LibrarySort.LAST_WATCHED -> filteredEntries.sortedWith(
                compareByDescending<StoredLibraryEntry> {
                    lastWatchedByAnime[it.ref.sourceId to it.ref.sourceAnimeId] ?: 0L
                }.thenBy { it.title.lowercase() },
            )
        }

        val selectedCategoryRefs = if (selectedCategoryId == null) {
            emptySet()
        } else {
            entries.asSequence()
                .filter { it.id in categoryEntryIds }
                .map { it.ref.sourceId to it.ref.sourceAnimeId }
                .toSet()
        }

        val shownContinueWatching = continueWatching.filter { progress ->
            val animeId = progress.sourceAnimeId
            val matchesCategory = selectedCategoryId == null ||
                (animeId != null && (progress.sourceId to animeId) in selectedCategoryRefs)
            val matchesDownload = !downloadedOnly ||
                (animeId != null && (progress.sourceId to animeId) in localAnime)
            val matchesQuery = normalizedQuery.isBlank() ||
                progress.animeTitle.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
                progress.episodeTitle.orEmpty().contains(normalizedQuery, ignoreCase = true)
            matchesCategory && matchesDownload && matchesQuery
        }

        if (shownEntries.isEmpty() && shownContinueWatching.isEmpty()) {
            EmptyCenter(
                modifier = Modifier.padding(padding),
                text = when {
                    normalizedQuery.isNotBlank() -> "No library results for “$normalizedQuery”."
                    selectedCategoryId != null -> {
                        val name = categories.firstOrNull { it.id == selectedCategoryId }?.name
                        if (name.isNullOrBlank()) "No anime in this category." else "No anime in $name."
                    }
                    downloadedOnly -> "No downloaded anime in your library."
                    else -> "Your anime library is empty."
                },
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
                if (shownContinueWatching.isNotEmpty()) {
                    item(
                        key = "continue-watching",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Column {
                            Text(
                                text = "Continue watching",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp),
                            ) {
                                lazyItems(
                                    items = shownContinueWatching,
                                    key = {
                                        it.sourceId + "|" + it.sourceEpisodeId
                                    },
                                ) { progress ->
                                    ContinueWatchingCard(
                                        progress = progress,
                                        onClick = { openProgress(progress) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (shownEntries.isNotEmpty()) {
                    item(
                        key = "library-heading",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Text(
                            text = "My library",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }

                gridItems(shownEntries, key = { it.id }) { entry ->
                    AnimeCard(
                        item = AnimeSearchResult(
                            ref = entry.ref,
                            title = entry.title,
                            coverUrl = entry.coverUrl,
                            sourceState = entry.sourceState,
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
                                        sourceState = entry.sourceState,
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
private fun ContinueWatchingCard(
    progress: StoredWatchProgress,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(230.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = progress.animeTitle ?: "Anime",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = progress.episodeTitle ?: "Continue episode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress.durationMs > 0L) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = {
                        (progress.positionMs.toFloat() / progress.durationMs.toFloat())
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = formatWatchTime(progress.positionMs) + " watched",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Resume",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun formatWatchTime(positionMs: Long): String {
    val seconds = positionMs.coerceAtLeast(0L) / 1_000L
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    return if (hours > 0L) {
        "${hours}h ${minutes}m"
    } else if (seconds < 60L) {
        "${seconds}s"
    } else {
        "${minutes}m"
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
