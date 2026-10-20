package com.night.soundatlas

import org.junit.Assert.*
import org.junit.Test

class AudioQualityGovernorTest {
    @Test fun highCpuCostReducesMeasuredSourcesInsteadOfCrackling() {
        val quality=AudioQualityGovernor()
        assertEquals(4,quality.measuredVoices)
        repeat(5){quality.record(quality.budgetNanos)}
        assertEquals(0,quality.measuredVoices)
    }
    @Test fun underloadedAudioRestoresBinauralQualityGradually() {
        val quality=AudioQualityGovernor()
        repeat(4){quality.record(quality.budgetNanos)}
        assertEquals(0,quality.measuredVoices)
        repeat(201){quality.record(0)}
        assertEquals(1,quality.measuredVoices)
        repeat(201){quality.record(0)}
        assertEquals(2,quality.measuredVoices)
    }
    @Test fun scatteredFastFramesDoNotPrematurelyRaiseCpuBudget() {
        val quality=AudioQualityGovernor()
        quality.record(quality.budgetNanos)
        repeat(199){quality.record(0)}
        quality.record((quality.budgetNanos*.5).toLong())
        repeat(100){quality.record(0)}
        assertEquals(3,quality.measuredVoices)
    }
}
