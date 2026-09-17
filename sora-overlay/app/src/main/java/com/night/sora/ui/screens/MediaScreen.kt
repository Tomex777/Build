@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.night.sora.extension.ExtensionManager
import com.night.sora.extension.InstalledExtension
import com.night.sora.extension.isCatalogProvider
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.data.CachedMediaRecord
import com.night.sora.data.MediaCatalogCache
import com.night.sora.model.ContentType
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.model.LibraryEntry
import com.night.sora.model.ListeningSignal
import com.night.sora.model.MediaProgressEntry
import com.night.sora.recommendation.MusicTasteEngine
import com.night.sora.ui.theme.*
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

enum class MediaDestination(val label: String, val icon: ImageVector) {
    ANIME_MANGA("Anime & Manga", Icons.Rounded.AutoStories),
    MOVIES_TV("Movies & TV", Icons.Rounded.Movie),
    MUSIC("Music", Icons.Rounded.Headphones),
    MEMES("Memes", Icons.Rounded.TagFaces),
    BIBLE("Bible", Icons.Rounded.MenuBook),
}

private enum class MusicLocal(val label: String) { HOME("Home"), DISCOVER("Discover"), LIBRARY("Your Music") }

private data class BrowseCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val sourceId: String,
    val extensionPackage: String,
)

private fun CachedMediaRecord.toBrowseCard() = BrowseCard(id, title, subtitle, artworkUrl, sourceId, extensionPackage)
private fun BrowseCard.toCachedRecord() = CachedMediaRecord(id, title, subtitle, artworkUrl, sourceId, extensionPackage)

