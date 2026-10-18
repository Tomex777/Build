package com.night.endless.engine.render

import kotlin.math.max
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

    private fun hash2(x: Int, z: Int): Double {
        // Geographically seeded, reproducible relief with no repeating
        // trigonometric ridges running diagonally across crater slopes.
        var h = (x * 374761393) xor (z * 668265263)
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)).toLong() and 0xffffffffL).toDouble() /
            4294967296.0
    }

    private fun coherentNoise(x: Double, z: Double): Double {
        val ix = kotlin.math.floor(x).toInt()
        val iz = kotlin.math.floor(z).toInt()
        val fx = x - ix
        val fz = z - iz
        val sx = fx * fx * (3.0 - 2.0 * fx)
        val sz = fz * fz * (3.0 - 2.0 * fz)
        val a = hash2(ix, iz) * (1.0 - sx) + hash2(ix + 1, iz) * sx
        val b = hash2(ix, iz + 1) * (1.0 - sx) + hash2(ix + 1, iz + 1) * sx
        return a * (1.0 - sz) + b * sz
    }

    private fun fineAmplitude(x: Double, z: Double): Double {
        // Two nonperiodic, band-limited geological scales (~250m and ~580m).
        // 21m vertices must never sample 30m periodic waves: that caused
        // repeating diagonal black/white stripes in actual Moon screenshots.
        // This illustrative residual is bounded by 0.93 metres vertically.
        val warpX = (coherentNoise(x * 5.4 + 19.4, z * 5.4 - 7.0) - .5) * .035
        val warpZ = (coherentNoise(x * 5.4 - 35.0, z * 5.4 + 21.2) - .5) * .035
        val broad = coherentNoise((x + warpX) * 5.2, (z + warpZ) * 5.2)
        val eroded = coherentNoise(
            (x + warpX) * 11.7 + 71.0, (z + warpZ) * 11.7 - 23.0
        )
        return (broad - .5) * .00032 + (eroded - .5) * .00030
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
