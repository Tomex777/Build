package com.night.sora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.ui.theme.SoraAccent
import com.night.sora.ui.theme.SoraMuted
import com.night.sora.ui.theme.SoraSurface

private data class GameItem(val title: String, val subtitle: String, val icon: ImageVector)

private val soraGames = listOf(
    GameItem("Who am I?", "Sora describes an anime or manga character. You guess who it is.", Icons.Rounded.HelpOutline),
    GameItem("Who said it?", "Match quotes to characters, films or shows you know.", Icons.Rounded.FormatQuote),
    GameItem("Bible trivia", "Questions based on books and passages you've read.", Icons.Rounded.MenuBook),
    GameItem("Would you rather?", "AI-generated choices based on your interests and media.", Icons.Rounded.SwapHoriz),
    GameItem("Meme caption", "Caption a random meme. Sora judges the result.", Icons.Rounded.TagFaces),
)

@Composable
fun GamesScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 4.dp)) {
                Text("Games", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                Text("Play something.", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp)
                Text("Tiny games built around what you already like.", color = SoraMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
            }
        }
        item { DailyFiveCard() }
        item {
            Text("This week", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeekStat("18", "Played", Modifier.weight(1f))
                WeekStat("72%", "Correct", Modifier.weight(1f))
                WeekStat("4", "Streak", Modifier.weight(1f))
            }
        }
        item {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 10.dp)) {
                Text("Quick games", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Generated from your library", color = SoraMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        soraGames.forEach { game -> item { GameRow(game) } }
    }
}

@Composable
private fun DailyFiveCard() {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)
            .heightIn(min = 205.dp)
            .background(Color(0xFFE7DFCB), RoundedCornerShape(22.dp))
            .padding(21.dp),
    ) {
        Column(Modifier.widthIn(max = 235.dp)) {
            Text("TODAY'S GAME", color = Color(0xFF161512), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Text("Daily 5", color = Color(0xFF161512), fontSize = 31.sp, lineHeight = 31.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 16.dp))
            Text(
                "Five questions pulled from your anime, manga, movies, music and Bible activity.",
                color = Color(0xFF615D53), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF171612), contentColor = Color(0xFFF5F1E8)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.padding(top = 18.dp),
            ) { Text("Play today's five", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        Text("5", color = Color(0x141C1B17), fontSize = 146.sp, lineHeight = 146.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.BottomEnd).offset(x = 13.dp, y = 42.dp))
    }
}

@Composable
private fun WeekStat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.background(Color(0xFF151513), RoundedCornerShape(15.dp)).padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label.uppercase(), color = SoraMuted, fontSize = 9.sp, letterSpacing = .7.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun GameRow(game: GameItem) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp)
            .background(SoraSurface, RoundedCornerShape(18.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).background(Color(0xFF272621), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(game.icon, null, tint = SoraAccent, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(game.title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(game.subtitle, color = SoraMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Color(0xFF737168))
    }
}