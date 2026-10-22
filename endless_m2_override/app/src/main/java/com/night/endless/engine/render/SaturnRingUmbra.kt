package com.night.endless.engine.render

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Solar-ray occlusion by Saturn's *tilted oblate* cloud top.
 *
 * The planet shader and ring renderer already use a volume-preserving oblate
 * spheroid. Treating the ring umbra as a circle produces a physically wrong
 * shadow at high solar elevations. This CPU reference mirrors the ring GLSL
 * ellipsoid closest-approach test. All coordinates use the same world units;
 * position is relative to Saturn's centre and toSun points toward the Sun.
 */
internal object SaturnRingUmbra {
    fun closestEllipsoidRadiusSquared(
        positionX: Double, positionY: Double, positionZ: Double,
        toSunX: Double, toSunY: Double, toSunZ: Double,
        polarAxisX: Double, polarAxisY: Double, polarAxisZ: Double,
        equatorialRadius: Double, polarRatio: Double
    ): Double {
        val inputs = doubleArrayOf(
            positionX, positionY, positionZ, toSunX, toSunY, toSunZ,
            polarAxisX, polarAxisY, polarAxisZ, equatorialRadius, polarRatio
        )
        if (inputs.any { !it.isFinite() } ||
            equatorialRadius <= 0.0 || polarRatio <= 0.0 || polarRatio > 1.0
        ) return Double.POSITIVE_INFINITY

        val sunLength = sqrt(toSunX * toSunX + toSunY * toSunY + toSunZ * toSunZ)
        val axisLength = sqrt(
            polarAxisX * polarAxisX + polarAxisY * polarAxisY +
                polarAxisZ * polarAxisZ
        )
        if (sunLength < 1e-12 || axisLength < 1e-12)
            return Double.POSITIVE_INFINITY

        val sx = toSunX / sunLength
        val sy = toSunY / sunLength
        val sz = toSunZ / sunLength
        val nx = polarAxisX / axisLength
        val ny = polarAxisY / axisLength
        val nz = polarAxisZ / axisLength
        val x = positionX / equatorialRadius
        val y = positionY / equatorialRadius
        val z = positionZ / equatorialRadius

        val polarStretch = 1.0 / (polarRatio * polarRatio) - 1.0
        val alongAxis = x * nx + y * ny + z * nz
        val sunAxis = sx * nx + sy * ny + sz * nz
        val a = 1.0 + polarStretch * sunAxis * sunAxis
        val b = x * sx + y * sy + z * sz + polarStretch * alongAxis * sunAxis
        // Only the FORWARD half-ray reaches sunlight. The near side cannot
        // acquire a fake shadow from an ellipsoid *behind* the ring particle.
        val t = max(0.0, -b / a)
        return x * x + y * y + z * z + polarStretch * alongAxis * alongAxis +
            2.0 * b * t + a * t * t
    }

    fun blocked(
        x: Double, y: Double, z: Double,
        sunX: Double, sunY: Double, sunZ: Double,
        axisX: Double = 0.0, axisY: Double = 1.0, axisZ: Double = 0.0,
        equatorialRadius: Double = 1.0,
        polarRatio: Double = PlanetShape.polarRatio("saturn").toDouble()
    ): Boolean = closestEllipsoidRadiusSquared(
        x, y, z, sunX, sunY, sunZ, axisX, axisY, axisZ,
        equatorialRadius, polarRatio
    ) < 1.0
}
