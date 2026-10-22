package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FineRegolithReliefTest {
    @Test fun lunarImpactGardeningAndMartianWindRipplesRemainDistinct() {
        var different = 0
        for (i in -100..100) {
            val x = i * .00083
            val z = i * .00067
            if (abs(FineRegolithRelief.moon(x,z) -
                    FineRegolithRelief.mars(x,z)) > .0000001) different++
        }
        assertTrue("Mars dust and lunar pits should not share uniform sine waves",
            different > 100)
    }

    @Test fun nearFieldReliefIsFiniteDeterministicAndDecimetreScale() {
        for (body in listOf("mars", "moon")) {
            for (i in -150..150 step 3) {
                val x = i * .0007
                val z = i * .0011
                val fn: (Double,Double) -> Double =
                    if(body == "mars") FineRegolithRelief::mars else FineRegolithRelief::moon
                val first = fn(x,z)
                assertEquals(first, fn(x,z), 0.0)
                assertTrue("$body generated NaN elevation", first.isFinite())
                assertTrue("$body texture became a toy-sized relief ramp: $first",
                    abs(first) < .000055) // <16.5cm in Endless 3km units
                val neighbor = fn(x + .0000001, z)
                assertTrue("$body heightfield has an abrupt LOD-tearing step",
                    abs(neighbor - first) < .000003)
            }
        }
    }
}
