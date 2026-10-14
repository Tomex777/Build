package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoBoneIkTest {
    @Test
    fun bendsStraightChainTowardReachableTarget() {
        val solution = TwoBoneIk.solve(
            root = IkPoint(0f, 0f),
            mid = IkPoint(1f, 0f),
            end = IkPoint(2f, 0f),
            target = IkPoint(1f, 1f),
        )
        assertNotNull(solution)
        val solved = requireNotNull(solution)

        assertEquals(0f, solved.rootDeltaDegrees, 0.05f)
        assertEquals(90f, solved.midDeltaDegrees, 0.05f)
    }

    @Test
    fun rotatesWholeStraightChainTowardFullReachTarget() {
        val solution = TwoBoneIk.solve(
            root = IkPoint(0f, 0f),
            mid = IkPoint(1f, 0f),
            end = IkPoint(2f, 0f),
            target = IkPoint(0f, 2f),
        )
        assertNotNull(solution)
        val solved = requireNotNull(solution)

        assertEquals(90f, solved.rootDeltaDegrees, 0.2f)
        assertEquals(0f, solved.midDeltaDegrees, 0.2f)
    }

    @Test
    fun preservesExistingBendDirectionInsteadOfFlippingLimb() {
        val solution = TwoBoneIk.solve(
            root = IkPoint(0f, 0f),
            mid = IkPoint(1f, 0f),
            end = IkPoint(1f, -1f),
            target = IkPoint(1f, -1f),
        )
        assertNotNull(solution)
        val solved = requireNotNull(solution)

        assertEquals(0f, solved.rootDeltaDegrees, 0.05f)
        assertEquals(0f, solved.midDeltaDegrees, 0.05f)
    }

    @Test
    fun clampsUnreachableTargetsWithoutProducingInvalidAngles() {
        val solution = TwoBoneIk.solve(
            root = IkPoint(0f, 0f),
            mid = IkPoint(1f, 0f),
            end = IkPoint(2f, 0f),
            target = IkPoint(100f, 50f),
        )
        assertNotNull(solution)
        val solved = requireNotNull(solution)

        assertTrue(solved.rootDeltaDegrees.isFinite())
        assertTrue(solved.midDeltaDegrees.isFinite())
        assertTrue(solved.rootDeltaDegrees in -180f..180f)
        assertTrue(solved.midDeltaDegrees in -180f..180f)
    }
}
