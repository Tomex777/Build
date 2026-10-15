package com.night.endless.engine.render

import kotlin.math.hypot

/** Pure, frame-rate independent GTA-style surface drive math. */
internal object SurfaceJoystickMath {
    private const val DEAD_ZONE = .085f
    private const val METERS_PER_SECOND = 3.0

    /**
     * Screen Y increases downward; engine forward is positive toward camera
     * heading. Normalization gives analogue speed and clamps diagonal travel.
     */
    fun fromDisplacement(dx: Float, dy: Float, radius: Float): Pair<Float, Float> {
        if (!dx.isFinite() || !dy.isFinite() || !radius.isFinite() || radius <= 0f)
            return 0f to 0f
        val magnitude = hypot(dx, dy) / radius
        if (magnitude <= DEAD_ZONE) return 0f to 0f
        val intensity = ((magnitude - DEAD_ZONE) / (1f - DEAD_ZONE)).coerceIn(0f, 1f)
        return -dy / hypot(dx, dy) * intensity to dx / hypot(dx, dy) * intensity
    }

    /** Engine units = 3000m. Delta is clamped to avoid jumps after resume. */
    fun travelWorldUnits(deltaSeconds: Double): Double =
        deltaSeconds.coerceIn(0.0, .05) * METERS_PER_SECOND / 3000.0
}
