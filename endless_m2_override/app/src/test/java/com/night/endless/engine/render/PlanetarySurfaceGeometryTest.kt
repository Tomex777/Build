package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanetarySurfaceGeometryTest {
    @Test fun horizonIsFourKilometersOnMarsAndAboutThreeOnMoon() {
        assertEquals(3.983, PlanetarySurfaceGeometry.horizonDistanceKm(
            PlanetarySurfaceGeometry.MARS_RADIUS_KM, 2.34), 0.03)
        assertEquals(2.851, PlanetarySurfaceGeometry.horizonDistanceKm(
            PlanetarySurfaceGeometry.MOON_RADIUS_KM, 2.34), 0.03)
    }

    @Test fun distantTerrainCurvesDownWithoutDistortingNearbyRocks() {
        val mars = PlanetarySurfaceGeometry.MARS_RADIUS_KM
        val unitsOneKm = 1.0 / 3.0
        val localSagMeters = PlanetarySurfaceGeometry.approximateDropUnits(unitsOneKm, mars) * 3000.0
        assertEquals(0.1475, localSagMeters, 0.005)
        val unitsThirtyKm = 10.0
        val marsDrop = PlanetarySurfaceGeometry.approximateDropUnits(unitsThirtyKm, mars)
        val moonDrop = PlanetarySurfaceGeometry.approximateDropUnits(unitsThirtyKm,
            PlanetarySurfaceGeometry.MOON_RADIUS_KM)
        assertTrue(marsDrop > 0.04)
        assertTrue(moonDrop > marsDrop)
    }
}
