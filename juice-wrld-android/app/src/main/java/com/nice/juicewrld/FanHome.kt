package com.nice.juicewrld

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders

private val Night = Color(0xFF08080E)
private val Panel = Color(0xFF15131F)
private val Panel2 = Color(0xFF211C2E)
private val Hairline = Color(0xFF292636)
private val White = Color(0xFFF7F3FF)
private val Gray = Color(0xFFA9A3B6)
private val Lavender = Color(0xFFB997F6)
private val Rose = Color(0xFFFA9BBB)

private val scheme = darkColorScheme(
    primary = Lavender, onPrimary = Night, background = Night, onBackground = White,
    surface = Panel, onSurface = White, outline = Hairline
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This is a fullscreen fan experience. Keep the navigation gesture bar,
        // but let the status bar appear temporarily on a deliberate edge swipe.
        window.navigationBarColor = android.graphics.Color.rgb(8, 8, 14)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
        setContent {
            MaterialTheme(colorScheme = scheme) {
                FanHome()
            }
        }
    }
}

@Composable
fun FanHome(player: FanPlayer = viewModel()) {
    val state by player.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var moodName by rememberSaveable { mutableStateOf(Mood.ALL.name) }
    var search by rememberSaveable { mutableStateOf("") }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    var nowPlayingVisible by rememberSaveable { mutableStateOf(false) }
    val mood = Mood.entries.firstOrNull { it.name == moodName } ?: Mood.ALL
    val songs = remember(mood, search, state.favorites) {
        FanSongs.filter(mood, search, state.favorites)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Night).statusBarsPadding().navigationBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("songs"),
            contentPadding = PaddingValues(bottom = 25.dp),
        ) {
            item {
                FanHeader()
            }
            item {
                FanHero()
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pick a feeling", fontSize = 20.sp, letterSpacing = (-0.7).sp,
                        color = White, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            moodName = if (mood == Mood.FAVORITES) Mood.ALL.name
                                else Mood.FAVORITES.name
                        },
                        modifier = Modifier.heightIn(min = 44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            if (mood == Mood.FAVORITES) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null, tint = if (mood == Mood.FAVORITES) Rose else Lavender,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Favorites", color = if (mood == Mood.FAVORITES) Rose else Lavender,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                LazyRow(
                    modifier = Modifier.testTag("moods"),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    items(
                        listOf(Mood.ALL, Mood.LOVE, Mood.SAD, Mood.HEARTBREAK, Mood.HYPE, Mood.CHILL),
                        key = { it.name }
                    ) { item ->
                        FeelingCard(
                            mood = item,
                            selected = mood == item,
                            count = FanSongs.count(item, state.favorites),
                            onClick = { moodName = if (mood == item) Mood.ALL.name else item.name }
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 13.dp, bottom = 5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (mood) {
                                    Mood.ALL -> "All songs"
                                    Mood.FAVORITES -> "Your favorites"
                                    else -> mood.label + " songs"
                                },
                                fontSize = 21.sp, letterSpacing = (-0.7).sp,
                                color = White, fontWeight = FontWeight.ExtraBold
                            )
                            if (search.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${songs.size} " + if (songs.size == 1) "match" else "matches",
                                    fontSize = 12.sp, color = Gray
                                )
                            }
                        }
                        if (songs.any { it.id in state.attached }) {
                            IconButton(
                                onClick = { player.playMood(mood) },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow, contentDescription = "Play this mood",
                                    tint = Lavender, modifier = Modifier.size(25.dp)
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    Spacer(Modifier.height(15.dp))
                    FanSearch(
                        text = search,
                        onTextChange = { search = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (songs.isEmpty()) {
                item { FanEmpty(mood, search) }
            } else {
                items(songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        favorite = song.id in state.favorites,
                        attached = song.id in state.attached,
                        playing = song.id == state.activeSongId && state.isPlaying,
                        onFavorite = { player.toggleFavorite(song.id) },
                        onOpen = { openedId = song.id },
                        onPlay = { player.playInMood(song.id, mood) }
                    )
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 29.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalDivider(color = Hairline, thickness = 1.dp)
                    Spacer(Modifier.height(21.dp))
                    Text("An unofficial fan-made collection.", color = Gray, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Portrait: Lexiou WesCudi · CC BY-SA 2.0",
                        color = Gray, fontSize = 10.sp,
                        modifier = Modifier.clickable { openPortraitCredit(context) }
                    )
                }
            }
        }
        if (state.activeSongId != null) {
            MiniPlayer(
                state = state,
                song = FanSongs.find(state.activeSongId!!),
                onToggle = { player.play(state.activeSongId!!) },
                onSkip = player::skip,
                onSeek = player::seekTo,
                onOpen = { nowPlayingVisible = true }
            )
        }
    }

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = player::dismissError,
            confirmButton = { TextButton(onClick = player::dismissError) { Text("OK") } },
            title = { Text("Audio unavailable") },
            text = { Text(state.error ?: "") },
            containerColor = Panel2, titleContentColor = White, textContentColor = Gray
        )
    }
    val selected = openedId?.let(FanSongs::find)
    if (selected != null) {
        SongSheet(
            song = selected,
            favorite = selected.id in state.favorites,
            attached = selected.id in state.attached,
            playing = selected.id == state.activeSongId && state.isPlaying,
            preparing = selected.id == state.activeSongId && state.isPreparing,
            shuffle = state.shuffle,
            repeat = state.repeat,
            onShuffle = player::toggleShuffle,
            onRepeat = player::toggleRepeat,
            memory = state.memories[selected.id].orEmpty(),
            onDismiss = { openedId = null },
            onFavorite = { player.toggleFavorite(selected.id) },
            onPlay = {
                player.playInMood(selected.id, mood)
                openedId = null
                nowPlayingVisible = true
            },
            onSaveMemory = { player.saveMemory(selected.id, it) },
            onShare = { shareSong(context, selected) },
            downloadAvailable = LicensedAudioCatalog.find(context, selected.id) != null,
            downloading = state.downloadingSongId == selected.id,
            onDownload = { player.downloadLicensedSong(selected.id) },
            onOfficial = {
                openOfficialSong(context, selected)
            }
        )
    }
    if (nowPlayingVisible) {
        val playingSong = state.activeSongId?.let(FanSongs::find)
        if (playingSong != null) {
            NowPlayingScreen(
                song = playingSong,
                state = state,
                onClose = { nowPlayingVisible = false },
                onTogglePlay = { player.play(playingSong.id) },
                onPrevious = { player.skip(-1) },
                onNext = { player.skip(1) },
                onSeek = player::seekTo,
                onFavorite = { player.toggleFavorite(playingSong.id) },
                onShuffle = player::toggleShuffle,
                onRepeat = player::toggleRepeat
            )
        } else {
            LaunchedEffect(Unit) { nowPlayingVisible = false }
        }
    }

}

