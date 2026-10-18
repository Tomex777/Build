package com.night.soundatlas

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PcmLevelerTest {
    @Test fun lowLevelNatureAmbienceIsAudibleWithoutClipping() {
        val quiet=ShortArray(10000){if(it%2==0) 100 else -100}
        PcmLeveler.normalize(quiet)
        assertTrue(abs(quiet[0].toInt())>=1000)
        assertTrue(abs(quiet[0].toInt())<=2000)
    }

    @Test fun trafficRecordingIsReducedToComfortableLevel() {
        val loud=ShortArray(10000){if(it%2==0) 25000 else -25000}
        PcmLeveler.normalize(loud)
        assertTrue(abs(loud[0].toInt()) in 900..2300)
    }

    @Test fun silenceStaysSilentAndHotTransientsNeverOverflow() {
        val silence=ShortArray(8000)
        assertSame(silence,PcmLeveler.normalize(silence))
        assertTrue(silence.all{it==0.toShort()})
        val transient=ShortArray(10000){if(it%500==0) Short.MAX_VALUE else 100}
        PcmLeveler.normalize(transient)
        assertTrue(transient.any{it>0})
        assertTrue(transient.maxOf{abs(it.toInt())}<=31130)
    }
}
