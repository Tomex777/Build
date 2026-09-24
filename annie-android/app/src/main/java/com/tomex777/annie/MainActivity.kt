package com.tomex777.annie

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Night = Color(0xFF07111E)
private val Panel = Color(0xFF0D1B2C)
private val Bubble = Color(0xFF13243A)
private val Blue = Color(0xFF168EEA)
private val SoftText = Color(0xFF9CB2CC)
private val BrightText = Color(0xFFEEF5FF)
private val Teal = Color(0xFF54D6AE)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AnnieTheme { AnnieChat() } }
    }
}

@Composable
private fun AnnieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF36A8F4),
            onPrimary = BrightText,
            background = Night,
            surface = Panel,
            onSurface = BrightText,
            secondary = Teal
        ),
        content = content
    )
}

internal data class ChatEntry(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val catalog: List<CatalogItem> = emptyList(),
    val menuTitle: String? = null,
    val actions: List<String> = emptyList(),
    val searchMedia: String? = null,
    val searchInitial: String = "",
    val selectedItem: CatalogItem? = null,
    val selectedStage: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnieChat() {
    val context = LocalContext.current
    val messages = remember {
        mutableStateListOf(ChatEntry(1, false, "Hi, I’m Annie. What are you in the mood for? Type a command to start. Providers stay separate, and I’ll show clearly when one is unavailable."))
    }
    var draft by remember { mutableStateOf("") }
    var activeSheet by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(messages.size, keyboardVisible) {
        if (messages.size > 1) listState.animateScrollToItem(messages.lastIndex)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun addAnnie(
        text: String,
        catalog: List<CatalogItem> = emptyList(),
        menuTitle: String? = null,
        actions: List<String> = emptyList(),
        searchMedia: String? = null,
        searchInitial: String = "",
        selectedItem: CatalogItem? = null,
        selectedStage: String? = null
    ) {
        messages.add(ChatEntry(System.nanoTime(), false, text, catalog, menuTitle, actions, searchMedia, searchInitial, selectedItem, selectedStage))
        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
    }

    fun openSearch(media: String, query: String = "") {
        addAnnie("", searchMedia = media, searchInitial = query)
    }

    val downloads = remember { mutableStateListOf<DownloadItem>().apply { addAll(DownloadStore.read(context)) } }

    fun openDownloads(mediaFilter: String = "All") {
        downloads.clear()
        downloads.addAll(DownloadStore.read(context))
        activeSheet = "Downloads:$mediaFilter"
    }

    fun openSelectedTitle(item: CatalogItem) {
        selectedDetailsStage(item)?.let { addAnnie("", selectedItem = item, selectedStage = it) }
    }

    fun openCategory(category: String) {
        when (category) {
            "Anime" -> addAnnie("Choose an action or type a title to search.", menuTitle = category,
                actions = listOf("Search anime", "Recently aired", "Continue watching", "Downloads"))
            "Movies & TV" -> addAnnie("Choose an action or type a title to search.", menuTitle = category,
                actions = listOf("Search movies", "Search TV series", "Recently released", "Continue watching", "Downloads"))
            "Manga" -> addAnnie("Choose an action or type a title to search.", menuTitle = category,
                actions = listOf("Search manga", "Recently updated", "Continue reading", "Downloads"))
            "Music" -> addAnnie("Choose an action or type a song to search.", menuTitle = category,
                actions = listOf("Search music", "Open YouTube link"))
            else -> activeSheet = category
        }
    }

    fun handleMenuAction(category: String, action: String) {
        when (category to action) {
            "Anime" to "Search anime" -> openSearch("anime")
            "Anime" to "Recently aired" -> addAnnie("No episodes found yet. Connect an anime extension to check episode availability.", menuTitle = "New anime episodes", actions = listOf("Today", "This week", "All"))
            "Anime" to "Continue watching" -> addAnnie("Nothing to continue watching yet.", menuTitle = "Continue watching")
            "Anime" to "Downloads" -> openDownloads("Anime")
            "Movies & TV" to "Search movies" -> openSearch("movie")
            "Movies & TV" to "Search TV series" -> openSearch("tv")
            "Movies & TV" to "Recently released" -> addAnnie("Recently released titles need a connected movie extension.")
            "Movies & TV" to "Continue watching" -> addAnnie("Nothing to continue watching yet.", menuTitle = "Continue watching")
            "Movies & TV" to "Downloads" -> openDownloads("Movies")
            "Manga" to "Search manga" -> openSearch("manga")
            "Manga" to "Recently updated" -> addAnnie("Recently updated chapters need a connected manga extension.")
            "Manga" to "Continue reading" -> addAnnie("Nothing to continue reading yet.", menuTitle = "Continue reading")
            "Manga" to "Downloads" -> openDownloads("Manga")
            "Music" to "Search music" -> openSearch("music")
            "Music" to "Open YouTube link" -> draft = "/music "
            "New anime episodes" to "Today", "New anime episodes" to "This week", "New anime episodes" to "All" ->
                addAnnie(recentEpisodesUnavailableMessage(action))
            else -> openDownloads()
        }
    }

    fun startSearch(media: String, query: String) {
        openSearch(media, query)
    }

    fun submit() {
        val value = draft.trim()
        if (value.isEmpty()) return
        messages.add(ChatEntry(System.nanoTime(), true, value))
        draft = ""
        val parts = value.split(Regex("\\s+"), limit = 2)
        val command = parts.firstOrNull()?.lowercase().orEmpty()
        val query = parts.getOrNull(1)?.trim().orEmpty()
        when (command) {
            "/anime" -> when {
                query.isBlank() -> openCategory("Anime")
                query.equals("search", true) -> startSearch("anime", "")
                query.startsWith("search ", true) -> startSearch("anime", query.substringAfter(" ", "").trim())
                query.equals("download", true) || query.equals("downloads", true) -> openDownloads("Anime")
                query.equals("recent", true) || query.equals("recently aired", true) -> handleMenuAction("Anime", "Recently aired")
                query.equals("continue", true) || query.equals("continue watching", true) -> addAnnie("Nothing to continue watching yet.", menuTitle = "Continue watching")
                else -> startSearch("anime", query)
            }
            "/manga" -> when {
                query.isBlank() -> openCategory("Manga")
                query.equals("search", true) -> startSearch("manga", "")
                query.startsWith("search ", true) -> startSearch("manga", query.substringAfter(" ", "").trim())
                query.equals("download", true) || query.equals("downloads", true) -> openDownloads("Manga")
                query.equals("continue", true) || query.equals("continue reading", true) -> addAnnie("Nothing to continue reading yet.", menuTitle = "Continue reading")
                else -> startSearch("manga", query)
            }
            "/movie", "/movies" -> when {
                query.isBlank() -> openCategory("Movies & TV")
                query.equals("search", true) -> startSearch("movie", "")
                query.startsWith("search ", true) -> startSearch("movie", query.substringAfter(" ", "").trim())
                query.equals("download", true) || query.equals("downloads", true) -> openDownloads("Movies")
                query.equals("continue", true) || query.equals("continue watching", true) -> addAnnie("Nothing to continue watching yet.", menuTitle = "Continue watching")
                else -> startSearch("movie", query)
            }
            "/tv", "/series" -> when {
                query.isBlank() -> startSearch("tv", "")
                query.equals("series", true) -> startSearch("tv", "")
                query.equals("search", true) -> startSearch("tv", "")
                query.startsWith("search ", true) -> startSearch("tv", query.substringAfter(" ", "").trim())
                query.equals("continue", true) || query.equals("continue watching", true) -> addAnnie("Nothing to continue watching yet.", menuTitle = "Continue watching")
                else -> startSearch("tv", query)
            }
            "/music" -> {
                if (query.startsWith("https://", true) || query.startsWith("http://", true)) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(query))) }
                    addAnnie("Opening that link in the official app or browser.")
                } else if (query.isBlank()) openCategory("Music")
                else addAnnie("For music playback, paste a YouTube link. Annie keeps playback in YouTube’s official player.")
            }
            "/downloads" -> openDownloads()
            "/continue" -> addAnnie("Nothing to continue watching yet.", menuTitle = "Continue watching")
            "/extensions", "/settings" -> openCategory("Extensions")
            "/help" -> addAnnie("Try /anime, /movie, /tv, /manga, /music, /downloads, or /extensions.")
            else -> addAnnie("Try a slash command: /anime, /movie, /tv, /manga, /music, or /downloads.")
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Night) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().testTag("chat_root")) {
            AnnieTopBar(onExtensions = { activeSheet = "Extensions" })
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("conversation"),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.Top)
            ) {
                items(messages, key = { it.id }) { entry ->
                    ChatBubble(
                        entry,
                        onCatalogClick = ::openSelectedTitle,
                        onActionClick = ::handleMenuAction,
                        onOpenSource = { sourceUrl ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl))) }
                        },
                        onSeriesAction = { item, stage, season ->
                            if (stage == "play") {
                                addAnnie("Playback is unavailable until an anime streaming source is connected.")
                            } else {
                                addAnnie("", selectedItem = season?.asCatalogItem() ?: item, selectedStage = stage)
                            }
                        }
                    )
                }
            }
            Composer(
                value = draft,
                onValueChange = { draft = it },
                onSuggestionSelected = { draft = "$it " },
                onSend = { submit() },
                onMenu = { activeSheet = "Attachments" }
            )
        }
    }

    if (activeSheet != null) {
        val category = activeSheet!!
        ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState,
            containerColor = Panel,
            contentColor = BrightText,
        ) {
            if (category.startsWith("Downloads:")) {
                DownloadsManagerContent(
                    items = downloads,
                    onRemove = { item ->
                        downloads.remove(item)
                        DownloadStore.write(context, downloads)
                    },
                    onStateChange = { item, state ->
                        val index = downloads.indexOfFirst { it.id == item.id }
                        if (index >= 0) {
                            downloads[index] = downloads[index].copy(state = state)
                            DownloadStore.write(context, downloads)
                        }
                    },
                    initialMediaFilter = category.substringAfter(":", "All"),
                )
            } else CommandSheet(category = category) { action ->
                activeSheet = null
                when (action) {
                    "Search anime" -> openSearch("anime")
                    "Recently aired" -> handleMenuAction("Anime", "Recently aired")
                    "Continue watching" -> addAnnie("Nothing to continue watching yet.", menuTitle = "Continue watching")
                    "Downloads" -> openDownloads(when (category) {
                        "Anime" -> "Anime"
                        "Manga" -> "Manga"
                        "Movies & TV" -> "Movies"
                        else -> "All"
                    })
                    "Search movies" -> openSearch("movie")
                    "Search TV series" -> openSearch("tv")
                    "Recently released" -> addAnnie("Recently released titles need a connected movie extension.")
                    "Search manga" -> openSearch("manga")
                    "Recently updated" -> addAnnie("Recently updated chapters need a connected manga extension.")
                    "Continue reading" -> addAnnie("Nothing to continue reading yet.", menuTitle = "Continue reading")
                    "Search music", "Open YouTube link" -> openSearch("music")
                    else -> addAnnie("AniList provides anime and manga metadata; Wikidata provides movie metadata; TVmaze provides TV metadata. Search results do not provide playable or downloadable files.")
                }
            }
        }
    }
}

