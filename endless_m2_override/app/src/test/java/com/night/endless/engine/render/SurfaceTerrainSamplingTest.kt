package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.cos

class SurfaceTerrainSamplingTest {
    @Test fun cameraHeightMatchesExactlyTheVisibleTriangle() {
        val side = 121
        val step = 0.22f.toDouble()
        val h: (Double, Double) -> Double = { x, z ->
            0.5 * sin(x * 8.0) + 0.25 * cos(z * 17.0)
        }
        val x = 0.017
        val z = -0.093
        val half = (side - 1) / 2.0
        val ix = floor(x / step + half).toInt()
        val iz = floor(z / step + half).toInt()
        val fx = x / step + half - ix
        val fz = z / step + half - iz
        val x0 = (ix - half) * step
        val z0 = (iz - half) * step
        val triangle = if (fx + fz <= 1.0) {
            (1 - fx - fz) * h(x0,z0) + fx * h(x0+step,z0) + fz*h(x0,z0+step)
        } else {
            (fx + fz - 1) * h(x0+step,z0+step) +
                (1-fz)*h(x0+step,z0) + (1-fx)*h(x0,z0+step)
        }
        assertEquals(triangle, SurfaceTerrainSampling.renderedHeight(x,z,side,step,h), 1e-10)
        assertTrue(
            "Using raw (unmeshed) elevation can place camera inside geometry",
            kotlin.math.abs(h(x,z)-triangle) > 0.001
        )
    }

    @Test fun flatSurfaceAndOutsidePatchStayStable() {
        val h: (Double, Double) -> Double = { x, z -> 1.0 + 0.025*x - 0.012*z }
        for (x in listOf(-3.5, -0.09, 0.0, 2.0)) {
            assertEquals(h(x, 0.4), SurfaceTerrainSampling.renderedHeight(
                x, 0.4, 161, 0.17f.toDouble(), h), 1e-6)
        }
        assertEquals(h(100.0, 0.0), SurfaceTerrainSampling.renderedHeight(
            100.0, 0.0, 161, 0.17f.toDouble(), h), 1e-12)
    }
}
