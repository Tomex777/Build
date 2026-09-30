package com.night.endless.engine.scene

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class CosmicPointCloudsTest {
    @Test
    fun pointCloudsAreDeterministicAndFinite() {
        val first = CosmicPointClouds.localStars(32)
        val second = CosmicPointClouds.localStars(32)
        assertArrayEquals(first, second, 0f)
        assertEquals(96, first.size)
        assertTrue(first.all { it.isFinite() })
    }

    @Test
    fun milkyWayIsAFlattenedDiskWithAVisibleBulge() {
        val points = CosmicPointClouds.milkyWay(700)
        var maxPlanar = 0.0
        var maxHeight = 0.0
        for (i in points.indices step 3) {
            maxPlanar = maxOf(maxPlanar, sqrt((points[i] * points[i] + points[i + 2] * points[i + 2]).toDouble()))
            maxHeight = maxOf(maxHeight, kotlin.math.abs(points[i + 1].toDouble()))
        }
        assertTrue(maxPlanar > 20.0)
        assertTrue(maxHeight < maxPlanar * 0.5)
    }

    @Test
    fun universeViewContainsBroadThreeDimensionalStructure() {
        val points = CosmicPointClouds.observableUniverse(500)
        val xs = points.indices.step(3).map { points[it] }
        val ys = points.indices.step(3).map { points[it + 1] }
        val zs = points.indices.step(3).map { points[it + 2] }
        assertTrue((xs.max() - xs.min()) > 25f)
        assertTrue((ys.max() - ys.min()) > 20f)
        assertTrue((zs.max() - zs.min()) > 25f)
    }
}