@Composable
private fun FanHeader() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Lavender, Color(0xFF7850B8)))),
            contentAlignment = Alignment.Center
        ) {
            Text("999", color = Night, fontSize = 13.sp,
                letterSpacing = (-1.5).sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text("JUICE WRLD", color = White, fontSize = 17.sp,
                letterSpacing = 1.6.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(2.dp))
            Text("THE 999 FAN SPACE", color = Gray, fontSize = 9.sp,
                letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FanHero() {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 5.dp)
            .heightIn(min = 160.dp).clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF42235C), Color(0xFF251936), Color(0xFF151121))
                )
            )
    ) {
        Text("999",
            fontSize = 112.sp, fontWeight = FontWeight.Black,
            color = White.copy(alpha = 0.055f), letterSpacing = (-7).sp,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-18).dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 17.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("999 FOREVER", color = Rose, fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.1.sp, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Songs for\nevery feeling.",
                    color = White, fontWeight = FontWeight.Black, fontSize = 24.sp,
                    lineHeight = 27.sp, letterSpacing = (-1).sp
                )
                // Keep the hero editorial, not a second catalogue statistics block.
            }
            ArtistPortrait(
                Modifier.width(103.dp).height(137.dp).clip(RoundedCornerShape(14.dp))
            )
        }
    }
}


@Composable
private fun ArtistPortrait(modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(context) {
        ImageRequest.Builder(context)
            .data("https://upload.wikimedia.org/wikipedia/commons/3/33/Juice_WRLD_-_Les_Ardentes_2019_%28cropped_2%29.jpg")
            .httpHeaders(NetworkHeaders.Builder().set(
                "User-Agent", "NIGHTJuiceWRLD/0.2.0 (https://github.com/Tomex777/Build)"
            ).build())
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = "Juice WRLD performing in 2019, photo by Lexiou WesCudi",
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = { CoverPlaceholder("Juice WRLD", Modifier.fillMaxSize()) },
        error = { CoverPlaceholder("Juice WRLD", Modifier.fillMaxSize()) },
        success = { SubcomposeAsyncImageContent(contentScale = ContentScale.Crop) }
    )
}

