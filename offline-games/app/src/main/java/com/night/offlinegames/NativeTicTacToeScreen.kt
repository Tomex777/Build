package com.night.offlinegames

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ticInk = Color(0xFF203F32)
private val ticGreen = Color(0xFF287555)
private val ticGold = Color(0xFFD4A156)

@Composable
fun NativeTicTacToeScreen(onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("native_tictactoe", Context.MODE_PRIVATE) }
    var state by remember { mutableStateOf(NativeTicTacToe.decode(prefs.getString("match",null)) ?: NativeTicState()) }
    var draws by remember { mutableIntStateOf(prefs.getInt("draws",0)) }
    var defeats by remember { mutableIntStateOf(prefs.getInt("cpu_wins",0)) }

    fun play(index: Int) {
        val after = NativeTicTacToe.move(state,index)
        if (after == state) return
        if (state.result == 0 && after.result == 3) draws++
        if (state.result == 0 && after.result == 2) defeats++
        state = after
        prefs.edit().putString("match",NativeTicTacToe.encode(after))
            .putInt("draws",draws).putInt("cpu_wins",defeats).apply()
    }
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().background(Color(0xFFF7F9F5))
            .padding(horizontal=16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onExit,modifier=Modifier.size(44.dp)) {
                Icon(Icons.Outlined.ArrowBack,contentDescription="Back to games",tint=ticInk)
            }
            Text("Tic-Tac-Toe",color=ticInk,fontSize=26.sp,fontWeight=FontWeight.Bold)
        }
        Spacer(Modifier.height(19.dp))
        Text("YOU  X", color=ticGreen,fontWeight=FontWeight.Black,letterSpacing=1.sp,fontSize=17.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            when(state.result) {
                1 -> "You win!"
                2 -> "The computer wins"
                3 -> "Draw. Well played."
                else -> "Your move"
            },
            color=ticInk,fontSize=16.sp,fontWeight=FontWeight.Medium
        )
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().widthIn(max=430.dp).aspectRatio(1f),
            verticalArrangement=Arrangement.spacedBy(9.dp)) {
            repeat(3) { row ->
                Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                    repeat(3) { column ->
                        val index=row*3+column
                        val mark=state.squares[index]
                        Surface(
                            modifier=Modifier.weight(1f).aspectRatio(1f)
                                .semantics { contentDescription="Cell ${index+1}: " + when(mark) {
                                    1->"X";2->"O";else->"empty"
                                } }
                                .clickable(enabled = state.result==0 && mark==0) { play(index) },
                            color=if(mark==1) Color(0xFFE1F2E8) else if(mark==2) Color(0xFFF6E8D0) else Color.White,
                            shape=RoundedCornerShape(13.dp),
                            border=BorderStroke(1.dp,Color(0xFFD9E5DD))
                        ) { TicMark(mark) }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text("DRAWS",fontSize=10.sp,letterSpacing=1.3.sp,color=Color(0xFF69796F),fontWeight=FontWeight.Bold)
                Text("$draws",fontSize=22.sp,color=ticInk,fontWeight=FontWeight.Bold)
            }
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text("CPU WINS",fontSize=10.sp,letterSpacing=1.3.sp,color=Color(0xFF69796F),fontWeight=FontWeight.Bold)
                Text("$defeats",fontSize=22.sp,color=ticInk,fontWeight=FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick={
            state=NativeTicState()
            prefs.edit().putString("match",NativeTicTacToe.encode(state)).apply()
        },colors=ButtonDefaults.buttonColors(containerColor=ticInk),
            shape=RoundedCornerShape(11.dp)) { Text("New game") }
    }
}

@Composable
private fun TicMark(mark: Int) {
    val progress by animateFloatAsState(if(mark!=0) 1f else 0f,
        animationSpec=tween(durationMillis=200),label="piece placement")
    Canvas(Modifier.fillMaxSize().padding(20.dp)) {
        if(mark==1) {
            val start=Offset(size.width*.2f,size.height*.2f)
            val end=Offset(size.width*.8f,size.height*.8f)
            drawLine(ticGreen,start,start+(end-start)*progress,strokeWidth=6.dp.toPx())
            val start2=Offset(size.width*.8f,size.height*.2f)
            val end2=Offset(size.width*.2f,size.height*.8f)
            drawLine(ticGreen,start2,start2+(end2-start2)*progress,strokeWidth=6.dp.toPx())
        } else if(mark==2) {
            drawArc(ticGold,startAngle=0f,sweepAngle=360f*progress,
                useCenter=false,topLeft=Offset(size.width*.2f,size.height*.2f),
                size=androidx.compose.ui.geometry.Size(size.width*.6f,size.height*.6f),
                style=Stroke(width=6.dp.toPx()))
        }
    }
}
