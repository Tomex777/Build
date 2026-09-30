package com.night.endless.engine.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CosmicScaleModelTest {
    @Test
    fun scaleLadderExpandsMonotonicallyFromSolarSystemToUniverse() {
        val spans = CosmicScaleModel.ordered.map { CosmicScaleModel.descriptor(it).spanLightYears }
        assertTrue(spans.zipWithNext().all { (near, far) -> far > near })
        assertEquals(ExplorationScale.SOLAR_SYSTEM, CosmicScaleModel.previous(ExplorationScale.SOLAR_SYSTEM))
        assertEquals(
            ExplorationScale.OBSERVABLE_UNIVERSE,
            CosmicScaleModel.next(ExplorationScale.OBSERVABLE_UNIVERSE)
        )
    }

    @Test
    fun largerScaleViewsAreExplicitlySchematic() {
        assertTrue(CosmicScaleModel.descriptor(ExplorationScale.LOCAL_STARS).schematic)
        assertTrue(CosmicScaleModel.descriptor(ExplorationScale.MILKY_WAY).schematic)
        assertTrue(CosmicScaleModel.descriptor(ExplorationScale.OBSERVABLE_UNIVERSE).schematic)
    }
}
