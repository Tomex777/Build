package com.night.sora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.R
import com.night.sora.model.LibraryEntry
import com.night.sora.ui.theme.*

private data class ArcadeEntry(val key: String, val title: String, val subtitle: String, val icon: Int)

private val arcadeEntries = listOf(
    ArcadeEntry("snake", "Snake", "Swipe to steer, survive and grow", R.drawable.sora_game_snake),
    ArcadeEntry("tiles", "2048", "Swipe and merge matching numbers", R.drawable.sora_game_tiles),
    ArcadeEntry("ttt", "Tic-tac-toe", "Challenge a computer opponent", R.drawable.sora_game_tictactoe),
    ArcadeEntry("memory", "Memory match", "Find all eight hidden pairs", R.drawable.sora_game_memory),
    ArcadeEntry("quiz", "Cover quiz", "Guess titles saved in your Library", R.drawable.sora_game_quiz),
    ArcadeEntry("daily", "Daily 5", "Five quick questions, fresh each session", R.drawable.sora_game_daily),
    ArcadeEntry("who", "Who am I?", "Identify the character from its clues", R.drawable.sora_game_who),
    ArcadeEntry("quotes", "Who said it?", "Match a famous line to its speaker", R.drawable.sora_game_quotes),
    ArcadeEntry("bible", "Bible trivia", "Test your Bible knowledge", R.drawable.sora_game_bible),
    ArcadeEntry("rather", "Would you rather?", "Make tough choices; see your own picks", R.drawable.sora_game_rather),
    ArcadeEntry("meme", "Meme caption", "Write captions for saved memes", R.drawable.sora_game_meme),
)

@Composable
fun SoraArcadePage(selected: String, onSelect: (String) -> Unit, library: List<LibraryEntry>, modifier: Modifier = Modifier) {
    if (selected.isEmpty()) {
        // Cards are intentionally bigger than the original compact list:
        // game SVGs are readable at 360dp and the entire card is touchable.
        // A 2-column layout makes the full arcade discoverable without hiding
        // eight games below a long sequence of identical-looking rows.
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
                    Text("PLAY ANYWHERE", fontSize = 10.sp, color = SoraAccent,
                        fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
                    Text("Games", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    Text("11 playable games · Offline-friendly",
                        color = SoraMuted, fontSize = 12.sp)
                }
            }
            items(arcadeEntries, key = { it.key }) { game ->
                val accent = when (game.key) {
                    "snake", "who", "memory" -> Color(0xFF70D8AB)
                    "tiles", "daily", "meme" -> Color(0xFFFFC86A)
                    "ttt", "quotes", "bible" -> Color(0xFF9CBBFF)
                    else -> Color(0xFFF3A7BA)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(game.key) },
                    color = SoraSurfaceHigh,
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().height(177.dp)
                            .background(Brush.verticalGradient(listOf(accent.copy(alpha = .13f),
                                SoraSurfaceHigh, SoraSurfaceHigh)))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Surface(shape = RoundedCornerShape(14.dp),
                                color = accent.copy(alpha = .12f)) {
                                Icon(painter = painterResource(game.icon), contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.padding(9.dp).size(46.dp))
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null,
                                tint = accent, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(game.title, fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Text(game.subtitle, color = SoraMuted, fontSize = 10.sp,
                                lineHeight = 13.sp, maxLines = 2)
                        }
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
            "daily", "who", "quotes", "bible" -> MediaTriviaArcade(selected, Modifier.weight(1f))
            "rather" -> RatherArcade(Modifier.weight(1f))
            "meme" -> MemeCaptionArcade(library, Modifier.weight(1f))
        }
    }
}
