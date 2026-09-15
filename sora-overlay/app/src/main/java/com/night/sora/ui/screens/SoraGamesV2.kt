package com.night.sora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.ui.theme.SoraMuted
import com.night.sora.ui.theme.SoraSurface

private data class PersonalGame(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tag: String,
)

private val libraryGames = listOf(
    PersonalGame("Guess the title", "Sora gives clues from things you know", Icons.Rounded.Lightbulb, "LIBRARY"),
    PersonalGame("Cover memory", "How well do you remember your own shelf?", Icons.Rounded.Image, "VISUAL"),
    PersonalGame("Higher or lower", "Compare ratings, popularity and release dates", Icons.Rounded.SwapVert, "MEDIA"),
)

private val musicGames = listOf(
    PersonalGame("Finish the lyric", "A music challenge built around what you play", Icons.Rounded.MusicNote, "MUSIC"),
    PersonalGame("Name that artist", "Recognise artists from hints and artwork", Icons.Rounded.Headphones, "MUSIC"),
)

private val quietGames = listOf(
    PersonalGame("Bible trivia", "Short rounds from books and stories", Icons.Rounded.MenuBook, "BIBLE"),
    PersonalGame("Story chain", "You and Sora build a story one turn at a time", Icons.Rounded.AutoStories, "AI"),
)

@Composable
fun SoraGamesScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 116.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 22.dp)) {
                Text("Games", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Small games made from your Sora world.", color = SoraMuted, fontSize = 13.sp)
            }
        }

        item {
            Surface(
                color = SoraSurface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("PLAY WITH SORA", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("Guess the title", fontSize = 25.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 18.dp))
                    Text("Sora picks something from anime, manga, movies or music and gives you increasingly obvious clues.", color = SoraMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
                    Button(onClick = {}, shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 18.dp)) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Play")
                    }
                }
            }
        }

        item { GameSection("From your library", libraryGames) }
        item { GameSection("Music games", musicGames) }
        item { GameSection("More", quietGames) }
    }
}

@Composable
private fun GameSection(title: String, games: List<PersonalGame>) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(games, key = { it.title }) { game ->
                Surface(
                    color = SoraSurface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.width(196.dp).height(142.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(game.icon, null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.weight(1f))
                            Text(game.tag, color = SoraMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(game.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(game.subtitle, color = SoraMuted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }
}
