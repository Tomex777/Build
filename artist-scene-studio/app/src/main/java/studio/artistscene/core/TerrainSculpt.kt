package studio.artistscene.core

import kotlin.math.sqrt

/** A brush operates in the terrain mesh's local X/Z meters, not screen pixels. */
enum class TerrainBrush { RAISE, LOWER, SMOOTH, FLATTEN }

/**
 * One undoable stroke. strength is meters for RAISE/LOWER, and an interpolation
 * fraction (0..1) for SMOOTH/FLATTEN. Target height is absolute local meters.
 */
data class TerrainStroke(
    val centerX: Float,
    val centerZ: Float,
    val radiusMeters: Float,
    val brush: TerrainBrush,
    val strength: Float,
    val targetHeightMeters: Float = 0f,
)

/**
 * Deterministic CPU terrain sculpting. Never mutates the input, so the caller
 * can keep old scene GLBs for genuine undo/redo rather than rewriting an asset
 * that may still be referenced by the history stack or a saved project.
 */
object TerrainSculpt {
    private const val MIN_HEIGHT = -100f
    private const val MAX_HEIGHT = 200f

    fun apply(heights: FloatArray, stroke: TerrainStroke): FloatArray {
        require(heights.size == TerrainMesh.VERTICES) { "Expected a 65×65 terrain heightfield" }
        require(heights.all { it.isFinite() && it in MIN_HEIGHT..MAX_HEIGHT }) {
            "Invalid source terrain elevations"
        }
        require(stroke.centerX.isFinite() && stroke.centerZ.isFinite() &&
            stroke.radiusMeters.isFinite() && stroke.radiusMeters in 0.25f..18f &&
            stroke.strength.isFinite() && stroke.strength in 0f..8f &&
            stroke.targetHeightMeters.isFinite() &&
            stroke.targetHeightMeters in MIN_HEIGHT..MAX_HEIGHT
        ) { "Invalid terrain brush settings" }
        val result = heights.copyOf()
        if (stroke.strength == 0f) return result

        val cells = TerrainMesh.CELLS
        val size = cells + 1
        val spacing = TerrainMesh.EDGE_METERS / cells
        val half = TerrainMesh.EDGE_METERS * 0.5f
        val radiusSq = stroke.radiusMeters * stroke.radiusMeters

        for (row in 0..cells) {
            val z = -half + row * spacing
            val dz = z - stroke.centerZ
            if (dz * dz >= radiusSq) continue
            for (col in 0..cells) {
                val x = -half + col * spacing
                val dx = x - stroke.centerX
                val distSq = dx * dx + dz * dz
                if (distSq >= radiusSq) continue
                // Compact C1-continuous falloff: 0 force and 0 slope at rim.
                val t = 1f - distSq / radiusSq
                val falloff = t * t
                val index = row * size + col
                val old = heights[index]
                val next = when (stroke.brush) {
                    TerrainBrush.RAISE -> old + stroke.strength * falloff
                    TerrainBrush.LOWER -> old - stroke.strength * falloff
                    TerrainBrush.FLATTEN -> old + (stroke.targetHeightMeters - old) *
                        stroke.strength.coerceAtMost(1f) * falloff
                    TerrainBrush.SMOOTH -> {
                        // Snapshot sampling prevents directional streaks and
                        // makes repeated strokes independent of loop order.
                        var weighted = 0f
                        var weightTotal = 0f
                        for (dRow in -1..1) for (dCol in -1..1) {
                            val r = (row + dRow).coerceIn(0, cells)
                            val c = (col + dCol).coerceIn(0, cells)
                            val w = (if (dRow == 0) 2f else 1f) *
                                (if (dCol == 0) 2f else 1f)
                            weighted += heights[r * size + c] * w
                            weightTotal += w
                        }
                        old + (weighted / weightTotal - old) *
                            stroke.strength.coerceAtMost(1f) * falloff
                    }
                }
                result[index] = next.coerceIn(MIN_HEIGHT, MAX_HEIGHT)
            }
        }
        return result
    }

    /** World X/Z to the nearest vertex, useful for brush targeting/preview. */
    fun nearestVertex(xMeters: Float, zMeters: Float): Pair<Int, Int> {
        require(xMeters.isFinite() && zMeters.isFinite())
        val half = TerrainMesh.EDGE_METERS / 2f
        val col = (((xMeters + half) / TerrainMesh.EDGE_METERS) *
            TerrainMesh.CELLS).toInt().coerceIn(0, TerrainMesh.CELLS)
        val row = (((zMeters + half) / TerrainMesh.EDGE_METERS) *
            TerrainMesh.CELLS).toInt().coerceIn(0, TerrainMesh.CELLS)
        return row to col
    }
}
