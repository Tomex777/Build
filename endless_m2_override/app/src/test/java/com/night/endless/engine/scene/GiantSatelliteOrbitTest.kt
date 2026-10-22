package com.night.endless.engine.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GiantSatelliteOrbitTest {
    @Test fun titanAlwaysTravelsInSaturnsTiltedRingPlane() {
        val tilt = 26.73
        val rad = Math.toRadians(tilt)
        val polar = doubleArrayOf(-sin(rad), cos(rad), 0.0)
        val radius = 1.93
        for (step in 0..720) {
            val angle = 2 * PI * step / 720.0
            val p = GiantSatelliteOrbit.position(radius, angle, tilt)
            val distance = sqrt(p.x*p.x+p.y*p.y+p.z*p.z)
            val ringPlaneDistance = p.x*polar[0] + p.y*polar[1] + p.z*polar[2]
            assertEquals("Giant's satellite radius must not oscillate", radius, distance, 1e-12)
            assertEquals("Titan must be coplanar with Saturn's ring equator",
                0.0, ringPlaneDistance, 1e-12)
        }
    }

    @Test fun europaIsNearlyInJupitersEquatorialPlaneNotAnArbitraryWobble() {
        val radius = 1.98
        val p = GiantSatelliteOrbit.position(radius, 0.0, 3.13)
        assertEquals(radius*cos(Math.toRadians(3.13)), p.x, 1e-12)
        assertEquals(radius*sin(Math.toRadians(3.13)), p.y, 1e-12)
        assertEquals(0.0, p.z, 1e-12)
        val pLater = GiantSatelliteOrbit.position(radius, 2.0*PI, 3.13)
        assertEquals(p.x, pLater.x, 1e-12)
        assertEquals(p.y, pLater.y, 1e-12)
        assertEquals(p.z, pLater.z, 1e-12)
    }

    @Test fun renderedOrbitLoopAndSimulationShareTheExactGeometry() {
        for ((tilt, radius) in listOf(3.13 to 1.98, 26.73 to 1.93)) {
            val samples = GiantSatelliteOrbit.polyline(radius, tilt, 96)
            assertEquals(288, samples.size)
            for (index in 0 until 96) {
                val p = GiantSatelliteOrbit.position(radius, 2.0*PI*index/96, tilt)
                assertEquals(p.x, samples[3*index].toDouble(), 2e-7)
                assertEquals(p.y, samples[3*index+1].toDouble(), 2e-7)
                assertEquals(p.z, samples[3*index+2].toDouble(), 2e-7)
            }
        }
    }

    @Test fun invalidOrbitInputsFailRatherThanPoisoningRenderer() {
        for (radius in listOf(0.0,-1.0,Double.NaN)) {
            try {
                GiantSatelliteOrbit.position(radius, 0.0, 26.73)
                throw AssertionError("Invalid orbital radius $radius was accepted")
            } catch (_: IllegalArgumentException) { }
        }
        try {
            GiantSatelliteOrbit.polyline(1.0, 3.13, 0)
            throw AssertionError("Degenerate orbit line was accepted")
        } catch (_: IllegalArgumentException) { }
        assertTrue(GiantSatelliteOrbit.position(1.0, 0.0, 0.0).y == 0.0)
    }
}
