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
}
