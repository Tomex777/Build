package com.night.endless.engine.scene

import com.night.endless.engine.math.Vec3d
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Consistent display-scale Moon trajectory and sampled orbit line.
 *
 * The live Moon previously used an eccentric, 5.145-degree inclined orbit,
 * while the orbit guide drawn through space was a flat perfect circle.
 * This helper preserves the existing illustrative lunar model and supplies
 * BOTH positions and guide geometry from the same approximation.
 *
 * Elements: e≈0.0549, inclination≈5.145° to the ecliptic, nodal regression
 * ≈0.0529539°/day. Sidereal period≈27.321661 days. This intentionally omits
 * solar/lunar perturbations and is NOT JPL Horizons or SPICE.
 */
internal object LunarOrbitApproximation {
    const val EPOCH_MILLIS = 946728000000L // J2000 noon UTC
    const val SIDEREAL_DAYS = 27.321661
    private const val ECCENTRICITY = .0549
    private const val INCLINATION_RAD = 5.145 * PI / 180.0
    private const val MILLIS_PER_DAY = 86400000.0

    fun relativePosition(utcMillis: Long, orbitScale: Double): Vec3d {
        require(orbitScale.isFinite() && orbitScale > 0.0)
        val elapsedDays = (utcMillis - EPOCH_MILLIS) / MILLIS_PER_DAY
        val mean = Math.toRadians(134.963) + 2.0 * PI * elapsedDays / SIDEREAL_DAYS
        val anomaly = KeplerEphemeris.solveEccentricAnomaly(mean % (2.0*PI), ECCENTRICITY)
        val x = orbitScale * (cos(anomaly) - ECCENTRICITY)
        val z = orbitScale * sqrt(1.0 - ECCENTRICITY*ECCENTRICITY) * sin(anomaly)
        val ascendingNode = Math.toRadians(125.045 - .0529539 * elapsedDays)
        return Vec3d(
            x*cos(ascendingNode) - z*sin(ascendingNode)*cos(INCLINATION_RAD),
            z*sin(INCLINATION_RAD),
            x*sin(ascendingNode) + z*cos(ascendingNode)*cos(INCLINATION_RAD)
        )
    }

    fun orbitPolyline(utcMillis: Long, orbitScale: Double, segments: Int): FloatArray {
        require(segments >= 8)
        val dayStep = SIDEREAL_DAYS * MILLIS_PER_DAY / segments
        return FloatArray(segments * 3).also { points ->
            for (index in 0 until segments) {
                val p = relativePosition(utcMillis + (index * dayStep).toLong(), orbitScale)
                points[index*3] = p.x.toFloat()
                points[index*3+1] = p.y.toFloat()
                points[index*3+2] = p.z.toFloat()
            }
        }
    }
}
