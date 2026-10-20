package com.night.offlinegames

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
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    GameEntry("maze", "Maze Chase", "Arcade", "Collect dots, dodge chasers"),
    GameEntry("sudoku", "Sudoku", "Puzzle", "Complete the number grid"),
    GameEntry("connect", "Connect Four", "Board", "Outsmart the computer"),
    GameEntry("ttt", "Tic-Tac-Toe", "Board", "Challenge the perfect computer"),
    GameEntry("reversi", "Reversi", "Board", "Flip your opponent's discs"),
    GameEntry("dots", "Dots & Boxes", "Board", "Complete squares and claim points"),
    GameEntry("mancala", "Mancala", "Board", "Sow seeds and capture stones"),
    GameEntry("hex", "Hex", "Board", "Connect your opposite sides"),
    GameEntry("battleship", "Battleship", "Board", "Find the hidden fleet"),
    GameEntry("pentago", "Pentago", "Board", "Place and rotate to connect five"),
    GameEntry("lights", "Lights Out", "Puzzle", "Switch every light off"),
    GameEntry("sliding", "Sliding Puzzle", "Puzzle", "Restore the scrambled 15 tiles"),
    GameEntry("hanoi", "Tower of Hanoi", "Puzzle", "Transfer five disks legally"),
    GameEntry("colorsort", "Water Sort Puzzle", "Puzzle", "Pour matching colours into the same tubes"),
    GameEntry("nonogram", "Nonogram", "Puzzle", "Decode the picture from clues"),
    GameEntry("wordsearch", "Word Search", "Puzzle", "Find six words in the grid"),
    GameEntry("simon", "Simon", "Memory", "Repeat a growing colour pattern"),
    GameEntry("pattern", "Pattern Recall", "Memory", "Remember the highlighted tiles"),
    GameEntry("nim", "Take Away", "Strategy", "Plan your last move"),
    GameEntry("higher", "Higher or Lower", "Card", "Guess the next card"),
    GameEntry("war", "War", "Card", "Reveal cards and compare ranks"),
    GameEntry("snap", "Snap", "Card", "Spot identical ranks"),
    GameEntry("crazyeights", "Crazy Eights", "Card", "Match cards against the CPU"),
    GameEntry("pong", "Pong Classic", "Arcade", "Outplay the computer paddle"),
    GameEntry("breakout", "Breakout", "Arcade", "Clear every brick"),
    GameEntry("invaders", "Space Invaders", "Arcade", "Defend against descending invaders"),
    GameEntry("fruit", "Fruit Slash", "Action", "Slice fruit without hitting hazards"),
    GameEntry("snake", "Snake", "Arcade", "Grow longer, swipe to turn"),
    GameEntry("frogger", "Frogger", "Arcade", "Dodge the moving traffic"),
    GameEntry("stack", "Stack Tower", "Endless", "Time your moving layers"),
    GameEntry("whack", "Whack-a-Bunny", "Action", "Tap the popping bunnies"),
    GameEntry("quickdraw", "Quick Draw", "Action", "React when green appears"),
    GameEntry("coins", "Coin Catcher", "Action", "Catch coins, dodge falling rocks"),
    GameEntry("bubble", "Bubble Pop", "Action", "Pop rising bubbles against time"),
    GameEntry("gravity", "Gravity Flip", "Endless", "Switch between floor and ceiling"),
    GameEntry("go", "Go 5 × 5", "Board", "Capture stones and claim territory"),
    GameEntry("morris", "Nine Men's Morris", "Board", "Form mills and capture pieces"),
    GameEntry("golf", "Golf Solitaire", "Card", "Clear cards in ascending runs"),
    GameEntry("pyramid", "Pyramid Solitaire", "Card", "Remove pairs adding to thirteen"),
    GameEntry("tripeaks", "Tri Peaks", "Card", "Uncover three card peaks"),
    GameEntry("freecell", "FreeCell", "Card", "Four cells, eight cascades"),
    GameEntry("solitaire", "Solitaire", "Card", "Classic draw-one Klondike"),
    GameEntry("mazerunner", "Maze Runner", "Puzzle", "Navigate a new carved labyrinth"),
    GameEntry("darts", "Darts", "Sports", "Time your dart throws"),
    GameEntry("basketball", "Basketball Shoot", "Sports", "Flick a ball into the basket"),
    GameEntry("penalty", "Penalty Shootout", "Sports", "Beat the diving goalkeeper"),
    GameEntry("ski", "Ski Slalom", "Sports", "Stay between the racing gates"),
    GameEntry("bomb", "Bomb Diffuser", "Action", "Cut the correct wires in time"),
    GameEntry("car", "Car Dodge", "Racing", "Weave between speeding traffic"),
    GameEntry("zigzag", "Zigzag Path", "Endless", "Survive the winding path"),
    GameEntry("laser", "Laser Dodge", "Action", "Evade timed sweeping beams"),
    GameEntry("archery", "Archery", "Sports", "Aim against shifting wind"),
    GameEntry("golfputt", "Golf Putt", "Sports", "Play three precision holes"),
    GameEntry("bowling", "Bowling", "Sports", "Ten frames, strikes and spares"),
    GameEntry("swimming", "Swimming Race", "Sports", "Race fifty meters with rhythm"),
    GameEntry("checkers", "Checkers", "Board", "Capture, crown, and chain-jump"),
    GameEntry("chess", "Chess", "Board", "Full legal chess against the CPU"),
    GameEntry("drag", "Drag Race", "Racing", "Shift perfectly through four hundred meters"),
    GameEntry("biketrail", "Bike Trail", "Racing", "Jump rocky hazards"),
    GameEntry("boat", "Boat Race", "Racing", "Navigate floating race gates"),
    GameEntry("drifthunt", "Drift Hunt", "Racing", "Build drifting combos"),
    GameEntry("horse", "Horse Derby", "Racing", "Alternate strides to beat the CPU"),
    GameEntry("memory", "Memory Match", "Memory", "Match illustrated animals, vehicles and space cards"),
    GameEntry("speedboat", "Speed Boat", "Racing", "Boost past river rocks"),
    GameEntry("jetsprint", "Jet Sprint", "Racing", "Navigate a narrowing canyon"),
    GameEntry("spacerace", "Space Race", "Racing", "Collect beacons by inertia"),
    GameEntry("cycle", "Cycle Sprint", "Racing", "Alternate left and right pedals"),
    GameEntry("kart", "Kart Circuit", "Racing", "Race three laps"),
    GameEntry("rally", "Rally Cross", "Racing", "Steer on loose gravel"),
    GameEntry("driftking", "Drift King", "Racing", "Choose the correct corner drift"),
    GameEntry("football", "Football Kick", "Sports", "Curve free kicks past defenders"),
    GameEntry("tabletennis", "Table Tennis", "Sports", "Return balls to eleven"),
    GameEntry("boxing", "Boxing Tap", "Sports", "Read and counter incoming punches"),
    GameEntry("longjump", "Long Jump", "Sports", "Perfect the takeoff"),
    GameEntry("weightlifting", "Weight Lifting", "Sports", "Control five lifts"),
    GameEntry("dartwheel", "Dart Wheel", "Sports", "Place darts on a spinning wheel"),
    GameEntry("tapshooter", "Tap Shooter", "Action", "Hit quick moving targets"),
    GameEntry("zombies", "Zombie Zapper", "Action", "Defend the barricade"),
    GameEntry("ninja", "Ninja Stars", "Action", "Avoid previously placed stars"),
    GameEntry("colorclash", "Color Clash", "Action", "Tap the requested colour"),
    GameEntry("shield", "Shield Block", "Action", "Rotate toward incoming projectiles"),
    GameEntry("balloonguard", "Balloon Guard", "Action", "Protect a rising balloon"),
    GameEntry("towerdefense", "Tower Defense", "Strategy", "Build towers against ten waves"),
    GameEntry("bikerunner", "Bike Runner", "Endless", "Duck and jump over trail hazards"),
    GameEntry("colorswitch", "Color Switch", "Endless", "Match a spinning hoop"),
    GameEntry("ballbounce", "Ball Bounce", "Endless", "Climb bouncing platforms"),
    GameEntry("helix", "Helix Jump", "Endless", "Align gaps in the rotating tower"),
    GameEntry("tapdash", "Tap Tap Dash", "Endless", "Tap at the highlighted corners"),
    GameEntry("paperplane", "Paper Plane", "Endless", "Glide between floating rings"),
    GameEntry("cats", "Cat Collector", "Endless", "Sort cats into coloured shelters"),
    GameEntry("wavesurfer", "Wave Surfer", "Endless", "Lean into the waves"),
    GameEntry("tunnel", "Tunnel Rush", "Endless", "Dodge approaching tunnel panels"),
    GameEntry("pinball", "Pinball Wizard", "Arcade", "Responsive flippers and bumpers"),
    GameEntry("asteroids", "Asteroids", "Arcade", "Thrust, aim and clear drifting rocks"),
    GameEntry("blackjack", "Blackjack", "Card", "Reach twenty-one against the dealer"),
    GameEntry("pokerdice", "Poker Dice", "Card", "Roll and hold five dice combinations"),
    GameEntry("speed", "Speed", "Card", "Race the CPU on two center piles"),
    GameEntry("patternlock", "Pattern Lock", "Puzzle", "Remember the connected-dot sequence"),
    GameEntry("brickbuilder", "Brick Builder", "Puzzle", "Clear complete lines and squares"),
    GameEntry("centipede", "Centipede", "Arcade", "Shoot descending segmented enemies"),
    GameEntry("galaga", "Galaga Blitz", "Arcade", "Survive five diving attack formations"),
    GameEntry("shark", "Shark Survival", "Action", "Swim freely, eat prey and evade ocean predators")
)
private val categories = listOf("All", "Favourites", "Arcade", "Puzzle", "Board", "Memory", "Strategy", "Card", "Action", "Endless", "Sports", "Racing")

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

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun open(id: String) {
        focusManager.clearFocus()
        keyboard?.hide()
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
                    Text("${games.size} games", color = muted, fontSize = 13.sp)
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
        } else if (selected == "mines") {
            NativeMinesScreen(onExit = { selected = null })
        } else if (selected == "lights") {
            NativeLightsScreen(onExit = { selected = null })
        } else if (selected == "reversi") {
            NativeReversiScreen(onExit = { selected = null })
        } else if (selected == "snake") {
            NativeSnakeScreen(onExit = { selected = null })
        } else if (selected == "ttt") {
            NativeTicTacToeScreen(onExit = { selected = null })
        } else if (selected == "colorsort") {
            WaterSortScreen(onExit = { selected = null })
        } else if (selected == "shark") {
            SharkSurvivalScreen(onExit = { selected = null })
        } else {
            // A completely bundled, isolated version of the existing NIGHT Play arcade.
            // No INTERNET permission or remote script/assets are necessary.
            OfflineBundledGameView(
                gameId = selected!!,
                directory = when (selected) { "sudoku", "connect" -> "puzzles"; "mines", "blocks", "bird", "maze" -> "arcade"; else -> "classics" },
                onExit = { selected = null }
            )
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
                modifier = Modifier.weight(1f).clickable(onClick = onOpen)
                    .semantics { contentDescription = "Open ${entry.title}" }
                    .padding(start = 14.dp, top = 13.dp, bottom = 13.dp)
            ) {
                Surface(color = Color(0xFFECF3ED), shape = RoundedCornerShape(10.dp), modifier = Modifier.size(53.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        val icon = when (entry.id) {
                            "2048" -> Icons.Outlined.ViewModule
                            "colorsort" -> Icons.Outlined.WaterDrop
                            "shark" -> Icons.Outlined.Waves
                            "mines" -> Icons.Outlined.BugReport
                            "blocks" -> Icons.Outlined.Apps
                            "bird" -> Icons.Outlined.Flight
                            "maze" -> Icons.Outlined.Extension
                            "sudoku" -> Icons.Outlined.ViewModule
                            "connect" -> Icons.Outlined.FilterNone
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
private fun OfflineBundledGameView(gameId: String, directory: String, onExit: () -> Unit) {
    // Android 8 emulator images can ship without any WebView provider. Never crash the
    // entire offline-games app when one of the remaining legacy games is opened.
    // Remove this path entirely once the last legacy game has a native implementation.
    if (WebView.getCurrentWebViewPackage() == null) {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Native version in progress", color = ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(Modifier.height(12.dp))
            Text("This game still relies on the device's WebView component, which is unavailable here. It is being rebuilt in Kotlin.", color = muted)
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onExit) { Text("Back to games") }
        }
        return
    }
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
                            uri.path?.startsWith("/" + directory + "/") == true) return false
                        onExit()
                        return true
                    }
                }
                loadUrl("https://appassets.androidplatform.net/" + directory + "/index.html?game=" + gameId)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() }
    )
}
