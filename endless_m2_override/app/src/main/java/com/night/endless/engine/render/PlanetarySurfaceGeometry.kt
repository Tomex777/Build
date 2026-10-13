package com.night.endless.engine.render

import kotlin.math.sqrt

/**
 * Analytic consistency checks for GPU tangent-plane planetary curvature.
 *
 * Planetary geometry uses 1 world unit = 3 km locally. GPU applies the
 * second-order sagitta −distance²/(2R); at 80 km extent the next-order error
 * is below 0.3 m on Mars and below 1.8 m on the Moon.
 */
object PlanetarySurfaceGeometry {
    const val KILOMETERS_PER_WORLD_UNIT = 3.0
    const val MARS_RADIUS_KM = 3389.5
    const val MOON_RADIUS_KM = 1737.4

    fun radiusUnits(radiusKm: Double) = radiusKm / KILOMETERS_PER_WORLD_UNIT

    fun approximateDropUnits(distanceUnits: Double, radiusKm: Double): Double {
        require(radiusKm > 0.0 && distanceUnits >= 0.0)
        return distanceUnits * distanceUnits / (2.0 * radiusUnits(radiusKm))
    }

    fun horizonDistanceKm(radiusKm: Double, eyeHeightMeters: Double): Double {
        require(radiusKm > 0.0 && eyeHeightMeters >= 0.0)
        val heightKm = eyeHeightMeters / 1000.0
        return sqrt(2.0 * radiusKm * heightKm + heightKm * heightKm)
    }
}
