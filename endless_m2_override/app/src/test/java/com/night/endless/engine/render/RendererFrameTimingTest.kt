package com.night.endless.engine.render

import com.night.endless.engine.scene.UniverseClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererFrameTimingTest {
    @Test fun astronomyKeepsTimeEvenIfSoftwareGlesOnlyDrawsOneFps() {
        val start = 9_000_000_000L
        val elapsed = RendererFrameTiming.elapsedSeconds(start, start+1_000_000_000L)
        assertEquals(1.0, elapsed, 0.0)
        val clock = UniverseClock()
        val before = clock.seconds()
        clock.advance(elapsed)
        assertEquals(before+10.0, clock.seconds(), 1e-8)
    }

    @Test fun animationSmoothnessIsSeparateFromActualSimulationRate() {
        val elapsed = RendererFrameTiming.elapsedSeconds(4_000L, 500_004_000L)
        assertEquals(.5, elapsed, 1e-8)
        assertEquals(.12, elapsed.coerceAtMost(.12), 1e-8)
        assertEquals(0.0, RendererFrameTiming.elapsedSeconds(20L,15L), 0.0)
        assertEquals(2.0, RendererFrameTiming.elapsedSeconds(10L,Long.MAX_VALUE), 0.0)
    }

    @Test fun stoppedClockNeverProgressesAndInvalidSecondsAreRejected() {
        val clock = UniverseClock()
        val initial = clock.seconds()
        clock.advance(Double.NaN)
        clock.advance(Double.NEGATIVE_INFINITY)
        assertEquals(initial, clock.seconds(), 0.0)
    }
}
