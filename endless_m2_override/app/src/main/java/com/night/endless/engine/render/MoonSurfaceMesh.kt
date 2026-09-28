package com.night.endless.engine.render

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

data class MoonSurfaceMeshData(
    val vertices: FloatArray,
    val indices: ShortArray,
    val side: Int,
    val step: Float
)

object MoonSurfaceMeshGenerator {
    const val DEFAULT_SIDE = 161
    const val DEFAULT_STEP = 0.17f
    const val STRIDE_FLOATS = 9

    fun build(side: Int = DEFAULT_SIDE, step: Float = DEFAULT_STEP): MoonSurfaceMeshData {
        require(side in 2..255) { "Moon terrain side must fit unsigned-short indices" }
        require(step.isFinite() && step > 0f) { "Moon terrain step must be finite and positive" }

        val data = FloatArray(side * side * STRIDE_FLOATS)
        var p = 0
        val half = (side - 1) / 2f
        for (zi in 0 until side) {
            val z = (zi - half) * step
            for (xi in 0 until side) {
                val x = (xi - half) * step
                val h = heightAt(x.toDouble(), z.toDouble()).toFloat()
                val nx = -(heightAt(x + 0.015, z.toDouble()) - heightAt(x - 0.015, z.toDouble())).toFloat()
                val nz = -(heightAt(x.toDouble(), z + 0.015) - heightAt(x.toDouble(), z - 0.015)).toFloat()
                val ny = 0.027f
                val length = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
                val dust = ((h + 0.36f) / 0.68f).coerceIn(0f, 1f)
                val mottling =
                    0.028f * sin((x * 6.7f + z * 1.9f).toDouble()).toFloat() +
                        0.018f * cos((z * 8.1f - x * 2.6f).toDouble()).toFloat()
                val base = (0.34f + dust * 0.18f + mottling).coerceIn(0.24f, 0.62f)

                data[p++] = x
                data[p++] = h
                data[p++] = z
                data[p++] = nx / length
                data[p++] = ny / length
                data[p++] = nz / length
                data[p++] = base * 0.96f
                data[p++] = base * 0.97f
                data[p++] = base
            }
        }

        val indexData = ShortArray((side - 1) * (side - 1) * 6)
        var i = 0
        for (z in 0 until side - 1) {
            for (x in 0 until side - 1) {
                val a = z * side + x
                val b = a + 1
                val c = a + side
                val d = c + 1
                indexData[i++] = a.toShort()
                indexData[i++] = c.toShort()
                indexData[i++] = b.toShort()
                indexData[i++] = b.toShort()
                indexData[i++] = c.toShort()
                indexData[i++] = d.toShort()
            }
        }

        return MoonSurfaceMeshData(data, indexData, side, step)
    }

    fun heightAt(x: Double, z: Double): Double {
        var h =
            0.042 * sin(x * 0.24) * cos(z * 0.19) +
                0.020 * sin(x * 1.42 + z * 0.36) * cos(z * 1.08) +
                0.010 * sin(x * 4.7 + z * 1.3) * cos(z * 4.1 - x * 0.8) +
                0.005 * sin(x * 9.2 - z * 6.4)
        h += crater(x, z, -2.8, 1.7, 1.52, 0.17, 0.086)
        h += crater(x, z, 2.3, -2.1, 0.92, 0.125, 0.068)
        h += crater(x, z, 1.95, -1.28, 0.54, 0.078, 0.046)
        h += crater(x, z, 0.72, -1.52, 0.29, 0.050, 0.028)
        h += crater(x, z, 3.9, 2.8, 0.58, 0.080, 0.044)
        h += crater(x, z, -0.4, -4.0, 0.44, 0.056, 0.033)
        h += crater(x, z, 0.7, 0.6, 0.33, 0.044, 0.026)
        return h
    }

    private fun crater(
        x: Double,
        z: Double,
        cx: Double,
        cz: Double,
        radius: Double,
        depth: Double,
        rim: Double
    ): Double {
        val dx = x - cx
        val dz = z - cz
        val d = sqrt(dx * dx + dz * dz)
        val bowl = -depth * exp(-(d * d) / (radius * radius * 0.58))
        val rimWidth = radius * 0.18
        val ring = rim * exp(-((d - radius) * (d - radius)) / (rimWidth * rimWidth))
        return bowl + ring
    }
}
