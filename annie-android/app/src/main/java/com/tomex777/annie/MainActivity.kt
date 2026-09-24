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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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

private data class SeasonItem(
    val id: Int,
    val title: String,
    val image: String,
    val year: Int?,
    val episodes: Int?
) {
    fun asCatalogItem() = CatalogItem(id, "ANIME", title, image, year, "UNKNOWN", episodes, null)
}

private data class CatalogItem(
    val id: Int,
    val mediaType: String,
    val title: String,
    val image: String,
    val year: Int?,
    val status: String,
    val episodes: Int?,
    val chapters: Int?,
    val format: String = "",
    val seasons: List<SeasonItem> = emptyList()
)

private data class ChatEntry(
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

    fun openDownloads() {
        addAnnie("No downloads yet.", menuTitle = "Downloads")
    }

    fun openSelectedTitle(item: CatalogItem) {
        if (item.mediaType == "ANIME" && item.seasons.size > 1) {
            addAnnie("", selectedItem = item, selectedStage = "series")
        } else if (item.mediaType == "ANIME") {
            addAnnie("", selectedItem = item, selectedStage = "episodes")
        } else {
            addAnnie("", selectedItem = item, selectedStage = "manga")
        }
    }

    fun openCategory(category: String) {
        when (category) {
            "Anime" -> addAnnie("Choose an action or type a title to search.", menuTitle = category,
                actions = listOf("Search anime", "Recently aired", "Continue watching", "Downloads"))
            "Movies & TV" -> addAnnie("Choose an action or type a title to search.", menuTitle = category,
                actions = listOf("Search movies", "Recently released", "Continue watching", "Downloads"))
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
            "Anime" to "Downloads" -> openDownloads()
            "Movies & TV" to "Search movies" -> openSearch("movie")
            "Movies & TV" to "Recently released" -> addAnnie("Recently released titles need a connected movie extension.")
            "Movies & TV" to "Continue watching" -> addAnnie("Nothing to continue watching yet.", menuTitle = "Continue watching")
            "Movies & TV" to "Downloads" -> openDownloads()
            "Manga" to "Search manga" -> openSearch("manga")
            "Manga" to "Recently updated" -> addAnnie("Recently updated chapters need a connected manga extension.")
            "Manga" to "Continue reading" -> addAnnie("Nothing to continue reading yet.", menuTitle = "Continue reading")
            "Manga" to "Downloads" -> openDownloads()
            "Music" to "Search music" -> openSearch("music")
            "Music" to "Open YouTube link" -> draft = "/music "
            "New anime episodes" to "Today", "New anime episodes" to "This week", "New anime episodes" to "All" ->
                addAnnie("No episodes found for this time range. Connect an anime extension to check availability.", menuTitle = "New anime episodes", actions = listOf("Today", "This week", "All"))
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
                query.equals("download", true) || query.equals("downloads", true) -> openDownloads()
                query.equals("recently aired", true) -> handleMenuAction("Anime", "Recently aired")
                query.equals("continue", true) || query.equals("continue watching", true) -> addAnnie("Nothing to continue watching yet.", menuTitle = "Continue watching")
                else -> startSearch("anime", query)
            }
            "/manga" -> when {
                query.isBlank() -> openCategory("Manga")
                query.equals("search", true) -> startSearch("manga", "")
                query.startsWith("search ", true) -> startSearch("manga", query.substringAfter(" ", "").trim())
                query.equals("download", true) || query.equals("downloads", true) -> openDownloads()
                query.equals("continue", true) || query.equals("continue reading", true) -> addAnnie("Nothing to continue reading yet.", menuTitle = "Continue reading")
                else -> startSearch("manga", query)
            }
            "/movie", "/movies" -> when {
                query.isBlank() -> openCategory("Movies & TV")
                query.equals("search", true) -> startSearch("movie", "")
                query.startsWith("search ", true) -> startSearch("movie", query.substringAfter(" ", "").trim())
                query.equals("download", true) || query.equals("downloads", true) -> openDownloads()
                query.equals("continue", true) || query.equals("continue watching", true) -> addAnnie("Nothing to continue watching yet.", menuTitle = "Continue watching")
                else -> startSearch("movie", query)
            }
            "/music" -> {
                if (query.startsWith("https://", true) || query.startsWith("http://", true)) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(query))) }
                    addAnnie("Opening that link in the official app or browser.")
                } else if (query.isBlank()) openCategory("Music")
                else addAnnie("For music playback, paste a YouTube link. Annie keeps playback in YouTube’s official player.")
            }
            "/downloads" -> openDownloads()
            "/extensions", "/settings" -> openCategory("Extensions")
            "/help" -> addAnnie("Try /anime, /movie, /manga, /music, /downloads, or /extensions.")
            else -> addAnnie("Try a slash command: /anime, /movie, /manga, /music, or /downloads.")
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Night) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            AnnieTopBar(onExtensions = { activeSheet = "Extensions" })
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(messages, key = { it.id }) { entry ->
                    ChatBubble(
                        entry,
                        onCatalogClick = ::openSelectedTitle,
                        onActionClick = ::handleMenuAction,
                        onSeriesAction = { item, stage, season ->
                            addAnnie("", selectedItem = season?.asCatalogItem() ?: item, selectedStage = stage)
                        }
                    )
                }
            }
            CommandSuggestions(value = draft, onSelect = { command -> draft = "$command " })
            Composer(
                value = draft,
                onValueChange = { draft = it },
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
            CommandSheet(category = category) { action ->
                activeSheet = null
                when (action) {
                    "Search anime" -> openSearch("anime")
                    "Recently aired" -> handleMenuAction("Anime", "Recently aired")
                    "Continue watching" -> addAnnie("Nothing to continue watching yet.", menuTitle = "Continue watching")
                    "Downloads" -> openDownloads()
                    "Search movies" -> openSearch("movie")
                    "Recently released" -> addAnnie("Recently released titles need a connected movie extension.")
                    "Search manga" -> openSearch("manga")
                    "Recently updated" -> addAnnie("Recently updated chapters need a connected manga extension.")
                    "Continue reading" -> addAnnie("Nothing to continue reading yet.", menuTitle = "Continue reading")
                    "Search music", "Open YouTube link" -> openSearch("music")
                    else -> addAnnie("AniList provides anime and manga metadata. YouTube uses its official player. Other content actions need supported sources.")
                }
            }
        }
    }
}

