package com.night.offlinegames

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val mineInk = Color(0xFF203D35)
private val mineGreen = Color(0xFF2A7856)
private val mineGold = Color(0xFFE8A65D)
private val mineBlue = Color(0xFF386B8D)

/** Fully offline native Minesweeper: reveal, flood fill, flags, chording and saved boards. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NativeMinesScreen(onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("native_mines", Context.MODE_PRIVATE) }
    var state by remember {
        mutableStateOf(NativeMinesEngine.decode(prefs.getString("board", null)) ?: NativeMinesEngine.newGame())
    }
    var flagMode by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        prefs.edit().putString("board", NativeMinesEngine.encode(state)).apply()
    }

    fun tap(index: Int) {
        state = when {
            flagMode -> NativeMinesEngine.toggleFlag(state, index)
            index in state.revealed -> NativeMinesEngine.chord(state, index)
            else -> NativeMinesEngine.reveal(state, index)
        }
    }

    Column(
        Modifier.fillMaxSize().navigationBarsPadding().background(Color(0xFFF4F8F5))
            .padding(horizontal = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to games", tint = mineInk)
            }
            Text("Minesweeper", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = mineInk)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("SAFE ${state.revealed.count { it !in state.mines }} / 71",
                    fontSize = 17.sp, color = mineInk, fontWeight = FontWeight.Bold)
                Text("MINES LEFT ${NativeMinesEngine.mineCount - state.flags.size}",
                    fontSize = 12.sp, color = Color(0xFF668074), fontWeight = FontWeight.Medium)
            }
            OutlinedButton(onClick = { flagMode = !flagMode }, enabled = !state.lost && !state.won) {
                Text(if (flagMode) "Flag mode ON" else "Flag mode OFF")
            }
        }
        Spacer(Modifier.height(17.dp))
        Text(
            when {
                state.won -> "Cleared! Every safe square revealed."
                state.lost -> "Mine hit. Start a new board."
                !state.started -> "Choose a square to begin."
                flagMode -> "Tap a square to place or remove a flag."
                else -> "Reveal safe squares. Long-press to flag."
            },
            fontSize = 14.sp, color = if (state.lost) Color(0xFFA84C3D) else mineGreen
        )
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp).aspectRatio(1f),
            shape = RoundedCornerShape(13.dp),
            color = Color(0xFFDEEAE3)
        ) {
            Box(Modifier.fillMaxSize().padding(6.dp)) {
                Canvas(Modifier.fillMaxSize()) { drawNativeMineBoard(state) }
                Column(Modifier.fillMaxSize()) {
                    repeat(NativeMinesEngine.side) { row ->
                        Row(Modifier.fillMaxWidth().weight(1f)) {
                            repeat(NativeMinesEngine.side) { col ->
                                val index = row * NativeMinesEngine.side + col
                                val detail = when {
                                    index in state.flags -> "flagged"
                                    index in state.revealed && index in state.mines -> "mine"
                                    index in state.revealed -> "revealed ${NativeMinesEngine.adjacent(state, index)}"
                                    else -> "hidden"
                                }
                                Box(
                                    Modifier.weight(1f).fillMaxHeight()
                                        .combinedClickable(
                                            enabled = !state.won && !state.lost,
                                            onClick = { tap(index) },
                                            onLongClick = {
                                                state = NativeMinesEngine.toggleFlag(state, index)
                                            }
                                        )
                                        .semantics {
                                            contentDescription = "Mine cell ${row + 1}, ${col + 1}: $detail"
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { state = NativeMinesEngine.newGame(); flagMode = false },
            colors = ButtonDefaults.buttonColors(containerColor = mineInk),
            shape = RoundedCornerShape(11.dp)
        ) { Text("New game") }
        Spacer(Modifier.height(13.dp))
        Text("Tap to reveal. Long-press to flag. Tap a revealed number to clear its neighbors when flagged.",
            color = Color(0xFF6A8277), fontSize = 12.sp)
    }
}

private fun DrawScope.drawNativeMineBoard(board: NativeMinesState) {
    val cell = size.width / NativeMinesEngine.side
    val numbers = intArrayOf(
        0xFF1E5E9A.toInt(), 0xFF25815A.toInt(), 0xFFB5503D.toInt(),
        0xFF6557A6.toInt(), 0xFF8D523A.toInt(), 0xFF287F85.toInt(),
        0xFF555555.toInt(), 0xFF9D4D73.toInt()
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = cell * .49f
    }
    for (index in 0 until NativeMinesEngine.count) {
        val x = (index % NativeMinesEngine.side) * cell
        val y = (index / NativeMinesEngine.side) * cell
        val open = index in board.revealed
        val bomb = index in board.mines
        val flagged = index in board.flags
        val shownBomb = board.lost && bomb
        drawRoundRect(
            color = when {
                open && bomb -> Color(0xFFF4B5A3)
                open -> Color(0xFFF8FBF7)
                else -> Color(0xFFBBD6C8)
            },
            topLeft = Offset(x + 1.5f, y + 1.5f),
            size = Size(cell - 3f, cell - 3f),
            cornerRadius = CornerRadius(cell * .10f)
        )
        if (!open && !shownBomb) {
            drawRoundRect(Color.White.copy(alpha = .23f),
                Offset(x + cell * .10f, y + cell * .09f),
                Size(cell * .80f, cell * .10f), CornerRadius(cell * .05f))
        }
        val center = Offset(x + cell / 2f, y + cell / 2f)
        if (shownBomb || (open && bomb)) {
            drawCircle(Color(0xFF3E4851), cell * .22f, center)
            drawCircle(Color(0xFF79858B), cell * .07f,
                center + Offset(-cell * .08f, -cell * .08f))
        } else if (flagged) {
            drawLine(mineInk,
                Offset(center.x - cell * .10f, center.y - cell * .28f),
                Offset(center.x - cell * .10f, center.y + cell * .28f), cell * .065f)
            drawPath(Path().apply {
                moveTo(center.x - cell * .10f, center.y - cell * .27f)
                lineTo(center.x + cell * .24f, center.y - cell * .11f)
                lineTo(center.x - cell * .10f, center.y + cell * .045f)
                close()
            }, mineGold)
        } else if (open) {
            val number = NativeMinesEngine.adjacent(board, index)
            if (number > 0) {
                paint.color = numbers[number - 1]
                drawIntoCanvas {
                    it.nativeCanvas.drawText(number.toString(), center.x,
                        center.y - (paint.ascent() + paint.descent()) / 2f, paint)
                }
            }
        }
    }
}