@Composable
fun MediaScreen(
    modifier: Modifier = Modifier,
    extensions: List<InstalledExtension>,
    extensionScanDone: Boolean,
    manager: ExtensionManager,
    libraryEntries: List<LibraryEntry>,
    listeningSignals: List<ListeningSignal>,
    progressEntries: List<MediaProgressEntry>,
    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenDetails: (ExtensionMediaSelection) -> Unit,
    onOpenBible: () -> Unit,
    onResumeProgress: (MediaProgressEntry) -> Unit,
    onPlayMusic: (ExtensionMediaSelection, List<ExtensionMediaSelection>) -> Unit,
) {
    val context = LocalContext.current
    val mediaCache = remember { MediaCatalogCache(context.applicationContext) }
    var destination by remember { mutableStateOf(MediaDestination.ANIME_MANGA) }
    var selectedType by remember { mutableStateOf(ContentType.ANIME) }
    var musicLocal by remember { mutableStateOf(MusicLocal.HOME) }
    var rows by remember { mutableStateOf(mediaCache.read(ContentType.ANIME).map { it.toBrowseCard() }) }
    var popularRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }
    var upcomingRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }
    var topRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var switchOpen by remember { mutableStateOf(false) }
    var networkEpoch by remember { mutableIntStateOf(0) }
    var memeLoading by remember { mutableStateOf(false) }
    var memeError by remember { mutableStateOf<String?>(null) }

    val engine = remember { MusicTasteEngine() }
    val rankedTaste = remember(listeningSignals) { engine.ranked(listeningSignals, System.currentTimeMillis()) }

    DisposableEffect(context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val mainHandler = Handler(Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post { networkEpoch++ }
            }
        }
        val registered = runCatching {
            connectivity.registerDefaultNetworkCallback(callback)
            true
        }.getOrDefault(false)
        onDispose {
            if (registered) runCatching { connectivity.unregisterNetworkCallback(callback) }
        }
    }

    fun setDestination(next: MediaDestination) {
        destination = next
        query = ""
        selectedType = when (next) {
            MediaDestination.ANIME_MANGA -> ContentType.ANIME
            MediaDestination.MOVIES_TV -> ContentType.MOVIE
            MediaDestination.MUSIC -> ContentType.MUSIC
            MediaDestination.MEMES -> ContentType.MEME
            MediaDestination.BIBLE -> ContentType.ANIME
        }
    }

    fun selection(card: BrowseCard, type: ContentType = selectedType) = ExtensionMediaSelection(
        id = card.id,
        sourceId = card.sourceId,
        extensionPackage = card.extensionPackage,
        type = type,
        title = card.title,
        subtitle = card.subtitle,
        artworkUrl = card.artworkUrl,
    )

    fun loadJikanFeed(feed: String, requestType: ContentType, onResult: (List<BrowseCard>) -> Unit) {
        if (requestType != ContentType.ANIME && requestType != ContentType.MANGA) {
            onResult(emptyList())
            return
        }
        val key = typeKey(requestType)
        val ext = extensions.firstOrNull { it.error == null && it.declaredId == "sora.core.jikan" }
        val source = ext?.descriptor?.sources?.firstOrNull { key in it.contentTypes }
        if (ext == null || source == null) {
            onResult(emptyList())
            return
        }
        val payload = JSONObject()
            .put("sourceId", source.id)
            .put("type", key)
            .put("feed", feed)
            .toString()
        manager.call(ext, ExtensionContract.Method.BROWSE, payload) { result ->
            onResult(result.getOrNull()?.let { parseBrowse(it, source.id, ext.packageName) }.orEmpty())
        }
    }

    fun load(search: String) {
        if (destination == MediaDestination.BIBLE) {
            rows = emptyList()
            return
        }

        val requestType = selectedType
        val requestDestination = destination
        val requestQuery = search.trim()
        val cached = if (requestType == ContentType.MEME) {
            emptyList()
        } else if (requestQuery.isBlank()) {
            mediaCache.read(requestType)
        } else {
            mediaCache.search(requestType, requestQuery)
        }
        rows = cached.map { it.toBrowseCard() }
        if (requestType == ContentType.MEME) {
            memeLoading = true
            memeError = null
        }

        val key = typeKey(requestType)
        val providers = extensions.flatMap { ext ->
            ext.descriptor?.sources.orEmpty()
                .filter { source -> ext.isCatalogProvider() && key in source.contentTypes }
                .map { source -> ext to source }
        }
        if (providers.isEmpty()) {
            if (requestType == ContentType.MEME) {
                memeLoading = false
                memeError = if (extensionScanDone) "No meme source is installed." else null
            }
            return
        }

        val method = if (requestQuery.isBlank()) ExtensionContract.Method.BROWSE else ExtensionContract.Method.SEARCH
        val collected = MutableList(providers.size) { emptyList<BrowseCard>() }
        val failures = MutableList<String?>(providers.size) { null }
        var completed = 0

        providers.forEachIndexed { index, (ext, source) ->
            val payload = JSONObject()
                .put("sourceId", source.id)
                .put("type", key)
                .put("query", requestQuery)
                .toString()
            manager.call(ext, method, payload) { result ->
                collected[index] = result.getOrNull()?.let { parseBrowse(it, source.id, ext.packageName) }.orEmpty()
                failures[index] = result.exceptionOrNull()?.message
                completed++
                if (completed == providers.size) {
                    val fresh = collected.flatten().distinctBy { it.title.trim().lowercase() }
                    if (requestType != ContentType.MEME && requestQuery.isBlank() && fresh.isNotEmpty()) {
                        mediaCache.write(requestType, fresh.map { it.toCachedRecord() })
                    }
                    if (selectedType == requestType && destination == requestDestination && query.trim() == requestQuery) {
                        if (requestType == ContentType.MEME) {
                            memeLoading = false
                            memeError = if (fresh.isEmpty()) {
                                failures.firstOrNull { !it.isNullOrBlank() }
                                    ?: if (requestQuery.isBlank()) "No image posts are available from this source right now." else "No meme results matched your search."
                            } else null
                            rows = fresh
                        } else if (fresh.isNotEmpty()) {
                            rows = fresh
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedType, extensions, query, destination, networkEpoch) {
        if (destination == MediaDestination.BIBLE) return@LaunchedEffect
        if (query.isNotBlank()) delay(250)
        load(query)
    }

    LaunchedEffect(selectedType, extensions, destination, networkEpoch) {
        popularRows = emptyList()
        upcomingRows = emptyList()
        topRows = emptyList()
        if (destination != MediaDestination.ANIME_MANGA) return@LaunchedEffect
        if (selectedType != ContentType.ANIME && selectedType != ContentType.MANGA) return@LaunchedEffect
        val requestType = selectedType
        loadJikanFeed("popular", requestType) { result ->
            if (destination == MediaDestination.ANIME_MANGA && selectedType == requestType) popularRows = result
        }
        loadJikanFeed("upcoming", requestType) { result ->
            if (destination == MediaDestination.ANIME_MANGA && selectedType == requestType) upcomingRows = result
        }
        loadJikanFeed("top", requestType) { result ->
            if (destination == MediaDestination.ANIME_MANGA && selectedType == requestType) topRows = result
        }
    }

    Column(modifier.fillMaxSize()) {
        MediaTopBar(
            title = destination.label,
            searchOpen = searchOpen,
            query = query,
            selectedType = selectedType,
            searchEnabled = destination != MediaDestination.BIBLE,
            onSwitch = { switchOpen = true },
            onOpenSearch = { searchOpen = true },
            onCloseSearch = { searchOpen = false; query = "" },
            onQuery = { query = it },
        )

        when (destination) {
            MediaDestination.ANIME_MANGA -> LocalTabs(
                labels = listOf("Anime", "Manga"),
                selected = if (selectedType == ContentType.ANIME) 0 else 1,
                onSelect = { selectedType = if (it == 0) ContentType.ANIME else ContentType.MANGA; query = "" },
            )
            MediaDestination.MOVIES_TV -> LocalTabs(
                labels = listOf("Movies", "Series"),
                selected = if (selectedType == ContentType.MOVIE) 0 else 1,
                onSelect = { selectedType = if (it == 0) ContentType.MOVIE else ContentType.TV; query = "" },
            )
            MediaDestination.MUSIC -> LocalTabs(
                labels = MusicLocal.entries.map { it.label },
                selected = MusicLocal.entries.indexOf(musicLocal),
                onSelect = { musicLocal = MusicLocal.entries[it] },
            )
            else -> Unit
        }

        when {
            destination == MediaDestination.BIBLE -> BibleHubContent(Modifier.fillMaxSize(), onOpen = onOpenBible)
            destination == MediaDestination.MEMES -> MemeSurface(
                rows = rows,
                selection = ::selection,
                onOpen = onOpenDetails,
                loading = memeLoading,
                error = memeError,
                onRetry = { load(query) },
                onOpenExtensions = onOpenExtensions,
            )
            query.isNotBlank() -> SearchResultsSurface(rows, selectedType, ::selection, onOpenDetails, onPlayMusic)
            destination == MediaDestination.ANIME_MANGA -> AnimeMangaSurface(
                type = selectedType, rows = rows, popularRows = popularRows, upcomingRows = upcomingRows, topRows = topRows,
                libraryEntries = libraryEntries, progressEntries = progressEntries, selection = ::selection, isSaved = isSaved,
                onToggleSaved = onToggleSaved, onOpen = onOpenDetails, onResume = onResumeProgress,
            )
            destination == MediaDestination.MOVIES_TV -> MovieTvSurface(
                type = selectedType, rows = rows, libraryEntries = libraryEntries, progressEntries = progressEntries,
                selection = ::selection, isSaved = isSaved, onToggleSaved = onToggleSaved, onOpen = onOpenDetails,
                onResume = onResumeProgress,
            )
            destination == MediaDestination.MUSIC -> MusicSurface(
                panel = musicLocal, rows = rows, libraryEntries = libraryEntries, rankedTaste = rankedTaste,
                selection = ::selection, onPlay = onPlayMusic, onOpen = onOpenDetails,
                onOpenExtensions = onOpenExtensions, onSelectPanel = { musicLocal = it },
            )
        }
    }

    if (switchOpen) {
        ModalBottomSheet(onDismissRequest = { switchOpen = false }, containerColor = SoraSurface) {
            Text("Go to", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            MediaDestination.entries.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        setDestination(item); switchOpen = false
                    }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(42.dp).background(SoraSurfaceHigh, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                        Icon(item.icon, null, tint = if (item == destination) SoraAccent else SoraText)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                        Text(item.label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(mediaDescription(item), color = SoraMuted, fontSize = 11.sp)
                    }
                    if (item == destination) Icon(Icons.Rounded.Check, null, tint = SoraAccent)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MediaTopBar(
    title: String,
    searchOpen: Boolean,
    query: String,
    selectedType: ContentType,
    searchEnabled: Boolean,
    onSwitch: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onQuery: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(58.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchOpen && searchEnabled) {
            IconButton(onClick = onCloseSearch) { Icon(Icons.Rounded.ArrowBack, "Close search") }
            Box(Modifier.weight(1f).background(SoraSurfaceHigh, RoundedCornerShape(14.dp)).padding(horizontal = 13.dp, vertical = 10.dp)) {
                BasicTextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    textStyle = TextStyle(color = SoraText, fontSize = 14.sp), modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner -> if (query.isEmpty()) Text("Search ${selectedType.label}…", color = SoraMuted, fontSize = 14.sp); inner() },
                )
            }
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear") }
        } else {
            TextButton(onClick = onSwitch, colors = ButtonDefaults.textButtonColors(contentColor = SoraText)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            if (searchEnabled) {
                IconButton(onClick = onOpenSearch) { Icon(Icons.Rounded.Search, "Search $title") }
            }
        }
    }
}

@Composable
private fun LocalTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 18.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        labels.forEachIndexed { index, label ->
            Column(Modifier.clickable { onSelect(index) }.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = if (index == selected) SoraText else SoraMuted, fontSize = 12.sp, fontWeight = if (index == selected) FontWeight.ExtraBold else FontWeight.SemiBold)
                if (index == selected) Box(Modifier.padding(top = 7.dp).width(24.dp).height(2.dp).background(SoraAccent, RoundedCornerShape(99.dp)))
            }
        }
    }
}

@Composable
private fun AnimeMangaSurface(
    type: ContentType,
    rows: List<BrowseCard>,
    popularRows: List<BrowseCard>,
    upcomingRows: List<BrowseCard>,
    topRows: List<BrowseCard>,
    libraryEntries: List<LibraryEntry>,
    progressEntries: List<MediaProgressEntry>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpen: (ExtensionMediaSelection) -> Unit,
    onResume: (MediaProgressEntry) -> Unit,
) {
    val selected = rows.firstOrNull() ?: popularRows.firstOrNull() ?: topRows.firstOrNull()
    val saved = libraryEntries.filter { it.contentType == type }
    val continued = progressEntries.filter { it.contentType == type && it.progress < .999f }.sortedByDescending { it.updatedAt }
    val currentLabel = if (type == ContentType.ANIME) "Airing now" else "Publishing now"
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        if (selected == null) item { EmptyFeatureShell(type) }
        if (selected != null) item {
            val selectedMedia = selection(selected, type)
            StreamFeature(
                card = selected,
                kicker = currentLabel,
                body = selected.subtitle.ifBlank {
                    if (type == ContentType.ANIME) "Currently airing in the Sora catalog." else "Currently publishing in the Sora catalog."
                },
                primaryLabel = "Open",
                selection = selectedMedia,
                isSaved = isSaved(selectedMedia),
                onToggleSaved = onToggleSaved,
                onOpen = onOpen,
            )
        }
        if (continued.isNotEmpty()) item {
            MediaSectionTitle(
                if (type == ContentType.ANIME) "Continue watching" else "Continue reading",
                "Real progress from your last session",
            )
            ProgressLandscapeRail(continued, onResume)
        }
        item {
            MediaSectionTitle("In your library", if (type == ContentType.ANIME) "Anime you saved in Sora" else "Manga you saved in Sora")
            if (saved.isNotEmpty()) ContinueLandscapeRail(saved, onOpen)
            else HintLine(if (type == ContentType.ANIME) "Saved anime will appear here." else "Saved manga will appear here.")
        }
        if (popularRows.isNotEmpty()) item {
            MediaSectionTitle("Popular now", if (type == ContentType.ANIME) "Popular anime from the catalog" else "Popular manga from the catalog")
            PortraitRail(popularRows, type, selection, onOpen)
        }
        if (upcomingRows.isNotEmpty()) item {
            MediaSectionTitle(if (type == ContentType.ANIME) "Upcoming anime" else "Upcoming manga", "Titles coming next")
            NewHotStack(upcomingRows.take(4), type, selection, onOpen)
        }
        if (topRows.isNotEmpty()) item {
            MediaSectionTitle("Top 10 ${type.label.lowercase()}", "Highest-ranked titles from the catalog")
            TopTenRail(topRows.take(10), type, selection, onOpen)
        }
        if (rows.isNotEmpty()) item {
            MediaSectionTitle(currentLabel, if (type == ContentType.ANIME) "Anime currently airing" else "Manga currently publishing")
            PortraitRail(rows, type, selection, onOpen)
        }
    }
}

@Composable
private fun MovieTvSurface(
    type: ContentType,
    rows: List<BrowseCard>,
    libraryEntries: List<LibraryEntry>,
    progressEntries: List<MediaProgressEntry>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpen: (ExtensionMediaSelection) -> Unit,
    onResume: (MediaProgressEntry) -> Unit,
) {
    val selected = rows.firstOrNull()
    val saved = libraryEntries.filter { it.contentType == type }
    val continued = progressEntries.filter { it.contentType == type && it.progress < .999f }.sortedByDescending { it.updatedAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        if (selected == null) item { EmptyFeatureShell(type) }
        if (selected != null) item {
            StreamFeature(
                card = selected,
                kicker = if (type == ContentType.MOVIE) "Featured movie" else "Featured series",
                body = selected.subtitle.ifBlank { "From your active catalog source" },
                primaryLabel = "Open",
                selection = selection(selected, type), isSaved = isSaved(selection(selected, type)), onToggleSaved = onToggleSaved, onOpen = onOpen,
            )
        }
        if (continued.isNotEmpty()) item {
            MediaSectionTitle("Continue watching", "Real playback progress from your last session")
            ProgressLandscapeRail(continued, onResume)
        }
        item {
            MediaSectionTitle("In your library", "${if (type == ContentType.MOVIE) "Movies" else "Series"} you saved in Sora")
            if (saved.isNotEmpty()) ContinueLandscapeRail(saved, onOpen) else HintLine("Saved titles will appear here.")
        }
        if (rows.isNotEmpty()) {
            item { MediaSectionTitle("Featured picks", "From your active catalog source"); PortraitRail(rows, type, selection, onOpen) }
            item { MediaSectionTitle("More to watch", "More titles from the same source"); PortraitRail(rows.drop(6).ifEmpty { rows }, type, selection, onOpen) }
            item { MediaSectionTitle("10 picks", "A quick shortlist from your source"); TopTenRail(rows.take(10), type, selection, onOpen) }
        }
    }
}

@Composable
private fun MusicSurface(
    panel: MusicLocal,
    rows: List<BrowseCard>,
    libraryEntries: List<LibraryEntry>,
    rankedTaste: List<ListeningSignal>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
    onPlay: (ExtensionMediaSelection, List<ExtensionMediaSelection>) -> Unit,
    onOpen: (ExtensionMediaSelection) -> Unit,
    onOpenExtensions: () -> Unit,
    onSelectPanel: (MusicLocal) -> Unit,
) {
    val queue = remember(rows) { rows.map { selection(it, ContentType.MUSIC) } }
    val playFromQueue: (ExtensionMediaSelection) -> Unit = { track -> onPlay(track, queue) }
    when (panel) {
        MusicLocal.HOME -> MusicHome(rows, rankedTaste, selection, playFromQueue, onOpen, onOpenExtensions, onSelectPanel)
        MusicLocal.DISCOVER -> MusicDiscover(rows, selection, playFromQueue)
        MusicLocal.LIBRARY -> MusicLibrary(libraryEntries, playFromQueue)
    }
}

@Composable
private fun MusicHome(
    rows: List<BrowseCard>, rankedTaste: List<ListeningSignal>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
    onPlay: (ExtensionMediaSelection) -> Unit, onOpen: (ExtensionMediaSelection) -> Unit,
    onOpenExtensions: () -> Unit, onSelectPanel: (MusicLocal) -> Unit,
) {
    var optionsOpen by remember { mutableStateOf(false) }
    val rankedRows = remember(rows, rankedTaste) {
        val order = rankedTaste.mapIndexed { index, signal -> signal.artistName.trim().lowercase() to index }.toMap()
        rows.sortedBy { card -> order[card.subtitle.substringBefore(" · ").trim().lowercase()] ?: Int.MAX_VALUE }
    }
    val recentRows = remember(rows, rankedTaste) {
        rankedTaste
            .sortedByDescending { it.lastPlayedEpochMs }
            .flatMap { signal ->
                rows.filter { card -> card.subtitle.substringBefore(" · ").trim().equals(signal.artistName.trim(), ignoreCase = true) }
            }
            .distinctBy { it.id }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Music", fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Your listening space", color = SoraMuted, fontSize = 11.sp)
                }
                Box {
                    IconButton(onClick = { optionsOpen = true }) { Icon(Icons.Rounded.MoreVert, "Music options") }
                    DropdownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Discover") },
                            leadingIcon = { Icon(Icons.Rounded.Explore, null) },
                            onClick = { optionsOpen = false; onSelectPanel(MusicLocal.DISCOVER) },
                        )
                        DropdownMenuItem(
                            text = { Text("Your Music") },
                            leadingIcon = { Icon(Icons.Rounded.LibraryMusic, null) },
                            onClick = { optionsOpen = false; onSelectPanel(MusicLocal.LIBRARY) },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Music sources") },
                            leadingIcon = { Icon(Icons.Rounded.Extension, null) },
                            onClick = { optionsOpen = false; onOpenExtensions() },
                        )
                    }
                }
            }
        }
        item { MusicQuickGrid(rows.take(6), selection, onPlay) }
        if (rankedRows.isNotEmpty()) {
            item { MusicSectionTitle("Made for you", if (rankedTaste.isEmpty()) "Fresh picks from your music source" else "Ordered from your listening history", null) }
            item { MusicSquareRail(rankedRows.take(8), selection, onPlay) }
        }
        if (recentRows.isNotEmpty()) {
            item { MusicSectionTitle("From artists you played recently", "Pulled from your actual listening history", null) }
            item { MusicSquareRail(recentRows.take(8), selection, onPlay) }
        }
        if (rankedTaste.isNotEmpty()) {
            item { MusicSectionTitle("Your top artists", "Based on your listening history", null); ArtistRail(rankedRows) }
            item { MusicSectionTitle("Your rotation", "Artists and songs you return to", null); MusicTrackList(rankedRows.take(8), selection, onPlay) }
        }
    }
}

