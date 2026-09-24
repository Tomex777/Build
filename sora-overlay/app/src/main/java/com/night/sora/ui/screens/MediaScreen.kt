Warning: truncated output (original token count: 17635)
Total output lines: 1241

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

import android.net.ConnectivityManager
import android.net.Network
import android.content.Intent
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
private fun com.night.sora.data.CachedMediaSnapshot?.orEmptySnapshot() = this ?: com.night.sora.data.CachedMediaSnapshot(emptyList())
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
    initialSelectedType: ContentType = ContentType.ANIME,
    onSelectedTypeChange: (ContentType) -> Unit = {},
    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenBible: () -> Unit,
    onOpenDetails: (ExtensionMediaSelection) -> Unit,
    onResumeProgress: (MediaProgressEntry) -> Unit,
    onPlayMusic: (ExtensionMediaSelection, List<ExtensionMediaSelection>) -> Unit,
) {
    val context = LocalContext.current
    val mediaCache = remember { MediaCatalogCache(context.applicationContext) }
    var destination by remember { mutableStateOf(MediaDestination.ANIME_MANGA) }
    var selectedType by remember { mutableStateOf(initialSelectedType) }
    var musicLocal by remember { mutableStateOf(MusicLocal.HOME) }
    var rows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }
    var popularRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }
    var seasonRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }
    var upcomingRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }
    var topRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }
    var discoverRows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var switchOpen by remember { mutableStateOf(false) }
    var networkEpoch by remember { mutableIntStateOf(0) }
    var refreshEpoch by remember { mutableIntStateOf(0) }
    var primaryLoading by remember { mutableStateOf(false) }
    var primaryError by remember { mutableStateOf<String?>(null) }
    var primaryCacheFetchedAt by remember { mutableLongStateOf(0L) }
    var feedFailures by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedType) {
        if (selectedType == ContentType.ANIME || selectedType == ContentType.MANGA) {
            onSelectedTypeChange(selectedType)
        }
    }

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
        if (next == MediaDestination.BIBLE) {
            onOpenBible()
            return
        }
        destination = next
        query = ""
        selectedType = when (next) {
            MediaDestination.ANIME_MANGA -> ContentType.ANIME
            MediaDestination.MOVIES_TV -> ContentType.MOVIE
            MediaDestination.MUSIC -> ContentType.MUSIC
            MediaDestination.MEMES -> ContentType.MEME
            MediaDestination.BIBLE -> selectedType
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

    fun animeMangaCatalogSource(requestType: ContentType): Pair<InstalledExtension, com.night.sora.extension.api.SourceDescriptor>? {
        if (requestType != ContentType.ANIME && requestType != ContentType.MANGA) return null
        val key = typeKey(requestType)
        val ext = extensions.firstOrNull { it.error == null && it.declaredId == "sora.core.anilist" } ?: return null
        val source = ext.descriptor?.sources?.firstOrNull { key in it.contentTypes } ?: return null
        return ext to source
    }

    fun loadCatalogFeed(feed: String, requestType: ContentType, onResult: (Result<List<BrowseCard>>) -> Unit) {
        val pair = animeMangaCatalogSource(requestType)
        if (pair == null) {
            onResult(Result.failure(IllegalStateException("Sora Anime & Manga catalog is unavailable")))
            return
        }
        val (ext, source) = pair
        val payload = JSONObject()
            .put("sourceId", source.id)
            .put("type", typeKey(requestType))
            .put("feed", feed)
            .toString()
        manager.call(ext, ExtensionContract.Method.BROWSE, payload) { result ->
            onResult(result.mapCatching { parseBrowse(it, source.id, ext.packageName) })
        }
    }

    fun load(search: String) {
        val requestType = selectedType
        val requestDestination = destination
        val requestQuery = search.trim()

        if (requestType == ContentType.ANIME || requestType == ContentType.MANGA) {
            val pair = animeMangaCatalogSource(requestType)
            val cached = if (requestQuery.isBlank()) {
                val current = mediaCache.readSnapshot(requestType, "current")
                if (current.rows.isNotEmpty()) current else mediaCache.readSnapshot(requestType)
            } else null

            if (requestQuery.isBlank()) {
                rows = cached.orEmptySnapshot().rows.map { it.toBrowseCard() }
                primaryCacheFetchedAt = cached?.fetchedAt ?: 0L
            } else {
                rows = emptyList()
                primaryCacheFetchedAt = 0L
            }
            primaryLoading = true
            primaryError = null

            if (pair == null) {
                primaryLoading = false
                primaryError = "The built-in AniList catalog is unavailable."
                return
            }
            val (ext, source) = pair
            val method = if (requestQuery.isBlank()) ExtensionContract.Method.BROWSE else ExtensionContract.Method.SEARCH
            val payload = JSONObject()
                .put("sourceId", source.id)
                .put("type", typeKey(requestType))
                .put("query", requestQuery)
                .toString()
            manager.call(ext, method, payload) { result ->
                if (selectedType != requestType || destination != requestDestination || query.trim() != requestQuery) return@call
                primaryLoading = false
                result.mapCatching { parseBrowse(it, source.id, ext.packageName) }
                    .onSuccess { fresh ->
                        rows = fresh
                        primaryError = null
                        if (requestQuery.isBlank() && fresh.isNotEmpty()) {
                            mediaCache.write(requestType, "current", fresh.map { it.toCachedRecord() })
                            primaryCacheFetchedAt = System.currentTimeMillis()
                        }
                    }
                    .onFailure {
                        primaryError = "AniList did not return live ${requestType.label} data. Please retry in a moment."
                    }
            }
            return
        }

        primaryLoading = false
        primaryError = null
        val cached = if (requestQuery.isBlank()) mediaCache.read(requestType) else mediaCache.search(requestType, requestQuery)
        rows = cached.map { it.toBrowseCard() }
        val key = typeKey(requestType)
        val providers = extensions.flatMap { ext ->
            ext.descriptor?.sources.orEmpty()
                .filter { source -> ext.isCatalogProvider() && key in source.contentTypes }
                .map { source -> ext to source }
        }
        if (providers.isEmpty()) {
            primaryError = "No compatible ${requestType.label.lowercase()} catalog source is installed."
            return
        }

        val method = if (requestQuery.isBlank()) ExtensionContract.Method.BROWSE else ExtensionContract.Method.SEARCH
        primaryLoading = rows.isEmpty()
        val failures = MutableList(providers.size) { false }
        val collected = MutableList(providers.size) { emptyList<BrowseCard>() }
        var completed = 0
        providers.forEachIndexed { index, (ext, source) ->
            val payload = JSONObject().put("sourceId", source.id).put("type", key).put("query", requestQuery).toString()
            manager.call(ext, method, payload) { result ->
                failures[index] = result.isFailure
                collected[index] = result.getOrNull()?.let { parseBrowse(it, source.id, ext.packageName) }.orEmpty()
                completed++
                if (completed == providers.size) {
                    val fresh = collected.flatten().distinctBy { it.title.trim().lowercase() }
                    if (requestQuery.isBlank() && fresh.isNotEmpty()) mediaCache.write(requestType, fresh.map { it.toCachedRecord() })
                    if (selectedType == requestType && destination == requestDestination && query.trim() == requestQuery) {
                        primaryLoading = false
                        if (fresh.isNotEmpty()) {
                            rows = fresh
                            primaryError = null
                        } else if (failures.all { it }) {
                            primaryError = "${requestType.label} sources could not load right now. Check the source extension and retry."
                        } else {
                            primaryError = null
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedType, extensions, query, destination, networkEpoch, refreshEpoch) {
        if (query.isNotBlank()) delay(450)
        load(query)
    }

    LaunchedEffect(selectedType, extensions, destination, networkEpoch, refreshEpoch) {
        popularRows = emptyList()
        seasonRows = emptyList()
        upcomingRows = emptyList()
        topRows = emptyList()
        discoverRows = emptyList()
        feedFailures = 0
        if (destination != MediaDestination.ANIME_MANGA) return@LaunchedEffect
        if (selectedType != ContentType.ANIME && selectedType != ContentType.MANGA) return@LaunchedEffect
        val requestType = selectedType

        fun refreshFeed(feed: String, setRows: (List<BrowseCard>) -> Unit) {
            val snapshot = mediaCache.readSnapshot(requestType, feed)
            if (snapshot.rows.isNotEmpty()) setRows(snapshot.rows.map { it.toBrowseCard() })
            loadCatalogFeed(feed, requestType) { result ->
                if (destination != MediaDestination.ANIME_MANGA || selectedType != requestType) return@loadCatalogFeed
                result.onSuccess { fresh ->
                    if (fresh.isNotEmpty()) {
                        setRows(fresh)
                        mediaCache.write(requestType, feed, fresh.map { it.toCachedRecord() })
                    }
                }.onFailure { feedFailures++ }
            }
        }

        refreshFeed("popular") { popularRows = it }
        refreshFeed("top") { topRows = it }
        if (requestType == ContentType.ANIME) {
            refreshFeed("season") { seasonRows = it }
            refreshFeed("upcoming") { upcomingRows = it }
        } else {
            refreshFeed("recent") { discoverRows = it }
        }
    }

    Column(modifier.fillMaxSize()) {
        MediaTopBar(
            title = destination.label,
            searchOpen = searchOpen,
            query = query,
            selectedType = selectedType,
            searchEnabled = true,
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
            query.isNotBlank() -> SearchResultsSurface(
                rows = rows, type = selectedType, query = query, loading = primaryLoading, error = primaryError,
                selection = ::selection, onOpen = onOpenDetails, onPlayMusic = onPlayMusic, onRetry = { refreshEpoch++ },
            )
            destination == MediaDestination.ANIME_MANGA -> AnimeMangaSurface(
                type = selectedType, rows = rows, popularRows = popularRows, seasonRows = seasonRows,
                upcomingRows = upcomingRows, topRows = topRows, discoverRows = discoverRows,
                loading = primaryLoading, error = primaryError, cacheFetchedAt = primaryCacheFetchedAt, feedFailures = feedFailures,
                libraryEntries = libraryEntries, progressEntries = progressEntries, selection = ::selection, isSaved = isSaved,
                onToggleSaved = onToggleSaved, onOpen = onOpenDetails, onResume = onResumeProgress, onRetry = { refreshEpoch++ },
            )
            destination == MediaDestination.MOVIES_TV -> MovieTvSurface(
                type = selectedType, rows = rows, libraryEntries = libraryEntries, progressEntries = progressEntries,
                selection = ::selection, isSaved = isSaved, onToggleSaved = onToggleSaved, onOpen = onOpenDetails,
                onResume = onResumeProgress, loading = primaryLoading, error = primaryError, onRetry = { refreshEpoch++ },
            )
            destination == MediaDestination.MUSIC -> MusicSurface(
                panel = musicLocal, rows = rows, libraryEntries = libraryEntries, rankedTaste = rankedTaste,
                selection = ::selection, onPlay = onPlayMusic, onOpen = onOpenDetails,
                onOpenExtensions = onOpenExtensions, onSelectPanel = { musicLocal = it },
                loading = primaryLoading, error = primaryError, onRetry = { refreshEpoch++ },
            )
            destination == MediaDestination.MEMES -> MemeSurface(
                rows = rows, selection = ::selection, isSaved = isSaved, onToggleSaved = onToggleSaved, onOpen = onOpenDetails,
                loading = primaryLoading, error = primaryError, onRetry = { refreshEpoch++ }, onOpenExtensions = onOpenExtensions,
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
            Sp…7635 tokens truncated… }) {
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
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { card ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(selection(card, type)) }, verticalAlignment = Alignment.CenterVertically) {
                Poster(card.artworkUrl, card.title, Modifier.size(width = 62.dp, height = 88.dp), 6)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("UPCOMING", color = SoraAccent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                    Text(card.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                    if (card.subtitle.isNotBlank()) Text(card.subtitle, color = SoraMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = SoraFaint, modifier = Modifier.size(18.dp))
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
private fun CatalogNotice(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CloudOff, null, tint = SoraMuted, modifier = Modifier.size(16.dp))
        Text(text, color = SoraMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun CatalogFailure(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 24.dp)) {
        Icon(Icons.Rounded.CloudOff, null, tint = SoraMuted, modifier = Modifier.size(28.dp))
        Text("Catalog unavailable", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(message, color = SoraMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 5.dp))
        TextButton(onClick = onRetry, contentPadding = PaddingValues(vertical = 8.dp)) { Text("Retry") }
    }
}

@Composable
private fun CatalogSourceState(
    label: String,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenExtensions: () -> Unit,
) {
    if (loading) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Loading $label from installed sources…", color = SoraMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp))
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp)) {
            Text(error ?: "No $label items were returned by the installed source feed.", color = SoraMuted, fontSize = 11.sp, lineHeight = 16.sp)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onRetry) { Text("Retry") }
                TextButton(onClick = onOpenExtensions) { Text("Manage sources") }
            }
        }
    }
}

private fun cacheAgeSuffix(fetchedAt: Long): String {
    if (fetchedAt <= 0L) return ""
    val minutes = ((System.currentTimeMillis() - fetchedAt).coerceAtLeast(0L) / 60_000L)
    return when {
        minutes < 1 -> " from moments ago"
        minutes < 60 -> " from ${minutes}m ago"
        minutes < 1_440 -> " from ${minutes / 60}h ago"
        else -> " from ${minutes / 1_440}d ago"
    }
}

@Composable
private fun HintLine(text: String) { Text(text, color = SoraMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) }

private fun parseBrowse(raw: String, sourceId: String, packageName: String): List<BrowseCard> = runCatching { val arr = JSONArray(raw); buildList { for (i in 0 until arr.length()) { val item = arr.getJSONObject(i); add(BrowseCard(item.optString("id"), item.optString("title", "Untitled"), item.optString("subtitle"), artworkFrom(item), sourceId, packageName)) } } }.getOrDefault(emptyList())
private fun artworkFrom(item: JSONObject): String? = listOf("artworkUrl", "poster", "posterUrl", "image", "imageUrl", "thumbnail", "cover", "coverUrl").firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.startsWith("http://") || it.startsWith("https://") } }
private fun typeKey(type: ContentType): String = when (type) { ContentType.MOVIE -> "movie"; ContentType.MEME -> "memes"; else -> type.name.lowercase() }
private fun mediaDescription(item: MediaDestination): String = when (item) { MediaDestination.ANIME_MANGA -> "Watch, read and move between adaptations"; MediaDestination.MOVIES_TV -> "Films, series, watchlists and continue watching"; MediaDestination.MUSIC -> "Your listening space and persistent player"; MediaDestination.MEMES -> "Feeds, saved posts and source extensions"; MediaDestination.BIBLE -> "Reading, bookmarks, notes and history" }
