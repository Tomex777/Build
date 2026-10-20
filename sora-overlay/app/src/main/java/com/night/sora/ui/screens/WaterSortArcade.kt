package com.night.sora.ui.screens

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.night.sora.ui.theme.*
import kotlin.math.min
import kotlin.random.Random

private const val WATER_CAP = 4
private val waterColorNames = listOf(
    "Coral", "Blue", "Gold", "Lavender", "Green", "Rose", "Sand",
)
private val waterColors = listOf(
    Color(0xFFED796D), Color(0xFF63B5D7), Color(0xFFF5BD58),
    Color(0xFF9B87D6), Color(0xFF6FBF8C), Color(0xFFD88CB8),
    Color(0xFFD5A36B),
)

// Tubes are stored bottom-to-top. A scramble is made from the completed
// state using *reversible* moves: after each reverse move, its source remains
// empty or has the same top colour, so undoing that move is always legal.
// Every generated puzzle therefore has at least one valid solution.
private fun newWaterBoard(level: Int): List<List<Int>> {
    val colours = (3 + (level - 1) / 2).coerceIn(3, waterColors.size)
    val random = Random(level * 104729 + 4187)
    val tubes = MutableList(colours + 2) { i ->
        if (i < colours) MutableList(WATER_CAP) { i } else mutableListOf()
    }
    repeat(50 + level.coerceAtMost(20) * 9) {
        val possibilities = buildList {
            tubes.forEachIndexed { source, tube ->
                if (tube.isEmpty()) return@forEachIndexed
                val colour = tube.last()
                val run = tube.takeLastWhile { it == colour }.size
                // Taking the whole top group is reversible only when it
                // empties the source; otherwise leave one same-colour unit.
                val limit = if (run == tube.size) run else run - 1
                if (limit <= 0) return@forEachIndexed
                tubes.forEachIndexed { target, destination ->
                    if (target == source || destination.size == WATER_CAP) return@forEachIndexed
                    if (destination.lastOrNull() == colour) return@forEachIndexed
                    val transferable = min(limit, WATER_CAP - destination.size)
                    for (amount in 1..transferable) add(Triple(source, target, amount))
                }
            }
        }
        if (possibilities.isEmpty()) return@repeat
        val (source, target, count) = possibilities.random(random)
        val colour = tubes[source].last()
        repeat(count) { tubes[source].removeAt(tubes[source].lastIndex); tubes[target].add(colour) }
    }
    return tubes.map { it.toList() }
}
private fun parseWaterBoard(encoded: String?, level: Int): List<List<Int>>? {
    if (encoded == null) return null
    val colours = (3 + (level - 1) / 2).coerceIn(3, waterColors.size)
    val parsed = encoded.split(';').map { tube ->
        if (tube.isBlank()) emptyList() else tube.split(',').mapNotNull(String::toIntOrNull)
    }
    if (parsed.size != colours + 2 || parsed.any { it.size > WATER_CAP } ||
        parsed.flatten().any { it !in 0 until colours } ||
        (0 until colours).any { colour -> parsed.flatten().count { it == colour } != WATER_CAP }) return null
    return parsed
}
private fun isWaterSolved(tubes: List<List<Int>>): Boolean =
    tubes.all { it.isEmpty() || (it.size == WATER_CAP && it.all { colour -> colour == it[0] }) }

