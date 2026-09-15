@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.night.sora.ui.screens

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.night.sora.extension.ExtensionManager
import com.night.sora.extension.InstalledExtension
import com.night.sora.extension.api.ExtensionContract
import com.night.sora.model.ContentType
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.model.LibraryEntry
import com.night.sora.model.ListeningSignal
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

@Composable
fun MediaScreen(
    modifier: Modifier = Modifier,
    extensions: List<InstalledExtension>,
    extensionScanDone: Boolean,
    manager: ExtensionManager,
    libraryEntries: List<LibraryEntry>,
    listeningSignals: List<ListeningSignal>,
    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenDetails: (ExtensionMediaSelection) -> Unit,
    onPlayMusic: (ExtensionMediaSelection) -> Unit,
) {
    var destination by remember { mutableStateOf(MediaDestination.ANIME_MANGA) }
    var selectedType by remember { mutableStateOf(ContentType.ANIME) }
    var musicLocal by remember { mutableStateOf(MusicLocal.HOME) }
    var rows by remember { mutableStateOf<List<BrowseCard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var switchOpen by remember { mutableStateOf(false) }
    var sourceMissing by remember { mutableStateOf(false) }

    val engine = remember { MusicTasteEngine() }
    val rankedTaste = remember(listeningSignals) { engine.ranked(listeningSignals, System.currentTimeMillis()) }

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

    fun load(search: String) {
        if (destination == MediaDestination.BIBLE) {
            rows = emptyList(); loading = false; sourceMissing = false; return
        }
        val key = typeKey(selectedType)
        val sourceExtension = extensions.firstOrNull { ext ->
            ext.error == null && ext.descriptor?.sources?.any { key in it.contentTypes } == true
        }
        if (sourceExtension == null) {
            rows = emptyList(); loading = false; sourceMissing = extensionScanDone; return
        }
        val source = sourceExtension.descriptor!!.sources.first { key in it.contentTypes }
        sourceMissing = false
        loading = true
        val method = if (search.isBlank()) ExtensionContract.Method.BROWSE else ExtensionContract.Method.SEARCH
        val payload = JSONObject().put("sourceId", source.id).put("type", key).put("query", search.trim()).toString()
        manager.call(sourceExtension, method, payload) { result ->
            rows = result.getOrNull()?.let { parseBrowse(it, source.id, sourceExtension.packageName) } ?: emptyList()
            loading = false
        }
    }

    LaunchedEffect(selectedType, extensions, query, destination) {
        if (destination == MediaDestination.BIBLE) return@LaunchedEffect
        if (query.isNotBlank()) delay(250)
        load(query)
    }

    Column(modifier.fillMaxSize()) {
        MediaTopBar(
            title = destination.label,
            searchOpen = searchOpen,
            query = query,
            selectedType = selectedType,
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
            destination == MediaDestination.BIBLE -> BibleHubContent(Modifier.fillMaxSize())
            !extensionScanDone || loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            sourceMissing -> MissingMediaSource(selectedType.label, onOpenExtensions)
            query.isNotBlank() -> SearchResultsSurface(rows, selectedType, ::selection, onOpenDetails, onPlayMusic)
            destination == MediaDestination.ANIME_MANGA -> AnimeMangaSurface(
                type = selectedType, rows = rows, libraryEntries = libraryEntries,
                selection = ::selection, isSaved = isSaved, onToggleSaved = onToggleSaved, onOpen = onOpenDetails,
            )
            destination == MediaDestination.MOVIES_TV -> MovieTvSurface(
                type = selectedType, rows = rows, libraryEntries = libraryEntries,
                selection = ::selection, isSaved = isSaved, onToggleSaved = onToggleSaved, onOpen = onOpenDetails,
            )
            destination == MediaDestination.MUSIC -> MusicSurface(
                panel = musicLocal, rows = rows, libraryEntries = libraryEntries, rankedTaste = rankedTaste,
                selection = ::selection, onPlay = onPlayMusic, onOpen = onOpenDetails,
            )
            destination == MediaDestination.MEMES -> MemeSurface(rows, ::selection, onOpenDetails)
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
    onSwitch: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onQuery: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(58.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchOpen) {
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
            IconButton(onClick = onOpenSearch) { Icon(Icons.Rounded.Search, "Search $title") }
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
    libraryEntries: List<LibraryEntry>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpen: (ExtensionMediaSelection) -> Unit,
) {
    val selected = rows.firstOrNull()
    val continued = libraryEntries.filter { it.contentType == type }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 126.dp)) {
        if (selected != null) item {
            StreamFeature(
                card = selected,
                kicker = if (type == ContentType.ANIME) "Featured anime" else "Featured manga",
                body = if (type == ContentType.ANIME) "Continue the anime without mixing it with your manga progress." else "Pick up your reading progress directly from the chapter you left.",
                primaryLabel = if (type == ContentType.ANIME) "Continue" else "Read",
                selection = selection(selected, type), isSaved = isSaved(selection(selected, type)), onToggleSaved = onToggleSaved, onOpen = onOpen,
            )
        }
        item {
            MediaSectionTitle(if (type == ContentType.ANIME) "Continue watching" else "Continue reading", "Right where you stopped")
            if (continued.isNotEmpty()) ContinueLandscapeRail(continued, onOpen)
            else HintLine(if (type == ContentType.ANIME) "Your watching progress will appear here." else "Your reading progress will appear here.")
        }
        item {
            MediaSectionTitle(
                if (type == ContentType.ANIME) "Because you watched ${selected?.title ?: "anime"}" else "Because you read ${selected?.title ?: "manga"}",
                if (type == ContentType.ANIME) "More from your enabled anime sources" else "More from your enabled manga sources",
            )
            PortraitRail(rows.drop(1).ifEmpty { rows }, type, selection, onOpen)
        }
        item {
            MediaSectionTitle("New & hot", if (type == ContentType.ANIME) "Fresh episodes and upcoming releases" else "Fresh chapters and upcoming releases")
            NewHotStack(rows.take(3), type, selection, onOpen)
        }
        item {
            MediaSectionTitle("Top 10 ${type.label.lowercase()} today", if (type == ContentType.ANIME) "Trending across your enabled sources" else "Popular across your manga sources")
            TopTenRail(rows, type, selection, onOpen)
        }
        item {
            MediaSectionTitle(if (type == ContentType.ANIME) "New episodes" else "Recently updated", if (type == ContentType.ANIME) "Fresh from your sources" else "New chapters from your library")
            PortraitRail(rows.reversed(), type, selection, onOpen)
        }
        item {
            MediaSectionTitle("Browse by mood", "Jump straight to a vibe")
            GenreRail(if (type == ContentType.ANIME) listOf("Dark", "Funny", "Psychological", "Adventure", "Romance", "Slice of life") else listOf("Drama", "Psychological", "Action", "Romance", "Mystery", "Slice of life"))
        }
    }
}

@Composable
private fun MovieTvSurface(
    type: ContentType,
    rows: List<BrowseCard>,
    libraryEntries: List<LibraryEntry>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
    isSaved: (ExtensionMediaSelection) -> Boolean,
    onToggleSaved: (ExtensionMediaSelection) -> Unit,
    onOpen: (ExtensionMediaSelection) -> Unit,
) {
    val selected = rows.firstOrNull()
    val continued = libraryEntries.filter { it.contentType == type }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 126.dp)) {
        if (selected != null) item {
            StreamFeature(
                card = selected,
                kicker = if (type == ContentType.MOVIE) "Featured movie" else "Featured series",
                body = selected.subtitle.ifBlank { "From your enabled sources" },
                primaryLabel = "Play",
                selection = selection(selected, type), isSaved = isSaved(selection(selected, type)), onToggleSaved = onToggleSaved, onOpen = onOpen,
            )
        }
        item {
            MediaSectionTitle("Continue watching", "Right where you stopped")
            if (continued.isNotEmpty()) ContinueLandscapeRail(continued, onOpen) else HintLine("Your movie and series progress will appear here.")
        }
        item { MediaSectionTitle("Top 10 ${if (type == ContentType.MOVIE) "movies" else "series"} today", "Popular across your enabled sources"); TopTenRail(rows, type, selection, onOpen) }
        item { MediaSectionTitle("Trending now", "What people are watching"); PortraitRail(rows, type, selection, onOpen) }
        item { MediaSectionTitle("New & popular", "Fresh additions and returning favourites"); PortraitRail(rows.reversed(), type, selection, onOpen) }
        item { MediaSectionTitle("Browse by mood", "Pick a lane"); GenreRail(listOf("Thriller", "Drama", "Comedy", "Sci-fi", "Crime", "Documentary")) }
    }
}

