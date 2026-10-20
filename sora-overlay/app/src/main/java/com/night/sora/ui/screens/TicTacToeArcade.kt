package com.night.sora.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.ui.theme.*
import kotlinx.coroutines.delay

private val winLines = listOf(
    listOf(0,1,2),listOf(3,4,5),listOf(6,7,8),
    listOf(0,3,6),listOf(1,4,7),listOf(2,5,8),
    listOf(0,4,8),listOf(2,4,6)
)
private fun ticWinner(b: List<String>): String? = winLines.firstNotNullOfOrNull { line ->
    b[line[0]].takeIf { it.isNotEmpty() && it == b[line[1]] && it == b[line[2]] }
}
private fun computerPick(b: List<String>): Int? {
    val free = b.indices.filter { b[it].isBlank() }
    if (free.isEmpty()) return null
    for (piece in listOf("O","X")) for (at in free) {
        if (ticWinner(b.toMutableList().also { it[at] = piece }) == piece) return at
    }
    if (4 in free) return 4
    return listOf(0,2,6,8).filter { it in free }.randomOrNull() ?: free.random()
}

@Composable
fun TicTacToeArcade(modifier: Modifier = Modifier) {
    val prefs = LocalContext.current.getSharedPreferences("sora_arcade_v1",Context.MODE_PRIVATE)
    var board by remember { mutableStateOf(List(9) { "" }) }
    var yourTurn by remember { mutableStateOf(true) }
    var wins by remember { mutableIntStateOf(prefs.getInt("ttt_wins",0)) }
    val winner = ticWinner(board)
    val draw = winner == null && board.none { it.isBlank() }

    fun select(at: Int) {
        if (!yourTurn || winner != null || draw || board[at].isNotEmpty()) return
        board = board.toMutableList().also { it[at] = "X" }
        yourTurn = false
        if (ticWinner(board) == "X") {
            wins++
            prefs.edit().putInt("ttt_wins",wins).apply()
        }
    }

    LaunchedEffect(board,yourTurn) {
        if (!yourTurn && winner == null && !draw) {
            delay(300)
            val next = computerPick(board)
            if (next != null && board[next].isEmpty()) board = board.toMutableList().also { it[next] = "O" }
            yourTurn = true
        }
    }

    Column(modifier.padding(horizontal=18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text("YOU   X       COMPUTER   O",color=SoraAccent,fontSize=12.sp,fontWeight=FontWeight.Bold)
        Text("YOUR WINS   " + wins,fontSize=14.sp,modifier=Modifier.padding(vertical=18.dp))
        Column(Modifier.fillMaxWidth().aspectRatio(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            for (row in 0..2) {
                Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    for (col in 0..2) {
                        val at = row*3+col
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                                .background(SoraSurfaceHigh)
                                .clickable(enabled=yourTurn && winner==null && !draw && board[at].isBlank()) { select(at) },
                            contentAlignment=Alignment.Center
                        ) {
                            Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                                when(board[at]) {
                                    "X" -> {
                                        drawLine(SoraAccent,Offset(4f,4f),Offset(size.width-4f,size.height-4f),strokeWidth=9f,cap=StrokeCap.Round)
                                        drawLine(SoraAccent,Offset(size.width-4f,4f),Offset(4f,size.height-4f),strokeWidth=9f,cap=StrokeCap.Round)
                                    }
                                    "O" -> drawCircle(Color(0xFF84BBA3),radius=size.minDimension*.42f,style=Stroke(width=9f))
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(25.dp))
        Text(when {
            winner=="X" -> "You win!"
            winner=="O" -> "Computer wins"
            draw -> "It's a draw"
            yourTurn -> "Your turn"
            else -> "Computer is thinking…"
        },fontSize=20.sp,fontWeight=FontWeight.Bold)
        TextButton(onClick={ board=List(9){""};yourTurn=true },modifier=Modifier.padding(top=10.dp)) { Text("Play again") }
    }
}
