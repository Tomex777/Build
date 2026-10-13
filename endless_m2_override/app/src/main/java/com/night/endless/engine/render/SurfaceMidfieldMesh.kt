package com.night.endless.engine.render

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A middle-detail geometry ring between NASA MOLA/LOLA global DEM
 * (~500-700m triangles) and the existing ~1m walking patch.
 *
 * Geographic geometry is intentionally grounded in the triangulated
 * spacecraft DEM. Fine surface relief is illustrative and fades out
 * both toward the eye-level patch and toward the coarse outer ring.
 *
 * 161x161 vertices at 21m intervals, extending 1.68km each direction.
 * The mesh stays fixed until the observer walks 85m, so we do not
 * allocate and triangulate 26k vertices every footstep.
 */
internal object SurfaceMidfieldMesh {
    const val SIDE = 161
    const val STEP = 0.007f
    const val STRIDE = 9
    const val EXTENT = (SIDE - 1) * STEP / 2f

    private fun fineAmplitude(x: Double, z: Double): Double {
        // <1.7m detail relief, not claimed spacecraft measurement.
        // The shortest meaningful wavelength stays above 110 metres:
        // 21m vertices must not be fed sub-Nyquist 30m sine waves.
        val ripples = sin(x * 138.0 + sin(z * 41.0) * .31) *
            cos(z * 94.0 + sin(x * 38.0) * .7)
        val low = sin(x * 49.0 + z * 21.0) *
            cos(z * 67.0 - x * 16.0)
        return 0.00023 * ripples + 0.00019 * low
    }

    fun build(
        centerX: Double, centerZ: Double,
        coarse: (Double, Double) -> Double
    ): SurfaceDetailMesh.Mesh {
        val out = FloatArray(SIDE * SIDE * STRIDE)
        val heights = FloatArray(SIDE * SIDE)
        val half = (SIDE - 1) / 2f
        val full = EXTENT.toDouble()
        for (z in 0 until SIDE) for (x in 0 until SIDE) {
            val localX = (x - half) * STEP
            val localZ = (z - half) * STEP
            val r = max(kotlin.math.abs(localX), kotlin.math.abs(localZ)).toDouble()
            // Leave the near-field replacement grounded at the *same*
            // baseline DEM triangle, avoiding a lip at the join.
            val nearFade = ((r - .028) / .070).coerceIn(0.0, 1.0)
            val outerFade = ((full - r) / .090).coerceIn(0.0, 1.0)
            val weight = nearFade * outerFade
            val wx = centerX + localX
            val wz = centerZ + localZ
            heights[z * SIDE + x] = (
                coarse(wx, wz) + fineAmplitude(wx, wz) * weight
            ).toFloat()
        }

        var i = 0
        for (z in 0 until SIDE) for (x in 0 until SIDE) {
            val wx = centerX + (x - half) * STEP
            val wz = centerZ + (z - half) * STEP
            val x0 = (x - 1).coerceAtLeast(0)
            val x1 = (x + 1).coerceAtMost(SIDE - 1)
            val z0 = (z - 1).coerceAtLeast(0)
            val z1 = (z + 1).coerceAtMost(SIDE - 1)
            val dx = (heights[z*SIDE+x1] - heights[z*SIDE+x0]) /
                ((x1-x0)*STEP).toDouble()
            val dz = (heights[z1*SIDE+x] - heights[z0*SIDE+x]) /
                ((z1-z0)*STEP).toDouble()
            val inv = 1.0 / sqrt(1.0 + dx*dx + dz*dz)
            out[i++] = wx.toFloat()
            out[i++] = heights[z*SIDE+x]
            out[i++] = wz.toFloat()
            out[i++] = (-dx * inv).toFloat()
            out[i++] = inv.toFloat()
            out[i++] = (-dz * inv).toFloat()
            out[i++] = .48f
            out[i++] = .47f
            out[i++] = .46f
        }

        val tris = ShortArray((SIDE - 1) * (SIDE - 1) * 6)
        var at = 0
        for (z in 0 until SIDE - 1) for (x in 0 until SIDE - 1) {
            val a = z * SIDE + x
            val b = a+1
            val c = a+SIDE
            val d = c+1
            tris[at++] = a.toShort()
            tris[at++] = c.toShort()
            tris[at++] = b.toShort()
            tris[at++] = b.toShort()
            tris[at++] = c.toShort()
            tris[at++] = d.toShort()
        }
        return SurfaceDetailMesh.Mesh(out, tris)
    }
}
