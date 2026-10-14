package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SurfaceDetailMeshTest {
    @Test fun localLodPreservesPlanetCoordinatesAndAddsOnlySmallRelief() {
        val elevation: (Double, Double) -> Double = { x, z -> 0.2 + x * 0.012 + z * 0.026 }
        val mesh = SurfaceDetailMesh.buildPatch(0.01, -0.017, elevation)
        assertEquals(SurfaceDetailMesh.SIDE * SurfaceDetailMesh.SIDE * 9, mesh.vertices.size)
        assertEquals(6 * 112 * 112, mesh.indices.size)
        val center = (SurfaceDetailMesh.SIDE * SurfaceDetailMesh.SIDE / 2) * 9
        assertEquals(0.01f, mesh.vertices[center], 1e-6f)
        assertEquals(-0.017f, mesh.vertices[center + 2], 1e-6f)
        assertTrue(abs(mesh.vertices[center + 1] - 0.2f) < 0.001f)
        assertTrue(mesh.vertices.all { it.isFinite() })
    }

    @Test fun rocksAreDeterministicFixedOnThePlanetSurface() {
        val flat: (Double, Double) -> Double = { _, _ -> 0.0 }
        val first = SurfaceDetailMesh.buildRocks(0.0, 0.0, flat)
        val second = SurfaceDetailMesh.buildRocks(0.0, 0.0, flat)
        assertTrue(first.count > 200)
        assertEquals(first.count, second.count)
        assertTrue(first.vertices.contentEquals(second.vertices))
        assertEquals(first.count * 8 * 5 * 3 * 9, first.vertices.size)
        assertTrue(first.vertices.all { it.isFinite() })
    }


    @Test fun surfaceRocksKeepCoherentReflectanceAndMostlyGravelSize() {
        val rocks = SurfaceDetailMesh.buildRocks(0.0, 0.0) { _, _ -> 0.0 }
        val stride = SurfaceDetailMesh.STRIDE
        val verticesPerStone = 8 * 5 * 3
        var gravel = 0
        for (stone in 0 until rocks.count) {
            val first = stone * verticesPerStone * stride
            val tone = rocks.vertices[first + 6]
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            for (v in 0 until verticesPerStone) {
                val p = first + v * stride
                assertEquals("A single basalt stone must not have random corner tint",
                    tone, rocks.vertices[p + 6], 0.000001f)
                minX = minOf(minX, rocks.vertices[p])
                maxX = maxOf(maxX, rocks.vertices[p])
            }
            if (maxX - minX < 0.0002f) gravel++
        }
        assertTrue("Most surface fragments should be sub-30cm gravel",
            gravel > rocks.count / 2)
    }

    @Test fun nearbyBouldersHaveMeterScaleRadiusesRatherThanHundredsOfMeters() {
        val data = SurfaceDetailMesh.buildRocks(0.0, 0.0) { _, _ -> 0.0 }.vertices
        var peak = 0f
        for (i in data.indices step 9) peak = maxOf(peak, data[i + 1])
        assertTrue(peak > 0f)
        assertTrue("Tops should remain under roughly 1.5m high", peak < 0.0006f)
    }
}
