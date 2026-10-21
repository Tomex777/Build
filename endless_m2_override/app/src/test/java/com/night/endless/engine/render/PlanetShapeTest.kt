package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanetShapeTest {
    @Test fun giantPlanetsAreOblateNotPerfectSpheres() {
        assertEquals(66854.0 / 71492.0, PlanetShape.polarRatio("jupiter").toDouble(), 1e-6)
        assertEquals(54364.0 / 60268.0, PlanetShape.polarRatio("saturn").toDouble(), 1e-6)
        assertTrue(PlanetShape.polarRatio("jupiter") < PlanetShape.polarRatio("uranus"))
        assertTrue(PlanetShape.polarRatio("saturn") < PlanetShape.polarRatio("jupiter"))
        assertEquals(1f, PlanetShape.polarRatio("moon"), 0f)
        assertEquals(1f, PlanetShape.polarRatio("earth"), 0f)
    }

    @Test fun eachScalePreservesVolumeEquivalentMeanRadius() {
        for (body in listOf("jupiter", "saturn", "uranus", "neptune", "earth")) {
            val eq = PlanetShape.equatorialScale(body).toDouble()
            val polar = PlanetShape.polarScale(body).toDouble()
            val ratio = PlanetShape.polarRatio(body).toDouble()
            assertEquals(body, ratio, polar / eq, 2e-7)
            assertEquals(body, 1.0, eq * eq * polar, 3e-7)
        }
    }

    @Test fun ellipsoidNormalsUseReciprocalPolarScale() {
        // Vertex shader transforms (nx, ny / ratio², nz) with model
        // (eq, polar, eq). For a 45-degree local normal, y/x must become
        // eq/polar (not polar/eq): inverse-transpose normal math.
        for (body in listOf("jupiter", "saturn", "uranus", "neptune")) {
            val eq = PlanetShape.equatorialScale(body).toDouble()
            val polar = PlanetShape.polarScale(body).toDouble()
            val ratio = PlanetShape.polarRatio(body).toDouble()
            val shaderNormalX = eq
            val shaderNormalY = polar / (ratio * ratio)
            assertEquals(body, eq / polar, shaderNormalY / shaderNormalX, 2e-7)
        }
    }

    @Test fun selectionRayMatchesFlattenedGiantDiskAndAxialTilt() {
        val radius = 2.0
        val jupiterEq = radius * PlanetShape.equatorialScale("jupiter")
        val jupiterPolar = radius * PlanetShape.polarScale("jupiter")
        val alongX = com.night.endless.engine.math.Vec3d(-1.0, 0.0, 0.0)
        val alongY = com.night.endless.engine.math.Vec3d(0.0, -1.0, 0.0)

        val equatorialHit = PlanetShape.rayHitDistance(
            "jupiter", com.night.endless.engine.math.Vec3d(6.0, 0.0, 0.0),
            alongX, radius, 0.0
        )
        val polarHit = PlanetShape.rayHitDistance(
            "jupiter", com.night.endless.engine.math.Vec3d(0.0, 6.0, 0.0),
            alongY, radius, 0.0
        )
        assertEquals(6.0 - jupiterEq, equatorialHit!!, 1e-6)
        assertEquals(6.0 - jupiterPolar, polarHit!!, 1e-6)

        // A ninety-degree axial tilt rotates the polar axis onto world X.
        val tiltedHit = PlanetShape.rayHitDistance(
            "jupiter", com.night.endless.engine.math.Vec3d(6.0, 0.0, 0.0),
            alongX, radius, 90.0
        )
        assertEquals(6.0 - jupiterPolar, tiltedHit!!, 1e-6)
        assertTrue(PlanetShape.rayHitDistance(
            "jupiter", com.night.endless.engine.math.Vec3d(6.0, 6.0, 0.0),
            alongX, radius, 0.0
        ) == null)
    }
}
