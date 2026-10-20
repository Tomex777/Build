package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoBoneIkRobustnessTest {
    @Test
    fun rejectsNonFiniteProjectionAndCollapsedLimbInsteadOfCorruptingSkeleton() {
        val origin = IkPoint(0f, 0f)
        val mid = IkPoint(1f, 0f)
        val end = IkPoint(2f, 0f)
        assertNull(TwoBoneIk.solve(origin, mid, end, IkPoint(Float.NaN, 1f)))
        assertNull(TwoBoneIk.solve(origin, mid, end, IkPoint(1f, Float.POSITIVE_INFINITY)))
        assertNull(TwoBoneIk.solve(origin, IkPoint(Float.NEGATIVE_INFINITY, 0f), end, mid))
        assertNull(TwoBoneIk.solve(origin, origin, end, mid))
        assertNull(TwoBoneIk.solve(origin, mid, mid, end))
    }

    @Test
    fun largeFiniteProjectedRigStillProducesFiniteAccurateAngles() {
        // Float length-squared overflows above ~1e19; this is a valid finite geometry input.
        val length = 1e20f
        val result = requireNotNull(
            TwoBoneIk.solve(
                IkPoint(0f, 0f),
                IkPoint(length, 0f),
                IkPoint(2f * length, 0f),
                IkPoint(length, length),
            ),
        )
        assertTrue(result.rootDeltaDegrees.isFinite())
        assertTrue(result.midDeltaDegrees.isFinite())
        assertEquals(0f, result.rootDeltaDegrees, 0.1f)
        assertEquals(90f, result.midDeltaDegrees, 0.1f)
    }
}
