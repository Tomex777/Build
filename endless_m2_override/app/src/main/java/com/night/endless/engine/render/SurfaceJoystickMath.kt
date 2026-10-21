package com.night.endless.engine.render

import kotlin.math.hypot

/**
 * Single geometry contract for both the Android touch target and the visual
 * thumbstick. All sizes are density-independent pixels; do not duplicate them
 * in the View or Compose overlay.
 */
internal object SurfaceJoystickMath {
    const val PAD_SIZE_DP = 148f
    const val PAD_START_DP = 18f
    const val PAD_BOTTOM_DP = 28f
    const val KNOB_TRAVEL_DP = 51f
    private const val TOUCH_RADIUS_DP = 86f
    private const val DEAD_ZONE = .085f
    private const val METERS_PER_SECOND = 3.0

    fun centerX(density: Float): Float = (PAD_START_DP + PAD_SIZE_DP / 2f) * density
    fun centerY(viewHeight: Int, density: Float): Float =
        viewHeight - (PAD_BOTTOM_DP + PAD_SIZE_DP / 2f) * density

    fun isInMoveZone(x: Float, y: Float, viewHeight: Int, density: Float): Boolean {
        if (!x.isFinite() || !y.isFinite() || !density.isFinite() || density <= 0f) return false
        return hypot(x - centerX(density), y - centerY(viewHeight, density)) <=
            TOUCH_RADIUS_DP * density
    }

    /** The left side isn't a camera touchpad. Look gestures start on the right. */
    fun isInLookZone(x: Float, viewportWidth: Int): Boolean =
        x.isFinite() && viewportWidth > 0 &&
            x >= viewportWidth * .5f && x <= viewportWidth

    /** Exact visual thumb displacement; no dead-zone or response curve. */
    fun knobOffset(dx: Float, dy: Float, radius: Float): Pair<Float, Float> {
        if (!dx.isFinite() || !dy.isFinite() || !radius.isFinite() || radius <= 0f)
            return 0f to 0f
        val length = hypot(dx, dy)
        if (length <= 0f) return 0f to 0f
        val scale = 1f / maxOf(length, radius)
        return dx * scale to dy * scale
    }

    /** Screen Y increases downward. Output is forward/strafe in [-1, 1]. */
    fun fromDisplacement(dx: Float, dy: Float, radius: Float): Pair<Float, Float> {
        if (!dx.isFinite() || !dy.isFinite() || !radius.isFinite() || radius <= 0f)
            return 0f to 0f
        val length = hypot(dx, dy)
        if (length <= radius * DEAD_ZONE) return 0f to 0f
        val intensity = ((length / radius - DEAD_ZONE) / (1f - DEAD_ZONE))
            .coerceIn(0f, 1f)
        return -dy / length * intensity to dx / length * intensity
    }

    /**
     * Use the real time between touch updates even when no GL frame completes.
     * Bound long background or GPU stalls to 450ms to avoid teleporting.
     * Per-step physics integration remains capped to 50ms below.
     */
    fun elapsedMovementSeconds(previousNanos: Long, nowNanos: Long): Double {
        if (nowNanos <= previousNanos) return 0.0
        return ((nowNanos - previousNanos).toDouble() / 1_000_000_000.0)
            .coerceIn(0.0, 0.45)
    }

    /** Engine units = 3000m. Delta is clamped to avoid jumps after resume. */
    fun travelWorldUnits(deltaSeconds: Double): Double =
        deltaSeconds.coerceIn(0.0, .05) * METERS_PER_SECOND / 3000.0
}
