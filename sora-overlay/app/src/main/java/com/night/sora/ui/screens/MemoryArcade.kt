package com.night.sora.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.ui.theme.*
import kotlinx.coroutines.delay

private val cards = listOf(
    Icons.Rounded.AutoStories, Icons.Rounded.Headphones, Icons.Rounded.RocketLaunch,
    Icons.Rounded.SportsEsports, Icons.Rounded.Diamond, Icons.Rounded.Star,
    Icons.Rounded.Bolt, Icons.Rounded.Favorite,
)
private fun dealCards() = (0..7).flatMap { listOf(it,it) }.shuffled()

@Composable
fun MemoryArcade(modifier: Modifier = Modifier) {
    val prefs = LocalContext.current.getSharedPreferences("sora_arcade_v1",Context.MODE_PRIVATE)
    var board by remember { mutableStateOf(dealCards()) }
    var shown by remember { mutableStateOf<List<Int>>(emptyList()) }
    var completed by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var moves by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(prefs.getInt("memory_best",0)) }
    val solved = completed.size == 16

    LaunchedEffect(shown) {
        if (shown.size == 2) {
            if (board[shown[0]] == board[shown[1]]) {
                completed = completed + shown
                shown = emptyList()
            } else {
                delay(800)
                shown = emptyList()
            }
        }
    }
    LaunchedEffect(solved) {
        if (solved && (best==0 || moves<best)) {
            best=moves
            prefs.edit().putInt("memory_best",best).apply()
        }
    }

    Column(modifier.padding(horizontal=18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(bottom=17.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("MOVES  "+moves,color=SoraAccent,fontWeight=FontWeight.Bold)
            Text("BEST  "+if(best==0)"—" else best.toString(),fontWeight=FontWeight.Bold)
        }
        Column(Modifier.fillMaxWidth().aspectRatio(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            for(row in 0..3) {
                Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    for(col in 0..3) {
                        val index = row*4+col
                        val revealed = index in completed || index in shown
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                                .background(if(revealed) SoraSurfaceRaised else SoraSurfaceHigh)
                                .clickable(enabled = !revealed && shown.size<2 && !solved) {
                                    shown = shown + index
                                    if(shown.size==2) moves++
                                },
                            contentAlignment=Alignment.Center
                        ) {
                            if(revealed) Icon(cards[board[index]],"Revealed",tint=if(index in completed) Color(0xFF84BBA3) else SoraAccent,
                                modifier=Modifier.fillMaxSize(.55f))
                            else Icon(Icons.Rounded.QuestionMark,"Hidden",tint=SoraFaint,modifier=Modifier.fillMaxSize(.35f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(if(solved) "All eight pairs found in "+moves+" moves!" else "Tap two cards and match the vector icons.",
            color=SoraMuted,textAlign=TextAlign.Center,fontSize=13.sp)
        TextButton(onClick={
            board=dealCards();shown=emptyList();completed=emptySet();moves=0
        },modifier=Modifier.padding(top=10.dp)) { Text(if(solved)"Play again" else "Restart") }
    }
}
