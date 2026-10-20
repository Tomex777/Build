package com.night.offlinegames

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

private val snakeInk = Color(0xFF173E35)
private val snakeDark = Color(0xFF23674F)
private val snakeBright = Color(0xFF57B687)

@Composable
fun NativeSnakeScreen(onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("native_snake", Context.MODE_PRIVATE) }
    var state by remember { mutableStateOf(NativeSnakeEngine.decode(prefs.getString("board", null)) ?: NativeSnakeEngine.newGame()) }
    var best by remember { mutableIntStateOf(prefs.getInt("best", 0)) }
    var paused by remember { mutableStateOf(false) }

    fun save() {
        prefs.edit().putString("board", NativeSnakeEngine.encode(state))
            .putInt("best", maxOf(best, state.score)).apply()
    }

    val owner = context as? LifecycleOwner
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                save()
                paused = true
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            save()
            owner?.lifecycle?.removeObserver(observer)
        }
    }

    LaunchedEffect(paused, state.over, state.victory) {
        if (paused || state.over || state.victory) return@LaunchedEffect
        while (true) {
            delay(NativeSnakeEngine.tickMillis(state.score))
            state = NativeSnakeEngine.tick(state)
        }
    }
    LaunchedEffect(state.ticks / 6, state.over, state.victory) {
        if (state.score > best) best = state.score
        save()
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF4F8F4))
            .navigationBarsPadding().padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = { save(); onExit() }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to games", tint = snakeInk)
            }
            Text("Snake", color = snakeInk, fontWeight = FontWeight.Bold, fontSize = 28.sp,
                modifier = Modifier.weight(1f))
            Button(
                onClick = { paused = !paused },
                enabled = !state.over && !state.victory,
                colors = ButtonDefaults.buttonColors(containerColor = snakeInk)
            ) {
                Text(if (paused) "Resume" else "Pause")
            }
        }
        Spacer(Modifier.height(15.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("SCORE  ${state.score}", color = snakeInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("BEST  ${maxOf(best, state.score)}", color = Color(0xFF547065),
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(13.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            // Swipe on the playing field, not over tiny separate directional buttons.
            Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                var ax = 0f
                var ay = 0f
                var sent = false
                detectDragGestures(
                    onDragStart = { ax = 0f; ay = 0f; sent = false },
                    onDrag = { change, drag ->
                        change.consume()
                        if (paused || state.over || state.victory || sent) return@detectDragGestures
                        ax += drag.x
                        ay += drag.y
                        if (abs(ax) >= 17.dp.toPx() || abs(ay) >= 17.dp.toPx()) {
                            sent = true
                            val turn = if (abs(ax) >= abs(ay)) {
                                if (ax > 0) SnakeHeading.RIGHT else SnakeHeading.LEFT
                            } else if (ay > 0) SnakeHeading.DOWN else SnakeHeading.UP
                            state = NativeSnakeEngine.requestTurn(state, turn)
                        }
                    }
                )
            }) {
                drawNativeSnake(state)
            }
            if (state.over || state.victory || paused) {
                Column(
                    Modifier.background(Color(0xEBF5FAF6),
                        androidx.compose.foundation.shape.RoundedCornerShape(15.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(when {
                        state.victory -> "Board cleared!"
                        state.over -> "Game over"
                        else -> "Paused"
                    }, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = snakeInk)
                    if (state.over || state.victory) {
                        Spacer(Modifier.height(12.dp))
                        Text("You scored ${state.score} points.", color = snakeInk)
                        Spacer(Modifier.height(15.dp))
                        Button(onClick = {
                            state = NativeSnakeEngine.newGame()
                            paused = false
                            save()
                        }, colors = ButtonDefaults.buttonColors(containerColor = snakeInk)) {
                            Text("Play again")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Swipe in any direction to turn.",
            fontSize = 13.sp, color = Color(0xFF5B766A),
            modifier = Modifier.padding(bottom = 16.dp))
    }
}

private fun DrawScope.drawNativeSnake(game: NativeSnakeState) {
    val cells = min(size.width / NativeSnakeEngine.columns,
        size.height / NativeSnakeEngine.rows)
    val left = (size.width - NativeSnakeEngine.columns * cells) / 2f
    val top = (size.height - NativeSnakeEngine.rows * cells) / 2f
    val w = NativeSnakeEngine.columns * cells
    val h = NativeSnakeEngine.rows * cells
    val corner = CornerRadius(cells * .65f)
    drawRoundRect(Color(0xFFE1EBDF),
        Offset(left - 3f, top - 3f), Size(w + 6f, h + 6f), corner)
    repeat(NativeSnakeEngine.rows) { y ->
        repeat(NativeSnakeEngine.columns) { x ->
            if ((x + y) % 2 == 0) {
                drawRect(Color(0xFFEDF4E8).copy(alpha = .75f),
                    Offset(left + x * cells, top + y * cells), Size(cells, cells))
            }
        }
    }
    val fruit = game.fruit
    val fx = left + (fruit.x + .5f) * cells
    val fy = top + (fruit.y + .5f) * cells
    drawCircle(Color(0xFFF6B09A).copy(alpha = .5f), cells * .44f, Offset(fx, fy))
    drawCircle(Color(0xFFE76B5B), cells * .31f, Offset(fx, fy))
    drawLine(Color(0xFF398B65), Offset(fx, fy - cells * .24f),
        Offset(fx + cells * .1f, fy - cells * .44f), cells * .09f)
    game.snake.asReversed().forEachIndexed { i, part ->
        val isHead = i == game.snake.lastIndex
        val x = left + part.x * cells
        val y = top + part.y * cells
        drawRoundRect(
            if (isHead) snakeDark else Brush.linearGradient(listOf(snakeBright, snakeDark)),
            Offset(x + cells * .065f, y + cells * .065f),
            Size(cells * .87f, cells * .87f), CornerRadius(cells * .27f)
        )
        if (isHead) {
            val h = game.heading
            val xx = x + cells * .5f
            val yy = y + cells * .5f
            val horizontal = h.dx != 0
            for (side in listOf(-1f, 1f)) {
                val dx = if (horizontal) h.dx * .17f else side * .16f
                val dy = if (horizontal) side * .16f else h.dy * .17f
                drawCircle(Color.White, cells * .077f,
                    Offset(xx + dx * cells, yy + dy * cells))
                drawCircle(Color(0xFF102B22), cells * .034f,
                    Offset(xx + dx * cells + h.dx * cells * .024f,
                        yy + dy * cells + h.dy * cells * .024f))
            }
        }
    }
}