@Composable
private fun MusicSurface(
    panel: MusicLocal,
    rows: List<BrowseCard>,
    libraryEntries: List<LibraryEntry>,
    rankedTaste: List<ListeningSignal>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
    onPlay: (ExtensionMediaSelection) -> Unit,
    onOpen: (ExtensionMediaSelection) -> Unit,
) {
    when (panel) {
        MusicLocal.HOME -> MusicHome(rows, rankedTaste, selection, onPlay, onOpen)
        MusicLocal.DISCOVER -> MusicDiscover(rows, selection, onPlay)
        MusicLocal.LIBRARY -> MusicLibrary(rows, libraryEntries, selection, onPlay)
    }
}

@Composable
private fun MusicHome(
    rows: List<BrowseCard>, rankedTaste: List<ListeningSignal>,
    selection: (BrowseCard, ContentType) -> ExtensionMediaSelection,
    onPlay: (ExtensionMediaSelection) -> Unit, onOpen: (ExtensionMediaSelection) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 126.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Good evening", color = SoraMuted, fontSize = 11.sp); Text("Music", fontSize = 28.sp, fontWeight = FontWeight.Black) }
                IconButton(onClick = {}) { Icon(Icons.Rounded.MoreVert, "Music options") }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Made for you", "Recently played", "Downloaded").forEachIndexed { index, label ->
                    item { Surface(color = if (index == 0) SoraAccent else SoraSurfaceHigh, contentColor = if (index == 0) SoraAccentInk else SoraText, shape = RoundedCornerShape(999.dp)) { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) } }
                }
            }
        }
        item { MusicQuickGrid(rows.take(6), selection, onPlay) }
        item { MusicSectionTitle("Made for you", "Personal mixes that change with your listening", "More") }
        item { MusicMixRail(rows, selection, onPlay) }
        item { MusicSectionTitle("Recently played", "Pick up where you left off", null); MusicSquareRail(rows.take(8), selection, onPlay) }
        item { MusicSectionTitle("Your top artists", if (rankedTaste.isNotEmpty()) "Based on recent listening" else "Artists from your recent music", null); ArtistRail(rows) }
        item { MusicSectionTitle("Your rotation", "Most played this week", "Open"); MusicTrackList(rows.take(8), selection, onPlay) }
    }
}

