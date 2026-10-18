package com.nice.offlinegames

import android.content.Context
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader

private val ink = Color(0xFF172822)
private val muted = Color(0xFF66746E)
private val backdrop = Color(0xFFF7F8F5)
private val accent = Color(0xFF276346)

data class GameEntry(val id: String, val title: String, val category: String, val detail: String)
private val games = listOf(
    GameEntry("2048", "2048", "Puzzle", "Slide and combine tiles"),
    GameEntry("mines", "Minesweeper", "Puzzle", "Reveal safe cells"),
    GameEntry("blocks", "Block Drop", "Arcade", "Build lines, clear the board"),
    GameEntry("bird", "Wing Hop", "Arcade", "Navigate the moving gaps"),
    GameEntry("maze", "Maze Chase", "Arcade", "Collect dots, dodge chasers")
)
private val categories = listOf("All", "Favourites", "Arcade", "Puzzle")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.statusBars())
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = accent, background = backdrop, surface = Color.White)) {
                OfflineHub()
            }
        }
    }
}

@Composable
private fun OfflineHub() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { ctx.getSharedPreferences("offline_games_hub", Context.MODE_PRIVATE) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var search by rememberSaveable { mutableStateOf("") }
    var favourites by remember { mutableStateOf(preferences.getStringSet("favourites", emptySet()).orEmpty().toSet()) }
    var recent by remember { mutableStateOf(preferences.getString("recent", "").orEmpty().split("|").filter { id -> games.any { it.id == id } }) }

    fun open(id: String) {
        val updated = (listOf(id) + recent.filterNot { it == id }).take(5)
        recent = updated
        preferences.edit().putString("recent", updated.joinToString("|")).apply()
        selected = id
    }

    BackHandler(enabled = selected != null) { selected = null }
    Surface(modifier = Modifier.fillMaxSize(), color = backdrop) {
        if (selected == null) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 13.dp)) {
                    Text("GAMES", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = accent)
                    Spacer(Modifier.height(7.dp))
                    Text("Offline collection", fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, color = ink)
                    Spacer(Modifier.height(7.dp))
                    Text("${games.size} playable games · No internet needed", color = muted, fontSize = 13.sp)
                    Spacer(Modifier.height(19.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFDDE5DF))) {
                        BasicTextField(
                            value = search,
                            onValueChange = { search = it },
                            singleLine = true,
                            cursorBrush = SolidColor(accent),
                            textStyle = TextStyle(fontSize = 15.sp, color = ink),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
                            decorationBox = { field ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Search, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Box {
                                        if (search.isEmpty()) Text("Search games", color = muted, fontSize = 15.sp)
                                        field()
                                    }
                                }
                            }
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(option) }
                        )
                    }
                }
                val matched = games.filter {
                    (filter == "All" || (filter == "Favourites" && it.id in favourites) || it.category == filter) &&
                        (search.isBlank() || it.title.contains(search, ignoreCase = true) || it.category.contains(search, ignoreCase = true))
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    if (filter == "All" && search.isBlank() && recent.isNotEmpty()) {
                        item {
                            Text("RECENT", color = muted, letterSpacing = 1.7.sp, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                        }
                        items(recent.take(2), key = { "recent_${it}" }) { id ->
                            games.firstOrNull { it.id == id }?.let { game ->
                                GameRow(game, id in favourites, { open(id) }, {
                                    favourites = favourites.toggle(id)
                                    preferences.edit().putStringSet("favourites", favourites).apply()
                                })
                            }
                        }
                        item {
                            Spacer(Modifier.height(10.dp))
                            Text("ALL GAMES", color = muted, letterSpacing = 1.7.sp, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                        }
                    }
                    items(matched, key = { it.id }) { game ->
                        GameRow(game, game.id in favourites, { open(game.id) }, {
                            favourites = favourites.toggle(game.id)
                            preferences.edit().putStringSet("favourites", favourites).apply()
                        })
                    }
                    if (matched.isEmpty()) item {
                        Text("No games found", color = muted, modifier = Modifier.padding(top = 35.dp))
                    }
                }
            }
        } else if (selected == "2048") {
            Game2048Screen(onExit = { selected = null })
        } else {
            // A completely bundled, isolated version of the existing NIGHT Play arcade.
            // No INTERNET permission or remote script/assets are necessary.
            OfflineArcadeWebView(gameId = selected!!, onExit = { selected = null })
        }
    }
}

private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

@Composable
private fun GameRow(entry: GameEntry, favourite: Boolean, onOpen: () -> Unit, onFavourite: () -> Unit) {
    Surface(
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE3E8E1)),
        shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).clickable(onClick = onOpen).padding(start = 14.dp, top = 13.dp, bottom = 13.dp)
            ) {
                Surface(color = Color(0xFFECF3ED), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(53.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        val icon = when (entry.id) {
                            "2048" -> Icons.Outlined.ViewModule
                            "mines" -> Icons.Outlined.BugReport
                            "blocks" -> Icons.Outlined.Apps
                            "bird" -> Icons.Outlined.Flight
                            "maze" -> Icons.Outlined.Extension
                            else -> Icons.Outlined.SportsEsports
                        }
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(27.dp))
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column {
                    Text(entry.title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(entry.detail, color = muted, fontSize = 12.sp, maxLines = 2)
                }
            }
            IconButton(onClick = onFavourite, modifier = Modifier.padding(end = 7.dp)) {
                Icon(
                    if (favourite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (favourite) "Remove ${entry.title} from favourites" else "Favourite ${entry.title}",
                    tint = if (favourite) accent else muted
                )
            }
        }
    }
}

@Composable
private fun OfflineArcadeWebView(gameId: String, onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val loader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }
    AndroidView(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        factory = {
            WebView(it).apply {
                setBackgroundColor(android.graphics.Color.WHITE)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.setSupportZoom(false)
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (uri.host == "appassets.androidplatform.net" &&
                            uri.path?.startsWith("/arcade/") == true) return false
                        onExit()
                        return true
                    }
                }
                loadUrl("https://appassets.androidplatform.net/arcade/index.html?game=${gameId}")
            }
        },
        onRelease = { it.stopLoading(); it.destroy() }
    )
}
