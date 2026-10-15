package com.night.poetry

import kotlin.math.abs

/** Layout-space coordinates on the actual card, independent from preview zoom. */
data class SnapResult(
    val x: Float, val y: Float,
    val snappedX: Boolean, val snappedY: Boolean,
    val guideX: Float? = null, val guideY: Float? = null
)

/**
 * Align an object's start/centre/end to any canvas or sibling anchor. This code has no
 * Android dependency and is shared by drag gestures and small deterministic tests.
 */
fun snapPosition(x: Float, y: Float, width: Float, height: Float,
                 guideXs: List<Float> = listOf(0f, .5f, 1f),
                 guideYs: List<Float> = listOf(0f, .5f, 1f),
                 tolerance: Float = .018f): SnapResult {
    fun find(value: Float, span: Float, candidates: List<Float>): Pair<Float, Float?> {
        val myAnchors = listOf(value, value + span / 2f, value + span)
        var bestDist = Float.POSITIVE_INFINITY
        var newValue = value
        var guide: Float? = null
        for (anchor in myAnchors) for (target in candidates) {
            val dist = abs(target - anchor)
            if (dist <= tolerance && dist < bestDist) {
                bestDist = dist
                newValue = value + target - anchor
                guide = target
            }
        }
        return newValue to guide
    }
    val sx = find(x, width, guideXs)
    val sy = find(y, height, guideYs)
    return SnapResult(sx.first.coerceIn(0f, (1f - width).coerceAtLeast(0f)),
        sy.first.coerceIn(0f, (1f - height).coerceAtLeast(0f)),
        sx.second != null, sy.second != null, sx.second, sy.second)
}
