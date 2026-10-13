package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceRockShadowsTest {
    private val flat: (Double, Double) -> Double = { _, _ -> 0.0 }

    @Test fun eachRockHasFiniteGeographicallyAnchoredFeatheredShadow() {
        val rocks = SurfaceDetailMesh.buildRocks(0.0, 0.0, flat)
        val contact = SurfaceRockShadows.build(rocks, flat, -.45, .85, .35)
        assertEquals(rocks.count * SurfaceRockShadows.SEGMENTS * 3 * 9, contact.size)
        assertTrue(contact.all { it.isFinite() })
        assertTrue(rocks.count > 100)
        // Every triangular wedge has a darker centre and zero-alpha edge.
        for (index in 0 until rocks.count * SurfaceRockShadows.SEGMENTS) {
            val p = index * 3 * 9
            assertTrue(contact[p + 6] > .1f)
            assertEquals(0f, contact[p + 9 + 6], 1e-6f)
            assertEquals(0f, contact[p + 18 + 6], 1e-6f)
        }
    }

    @Test fun contactShadowOrientationChangesWithSolarAzimuth() {
        val rocks = SurfaceDetailMesh.buildRocks(0.0, 0.0, flat)
        val east = SurfaceRockShadows.build(rocks, flat, -.45, .85, .35)
        val west = SurfaceRockShadows.build(rocks, flat, .45, .85, .35)
        assertTrue("Shadows must follow the incoming light direction",
            kotlin.math.abs(east[0] - west[0]) > 0.00001f)
    }
}