@Composable
private fun MusicDiscover(rows: List<BrowseCard>, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onPlay: (ExtensionMediaSelection) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            Surface(color = Color(0xFF242118), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("DISCOVER", color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Text("Something new for tonight.", fontSize = 27.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 16.dp))
                    Text("Fresh music from your installed source, ready to explore.", color = SoraMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { rows.firstOrNull()?.let { onPlay(selection(it, ContentType.MUSIC)) } }, enabled = rows.isNotEmpty(), shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(top = 18.dp)) {
                        Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Play from Discover")
                    }
                }
            }
        }
        if (rows.isEmpty()) {
            item { HintLine("Install or refresh a Music source to fill Discover.") }
        } else {
            item { MusicSectionTitle("Fresh picks", "Music returned by your active source", null); MusicSquareRail(rows, selection, onPlay) }
            item { MusicSectionTitle("Top songs", "From the current source feed", null); MusicTrackList(rows.take(10), selection, onPlay, numbered = true) }
        }
    }
}

@Composable
private fun MusicLibrary(
    libraryEntries: List<LibraryEntry>,
    onPlay: (ExtensionMediaSelection) -> Unit,
) {
    val savedMusic = libraryEntries.filter { it.contentType == ContentType.MUSIC }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text("YOUR MUSIC", color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("Everything you kept.", fontSize = 25.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 3.dp))
                Text(
                    if (savedMusic.isEmpty()) "Saved songs will appear here." else "${savedMusic.size} saved ${if (savedMusic.size == 1) "song" else "songs"}",
                    color = SoraMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (savedMusic.isEmpty()) {
            item { HintLine("Save a song from its player or details screen to keep it in Your Music.") }
        } else {
            item { MusicSectionTitle("Saved songs", "Stored in your Sora library", null) }
            items(savedMusic, key = { it.id }) { entry ->
                val track = entry.toMediaSelection()
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (track != null) Modifier.clickable { onPlay(track) } else Modifier)
                        .padding(horizontal = 18.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Poster(entry.artworkUrl, entry.label, Modifier.size(48.dp), 5)
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                        Text(entry.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(entry.mediaSubtitle.ifBlank { entry.detail }, color = SoraMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (track != null) Icon(Icons.Rounded.PlayArrow, "Play ${entry.label}", tint = SoraMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun MemeSurface(
    rows: List<BrowseCard>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
    onOpen: (ExtensionMediaSelection) -> Unit,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenExtensions: () -> Unit,
) {
    if (rows.isEmpty() && loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = SoraAccent, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                Text("Loading meme feed…", color = SoraMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        return
    }
    if (rows.isEmpty()) {
        val missingSource = error?.contains("installed", ignoreCase = true) == true
        val noSearchResults = error?.contains("matched your search", ignoreCase = true) == true
        val emptyFeed = error?.contains("No image posts", ignoreCase = true) == true
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
                Icon(
                    if (missingSource) Icons.Rounded.ExtensionOff else if (noSearchResults) Icons.Rounded.SearchOff else Icons.Rounded.CloudOff,
                    null,
                    tint = SoraMuted,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    when {
                        missingSource -> "No meme source installed"
                        noSearchResults -> "No meme results"
                        emptyFeed -> "Nothing new right now"
                        else -> "Meme feed unavailable"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    error ?: "No posts are available right now.",
                    color = SoraMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Row(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!missingSource) {
                        TextButton(onClick = onRetry) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Retry")
                        }
                    }
                    TextButton(onClick = onOpenExtensions) {
                        Icon(Icons.Rounded.Extension, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(if (missingSource) "Manage extensions" else "Sources")
                    }
                }
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
        items(rows, key = { it.id }) { card ->
            Surface(color = Color(0xFFF0EDE5), contentColor = Color(0xFF141412), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clickable { onOpen(selection(card, ContentType.MEME)) }) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(card.subtitle.ifBlank { "meme source" }, fontSize = 11.sp, color = Color(0xFF656158)); Icon(Icons.Rounded.MoreHoriz, null) }
                    Text(card.title, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                    Box(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFFC5C0B3))) { if (!card.artworkUrl.isNullOrBlank()) AsyncImage(card.artworkUrl, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Open post", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsSurface(rows: List<BrowseCard>, type: ContentType, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onOpen: (ExtensionMediaSelection) -> Unit, onPlayMusic: (ExtensionMediaSelection, List<ExtensionMediaSelection>) -> Unit) {
    val musicQueue = remember(rows, type) { if (type == ContentType.MUSIC) rows.map { selection(it, type) } else emptyList() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
        item { MediaSectionTitle("Results", type.label) }
        items(rows, key = { it.id }) { card ->
            Row(Modifier.fillMaxWidth().clickable { if (type == ContentType.MUSIC) onPlayMusic(selection(card, type), musicQueue) else onOpen(selection(card, type)) }.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Poster(card.artworkUrl, card.title, Modifier.size(width = 58.dp, height = if (type == ContentType.MUSIC) 58.dp else 76.dp), if (type == ContentType.MUSIC) 7 else 5)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(type.label.uppercase(), color = SoraAccent, fontSize = 8.sp, fontWeight = FontWeight.Black); Text(card.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text(card.subtitle, color = SoraMuted, fontSize = 10.sp, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun EmptyFeatureShell(type: ContentType) {
    val kicker = when (type) {
        ContentType.ANIME -> "FEATURED ANIME"
        ContentType.MANGA -> "FEATURED MANGA"
        ContentType.MOVIE -> "FEATURED MOVIE"
        ContentType.TV -> "FEATURED SERIES"
        ContentType.MUSIC -> "FEATURED MUSIC"
        ContentType.MEME -> "FEATURED"
    }
    Box(
        Modifier.fillMaxWidth().height(260.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF262621), Color(0xFF171714), SoraBg)))
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(kicker, color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Box(Modifier.padding(top = 10.dp).width(236.dp).height(28.dp).background(SoraSurfaceHigh, RoundedCornerShape(6.dp)))
            Box(Modifier.padding(top = 9.dp).width(292.dp).height(10.dp).background(SoraSurfaceHigh, RoundedCornerShape(5.dp)))
            Box(Modifier.padding(top = 6.dp).width(220.dp).height(10.dp).background(SoraSurfaceHigh, RoundedCornerShape(5.dp)))
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = Color.White.copy(alpha = .12f), shape = RoundedCornerShape(6.dp)) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (type == ContentType.MANGA) Icons.Rounded.MenuBook else Icons.Rounded.PlayArrow, null, tint = SoraMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (type == ContentType.MANGA) "Read" else "Continue", color = SoraMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(color = SoraSurfaceHigh, shape = RoundedCornerShape(6.dp)) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null, tint = SoraMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Library", color = SoraMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamFeature(card: BrowseCard, kicker: String, body: String, primaryLabel: String, selection: ExtensionMediaSelection, isSaved: Boolean, onToggleSaved: (ExtensionMediaSelection) -> Unit, onOpen: (ExtensionMediaSelection) -> Unit) {
    Box(Modifier.fillMaxWidth().height(420.dp).clickable { onOpen(selection) }) {
        Poster(card.artworkUrl, card.title, Modifier.fillMaxSize(), 0)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, SoraBg))))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(kicker.uppercase(), color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(card.title, fontSize = 31.sp, lineHeight = 33.sp, fontWeight = FontWeight.Black, maxLines = 2, modifier = Modifier.padding(top = 7.dp))
            Text(body, color = Color.White.copy(alpha = .78f), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 5.dp))
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpen(selection) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(6.dp)) { Icon(if (selection.type == ContentType.MANGA) Icons.Rounded.MenuBook else Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text(primaryLabel, fontWeight = FontWeight.Bold) }
                FilledTonalButton(onClick = { onToggleSaved(selection) }, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xCC282824), contentColor = Color.White)) { Icon(if (isSaved) Icons.Rounded.Check else Icons.Rounded.Add, null); Spacer(Modifier.width(5.dp)); Text("Library") }
            }
        }
    }
}

@Composable
private fun MediaSectionTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 10.dp)) { Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = SoraMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)) }
}

@Composable
private fun PortraitRail(rows: List<BrowseCard>, type: ContentType, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onOpen: (ExtensionMediaSelection) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (rows.isEmpty()) {
            items(6) { index ->
                Column(Modifier.width(116.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(if (index % 2 == 0) SoraSurfaceHigh else SoraSurface, RoundedCornerShape(7.dp)))
                    Box(Modifier.padding(top = 7.dp).width(82.dp).height(8.dp).background(SoraSurfaceHigh, RoundedCornerShape(4.dp)))
                    Box(Modifier.padding(top = 5.dp).width(56.dp).height(6.dp).background(SoraSurface, RoundedCornerShape(4.dp)))
                }
            }
        } else {
            items(rows.take(12), key = { "p-${type.name}-${it.id}" }) { card ->
                Column(Modifier.width(116.dp).clickable { onOpen(selection(card, type)) }) {
                    Poster(card.artworkUrl, card.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f), 7)
                    Text(card.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun TopTenRail(rows: List<BrowseCard>, type: ContentType, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onOpen: (ExtensionMediaSelection) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (rows.isEmpty()) {
            items(10) { index ->
                Row(Modifier.height(178.dp), verticalAlignment = Alignment.Bottom) {
                    Text("${index + 1}", color = Color(0xFF4E4C47), fontSize = 70.sp, lineHeight = 70.sp, fontWeight = FontWeight.Black, letterSpacing = (-5).sp)
                    Box(Modifier.width(96.dp).fillMaxHeight().background(if (index % 2 == 0) SoraSurfaceHigh else SoraSurface, RoundedCornerShape(5.dp)))
                }
            }
        } else {
            items(rows.take(10).withIndex().toList(), key = { "top-${type.name}-${it.value.id}" }) { ranked ->
                Row(Modifier.height(178.dp).clickable { onOpen(selection(ranked.value, type)) }, verticalAlignment = Alignment.Bottom) {
                    Text("${ranked.index + 1}", color = Color(0xFF6F6C64), fontSize = 70.sp, lineHeight = 70.sp, fontWeight = FontWeight.Black, letterSpacing = (-5).sp)
                    Poster(ranked.value.artworkUrl, ranked.value.title, Modifier.width(96.dp).fillMaxHeight(), 5)
                }
            }
        }
    }
}

@Composable
private fun ProgressLandscapeRail(entries: List<MediaProgressEntry>, onResume: (MediaProgressEntry) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items(entries.take(8), key = { "progress-${it.extensionPackage}-${it.sourceId}-${it.mediaId}" }) { entry ->
            Column(Modifier.width(190.dp).clickable { onResume(entry) }) {
                Box(Modifier.fillMaxWidth().height(107.dp).clip(RoundedCornerShape(7.dp)).background(SoraSurface)) {
                    if (!entry.artworkUrl.isNullOrBlank()) AsyncImage(entry.artworkUrl, entry.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    LinearProgressIndicator(
                        progress = { entry.progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                        color = SoraAccent,
                        trackColor = Color(0xFF555248),
                    )
                }
                Text(entry.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "${entry.itemLabel} · ${(entry.progress * 100f).toInt().coerceIn(0, 100)}%",
                    color = SoraMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ContinueLandscapeRail(entries: List<LibraryEntry>, onOpen: (ExtensionMediaSelection) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items(entries.take(8), key = { it.id }) { entry ->
            Column(Modifier.width(190.dp).clickable { entry.toMediaSelection()?.let(onOpen) }) {
                Box(Modifier.fillMaxWidth().height(107.dp).clip(RoundedCornerShape(7.dp)).background(SoraSurface)) {
                    if (!entry.artworkUrl.isNullOrBlank()) AsyncImage(entry.artworkUrl, entry.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Text(entry.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 6.dp)); Text(entry.detail, color = SoraMuted, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun NewHotStack(rows: List<BrowseCard>, type: ContentType, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onOpen: (ExtensionMediaSelection) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (rows.isEmpty()) {
            repeat(3) { index ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(47.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SOON", color = SoraFaint, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        Text("${index + 1}", color = SoraFaint, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    }
                    Box(Modifier.size(width = 54.dp, height = 74.dp).background(SoraSurfaceHigh, RoundedCornerShape(6.dp)))
                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                        Box(Modifier.width(64.dp).height(7.dp).background(SoraSurfaceHigh, RoundedCornerShape(4.dp)))
                        Box(Modifier.padding(top = 8.dp).fillMaxWidth(.62f).height(10.dp).background(SoraSurfaceHigh, RoundedCornerShape(4.dp)))
                        Box(Modifier.padding(top = 6.dp).fillMaxWidth(.42f).height(7.dp).background(SoraSurface, RoundedCornerShape(4.dp)))
                    }
                }
            }
        } else {
            rows.forEachIndexed { index, card ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(selection(card, type)) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(47.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("SOON", color = SoraMuted, fontSize = 8.sp, fontWeight = FontWeight.Black); Text("${index + 1}", fontSize = 19.sp, fontWeight = FontWeight.Black) }
                    Poster(card.artworkUrl, card.title, Modifier.size(width = 54.dp, height = 74.dp), 6)
                    Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(if (index == 0) "NEW NOW" else "COMING SOON", color = SoraAccent, fontSize = 8.sp, fontWeight = FontWeight.Black); Text(card.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun GenreRail(labels: List<String>) { LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(labels) { label -> Surface(color = SoraSurfaceHigh, shape = RoundedCornerShape(999.dp)) { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) } } } }

@Composable
private fun GenreGrid(labels: List<String>) { Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { labels.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { label -> Surface(color = SoraSurfaceHigh, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f).height(68.dp)) { Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomStart) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold) } } }; if (row.size == 1) Spacer(Modifier.weight(1f)) } } } }

@Composable
private fun MusicQuickGrid(rows: List<BrowseCard>, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onPlay: (ExtensionMediaSelection) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (rows.isEmpty()) {
            repeat(3) { rowIndex ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(2) { columnIndex ->
                        Row(Modifier.weight(1f).height(58.dp).background(SoraSurfaceHigh, RoundedCornerShape(7.dp)), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(58.dp).background(if ((rowIndex + columnIndex) % 2 == 0) SoraSurface else Color(0xFF242420), RoundedCornerShape(7.dp)))
                            Box(Modifier.padding(horizontal = 9.dp).weight(1f).height(8.dp).background(SoraSurface, RoundedCornerShape(4.dp)))
                        }
                    }
                }
            }
        } else {
            rows.chunked(2).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunk.forEach { card ->
                        Row(Modifier.weight(1f).height(58.dp).background(SoraSurfaceHigh, RoundedCornerShape(7.dp)).clickable { onPlay(selection(card, ContentType.MUSIC)) }, verticalAlignment = Alignment.CenterVertically) {
                            Poster(card.artworkUrl, card.title, Modifier.size(58.dp), 7)
                            Text(card.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 9.dp).weight(1f))
                        }
                    }
                    if (chunk.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MusicSectionTitle(title: String, subtitle: String, action: String?) { Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 22.dp, bottom = 9.dp), verticalAlignment = Alignment.Bottom) { Column(Modifier.weight(1f)) { Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = SoraMuted, fontSize = 10.sp) }; if (action != null) Text(action, color = SoraMuted, fontSize = 10.sp) } }

@Composable
private fun MusicMixRail(rows: List<BrowseCard>, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onPlay: (ExtensionMediaSelection) -> Unit) {
    val labels = listOf("Night Drive", "Soft Static", "Repeat Mix")
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(3) { index ->
            val card = rows.getOrNull(index)
            Column(Modifier.width(154.dp).clickable { card?.let { onPlay(selection(it, ContentType.MUSIC)) } }) {
                Box(Modifier.size(154.dp).background(listOf(Color(0xFF6D5D31), Color(0xFF4E4A55), Color(0xFF5D493F))[index], RoundedCornerShape(6.dp)).padding(14.dp)) { Text("0${index + 1}\n${labels[index].replace(" ", "\n")}", color = Color.White, fontSize = 20.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.BottomStart)) }
                Text(labels[index], fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 7.dp)); Text(card?.subtitle ?: "Made for you", color = SoraMuted, fontSize = 9.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun MusicSquareRail(rows: List<BrowseCard>, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onPlay: (ExtensionMediaSelection) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (rows.isEmpty()) {
            items(6) { index ->
                Column(Modifier.width(146.dp)) {
                    Box(Modifier.size(146.dp).background(if (index % 2 == 0) SoraSurfaceHigh else SoraSurface, RoundedCornerShape(6.dp)))
                    Box(Modifier.padding(top = 7.dp).width(90.dp).height(8.dp).background(SoraSurfaceHigh, RoundedCornerShape(4.dp)))
                    Box(Modifier.padding(top = 5.dp).width(58.dp).height(6.dp).background(SoraSurface, RoundedCornerShape(4.dp)))
                }
            }
        } else {
            items(rows.take(10), key = { "sq-${it.id}" }) { card ->
                Column(Modifier.width(146.dp).clickable { onPlay(selection(card, ContentType.MUSIC)) }) {
                    Poster(card.artworkUrl, card.title, Modifier.size(146.dp), 6)
                    Text(card.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 7.dp))
                    Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ArtistRail(rows: List<BrowseCard>) {
    val artists = rows.mapNotNull { it.subtitle.substringBefore(" · ").takeIf(String::isNotBlank) }.distinct().take(8)
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (artists.isEmpty()) {
            items(6) { index ->
                Column(Modifier.width(94.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(86.dp).background(if (index % 2 == 0) SoraSurfaceHigh else SoraSurface, CircleShape))
                    Box(Modifier.padding(top = 7.dp).width(54.dp).height(7.dp).background(SoraSurfaceHigh, RoundedCornerShape(4.dp)))
                }
            }
        } else {
            items(artists) { artist ->
                Column(Modifier.width(94.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(86.dp).background(SoraSurfaceHigh, CircleShape), contentAlignment = Alignment.Center) { Text(artist.take(1), fontSize = 28.sp, fontWeight = FontWeight.Black) }
                    Text(artist, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun MusicTrackList(rows: List<BrowseCard>, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onPlay: (ExtensionMediaSelection) -> Unit, numbered: Boolean = false) {
    Column {
        if (rows.isEmpty()) {
            repeat(5) { index ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (numbered) Text("${index + 1}", color = SoraFaint, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    Box(Modifier.size(48.dp).background(SoraSurfaceHigh, RoundedCornerShape(5.dp)))
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                        Box(Modifier.fillMaxWidth(.54f).height(8.dp).background(SoraSurfaceHigh, RoundedCornerShape(4.dp)))
                        Box(Modifier.padding(top = 6.dp).fillMaxWidth(.34f).height(6.dp).background(SoraSurface, RoundedCornerShape(4.dp)))
                    }
                }
            }
        } else {
            rows.forEachIndexed { index, card ->
                Row(Modifier.fillMaxWidth().clickable { onPlay(selection(card, ContentType.MUSIC)) }.padding(horizontal = 18.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (numbered) Text("${index + 1}", color = SoraMuted, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    Poster(card.artworkUrl, card.title, Modifier.size(48.dp), 5)
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) { Text(card.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun MusicShortcut(label: String, icon: ImageVector, modifier: Modifier) { Surface(color = SoraSurfaceHigh, shape = RoundedCornerShape(10.dp), modifier = modifier.height(72.dp)) { Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.SpaceBetween) { Icon(icon, null, modifier = Modifier.size(20.dp)); Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) } } }

@Composable
private fun Poster(url: String?, title: String, modifier: Modifier, radius: Int) { Box(modifier.clip(RoundedCornerShape(radius.dp)).background(SoraSurfaceHigh), contentAlignment = Alignment.Center) { if (!url.isNullOrBlank()) AsyncImage(url, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Text(title.take(1), color = SoraMuted, fontSize = 28.sp, fontWeight = FontWeight.Black) } }

@Composable
private fun MissingMediaSource(label: String, onOpenExtensions: () -> Unit) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) { Icon(Icons.Rounded.ExtensionOff, null, tint = SoraMuted, modifier = Modifier.size(38.dp)); Text("No $label source installed", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)); Text("Add one from More → Extensions.", color = SoraMuted, fontSize = 11.sp); TextButton(onClick = onOpenExtensions) { Text("Manage extensions") } } } }

@Composable
private fun HintLine(text: String) { Text(text, color = SoraMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) }

private fun parseBrowse(raw: String, sourceId: String, packageName: String): List<BrowseCard> = runCatching { val arr = JSONArray(raw); buildList { for (i in 0 until arr.length()) { val item = arr.getJSONObject(i); add(BrowseCard(item.optString("id"), item.optString("title", "Untitled"), item.optString("subtitle"), artworkFrom(item), sourceId, packageName)) } } }.getOrDefault(emptyList())
private fun artworkFrom(item: JSONObject): String? = listOf("artworkUrl", "poster", "posterUrl", "image", "imageUrl", "thumbnail", "cover", "coverUrl").firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.startsWith("http://") || it.startsWith("https://") } }
private fun typeKey(type: ContentType): String = when (type) { ContentType.MOVIE -> "movie"; ContentType.MEME -> "memes"; else -> type.name.lowercase() }
private fun mediaDescription(item: MediaDestination): String = when (item) { MediaDestination.ANIME_MANGA -> "Watch, read and move between adaptations"; MediaDestination.MOVIES_TV -> "Films, series, watchlists and continue watching"; MediaDestination.MUSIC -> "Your listening space and persistent player"; MediaDestination.MEMES -> "Feeds, saved posts and source extensions"; MediaDestination.BIBLE -> "Reading, bookmarks, notes and history" }