private fun openPortraitCredit(context: Context) {
    val url = "https://commons.wikimedia.org/wiki/File:Juice_WRLD_-_Les_Ardentes_2019_(cropped_2).jpg"
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (_: ActivityNotFoundException) { }
}

private fun moodColors(mood: Mood): List<Color> = when (mood) {
    Mood.LOVE -> listOf(Color(0xFF914466), Color(0xFF2D1C3F))
    Mood.HEARTBREAK -> listOf(Color(0xFF754461), Color(0xFF231C35))
    Mood.SAD -> listOf(Color(0xFF4D547E), Color(0xFF1B223D))
    Mood.HYPE -> listOf(Color(0xFF78444E), Color(0xFF342137))
    Mood.CHILL -> listOf(Color(0xFF376E77), Color(0xFF1A3448))
    else -> listOf(Color(0xFF6F5187), Color(0xFF241D34))
}

private fun songCountLabel(count: Int): String =
    if (count == 1) "1 song" else "${count} songs"

@Composable
private fun FeelingCard(mood: Mood, selected: Boolean, count: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    val colors = moodColors(mood)
    Column(
        modifier = Modifier
            .width(125.dp).height(99.dp)
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) Lavender.copy(alpha = 0.95f)
                    else White.copy(alpha = 0.10f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 13.dp, top = 13.dp, bottom = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Small color mark, not a repeated giant "9" watermark obscuring the labels.
            Box(
                Modifier.width(23.dp).height(3.dp)
                    .clip(CircleShape).background(if (selected) White else White.copy(alpha = .55f))
            )
            Spacer(Modifier.weight(1f))
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = White, modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            mood.label, color = White, fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        Text(
            songCountLabel(count), color = White.copy(alpha = .77f),
            fontSize = 11.sp, maxLines = 1
        )
    }
}

