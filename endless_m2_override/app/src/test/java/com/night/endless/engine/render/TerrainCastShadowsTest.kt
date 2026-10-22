package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Terrain-only direct-sun occlusion. Light is computed from actual triangles. */
class TerrainCastShadowsTest {
    private val side = 33
    private val step = .001f

    private fun mesh(ridgeAtX: Int? = null): FloatArray =
        FloatArray(side * side * 9).apply {
            for (z in 0 until side) for (x in 0 until side) {
                val p = (z * side + x) * 9
                this[p] = x * step
                this[p + 1] = if (x == ridgeAtX) .003f else 0f
                this[p + 2] = z * step
                this[p + 4] = 1f
                this[p + 6] = .52f
            }
        }

    private fun visibility(mesh: FloatArray, x: Int, z: Int) =
        mesh[(z * side + x) * 9 + 6]

    @Test fun flatTerrainCannotCastFakeShadows() {
        val flat = mesh()
        TerrainCastShadows.bake(flat, side, step, 1.0, .10, 0.0)
        for (z in 0 until side) for (x in 0 until side)
            assertEquals(1f, visibility(flat, x, z), 0f)
    }

    @Test fun ridgeCastsBehindSunDirectionNotAhead() {
        val elevated = mesh(ridgeAtX = 17)
        TerrainCastShadows.bake(elevated, side, step, 1.0, .10, 0.0)
        assertTrue("Ridge must block light on its westward lee side",
            visibility(elevated, 14, 16) < .50f)
        assertEquals("Ridge cannot cast against the sunlight ray",
            1f, visibility(elevated, 20, 16), 0f)
    }

    @Test fun shadowsHaveSeamlessEdgesAndKeepAllGeometryUnchanged() {
        val field = mesh(ridgeAtX = 17)
        val geometry = field.copyOf()
        TerrainCastShadows.bake(field, side, step, 1.0, .10, 0.0)
        for (z in 0 until side) for (x in 0 until side) {
            val p = (z * side + x) * 9
            for (i in 0..5) assertEquals(geometry[p+i], field[p+i], 0f)
            assertTrue(field[p+6] in 0.1f..1f)
            if (x == 0 || z == 0 || x == side-1 || z == side-1)
                assertEquals(1f, field[p+6], 0f)
        }
    }

    @Test fun verticalLightAndBadDirectionsDoNotLeakNanIntoColors() {
        for ((sx, sy, sz) in listOf(
            Triple(0.0, 1.0, 0.0),
            Triple(0.0, -1.0, 0.0),
            Triple(Double.NaN, 0.5, 0.0)
        )) {
            val field = mesh(ridgeAtX = 17)
            TerrainCastShadows.bake(field, side, step, sx, sy, sz)
            assertTrue(field.indices.step(9).all { field[it + 6].isFinite() })
        }
    }

    @Test fun identicalTriangleInputsAndSunDirectionsProduceIdenticalShadows() {
        val a = mesh(ridgeAtX = 17)
        val b = a.copyOf()
        TerrainCastShadows.bake(a, side, step, -.74, .38, .31)
        TerrainCastShadows.bake(b, side, step, -.74, .38, .31)
        assertTrue(a.contentEquals(b))
    }
}
