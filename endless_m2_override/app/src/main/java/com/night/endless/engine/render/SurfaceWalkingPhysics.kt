package com.night.endless.engine.render

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Meter-scale walking on each body's rendered heightfield (one unit = 3km).
 *
 * A destination-only grade check is unsafe: a slow frame or an injected gesture
 * can cross an entire narrow ridge and land at the same height on the far side.
 * Sweep the displacement through <=30cm segments, using the same terrain for
 * foot placement and collision. Allow sliding parallel to an obstruction.
 *
 * Normal live joystick frames already move <=15cm per physics step, so the
 * regular touch path incurs no additional sampling or input latency.
 */
internal object SurfaceWalkingPhysics {
    private const val MAX_UP_GRADE = .700
    private const val MAX_DOWN_GRADE = .900
    private const val MICRO_TOLERANCE_UNITS = .0000067
    private const val BOUNDARY = 8.0
    private const val MAX_SWEEP_STEP_UNITS = .00010 // 30cm
    private const val MAX_SWEEP_SAMPLES = 96

    fun move(
        x: Double, z: Double, dx: Double, dz: Double,
        terrain: (Double, Double) -> Double
    ): Pair<Double, Double> {
        if (!x.isFinite() || !z.isFinite() ||
            !dx.isFinite() || !dz.isFinite()) return x to z
        var altitude = terrain(x, z)
        if (!altitude.isFinite()) return x to z

        val goalX = (x + dx).coerceIn(-BOUNDARY, BOUNDARY)
        val goalZ = (z + dz).coerceIn(-BOUNDARY, BOUNDARY)
        val wholeDistance = hypot(goalX - x, goalZ - z)
        if (wholeDistance <= 1e-12 || !wholeDistance.isFinite()) return x to z
        val count = min(MAX_SWEEP_SAMPLES, max(1, ceil(wholeDistance /
            MAX_SWEEP_STEP_UNITS).toInt()))
        val stepX = (goalX - x) / count
        val stepZ = (goalZ - z) / count

        var currentX = x
        var currentZ = z
        repeat(count) {
            fun isReachable(nextX: Double, nextZ: Double): Boolean {
                val travel = hypot(nextX - currentX, nextZ - currentZ)
                if (travel <= 1e-12) return false
                val elevation = terrain(nextX, nextZ)
                if (!elevation.isFinite()) return false
                val change = elevation - altitude
                return change <= MAX_UP_GRADE * travel + MICRO_TOLERANCE_UNITS &&
                    change >= -MAX_DOWN_GRADE * travel - MICRO_TOLERANCE_UNITS
            }

            val candidateX = currentX + stepX
            val candidateZ = currentZ + stepZ
            val next = if (isReachable(candidateX, candidateZ)) {
                candidateX to candidateZ
            } else {
                // Attempt a wall slide: a ridge should block uphill progress,
                // not movement along the parallel axis.
                val alongX = isReachable(candidateX, currentZ)
                val alongZ = isReachable(currentX, candidateZ)
                when {
                    alongX && alongZ -> if (abs(stepX) >= abs(stepZ))
                        candidateX to currentZ else currentX to candidateZ
                    alongX -> candidateX to currentZ
                    alongZ -> currentX to candidateZ
                    else -> currentX to currentZ
                }
            }
            if (next.first != currentX || next.second != currentZ) {
                currentX = next.first
                currentZ = next.second
                altitude = terrain(currentX, currentZ)
            }
        }
        // Avoid accumulated floating point rounding placing the walker
        // outside the traversable heightfield.
        return currentX.coerceIn(-BOUNDARY, BOUNDARY) to
            currentZ.coerceIn(-BOUNDARY, BOUNDARY)
    }
}
