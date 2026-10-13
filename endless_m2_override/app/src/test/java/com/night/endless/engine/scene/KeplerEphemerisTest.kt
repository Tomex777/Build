package com.night.endless.engine.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class KeplerEphemerisTest {
    private val j2000 = 946728000000L // 2000-01-01T12:00:00Z

    @Test fun earthBarycentreAtJ2000HasKnownApproximateHeliocentricLocation() {
        val earth = requireNotNull(KeplerEphemeris.position("earth", j2000, 9.5))
        // JPL mean ecliptic J2000 barycentre: approximately (-0.177, +0.967) AU.
        // Z is scene ecliptic +Y, Y is vertical latitude.
        assertEquals(-1.68, earth.x, 0.15)
        assertEquals(0.0, earth.y, 0.03)
        assertEquals(9.18, earth.z, 0.15)
    }

    @Test fun ellipticalOrbitsHavePerihelionAndAphelionAndNonzeroInclination() {
        val mars = requireNotNull(KeplerEphemeris.orbitPoints("mars", j2000, 12.3, 360))
        val radii = (0 until 360).map { i ->
            val x = mars[i * 3].toDouble()
            val y = mars[i * 3 + 1].toDouble()
            val z = mars[i * 3 + 2].toDouble()
            sqrt(x * x + y * y + z * z)
        }
        assertTrue("Mars needs an elliptical orbit, not a circle", radii.max() - radii.min() > 1.5)
        val mercury = requireNotNull(KeplerEphemeris.orbitPoints("mercury", j2000, 5.0, 180))
        assertTrue("Mercury must leave the reference orbital plane",
            (0 until 180).any { abs(mercury[it * 3 + 1]) > 0.03 })
    }

    @Test fun eccentricAnomalySatisfiesKeplersEquation() {
        val mean = 2.2
        val e = 0.20563593
        val anomaly = KeplerEphemeris.solveEccentricAnomaly(mean, e)
        assertEquals(mean, anomaly - e * kotlin.math.sin(anomaly), 1e-12)
    }
}
