package studio.artistscene.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import studio.artistscene.core.TerrainBrush
import studio.artistscene.core.TerrainMesh
import studio.artistscene.core.TerrainSculpt
import studio.artistscene.core.TerrainStroke

/**
 * A touch brush controller for an existing REAL terrain heightfield. This is a
 * top-down edit view, not a decorative pseudo-3D image: each sampled stroke
 * modifies the actual GLB vertices, and the host commits once per finger lift.
 * Keeping the gesture transactional prevents disk writes on every move.
 */
@Composable
internal fun TerrainBrushControls(
    heights: FloatArray,
    busy: Boolean,
    onCommit: (List<TerrainStroke>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var brush by remember { mutableStateOf(TerrainBrush.RAISE) }
    var radius by remember { mutableStateOf(3f) }
    var strength by remember { mutableStateOf(0.75f) }
    var preview by remember(heights) { mutableStateOf(heights.copyOf()) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    // Stroke samples are kept out of Compose state: only preview/cursor redraw.
    val samples = remember(heights) { mutableListOf<TerrainStroke>() }
    val fine = brush == TerrainBrush.SMOOTH || brush == TerrainBrush.FLATTEN
    val effectiveStrength = if (fine) strength.coerceIn(0f, 1f) else strength

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Terrain sculpt", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            TerrainBrush.entries.forEach { option ->
                FilterChip(
                    selected = option == brush,
                    onClick = { brush = option },
                    label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 10.sp) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).testTag("terrain-brush-${option.name.lowercase()}"),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Radius", modifier = Modifier.weight(0.65f), fontSize = 12.sp)
            listOf(1.5f, 3f, 6f).forEach { meters ->
                FilterChip(
                    selected = radius == meters,
                    onClick = { radius = meters },
                    label = { Text("${meters}m", fontSize = 11.sp) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).testTag("terrain-radius-${meters.toInt()}"),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(if (fine) "Blend" else "Depth", modifier = Modifier.weight(0.65f), fontSize = 12.sp)
            listOf(0.3f, 0.75f, 1.5f).forEach { amount ->
                FilterChip(
                    selected = strength == amount,
                    onClick = { strength = amount },
                    label = { Text(if (fine) "${(amount.coerceAtMost(1f) * 100).toInt()}%"
                        else "${amount}m", fontSize = 11.sp) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).testTag("terrain-strength-${amount}"),
                )
            }
        }
        Canvas(
            modifier = Modifier
                .size(240.dp)
                .fillMaxWidth()
                .testTag("terrain-sculpt-canvas")
                .pointerInput(heights, brush, radius, effectiveStrength, busy) {
                    if (busy) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        samples.clear()
                        var last: Offset? = null
                        var flattenHeight = 0f
                        fun enqueue(point: Offset) {
                            if (samples.size >= 1024) return
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val x = (point.x / w).coerceIn(0f, 1f) *
                                TerrainMesh.EDGE_METERS - TerrainMesh.EDGE_METERS / 2f
                            val z = (point.y / h).coerceIn(0f, 1f) *
                                TerrainMesh.EDGE_METERS - TerrainMesh.EDGE_METERS / 2f
                            if (samples.isEmpty()) {
                                val (row, col) = TerrainSculpt.nearestVertex(x, z)
                                flattenHeight = preview[row * (TerrainMesh.CELLS + 1) + col]
                            }
                            val stroke = TerrainStroke(x, z, radius, brush, effectiveStrength, flattenHeight)
                            samples.add(stroke)
                            preview = TerrainSculpt.apply(preview, stroke)
                            cursor = point
                        }
                        enqueue(down.position)
                        last = down.position
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.position != last) {
                                enqueue(change.position)
                                last = change.position
                            }
                            if (!change.pressed) break
                            change.consume()
                        }
                        if (samples.isNotEmpty()) onCommit(samples.toList())
                        samples.clear()
                        cursor = null
                    }
                },
        ) {
            val cellWidth = size.width / 32f
            val cellHeight = size.height / 32f
            val max = preview.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            for (row in 0 until 32) for (col in 0 until 32) {
                val height = preview[(row * 2) * (TerrainMesh.CELLS + 1) + col * 2]
                val normalized = (height / max).coerceIn(0f, 1f)
                drawRect(
                    color = Color(
                        red = 0.18f + 0.42f * normalized,
                        green = 0.28f + 0.41f * normalized,
                        blue = 0.22f + 0.23f * normalized,
                    ),
                    topLeft = Offset(col * cellWidth, row * cellHeight),
                    size = androidx.compose.ui.geometry.Size(cellWidth + 0.3f, cellHeight + 0.3f),
                )
            }
            cursor?.let { at ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = radius / TerrainMesh.EDGE_METERS * size.width,
                    center = at,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        Text(
            if (busy) "Updating 3D mesh…" else "Drag on the top view. Lift to update the 3D terrain.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
