package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.PI

class ExtendedLandingSitesTest {
    @Test fun eachNewWorldStartsWithinTraversableSampledHeightfield() {
        for (body in listOf("mercury", "ceres", "europa", "titan")) {
            val site = ExtendedLandingSites.forBody(body)
            assertTrue(abs(site.x) < 8.0 && abs(site.z) < 8.0)
            assertTrue(site.yaw.isFinite() && site.yaw in -PI..PI)
            assertTrue(site.pitch in -.35..0.20)
            assertTrue(ExtendedSurfaceMesh.renderedHeightAt(body, site.x, site.z).isFinite())
        }
    }

    @Test fun mercuryAndCeresShowSunlitCraterRimsAheadInsteadOfFlatHorizon() {
        for (body in listOf("mercury", "ceres")) {
            val site = ExtendedLandingSites.forBody(body)
            assertEquals(1.57, site.yaw, 0.001)
            val eye = ExtendedSurfaceMesh.renderedHeightAt(body, site.x, site.z)
            val easternRim = ExtendedSurfaceMesh.renderedHeightAt(body, site.x + 1.1, site.z)
            assertTrue("$body: restored landing should look toward obvious crater relief",
                easternRim - eye > .025)
        }
    }

    @Test fun simulatedLandingMustNotPretendToBeASurveyedPosition() {
        // A user can still walk in the familiar six-world environment;
        // geodesic coordinates/observed terrain are not implied by these
        // relative 3-km-world-unit placements.
        val europa = ExtendedLandingSites.forBody("europa")
        assertEquals(0.0, europa.x, 0.0)
        assertEquals(0.0, europa.z, 0.0)
        assertEquals(1.24, europa.yaw, 0.0)
    }
}