@Composable
private fun AnnieTopBar(onExtensions: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().height(68.dp).background(Panel).padding(horizontal = 16.dp),
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
private fun ChatBubble(
    entry: ChatEntry,
    onCatalogClick: (CatalogItem) -> Unit,
    onActionClick: (String, String) -> Unit,
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
                    "series" -> SeriesCardMessage(entry.selectedItem) {
                        onSeriesAction(entry.selectedItem, "seasons", null)
                    }
                    "seasons" -> SeasonListMessage(entry.selectedItem) { season ->
                        onSeriesAction(entry.selectedItem, "episodes", season)
                    }
                    "episodes" -> EpisodeListMessage(entry.selectedItem)
                    else -> MangaResultMessage(entry.selectedItem)
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
                                ActionGlyph(icon, Color(0xFF27A8F2))
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
private fun SearchMessage(mediaType: String, initialQuery: String, onSelect: (CatalogItem) -> Unit) {
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
        if (mediaType != "anime" && mediaType != "manga") {
            loading = false
            error = true
            return@LaunchedEffect
        }
        delay(300)
        loading = true
        try {
            results = searchAniList(mediaType, query.trim())
        } catch (_: Exception) {
            error = true
        } finally {
            loading = false
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(if (mediaType == "manga") "Search manga" else "Search anime", color = BrightText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
            error && mediaType != "anime" && mediaType != "manga" ->
                Text("A matching content source is not connected yet.", color = SoftText, fontSize = 13.sp)
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
private fun SeriesCardMessage(item: CatalogItem, onSeasons: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Series", color = Color(0xFF77C5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(item.image, item.title, Modifier.width(96.dp).height(130.dp).clip(RoundedCornerShape(12.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(item.year?.toString(), item.status.takeIf { it != "UNKNOWN" }?.let(::statusLabel)).joinToString(" · "), color = SoftText, fontSize = 12.sp)
            }
        }
        Surface(
            color = Color(0xFF10263D), shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF294562)),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSeasons)
        ) { Text("Seasons", color = BrightText, fontSize = 15.sp, modifier = Modifier.padding(14.dp)) }
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
        Text("Choose a season", color = SoftText, fontSize = 13.sp)
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
private fun MangaResultMessage(item: CatalogItem) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp, 22.dp, 22.dp, 22.dp))
            .background(Bubble).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Manga", color = Color(0xFF77C5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(item.image, item.title, Modifier.width(96.dp).height(130.dp).clip(RoundedCornerShape(12.dp)))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, color = BrightText, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(item.year?.toString(), item.chapters?.let { "$it chapters" }).joinToString(" · "), color = SoftText, fontSize = 12.sp)
            }
        }
        Text("Chapter list requires a connected manga extension.", color = SoftText, fontSize = 12.sp)
    }
}

