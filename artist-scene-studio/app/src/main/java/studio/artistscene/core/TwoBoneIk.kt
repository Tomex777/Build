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
        // A camera-plane projection can temporarily collapse a limb, or deliver invalid points
        // during a viewport transition. Never write non-finite rotations into a real rig.
        if (!root.isFinite() || !mid.isFinite() || !end.isFinite() || !target.isFinite()) return null
        val upperLength = distance(root, mid)
        val lowerLength = distance(mid, end)
        if (!upperLength.isFinite() || !lowerLength.isFinite() ||
            upperLength <= EPSILON || lowerLength <= EPSILON) return null

        val currentUpper = angle(root, mid)
        val currentLower = angle(mid, end)
        val currentRelative = normalizeRadians(currentLower - currentUpper)
        val bendSign = if (abs(currentRelative) <= STRAIGHT_EPSILON) 1f else if (currentRelative > 0f) 1f else -1f

        val targetDx = target.x - root.x
        val targetDy = target.y - root.y
        val requestedDistance = hypot(targetDx, targetDy)
        if (!requestedDistance.isFinite()) return null
        val maxReach = (upperLength + lowerLength).coerceAtLeast(EPSILON)
        val minReach = (abs(upperLength - lowerLength) + REACH_EPSILON).coerceAtMost(maxReach)
        val distance = requestedDistance.coerceIn(minReach, maxReach)
        val targetHeading = if (requestedDistance > EPSILON) {
            atan2(targetDy, targetDx)
        } else {
            currentUpper
        }

        // Compute the law-of-cosines terms as Doubles: Float-square overflow on large but
        // finite world/screen projections otherwise produces NaN joints (even after clamping).
        val upper = upperLength.toDouble()
        val lower = lowerLength.toDouble()
        val reach = distance.toDouble()
        val rootOffsetCos = (
            (upper * upper + reach * reach - lower * lower) / (2.0 * upper * reach)
            ).coerceIn(-1.0, 1.0)
        val rootOffset = acos(rootOffsetCos).toFloat()

        val elbowInteriorCos = (
            (upper * upper + lower * lower - reach * reach) / (2.0 * upper * lower)
            ).coerceIn(-1.0, 1.0)
        val elbowInterior = acos(elbowInteriorCos).toFloat()

        val desiredUpper = targetHeading - bendSign * rootOffset
        val desiredRelative = bendSign * (PI.toFloat() - elbowInterior)

        return TwoBoneIkSolution(
            rootDeltaDegrees = radiansToNormalizedDegrees(desiredUpper - currentUpper),
            midDeltaDegrees = radiansToNormalizedDegrees(desiredRelative - currentRelative),
        )
    }

    private fun IkPoint.isFinite(): Boolean = x.isFinite() && y.isFinite()

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
