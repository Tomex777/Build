package com.night.endless.engine.render

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererFramePacingTest {
    @Test fun visibleSceneDrawsAtEveryDisplayRefresh() {
        val first = 10_000_000_000L
        assertTrue(RendererFramePacing.shouldRequest(false, first, first + 8_333_333L))
        assertTrue(RendererFramePacing.shouldRequest(false, first, first + 16_666_667L))
        assertTrue(RendererFramePacing.shouldRequest(false, first, first + 33_333_333L))
    }

    @Test fun hiddenSceneRunsAtMostFiveRequestsPerSecond() {
        val first = 2_000_000_000L
        assertTrue(RendererFramePacing.shouldRequest(true, 0L, first))
        assertFalse(RendererFramePacing.shouldRequest(true, first, first + 16_666_666L))
        assertFalse(RendererFramePacing.shouldRequest(true, first, first + 199_999_999L))
        assertTrue(RendererFramePacing.shouldRequest(true, first, first + 200_000_000L))
    }

    @Test fun resumptionNeverWaitsForPreviousHiddenFrame() {
        val previous = 5_000_000_000L
        assertTrue(RendererFramePacing.shouldRequest(false, previous, previous + 1L))
        assertTrue(RendererFramePacing.shouldRequest(true, 0L, 1L))
        assertFalse(RendererFramePacing.shouldRequest(true, previous, previous - 1L))
        assertFalse(RendererFramePacing.shouldRequest(true, previous, previous))
    }
}