@Composable
fun WaterSortArcade(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sora_water_sort_v1", Context.MODE_PRIVATE) }
    var level by remember { mutableIntStateOf(prefs.getInt("level", 1).coerceIn(1, 40)) }
    var board by remember(level) {
        mutableStateOf(parseWaterBoard(prefs.getString("board_$level", null), level) ?: newWaterBoard(level))
    }
    var moves by remember(level) { mutableIntStateOf(prefs.getInt("moves_$level", 0).coerceAtLeast(0)) }
    var selected by remember(level) { mutableIntStateOf(-1) }
    var history by remember(level) { mutableStateOf<List<List<List<Int>>>>(emptyList()) }
    val won = isWaterSolved(board)

    fun save() {
        prefs.edit().putString("board_$level", board.joinToString(";") { it.joinToString(",") })
            .putInt("moves_$level", moves).apply()
    }
    fun choose(at: Int) {
        if (won) return
        if (selected == at) { selected = -1; return }
        if (selected == -1) {
            if (board[at].isNotEmpty()) selected = at
            return
        }
        val from = board[selected]
        val to = board[at]
        val colour = from.lastOrNull()
        if (colour == null || to.size == WATER_CAP || (to.isNotEmpty() && to.last() != colour)) {
            // A tap on another filled tube selects it; invalid pours cannot
            // silently change either tube or increment the move counter.
            selected = if (to.isNotEmpty()) at else selected
            return
        }
        val amount = min(from.takeLastWhile { it == colour }.size, WATER_CAP - to.size)
        history = (history + listOf(board)).takeLast(30)
        board = board.mapIndexed { i, tube ->
            when (i) {
                selected -> tube.dropLast(amount)
                at -> tube + List(amount) { colour }
                else -> tube
            }
        }
        moves++
        selected = -1
        save()
        if (isWaterSolved(board)) {
            val best = prefs.getInt("best_$level", 0)
            if (best == 0 || moves < best) prefs.edit().putInt("best_$level", moves).apply()
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("LEVEL $level", color = SoraAccent, fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("$moves MOVES", color = SoraText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        val best = prefs.getInt("best_$level", 0)
        if (best > 0) Text("Best: $best moves", color = SoraMuted, fontSize = 11.sp)

        board.chunked(3).forEachIndexed { rowIndex, row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEachIndexed { localIndex, tube ->
                    val index = rowIndex * 3 + localIndex
                    WaterTube(
                        tube = tube,
                        picked = index == selected,
                        number = index + 1,
                        onTap = { choose(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        if (won) {
            Text("All colours sorted!", color = SoraAccent, fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 14.dp))
            Button(
                onClick = { level = (level + 1).coerceAtMost(40); prefs.edit().putInt("level", level).apply() },
                modifier = Modifier.padding(top = 12.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Next level") }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    enabled = history.isNotEmpty(),
                    onClick = {
                        board = history.last()
                        history = history.dropLast(1)
                        moves = (moves - 1).coerceAtLeast(0)
                        selected = -1
                        save()
                    },
                ) { Text("Undo") }
                TextButton(
                    onClick = {
                        board = newWaterBoard(level)
                        moves = 0
                        history = emptyList()
                        selected = -1
                        save()
                    },
                ) { Text("Restart") }
            }
        }
    }
}

@Composable
private fun WaterTube(
    tube: List<Int>,
    picked: Boolean,
    number: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lift by animateFloatAsState(
        targetValue = if (picked) -10f else 0f,
        animationSpec = tween(180), label = "Lift selected tube",
    )
    val tubeDescription = if (tube.isEmpty()) {
        "Tube $number, empty"
    } else {
        "Tube $number, top to bottom: " +
            tube.asReversed().joinToString(", ") { waterColorNames[it] }
    }
    Box(
        modifier.height(174.dp).clip(RoundedCornerShape(16.dp))
            .background(if (picked) SoraSurfaceRaised else SoraSurfaceHigh)
            .clickable(onClick = onTap)
            .semantics {
                contentDescription = if (picked) "$tubeDescription, selected" else tubeDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(146.dp).offset(y = lift.dp)) {
            val left = size.width * .19f
            val right = size.width * .81f
            val top = size.height * .05f
            val bottom = size.height * .95f
            val innerX = left + 5f
            val innerW = right - left - 10f
            val height = bottom - top - 8f
            val section = height / WATER_CAP
            tube.forEachIndexed { i, colour ->
                val unitTop = bottom - (i + 1) * section
                drawRoundRect(
                    color = waterColors[colour], topLeft = androidx.compose.ui.geometry.Offset(innerX, unitTop),
                    size = androidx.compose.ui.geometry.Size(innerW, section + 1),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f),
                )
                drawLine(waterColors[colour].copy(alpha = .65f),
                    androidx.compose.ui.geometry.Offset(innerX + 2, unitTop + 1),
                    androidx.compose.ui.geometry.Offset(innerX + innerW - 2, unitTop + 1),
                    strokeWidth = 1.5f)
            }
            drawRoundRect(
                color = Color.White.copy(alpha = if (picked) .95f else .57f),
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(13f),
                style = Stroke(width = 4f),
            )
            drawLine(Color.White.copy(alpha = .17f),
                androidx.compose.ui.geometry.Offset(left + 11f, top + 10f),
                androidx.compose.ui.geometry.Offset(left + 11f, bottom - 11f),
                strokeWidth = 4f, cap = StrokeCap.Round)
        }
    }
}