@Composable
private fun CatalogCard(item: CatalogItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1A2A)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF29425E))
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = item.image,
                contentDescription = item.title,
                modifier = Modifier.width(98.dp).height(132.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF1D3550))
            )
            Column(modifier = Modifier.weight(1f).height(132.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(if (item.mediaType == "MANGA") "MANGA · ANILIST" else "ANIME · ANILIST", color = Color(0xFF75BDF1), fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                    Text(item.title, color = BrightText, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                    val count = if (item.mediaType == "MANGA") item.chapters?.let { "$it chapters listed" } else item.episodes?.let { "$it episodes listed" }
                    Text(listOfNotNull(item.year?.toString(), count).joinToString(" · ").ifBlank { "Catalog details" }, color = SoftText, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                    Text(statusLabel(item.status), color = Teal, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                }
                Text("Select this title  ›", color = Color(0xFF9CD7FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
private fun CommandSuggestions(value: String, onSelect: (String) -> Unit) {
    val commands = listOf(
        "/anime" to "Anime menu",
        "/anime search" to "Search anime",
        "/anime downloads" to "Downloads",
        "/anime recently aired" to "Recently aired",
        "/anime continue watching" to "Continue watching",
        "/movie search" to "Search movies",
        "/manga search" to "Search manga",
        "/music" to "Music",
        "/downloads" to "Downloads",
        "/extensions" to "Extensions",
        "/help" to "Help"
    )
    val raw = value.trimStart()
    if (!raw.startsWith("/") || raw.contains("\n")) return
    val matches = commands.filter { it.first.startsWith(raw, ignoreCase = true) }
    if (matches.isEmpty()) return

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(Panel).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        matches.forEach { (command, label) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                    .clickable { onSelect(command) }.padding(horizontal = 12.dp, vertical = 10.dp),
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
private fun Composer(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, onMenu: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().background(Night).padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
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
                modifier = Modifier.weight(1f),
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
            MenuAction("⌕", "Search movies", "Find a movie or series"),
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

private suspend fun searchAniList(mediaType: String, search: String): List<CatalogItem> = withContext(Dispatchers.IO) {
    val quotedSearch = JSONObject.quote(search)
    val typeName = if (mediaType == "anime") "ANIME" else "MANGA"
    val graph = """
        query AnnieSearch {
          Page(page: 1, perPage: 10) {
            media(search: $quotedSearch, type: $typeName, isAdult: false, sort: SEARCH_MATCH) {
              id
              type
              title { english romaji native }
              startDate { year }
              episodes
              chapters
              status
              format
              relations {
                edges {
                  relationType
                  node {
                    id
                    type
                    format
                    title { english romaji native }
                    startDate { year }
                    episodes
                    coverImage { large }
                  }
                }
              }
              coverImage { large }
            }
          }
        }
    """.trimIndent()
    val body = JSONObject().put("query", graph).toString()
    val connection = (URL("https://graphql.anilist.co").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 12_000
        readTimeout = 15_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
    }
    try {
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val payload = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) error("AniList HTTP $code")
        val rows = JSONObject(payload).getJSONObject("data").getJSONObject("Page").getJSONArray("media")
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val titles = row.optJSONObject("title") ?: continue
                val name = titles.optString("english").takeIf { it.isNotBlank() }
                    ?: titles.optString("romaji").takeIf { it.isNotBlank() }
                    ?: titles.optString("native").takeIf { it.isNotBlank() }
                    ?: continue
                val itemId = row.optInt("id")
                val itemType = row.optString("type")
                val itemFormat = row.optString("format")
                val itemYear = row.optJSONObject("startDate")?.optInt("year")?.takeIf { it > 0 }
                val linkedSeasons = buildList {
                    val edges = row.optJSONObject("relations")?.optJSONArray("edges") ?: return@buildList
                    for (edgeIndex in 0 until edges.length()) {
                        val edge = edges.optJSONObject(edgeIndex) ?: continue
                        if (edge.optString("relationType") !in setOf("SEQUEL", "PREQUEL")) continue
                        val node = edge.optJSONObject("node") ?: continue
                        if (node.optString("type") != "ANIME" || node.optString("format") != "TV") continue
                        val relatedTitle = node.optJSONObject("title") ?: continue
                        val relatedName = relatedTitle.optString("english").takeIf { it.isNotBlank() }
                            ?: relatedTitle.optString("romaji").takeIf { it.isNotBlank() }
                            ?: relatedTitle.optString("native").takeIf { it.isNotBlank() }
                            ?: continue
                        add(SeasonItem(
                            id = node.optInt("id"),
                            title = relatedName,
                            image = node.optJSONObject("coverImage")?.optString("large").orEmpty(),
                            year = node.optJSONObject("startDate")?.optInt("year")?.takeIf { it > 0 },
                            episodes = node.optInt("episodes").takeIf { it > 0 }
                        ))
                    }
                }.plus(
                    if (itemType == "ANIME" && itemFormat == "TV") listOf(
                        SeasonItem(itemId, name, row.optJSONObject("coverImage")?.optString("large").orEmpty(), itemYear,
                            row.optInt("episodes").takeIf { it > 0 })
                    ) else emptyList()
                ).distinctBy { it.id }.sortedWith(compareBy<SeasonItem> { it.year ?: Int.MAX_VALUE }.thenBy { it.id })
                add(
                    CatalogItem(
                        id = itemId,
                        mediaType = itemType,
                        title = name,
                        image = row.optJSONObject("coverImage")?.optString("large").orEmpty(),
                        year = itemYear,
                        status = row.optString("status"),
                        episodes = row.optInt("episodes").takeIf { it > 0 },
                        chapters = row.optInt("chapters").takeIf { it > 0 },
                        format = itemFormat,
                        seasons = linkedSeasons
                    )
                )
            }
        }
    } finally {
        connection.disconnect()
    }
}
