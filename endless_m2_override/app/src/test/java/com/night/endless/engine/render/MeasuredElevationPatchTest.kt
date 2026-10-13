package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MeasuredElevationPatchTest {
    @Test fun tileUsesMeasuredHeightSamplesAndBilinearInterpolation() {
        val values = ShortArray(MeasuredElevationPatch.SIDE * MeasuredElevationPatch.SIDE)
        val idx = 80 * MeasuredElevationPatch.SIDE + 80
        values[idx] = 1000
        values[idx + 1] = 2000
        values[idx + MeasuredElevationPatch.SIDE] = 3000
        values[idx + MeasuredElevationPatch.SIDE + 1] = 4000
        val tile = MeasuredElevationPatch.fromSamples(values)
        assertEquals(1.0, requireNotNull(tile.heightAt(0.0, 0.0)), 1e-12)
        assertEquals(2.5, requireNotNull(tile.heightAt(.085, .085)), 1e-9)
        assertNull(tile.heightAt(100.0, 0.0))
    }

    @Test fun corruptTileDimensionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MeasuredElevationPatch.fromSamples(ShortArray(14))
        }
    }
}
