package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class PlanetarySpinTest {
    @Test fun rotationPreservesSiderealPeriodButDoesNotDoubleReverseTiltedPlanets() {
        val venus = -5832.5
        val uranus = -17.24
        val normalAngle = PlanetarySpin.angleDegrees(5.0, 24.0)
        assertEquals(75f, normalAngle, .0001f)
        assertEquals(90f, PlanetarySpin.angleDegrees(venus / -4.0, venus), .001f)
        assertEquals(90f, PlanetarySpin.angleDegrees(uranus / -4.0, uranus), .001f)
        // Sign of spin rate projected onto ecliptic north is determined by
        // obliquity alone now: Venus and Uranus should be RETROGRADE.
        for (tilt in listOf(177.36, 97.77)) {
            val poleEclipticNorth = cos(Math.toRadians(tilt))
            assertTrue("Tilted spin axis must point south of ecliptic", poleEclipticNorth < 0.0)
            val localPositiveRotation =
                PlanetarySpin.angleDegrees(2.0, if (tilt > 150) venus else uranus)
            assertTrue("Local spin must not undo physical axial obliquity", localPositiveRotation > 0f)
            assertTrue("World-space spin must be retrograde",
                poleEclipticNorth * localPositiveRotation < 0.0)
        }
    }

    @Test fun phaseRemainsBoundedOverLongFastForwardAndRunsBackwardsBeforeEpoch() {
        for (hours in listOf(-1e12, -24.0, 0.0, 1.0, 1e12)) {
            val phase = PlanetarySpin.angleDegrees(hours, 23.9345)
            assertTrue(phase.isFinite())
            assertTrue(kotlin.math.abs(phase) < 360f)
        }
        assertEquals(0f, PlanetarySpin.angleDegrees(5832.5, -5832.5), .001f)
        assertEquals(-90f, PlanetarySpin.angleDegrees(-6.0, 24.0), .001f)
    }

    @Test fun invalidPeriodsCannotPolluteTheOpenGlModelMatrix() {
        assertEquals(0f, PlanetarySpin.angleDegrees(12.0, 0.0), 0f)
        assertEquals(0f, PlanetarySpin.angleDegrees(Double.NaN, -17.24), 0f)
        assertEquals(0f, PlanetarySpin.angleDegrees(12.0, Double.POSITIVE_INFINITY), 0f)
        assertEquals(0f, PlanetarySpin.angleDegrees(Double.POSITIVE_INFINITY, 24.0), 0f)
    }
}
