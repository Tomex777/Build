package com.night.offlinegames

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val slidingInk = Color(0xFF193B31)
private val slidingGreen = Color(0xFF236C4F)

/** Fully offline native sliding puzzle. Tiles animate to their new positions. */
@Composable
fun NativeSlidingScreen(onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("native_sliding", Context.MODE_PRIVATE) }
    var state by remember {
        mutableStateOf(NativeSlidingEngine.decode(prefs.getString("board", null))
            ?: NativeSlidingEngine.newGame())
    }
    fun apply(next: NativeSlidingState) {
        state = next
        prefs.edit().putString("board", NativeSlidingEngine.encode(next)).apply()
    }
    Column(
        Modifier.fillMaxSize().navigationBarsPadding()
            .background(Color(0xFFF6F8F5)).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) { Text("Back", color = slidingInk) }
            Text("Sliding Puzzle", color = slidingInk,
                fontWeight = FontWeight.Bold, fontSize = 25.sp)
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("LEVEL ${state.level}", fontWeight = FontWeight.Bold, color = slidingInk)
            Text("MOVES ${state.moves}", fontWeight = FontWeight.Bold, color = slidingInk)
            Text("IN PLACE ${state.tiles.indices.count { state.tiles[it] == it + 1 }}/15",
                fontWeight = FontWeight.Bold, color = slidingInk)
        }
        Spacer(Modifier.height(18.dp))
        Text(if (state.won) "Puzzle solved!" else "Slide a tile into the empty space.",
            color = slidingGreen, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))
        BoxWithConstraints(
            Modifier.fillMaxWidth().widthIn(max = 460.dp).aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp)).background(Color(0xFFDEE7DF))
                .semantics { contentDescription = "Sliding Puzzle board" }
        ) {
            val tileSide = maxWidth / 4
            for (tile in 1..15) {
                key(tile) {
                    val index = state.tiles.indexOf(tile)
                    val row = index / 4
                    val col = index % 4
                    val x by animateDpAsState(tileSide * col, tween(135), label = "tile-x-$tile")
                    val y by animateDpAsState(tileSide * row, tween(135), label = "tile-y-$tile")
                    val inPlace = index == tile - 1
                    Box(
                        Modifier.offset(x = x, y = y).size(tileSide).padding(3.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (inPlace) Color(0xFFD0E8D9) else Color.White)
                            .clickable(enabled = !state.won) {
                                apply(NativeSlidingEngine.move(state, state.tiles.indexOf(tile)))
                            }
                            .semantics { contentDescription = "Tile $tile" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$tile", fontWeight = FontWeight.Bold, fontSize = 29.sp,
                            color = if (inPlace) slidingGreen else slidingInk)
                    }
                }
            }
        }
        Spacer(Modifier.height(25.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                enabled = state.history.isNotEmpty(),
                onClick = { apply(NativeSlidingEngine.undo(state)) }
            ) { Text("Undo") }
            Button(
                onClick = {
                    apply(if (state.won)
                        NativeSlidingEngine.newGame((state.level + 1).coerceAtMost(10000), state.seed + 1)
                    else NativeSlidingEngine.newGame(state.level, state.seed + 1))
                },
                colors = ButtonDefaults.buttonColors(containerColor = slidingInk)
            ) { Text(if (state.won) "Next level" else "New puzzle") }
        }
        Spacer(Modifier.height(18.dp))
        Text("Arrange 1–15 from left to right, top to bottom.",
            color = Color(0xFF71847C), fontSize = 12.sp)
    }
}
