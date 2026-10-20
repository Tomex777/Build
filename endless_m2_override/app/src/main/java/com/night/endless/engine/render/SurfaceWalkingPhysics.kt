package com.night.endless.engine.render

import kotlin.math.hypot

/**
 * Meter-scale locomotion on planetary heightfields (one unit is 3km).
 *
 * Continuous collision considers the grade of each actual movement step,
 * not an arbitrary absolute height delta. A diagonal blocked by a ridge
 * may still slide sideways; a straight cliff must stop the player.
 * No physics randomness, stored frame timer or Android dependency.
 */
internal object SurfaceWalkingPhysics {
    // 35-degree ascent and approximately 42-degree descent.
    // Pixel-level terrain can change by a few centimetres between samples;
    // allow 2cm clearance without permitting metre-high cliff steps.
    private const val MAX_UP_GRADE = .700
    private const val MAX_DOWN_GRADE = .900
    private const val MICRO_TOLERANCE_UNITS = .0000067
    private const val BOUNDARY = 8.0

    fun move(
        x: Double, z: Double, dx: Double, dz: Double,
        terrain: (Double, Double) -> Double
    ): Pair<Double, Double> {
        if (!x.isFinite() || !z.isFinite() ||
            !dx.isFinite() || !dz.isFinite()) return x to z
        val initialHeight = terrain(x, z)
        if (!initialHeight.isFinite()) return x to z

        fun reachable(tryX: Double, tryZ: Double): Boolean {
            val travel = hypot(tryX-x, tryZ-z)
            if (travel <= 1e-12) return false
            val height = terrain(tryX, tryZ)
            if (!height.isFinite()) return false
            val change = height - initialHeight
            return change <= MAX_UP_GRADE * travel + MICRO_TOLERANCE_UNITS &&
                change >= -MAX_DOWN_GRADE * travel - MICRO_TOLERANCE_UNITS
        }

        val nextX = (x+dx).coerceIn(-BOUNDARY, BOUNDARY)
        val nextZ = (z+dz).coerceIn(-BOUNDARY, BOUNDARY)
        if (reachable(nextX, nextZ)) return nextX to nextZ

        // Project collision onto the surface axes; prefer the longer
        // useful slide, rather than unexpectedly moving uphill diagonally.
        val allowedX = reachable(nextX, z)
        val allowedZ = reachable(x, nextZ)
        return when {
            allowedX && allowedZ -> if (kotlin.math.abs(dx) >= kotlin.math.abs(dz))
                nextX to z else x to nextZ
            allowedX -> nextX to z
            allowedZ -> x to nextZ
            else -> x to z
        }
    }
}
