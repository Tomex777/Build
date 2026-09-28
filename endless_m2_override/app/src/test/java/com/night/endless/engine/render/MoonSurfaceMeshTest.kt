package com.night.endless.engine.render

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonSurfaceMeshTest {
    @Test
    fun defaultMeshIsFiniteNormalizedAndUpwardWound() {
        val mesh = MoonSurfaceMeshGenerator.build()
        val stride = MoonSurfaceMeshGenerator.STRIDE_FLOATS
        val vertexCount = mesh.side * mesh.side

        assertEquals(vertexCount * stride, mesh.vertices.size)
        assertEquals((mesh.side - 1) * (mesh.side - 1) * 6, mesh.indices.size)

        var minHeight = Float.POSITIVE_INFINITY
        var maxHeight = Float.NEGATIVE_INFINITY
        for (vertex in 0 until vertexCount) {
            val base = vertex * stride
            for (component in 0 until stride) {
                assertTrue("non-finite vertex component at $vertex/$component", mesh.vertices[base + component].isFinite())
            }

            val h = mesh.vertices[base + 1]
            minHeight = minOf(minHeight, h)
            maxHeight = maxOf(maxHeight, h)

            val nx = mesh.vertices[base + 3]
            val ny = mesh.vertices[base + 4]
            val nz = mesh.vertices[base + 5]
            val normalLength = sqrt(nx * nx + ny * ny + nz * nz)
            assertTrue("normal length drifted at vertex $vertex: $normalLength", abs(normalLength - 1f) < 0.002f)
            assertTrue("terrain normal points below the surface at vertex $vertex", ny > 0f)

            for (component in 6..8) {
                val color = mesh.vertices[base + component]
                assertTrue("lunar color escaped normalized range at $vertex/$component: $color", color in 0f..1f)
            }
        }
        assertTrue("lunar terrain lost meaningful relief", maxHeight - minHeight > 0.10f)

        mesh.indices.forEachIndexed { index, encoded ->
            val vertex = encoded.toInt() and 0xffff
            assertTrue("index $index is outside vertex bounds: $vertex", vertex in 0 until vertexCount)
        }

        for (triangle in mesh.indices.indices step 3) {
            val ia = (mesh.indices[triangle].toInt() and 0xffff) * stride
            val ib = (mesh.indices[triangle + 1].toInt() and 0xffff) * stride
            val ic = (mesh.indices[triangle + 2].toInt() and 0xffff) * stride

            val ax = mesh.vertices[ia]
            val ay = mesh.vertices[ia + 1]
            val az = mesh.vertices[ia + 2]
            val ux = mesh.vertices[ib] - ax
            val uy = mesh.vertices[ib + 1] - ay
            val uz = mesh.vertices[ib + 2] - az
            val vx = mesh.vertices[ic] - ax
            val vy = mesh.vertices[ic + 1] - ay
            val vz = mesh.vertices[ic + 2] - az

            val crossX = uy * vz - uz * vy
            val crossY = uz * vx - ux * vz
            val crossZ = ux * vy - uy * vx
            val areaSquared = crossX * crossX + crossY * crossY + crossZ * crossZ
            assertTrue("degenerate lunar triangle at index $triangle", areaSquared > 1e-10f)
            assertTrue("lunar triangle winding flipped at index $triangle: crossY=$crossY", crossY > 0f)
        }
    }

    @Test
    fun heightFieldIsFiniteAndDeterministic() {
        val samples = listOf(
            -8.0 to -8.0,
            -2.8 to 1.7,
            0.0 to 0.0,
            1.5 to -0.5,
            2.3 to -2.1,
            8.0 to 8.0
        )

        for ((x, z) in samples) {
            val first = MoonSurfaceMeshGenerator.heightAt(x, z)
            val second = MoonSurfaceMeshGenerator.heightAt(x, z)
            assertTrue("heightAt($x, $z) returned a non-finite value", first.isFinite())
            assertEquals("heightAt($x, $z) is not deterministic", first, second, 0.0)
        }
    }
}
