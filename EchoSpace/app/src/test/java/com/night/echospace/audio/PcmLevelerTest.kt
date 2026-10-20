package com.night.echospace.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class PcmLevelerTest {
    private fun rms(pcm: ShortArray): Double =
        sqrt(pcm.sumOf { val v=it.toDouble()/32768.0; v*v }/pcm.size)

    @Test fun quietAmbienceIsAudibleWithoutUnlimitedNoiseAmplification() {
        val pcm=ShortArray(22050) { 100 }
        PcmLeveler.normalize(pcm)
        assertTrue(pcm[0] > 100)
        assertTrue("12x maximum",pcm[0] <= 1200)
    }

    @Test fun loudTrafficIsLoweredToConsistentRecordingLevel() {
        val pcm=ShortArray(44100) { 20000 }
        PcmLeveler.normalize(pcm)
        assertEquals(0.04,rms(pcm),0.002)
    }

    @Test fun rareTransientNeverClipsWhenNormalizingQuietSounds() {
        val pcm=ShortArray(44100) { if(it==1000) 32767 else 160 }
        PcmLeveler.normalize(pcm)
        assertTrue(pcm.maxOf { abs(it.toInt()) } <= (32768*0.95).toInt())
    }

    @Test fun silenceAndEmptyAudioRemainValid() {
        assertTrue(PcmLeveler.normalize(ShortArray(0)).isEmpty())
        val silence=ShortArray(512)
        PcmLeveler.normalize(silence)
        assertTrue(silence.all { it==0.toShort() })
    }

    @Test fun rejectsDangerousTargetLevels() {
        try {
            PcmLeveler.normalize(shortArrayOf(1000),0.5)
            fail("Unsafe target should not be accepted")
        } catch (_: IllegalArgumentException) { }
    }
}
