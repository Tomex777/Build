package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Saturn ring scattering geometry. The actual umbra's oblate ray intersection
 * is checked separately in SaturnRingUmbraTest, not by a spherical stand-in.
 */
class SaturnRingIlluminationTest {
    private fun illumination(sunHeight: Double): Double =
        0.20 + 0.80 * sqrt(sunHeight.coerceIn(0.0, 1.0))

    @Test fun illuminationChangesSmoothlyWithSolarIncidence() {
        assertEquals(0.20, illumination(0.0), 1e-12)
        assertTrue(illumination(0.05) > illumination(0.0))
        assertTrue(illumination(0.5) > illumination(0.05))
        assertEquals(1.0, illumination(1.0), 1e-12)
    }

    @Test fun tiltRotatesTheAxisAndBothSidesScattersSunlight() {
        for (degrees in listOf(0.0, 26.73, 90.0, 180.0)) {
            val rad = Math.toRadians(degrees)
            val nx = -sin(rad)
            val ny = cos(rad)
            assertEquals(1.0, nx * nx + ny * ny, 1e-12)
            val front = abs(nx * 0.6 + ny * 0.8)
            val reverse = abs(nx * -0.6 + ny * -0.8)
            assertEquals(illumination(front), illumination(reverse), 1e-12)
        }
    }
}
