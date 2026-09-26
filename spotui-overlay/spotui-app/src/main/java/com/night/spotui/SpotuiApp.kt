package com.night.spotui

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.night.spotui.playback.SpotPlaybackController
import com.night.spotui.playback.SpotRepeatMode
import com.night.spotui.playback.SpotRuntime
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject

private val SpotGreen = Color(0xFF1ED760)
private val SpotBlack = Color(0xFF080808)
private val SpotSurface = Color(0xFF141414)
private val SpotRaised = Color(0xFF242424)
private val SpotText = Color(0xFFF5F5F5)
private val SpotMuted = Color(0xFFAAAAAA)

private enum class SpotTab { HOME, SEARCH, LIBRARY }

@Composable
fun SpotuiApp() {
    val context = LocalContext.current
    val source = remember { SpotRuntime.source(context) }
    val player = remember { SpotRuntime.player(context) }
    val taste = remember { SpotRuntime.taste(context) }
    val library = remember { LibraryStore(context) }
    val homeCache = remember { HomeCacheStore(context) }
    val lyricsRepository = remember { LyricsRepository() }
    val soundCloudSuggestions = remember {
        SoundCloudSuggestions(BuildConfig.SOUNDCLOUD_SUGGEST_PROXY)
    }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(SpotTab.HOME) }
    var homeTracks by remember { mutableStateOf(homeCache.load()) }
    var searchTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var likedTracks by remember { mutableStateOf(library.all()) }
    var likedAlbums by remember { mutableStateOf(library.albums()) }
    var recentlyPlayed by remember { mutableStateOf(taste.recentHistory()) }
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var recentSearches by remember { mutableStateOf(taste.recentQueries()) }
    var homeLoading by remember { mutableStateOf(homeTracks.isEmpty()) }
    var searchLoading by remember { mutableStateOf(false) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var showSignIn by remember { mutableStateOf(false) }
    var homeReloadEpoch by remember { mutableIntStateOf(0) }
    var searchEpoch by remember { mutableIntStateOf(0) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedArtistId by remember { mutableStateOf<String?>(null) }
    var artistTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var artistReleases by remember { mutableStateOf<List<AlbumSummary>>(emptyList()) }
    var artistArtwork by remember { mutableStateOf<String?>(null) }
    var artistLoading by remember { mutableStateOf(false) }
    var artistError by remember { mutableStateOf<String?>(null) }
    var artistEpoch by remember { mutableIntStateOf(0) }
    var selectedAlbum by remember { mutableStateOf<AlbumCatalog?>(null) }
    var pendingAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var albumLoading by remember { mutableStateOf(false) }
    var albumError by remember { mutableStateOf<String?>(null) }
    var albumEpoch by remember { mutableIntStateOf(0) }

    fun openArtist(name: String, artistId: String? = null) {
        val clean = name.trim()
        if (clean.isBlank()) return
        selectedArtist = clean
        selectedArtistId = artistId
        selectedAlbum = null
        pendingAlbum = null
        artistTracks = (homeTracks + searchTracks + likedTracks)
            .filter { it.artist.equals(clean, ignoreCase = true) }
            .distinctBy(Track::id)
        artistReleases = emptyList()
        artistArtwork = artistTracks.firstOrNull()?.artworkUrl
        artistError = null
        val requestEpoch = ++artistEpoch
        scope.launch {
            artistLoading = true
            source.artist(clean, artistId)
                .onSuccess { catalog ->
                    if (requestEpoch != artistEpoch) return@onSuccess
                    selectedArtist = catalog.name.ifBlank { clean }
                    selectedArtistId = catalog.id.ifBlank { artistId }
                    artistTracks = catalog.songs
                    artistReleases = catalog.releases
                    artistArtwork = catalog.artworkUrl ?: artistTracks.firstOrNull()?.artworkUrl
                }
                .onFailure {
                    if (requestEpoch == artistEpoch) artistError = "Artist page didn’t load."
                }
            if (requestEpoch == artistEpoch) artistLoading = false
        }
    }

    fun openAlbum(summary: AlbumSummary) {
        pendingAlbum = summary
        selectedAlbum = null
        albumError = null
        val requestEpoch = ++albumEpoch
        scope.launch {
            albumLoading = true
            source.album(summary.title + " " + summary.artist, summary.id)
                .onSuccess { catalog ->
                    if (requestEpoch != albumEpoch) return@onSuccess

                    // The artist discography card is a trusted catalog record that
                    // opened this stable album ID. Keep its metadata when a source
                    // returns a malformed album header (for example, year as artist).
                    fun usableArtist(value: String): Boolean =
                        value.isNotBlank() &&
                            !value.equals("YouTube Music", ignoreCase = true) &&
                            !value.matches(Regex("""(?:19|20)\d{2}"""))

                    val sourceArtistIsUsable = usableArtist(catalog.artist)
                    val artist = catalog.artist.takeIf { sourceArtistIsUsable }
                        ?: summary.artist
                    val artistId = if (sourceArtistIsUsable) {
                        catalog.artistId.ifBlank { summary.artistId }
                    } else {
                        summary.artistId
                    }
                    val providerTitle = catalog.title.trim()
                    val title = if (
                        summary.year > 0 &&
                        providerTitle.startsWith(summary.title, ignoreCase = true) &&
                        providerTitle.endsWith(summary.year.toString()) &&
                        providerTitle != summary.title
                    ) {
                        summary.title
                    } else {
                        providerTitle.ifBlank { summary.title }
                    }

                    selectedAlbum = catalog.copy(
                        title = title,
                        artist = artist,
                        artistId = artistId,
                        artworkUrl = catalog.artworkUrl ?: summary.artworkUrl,
                        songs = catalog.songs.map { track ->
                            val trackHasArtist = usableArtist(track.artist)
                            track.copy(
                                artist = track.artist.takeIf { trackHasArtist } ?: artist,
                                artistId = if (trackHasArtist) {
                                    track.artistId.ifBlank { artistId }
                                } else {
                                    artistId
                                },
                                album = track.album.takeIf {
                                    it.isNotBlank() && it != providerTitle
                                } ?: title,
                                albumId = track.albumId.ifBlank { summary.id },
                                artworkUrl = track.artworkUrl ?: summary.artworkUrl,
                            )
                        },
                    )
                }
                .onFailure {
                    if (requestEpoch == albumEpoch) albumError = "Album didn’t load."
                }
            if (requestEpoch == albumEpoch) albumLoading = false
        }
    }

    fun toggleAlbum(album: AlbumSummary) {
        library.toggleAlbum(album)
        likedAlbums = library.albums()
    }

    fun toggleLike(track: Track) {
        val wasLiked = likedTracks.any { it.id == track.id }
        library.toggle(track)
        likedTracks = library.all()
        taste.recordLike(track, liked = !wasLiked)
        homeTracks = taste.rank(homeTracks)
        homeCache.save(homeTracks)
    }

    fun runSearch(term: String = query) {
        val clean = term.trim()
        if (clean.isBlank()) return
        query = clean
        submittedQuery = clean
        suggestions = emptyList()
        searchTracks = emptyList()
        val requestEpoch = ++searchEpoch
        scope.launch {
            searchLoading = true
            searchError = null
            source.search(clean)
                .onSuccess { results ->
                    if (requestEpoch != searchEpoch) return@onSuccess
                    searchTracks = taste.rank(results)
                    taste.recordSearch(clean, results)
                    recentSearches = taste.recentQueries()
                    homeTracks = taste.rank((results.take(8) + homeTracks).distinctBy(Track::id))
                    homeCache.save(homeTracks)
                }
                .onFailure {
                    if (requestEpoch == searchEpoch) searchError = "Search didn’t load. Try again."
                }
            if (requestEpoch == searchEpoch) searchLoading = false
        }
    }

    LaunchedEffect(homeReloadEpoch) {
        homeLoading = homeTracks.isEmpty()
        homeError = null
        source.home()
            .onSuccess { base ->
                val personal = buildList {
                    taste.topArtists(4).forEach { artist ->
                        source.search(artist.name).getOrNull()?.take(6)?.let { addAll(it) }
                    }
                }
                homeTracks = taste.homeMix(personal, base)
                homeCache.save(homeTracks)
            }
            .onFailure {
                if (homeTracks.isEmpty()) homeError = "Home didn’t refresh."
            }
        homeLoading = false
    }

    LaunchedEffect(player.historyRevision) {
        recentlyPlayed = taste.recentHistory()
        homeTracks = taste.rank(homeTracks)
        homeCache.save(homeTracks)
    }

    LaunchedEffect(player.currentTrack?.id) {
        if (player.currentTrack != null) {
            delay(250)
            homeTracks = taste.rank(homeTracks)
        }
    }

    LaunchedEffect(query) {
        val clean = query.trim()
        if (clean.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(140)
        val local = taste.querySuggestions(clean)
        val network = source.suggestions(clean).getOrDefault(emptyList())
        val soundCloud = if (network.isEmpty()) soundCloudSuggestions.suggest(clean) else emptyList()
        suggestions = (local + network + soundCloud)
            .distinctBy { it.lowercase() }
            .take(10)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SpotGreen,
            background = SpotBlack,
            surface = SpotSurface,
            onBackground = SpotText,
            onSurface = SpotText,
        )
    ) {
        Box(Modifier.fillMaxSize().background(SpotBlack)) {
            Scaffold(
                containerColor = SpotBlack,
                bottomBar = {
                    Column {
                        player.currentTrack?.let { track ->
                            MiniPlayer(
                                track = track,
                                player = player,
                                onOpen = { showPlayer = true },
                                onSignIn = { showSignIn = true },
                            )
                        }
                        NavigationBar(containerColor = Color(0xFF101010)) {
                            NavigationBarItem(
                                selected = tab == SpotTab.HOME,
                                onClick = { tab = SpotTab.HOME },
                                icon = { Icon(Icons.Rounded.Home, null) },
                                label = { Text("Home") },
                            )
                            NavigationBarItem(
                                selected = tab == SpotTab.SEARCH,
                                onClick = { tab = SpotTab.SEARCH },
                                icon = { Icon(Icons.Rounded.Search, null) },
                                label = { Text("Search") },
                            )
                            NavigationBarItem(
                                selected = tab == SpotTab.LIBRARY,
                                onClick = { tab = SpotTab.LIBRARY },
                                icon = { Icon(Icons.Rounded.LibraryMusic, null) },
                                label = { Text("Library") },
                            )
                        }
                    }
                },
            ) { padding ->
                val albumRequest = pendingAlbum
                val artist = selectedArtist
                when {
                    albumRequest != null -> AlbumScreen(
                        modifier = Modifier.padding(padding),
                        summary = albumRequest,
                        album = selectedAlbum,
                        loading = albumLoading,
                        error = albumError,
                        saved = likedAlbums.any { it.id == albumRequest.id },
                        liked = likedTracks.map(Track::id).toSet(),
                        onBack = {
                            pendingAlbum = null
                            selectedAlbum = null
                        },
                        onPlay = { track ->
                            val queue = selectedAlbum?.songs.orEmpty().ifEmpty { listOf(track) }
                            player.play(track, queue)
                        },
                        onToggleLike = ::toggleLike,
                        onToggleAlbum = { catalog ->
                            toggleAlbum(
                                AlbumSummary(
                                    id = catalog.id,
                                    title = catalog.title,
                                    artist = catalog.artist,
                                    artistId = catalog.artistId,
                                    year = catalog.year,
                                    artworkUrl = catalog.artworkUrl,
                                )
                            )
                        },
                        onArtist = { name, id ->
                            pendingAlbum = null
                            selectedAlbum = null
                            openArtist(name, id)
                        },
                        onRetry = { openAlbum(albumRequest) },
                    )
                    artist != null -> ArtistScreen(
                        modifier = Modifier.padding(padding),
                        artist = artist,
                        artworkUrl = artistArtwork,
                        tracks = artistTracks,
                        releases = artistReleases,
                        loading = artistLoading,
                        error = artistError,
                        liked = likedTracks.map(Track::id).toSet(),
                        onBack = {
                            selectedArtist = null
                            selectedArtistId = null
                        },
                        onPlay = { player.play(it, artistTracks) },
                        onToggleLike = ::toggleLike,
                        onAlbum = ::openAlbum,
                        onRetry = { openArtist(artist, selectedArtistId) },
                    )
                    else -> when (tab) {
                        SpotTab.HOME -> HomeScreen(
                            modifier = Modifier.padding(padding),
                            tracks = homeTracks,
                            personalized = taste.hasTaste(),
                            loading = homeLoading,
                            error = homeError,
                            liked = likedTracks.map(Track::id).toSet(),
                            onPlay = { player.play(it, homeTracks) },
                            onToggleLike = ::toggleLike,
                            onArtist = { name, id -> openArtist(name, id) },
                            onRetry = { homeReloadEpoch++ },
                        )
                        SpotTab.SEARCH -> SearchScreen(
                            modifier = Modifier.padding(padding),
                            query = query,
                            submittedQuery = submittedQuery,
                            onQuery = { query = it },
                            suggestions = suggestions,
                            recentSearches = recentSearches,
                            tracks = searchTracks,
                            loading = searchLoading,
                            error = searchError,
                            liked = likedTracks.map(Track::id).toSet(),
                            onSearch = { term -> runSearch(term) },
                            onPlay = { player.play(it, searchTracks) },
                            onToggleLike = ::toggleLike,
                            onArtist = { name, id -> openArtist(name, id) },
                            onSuggestion = { value -> runSearch(value) },
                        )
                        SpotTab.LIBRARY -> LibraryScreen(
                            modifier = Modifier.padding(padding),
                            tracks = likedTracks,
                            albums = likedAlbums,
                            recentlyPlayed = recentlyPlayed,
                            onPlay = { player.play(it, likedTracks) },
                            onToggleLike = ::toggleLike,
                            onArtist = { name, id -> openArtist(name, id) },
                            onAlbum = ::openAlbum,
                        )
                    }
                }
            }

            if (showPlayer && player.currentTrack != null) {
                NowPlaying(
                    player = player,
                    liked = likedTracks.any { it.id == player.currentTrack?.id },
                    onToggleLike = { player.currentTrack?.let(::toggleLike) },
                    onSignIn = { showSignIn = true },
                    lyricsRepository = lyricsRepository,
                    onArtist = { artist, artistId ->
                        showPlayer = false
                        openArtist(artist, artistId)
                    },
                    onClose = { showPlayer = false },
                )
            }

            if (showSignIn) {
                SourceSessionBrowser(
                    source = source,
                    onConnected = player::retryCurrent,
                    onClose = { showSignIn = false },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    tracks: List<Track>,
    personalized: Boolean,
    loading: Boolean,
    error: String?,
    liked: Set<String>,
    onPlay: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onArtist: (String, String?) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier.fillMaxSize().background(SpotBlack),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Lyra", color = SpotText, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text("Your listening space", color = SpotMuted, fontSize = 11.sp)
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                if (loading) LoadingBlock("Building your mix…")
                else ErrorBlock(error ?: "No music was returned.", onRetry)
            }
        } else {
            item { MusicQuickGrid(tracks.take(6), liked, onPlay, onToggleLike) }
            item {
                MusicSectionTitle(
                    if (personalized) "Made for you" else "Explore music",
                    if (personalized) "Ordered by what you search, like and replay"
                    else "Start listening to shape your music mix",
                )
            }
            item { MusicSquareRail(tracks.take(10), onPlay, onArtist) }
            item {
                MusicSectionTitle(
                    if (personalized) "Artists in your mix" else "Artists to explore",
                    if (personalized) "Shaped by your listening" else "Your listening will shape what appears here",
                )
            }
            item { ArtistRail(tracks, onArtist) }
            item {
                MusicSectionTitle(
                    if (personalized) "Your rotation" else "Discover more",
                    if (personalized) "Songs and artists you keep coming back to"
                    else "New tracks to start your library",
                )
            }
            item { MusicTrackList(tracks.take(10), liked, onPlay, onToggleLike, onArtist) }
        }

        error?.takeIf { tracks.isNotEmpty() }?.let { message ->
            item { ErrorBlock(message, onRetry) }
        }
    }
}

@Composable
private fun SearchScreen(
    modifier: Modifier,
    query: String,
    submittedQuery: String,
    onQuery: (String) -> Unit,
    suggestions: List<String>,
    recentSearches: List<String>,
    tracks: List<Track>,
    loading: Boolean,
    error: String?,
    liked: Set<String>,
    onSearch: (String) -> Unit,
    onPlay: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onArtist: (String, String?) -> Unit,
    onSuggestion: (String) -> Unit,
) {
    val showingResults = submittedQuery.isNotBlank() && query.trim() == submittedQuery
    LazyColumn(modifier.fillMaxSize().background(SpotBlack)) {
        item {
            Column(Modifier.statusBarsPadding().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp)) {
                Text("Search", color = SpotText, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = Color(0xFF1F1F1F),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = SpotMuted, modifier = Modifier.size(23.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = onQuery,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = SpotText,
                                fontSize = 16.sp,
                            ),
                            cursorBrush = SolidColor(SpotGreen),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                            decorationBox = { inner ->
                                if (query.isBlank()) {
                                    Text("What do you want to listen to?", color = SpotMuted, fontSize = 15.sp)
                                }
                                inner()
                            },
                        )
                        if (loading && showingResults) {
                            CircularProgressIndicator(
                                color = SpotMuted,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }

        if (query.isBlank() && recentSearches.isNotEmpty()) {
            item { MusicSectionTitle("Recent searches", "Pick up where you left off") }
            items(recentSearches, key = { "recent-" + it.lowercase() }) { recent ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onSuggestion(recent) }
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = SpotMuted, modifier = Modifier.size(18.dp))
                    Text(recent, color = SpotText, fontSize = 14.sp, modifier = Modifier.padding(start = 13.dp))
                }
            }
        }

        if (query.isNotBlank() && suggestions.isNotEmpty() && !showingResults) {
            items(suggestions, key = { "suggest-" + it.lowercase() }) { suggestion ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onSuggestion(suggestion) }
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = SpotMuted, modifier = Modifier.size(18.dp))
                    Text(
                        suggestion,
                        color = SpotText,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 13.dp).weight(1f),
                    )
                }
            }
        }

        if (showingResults) {
            error?.let { item { ErrorBlock(it) { onSearch(query) } } }
            items(tracks, key = { "search-" + it.id }) { track ->
                TrackRow(
                    track,
                    liked.contains(track.id),
                    { onPlay(track) },
                    { onToggleLike(track) },
                    { onArtist(track.artist, track.artistId.takeIf(String::isNotBlank)) },
                )
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    modifier: Modifier,
    tracks: List<Track>,
    albums: List<AlbumSummary>,
    recentlyPlayed: List<Track>,
    onPlay: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onArtist: (String, String?) -> Unit,
    onAlbum: (AlbumSummary) -> Unit,
) {
    LazyColumn(modifier.fillMaxSize().background(SpotBlack)) {
        item {
            Column(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text("YOUR MUSIC", color = SpotGreen, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("Everything you kept.", color = SpotText, fontSize = 25.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 3.dp))
                Text(
                    tracks.size.toString() + " saved songs · " + albums.size + " albums · " +
                        recentlyPlayed.size + " recent plays",
                    color = SpotMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (recentlyPlayed.isEmpty() && tracks.isEmpty() && albums.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.FavoriteBorder, null, tint = SpotMuted, modifier = Modifier.size(48.dp))
                    Text("Songs and albums you save will live here.", color = SpotMuted, modifier = Modifier.padding(top = 14.dp))
                }
            }
        } else {
            if (recentlyPlayed.isNotEmpty()) {
                item { MusicSectionTitle("Recently played", "Your listening history") }
                items(recentlyPlayed, key = { "history-" + it.id }) { track ->
                    TrackRow(
                        track,
                        tracks.any { saved -> saved.id == track.id },
                        { onPlay(track) },
                        { onToggleLike(track) },
                        { onArtist(track.artist, track.artistId.takeIf(String::isNotBlank)) },
                    )
                }
            }
            if (albums.isNotEmpty()) {
                item { MusicSectionTitle("Saved albums", "Albums in your library") }
                item {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(albums, key = { "saved-album-" + it.id }) { album ->
                            AlbumCard(album = album, onClick = { onAlbum(album) })
                        }
                    }
                }
            }
            if (tracks.isNotEmpty()) {
                item { MusicSectionTitle("Saved songs", "Stored on this phone") }
                items(tracks, key = { "liked-" + it.id }) { track ->
                    TrackRow(
                        track,
                        true,
                        { onPlay(track) },
                        { onToggleLike(track) },
                        { onArtist(track.artist, track.artistId.takeIf(String::isNotBlank)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistScreen(
    modifier: Modifier,
    artist: String,
    artworkUrl: String?,
    tracks: List<Track>,
    releases: List<AlbumSummary>,
    loading: Boolean,
    error: String?,
    liked: Set<String>,
    onBack: () -> Unit,
    onPlay: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAlbum: (AlbumSummary) -> Unit,
    onRetry: () -> Unit,
) {
    BackHandler(onBack = onBack)

    LazyColumn(
        modifier.fillMaxSize().background(SpotBlack),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Back", tint = SpotText)
                }
                Box(
                    Modifier.size(144.dp).clip(CircleShape).background(SpotRaised).align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            artworkUrl,
                            artist,
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(artist.take(1), color = SpotText, fontSize = 42.sp, fontWeight = FontWeight.Black)
                    }
                }
                Text(
                    artist,
                    color = SpotText,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text("Artist", color = SpotMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }

        if (tracks.isEmpty() && loading) {
            item { LoadingBlock("Loading artist…") }
        } else if (tracks.isEmpty() && releases.isEmpty()) {
            item { ErrorBlock(error ?: "Artist page didn’t load.", onRetry) }
        } else {
            if (tracks.isNotEmpty()) {
                item { MusicSectionTitle("Popular", "Top songs") }
                items(tracks.take(10), key = { "artist-popular-" + it.id }) { track ->
                    TrackRow(
                        track = track,
                        liked = liked.contains(track.id),
                        onPlay = { onPlay(track) },
                        onToggleLike = { onToggleLike(track) },
                        onArtist = {},
                    )
                }
            }

            if (releases.isNotEmpty()) {
                item { MusicSectionTitle("Discography", "Albums, EPs and singles") }
                item {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(releases, key = { "album-" + it.id }) { release ->
                            AlbumCard(release, onClick = { onAlbum(release) })
                        }
                    }
                }
            }

            if (tracks.size > 10) {
                item { MusicSectionTitle("All songs", tracks.size.toString() + " songs from the catalog") }
                items(tracks, key = { "artist-all-" + it.id }) { track ->
                    TrackRow(
                        track = track,
                        liked = liked.contains(track.id),
                        onPlay = { onPlay(track) },
                        onToggleLike = { onToggleLike(track) },
                        onArtist = {},
                    )
                }
            }

            if (loading) item { LoadingBlock("Refreshing artist…") }
        }
    }
}

@Composable
private fun AlbumScreen(
    modifier: Modifier,
    summary: AlbumSummary,
    album: AlbumCatalog?,
    loading: Boolean,
    error: String?,
    saved: Boolean,
    liked: Set<String>,
    onBack: () -> Unit,
    onPlay: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onToggleAlbum: (AlbumCatalog) -> Unit,
    onArtist: (String, String?) -> Unit,
    onRetry: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val title = album?.title?.ifBlank { summary.title } ?: summary.title
    val artist = album?.artist?.ifBlank { summary.artist } ?: summary.artist
    val artwork = album?.artworkUrl ?: summary.artworkUrl
    val year = album?.year?.takeIf { it > 0 } ?: summary.year

    LazyColumn(
        modifier.fillMaxSize().background(SpotBlack),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Back", tint = SpotText)
                }
                Box(
                    Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(SpotRaised).align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!artwork.isNullOrBlank()) {
                        AsyncImage(artwork, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Rounded.LibraryMusic, null, tint = SpotMuted, modifier = Modifier.size(64.dp))
                    }
                }
                Text(title, color = SpotText, fontSize = 27.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 18.dp))
                Text(
                    artist,
                    color = SpotText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 5.dp).clickable {
                        onArtist(artist, album?.artistId?.takeIf(String::isNotBlank) ?: summary.artistId.takeIf(String::isNotBlank))
                    },
                )
                Text(
                    listOfNotNull(summary.type.takeIf(String::isNotBlank), year.takeIf { it > 0 }?.toString()).joinToString(" · "),
                    color = SpotMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        color = SpotText,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.clickable(enabled = album?.songs?.isNotEmpty() == true) {
                            album?.songs?.firstOrNull()?.let(onPlay)
                        },
                    ) {
                        Text("Play", color = SpotBlack, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
                    }
                    Surface(
                        color = SpotRaised,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.clickable(enabled = album != null) { album?.let(onToggleAlbum) },
                    ) {
                        Text(
                            if (saved) "Saved" else "Save album",
                            color = if (saved) SpotGreen else SpotText,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }

        when {
            album == null && loading -> item { LoadingBlock("Loading album…") }
            album == null -> item { ErrorBlock(error ?: "Album didn’t load.", onRetry) }
            else -> {
                item { MusicSectionTitle("Tracks", album.songs.size.toString() + " songs") }
                items(album.songs, key = { "album-track-" + it.id }) { track ->
                    TrackRow(
                        track = track,
                        liked = liked.contains(track.id),
                        onPlay = { onPlay(track) },
                        onToggleLike = { onToggleLike(track) },
                        onArtist = { onArtist(track.artist, track.artistId.takeIf(String::isNotBlank)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: AlbumSummary,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(148.dp).clickable(onClick = onClick),
    ) {
        Box(
            Modifier.size(148.dp).clip(RoundedCornerShape(8.dp)).background(SpotRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (!album.artworkUrl.isNullOrBlank()) {
                AsyncImage(album.artworkUrl, album.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Rounded.LibraryMusic, null, tint = SpotMuted, modifier = Modifier.size(42.dp))
            }
        }
        Text(
            album.title,
            color = SpotText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
        Text(
            listOfNotNull(album.type.takeIf(String::isNotBlank), album.year.takeIf { it > 0 }?.toString()).joinToString(" · "),
            color = SpotMuted,
            fontSize = 9.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun MusicQuickGrid(
    tracks: List<Track>,
    liked: Set<String>,
    onPlay: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
) {
    Column(
        Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tracks.chunked(2).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEach { track ->
                    Row(
                        Modifier.weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(SpotRaised)
                            .semantics { contentDescription = "Play " + track.title }
                            .clickable { onPlay(track) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(track, Modifier.size(58.dp))
                        Text(
                            track.title,
                            color = SpotText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 9.dp).weight(1f),
                        )
                        if (liked.contains(track.id)) {
                            Icon(
                                Icons.Rounded.Favorite,
                                "Unlike",
                                tint = SpotGreen,
                                modifier = Modifier.padding(end = 8.dp).size(16.dp).clickable { onToggleLike(track) },
                            )
                        }
                    }
                }
                if (chunk.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MusicSectionTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 22.dp, bottom = 9.dp)) {
        Text(title, color = SpotText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = SpotMuted, fontSize = 10.sp)
    }
}

@Composable
private fun MusicSquareRail(
    tracks: List<Track>,
    onPlay: (Track) -> Unit,
    onArtist: (String, String?) -> Unit,
) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(tracks, key = { "square-" + it.id }) { track ->
            Column(
                Modifier.width(146.dp)
                    .semantics { contentDescription = "Play " + track.title }
                    .clickable { onPlay(track) }
            ) {
                Artwork(track, Modifier.size(146.dp))
                Text(
                    track.title,
                    color = SpotText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
                Text(
                    track.artist,
                    color = SpotMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onArtist(track.artist, track.artistId.takeIf(String::isNotBlank)) },
                )
            }
        }
    }
}

@Composable
private fun ArtistRail(tracks: List<Track>, onArtist: (String, String?) -> Unit) {
    val artists = tracks
        .map { it.artist.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(8)
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(artists, key = { "artist-" + it }) { artist ->
            val representative = tracks.firstOrNull { it.artist.trim() == artist }
            Column(
                Modifier.width(94.dp).clickable {
                    onArtist(artist, representative?.artistId?.takeIf(String::isNotBlank))
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(86.dp).clip(CircleShape).background(SpotRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!representative?.artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            representative?.artworkUrl,
                            artist,
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(artist.take(1), color = SpotText, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    }
                }
                Text(
                    artist,
                    color = SpotText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun MusicTrackList(
    tracks: List<Track>,
    liked: Set<String>,
    onPlay: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onArtist: (String, String?) -> Unit,
) {
    Column {
        tracks.forEach { track ->
            TrackRow(
                track = track,
                liked = liked.contains(track.id),
                onPlay = { onPlay(track) },
                onToggleLike = { onToggleLike(track) },
                onArtist = { onArtist(track.artist, track.artistId.takeIf(String::isNotBlank)) },
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    liked: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onArtist: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "Play " + track.title }.clickable(onClick = onPlay).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(track, Modifier.size(56.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, color = SpotText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    track.artist,
                    color = SpotMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onArtist)
                        .semantics { contentDescription = "Open artist " + track.artist },
                )
                if (track.album.isNotBlank()) {
                    Text(" · " + track.album, color = SpotMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        IconButton(onClick = onToggleLike) {
            Icon(
                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (liked) "Unlike" else "Like",
                tint = if (liked) SpotGreen else SpotMuted,
            )
        }
    }
}

@Composable
private fun Artwork(track: Track, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(7.dp)).background(SpotRaised), contentAlignment = Alignment.Center) {
        if (!track.artworkUrl.isNullOrBlank()) {
            AsyncImage(track.artworkUrl, track.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = SpotMuted, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun MiniPlayer(
    track: Track,
    player: SpotPlaybackController,
    onOpen: () -> Unit,
    onSignIn: () -> Unit,
) {
    Surface(
        color = Color(0xFF2B2B2B),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).semantics { contentDescription = "Mini player" }.clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track, Modifier.size(46.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(track.title, color = SpotText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (requiresSourceSession(player.errorMessage)) {
                    Text(
                        "Source needs browser session · Open",
                        color = SpotGreen,
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.clickable(onClick = onSignIn),
                    )
                } else {
                    Text(track.artist, color = SpotMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = player::togglePlayPause) {
                if (player.isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = SpotText)
                } else {
                    Icon(
                        if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (player.isPlaying) "Pause" else "Play",
                        tint = SpotText,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlaying(
    player: SpotPlaybackController,
    liked: Boolean,
    onToggleLike: () -> Unit,
    onSignIn: () -> Unit,
    lyricsRepository: LyricsRepository,
    onArtist: (String, String?) -> Unit,
    onClose: () -> Unit,
) {
    val track = player.currentTrack ?: return
    var queueOpen by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf(false) }
    var lyrics by remember(track.id) { mutableStateOf<TrackLyrics?>(null) }
    var lyricsLoading by remember(track.id) { mutableStateOf(true) }
    var lyricsError by remember(track.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(track.id) {
        lyricsLoading = true
        lyricsError = null
        lyricsRepository.lyrics(track)
            .onSuccess { lyrics = it }
            .onFailure { lyricsError = it.message ?: "Lyrics unavailable" }
        lyricsLoading = false
    }
    BackHandler(onBack = onClose)
    Column(
        Modifier.fillMaxSize().background(Color(0xFF11140F)).statusBarsPadding().navigationBarsPadding().padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = SpotText) }
            Text("NOW PLAYING", color = SpotMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Artwork(track, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = SpotText,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    modifier = Modifier.semantics { contentDescription = "Now playing " + track.title },
                )
                Text(
                    track.artist,
                    color = SpotMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp).clickable {
                        onArtist(track.artist, track.artistId.takeIf(String::isNotBlank))
                    },
                )
            }
            IconButton(onClick = onToggleLike) {
                Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", tint = if (liked) SpotGreen else SpotText)
            }
        }
        Spacer(Modifier.height(22.dp))
        val progress = if (player.durationMs > 0) player.positionMs.toFloat() / player.durationMs else 0f
        SpotSeekBar(
            progress = progress,
            onSeek = player::seekToFraction,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(player.positionMs), color = SpotMuted, fontSize = 10.sp)
            Text(formatTime(player.durationMs), color = SpotMuted, fontSize = 10.sp)
        }
        player.errorMessage?.let { message ->
            if (requiresSourceSession(message)) {
                Surface(
                    color = Color(0xFF19271E),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable(onClick = onSignIn),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("This source needs a browser session on this network.", color = SpotText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Open source browser", color = SpotGreen, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            } else {
                Text(
                    "Playback didn’t start. Tap play to retry.",
                    color = SpotMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = player::toggleShuffle) {
                Icon(
                    Icons.Rounded.Shuffle,
                    "Shuffle",
                    tint = if (player.shuffleEnabled) SpotGreen else SpotMuted,
                )
            }
            IconButton(onClick = player::skipPrevious) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", tint = SpotText, modifier = Modifier.size(38.dp))
            }
            FilledIconButton(
                onClick = player::togglePlayPause,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = SpotText, contentColor = Color.Black),
            ) {
                if (player.isLoading) CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 2.5.dp, color = Color.Black)
                else Icon(if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (player.isPlaying) "Pause" else "Play", modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = player::skipNext) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = SpotText, modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = player::cycleRepeatMode) {
                Icon(
                    if (player.repeatMode == SpotRepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repeat",
                    tint = if (player.repeatMode == SpotRepeatMode.OFF) SpotMuted else SpotGreen,
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        val loadedLyrics = lyrics
        val previewLines = when {
            lyricsLoading -> listOf("Loading lyrics…")
            loadedLyrics?.instrumental == true -> listOf("♪ Instrumental")
            loadedLyrics != null && loadedLyrics.synced.isNotEmpty() -> {
                val active = loadedLyrics.synced.indexOfLast { it.timeMs <= player.positionMs }.coerceAtLeast(0)
                loadedLyrics.synced.drop(active).map { it.text }.filter { it.isNotBlank() }.take(3)
            }
            !loadedLyrics?.plain.isNullOrBlank() ->
                loadedLyrics?.plain.orEmpty().lines().filter { it.isNotBlank() }.take(3)
            else -> listOf("Lyrics unavailable")
        }
        Surface(
            color = Color(0xFF244B37),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().animateContentSize().clickable { lyricsOpen = true },
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lyrics", color = SpotText, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Text("Open", color = SpotText.copy(alpha = 0.72f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(14.dp))
                previewLines.forEachIndexed { index, line ->
                    Text(
                        line,
                        color = if (index == 0) SpotText else SpotText.copy(alpha = 0.48f),
                        fontSize = if (index == 0) 20.sp else 16.sp,
                        lineHeight = if (index == 0) 25.sp else 21.sp,
                        fontWeight = if (index == 0) FontWeight.Black else FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
            }
        }
        Surface(
            color = SpotSurface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable { queueOpen = true },
        ) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.QueueMusic, null, tint = SpotMuted)
                Column(Modifier.padding(start = 9.dp)) {
                    Text("Queue", color = SpotText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(player.queue.size.toString() + " tracks", color = SpotMuted, fontSize = 9.sp)
                }
            }
        }
    }

    if (queueOpen) {
        ModalBottomSheet(
            onDismissRequest = { queueOpen = false },
            containerColor = Color(0xFF161616),
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    "Queue",
                    color = SpotText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
                if (player.queue.isEmpty()) {
                    Text("Nothing queued.", color = SpotMuted, modifier = Modifier.padding(18.dp))
                } else {
                    player.queue.forEachIndexed { index, item ->
                        Row(
                            Modifier.fillMaxWidth()
                                .semantics { contentDescription = "Queue " + item.title }
                                .clickable {
                                    player.selectQueueIndex(index)
                                    queueOpen = false
                                }
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(item, Modifier.size(48.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    item.title,
                                    color = SpotText,
                                    fontSize = 12.sp,
                                    fontWeight = if (index == player.currentIndex) FontWeight.Black else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(item.artist, color = SpotMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (index == player.currentIndex) {
                                Icon(Icons.Rounded.GraphicEq, "Playing", tint = SpotGreen)
                            }
                        }
                    }
                }
            }
        }
    }
    if (lyricsOpen) {
        ModalBottomSheet(
            onDismissRequest = { lyricsOpen = false },
            containerColor = Color(0xFF244B37),
        ) {
            val loaded = lyrics
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.90f).navigationBarsPadding().padding(bottom = 18.dp)
            ) {
                Text(
                    track.title,
                    color = SpotText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
                Text(
                    track.artist,
                    color = SpotText.copy(alpha = 0.66f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
                )

                when {
                    lyricsLoading -> LoadingBlock("Loading lyrics…")
                    loaded?.instrumental == true -> {
                        Text(
                            "This track is instrumental.",
                            color = SpotMuted,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                    loaded != null && loaded.synced.isNotEmpty() -> {
                        val activeIndex = loaded.synced.indexOfLast { it.timeMs <= player.positionMs }
                            .coerceAtLeast(0)
                        val listState = rememberLazyListState()
                        val isDragged by listState.interactionSource.collectIsDraggedAsState()
                        var followPlayback by remember(track.id) { mutableStateOf(true) }

                        LaunchedEffect(isDragged) {
                            if (isDragged) {
                                followPlayback = false
                            } else if (!followPlayback) {
                                delay(2200)
                                followPlayback = true
                            }
                        }
                        LaunchedEffect(activeIndex, followPlayback) {
                            if (followPlayback && activeIndex in loaded.synced.indices) {
                                listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
                            }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 18.dp,
                                vertical = 18.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(
                                count = loaded.synced.size,
                                key = { index -> loaded.synced[index].timeMs },
                            ) { index ->
                                val line = loaded.synced[index]
                                Text(
                                    line.text.ifBlank { "♪" },
                                    color = if (index == activeIndex) SpotText else SpotText.copy(alpha = 0.34f),
                                    fontSize = if (index == activeIndex) 29.sp else 24.sp,
                                    lineHeight = if (index == activeIndex) 34.sp else 30.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.clickable {
                                        if (player.durationMs > 0) {
                                            player.seekToFraction(
                                                (line.timeMs.toFloat() / player.durationMs).coerceIn(0f, 1f)
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                    !loaded?.plain.isNullOrBlank() -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
                        ) {
                            items(loaded?.plain.orEmpty().lines()) { line ->
                                Text(
                                    line.ifBlank { " " },
                                    color = SpotText,
                                    fontSize = 24.sp,
                                    lineHeight = 31.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            lyricsError ?: "Lyrics unavailable for this track.",
                            color = SpotMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun SpotSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }

    LaunchedEffect(progress, dragging) {
        if (!dragging) dragProgress = progress.coerceIn(0f, 1f)
    }

    val displayed = if (dragging) dragProgress else progress.coerceIn(0f, 1f)
    Box(
        modifier
            .height(22.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(widthPx) {
                detectTapGestures { offset ->
                    onSeek((offset.x / widthPx.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(widthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragProgress = (offset.x / widthPx.toFloat()).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        dragProgress = (change.position.x / widthPx.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        onSeek(dragProgress)
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.fillMaxWidth().height(3.dp)
                .background(Color(0xFF5A5A5A), RoundedCornerShape(2.dp))
        )
        Box(
            Modifier.fillMaxWidth(displayed.coerceAtLeast(0.001f)).height(3.dp)
                .background(SpotText, RoundedCornerShape(2.dp))
        )
        if (dragging) {
            Box(
                Modifier.fillMaxWidth(displayed.coerceAtLeast(0.001f)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.size(10.dp).background(SpotText, CircleShape))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SourceSessionBrowser(
    source: MusicSource,
    onConnected: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var session by remember(source) { mutableStateOf<BrowserSessionSpec?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf("Opening source browser…") }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        source.browserSession()
            .onSuccess {
                session = it
                status = "Complete the source page, then tap Done."
            }
            .onFailure {
                status = it.message ?: "This source did not provide a browser session."
            }
    }

    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(SpotBlack).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Close source browser", tint = SpotText)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    session?.title ?: source.name,
                    color = SpotText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(status, color = SpotMuted, fontSize = 10.sp)
            }
            Text(
                "Done",
                color = if (session != null && !saving) SpotGreen else SpotMuted,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = session != null && !saving) {
                    val view = webView ?: return@clickable
                    val spec = session ?: return@clickable
                    scope.launch {
                        saving = true
                        status = "Connecting source…"
                        persistSourceBrowserSession(view, source, spec)
                            .onSuccess { connected ->
                                status = if (connected) "Source connected"
                                else "The source did not detect a completed session."
                                if (connected) {
                                    onConnected()
                                    delay(350)
                                    onClose()
                                } else {
                                    saving = false
                                }
                            }
                            .onFailure {
                                status = it.message ?: "Could not save source session."
                                saving = false
                            }
                    }
                }.padding(12.dp),
            )
        }

        val spec = session
        if (spec == null) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (status == "Opening source browser…") {
                    CircularProgressIndicator(color = SpotGreen)
                    Text(status, color = SpotMuted, modifier = Modifier.padding(top = 12.dp))
                } else {
                    Text(status, color = SpotMuted, modifier = Modifier.padding(24.dp))
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl(spec.url)
                        webView = this
                    }
                },
            )
        }
    }
}

private suspend fun persistSourceBrowserSession(
    view: WebView,
    source: MusicSource,
    session: BrowserSessionSpec,
): Result<Boolean> {
    CookieManager.getInstance().flush()
    val visitorData = evaluateSessionScript(view, session.scripts["visitorData"])
    val dataSyncId = evaluateSessionScript(view, session.scripts["dataSyncId"])
    val authUser = evaluateSessionScript(view, session.scripts["authUser"]).ifBlank { "0" }
    val cookieUrl = view.url.orEmpty().ifBlank { session.url }
    val cookie = CookieManager.getInstance().getCookie(cookieUrl).orEmpty()
    val agent = view.settings.userAgentString.orEmpty()
    return source.storeBrowserSession(
        cookieHeader = cookie,
        userAgent = agent,
        visitorData = visitorData,
        dataSyncId = dataSyncId,
        authUser = authUser,
    )
}

private suspend fun evaluateSessionScript(view: WebView, script: String?): String {
    if (script.isNullOrBlank()) return ""
    return suspendCancellableCoroutine { continuation ->
        view.evaluateJavascript(script) { raw ->
            if (!continuation.isActive) return@evaluateJavascript
            val decoded = runCatching {
                if (raw.isNullOrBlank() || raw == "null") "" else JSONArray("[" + raw + "]").optString(0)
            }.getOrDefault("")
            continuation.resume(decoded)
        }
    }
}

private fun requiresSourceSession(message: String?): Boolean {
    val text = message.orEmpty()
    return text.contains("browser session", ignoreCase = true) ||
        text.contains("LOGIN_REQUIRED", ignoreCase = true) ||
        text.contains("sign in", ignoreCase = true) ||
        text.contains("not a bot", ignoreCase = true) ||
        text.contains("confirm you", ignoreCase = true)
}

@Composable
private fun LoadingBlock(label: String) {
    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = SpotGreen)
        Text(label, color = SpotMuted, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun ErrorBlock(message: String, action: () -> Unit) {
    Surface(
        color = SpotSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (message.contains("search", ignoreCase = true)) "Search didn’t load." else "This section didn’t load.",
                color = SpotMuted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Try again",
                color = SpotText,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = action).padding(start = 14.dp),
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
