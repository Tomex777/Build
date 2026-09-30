package com.night.endless.engine.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepTimeHistoryTest {
    @Test
    fun earthEpochsProduceDistinctInterpolatedVisualStates() {
        val molten = DeepTimeHistory.earthVisualState(4.48)
        val snowball = DeepTimeHistory.earthVisualState(0.70)
        val present = DeepTimeHistory.earthVisualState(0.0)

        assertTrue("early Earth should be visibly molten", molten.lava > .7f)
        assertTrue("Snowball Earth should have extensive ice", snowball.ice > .9f)
        assertTrue("present Earth should not be molten", present.lava == 0f)
        assertTrue("present and early Earth states must differ", molten != present)
    }

    @Test
    fun eventTracksRemainOnOneGaClockAndLabelModelledFuture() {
        val earthFormation = DeepTimeHistory.events("Earth").first { it.id == "earth-forms" }
        val moonFormation = DeepTimeHistory.events("Moon").first { it.id == "moon-forms" }
        assertEquals(4.54, earthFormation.ageGa, 0.0)
        assertEquals(4.47, moonFormation.ageGa, 0.0)
        assertTrue(DeepTimeHistory.formatAge(-5.0).contains("future"))
        assertTrue(DeepTimeHistory.events("Sun").first { it.id == "red-giant" }.confidence.startsWith("Future"))
    }

    @Test
    fun systemTimelineStartsWithTheCloudAndEndsAtPresent() {
        val events = DeepTimeHistory.events("System")
        assertEquals("cloud", events.first().id)
        assertEquals("system-now", events.last().id)
        assertEquals("Present", DeepTimeHistory.formatAge(0.0))
        assertEquals("66 Ma ago", DeepTimeHistory.formatAge(0.066))
        assertEquals("20 ka ago", DeepTimeHistory.formatAge(0.00002))
        assertEquals("250 years ago", DeepTimeHistory.formatAge(0.00000025))
        assertEquals(0f, DeepTimeHistory.systemFormationProgress(4.6), .001f)
        assertEquals(1f, DeepTimeHistory.systemFormationProgress(3.85), .001f)
    }

    @Test
    fun timelineSliderRunsFromOldestToFutureAndEventControlsFollowChronology() {
        assertEquals(0f, DeepTimeHistory.sliderPosition(4.6), .001f)
        assertEquals(11.6f, DeepTimeHistory.sliderPosition(-7.0), .001f)
        assertEquals(4.6, DeepTimeHistory.ageFromSlider(0f), .001)
        assertEquals(-7.0, DeepTimeHistory.ageFromSlider(11.6f), .001)
        assertEquals("cloud", DeepTimeHistory.adjacentEvent("System", 4.56, -1)?.id)
        assertEquals("planetesimals", DeepTimeHistory.adjacentEvent("System", 4.56, 1)?.id)
    }
    @Test
    fun chicxulubEpochHasASchematicImpactCueThatFadesAcrossItsKeyframe() {
        assertEquals(1f, DeepTimeHistory.earthVisualState(.066).impact, .001f)
        assertEquals(0f, DeepTimeHistory.earthVisualState(.08).impact, .001f)
        assertEquals(0f, DeepTimeHistory.earthVisualState(.05).impact, .001f)
    }

    @Test
    fun marsHistoryMovesFromHotToWetToModernOnTheSharedClock() {
        val hot = DeepTimeHistory.marsVisualState(4.45)
        val wet = DeepTimeHistory.marsVisualState(3.70)
        val present = DeepTimeHistory.marsVisualState(0.0)

        assertTrue("young Mars should carry a strong hot-surface cue", hot.lava > .7f)
        assertTrue("early-water Mars should carry a strong schematic water cue", wet.water > .8f)
        assertEquals(0f, present.lava, .001f)
        assertEquals(0f, present.water, .001f)
    }

    @Test
    fun venusHistoryBuildsAtmosphereAndShowsResurfacingCue() {
        assertTrue("Venus" in DeepTimeHistory.domains)
        val magma = DeepTimeHistory.venusVisualState(4.40)
        val atmosphere = DeepTimeHistory.venusVisualState(4.00)
        val resurfacing = DeepTimeHistory.venusVisualState(0.70)
        val present = DeepTimeHistory.venusVisualState(0.0)

        assertTrue("early Venus should carry a strong magma cue", magma.lava > .7f)
        assertTrue("young Venus atmosphere should still be developing", magma.atmosphere < atmosphere.atmosphere)
        assertTrue("resurfacing anchor should carry a volcanic cue", resurfacing.lava >= .5f)
        assertEquals(0f, present.lava, .001f)
        assertEquals(1f, present.atmosphere, .001f)

        val events = DeepTimeHistory.events("Venus")
        assertEquals("venus-forms", events.first().id)
        assertEquals("venus-now", events.last().id)
        assertTrue(events.zipWithNext().all { (older, newer) -> older.ageGa >= newer.ageGa })
    }

    @Test
    fun asteroidBeltTrackSharesTheMasterTimeline() {
        assertTrue("Asteroid Belt" in DeepTimeHistory.domains)
        val events = DeepTimeHistory.events("Asteroid Belt")
        assertEquals("belt-solids", events.first().id)
        assertEquals("belt-now", events.last().id)
        assertTrue(events.zipWithNext().all { (older, newer) -> older.ageGa >= newer.ageGa })
    }

    @Test
    fun moonHistorySeparatesMagmaBombardmentAndMareEpochs() {
        val magma = DeepTimeHistory.moonVisualState(4.40)
        val bombardment = DeepTimeHistory.moonVisualState(3.90)
        val mare = DeepTimeHistory.moonVisualState(3.50)
        val present = DeepTimeHistory.moonVisualState(0.0)

        assertTrue("magma-ocean epoch should be visibly hot", magma.lava > .7f)
        assertTrue("basin-forming epoch should carry an impact cue", bombardment.impact > .8f)
        assertTrue("mare-volcanism epoch should carry a basalt cue", mare.basalt > .8f)
        assertEquals(0f, present.lava, .001f)
        assertEquals(0f, present.impact, .001f)
        assertEquals(0f, present.basalt, .001f)
    }
    @Test
    fun mercuryHistoryHasHotImpactAndPresentStatesOnTheSharedClock() {
        assertTrue("Mercury" in DeepTimeHistory.domains)
        val magma = DeepTimeHistory.mercuryVisualState(4.40)
        val caloris = DeepTimeHistory.mercuryVisualState(3.88)
        val present = DeepTimeHistory.mercuryVisualState(0.0)

        assertTrue("young Mercury should carry a hot-surface cue", magma.lava > .7f)
        assertTrue("Caloris epoch should carry a strong schematic impact cue", caloris.impact > .8f)
        assertEquals(0f, present.lava, .001f)
        assertEquals(0f, present.impact, .001f)

        val events = DeepTimeHistory.events("Mercury")
        assertEquals("mercury-forms", events.first().id)
        assertEquals("mercury-now", events.last().id)
        assertTrue(events.zipWithNext().all { (older, newer) -> older.ageGa >= newer.ageGa })
    }

    @Test
    fun giantPlanetTracksAreChronologicalAndVisiblySettleAfterFormation() {
        val domains = listOf("Jupiter", "Saturn", "Uranus", "Neptune")
        domains.forEach { domain ->
            assertTrue("$domain history track is missing", domain in DeepTimeHistory.domains)
            val events = DeepTimeHistory.events(domain)
            assertTrue("$domain needs multiple curated epochs", events.size >= 4)
            assertTrue("$domain events are not chronological", events.zipWithNext().all { (older, newer) -> older.ageGa >= newer.ageGa })
            assertEquals("Present $domain event must end at the shared present", 0.0, events.last().ageGa, 0.0)
        }

        assertTrue(
            "young Jupiter should differ from its settled present appearance",
            DeepTimeHistory.jupiterVisualState(4.54).atmosphere < DeepTimeHistory.jupiterVisualState(0.0).atmosphere
        )
        assertTrue(
            "young Saturn should differ from its settled present appearance",
            DeepTimeHistory.saturnVisualState(4.53).atmosphere < DeepTimeHistory.saturnVisualState(0.0).atmosphere
        )
        assertTrue(
            "young Uranus should differ from its settled present appearance",
            DeepTimeHistory.uranusVisualState(4.52).atmosphere < DeepTimeHistory.uranusVisualState(0.0).atmosphere
        )
        assertTrue(
            "young Neptune should differ from its settled present appearance",
            DeepTimeHistory.neptuneVisualState(4.52).atmosphere < DeepTimeHistory.neptuneVisualState(0.0).atmosphere
        )
    }

}
