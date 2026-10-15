package com.night.endless.engine.render

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SurfaceMicroMaterialTest {
    @Test fun atlasIsDeterministicAndChannelRich() {
        val first = SurfaceMicroMaterial.pixels()
        val next = SurfaceMicroMaterial.pixels()
        assertEquals(256 * 256 * 4, first.size)
        assertArrayEquals(first, next)
        for (channel in 0..3) {
            val values = (channel until first.size step 4).map { first[it].toInt() and 255 }
            assertTrue("channel $channel has no material variation", values.maxOrNull()!! - values.minOrNull()!! > 16)
        }
    }

    @Test fun textureEdgesWrapWithoutVisiblePeriodicSeams() {
        val bytes = SurfaceMicroMaterial.pixels()
        fun sample(x: Int, z: Int, channel: Int): Int =
            bytes[(z * 256 + x) * 4 + channel].toInt() and 255
        for (channel in 0..3) {
            val seamX = (0 until 256).map { abs(sample(0, it, channel) - sample(255, it, channel)) }.average()
            val seamZ = (0 until 256).map { abs(sample(it, 0, channel) - sample(it, 255, channel)) }.average()
            assertTrue("vertical seam channel $channel = $seamX", seamX < 27.0)
            assertTrue("horizontal seam channel $channel = $seamZ", seamZ < 27.0)
        }
    }
}
