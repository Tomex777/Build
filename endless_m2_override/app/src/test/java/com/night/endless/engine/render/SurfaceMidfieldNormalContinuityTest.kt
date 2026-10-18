package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Surface LOD edge regression: terrain and ice normals must not jump to
 * a visibly different lighting model across the 1.68 km midfield ring.
 */
class SurfaceMidfieldNormalContinuityTest {
    @Test fun outerRingUsesCoarseReferenceNormalsWithoutChangingGeometry() {
        val flat: (Double, Double) -> Double = { _, _ -> 0.0 }
        val slopedReference: (Double, Double) -> Double = { x, _ -> .35 * x }
        val mesh = SurfaceMidfieldMesh.build(0.0, 0.0, flat, slopedReference)
        val stride = SurfaceMidfieldMesh.STRIDE
        fun vertex(x: Int, z: Int, field: Int): Float =
            mesh.vertices[(z * SurfaceMidfieldMesh.SIDE + x) * stride + field]

        // Terrain heights remain the flat triangulated source, even when
        // the lighting normals are deliberately tuned to the coarse ring.
        assertEquals(0f, vertex(160, 80, 1), 0.00001f)
        assertEquals(0f, vertex(80, 80, 1), 0.00001f)
        val theoreticalNx = (-.35 / kotlin.math.sqrt(1.0 + .35*.35)).toFloat()
        assertEquals(theoreticalNx, vertex(160, 80, 3), .02f)
        assertEquals(0f, vertex(80, 80, 3), .002f)
        assertTrue(vertex(146, 80, 3) < vertex(125, 80, 3))
    }
}
