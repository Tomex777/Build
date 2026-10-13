package com.night.sora.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.R
import com.night.sora.ui.theme.*

private data class ArcadeEntry(val key: String, val title: String, val subtitle: String, val icon: Int)

private val arcadeEntries = listOf(
    ArcadeEntry("snake", "Snake", "Swipe to steer, survive and grow", R.drawable.sora_game_snake),
    ArcadeEntry("tiles", "2048", "Swipe and merge matching numbers", R.drawable.sora_game_tiles),
    ArcadeEntry("ttt", "Tic-tac-toe", "Challenge a computer opponent", R.drawable.sora_game_tictactoe),
    ArcadeEntry("memory", "Memory match", "Find all eight hidden pairs", R.drawable.sora_game_memory),
    ArcadeEntry("quiz", "Cover quiz", "Guess titles saved in your Library", R.drawable.sora_game_memory),
)

@Composable
fun SoraArcadePage(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    if (selected.isEmpty()) {
        LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Games", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("Real playable games. The arcade works offline.", color = SoraMuted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
            items(arcadeEntries, key = { it.key }) { game ->
                Surface(modifier = Modifier.fillMaxWidth().clickable { onSelect(game.key) },
                    color = SoraSurfaceHigh, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp), color = SoraSurfaceRaised) {
                            Icon(painter = painterResource(game.icon), contentDescription = null,
                                tint = Color.Unspecified, modifier = Modifier.padding(10.dp).size(39.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 13.dp)) {
                            Text(game.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(game.subtitle, color = SoraMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight, "Play", tint = SoraAccent)
                    }
                }
            }
        }
        return
    }
    val game = arcadeEntries.firstOrNull { it.key == selected } ?: return
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 18.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelect("") }) { Icon(Icons.Rounded.ArrowBack, "All games") }
            Column(Modifier.weight(1f)) {
                Text(game.title, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text(game.subtitle, color = SoraMuted, fontSize = 11.sp)
            }
            Icon(painterResource(game.icon), null, tint = Color.Unspecified, modifier = Modifier.size(40.dp))
        }
        when(selected) {
            "snake" -> SnakeArcade(Modifier.weight(1f))
            "tiles" -> TilesArcade(Modifier.weight(1f))
            "ttt" -> TicTacToeArcade(Modifier.weight(1f))
            "memory" -> MemoryArcade(Modifier.weight(1f))
        }
    }
}
