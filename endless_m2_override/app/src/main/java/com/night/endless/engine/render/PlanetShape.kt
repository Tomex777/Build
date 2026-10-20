package com.night.endless.engine.render

/**
 * Polar/equatorial radius ratios of the four giant planets. Their apparent
 * flattening is visible from orbit, particularly on Jupiter and Saturn.
 *
 * The app's body.radius is a presentation-scale mean radius. Preserve its
 * volume-equivalent radius by stretching x/z by ratio^(-1/3) and compressing
 * y by ratio^(2/3); do not change physical orbital distances or collisions.
 * NASA Planetary Fact Sheet equatorial/polar radii, in kilometres.
 */
internal object PlanetShape {
    fun polarRatio(id: String): Float = when (id) {
        "jupiter" -> (66854.0 / 71492.0).toFloat()
        "saturn" -> (54364.0 / 60268.0).toFloat()
        "uranus" -> (24973.0 / 25559.0).toFloat()
        "neptune" -> (24341.0 / 24764.0).toFloat()
        else -> 1f
    }

    fun equatorialScale(id: String): Float =
        (1.0 / Math.cbrt(polarRatio(id).toDouble())).toFloat()

    fun polarScale(id: String): Float = equatorialScale(id) * polarRatio(id)

    /**
     * True ray/oblate spheroid intersection in the same local coordinates
     * as buildBodyModel: a rotation around the world's Z axis followed by
     * equatorial/polar scaling. Returns the nearest positive ray distance.
     * Uses the input direction's norm (does not require an extra allocation).
     */
    fun rayHitDistance(
        id: String, origin: com.night.endless.engine.math.Vec3d,
        direction: com.night.endless.engine.math.Vec3d,
        meanRadius: Double, tiltDegrees: Double
    ): Double? {
        if (!meanRadius.isFinite() || meanRadius <= 0.0 ||
            !tiltDegrees.isFinite()) return null
        val tilt = Math.toRadians(tiltDegrees)
        val co = kotlin.math.cos(tilt)
        val si = kotlin.math.sin(tilt)
        // Inverse of the model's axial tilt: rotate world ray into
        // the un-tilted spheroid's object frame.
        val ox = co * origin.x + si * origin.y
        val oy = -si * origin.x + co * origin.y
        val oz = origin.z
        val dx = co * direction.x + si * direction.y
        val dy = -si * direction.x + co * direction.y
        val dz = direction.z
        val equator = meanRadius * equatorialScale(id)
        val pole = meanRadius * polarScale(id)
        val invEq2 = 1.0 / (equator * equator)
        val invPole2 = 1.0 / (pole * pole)
        val a = (dx * dx + dz * dz) * invEq2 + dy * dy * invPole2
        val b = (ox * dx + oz * dz) * invEq2 + oy * dy * invPole2
        val c = (ox * ox + oz * oz) * invEq2 + oy * oy * invPole2 - 1.0
        val discriminant = b * b - a * c
        if (a <= 0.0 || !discriminant.isFinite() || discriminant < 0.0)
            return null
        val root = kotlin.math.sqrt(discriminant)
        val near = (-b - root) / a
        val far = (-b + root) / a
        return when {
            near > 1e-8 -> near
            far > 1e-8 -> far
            else -> null
        }
    }
}
