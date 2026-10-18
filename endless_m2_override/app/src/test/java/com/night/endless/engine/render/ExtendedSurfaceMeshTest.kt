package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The new worlds are illustrative, NOT spacecraft DEMs.
 * Preserve deterministic geometry and safety of player/camera collision.
 */
class ExtendedSurfaceMeshTest {
    @Test fun solidWorldSetExcludesGasGiantsAndVenusCloudTops() {
        for (body in listOf("mercury", "ceres", "europa", "titan"))
            assertTrue(ExtendedSurfaceMesh.isSupported(body))
        for (body in listOf("jupiter", "saturn", "neptune", "venus", "sun"))
            assertFalse(ExtendedSurfaceMesh.isSupported(body))
    }

    @Test fun eachWorldBuildsFiniteIndexedTerrainWithDistinctGeology() {
        val terrainHashes = mutableSetOf<Float>()
        for (body in listOf("mercury", "ceres", "europa", "titan")) {
            val mesh = ExtendedSurfaceMesh.build(body)
            assertEquals(161, mesh.side)
            assertEquals(161 * 161 * 9, mesh.vertices.size)
            assertEquals(160 * 160 * 6, mesh.indices.size)
            assertTrue(mesh.vertices.all { it.isFinite() })
            assertTrue(mesh.indices.all { (it.toInt() and 0xffff) < 161 * 161 })
            terrainHashes += mesh.vertices[7 * 9 + 1]
            // The camera and rocks must sample the triangles that actually
            // appear on the GPU; simple analytic samples can cross slopes.
            val atGridCenter = mesh.vertices[(80 * 161 + 80) * 9 + 1]
            assertEquals(atGridCenter.toDouble(),
                ExtendedSurfaceMesh.renderedHeightAt(body, 0.0, 0.0), 1e-6)
            assertTrue(abs(ExtendedSurfaceMesh.renderedHeightAt(body, 3.0, 1.1)) < .6)
        }
        assertTrue(terrainHashes.size > 1)
    }

    @Test fun landingCollisionHasContinuousFiniteHeight() {
        for (body in listOf("mercury", "ceres", "europa", "titan")) {
            for (z in -5..5) for (x in -5..5) {
                val worldX = x*.117
                val worldZ = z*.133
                val h = ExtendedSurfaceMesh.renderedHeightAt(body,worldX,worldZ)
                val next = ExtendedSurfaceMesh.renderedHeightAt(body,worldX+.0001,worldZ)
                assertTrue(h.isFinite() && next.isFinite())
                assertTrue(abs(next-h) < .1)
            }
        }
    }
}
