package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanetTextureSizingTest {
    @Test fun smallerGpusDecodeWithoutOversizedUploads() {
        assertEquals(2, PlanetTextureSizing.inSampleSize(4096, 2048, 2048, false))
        assertEquals(2, PlanetTextureSizing.inSampleSize(3072, 1536, 2048, false))
        assertEquals(2, PlanetTextureSizing.inSampleSize(3072, 1536, 4096, true))
    }

    @Test fun wellEquippedGpusKeepObservedDetail() {
        assertEquals(1, PlanetTextureSizing.inSampleSize(3072, 1536, 4096, false))
        assertEquals(1, PlanetTextureSizing.inSampleSize(2048, 1024, 2048, false))
        assertEquals(1, PlanetTextureSizing.inSampleSize(4096, 2048, 8192, false))
    }

    @Test fun unknownHardwareStillUsesMinimumEs3Limit() {
        assertEquals(1, PlanetTextureSizing.inSampleSize(0, 0, 0, false))
        assertEquals(2, PlanetTextureSizing.inSampleSize(4096, 2048, 0, false))
        assertEquals(4, PlanetTextureSizing.inSampleSize(8192, 4096, 2048, false))
    }
}