@Composable
private fun MusicDiscover(rows: List<BrowseCard>, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onPlay: (ExtensionMediaSelection) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 126.dp)) {
        item {
            Surface(color = Color(0xFF242118), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("DISCOVER", color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Text("Something new for tonight.", fontSize = 27.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 16.dp))
                    Text("Fresh releases and unfamiliar artists, weighted toward what you actually finish listening to.", color = SoraMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { rows.firstOrNull()?.let { onPlay(selection(it, ContentType.MUSIC)) } }, shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(top = 18.dp)) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Play Discover Mix") }
                }
            }
        }
        item { MusicSectionTitle("New releases for you", "Fresh music from artists you follow", null); MusicSquareRail(rows, selection, onPlay) }
        item { MusicSectionTitle("Charts", "What is moving right now", null); MusicTrackList(rows.take(10), selection, onPlay, numbered = true) }
        item { MusicSectionTitle("Find your mood", "Start with a feeling, not a genre", null); GenreRail(listOf("Late night", "Focus", "Energy", "Soft", "Melancholy", "Drive")) }
        item { MusicSectionTitle("Browse all", "Genres and listening modes", null); GenreGrid(listOf("Alternative", "R&B", "Electronic", "Afrobeats", "Indie", "Soundtracks")) }
    }
}

