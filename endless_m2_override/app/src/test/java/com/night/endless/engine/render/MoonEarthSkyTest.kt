package com.night.endless.engine.render

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonEarthSkyTest {
    @Test fun tychoSeesEarthToNorthNortheastAtCorrectElevation() {
        val earth = MoonEarthSky.tychoDirection
        assertEquals(3, earth.size)
        assertEquals(1.0, sqrt(earth.map { it.toDouble() * it }.sum()), 1e-6)
        // Tycho crater lies in the lunar SOUTHERN hemisphere, so Earth
        // appears over the NORTHERN horizon, not in the southern sky.
        assertTrue("Earth must appear north of Tycho", earth[2] > .67f)
        assertEquals(.1976f, earth[0], .003f)
        assertEquals(.7112f, earth[1], .003f)
        val elevationDegrees = asin(earth[1].toDouble()) * 180.0 / PI
        val eastOfNorthDegrees = atan2(
            earth[0].toDouble(), earth[2].toDouble()
        ) * 180.0 / PI
        assertEquals(45.33, elevationDegrees, .5)
        assertEquals(16.32, eastOfNorthDegrees, .5)
    }

    @Test fun earthDirectionRespectsVisibleNearSideAndHiddenFarSide() {
        val beneathEarth = MoonEarthSky.earthDirectionAt(0.0, 0.0)
        assertTrue("Earth should be overhead at the sub-Earth point",
            beneathEarth[1] > .999f)
        val farSide = MoonEarthSky.earthDirectionAt(0.0, 180.0)
        assertTrue("Earth must be BELOW the far-side lunar horizon",
            farSide[1] < -.999f)
        val southPole = MoonEarthSky.earthDirectionAt(-90.0, 0.0)
        assertTrue("Pole Earth's finite-distance parallax shifts it below horizon",
            southPole[1] < 0f)
    }

    @Test fun invalidSiteCannotProduceNaNOrInfiniteShaderNormals() {
        for (site in listOf(Double.NaN, Double.POSITIVE_INFINITY, 91.0)) {
            try {
                MoonEarthSky.earthDirectionAt(site, 0.0)
                throw AssertionError("Expected invalid latitude rejection")
            } catch (_: IllegalArgumentException) {}
        }
    }
}
