package com.night.endless.engine.render

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class WorldGeomorphologyTest {
    @Test fun impactBasinsHaveDepressedFloorsAndIrregularRims() {
        val center = WorldGeomorphology.impactCrater(0.0, 0.0, 0.0, 0.0, 1.0, .10)
        val far = WorldGeomorphology.impactCrater(5.0, 5.0, 0.0, 0.0, 1.0, .10)
        assertTrue("Impact bowl must be a depression", center < -.09)
        assertTrue("Ejecta must vanish away from the crater", abs(far) < .00001)
        val rim = (0..11).map { i ->
            val angle = i * 6.283185307179586 / 12
            WorldGeomorphology.impactCrater(cos(angle), sin(angle), 0.0, 0.0, 1.0, .10)
        }
        assertTrue("Perfect synthetic ring detected",
            rim.maxOrNull()!! - rim.minOrNull()!! > .001)
    }

    @Test fun surfaceDisplacementsStayFiniteAndWithinLandingScale() {
        for (z in -30..30 step 3) for (x in -30..30 step 3) {
            val fx = x / 3.0
            val fz = z / 3.0
            val values = listOf(
                WorldGeomorphology.mercuryScarp(fx, fz),
                WorldGeomorphology.ceresSlump(fx, fz),
                WorldGeomorphology.europaDoubleRidges(fx, fz, 0.1, -0.2),
                WorldGeomorphology.titanDunes(fx, fz, -0.1, 0.2)
            )
            assertTrue("Non-finite geological elevation", values.all { it.isFinite() })
            assertTrue("Unphysical toy-size cliffs or dunes", values.all { abs(it) < .10 })
        }
    }

    @Test fun geometryMovesContinuouslyAsCameraCrossesCellBoundaries() {
        for (i in -35..35) {
            val x = i * 0.07
            val z = i * 0.043
            val dx = .000001
            val changes = listOf(
                abs(WorldGeomorphology.mercuryScarp(x + dx, z) -
                    WorldGeomorphology.mercuryScarp(x, z)),
                abs(WorldGeomorphology.ceresSlump(x + dx, z) -
                    WorldGeomorphology.ceresSlump(x, z)),
                abs(WorldGeomorphology.europaDoubleRidges(x + dx, z, .05, .02) -
                    WorldGeomorphology.europaDoubleRidges(x, z, .05, .02)),
                abs(WorldGeomorphology.titanDunes(x + dx, z, .05, .02) -
                    WorldGeomorphology.titanDunes(x, z, .05, .02))
            )
            assertTrue("Terrain crack/movement between adjacent samples", changes.all { it < .001 })
        }
    }
}
