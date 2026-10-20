package com.night.offlinegames

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val reversiInk = Color(0xFF1B4032)
private val reversiSurface = Color(0xFFF4F8F5)
private val blackDisc = Color(0xFF23362D)
private val whiteDisc = Color(0xFFF2F4EC)

@Composable
fun NativeReversiScreen(onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("native_reversi", Context.MODE_PRIVATE) }
    var state by remember {
        mutableStateOf(NativeReversiEngine.decode(prefs.getString("board", null)) ?: NativeReversiEngine.newGame())
    }
    var before by remember { mutableStateOf<NativeReversiState?>(null) }
    var previouslyRendered by remember { mutableStateOf(state.cells) }
    val progress = remember { Animatable(1f) }
    val moves = remember(state.cells) {
        if (state.finished) emptyList() else NativeReversiEngine.legal(state.cells, 1)
    }

    fun save(next: NativeReversiState) {
        state = next
        prefs.edit().putString("board", NativeReversiEngine.encode(next)).apply()
    }

    fun play(index: Int) {
        val next = NativeReversiEngine.play(state, index)
        if (next == state) return
        previouslyRendered = state.cells
        before = state
        save(next)
    }

    LaunchedEffect(state.cells) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(230))
    }

    Column(
        modifier = Modifier.fillMaxSize().background(reversiSurface).navigationBarsPadding()
            .padding(horizontal = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to games", tint = reversiInk)
            }
            Text("Reversi", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = reversiInk)
        }
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreSide("YOU · BLACK", state.black, blackDisc, Modifier.weight(1f))
            ScoreSide("CPU · WHITE", state.white, Color(0xFFBEC9C1), Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            when {
                state.finished && state.black > state.white -> "You win!"
                state.finished && state.black < state.white -> "The computer wins"
                state.finished -> "Draw — equal discs"
                else -> "Tap a highlighted square to place a disc"
            },
            fontSize = 14.sp, color = Color(0xFF587165)
        )
        Spacer(Modifier.height(18.dp))
        Surface(
            color = Color(0xFF154B35),
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().aspectRatio(1f),
            border = BorderStroke(2.dp, Color(0xFF134A36))
        ) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(state.cells, state.finished) {
                    detectTapGestures { point ->
                        if (state.finished) return@detectTapGestures
                        val x = (point.x / (size.width / 8f)).toInt().coerceIn(0, 7)
                        val y = (point.y / (size.height / 8f)).toInt().coerceIn(0, 7)
                        play(y * 8 + x)
                    }
                }
            ) {
                drawReversiBoard(state, moves, previouslyRendered, progress.value)
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                before?.let {
                    previouslyRendered = state.cells
                    save(it)
                }
                before = null
            }, enabled = before != null) { Text("Undo") }
            Button(onClick = {
                before = null
                previouslyRendered = state.cells
                save(NativeReversiEngine.newGame())
            }, colors = ButtonDefaults.buttonColors(containerColor = reversiInk)) {
                Text(if (state.finished) "Play again" else "New game")
            }
        }
    }
}

@Composable
private fun ScoreSide(label: String, value: Int, chip: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFDCE7DE)),
        shape = RoundedCornerShape(11.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Canvas(Modifier.size(15.dp)) { drawCircle(chip) }
            Column {
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6C8275))
                Text(value.toString(), color = reversiInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun DrawScope.drawReversiBoard(
    state: NativeReversiState,
    allowed: List<Int>,
    previouslyRendered: List<Int>,
    animation: Float
) {
    val unitX = size.width / 8f
    val unitY = size.height / 8f
    for (y in 0 until 8) for (x in 0 until 8) {
        val i = y * 8 + x
        val top = Offset(x * unitX, y * unitY)
        val center = Offset(top.x + unitX / 2, top.y + unitY / 2)
        drawRect(
            if ((x + y) % 2 == 0) Color(0xFF286F4B) else Color(0xFF347B55),
            top, Size(unitX, unitY)
        )
        drawRect(
            Color(0x5B113B2C), top, Size(unitX, unitY),
            style = Stroke(width = 1f)
        )
        val mark = state.cells[i]
        val animated = if (mark != previouslyRendered[i]) animation else 1f
        if (mark != 0) {
            val radius = (minOf(unitX, unitY) * .385f) * animated
            drawCircle(Color(0x40000000), radius + 1.5f,
                center = center + Offset(1.5f, 2f))
            drawCircle(if (mark == 1) blackDisc else whiteDisc, radius, center)
            drawCircle(if (mark == 1) Color(0xFF63796A) else Color(0xFFCBD8CC),
                radius * .78f, center,
                style = Stroke(width = minOf(unitX, unitY) * .027f))
            drawCircle(
                if (mark == 1) Color.White.copy(alpha = .14f) else Color.White.copy(alpha = .65f),
                radius * .25f, center + Offset(-radius * .32f, -radius * .27f)
            )
        } else if (i in allowed) {
            drawCircle(Color(0xE6D4F0D9).copy(alpha = .53f),
                radius = minOf(unitX, unitY) * .105f, center = center)
        }
    }
}