@Composable
private fun AnnieTopBar(onExtensions: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(68.dp).background(Panel).padding(horizontal = 16.dp).testTag("top_bar"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF8ED2FF), Color(0xFF245287)))),
            contentAlignment = Alignment.Center
        ) { Text("A", fontWeight = FontWeight.Bold, color = BrightText, fontSize = 18.sp) }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text("Annie", color = BrightText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Teal))
                Text("  Ready when you are", color = SoftText, fontSize = 12.sp)
            }
        }
        Text(
            "⋮",
            modifier = Modifier.clip(CircleShape).clickable(onClick = onExtensions).padding(horizontal = 12.dp, vertical = 6.dp),
            color = SoftText,
            fontSize = 24.sp
        )
    }
}

@Composable
private fun WelcomePanel() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF18385A), Color(0xFF102238), Color(0xFF101A2A)))
            ).padding(20.dp)
        ) {
            Column {
                Text("YOUR MEDIA, IN ONE CHAT", color = Color(0xFF7EC8FF), fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("What are you in the mood for?", color = BrightText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Search, pick up where you left off, or browse your library.", color = SoftText, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Anime", "Manga", "Music").forEachIndexed { index, name ->
                        Box(
                            Modifier.weight(1f).height(68.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name, color = Color(0xFFB7DFFF), fontSize = if (index < 2) 18.sp else 25.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatBubble(
    entry: ChatEntry,
    onCatalogClick: (CatalogItem) -> Unit,
    onActionClick: (String, String) -> Unit,
    onOpenSource: (String) -> Unit,
    onSeriesAction: (CatalogItem, String, SeasonItem?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (entry.fromUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!entry.fromUser) {
            Box(
                Modifier.padding(end = 9.dp, top = 18.dp).size(32.dp).clip(CircleShape).background(Color(0xFF274C78)),
                contentAlignment = Alignment.Center
            ) { Text("A", color = BrightText, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
        Column(
            modifier = Modifier.fillMaxWidth(0.88f),
            horizontalAlignment = if (entry.fromUser) Alignment.End else Alignment.Start
        ) {
            Text(if (entry.fromUser) "You" else "Annie", color = SoftText, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, bottom = 5.dp))
            if (entry.searchMedia != null) {
                SearchMessage(entry.searchMedia, entry.searchInitial, onCatalogClick)
            } else if (entry.selectedItem != null) {
                when (entry.selectedStage) {
                    "series" -> SeriesCardMessage(entry.selectedItem) { stage ->
                        onSeriesAction(entry.selectedItem, stage, null)
                    }
                    "movie" -> MediaMetadataMessage(entry.selectedItem, "Movie", onOpenSource)
                    "tv" -> MediaMetadataMessage(entry.selectedItem, "TV series", onOpenSource)
                    "seasons" -> SeasonListMessage(entry.selectedItem) { season ->
                        onSeriesAction(entry.selectedItem, "episodes", season)
                    }
                    "episodes" -> EpisodeListMessage(entry.selectedItem)
                    "chapters" -> MangaChapterListMessage(entry.selectedItem)
                    "reader" -> MangaReaderUnavailableMessage(entry.selectedItem)
                    else -> MangaResultMessage(entry.selectedItem) { stage ->
                        onSeriesAction(entry.selectedItem, stage, null)
                    }
                }
            } else if (entry.menuTitle != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
                        .background(Bubble).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(entry.menuTitle, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(entry.text, color = SoftText, fontSize = 14.sp, lineHeight = 20.sp)
                    entry.actions.forEach { action ->
                        val icon = when {
                            action.startsWith("Search") -> "search"
                            action.contains("aired") || action.contains("released") || action.contains("updated") || action == "Today" || action == "This week" || action == "All" -> "history"
                            action.contains("Continue") -> "play"
                            action == "Downloads" -> "download"
                            else -> "music"
                        }
                        Surface(
                            color = Color(0xFF10263D),
                            shape = RoundedCornerShape(15.dp),
                            border = BorderStroke(1.dp, Color(0xFF294562)),
                            modifier = Modifier.fillMaxWidth().clickable { onActionClick(entry.menuTitle, action) }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ActionGlyph(icon, actionColor(action))
                                Text(action, color = BrightText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            } else if (entry.catalog.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp)).background(Bubble).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(entry.text, color = BrightText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    entry.catalog.forEach { item -> CatalogCard(item) { onCatalogClick(item) } }
                }
            } else {
                Surface(
                    color = if (entry.fromUser) Color(0xFF0865A7) else Bubble,
                    shape = if (entry.fromUser) RoundedCornerShape(22.dp, 8.dp, 22.dp, 22.dp) else RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.035f))
                ) {
                    Text(entry.text, color = BrightText, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp))
                }
            }
        }
        if (entry.fromUser) {
            Box(
                Modifier.padding(start = 9.dp, top = 18.dp).size(32.dp).clip(CircleShape).background(Color(0xFF18598C)),
                contentAlignment = Alignment.Center
            ) { Text("Y", color = BrightText, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

internal fun actionColor(action: String): Color = when {
    action.startsWith("Search", ignoreCase = true) || action == "Downloads" -> Color(0xFF42B9F5)
    action.contains("aired", ignoreCase = true) || action.contains("released", ignoreCase = true) || action.contains("updated", ignoreCase = true) || action == "Today" || action == "This week" || action == "All" -> Color(0xFFB68CFF)
    action.contains("Continue", ignoreCase = true) || action.contains("reading", ignoreCase = true) -> Teal
    else -> Color(0xFF42B9F5)
}

internal fun recentEpisodesUnavailableMessage(range: String): String =
    "No episodes found for $range. Connect an anime extension to check availability."

@Composable
private fun ActionGlyph(name: String, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = 2.dp.toPx()
        when (name) {
            "search" -> {
                drawCircle(color, 5.5.dp.toPx(), Offset(8.dp.toPx(), 8.dp.toPx()), style = Stroke(w))
                drawLine(color, Offset(12.dp.toPx(), 12.dp.toPx()), Offset(18.dp.toPx(), 18.dp.toPx()), w)
            }
            "history" -> {
                drawCircle(color, 7.dp.toPx(), Offset(size.width / 2, size.height / 2), style = Stroke(w))
                drawLine(color, Offset(size.width / 2, size.height / 2), Offset(size.width / 2, 5.dp.toPx()), w)
                drawLine(color, Offset(size.width / 2, size.height / 2), Offset(14.dp.toPx(), 12.dp.toPx()), w)
            }
            "book" -> {
                drawRoundRect(color, topLeft = Offset(3.dp.toPx(), 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(6.dp.toPx(), 16.dp.toPx()), style = Stroke(w), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                drawRoundRect(color, topLeft = Offset(11.dp.toPx(), 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(6.dp.toPx(), 16.dp.toPx()), style = Stroke(w), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                drawLine(color, Offset(10.dp.toPx(), 4.dp.toPx()), Offset(10.dp.toPx(), 16.dp.toPx()), w)
            }
            "list" -> {
                for (y in listOf(4.dp, 10.dp, 16.dp)) { drawCircle(color, 1.dp.toPx(), Offset(3.dp.toPx(), y.toPx())); drawLine(color, Offset(7.dp.toPx(), y.toPx()), Offset(18.dp.toPx(), y.toPx()), w) }
            }
            "play" -> {
                val p = Path().apply {
                    moveTo(5.dp.toPx(), 2.dp.toPx())
                    lineTo(18.dp.toPx(), 10.dp.toPx())
                    lineTo(5.dp.toPx(), 18.dp.toPx())
                    close()
                }
                drawPath(p, color)
            }
            "download" -> {
                drawLine(color, Offset(10.dp.toPx(), 2.dp.toPx()), Offset(10.dp.toPx(), 14.dp.toPx()), w)
                drawLine(color, Offset(5.dp.toPx(), 10.dp.toPx()), Offset(10.dp.toPx(), 15.dp.toPx()), w)
                drawLine(color, Offset(15.dp.toPx(), 10.dp.toPx()), Offset(10.dp.toPx(), 15.dp.toPx()), w)
                drawLine(color, Offset(4.dp.toPx(), 18.dp.toPx()), Offset(16.dp.toPx(), 18.dp.toPx()), w)
            }
            else -> {
                drawCircle(color, 3.dp.toPx(), Offset(8.dp.toPx(), 7.dp.toPx()), style = Stroke(w))
                drawLine(color, Offset(11.dp.toPx(), 5.dp.toPx()), Offset(16.dp.toPx(), 3.dp.toPx()), w)
                drawLine(color, Offset(12.dp.toPx(), 12.dp.toPx()), Offset(16.dp.toPx(), 16.dp.toPx()), w)
            }
        }
    }
}

@Composable
internal fun SearchMessage(mediaType: String, initialQuery: String, onSelect: (CatalogItem) -> Unit) {
    var query by remember(mediaType, initialQuery) { mutableStateOf(initialQuery) }
    var results by remember(mediaType) { mutableStateOf(emptyList<CatalogItem>()) }
    var loading by remember(mediaType) { mutableStateOf(false) }
    var error by remember(mediaType) { mutableStateOf(false) }

    LaunchedEffect(mediaType, query) {
        results = emptyList()
        error = false
        if (query.trim().length < 2) {
            loading = false
            return@LaunchedEffect
        }
        if (mediaType !in setOf("anime", "manga", "movie", "tv")) {
            loading = false
            error = true
            return@LaunchedEffect
        }
        val searchedQuery = query.trim()
        delay(300)
        if (query.trim() != searchedQuery) return@LaunchedEffect
        loading = true
        try {
            val found = searchCatalog(mediaType, searchedQuery)
            if (query.trim() == searchedQuery) results = found
        } catch (_: Exception) {
            if (query.trim() == searchedQuery) error = true
        } finally {
            if (query.trim() == searchedQuery) loading = false
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            when (mediaType) {
                "manga" -> "Search manga"
                "movie" -> "Search movies"
                "tv" -> "Search TV series"
                else -> "Search anime"
            },
            color = BrightText,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFF0C1A2B))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionGlyph("search", Color(0xFF27A8F2))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Type a title to search…", color = SoftText, fontSize = 14.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = BrightText),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        when {
            error && mediaType !in setOf("anime", "manga", "movie", "tv") ->
                Text("A matching metadata catalog is not connected for this media type.", color = SoftText, fontSize = 13.sp)
            error -> Text("Search is temporarily unavailable. Try again.", color = SoftText, fontSize = 13.sp)
            loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Blue)
                Text("Finding matching titles…", color = SoftText, fontSize = 13.sp)
            }
            query.trim().length < 2 -> Text("Predictions will appear here as you type.", color = SoftText, fontSize = 13.sp)
            results.isEmpty() -> Text("No matching titles found.", color = SoftText, fontSize = 13.sp)
            else -> results.forEach { item -> CatalogCard(item) { onSelect(item) } }
        }
    }
}

@Composable
internal fun SeriesCardMessage(item: CatalogItem, onAction: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(14.dp).testTag("anime_details_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            if (item.image.isNotBlank()) {
                AsyncImage(
                    model = item.image,
                    contentDescription = "${item.title} cover artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(112.dp).height(176.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1D3550))
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(item.title, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                val facts = listOfNotNull(
                    item.seasons.size.takeIf { it > 0 }?.let { "$it seasons" },
                    item.episodes?.let { "$it episodes" },
                    item.year?.toString()
                )
                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = SoftText, fontSize = 12.sp, lineHeight = 17.sp)
                if (item.genres.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.genres.forEach { genre ->
                            Surface(color = Color(0xFF10263D), shape = RoundedCornerShape(14.dp)) {
                                Text(genre, color = Color(0xFF9CD7FF), fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                            }
                        }
                    }
                }
                if (item.summary.isNotBlank()) Text(item.summary, color = SoftText, fontSize = 12.sp, lineHeight = 17.sp,
                    maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("Last watched: Not started", color = Teal, fontSize = 12.sp, modifier = Modifier.testTag("anime_last_watched"))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SeriesCardAction("Play from the beginning", "play", Modifier.weight(1.2f)) { onAction("play") }
            SeriesCardAction("Seasons", "list", Modifier.weight(0.8f)) { onAction("seasons") }
        }
    }
}

@Composable
private fun SeriesCardAction(label: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (label.startsWith("Play")) Blue else Color(0xFF10263D),
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, if (label.startsWith("Play")) Blue else Color(0xFF168EEA)),
        modifier = modifier.clickable(onClick = onClick).testTag("anime_action_${if (label.startsWith("Play")) "play" else "seasons"}")
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ActionGlyph(icon, if (label.startsWith("Play")) Color.White else Color(0xFF42B9F5))
            Text(label, color = BrightText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun SeasonListMessage(item: CatalogItem, onSelect: (SeasonItem) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(item.title, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (item.seasons.isEmpty()) {
            Text("Season information is unavailable from this metadata source. Annie will show seasons when a connected source provides them.",
                color = SoftText, fontSize = 13.sp, lineHeight = 19.sp)
        } else {
            Text("Choose a season", color = SoftText, fontSize = 13.sp)
        }
        item.seasons.forEachIndexed { index, season ->
            Surface(
                color = Color(0xFF10263D), shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF294562)),
                modifier = Modifier.fillMaxWidth().clickable { onSelect(season) }
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(season.image, season.title, Modifier.size(54.dp, 70.dp).clip(RoundedCornerShape(8.dp)))
                    Column {
                        Text("Season ${index + 1}", color = Color(0xFF77C5FF), fontSize = 11.sp)
                        Text(season.title, color = BrightText, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(season.year?.toString(), season.episodes?.let { "$it episodes" }).joinToString(" · "), color = SoftText, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeListMessage(item: CatalogItem) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(item.title, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Episodes", color = Color(0xFF77C5FF), fontSize = 13.sp)
        Text("No episode list was returned by the selected extension. Connect an anime extension to load episode cards.", color = SoftText, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
internal fun MangaResultMessage(item: CatalogItem, onAction: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(12.dp).testTag("manga_details_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(208.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1D3550)).testTag("manga_cover_artwork")) {
            if (item.image.isNotBlank()) AsyncImage(
                model = item.image, contentDescription = "${item.title} cover artwork",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE607111E)))))
            Text("MANGA", color = Color(0xFF9CD7FF), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp))
        }
        Text(item.title, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 25.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        val metadata = listOfNotNull(
            item.creator?.takeIf(String::isNotBlank),
            item.year?.toString(),
            item.chapters?.let { "$it chapters" },
            item.status.takeIf { it in setOf("RELEASING", "FINISHED") }?.let(::mangaStatusLabel)
        )
        if (metadata.isNotEmpty()) Text(metadata.joinToString(" · "), color = SoftText, fontSize = 12.sp, lineHeight = 17.sp)
        if (item.genres.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item.genres.forEach { genre ->
                    Surface(color = Color(0xFF10263D), shape = RoundedCornerShape(14.dp)) {
                        Text(genre, color = Color(0xFF9CD7FF), fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }
        if (item.summary.isNotBlank()) Text(item.summary, color = SoftText, fontSize = 13.sp, lineHeight = 19.sp,
            maxLines = 5, overflow = TextOverflow.Ellipsis)
        Text("Last read chapter · Not started", color = SoftText, fontSize = 12.sp, modifier = Modifier.testTag("last_read_chapter"))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MangaCardAction("Continue reading", "book", Modifier.weight(1f)) { onAction("reader") }
            MangaCardAction("Chapters", "list", Modifier.weight(1f)) { onAction("chapters") }
        }
    }
}

@Composable
private fun MangaCardAction(label: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (label == "Continue reading") Blue else Color(0xFF10263D),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (label == "Continue reading") Blue else Color(0xFF168EEA)),
        modifier = modifier.clickable(onClick = onClick).testTag("manga_action_$label")
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionGlyph(icon, if (label == "Continue reading") Color.White else Color(0xFF42B9F5))
            Text(label, color = BrightText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun MediaMetadataMessage(item: CatalogItem, mediaLabel: String, onOpenSource: (String) -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("$mediaLabel metadata", color = Color(0xFF77C5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (item.image.isNotBlank()) {
                AsyncImage(
                    model = item.image,
                    contentDescription = item.title,
                    modifier = Modifier.width(96.dp).height(130.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1D3550))
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text(item.title, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val facts = listOfNotNull(
                    item.year?.toString(),
                    item.runtimeMinutes?.let { "$it min" },
                    item.status.takeIf { it.isNotBlank() && it != "METADATA" }
                )
                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = SoftText, fontSize = 11.sp, lineHeight = 16.sp)
                if (item.creator != null) Text("Directed by ${item.creator}", color = SoftText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.genres.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item.genres.forEach { genre ->
                    Surface(color = Color(0xFF10263D), shape = RoundedCornerShape(14.dp)) {
                        Text(genre, color = Color(0xFF9CD7FF), fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }
        if (item.summary.isNotBlank()) {
            Text(item.summary, color = SoftText, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        Surface(color = Color(0xFF10263D), shape = RoundedCornerShape(12.dp)) {
            Text("Metadata only · ${item.sourceLabel}. Playback and downloads need a connected media source.",
                color = SoftText, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(10.dp))
        }
        if (item.sourceUrl.isNotBlank()) {
            Surface(
                color = Color(0xFF10263D),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF294562)),
                modifier = Modifier.fillMaxWidth().clickable { onOpenSource(item.sourceUrl) }
            ) {
                Text("Open ${item.sourceLabel} record  ›", color = Color(0xFF9CD7FF), fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun MangaChapterListMessage(item: CatalogItem) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text("Chapters · ${item.title}", color = BrightText, fontWeight = FontWeight.Bold, fontSize = 17.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Source", color = SoftText, fontSize = 12.sp)
            Surface(color = Color(0xFF10263D), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFF294562))) {
                Text("No manga extension connected", color = SoftText, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp))
            }
        }
        Text(
            "Connect a manga extension to load chapters. The selected source will stay scoped to this chapter list.",
            color = SoftText, fontSize = 13.sp, lineHeight = 19.sp
        )
    }
}

@Composable
private fun MangaReaderUnavailableMessage(item: CatalogItem) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(item.title, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("Reader", color = Color(0xFF77C5FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Annie needs a manga source with chapter pages before it can open the Mihon-style reader.",
            color = SoftText, fontSize = 13.sp, lineHeight = 19.sp
        )
    }
}

private fun mangaStatusLabel(status: String): String = when (status) {
    "RELEASING" -> "Ongoing"
    "FINISHED" -> "Completed"
    else -> "Status unknown"
}

@Composable
internal fun CatalogCard(item: CatalogItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("catalog_result_card"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1A2A)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF29425E))
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.image,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(104.dp).height(78.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF1D3550))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val mediaLabel = when (item.mediaType) {
                    "MANGA" -> "MANGA"
                    "MOVIE" -> "MOVIE"
                    "TV" -> "TV SERIES"
                    else -> if (item.format == "MOVIE") "ANIME MOVIE" else "ANIME"
                }
                Text(mediaLabel, color = Color(0xFF75BDF1), fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                Text(item.title, color = BrightText, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val count = if (item.mediaType == "MANGA") item.chapters?.let { "$it chapters" } else item.episodes?.let { "$it episodes" }
                val facts = listOfNotNull(item.year?.toString(), count, item.sourceLabel.takeIf { it.isNotBlank() })
                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), color = SoftText, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(11.dp),
                border = BorderStroke(1.dp, Color(0xFF168EEA)),
                modifier = Modifier.testTag("catalog_details_action").clickable(onClick = onClick)
            ) {
                Text("Details", color = Color(0xFF42B9F5), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp))
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "RELEASING" -> "Ongoing"
    "FINISHED" -> "Completed"
    "NOT_YET_RELEASED" -> "Not yet released"
    else -> "Status unknown"
}

@Composable
internal fun CommandSuggestions(value: String, onSelect: (String) -> Unit) {
    val commands = listOf(
        "/anime" to "Browse anime",
        "/anime search" to "Search the catalog",
        "/anime recent" to "New episodes",
        "/anime downloads" to "Downloads",
        "/anime recently aired" to "Recently aired",
        "/anime continue" to "Continue watching",
        "/anime continue watching" to "Continue watching",
        "/manga" to "Browse manga",
        "/movie search" to "Search movies",
        "/movie" to "Browse movies",
        "/movie continue" to "Continue watching",
        "/tv series" to "Browse TV series",
        "/tv" to "Search TV series",
        "/tv search" to "Search TV series",
        "/tv continue" to "Continue watching",
        "/manga search" to "Search manga",
        "/manga continue" to "Continue reading",
        "/manga downloads" to "Downloads",
        "/music" to "Music",
        "/continue" to "Continue watching",
        "/downloads" to "Downloads",
        "/extensions" to "Extensions",
        "/help" to "Help"
    )
    val raw = value.trimStart()
    if (!raw.startsWith("/") || raw.contains("\n")) return
    val matches = if (raw == "/") {
        commands.filter { it.first.count { char -> char == ' ' } == 0 }
    } else {
        commands.filter { it.first.startsWith(raw, ignoreCase = true) }
    }
    if (matches.isEmpty()) return

    Column(
        Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(Panel).padding(6.dp).testTag("slash_suggestions"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        matches.take(if (raw == "/") 9 else 5).forEach { (command, label) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                    .clickable { onSelect(command) }.testTag("slash_command_$command")
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(command, color = BrightText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Text(label, color = SoftText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
internal fun Composer(value: String, onValueChange: (String) -> Unit, onSuggestionSelected: (String) -> Unit = {}, onSend: () -> Unit, onMenu: () -> Unit) {
    Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().testTag("composer")) {
        CommandSuggestions(value, onSuggestionSelected)
        Row(
        modifier = Modifier.fillMaxWidth()
            .background(Night).padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(color = Bubble, shape = CircleShape, modifier = Modifier.size(44.dp).clickable(onClick = onMenu)) {
            Box(contentAlignment = Alignment.Center) { Text("+", color = SoftText, fontSize = 26.sp) }
        }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).background(Color(0xFF102139)).padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).testTag("composer_input"),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = BrightText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text("Message Annie…", color = SoftText, fontSize = 15.sp)
                        inner()
                    }
                }
            )
        }
        Surface(color = Blue, shape = CircleShape, modifier = Modifier.size(46.dp).clickable(onClick = onSend)) {
            Box(contentAlignment = Alignment.Center) { Text("➤", color = Color.White, fontSize = 19.sp) }
        }
        }
    }
}

private data class MenuAction(val icon: String, val label: String, val hint: String)

@Composable
private fun CommandSheet(category: String, onChoose: (String) -> Unit) {
    val actions = when (category) {
        "Anime" -> listOf(
            MenuAction("⌕", "Search anime", "Find a title in the catalog"),
            MenuAction("◷", "Recently aired", "See new episodes when a source is connected"),
            MenuAction("▶", "Continue watching", "Resume your saved progress"),
            MenuAction("↓", "Downloads", "Open your saved media")
        )
        "Movies & TV" -> listOf(
            MenuAction("⌕", "Search movies", "Find a movie"),
            MenuAction("▤", "Search TV series", "Find a television series"),
            MenuAction("◷", "Recently released", "Browse recent releases"),
            MenuAction("▶", "Continue watching", "Resume a saved title"),
            MenuAction("↓", "Downloads", "Open your saved media")
        )
        "Manga" -> listOf(
            MenuAction("⌕", "Search manga", "Find manga, manhwa, or manhua"),
            MenuAction("◷", "Recently updated", "Browse new chapters"),
            MenuAction("▤", "Continue reading", "Resume at your saved page"),
            MenuAction("↓", "Downloads", "Open your saved chapters")
        )
        "Music" -> listOf(
            MenuAction("⌕", "Search music", "Find music to play"),
            MenuAction("▶", "Open YouTube link", "Play through YouTube’s official player")
        )
        "Extensions" -> listOf(
            MenuAction("A", "AniList", "Anime and manga catalog metadata"),
            MenuAction("▶", "YouTube", "Official search and playback"),
            MenuAction("i", "About sources", "Content actions need a supported source")
        )
        else -> listOf(
            MenuAction("▧", "Photo or video", "Attach media"),
            MenuAction("▤", "File", "Attach a file")
        )
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(category, color = BrightText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
        actions.forEach { action ->
            Surface(
                color = Color(0xFF11243A),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF29425F)),
                modifier = Modifier.fillMaxWidth().clickable { onChoose(action.label) }
            ) {
                Row(
                    Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF183553)), contentAlignment = Alignment.Center) {
                        Text(action.icon, color = Color(0xFF5CB7F5), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(action.label, color = BrightText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(action.hint, color = SoftText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("›", color = SoftText, fontSize = 22.sp)
                }
            }
        }
    }
}
