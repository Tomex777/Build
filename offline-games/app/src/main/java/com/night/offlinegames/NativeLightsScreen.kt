package com.night.offlinegames

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val lightsInk = Color(0xFF203A35)

/** Offline native 5x5 Lights Out with save, undo, next level and mathematically valid hints. */
@Composable
fun NativeLightsScreen(onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("native_lights", Context.MODE_PRIVATE) }
    var state by remember {
        mutableStateOf(NativeLightsEngine.decode(prefs.getString("board", null))
            ?: NativeLightsEngine.newGame())
    }
    var hint by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state) { prefs.edit().putString("board", NativeLightsEngine.encode(state)).apply() }
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().background(Color(0xFFF5F8F4))
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) { Text("Back", color = lightsInk) }
            Text("Lights Out", color = lightsInk, fontSize = 27.sp,
                fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("LEVEL ${state.level}", color = lightsInk, fontWeight = FontWeight.Bold)
            Text("MOVES ${state.moves}", color = lightsInk, fontWeight = FontWeight.Bold)
            Text("LIT ${state.litCount}", color = lightsInk, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text(if (state.won) "All lights off. Puzzle solved!"
            else "Switch off every light.", color = Color(0xFF2E7259))
        Spacer(Modifier.height(20.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).aspectRatio(1f),
            shape = RoundedCornerShape(18.dp), color = Color(0xFF18342E)
        ) {
            Box(Modifier.fillMaxSize().padding(8.dp)) {
                Canvas(Modifier.fillMaxSize()) { drawLightsBoard(state, hint) }
                Column(Modifier.fillMaxSize()) {
                    repeat(NativeLightsEngine.side) { row ->
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            repeat(NativeLightsEngine.side) { col ->
                                val index = row * NativeLightsEngine.side + col
                                val lit = state.board and (1 shl index) != 0
                                Box(
                                    Modifier.weight(1f).fillMaxHeight()
                                        .clickable(enabled = !state.won) {
                                            state = NativeLightsEngine.tap(state, index)
                                            hint = null
                                        }
                                        .semantics {
                                            contentDescription = "Light row ${row + 1}, column ${col + 1}: ${if (lit) "on" else "off"}"
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { state = NativeLightsEngine.undo(state); hint = null },
                enabled = state.history.isNotEmpty()
            ) { Text("Undo") }
            OutlinedButton(
                onClick = { hint = NativeLightsEngine.solve(state.board)?.firstOrNull() },
                enabled = !state.won
            ) { Text("Hint") }
            Button(
                onClick = {
                    state = if (state.won) NativeLightsEngine.newGame(state.level + 1, state.seed + 1)
                    else NativeLightsEngine.newGame(state.level, state.seed + 1)
                    hint = null
                },
                colors = ButtonDefaults.buttonColors(containerColor = lightsInk)
            ) { Text(if (state.won) "Next level" else "New puzzle") }
        }
        Spacer(Modifier.height(16.dp))
        Text(when {
            state.won -> "Ready for the next challenge."
            hint != null -> "The outlined light is a valid next move."
            else -> "Each tap flips that light and its neighbors."
        }, color = Color(0xFF71847C), fontSize = 12.sp)
    }
}
