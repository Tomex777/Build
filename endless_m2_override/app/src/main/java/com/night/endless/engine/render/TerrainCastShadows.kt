package com.night.endless.engine.render

import kotlin.math.hypot
import kotlin.math.min

/**
 * Bakes directional terrain self-occlusion from the same triangles we draw.
 * No per-fragment raymarching, extra depth passes or shadow-map VRAM.
 * This is local, finite-patch sunlight shadowing, not a global horizon map.
 *
 * Input vertices use [position, normal, color] with nine floats per vertex.
 * The otherwise unused terrain color.r carries visibility to the GLES shader.
 * Rocks/decal vertices are never passed through this function.
 */
internal object TerrainCastShadows {
    private const val STRIDE = 9
    private const val SAMPLES = 14
    private const val CELL_SPACING = 1.6

    fun bake(
        vertices: FloatArray,
        side: Int,
        step: Float,
        sunX: Double,
        sunY: Double,
        sunZ: Double,
        edgeFadeCells: Int = 9
    ) {
        require(side >= 3 && step > 0f && step.isFinite())
        require(vertices.size == side * side * STRIDE)
        require(edgeFadeCells > 0)
        val horizontal = hypot(sunX, sunZ)
        if (!sunX.isFinite() || !sunY.isFinite() || !sunZ.isFinite() ||
            horizontal < 1e-9 || sunY <= 0.0) {
            for (index in 0 until side * side) vertices[index * STRIDE + 6] = 1f
            return
        }
        val rayX = sunX / horizontal
        val rayZ = sunZ / horizontal
        val risePerCell = sunY / horizontal * step.toDouble()

        fun heightAt(gridX: Double, gridZ: Double): Double {
            val x = gridX.toInt().coerceIn(0, side - 2)
            val z = gridZ.toInt().coerceIn(0, side - 2)
            val fx = (gridX - x).coerceIn(0.0, 1.0)
            val fz = (gridZ - z).coerceIn(0.0, 1.0)
            val p = (z * side + x) * STRIDE + 1
            val a = vertices[p].toDouble()
            val b = vertices[p + STRIDE].toDouble()
            val c = vertices[p + side * STRIDE].toDouble()
            val d = vertices[p + (side + 1) * STRIDE].toDouble()
            // Both triangle halves match SurfaceDetailMesh's a,c,b / b,c,d.
            return if (fx + fz <= 1.0) a * (1.0 - fx - fz) + b * fx + c * fz
                else d * (fx + fz - 1.0) + b * (1.0 - fz) + c * (1.0 - fx)
        }

        for (z in 0 until side) for (x in 0 until side) {
            val index = (z * side + x) * STRIDE
            val edge = min(min(x, z), min(side - 1 - x, side - 1 - z))
            if (edge == 0) {
                vertices[index + 6] = 1f
                continue
            }
            val origin = vertices[index + 1].toDouble()
            var visibility = 1.0
            for (sample in 1..SAMPLES) {
                val cells = sample * CELL_SPACING
                val px = x + rayX * cells
                val pz = z + rayZ * cells
                if (px < 0.0 || pz < 0.0 || px >= side - 1 || pz >= side - 1) break
                val distance = cells * step
                val blockedBy = heightAt(px, pz) - (origin + risePerCell * cells)
                // Solar disk and unresolved heightfield edges create a subtle
                // penumbra rather than hard staircase bands in 1m triangles.
                val penumbra = 0.00001 + distance * 0.004
                val lit = ((penumbra - blockedBy) / (2.0 * penumbra)).coerceIn(0.0, 1.0)
                visibility = min(visibility, lit)
                if (visibility == 0.0) break
            }
            // The cropped patch cannot know about terrain outside its bounds.
            // Fade shadow coverage to full sunlight at its edge to avoid a
            // dark square at the near/mid/coarse terrain transition.
            val t = (edge.toDouble() / edgeFadeCells).coerceIn(0.0, 1.0)
            val edgeWeight = t * t * (3.0 - 2.0 * t)
            vertices[index + 6] = (1.0 - (1.0 - visibility) * 0.90 * edgeWeight).toFloat()
        }
    }
}
