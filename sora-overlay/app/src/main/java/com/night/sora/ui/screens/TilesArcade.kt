package com.night.sora.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.ui.theme.*
import kotlin.math.abs
import kotlin.random.Random

private data class SlideResult(val values: List<Int>, val points: Int)

private fun slideLine(values: List<Int>): Pair<List<Int>,Int> {
    val numbers = values.filter { it != 0 }
    val output = mutableListOf<Int>()
    var points = 0
    var n = 0
    while (n < numbers.size) {
        if (n+1 < numbers.size && numbers[n] == numbers[n+1]) {
            val both = numbers[n] * 2
            output.add(both); points += both; n += 2
        } else { output.add(numbers[n]); n++ }
    }
    return (output + List(4-output.size) { 0 }) to points
}

private fun slideBoard(cells: List<Int>, dx: Int, dy: Int): SlideResult {
    val out = cells.toMutableList()
    var points = 0
    for (line in 0..3) {
        val indices = (0..3).map { i ->
            val pos = if (dx+dy > 0) 3-i else i
            if (dx != 0) line*4+pos else pos*4+line
        }
        val (values,gained) = slideLine(indices.map { cells[it] })
        indices.forEachIndexed { index, position -> out[position] = values[index] }
        points += gained
    }
    return SlideResult(out,points)
}

private fun spawnTile(cells: List<Int>): List<Int> {
    val holes = cells.indices.filter { cells[it] == 0 }
    if (holes.isEmpty()) return cells
    val next = cells.toMutableList()
    next[holes.random()] = if (Random.nextInt(10)==0) 4 else 2
    return next
}
private fun freshBoard(): List<Int> = spawnTile(spawnTile(List(16){0}))

@Composable
fun TilesArcade(modifier: Modifier = Modifier) {
    val prefs = LocalContext.current.getSharedPreferences("sora_arcade_v1",Context.MODE_PRIVATE)
    var tiles by remember { mutableStateOf(freshBoard()) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(prefs.getInt("tiles_best",0)) }
    val won = tiles.any { it >= 2048 }
    val stuck = 0 !in tiles && listOf(1 to 0,-1 to 0,0 to 1,0 to -1)
        .all { (x,y) -> slideBoard(tiles,x,y).values == tiles }

    fun swipe(x: Int,y: Int) {
        val next = slideBoard(tiles,x,y)
        if (next.values == tiles) return
        tiles = spawnTile(next.values)
        score += next.points
        if (score > best) { best=score; prefs.edit().putInt("tiles_best",best).apply() }
    }

    Column(modifier.padding(horizontal=18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(bottom=13.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("SCORE  " + score,color=SoraAccent,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            Text("BEST  " + best,fontWeight=FontWeight.Bold)
            TextButton(onClick={tiles=freshBoard();score=0}) { Text("New") }
        }
        var dragging by remember { mutableStateOf(Offset.Zero) }
        Column(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                .background(SoraSurfaceRaised).padding(6.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart={dragging=Offset.Zero},
                        onDragEnd={
                            if(maxOf(abs(dragging.x),abs(dragging.y))>20f) {
                                if(abs(dragging.x)>abs(dragging.y)) swipe(if(dragging.x>0)1 else -1,0)
                                else swipe(0,if(dragging.y>0)1 else -1)
                            }
                            dragging=Offset.Zero
                        },
                        onDragCancel={dragging=Offset.Zero},
                        onDrag={change,move->change.consume();dragging+=move}
                    )
                },
            verticalArrangement=Arrangement.spacedBy(6.dp)
        ) {
            for(row in 0..3) {
                Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    for(col in 0..3) {
                        val value = tiles[row*4+col]
                        val shade = when(value) {
                            0->SoraSurfaceHigh
                            2,4->Color(0xFF554632)
                            8,16->Color(0xFF8C5A33)
                            32,64->Color(0xFFB16A32)
                            128,256->Color(0xFFC58C3C)
                            512,1024->Color(0xFFDEAA4D)
                            else->SoraAccent
                        }
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(shade),
                            contentAlignment=Alignment.Center) {
                            if(value!=0) Text(value.toString(),color=if(value>=2048)SoraAccentInk else SoraText,
                                fontSize=when{value>=1024->20.sp;value>=128->24.sp;else->30.sp},
                                fontWeight=FontWeight.Black)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(when{
            stuck->"No moves left — start a new board."
            won->"2048 achieved! Keep going."
            else->"Swipe on the board to merge matching tiles."
        },color=SoraMuted,textAlign=TextAlign.Center,fontSize=13.sp)
    }
}