@Composable
private fun FanSearch(text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        modifier.height(50.dp).clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .border(1.dp, Hairline.copy(alpha = .7f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Gray,
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = text, onValueChange = onTextChange, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            modifier = Modifier.weight(1f).semantics { contentDescription = "Search Juice WRLD songs" },
            cursorBrush = SolidColor(Lavender),
            textStyle = TextStyle(fontSize = 14.sp, color = White),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("Search songs or albums", color = Gray, fontSize = 14.sp)
                    inner()
                }
            }
        )
        if (text.isNotEmpty()) {
            IconButton(onClick = { onTextChange("") }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = Gray,
                    modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun SongRow(
    song: FanSong, favorite: Boolean, attached: Boolean, playing: Boolean,
    onFavorite: () -> Unit, onOpen: () -> Unit, onPlay: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 80.dp)
            .clickable(onClick = onOpen)
            .padding(start = 22.dp, end = 17.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumCover(song.album, Modifier.size(58.dp).clip(RoundedCornerShape(13.dp)), song.title)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(song.album, color = Gray, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (playing) {
            Text("♪", color = Lavender, fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 4.dp))
        } else if (attached) {
            IconButton(onClick = onPlay, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.PlayArrow, tint = Lavender,
                    contentDescription = "Play " + song.title + " from device", modifier = Modifier.size(23.dp))
            }
        }
        IconButton(
            onClick = onFavorite,
            modifier = Modifier.size(44.dp).semantics {
                contentDescription = if (favorite) "Unfavorite " + song.title else "Favorite " + song.title
            }
        ) {
            Icon(
                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (favorite) Rose else Color(0xFF80798D),
                contentDescription = null, modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun albumArt(album: String): String? = when (album) {
    "Goodbye & Good Riddance" -> "https://coverartarchive.org/release/5d4d8364-f9fa-4744-bec3-ba9f2b46fb39/front-250"
    "Death Race for Love" -> "https://coverartarchive.org/release/c89c95de-e9b6-453a-a593-45e3a28eb256/front-250"
    "Legends Never Die" -> "https://coverartarchive.org/release/a9820d2a-86f3-44c8-82d7-e05e28d4a5f2/front-250"
    "Fighting Demons" -> "https://coverartarchive.org/release/1900e0cf-4cd9-4681-93d3-0c71bf88771f/front-250"
    "WRLD ON DRUGS" -> "https://coverartarchive.org/release/b40009d1-0993-4328-b6ac-c848a980e2b1/front-250"
    "The Party Never Ends" -> "https://coverartarchive.org/release/454e3e80-b263-4604-ad79-693f2fde37b0/front-250"
    else -> null
}

@Composable
internal fun AlbumCover(album: String, modifier: Modifier, title: String) {
    val art = albumArt(album)
    if (art == null) {
        CoverPlaceholder(album, modifier)
    } else {
        SubcomposeAsyncImage(
            model = art,
            contentDescription = album + " album artwork",
            modifier = modifier, contentScale = ContentScale.Crop,
            loading = { CoverPlaceholder(album, Modifier.fillMaxSize()) },
            error = { CoverPlaceholder(album, Modifier.fillMaxSize()) },
            success = { SubcomposeAsyncImageContent(contentScale = ContentScale.Crop) }
        )
    }
}

@Composable
private fun CoverPlaceholder(album: String, modifier: Modifier) {
    val seed = (album.hashCode() and Int.MAX_VALUE) % 5
    val tone = listOf(
        listOf(Color(0xFF75466C), Color(0xFF242036)),
        listOf(Color(0xFF394D84), Color(0xFF1A1F39)),
        listOf(Color(0xFF69508A), Color(0xFF25203D)),
        listOf(Color(0xFF89566B), Color(0xFF322039)),
        listOf(Color(0xFF486F77), Color(0xFF202B37))
    )[seed]
    BoxWithConstraints(
        modifier.background(Brush.linearGradient(tone)),
        contentAlignment = Alignment.Center
    ) {
        // Small row covers must not wrap "GBGR" into the second line.
        val compact = maxWidth < 82.dp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (album) {
                    "Goodbye & Good Riddance" -> "GBGR"
                    "Death Race for Love" -> "DRFL"
                    "Legends Never Die" -> "LND"
                    "Fighting Demons" -> "FD"
                    "The Party Never Ends" -> "TPNE"
                    "WRLD ON DRUGS" -> "WRLD"
                    "Juice WRLD" -> "JW"
                    else -> "999"
                },
                fontSize = if (compact) 14.sp else 26.sp,
                fontWeight = FontWeight.Black,
                color = White.copy(alpha = 0.68f),
                letterSpacing = if (compact) (-0.7).sp else (-1.3).sp,
                maxLines = 1, softWrap = false,
            )
            Spacer(Modifier.height(2.dp))
            Text(if (album == "Juice WRLD") "999 FOREVER" else "999",
                fontSize = if (compact) 7.sp else 9.sp,
                color = White.copy(alpha = 0.63f), maxLines = 1, softWrap = false,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = if (compact) 0.4.sp else 1.8.sp)
        }
    }
}

@Composable
private fun FanEmpty(mood: Mood, search: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 45.dp, bottom = 35.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Lavender,
            modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(13.dp))
        Text(
            if (mood == Mood.FAVORITES && search.isEmpty())
                "No favorites yet"
            else "No matching songs",
            fontWeight = FontWeight.Bold, fontSize = 17.sp, color = White
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (mood == Mood.FAVORITES && search.isEmpty())
                "Heart a song and find it here."
            else "Try another feeling or search.",
            color = Gray, fontSize = 12.sp
        )
    }
}

