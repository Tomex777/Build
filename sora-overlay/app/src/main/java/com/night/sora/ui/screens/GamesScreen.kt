package com.night.sora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.ui.theme.SoraMuted
import com.night.sora.ui.theme.SoraSurface

private data class SoraGame(val title: String, val subtitle: String, val icon: ImageVector)

private val soraGames = listOf(
    SoraGame("Most Likely To", "Pick the person who fits the prompt", Icons.Rounded.Groups),
    SoraGame("Would You Rather", "Choose between two impossible options", Icons.Rounded.SwapHoriz),
    SoraGame("Who Knows Me Best", "Find out who actually knows you", Icons.Rounded.Psychology),
    SoraGame("Never Have I Ever", "Confessions without the boring setup", Icons.Rounded.BackHand),
    SoraGame("Hot Seat", "One person. Everyone asks.", Icons.Rounded.LocalFireDepartment),
    SoraGame("This or That", "Fast choices, no overthinking", Icons.Rounded.CompareArrows),
)

@Composable
fun GamesScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 22.dp)) {
                Text("Games", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Play solo or bring people in.", color = SoraMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }

        items((soraGames.size + 1) / 2) { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val first = soraGames[row * 2]
                GameCard(first, Modifier.weight(1f))
                val second = soraGames.getOrNull(row * 2 + 1)
                if (second != null) GameCard(second, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GameCard(game: SoraGame, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(154.dp)
            .background(SoraSurface, RoundedCornerShape(16.dp))
            .padding(15.dp),
    ) {
        Icon(game.icon, null, modifier = Modifier.size(25.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        Text(game.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
        Text(game.subtitle, color = SoraMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
