package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.night.endless.engine.math.Vec3d
import kotlin.math.PI
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
    @Test fun gpuShadowReferenceMatchesTrueModelRayIntersectionAcrossOrbitalAngles() {
        // Independent geometry implementation already used by the renderer
        // for selecting Saturn's *flattened, tilted* visible cloud disk.
        // Verify the shadow cannot diverge from the visible mesh radius.
        val tiltDegrees = 26.73
        val tilt = Math.toRadians(tiltDegrees)
        val axisX = -sin(tilt)
        val axisY = cos(tilt)
        val meanRadius = 2.0
        val equator = meanRadius * PlanetShape.equatorialScale("saturn")
        val polarRatio = PlanetShape.polarRatio("saturn").toDouble()
        val sunVectors = listOf(
            Vec3d(1.0, 0.0, 0.0), Vec3d(1.0, 0.35, 0.0),
            Vec3d(1.0, 0.8, 0.4), Vec3d(-1.0, 0.4, 0.7)
        )
        for (sun in sunVectors) {
            for (index in 0 until 32) {
                val theta = index * 2.0 * PI / 32
                // An annulus vertex generated in Saturn's equatorial plane
                // and then rotated around Z by the planetary axial tilt.
                val localX = equator * 1.8 * cos(theta)
                val x = localX * cos(tilt)
                val y = localX * sin(tilt)
                val z = equator * 1.8 * sin(theta)
                val metric = SaturnRingUmbra.closestEllipsoidRadiusSquared(
                    x, y, z, sun.x, sun.y, sun.z,
                    axisX, axisY, 0.0, equator, polarRatio
                )
                // Near-tangent cases are intentionally left for GPU
                // antialiasing/solar penumbra handling.
                if (abs(metric - 1.0) <= 0.012) continue
                val geometricHit = PlanetShape.rayHitDistance(
                    "saturn", Vec3d(x, y, z), sun, meanRadius, tiltDegrees
                ) != null
                assertEquals("theta=$theta sun=$sun", geometricHit, metric < 1.0)
            }
        }
    }

}
