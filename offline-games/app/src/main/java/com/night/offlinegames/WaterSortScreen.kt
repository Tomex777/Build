package com.night.offlinegames

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private val waterBackground = Color(0xFFF5F8F6)
private val waterInk = Color(0xFF173C35)
private val waterPalette = listOf(
    Color(0xFFED7C65), Color(0xFF66B6D5), Color(0xFFE9BD54),
    Color(0xFF9C83DE), Color(0xFF6BC39F)
)
private data class PourSequence(
    val from: Int, val into: Int, val colour: Int, val units: Int,
    val previous: WaterSortState, val result: WaterSortState
)

@Composable
fun WaterSortScreen(onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("native_water_sort", Context.MODE_PRIVATE) }
    var state by remember { mutableStateOf(WaterSortEngine.decode(prefs.getString("board", null)) ?: WaterSortEngine.newGame()) }
    var selected by remember { mutableIntStateOf(-1) }
    var previous by remember { mutableStateOf<WaterSortState?>(null) }
    var pouring by remember { mutableStateOf<PourSequence?>(null) }
    val progress = remember { Animatable(0f) }

    fun save(next: WaterSortState) {
        state = next
        prefs.edit().putString("board", WaterSortEngine.encode(next)).apply()
    }
    fun tap(index: Int) {
        if (pouring != null || state.solved) return
        if (selected < 0) {
            if (state.tubes[index].isNotEmpty()) selected = index
        } else if (selected == index) {
            selected = -1
        } else {
            val next = WaterSortEngine.pour(state, selected, index)
            if (next != null) {
                val colour = state.tubes[selected].last()
                val units = next.tubes[index].size - state.tubes[index].size
                pouring = PourSequence(selected, index, colour, units, state, next)
                selected = -1
            } else {
                selected = if (state.tubes[index].isNotEmpty()) index else -1
            }
        }
    }

    LaunchedEffect(pouring) {
        val active = pouring ?: return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 440, easing = FastOutSlowInEasing))
        previous = active.previous
        save(active.result)
        pouring = null
        progress.snapTo(0f)
    }

    Column(
        Modifier.fillMaxSize().background(waterBackground).navigationBarsPadding()
            .padding(horizontal = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to games", tint = waterInk)
            }
            Text("Water Sort Puzzle", color = waterInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WaterStat("LEVEL", "${state.level}", Modifier.weight(1f))
            WaterStat("POURS", "${state.moves}", Modifier.weight(1f))
            WaterStat("COMPLETE", "${state.tubes.count { it.size == 4 && it.distinct().size == 1 }}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            when {
                state.solved -> "Perfectly sorted. Ready for the next level?"
                pouring != null -> "Pouring…"
                selected >= 0 -> "Choose a matching colour or an empty tube"
                else -> "Tap a filled tube, then choose where to pour"
            },
            color = Color(0xFF58786C), fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Spacer(Modifier.height(17.dp))

        // Each tube is a genuine native Canvas with touch hit testing; no WebView.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            val count = state.tubes.size
            repeat((count + 3) / 4) { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    repeat(4) { column ->
                        val index = row * 4 + column
                        if (index < count) {
                            val tilt by animateFloatAsState(
                                targetValue = if (pouring?.from == index) -11f else 0f,
                                animationSpec = tween(220),
                                label = "water tube tilt"
                            )
                            val lift by animateFloatAsState(
                                targetValue = if (selected == index) -10f else 0f,
                                animationSpec = tween(190),
                                label = "selected tube lift"
                            )
                            WaterTube(
                                layers = state.tubes[index],
                                highlighted = selected == index,
                                removing = if (pouring?.from == index) pouring!!.units * progress.value else 0f,
                                adding = if (pouring?.into == index) pouring!!.units * progress.value else 0f,
                                addingColour = if (pouring?.into == index) pouring!!.colour else null,
                                modifier = Modifier.weight(1f)
                                    .graphicsLayer(rotationZ = tilt, translationY = lift)
                                    .clickable(enabled = pouring == null) { tap(index) }
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.solved) {
                Button(
                    onClick = {
                        previous = null
                        save(WaterSortEngine.newGame(state.level + 1))
                        selected = -1
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = waterInk)
                ) { Text("Next level") }
            } else {
                OutlinedButton(
                    onClick = {
                        previous?.let { save(it) }
                        previous = null
                        selected = -1
                    },
                    enabled = previous != null && pouring == null
                ) { Text("Undo") }
            }
            OutlinedButton(
                onClick = {
                    previous = null
                    selected = -1
                    save(WaterSortEngine.newGame(state.level))
                },
                enabled = pouring == null
            ) { Text("Restart") }
        }
        Spacer(Modifier.height(18.dp))
        Text("Only matching top colours can be poured together.", fontSize = 12.sp, color = Color(0xFF71857A))
    }
}

@Composable
private fun WaterStat(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(11.dp),
        color = Color.White, border = BorderStroke(1.dp, Color(0xFFDCE8E2))) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color(0xFF708176))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 23.sp, color = waterInk)
        }
    }
}

@Composable
private fun WaterTube(
    layers: List<Int>,
    highlighted: Boolean,
    removing: Float,
    adding: Float,
    addingColour: Int?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.height(171.dp)) {
        val glassLeft = size.width * .13f
        val glassWidth = size.width * .74f
        val inset = 5.dp.toPx()
        val bottom = size.height - 8.dp.toPx()
        val slot = (size.height - 28.dp.toPx()) / 4f
        var y = bottom
        var removeRemaining = removing.coerceAtLeast(0f)
        val visible = layers.map { colour -> colour to 1f }.toMutableList()
        for (i in visible.indices.reversed()) {
            val cut = min(1f, removeRemaining)
            visible[i] = visible[i].first to (1f - cut)
            removeRemaining -= cut
        }
        val portions = visible + if (adding > 0f && addingColour != null) {
            listOf(addingColour to adding)
        } else emptyList()
        portions.forEach { (colour, quantity) ->
            val h = slot * quantity
            if (h > 0f) {
                y -= h
                drawRoundRect(
                    waterPalette[colour], topLeft = Offset(glassLeft + inset, y),
                    size = Size(glassWidth - inset * 2, h + .6f),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                drawLine(
                    Color.White.copy(alpha = .30f),
                    Offset(glassLeft + inset + 2.dp.toPx(), y + 2.dp.toPx()),
                    Offset(glassLeft + inset + 2.dp.toPx(), y + min(h, slot) - 2.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        val strokeColour = if (highlighted) Color(0xFF316B5A) else Color(0xFF9CBDB2)
        val start = Offset(glassLeft, 8.dp.toPx())
        val endY = bottom + 5.dp.toPx()
        drawLine(strokeColour, start, Offset(glassLeft, endY - 10.dp.toPx()), 2.5.dp.toPx())
        drawLine(strokeColour, Offset(glassLeft + glassWidth, start.y),
            Offset(glassLeft + glassWidth, endY - 10.dp.toPx()), 2.5.dp.toPx())
        drawArc(strokeColour, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(glassLeft, endY - 22.dp.toPx()),
            size = Size(glassWidth, 22.dp.toPx()), style = Stroke(width = 2.5.dp.toPx()))
        drawLine(Color.White.copy(alpha = .8f),
            Offset(glassLeft + 5.dp.toPx(), start.y + 15.dp.toPx()),
            Offset(glassLeft + 5.dp.toPx(), endY - 27.dp.toPx()), 1.2.dp.toPx())
    }
}
