package com.night.endless.engine.render

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitGuideRefreshTest {
    private val epoch = 946728000000L

    @Test fun noGuideOrRestoredClocksForceRefresh() {
        assertTrue(OrbitGuideRefresh.shouldRefresh(epoch, Long.MIN_VALUE))
        assertTrue(OrbitGuideRefresh.shouldRefresh(epoch + 15L*86_400_000L, epoch))
        assertTrue(OrbitGuideRefresh.shouldRefresh(epoch - 15L*86_400_000L, epoch))
    }

    @Test fun guideDoesNotReallocateEachFrameOrOnEverySimulationDay() {
        assertFalse(OrbitGuideRefresh.shouldRefresh(epoch, epoch))
        assertFalse(OrbitGuideRefresh.shouldRefresh(epoch + 13L*86_400_000L, epoch))
        assertFalse(OrbitGuideRefresh.shouldRefresh(epoch - 13L*86_400_000L, epoch))
        assertTrue(OrbitGuideRefresh.shouldRefresh(epoch + 14L*86_400_000L, epoch))
    }

    @Test fun evenExtremeInvalidClockDifferencesCannotHideRefresh() {
        assertTrue(OrbitGuideRefresh.shouldRefresh(Long.MAX_VALUE, epoch))
        assertTrue(OrbitGuideRefresh.shouldRefresh(epoch, Long.MAX_VALUE))
    }
}
