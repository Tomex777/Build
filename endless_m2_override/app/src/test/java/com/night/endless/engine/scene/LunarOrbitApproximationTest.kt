package com.night.endless.engine.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

class LunarOrbitApproximationTest {
    @Test fun sampledGuideMatchesActualMoonPhysicsAtSameTimes() {
        val at = 1792630800000L // 2026 epoch, no external ephemeris claim
        val scale = 1.15
        val n = 96
        val points = LunarOrbitApproximation.orbitPolyline(at, scale, n)
        assertEquals(n*3, points.size)
        val millisStep = LunarOrbitApproximation.SIDEREAL_DAYS*86400000.0/n
        for (i in 0 until n) {
            val now = at + (i*millisStep).toLong()
            val p = LunarOrbitApproximation.relativePosition(now, scale)
            assertEquals(p.x,points[3*i].toDouble(),2e-7)
            assertEquals(p.y,points[3*i+1].toDouble(),2e-7)
            assertEquals(p.z,points[3*i+2].toDouble(),2e-7)
        }
    }

    @Test fun moonHasRealEccentricityAndInclinationInsteadOfFlatCircle() {
        val radius = 1.15
        val at = LunarOrbitApproximation.EPOCH_MILLIS
        val n = 720
        val period = LunarOrbitApproximation.SIDEREAL_DAYS*86400000.0
        var least = Double.POSITIVE_INFINITY
        var greatest = Double.NEGATIVE_INFINITY
        var maxVertical = 0.0
        for (i in 0 until n) {
            val p = LunarOrbitApproximation.relativePosition(at+(i*period/n).toLong(), radius)
            val d = sqrt(p.x*p.x+p.y*p.y+p.z*p.z)
            least = minOf(least,d)
            greatest = maxOf(greatest,d)
            maxVertical = maxOf(maxVertical,abs(p.y))
        }
        assertTrue("Moon needs noncircular distance", greatest/least > 1.10)
        assertTrue("Moon's orbit must leave the ecliptic", maxVertical > radius*.07)
        assertTrue("The orbital radius must remain near the moon scale", greatest < radius*1.06)
        assertTrue(least > radius*.94)
    }

    @Test fun invalidInputsFailBeforeRenderingGarbledGuide() {
        for (badScale in listOf(0.0, -1.0, Double.NaN)) {
            try {
                LunarOrbitApproximation.relativePosition(0L,badScale)
                throw AssertionError("Accepted invalid orbit scale")
            } catch (_: IllegalArgumentException) {}
        }
        try {
            LunarOrbitApproximation.orbitPolyline(0L,1.0,0)
            throw AssertionError("Accepted invalid vertex count")
        } catch (_: IllegalArgumentException) {}
    }
}
