package com.night.endless.engine.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot

/**
 * Soft contact-shadow decals for illustrative boulders. These approximate
 * blocked direct sunlight on regolith; they are not a general shadow map.
 * Baked at the local DEM height and stable in planet geographic coordinates.
 */
internal object SurfaceRockShadows {
    const val SEGMENTS = 10
    private const val ROCK_VERTEX_STRIDE = 9
    private const val ROCK_TRIANGLES = 8 * 5
    private const val VERTICES_PER_ROCK = ROCK_TRIANGLES * 3

    fun build(
        rocks: SurfaceDetailMesh.Rocks,
        elevation: (Double, Double) -> Double,
        lightX: Double,
        lightY: Double,
        lightZ: Double
    ): FloatArray {
        require(lightY > 0.01)
        val output = FloatArray(rocks.count * SEGMENTS * 3 * ROCK_VERTEX_STRIDE)
        var cursor = 0
        fun vertex(x: Double, z: Double, alpha: Float) {
            val y = elevation(x, z) + 0.000004 // 1.2 cm above terrain
            output[cursor++] = x.toFloat()
            output[cursor++] = y.toFloat()
            output[cursor++] = z.toFloat()
            output[cursor++] = 0f
            output[cursor++] = 1f
            output[cursor++] = 0f
            output[cursor++] = alpha
            output[cursor++] = 0f
            output[cursor++] = 0f
        }
        for (rock in 0 until rocks.count) {
            val first = rock * VERTICES_PER_ROCK * ROCK_VERTEX_STRIDE
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var minZ = Float.POSITIVE_INFINITY
            var maxZ = Float.NEGATIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (vertex in 0 until VERTICES_PER_ROCK) {
                val p = first + vertex * ROCK_VERTEX_STRIDE
                val x = rocks.vertices[p]
                val y = rocks.vertices[p + 1]
                val z = rocks.vertices[p + 2]
                minX = minOf(minX, x)
                maxX = maxOf(maxX, x)
                minY = minOf(minY, y)
                maxY = maxOf(maxY, y)
                minZ = minOf(minZ, z)
                maxZ = maxOf(maxZ, z)
            }
            val height = (maxY - minY).toDouble().coerceAtLeast(0.0)
            val baseX = (maxX + minX) * .5
            val baseZ = (maxZ + minZ) * .5
            val cx = baseX - lightX / lightY * height * 0.42
            val cz = baseZ - lightZ / lightY * height * 0.42
            // Stretch along the projected solar ray, not the global X/Z
            // axes. Diagonal sunlight previously made a broad axis-aligned
            // oval instead of a shadow pointing away from the Sun.
            val horizontal = hypot(lightX, lightZ)
            val directionX = if (horizontal > 1e-9) -lightX / horizontal else 1.0
            val directionZ = if (horizontal > 1e-9) -lightZ / horizontal else 0.0
            val halfX = ((maxX - minX).toDouble() * 0.64).coerceAtLeast(0.000009)
            val halfZ = ((maxZ - minZ).toDouble() * 0.64).coerceAtLeast(0.000009)
            val along = hypot(halfX * directionX, halfZ * directionZ) +
                horizontal / lightY * height * .6
            val across = hypot(halfX * directionZ, halfZ * directionX)
            fun edgeVertex(angle: Double) {
                val longitudinal = cos(angle) * along
                val transverse = sin(angle) * across
                vertex(cx + directionX * longitudinal - directionZ * transverse,
                    cz + directionZ * longitudinal + directionX * transverse, 0f)
            }
            for (edge in 0 until SEGMENTS) {
                val a0 = edge * 2.0 * PI / SEGMENTS
                val a1 = (edge + 1) * 2.0 * PI / SEGMENTS
                vertex(cx, cz, .32f)
                edgeVertex(a0)
                edgeVertex(a1)
            }
        }
        return output
    }
}
