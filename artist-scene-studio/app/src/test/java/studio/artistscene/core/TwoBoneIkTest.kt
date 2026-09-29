package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoBoneIkTest {
    @Test
    fun bendsStraightChainTowardReachableTarget() {
        val solution = assertNotNull(
            TwoBoneIk.solve(
                root = IkPoint(0f, 0f),
                mid = IkPoint(1f, 0f),
                end = IkPoint(2f, 0f),
                target = IkPoint(1f, 1f),
            ),
        ) as TwoBoneIkSolution

        assertEquals(0f, solution.rootDeltaDegrees, 0.05f)
        assertEquals(90f, solution.midDeltaDegrees, 0.05f)
    }

    @Test
    fun rotatesWholeStraightChainTowardFullReachTarget() {
        val solution = assertNotNull(
            TwoBoneIk.solve(
                root = IkPoint(0f, 0f),
                mid = IkPoint(1f, 0f),
                end = IkPoint(2f, 0f),
                target = IkPoint(0f, 2f),
            ),
        ) as TwoBoneIkSolution

        assertEquals(90f, solution.rootDeltaDegrees, 0.2f)
        assertEquals(0f, solution.midDeltaDegrees, 0.2f)
    }

    @Test
    fun preservesExistingBendDirectionInsteadOfFlippingLimb() {
        val solution = assertNotNull(
            TwoBoneIk.solve(
                root = IkPoint(0f, 0f),
                mid = IkPoint(1f, 0f),
                end = IkPoint(1f, -1f),
                target = IkPoint(1f, -1f),
            ),
        ) as TwoBoneIkSolution

        assertEquals(0f, solution.rootDeltaDegrees, 0.05f)
        assertEquals(0f, solution.midDeltaDegrees, 0.05f)
    }

    @Test
    fun clampsUnreachableTargetsWithoutProducingInvalidAngles() {
        val solution = assertNotNull(
            TwoBoneIk.solve(
                root = IkPoint(0f, 0f),
                mid = IkPoint(1f, 0f),
                end = IkPoint(2f, 0f),
                target = IkPoint(100f, 50f),
            ),
        ) as TwoBoneIkSolution

        assertTrue(solution.rootDeltaDegrees.isFinite())
        assertTrue(solution.midDeltaDegrees.isFinite())
        assertTrue(solution.rootDeltaDegrees in -180f..180f)
        assertTrue(solution.midDeltaDegrees in -180f..180f)
    }
}
