package com.night.endless.engine.render

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** CPU tests: run without GLES or an Android emulator. */
class PlanetaryGrainTreatmentTest {
    private fun atlas(world: String): ByteArray =
        SurfaceMicroMaterial.pixels().also { PlanetaryGrainTreatment.apply(world, it) }

    @Test fun allSixWorldsHaveDeterministicDistinctMaterial() {
        val outputs = PlanetaryGrainTreatment.worlds.associateWith { atlas(it) }
        for ((world, output) in outputs) {
            assertEquals("$world atlas byte count", 256 * 256 * 4, output.size)
            assertArrayEquals("$world changed between runs", output, atlas(world))
        }
        for ((first, a) in outputs) for ((second, b) in outputs) {
            if (first != second) {
                assertFalse("$first and $second incorrectly share one texture",
                    a.contentEquals(b))
            }
        }
    }

    @Test fun mipmappedRepeatedAtlasHasNoStrongEdgeSeams() {
        for (world in PlanetaryGrainTreatment.worlds) {
            val image = atlas(world)
            fun sample(x: Int, z: Int, channel: Int) =
                image[(z * 256 + x) * 4 + channel].toInt() and 255
            for (channel in 0..3) {
                val xSeam = (0 until 256).map { abs(sample(0, it, channel) -
                    sample(255, it, channel)) }.average()
                val zSeam = (0 until 256).map { abs(sample(it, 0, channel) -
                    sample(it, 255, channel)) }.average()
                assertTrue("$world channel $channel left/right seam $xSeam", xSeam < 30.0)
                assertTrue("$world channel $channel top/bottom seam $zSeam", zSeam < 30.0)
            }
        }
    }

    @Test fun allMaterialChannelsCarryNonFlatPhysicalVariations() {
        for (world in PlanetaryGrainTreatment.worlds) {
            val image = atlas(world)
            for (channel in 0..3) {
                var low = 255
                var high = 0
                for (i in channel until image.size step 4) {
                    val value = image[i].toInt() and 255
                    low = minOf(low, value)
                    high = maxOf(high, value)
                }
                assertTrue("$world channel $channel collapsed into a flat tint",
                    high - low > 18)
            }
        }
    }

    @Test fun rejectsUnsupportedWorldsAndMalformedTextures() {
        val base = SurfaceMicroMaterial.pixels()
        try {
            PlanetaryGrainTreatment.apply("jupiter", base)
            throw AssertionError("Should not allow terrain atlas for a gas giant")
        } catch (_: IllegalArgumentException) {
            // Jupiter has no solid-ground walking material.
        }
        try {
            PlanetaryGrainTreatment.apply("mars", ByteArray(42))
            throw AssertionError("Should reject incorrect pixel buffer size")
        } catch (_: IllegalArgumentException) {
        }
    }
}
