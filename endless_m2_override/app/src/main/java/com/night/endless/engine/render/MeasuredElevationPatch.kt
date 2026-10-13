package com.night.endless.engine.render

import android.content.Context
import java.io.DataInputStream
import kotlin.math.floor

/**
 * 161 x 161 georeferenced DEM samples packed as big-endian signed 16-bit values
 * (render units x 1000). Coordinates cover -13.6..+13.6 render units.
 *
 * Generated at build time from NASA MGS/MOLA and LRO/LOLA topography.
 * Source spatial resolution is ~2 km/pixel; interpolation does not add data.
 * Render units are 3 km horizontally and vertically. Minor small-scale
 * surface detail is illustrative; measured large-scale relief is preserved.
 */
class MeasuredElevationPatch private constructor(private val heights: ShortArray) {
    fun heightAt(x: Double, z: Double): Double? {
        val gx = x / STEP + CENTER
        val gz = z / STEP + CENTER
        if (!gx.isFinite() || !gz.isFinite() || gx < 0.0 || gz < 0.0 ||
            gx > SIDE - 1.0 || gz > SIDE - 1.0) return null
        val x0 = floor(gx).toInt().coerceIn(0, SIDE - 1)
        val z0 = floor(gz).toInt().coerceIn(0, SIDE - 1)
        val x1 = (x0 + 1).coerceAtMost(SIDE - 1)
        val z1 = (z0 + 1).coerceAtMost(SIDE - 1)
        val fx = gx - x0
        val fz = gz - z0
        fun value(ix: Int, iz: Int): Double = heights[iz * SIDE + ix].toDouble() / 1000.0
        val top = value(x0, z0) * (1.0 - fx) + value(x1, z0) * fx
        val bottom = value(x0, z1) * (1.0 - fx) + value(x1, z1) * fx
        return top * (1.0 - fz) + bottom * fz
    }

    companion object {
        const val SIDE = 161
        const val CENTER = 80.0
        const val STEP = 0.17
        fun load(context: Context, assetName: String): MeasuredElevationPatch? =
            try {
                context.assets.open(assetName).use { stream ->
                    DataInputStream(stream).use { data ->
                        val values = ShortArray(SIDE * SIDE) { data.readShort() }
                        check(data.read() == -1) { "Unexpected DEM tile length" }
                        MeasuredElevationPatch(values)
                    }
                }
            } catch (_: java.io.IOException) {
                // Explicitly fall back to the original synthetic test patch only
                // if an older APK did not package the DEM.
                null
            }
    }
}
