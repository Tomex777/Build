package com.night.endless.engine.render

import kotlin.math.floor

/**
 * Exactly reproduces the triangle interpolation of the lower-resolution
 * Mars and Moon terrain grids. The camera, collision system, and high-detail
 * overlay must use THIS height, not raw DEM values between mesh vertices;
 * otherwise the camera can end up below the rendered terrain (brown/grey
 * full-screen landing regression).
 */
internal object SurfaceTerrainSampling {
    fun renderedHeight(
        x: Double,
        z: Double,
        side: Int,
        step: Double,
        raw: (Double, Double) -> Double
    ): Double {
        require(side >= 2 && step > 0.0)
        val half = (side - 1) / 2.0
        val gx = x / step + half
        val gz = z / step + half
        if (!gx.isFinite() || !gz.isFinite() ||
            gx < 0.0 || gz < 0.0 || gx > side - 1.0 || gz > side - 1.0) {
            return raw(x, z)
        }
        val ix = floor(gx).toInt().coerceIn(0, side - 2)
        val iz = floor(gz).toInt().coerceIn(0, side - 2)
        val fx = (gx - ix).coerceIn(0.0, 1.0)
        val fz = (gz - iz).coerceIn(0.0, 1.0)
        val x0 = (ix - half) * step
        val z0 = (iz - half) * step
        val h00 = raw(x0, z0)
        val h10 = raw(x0 + step, z0)
        val h01 = raw(x0, z0 + step)
        val h11 = raw(x0 + step, z0 + step)
        return if (fx + fz <= 1.0) {
            h00 * (1.0 - fx - fz) + h10 * fx + h01 * fz
        } else {
            h11 * (fx + fz - 1.0) + h10 * (1.0 - fz) + h01 * (1.0 - fx)
        }
    }
}