@Composable
private fun MiniPlayer(
    state: FanPlayerState, song: FanSong?,
    onToggle: () -> Unit, onSkip: (Int) -> Unit,
    onSeek: (Float) -> Unit, onOpen: () -> Unit
) {
    if (song == null) return
    Column(Modifier.fillMaxWidth().background(Color(0xFF1D182B))) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumCover(song.album, Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)), song.title)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).clickable(onClick = onOpen)) {
                Text(song.title, fontSize = 13.sp, color = White, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("From your device", fontSize = 11.sp, color = Gray)
            }
            IconButton(onClick = { onSkip(-1) }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous imported song",
                    tint = White)
            }
            IconButton(
                onClick = onToggle, modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(Lavender),
                enabled = !state.isPreparing,
            ) {
                if (state.isPreparing) {
                    CircularProgressIndicator(modifier = Modifier.size(17.dp),
                        strokeWidth = 2.dp, color = Night)
                } else {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = Night, modifier = Modifier.size(23.dp)
                    )
                }
            }
            IconButton(onClick = { onSkip(1) }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next imported song", tint = White)
            }
        }
        if (state.durationMs > 0) {
            var dragging by remember(state.activeSongId) { mutableStateOf<Float?>(null) }
            Slider(
                value = dragging ?: (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let(onSeek)
                    dragging = null
                },
                modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 15.dp),
                colors = SliderDefaults.colors(thumbColor = Lavender, activeTrackColor = Lavender,
                    inactiveTrackColor = Color(0xFF4C435B))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongSheet(
    song: FanSong, favorite: Boolean, attached: Boolean,
    playing: Boolean, preparing: Boolean, shuffle: Boolean, repeat: RepeatMode,
    onShuffle: () -> Unit, onRepeat: () -> Unit, memory: String,
    onDismiss: () -> Unit, onFavorite: () -> Unit,
    onPlay: () -> Unit, onDownload: () -> Unit, onOfficial: () -> Unit,
    downloadAvailable: Boolean, downloading: Boolean,
    onSaveMemory: (String) -> Unit, onShare: () -> Unit
) {
    var memoryDraft by rememberSaveable(song.id) { mutableStateOf(memory) }
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = Color(0xFF181521),
        contentColor = White, dragHandle = {
            Box(
                Modifier.padding(top = 12.dp, bottom = 18.dp).width(46.dp).height(4.dp)
                    .clip(CircleShape).background(Color(0xFF696174))
            )
        }
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 660.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 23.dp).navigationBarsPadding().padding(bottom = 25.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlbumCover(
                song.album, Modifier.size(152.dp).clip(RoundedCornerShape(20.dp)), song.title
            )
            Spacer(Modifier.height(16.dp))
            Text(song.title, color = White, fontSize = 25.sp,
                fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(song.album + " · " + song.year, color = Gray, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                song.moods.take(2).forEach { mood ->
                    Text(
                        mood.label, color = Lavender, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.clip(CircleShape).background(Panel2)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = when {
                        attached -> onPlay
                        downloadAvailable -> onDownload
                        else -> onOfficial
                    },
                    enabled = !preparing && !downloading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Night),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(
                        if (attached) {
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow
                        } else if (downloadAvailable) Icons.Filled.Download
                        else Icons.Filled.OpenInNew,
                        contentDescription = null, modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (attached) {
                            if (playing) "Pause audio" else "Play my audio"
                        } else if (downloadAvailable) {
                            if (downloading) "Downloading…" else "Download"
                        } else "Listen on YouTube Music",
                        fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                }
                IconButton(
                    onClick = onFavorite,
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp))
                        .background(Panel2)
                ) {
                    Icon(
                        if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (favorite) "Remove favorite" else "Add favorite",
                        tint = if (favorite) Rose else White
                    )
                }
            }
            if (attached) {
                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    OutlinedButton(
                        onClick = onShuffle, modifier = Modifier.weight(1f).height(40.dp),
                        border = BorderStroke(1.dp, if (shuffle) Lavender else Hairline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (shuffle) Lavender else Gray
                        )
                    ) {
                        Text(if (shuffle) "Shuffle on" else "Shuffle", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onRepeat, modifier = Modifier.weight(1f).height(40.dp),
                        border = BorderStroke(1.dp, if (repeat != RepeatMode.OFF) Lavender else Hairline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (repeat != RepeatMode.OFF) Lavender else Gray
                        )
                    ) {
                        Text(repeat.label, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR MEMORY", color = Lavender, fontSize = 10.sp,
                        letterSpacing = 2.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Keep what this song means to you.", color = Gray,
                        fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                if (memoryDraft.trim() != memory) {
                    TextButton(onClick = { onSaveMemory(memoryDraft) }) {
                        Text("Save", color = Lavender, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = onShare) {
                    Text("Share song", color = Lavender, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = memoryDraft, onValueChange = { memoryDraft = it.take(600) },
                minLines = 2, maxLines = 5,
                cursorBrush = SolidColor(Lavender),
                textStyle = TextStyle(fontSize = 13.sp, color = White, lineHeight = 19.sp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp)
                    .clip(RoundedCornerShape(14.dp)).background(Panel2)
                    .padding(14.dp)
                    .semantics { contentDescription = "Personal memory about this song" },
                decorationBox = { inner ->
                    Box {
                        if (memoryDraft.isEmpty()) {
                            Text("A lyric, a moment, a feeling…", fontSize = 13.sp, color = Gray)
                        }
                        inner()
                    }
                }
            )

        }
    }
}

private fun openOfficialSong(context: Context, song: FanSong) {
    val q = Uri.encode("Juice WRLD " + song.title)
    val url = "https://music.youtube.com/search?q=" + q
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No browser available",
            android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun shareSong(context: Context, song: FanSong) {
    val search = "https://music.youtube.com/search?q=" + Uri.encode("Juice WRLD " + song.title)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Juice WRLD — " + song.title + "\n" + search)
    }
    try { context.startActivity(Intent.createChooser(intent, "Share this song")) }
    catch (_: ActivityNotFoundException) { }
}