@Composable
private fun MusicLibrary(rows: List<BrowseCard>, libraryEntries: List<LibraryEntry>, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onPlay: (ExtensionMediaSelection) -> Unit) {
    val savedMusic = libraryEntries.filter { it.contentType == ContentType.MUSIC }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 126.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) { Text("YOUR MUSIC", color = SoraAccent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp); Text("Everything you kept.", fontSize = 25.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 3.dp)) }
                Text("Sort⌄", color = SoraMuted, fontSize = 11.sp)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MusicShortcut("Liked songs", Icons.Rounded.Favorite, Modifier.weight(1f))
                MusicShortcut("Downloaded", Icons.Rounded.Download, Modifier.weight(1f))
                MusicShortcut("Artists", Icons.Rounded.Person, Modifier.weight(1f))
            }
        }
        item { MusicSectionTitle("Playlists", "Yours and saved", "New"); MusicMixRail(rows, selection, onPlay) }
        item { MusicSectionTitle("Albums", "Saved albums", null); MusicSquareRail(rows.take(6), selection, onPlay) }
        item { MusicSectionTitle("Artists", "Followed artists", null); ArtistRail(rows) }
        if (savedMusic.isNotEmpty()) item { MusicSectionTitle("Saved songs", "${savedMusic.size} in Sora", null) }
    }
}

