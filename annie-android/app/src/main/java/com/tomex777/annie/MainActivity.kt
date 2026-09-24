package com.tomex777.annie

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
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

private data class CatalogItem(
    val id: Int,
    val mediaType: String,
    val title: String,
    val image: String,
    val year: Int?,
    val status: String,
    val episodes: Int?,
    val chapters: Int?
)

private data class ChatEntry(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val catalog: List<CatalogItem> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnnieChat() {
    val context = LocalContext.current
    val messages = remember {
        mutableStateListOf(ChatEntry(1, false, "Hey, I’m Annie. Choose a media type or type a command to get started."))
    }
    var draft by remember { mutableStateOf("") }
    var activeSheet by remember { mutableStateOf<String?>(null) }
    var searchRequest by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun addAnnie(text: String, catalog: List<CatalogItem> = emptyList()) {
        messages.add(ChatEntry(System.nanoTime(), false, text, catalog))
        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
    }

    fun openCategory(category: String) { activeSheet = category }

    fun startSearch(media: String, query: String) {
        if (media == "movie") {
            addAnnie("Movie search needs a supported catalog source. I won’t show made-up results.")
            return
        }
        searchRequest = media to query
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
            "/anime" -> if (query.isBlank()) openCategory("Anime") else
                if (query.equals("recently aired", true)) addAnnie("The recently aired feed needs a source that can verify available episodes.")
                else if (query.equals("continue", true)) addAnnie("Nothing to continue watching yet.")
                else if (query.equals("downloads", true)) addAnnie("Your downloads will appear here when a supported source is connected.")
                else startSearch("anime", query)
            "/manga" -> if (query.isBlank()) openCategory("Manga") else
                if (query.equals("continue", true)) addAnnie("Nothing to continue reading yet.")
                else if (query.equals("downloads", true)) addAnnie("Your manga downloads will appear here when supported.")
                else startSearch("manga", query)
            "/movie", "/movies" -> if (query.isBlank()) openCategory("Movies & TV") else
                if (query.equals("continue", true)) addAnnie("Nothing to continue watching yet.")
                else startSearch("movie", query)
            "/music" -> {
                if (query.startsWith("https://", true) || query.startsWith("http://", true)) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(query))) }
                    addAnnie("Opening that link in the official app or browser.")
                } else if (query.isBlank()) openCategory("Music")
                else addAnnie("For music playback, paste a YouTube link. Annie keeps playback in YouTube’s official player.")
            }
            "/downloads" -> addAnnie("No downloads yet.")
            "/extensions", "/settings" -> openCategory("Extensions")
            "/help" -> addAnnie("Try /anime, /movie, /manga, /music, /downloads, or /extensions.")
            else -> addAnnie("Try a slash command: /anime, /movie, /manga, /music, or /downloads.")
        }
    }

    LaunchedEffect(searchRequest) {
        val request = searchRequest ?: return@LaunchedEffect
        isSearching = true
        try {
            val results = searchAniList(request.first, request.second)
            if (results.isEmpty()) addAnnie("AniList didn’t find a matching title.")
            else addAnnie("AniList catalog results · ${results.size} matches", results)
        } catch (_: Exception) {
            addAnnie("AniList couldn’t be reached right now. Check your connection and try again.")
        } finally {
            isSearching = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Night) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnnieTopBar(onExtensions = { activeSheet = "Extensions" })
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item { WelcomePanel() }
                items(messages, key = { it.id }) { entry ->
                    ChatBubble(entry) { item ->
                        val url = "https://anilist.co/${item.mediaType.lowercase()}/${item.id}"
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }
                }
                if (isSearching) item {
                    Row(
                        modifier = Modifier.padding(start = 48.dp).clip(RoundedCornerShape(18.dp)).background(Bubble).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Blue)
                        Text("Searching AniList…", color = SoftText, fontSize = 14.sp)
                    }
                }
            }
            QuickCommands(
                onSelect = { category ->
                    if (category == "Downloads") addAnnie("No downloads yet.")
                    else openCategory(category)
                }
            )
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
                    "Search anime" -> draft = "/anime "
                    "Recently aired" -> addAnnie("The recently aired feed needs a source that can verify available episodes.")
                    "Continue watching" -> addAnnie("Nothing to continue watching yet.")
                    "Search movies", "Recently released" -> addAnnie("Movie search needs a supported catalog source. I won’t show made-up results.")
                    "Search manga" -> draft = "/manga "
                    "Recently updated" -> addAnnie("Manga updates need a supported chapter source.")
                    "Continue reading" -> addAnnie("Nothing to continue reading yet.")
                    "Search music", "Open YouTube link" -> draft = "/music "
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
                    listOf("アニメ", "漫画", "♫").forEachIndexed { index, name ->
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
private fun ChatBubble(entry: ChatEntry, onCatalogClick: (CatalogItem) -> Unit) {
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
            if (entry.catalog.isNotEmpty()) {
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
                Text("Open AniList  ↗", color = Color(0xFF9CD7FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
private fun QuickCommands(onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf("Anime", "Movies & TV", "Manga", "Music", "Downloads").forEach { name ->
            Surface(
                color = Color(0xFF102139),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color(0xFF2B435F)),
                modifier = Modifier.clickable { onSelect(name) }
            ) {
                Text(name, color = Color(0xFFC1D2E7), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun Composer(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, onMenu: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().background(Night).padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
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
    val graph = """
        query AnnieSearch(\${'$'}search: String!, \${'$'}type: MediaType!) {
          Page(page: 1, perPage: 10) {
            media(search: \${'$'}search, type: \${'$'}type, isAdult: false, sort: SEARCH_MATCH) {
              id
              type
              title { english romaji native }
              startDate { year }
              episodes
              chapters
              status
              coverImage { large }
            }
          }
        }
    """.trimIndent()
    val body = JSONObject()
        .put("query", graph)
        .put("variables", JSONObject().put("search", search).put("type", if (mediaType == "anime") "ANIME" else "MANGA"))
        .toString()
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
                add(
                    CatalogItem(
                        id = row.optInt("id"),
                        mediaType = row.optString("type"),
                        title = name,
                        image = row.optJSONObject("coverImage")?.optString("large").orEmpty(),
                        year = row.optJSONObject("startDate")?.optInt("year")?.takeIf { it > 0 },
                        status = row.optString("status"),
                        episodes = row.optInt("episodes").takeIf { it > 0 },
                        chapters = row.optInt("chapters").takeIf { it > 0 }
                    )
                )
            }
        }
    } finally {
        connection.disconnect()
    }
}
