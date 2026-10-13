package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class SurfaceMidfieldMeshTest {
    @Test fun finitePlanetAnchoredTrianglesFitUnsignedShortIndices() {
        val base: (Double, Double) -> Double = { x, z ->
            0.15 + 0.013 * x - 0.008 * z
        }
        val mesh = SurfaceMidfieldMesh.build(1.5, -0.5, base)
        assertEquals(SurfaceMidfieldMesh.SIDE * SurfaceMidfieldMesh.SIDE * 9,
            mesh.vertices.size)
        assertEquals((SurfaceMidfieldMesh.SIDE - 1) *
            (SurfaceMidfieldMesh.SIDE - 1) * 6, mesh.indices.size)
        assertTrue(mesh.vertices.all { it.isFinite() })
        assertTrue(mesh.indices.all { it.toInt() and 0xffff <
            SurfaceMidfieldMesh.SIDE * SurfaceMidfieldMesh.SIDE })
    }

    @Test fun generatedRingBlendsToCoarseOnEdgesAndNearEye() {
        val base: (Double, Double) -> Double = { x, z ->
            0.32 + 0.012 * x - 0.011 * z
        }
        val mesh = SurfaceMidfieldMesh.build(0.0, 0.0, base)
        val side = SurfaceMidfieldMesh.SIDE
        val index = { x: Int, z: Int -> (z * side + x) * 9 }
        for ((x, z) in listOf(0 to 0, 0 to 80, 160 to 80, 80 to 0, 80 to 160, 80 to 80)) {
            val p = index(x, z)
            assertEquals("Ring must meet original NASA terrain without a lip",
                base(mesh.vertices[p].toDouble(), mesh.vertices[p+2].toDouble()),
                mesh.vertices[p+1].toDouble(), 0.000001)
        }
        val inland = index(110, 96)
        assertTrue("Synthetic geology must remain well under 2m height",
            abs(mesh.vertices[inland+1] - base(
                mesh.vertices[inland].toDouble(),
                mesh.vertices[inland+2].toDouble())) < .00060)
    }

    @Test fun middayRingIsAlwaysDeterministic() {
        val b: (Double, Double) -> Double = { x, z -> 0.003 * sin(x+z) }
        val first = SurfaceMidfieldMesh.build(.22, -.34, b)
        val second = SurfaceMidfieldMesh.build(.22, -.34, b)
        assertTrue(first.vertices.contentEquals(second.vertices))
        assertTrue(first.indices.contentEquals(second.indices))
    }
}
