package com.night.echospace.audio

import com.night.echospace.model.SoundKind
import com.night.echospace.model.SoundSource
import org.junit.Assert.*
import org.junit.Test

class BinauralQualityBudgetTest {
    @Test fun overloadTradesConvolutionForReliableSound() {
        val budget=BinauralQualityBudget()
        assertEquals(3,budget.measuredVoiceLimit)
        repeat(10) {budget.record(budget.bufferBudgetNanos)}
        assertEquals(0,budget.measuredVoiceLimit)
    }
    @Test fun quietCpuRecoversMeasuredDirectionSlowly() {
        val budget=BinauralQualityBudget()
        repeat(3) { budget.record(budget.bufferBudgetNanos) }
        repeat(200) { budget.record(0) }
        assertEquals(1,budget.measuredVoiceLimit)
        repeat(1500) { budget.record(0) }
        assertEquals(4,budget.measuredVoiceLimit)
    }
    @Test fun noPersonalizedHrtfStillProducesDirectionalFallback() {
        val synth=SpatialSynth()
        synth.setMeasuredVoiceLimit(0)
        val pcm=ShortArray(32768)
        synth.renderInto(pcm,listOf(SoundSource(9,SoundKind.CHIMES,-.85f,0f)),.85f)
        val left=(pcm.indices step 2).sumOf{ kotlin.math.abs(pcm[it].toInt()).toLong() }
        val right=(1 until pcm.size step 2).sumOf{ kotlin.math.abs(pcm[it].toInt()).toLong() }
        assertTrue(left>right)
    }
}
