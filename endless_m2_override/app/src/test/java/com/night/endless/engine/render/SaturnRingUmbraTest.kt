package com.night.endless.engine.render

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/** The same oblate ray test used by the on-GPU Saturn ring fragment shader. */
class SaturnRingUmbraTest {
    @Test fun nightSideRayHitsSaturnButSunwardRayNeverDoes() {
        assertTrue(SaturnRingUmbra.blocked(-1.5, 0.0, 0.0, 1.0, 0.0, 0.0))
        assertFalse(SaturnRingUmbra.blocked(1.5, 0.0, 0.0, 1.0, 0.0, 0.0))
        assertFalse(SaturnRingUmbra.blocked(-1.5, 0.0, 1.1, 1.0, 0.0, 0.0))
    }

    @Test fun polarFlatteningChangesTheActualRayIntersection() {
        // Spherical umbra would cover this position; Saturn's true b/a
        // ratio shortens the shadow at oblique solar elevations.
        val args = doubleArrayOf(-2.0, 0.0, 0.4, 1.0, 0.5, 0.0)
        assertTrue(SaturnRingUmbra.blocked(args[0], args[1], args[2], args[3], args[4], args[5], polarRatio = 1.0))
        assertFalse(
            SaturnRingUmbra.blocked(
                args[0], args[1], args[2], args[3], args[4], args[5],
                polarRatio = PlanetShape.polarRatio("saturn").toDouble()
            )
        )
    }

    @Test fun tiltedPlanetRetainsConsistentUmbraOrientation() {
        val a = Math.toRadians(26.73)
        val axisX = -sin(a)
        val axisY = cos(a)
        assertTrue(SaturnRingUmbra.blocked(
            -1.8, 0.0, 0.0, 1.0, 0.0, 0.0,
            axisX = axisX, axisY = axisY
        ))
        assertTrue(abs(axisX * axisX + axisY * axisY - 1.0) < 1e-12)
    }

    @Test fun edgeOnSunAndInvalidGeometryDoNotPaintFakeUmbra() {
        assertFalse(SaturnRingUmbra.blocked(
            -1.8, 0.0, 0.0, 0.0, 1.0, 0.0
        ))
        assertFalse(SaturnRingUmbra.blocked(
            -1.8, 0.0, 0.0, 0.0, 0.0, 0.0
        ))
        assertFalse(SaturnRingUmbra.blocked(
            -1.8, 0.0, 0.0, Double.NaN, 0.0, 0.0
        ))
        assertFalse(SaturnRingUmbra.blocked(
            -1.8, 0.0, 0.0, 1.0, 0.0, 0.0,
            equatorialRadius = 0.0
        ))
    }
}
