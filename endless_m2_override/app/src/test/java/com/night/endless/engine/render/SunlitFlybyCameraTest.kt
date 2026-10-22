package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class SunlitFlybyCameraTest {
    @Test fun initialJupiterAndSaturnFramingShowsDaylitGasAndRings() {
        for ((x, z) in listOf(
            18.7 to 10.1, -24.3 to 3.7, 1.0 to 30.0,
            -7.0 to -18.0, 0.0 to -12.0
        )) {
            val yaw = checkNotNull(SunlitFlybyCamera.sunFacingYaw(x, z))
            val phase = SunlitFlybyCamera.illuminatedFractionEstimate(x, z, yaw)
            assertTrue("Sunward first look must not be nearly-black: $x,$z = $phase",
                phase > .95)
        }
    }

    @Test fun turnAlwaysTakesShortestSmoothOrbit() {
        val almostMinusPi = -PI + .08
        val almostPlusPi = PI - .08
        assertEquals(.16, SunlitFlybyCamera.shortestTurn(almostPlusPi,
            almostMinusPi), 1e-10)
        assertEquals(-.16, SunlitFlybyCamera.shortestTurn(almostMinusPi,
            almostPlusPi), 1e-10)
        assertTrue(abs(SunlitFlybyCamera.shortestTurn(0.0, PI)) <= PI)
    }

    @Test fun invalidSolarPositionDoesNotTurnOrCreateNaNs() {
        assertNull(SunlitFlybyCamera.sunFacingYaw(0.0, 0.0))
        assertNull(SunlitFlybyCamera.sunFacingYaw(Double.NaN, 7.0))
        assertEquals(0.0,
            SunlitFlybyCamera.shortestTurn(Double.POSITIVE_INFINITY, .7), 0.0)
    }
}
