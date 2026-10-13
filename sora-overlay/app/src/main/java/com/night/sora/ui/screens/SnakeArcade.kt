package com.night.sora.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

private data class SnakePoint(val x: Int, val y: Int)
private const val GRID = 18
private fun randomFood(body: List<SnakePoint>): SnakePoint? {
    val free = buildList {
        for (y in 0 until GRID) for (x in 0 until GRID) {
            val p = SnakePoint(x, y)
            if (p !in body) add(p)
        }
    }
    return free.randomOrNull()
}

@Composable
fun SnakeArcade(modifier: Modifier = Modifier) {
    val prefs = LocalContext.current.getSharedPreferences("sora_arcade_v1", Context.MODE_PRIVATE)
    val initial = remember { listOf(SnakePoint(6,9), SnakePoint(5,9), SnakePoint(4,9)) }
    var snake by remember { mutableStateOf(initial) }
    var food by remember { mutableStateOf(randomFood(initial)) }
    var direction by remember { mutableStateOf(SnakePoint(1,0)) }
    var turn by remember { mutableStateOf(direction) }
    var running by remember { mutableStateOf(false) }
    var alive by remember { mutableStateOf(true) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(prefs.getInt("snake_best",0)) }

    fun reset() {
        snake = initial; food = randomFood(initial)
        direction = SnakePoint(1,0); turn = direction
        score = 0; alive = true; running = true
    }

    LaunchedEffect(running, alive) {
        while (isActive && running && alive) {
            delay((180L - score * 2L).coerceAtLeast(85L))
            val moving = turn.takeUnless { it.x == -direction.x && it.y == -direction.y } ?: direction
            direction = moving
            val head = SnakePoint(snake.first().x + moving.x, snake.first().y + moving.y)
            val grows = head == food
            if (head.x !in 0 until GRID || head.y !in 0 until GRID ||
                head in (if (grows) snake else snake.dropLast(1))) {
                alive = false; running = false
            } else {
                snake = (listOf(head) + snake).take(snake.size + if (grows) 1 else 0)
                if (grows) {
                    score++
                    if (score > best) {
                        best = score
                        prefs.edit().putInt("snake_best",best).apply()
                    }
                    food = randomFood(snake)
                    if (food == null) { alive = false; running = false }
                }
            }
        }
    }

    Column(modifier.padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SCORE  " + score, color = SoraAccent, fontWeight = FontWeight.Bold)
            Text("BEST  " + best, fontWeight = FontWeight.Bold)
        }
        var gesture by remember { mutableStateOf(Offset.Zero) }
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                .background(SoraSurfaceHigh)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { gesture = Offset.Zero },
                        onDragEnd = {
                            if (maxOf(abs(gesture.x),abs(gesture.y)) >= 13f) {
                                val next = if (abs(gesture.x) > abs(gesture.y)) {
                                    SnakePoint(if (gesture.x > 0) 1 else -1, 0)
                                } else SnakePoint(0,if (gesture.y > 0) 1 else -1)
                                if (!(next.x == -direction.x && next.y == -direction.y)) turn = next
                            }
                            gesture = Offset.Zero
                        },
                        onDragCancel = { gesture = Offset.Zero },
                        onDrag = { change, distance -> change.consume(); gesture += distance }
                    )
                }
        ) {
            val tile = size.width / GRID
            for (n in 1 until GRID) {
                val v = n * tile
                drawLine(SoraSurfaceRaised, Offset(v,0f), Offset(v,size.height), strokeWidth=.5f)
                drawLine(SoraSurfaceRaised, Offset(0f,v), Offset(size.width,v), strokeWidth=.5f)
            }
            food?.let {
                drawCircle(SoraDanger, radius=tile*.38f, center=Offset((it.x+.5f)*tile,(it.y+.5f)*tile))
            }
            snake.forEachIndexed { index, p ->
                drawRoundRect(
                    color=if (index==0) SoraAccent else Color(0xFF84BBA3),
                    topLeft=Offset(p.x*tile+tile*.1f,p.y*tile+tile*.1f),
                    size=Size(tile*.8f,tile*.8f),cornerRadius=CornerRadius(tile*.22f)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(when { !alive -> "Game over — score " + score
            running -> "Swipe anywhere on the board to turn"
            else -> "Touch-first Snake. No buttons or D-pad." },
            color=SoraMuted,textAlign=TextAlign.Center,fontSize=13.sp)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { if (!alive || score==0 && !running) reset() else running = !running },
            shape=RoundedCornerShape(12.dp)) {
            Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null)
            Text(when { !alive -> "Play again"; running -> "Pause"; score==0 -> "Start"; else -> "Resume" })
        }
    }
}
