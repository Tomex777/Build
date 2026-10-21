package com.night.offlinegames

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun OfflineHub() {
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
        } else if (selected == "sliding") {
            NativeSlidingScreen(onExit = { selected = null })
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

