package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A solver's angles must actually put the chain's wrist/ankle at the dragged target. */
class TwoBoneIkForwardAccuracyTest {
    private fun applySolution(
        root: IkPoint,
        mid: IkPoint,
        end: IkPoint,
        solution: TwoBoneIkSolution,
    ): IkPoint {
        val upper = hypot(mid.x - root.x, mid.y - root.y)
        val lower = hypot(end.x - mid.x, end.y - mid.y)
        val upperHeading = atan2(mid.y - root.y, mid.x - root.x) +
            Math.toRadians(solution.rootDeltaDegrees.toDouble()).toFloat()
        val lowerHeading = atan2(end.y - mid.y, end.x - mid.x) +
            Math.toRadians((solution.rootDeltaDegrees + solution.midDeltaDegrees).toDouble()).toFloat()
        return IkPoint(
            x = root.x + upper * cos(upperHeading) + lower * cos(lowerHeading),
            y = root.y + upper * sin(upperHeading) + lower * sin(lowerHeading),
        )
    }

    @Test
    fun projectedDragReachesTargetsInAllQuadrantsWithoutOverextendingLimb() {
        val root = IkPoint(0f, 0f)
        val mid = IkPoint(1f, 0f)
        val end = IkPoint(2f, 0f)
        val points = listOf(
            IkPoint(1f, 1f), IkPoint(-1f, 1f),
            IkPoint(-1f, -1f), IkPoint(1f, -1f),
            IkPoint(0.2f, 0.3f), IkPoint(-0.4f, 1.7f),
            IkPoint(0f, 1.999f), IkPoint(1.5f, 0.15f),
        )
        for (target in points) {
            val result = requireNotNull(TwoBoneIk.solve(root, mid, end, target))
            val actual = applySolution(root, mid, end, result)
            assertEquals("x for $target", target.x, actual.x, 0.003f)
            assertEquals("y for $target", target.y, actual.y, 0.003f)
            assertTrue(result.rootDeltaDegrees in -180f..180f)
            assertTrue(result.midDeltaDegrees in -180f..180f)
        }
    }

    @Test
    fun unreachableDragClampsToReachAndKeepsTargetHeading() {
        val root = IkPoint(0f, 0f)
        val mid = IkPoint(1f, 0f)
        val end = IkPoint(2f, 0f)
        val target = IkPoint(300f, 400f)
        val solved = requireNotNull(TwoBoneIk.solve(root, mid, end, target))
        val actual = applySolution(root, mid, end, solved)
        assertEquals(2f, hypot(actual.x, actual.y), 0.005f)
        assertEquals(0.6f, actual.x / 2f, 0.005f)
        assertEquals(0.8f, actual.y / 2f, 0.005f)
    }
}