@Composable
private fun MemeSurface(rows: List<BrowseCard>, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onOpen: (ExtensionMediaSelection) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
        if (rows.isEmpty()) item { HintLine("Install a meme source to build this feed.") }
        items(rows, key = { it.id }) { card ->
            Surface(color = Color(0xFFF0EDE5), contentColor = Color(0xFF141412), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clickable { onOpen(selection(card, ContentType.MEME)) }) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(card.subtitle.ifBlank { "meme source" }, fontSize = 11.sp, color = Color(0xFF656158)); Icon(Icons.Rounded.MoreHoriz, null) }
                    Text(card.title, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                    Box(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFFC5C0B3))) { if (!card.artworkUrl.isNullOrBlank()) AsyncImage(card.artworkUrl, card.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) { Text("♡ Save", fontSize = 12.sp); Text("↗ Share", fontSize = 12.sp); Text("Less like this", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsSurface(rows: List<BrowseCard>, type: ContentType, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onOpen: (ExtensionMediaSelection) -> Unit, onPlayMusic: (ExtensionMediaSelection) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 126.dp)) {
        item { MediaSectionTitle("Results", type.label) }
        items(rows, key = { it.id }) { card ->
            Row(Modifier.fillMaxWidth().clickable { if (type == ContentType.MUSIC) onPlayMusic(selection(card, type)) else onOpen(selection(card, type)) }.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Poster(card.artworkUrl, card.title, Modifier.size(width = 58.dp, height = if (type == ContentType.MUSIC) 58.dp else 76.dp), if (type == ContentType.MUSIC) 7 else 5)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(type.label.uppercase(), color = SoraAccent, fontSize = 8.sp, fontWeight = FontWeight.Black); Text(card.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text(card.subtitle, color = SoraMuted, fontSize = 10.sp, maxLines = 1) }
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
        items(rows.take(12), key = { "p-${type.name}-${it.id}" }) { card ->
            Column(Modifier.width(116.dp).clickable { onOpen(selection(card, type)) }) { Poster(card.artworkUrl, card.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f), 7); Text(card.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)); Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun TopTenRail(rows: List<BrowseCard>, type: ContentType, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onOpen: (ExtensionMediaSelection) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(rows.take(10).withIndex().toList(), key = { "top-${type.name}-${it.value.id}" }) { ranked ->
            Row(Modifier.height(178.dp).clickable { onOpen(selection(ranked.value, type)) }, verticalAlignment = Alignment.Bottom) {
                Text("${ranked.index + 1}", color = Color(0xFF6F6C64), fontSize = 70.sp, lineHeight = 70.sp, fontWeight = FontWeight.Black, letterSpacing = (-5).sp)
                Poster(ranked.value.artworkUrl, ranked.value.title, Modifier.width(96.dp).fillMaxHeight(), 5)
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
                    LinearProgressIndicator(progress = { .58f }, modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter), color = SoraAccent, trackColor = Color(0xFF555248))
                }
                Text(entry.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 6.dp)); Text(entry.detail, color = SoraMuted, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun NewHotStack(rows: List<BrowseCard>, type: ContentType, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onOpen: (ExtensionMediaSelection) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEachIndexed { index, card ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(selection(card, type)) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(47.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("SEP", color = SoraMuted, fontSize = 8.sp, fontWeight = FontWeight.Black); Text("${14 + index * 2}", fontSize = 19.sp, fontWeight = FontWeight.Black) }
                Poster(card.artworkUrl, card.title, Modifier.size(width = 54.dp, height = 74.dp), 6)
                Column(Modifier.weight(1f).padding(start = 11.dp)) { Text(if (index == 0) "NEW NOW" else "COMING SOON", color = SoraAccent, fontSize = 8.sp, fontWeight = FontWeight.Black); Text(card.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1) }
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
        rows.chunked(2).forEach { chunk -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chunk.forEach { card -> Row(Modifier.weight(1f).height(58.dp).background(SoraSurfaceHigh, RoundedCornerShape(7.dp)).clickable { onPlay(selection(card, ContentType.MUSIC)) }, verticalAlignment = Alignment.CenterVertically) { Poster(card.artworkUrl, card.title, Modifier.size(58.dp), 7); Text(card.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 9.dp).weight(1f)) } }; if (chunk.size == 1) Spacer(Modifier.weight(1f)) } }
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
private fun MusicSquareRail(rows: List<BrowseCard>, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onPlay: (ExtensionMediaSelection) -> Unit) { LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(rows.take(10), key = { "sq-${it.id}" }) { card -> Column(Modifier.width(146.dp).clickable { onPlay(selection(card, ContentType.MUSIC)) }) { Poster(card.artworkUrl, card.title, Modifier.size(146.dp), 6); Text(card.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 7.dp)); Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1) } } } }

@Composable
private fun ArtistRail(rows: List<BrowseCard>) { val artists = rows.mapNotNull { it.subtitle.substringBefore(" · ").takeIf(String::isNotBlank) }.distinct().take(8); LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { items(artists) { artist -> Column(Modifier.width(94.dp), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(86.dp).background(SoraSurfaceHigh, CircleShape), contentAlignment = Alignment.Center) { Text(artist.take(1), fontSize = 28.sp, fontWeight = FontWeight.Black) }; Text(artist, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) } } } }

@Composable
private fun MusicTrackList(rows: List<BrowseCard>, selection: (BrowseCard, ContentType) -> ExtensionMediaSelection, onPlay: (ExtensionMediaSelection) -> Unit, numbered: Boolean = false) { Column { rows.forEachIndexed { index, card -> Row(Modifier.fillMaxWidth().clickable { onPlay(selection(card, ContentType.MUSIC)) }.padding(horizontal = 18.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { if (numbered) Text("${index + 1}", color = SoraMuted, fontSize = 12.sp, modifier = Modifier.width(24.dp)); Poster(card.artworkUrl, card.title, Modifier.size(48.dp), 5); Column(Modifier.weight(1f).padding(horizontal = 11.dp)) { Text(card.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1); Text(card.subtitle, color = SoraMuted, fontSize = 9.sp, maxLines = 1) }; Icon(Icons.Rounded.MoreVert, null, tint = SoraMuted, modifier = Modifier.size(18.dp)) } } } }

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
