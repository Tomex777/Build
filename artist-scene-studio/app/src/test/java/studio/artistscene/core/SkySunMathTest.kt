package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkySunMathTest {
    @Test fun skySunTracksActualDownwardFilamentLightDirection() {
        val westLow = SunOrbit.direction(270f, 12f)
        val eastHigh = SunOrbit.direction(90f, 65f)
        val westPosition = SkySunMath.toSunPosition(westLow)
        val eastPosition = SkySunMath.toSunPosition(eastHigh)
        assertEquals(1f, SkySunMath.alignment(
            westPosition, westPosition.x, westPosition.y, westPosition.z), 0.0001f)
        assertEquals(1f, SkySunMath.alignment(
            eastPosition, eastPosition.x, eastPosition.y, eastPosition.z), 0.0001f)
        assertTrue(SkySunMath.alignment(
            westPosition, eastPosition.x, eastPosition.y, eastPosition.z) < 0.5f)
        assertTrue(westPosition.y < eastPosition.y)
    }

    @Test fun quantizedDirectionsAreStableAcrossSmallTapsButMoveAtLargeTurns() {
        val a = SkySunMath.quantizeDirection(SunOrbit.direction(140.15f, 30.2f))
        val b = SkySunMath.quantizeDirection(SunOrbit.direction(140.6f, 30.6f))
        assertEquals(a.x, b.x, 0.0001f)
        assertEquals(a.y, b.y, 0.0001f)
        assertEquals(a.z, b.z, 0.0001f)
        val shifted = SkySunMath.quantizeDirection(SunOrbit.direction(149f, 30f))
        assertTrue(kotlin.math.abs(a.x - shifted.x) > 0.05f)
    }

    @Test fun rejectsZeroNonFiniteVectorsAndImpossibleStep() {
        assertInvalid { SkySunMath.toSunPosition(Vec3()) }
        assertInvalid { SkySunMath.toSunPosition(Vec3(Float.NaN, -1f, 0f)) }
        assertInvalid { SkySunMath.quantizeDirection(Vec3(0f, -1f, 0f), 0f) }
    }

    private inline fun assertInvalid(block: () -> Unit) {
        var failed = false
        try { block() } catch (_: IllegalArgumentException) { failed = true }
        assertTrue("Expected invalid sunlight direction to be rejected", failed)
    }
}
