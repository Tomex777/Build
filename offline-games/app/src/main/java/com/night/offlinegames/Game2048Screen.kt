package com.night.offlinegames

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val darkInk = Color(0xFF1C3027)
private val boardBackground = Color(0xFFBAADA1)
private val tileColours = mapOf(
    0 to Color(0xFFCEC1B4),
    2 to Color(0xFFEEE4DA),
    4 to Color(0xFFEDE0C8),
    8 to Color(0xFFF2B179),
    16 to Color(0xFFF59563),
    32 to Color(0xFFF67C5F),
    64 to Color(0xFFF65E3B),
    128 to Color(0xFFEDCF72),
    256 to Color(0xFFEDCC61),
    512 to Color(0xFFEDC850),
    1024 to Color(0xFFEDC53F),
    2048 to Color(0xFFEDC22E)
)

@Composable
fun Game2048Screen(onExit: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("game_2048", Context.MODE_PRIVATE) }
    var board by remember { mutableStateOf(Board2048.decode(prefs.getString("board", null)) ?: Board2048.newGame()) }
    var best by remember { mutableStateOf(maxOf(board.score, prefs.getInt("best", 0))) }

    fun commit(next: BoardState) {
        if (next == board) return
        board = next
        best = maxOf(best, next.score)
        prefs.edit().putString("board", Board2048.encode(next)).putInt("best", best).apply()
    }

    Column(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().background(Color(0xFFF8F8F5))
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onExit, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to games", tint = darkInk)
            }
            Text("2048", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = darkInk)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreCard("SCORE", board.score, Modifier.weight(1f))
            ScoreCard("BEST", best, Modifier.weight(1f))
        }
        Spacer(Modifier.height(26.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth().aspectRatio(1f),
                color = boardBackground,
                shape = RoundedCornerShape(12.dp)
            ) {
                BoardGrid(board) { direction -> commit(Board2048.move(board, direction)) }
            }
        }
        Spacer(Modifier.height(22.dp))
        val status = when {
            board.gameOver -> "No moves left"
            board.won -> "2048 reached! Keep going."
            else -> "Swipe to combine matching tiles"
        }
        Text(status, fontSize = 13.sp, color = Color(0xFF69756D), modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { commit(Board2048.newGame()) },
            colors = ButtonDefaults.buttonColors(containerColor = darkInk),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("New game", modifier = Modifier.padding(horizontal = 13.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun ScoreCard(label: String, amount: Int, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE3E8E0)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            Text(label, color = Color(0xFF66746C), fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(amount.toString(), color = darkInk, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BoardGrid(board: BoardState, onMove: (Direction) -> Unit) {
    val currentOnMove by rememberUpdatedState(onMove)
    val gesture = Modifier.pointerInput(Unit) {
        var dx = 0f
        var dy = 0f
        var dispatched = false
        detectDragGestures(
            onDragStart = { dx = 0f; dy = 0f; dispatched = false },
            onDrag = { change, delta: Offset ->
                change.consume()
                if (!dispatched) {
                    dx += delta.x
                    dy += delta.y
                    val threshold = 20.dp.toPx()
                    if (abs(dx) > threshold || abs(dy) > threshold) {
                        val direction = if (abs(dx) > abs(dy)) {
                            if (dx > 0f) Direction.RIGHT else Direction.LEFT
                        } else {
                            if (dy > 0f) Direction.DOWN else Direction.UP
                        }
                        dispatched = true
                        currentOnMove(direction)
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().then(gesture).padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        repeat(4) { y ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                repeat(4) { x ->
                    val value = board.values[y * 4 + x]
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .background(tileColours[value] ?: Color(0xFF3D6256), RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (value > 0) Text(
                            value.toString(),
                            fontSize = when {
                                value >= 16384 -> 17.sp
                                value >= 1024 -> 21.sp
                                value >= 128 -> 25.sp
                                else -> 30.sp
                            },
                            fontWeight = FontWeight.Bold,
                            color = if (value <= 4) Color(0xFF645B50) else Color.White
                        )
                    }
                }
            }
        }
    }
}
