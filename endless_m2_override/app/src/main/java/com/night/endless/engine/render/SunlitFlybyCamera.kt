package com.night.endless.engine.render

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Framing helper for first close inspections of giant-planet clouds/rings.
 * Bright cloud bands are seen by moving the CAMERA toward the real Sun,
 * not by inventing light on the unilluminated hemisphere.
 * This is a camera-composition choice, not a simulated spacecraft orbit.
 */
internal object SunlitFlybyCamera {
    private const val LEADING_PHASE_RADIANS = .29 // ~17° oblique sunlight

    fun sunFacingYaw(bodyX: Double, bodyZ: Double): Double? {
        if (!bodyX.isFinite() || !bodyZ.isFinite() ||
            hypot(bodyX, bodyZ) <= 1e-9) return null
        // Camera offset from the body points *toward* the Sun at origin.
        return atan2(-bodyZ, -bodyX) + LEADING_PHASE_RADIANS
    }

    fun shortestTurn(current: Double, target: Double): Double {
        if (!current.isFinite() || !target.isFinite()) return 0.0
        val twoPi = 2.0 * PI
        return (target - current + PI).mod(twoPi) - PI
    }

    /** A targeted giant's camera should see a significantly sunlit globe. */
    fun illuminatedFractionEstimate(bodyX: Double, bodyZ: Double, cameraYaw: Double): Double {
        val separation = hypot(bodyX, bodyZ)
        if (!separation.isFinite() || separation < 1e-9 || !cameraYaw.isFinite()) return 0.0
        return ((-bodyX * cos(cameraYaw) - bodyZ * sin(cameraYaw)) /
            separation).coerceIn(-1.0, 1.0)
    }
}
