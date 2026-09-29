package studio.artistscene.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/** 2D point used by the viewport-facing two-bone IK solver. */
data class IkPoint(val x: Float, val y: Float)

/**
 * Rotation deltas for a two-bone chain. The root delta rotates the whole upper segment while the
 * mid delta changes the child segment relative to its parent.
 */
data class TwoBoneIkSolution(
    val rootDeltaDegrees: Float,
    val midDeltaDegrees: Float,
)

/**
 * Solves a two-bone chain in the viewport plane from its current projected joint positions.
 *
 * This deliberately returns deltas from the current pose instead of absolute imported-bone
 * rotations. Imported rigs have different rest orientations; applying deltas lets Mise keep the
 * real glTF rest pose as the source of truth while still giving artists a predictable wrist/ankle
 * drag in the camera plane.
 */
object TwoBoneIk {
    fun solve(
        root: IkPoint,
        mid: IkPoint,
        end: IkPoint,
        target: IkPoint,
    ): TwoBoneIkSolution? {
        val upperLength = distance(root, mid)
        val lowerLength = distance(mid, end)
        if (upperLength <= EPSILON || lowerLength <= EPSILON) return null

        val currentUpper = angle(root, mid)
        val currentLower = angle(mid, end)
        val currentRelative = normalizeRadians(currentLower - currentUpper)
        val bendSign = if (abs(currentRelative) <= STRAIGHT_EPSILON) 1f else if (currentRelative > 0f) 1f else -1f

        val targetDx = target.x - root.x
        val targetDy = target.y - root.y
        val requestedDistance = hypot(targetDx, targetDy)
        val maxReach = (upperLength + lowerLength - REACH_EPSILON).coerceAtLeast(EPSILON)
        val minReach = (abs(upperLength - lowerLength) + REACH_EPSILON).coerceAtMost(maxReach)
        val distance = requestedDistance.coerceIn(minReach, maxReach)
        val targetHeading = if (requestedDistance > EPSILON) {
            atan2(targetDy, targetDx)
        } else {
            currentUpper
        }

        val rootOffsetCos = (
            (upperLength * upperLength + distance * distance - lowerLength * lowerLength) /
                (2f * upperLength * distance)
            ).coerceIn(-1f, 1f)
        val rootOffset = acos(rootOffsetCos)

        val elbowInteriorCos = (
            (upperLength * upperLength + lowerLength * lowerLength - distance * distance) /
                (2f * upperLength * lowerLength)
            ).coerceIn(-1f, 1f)
        val elbowInterior = acos(elbowInteriorCos)

        val desiredUpper = targetHeading - bendSign * rootOffset
        val desiredRelative = bendSign * (PI.toFloat() - elbowInterior)

        return TwoBoneIkSolution(
            rootDeltaDegrees = radiansToNormalizedDegrees(desiredUpper - currentUpper),
            midDeltaDegrees = radiansToNormalizedDegrees(desiredRelative - currentRelative),
        )
    }

    private fun distance(a: IkPoint, b: IkPoint): Float = hypot(b.x - a.x, b.y - a.y)

    private fun angle(a: IkPoint, b: IkPoint): Float = atan2(b.y - a.y, b.x - a.x)

    private fun normalizeRadians(value: Float): Float {
        var result = value
        val pi = PI.toFloat()
        val twoPi = pi * 2f
        while (result > pi) result -= twoPi
        while (result <= -pi) result += twoPi
        return result
    }

    private fun radiansToNormalizedDegrees(radians: Float): Float {
        var value = Math.toDegrees(radians.toDouble()).toFloat() % 360f
        if (value > 180f) value -= 360f
        if (value <= -180f) value += 360f
        return value
    }

    private const val EPSILON = 0.0001f
    private const val REACH_EPSILON = 0.001f
    private const val STRAIGHT_EPSILON = 0.001f
}
