package com.night.endless.engine.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Meter-scale illustrative regolith mesh OVER a coarser, measured NASA DEM.
 *
 * World units = 3 km. The generated perturbations are at most decimeters.
 * Neither these procedurally generated rocks nor the grains are measured.
 * Height samples retain MOLA / LOLA georeferencing and terrain continuity.
 */
internal object SurfaceDetailMesh {
    const val SIDE = 113
    const val STEP = 0.00035f // 1.05 meters between near-field vertices
    const val STRIDE = 9

    data class Mesh(val vertices: FloatArray, val indices: ShortArray)
    data class Rocks(val vertices: FloatArray, val count: Int)

    private fun hash2(a: Int, b: Int): Double {
        var h = (a * 374761393) xor (b * 668265263)
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
    }

    private fun microRelief(x: Double, z: Double): Double {
        // Eroded ripples and compact regolith; <= 0.16 m vertical relief.
        return (0.000030 * sin(x * 6800.0 + sin(z * 1800.0) * 0.32) * cos(z * 5400.0)
                + 0.000018 * sin(x * 14300.0 - z * 8700.0))
    }

    /** Height at a mesh vertex before triangle interpolation. */
    private fun vertexHeightAt(
        x: Double, z: Double,
        centerX: Double, centerZ: Double,
        elevation: (Double, Double) -> Double
    ): Double {
        val extent = (SIDE - 1) * STEP * 0.5
        val dx = kotlin.math.abs(x - centerX)
        val dz = kotlin.math.abs(z - centerZ)
        val blend = ((1.0 - max(dx, dz) / extent) * 7.0).coerceIn(0.0, 1.0)
        return elevation(x, z) + microRelief(x, z) * blend
    }

    /**
     * Sample the actual foreground triangles (a,c,b and b,c,d), including
     * Float height quantization used by the vertex buffer. Sampling the
     * analytic ripple between vertices made small stones hover or sink.
     */
    fun surfaceHeightAt(
        x: Double, z: Double,
        centerX: Double, centerZ: Double,
        elevation: (Double, Double) -> Double
    ): Double {
        val half = (SIDE - 1) * 0.5
        val gridX = (x - centerX) / STEP + half
        val gridZ = (z - centerZ) / STEP + half
        // Rocks extend past the moving detail square: preserve the existing
        // continuous terrain fallback rather than extrapolating edge cells.
        if (gridX < 0.0 || gridZ < 0.0 ||
            gridX > SIDE - 1 || gridZ > SIDE - 1) return elevation(x, z)
        val ix = floor(gridX).toInt().coerceAtMost(SIDE - 2)
        val iz = floor(gridZ).toInt().coerceAtMost(SIDE - 2)
        val fx = gridX - ix
        val fz = gridZ - iz
        fun height(gx: Int, gz: Int): Double = vertexHeightAt(
            centerX + (gx - half) * STEP,
            centerZ + (gz - half) * STEP,
            centerX, centerZ, elevation
        ).toFloat().toDouble()
        val b = height(ix + 1, iz)
        val cc = height(ix, iz + 1)
        return if (fx + fz <= 1.0) {
            height(ix, iz) * (1.0 - fx - fz) + b * fx + cc * fz
        } else {
            b * (1.0 - fz) + cc * (1.0 - fx) +
                height(ix + 1, iz + 1) * (fx + fz - 1.0)
        }
    }

    fun buildPatch(centerX: Double, centerZ: Double, elevation: (Double, Double) -> Double): Mesh {
        val heights = FloatArray(SIDE * SIDE)
        val center = (SIDE - 1) * 0.5
        for (z in 0 until SIDE) for (x in 0 until SIDE) {
            val dx = (x - center) * STEP
            val dz = (z - center) * STEP
            val worldX = centerX + dx
            val worldZ = centerZ + dz
            heights[z * SIDE + x] = vertexHeightAt(
                worldX, worldZ, centerX, centerZ, elevation
            ).toFloat()
        }

        val out = FloatArray(SIDE * SIDE * STRIDE)
        var p = 0
        for (z in 0 until SIDE) for (x in 0 until SIDE) {
            val wx = centerX + (x - center) * STEP
            val wz = centerZ + (z - center) * STEP
            val y = heights[z * SIDE + x]
            val x0 = (x - 1).coerceAtLeast(0)
            val x1 = (x + 1).coerceAtMost(SIDE - 1)
            val z0 = (z - 1).coerceAtLeast(0)
            val z1 = (z + 1).coerceAtMost(SIDE - 1)
            val sx = ((x1 - x0) * STEP).toDouble()
            val sz = ((z1 - z0) * STEP).toDouble()
            val dhdx = (heights[z * SIDE + x1] - heights[z * SIDE + x0]) / sx
            val dhdz = (heights[z1 * SIDE + x] - heights[z0 * SIDE + x]) / sz
            val invLen = (1.0 / sqrt(1.0 + dhdx*dhdx + dhdz*dhdz)).toFloat()
            out[p++] = wx.toFloat(); out[p++] = y; out[p++] = wz.toFloat()
            out[p++] = (-dhdx * invLen).toFloat(); out[p++] = invLen
            out[p++] = (-dhdz * invLen).toFloat()
            out[p++] = .52f; out[p++] = .47f; out[p++] = .41f
        }

        val ids = ShortArray((SIDE - 1) * (SIDE - 1) * 6)
        var k = 0
        for (z in 0 until SIDE - 1) for (x in 0 until SIDE - 1) {
            val a = z * SIDE + x
            val b = a + 1
            val c = a + SIDE
            val d = c + 1
            ids[k++] = a.toShort(); ids[k++] = c.toShort(); ids[k++] = b.toShort()
            ids[k++] = b.toShort(); ids[k++] = c.toShort(); ids[k++] = d.toShort()
        }
        return Mesh(out, ids)
    }

    /**
     * Stable geographic stone placement. Rebuilt only after the viewer moves
     * enough to need another visible patch. Each rock has eight nonuniform
     * triangular facets and a weathered cap: not a flat sprite.
     */
    fun buildRocks(centerX: Double, centerZ: Double, elevation: (Double, Double) -> Double): Rocks {
        val spacing = 0.00155 // one candidate per ~4.65 x 4.65 m
        val radius = 0.023
        val lowX = floor((centerX - radius) / spacing).toInt()
        val highX = floor((centerX + radius) / spacing).toInt()
        val lowZ = floor((centerZ - radius) / spacing).toInt()
        val highZ = floor((centerZ + radius) / spacing).toInt()
        val data = ArrayList<Float>(27000)
        var stones = 0
        fun triVertex(x: Double, y: Double, z: Double, nx: Double, ny: Double, nz: Double, rockTone: Float) {
            data.add(x.toFloat()); data.add(y.toFloat()); data.add(z.toFloat())
            data.add(nx.toFloat()); data.add(ny.toFloat()); data.add(nz.toFloat())
            // One stable reflectance per fragment, not a random tint at
            // every triangle corner (which looked like faceted toy stones).
            data.add(rockTone); data.add(rockTone); data.add(rockTone)
        }
        for (gz in lowZ..highZ) for (gx in lowX..highX) {
            // Real scree is clustered around eroded slopes and outcrops,
            // not placed as a uniformly spaced field of large pebbles.
            val regionX = floor(gx / 8.0).toInt()
            val regionZ = floor(gz / 8.0).toInt()
            val region = hash2(regionX - 19, regionZ + 4)
            val density = 0.14 + 0.58 * region * region
            if (hash2(gx, gz) > density) continue
            val x = (gx + 0.17 + hash2(gx + 11, gz)) * spacing
            val z = (gz + 0.15 + hash2(gx, gz + 23)) * spacing
            if (hypot(x - centerX, z - centerZ) > radius) continue
            val base = elevation(x, z)
            val rockTone = (0.20 + 0.38 * hash2(gx - 57, gz + 91)).toFloat()
            // Heavy-tailed fragment sizes: mostly gravel, with occasional
            // large embedded meter-scale basalt blocks. Prior min radius
            // 25 cm created a cartoon field of repeated boulders.
            val grainSize = hash2(gx + 31, gz - 9)
            val size = (0.000018 + grainSize * grainSize * grainSize *
                grainSize * grainSize * grainSize * grainSize * 0.00043)
            val squish = 0.38 + hash2(gx - 7, gz + 45) * 0.45
             val offset = hash2(gx, gz - 27) * PI * 2.0
            val rockHeight = size * squish
            val tipX = x + cos(offset + 0.4) * size * 0.075
            val tipZ = z + sin(offset + 0.4) * size * 0.075
            data class Point(val x: Double, val y: Double, val z: Double)
            fun point(angleIndex: Int, tier: Int): Point {
                val angle = offset + 2.0 * PI * (angleIndex % 8) / 8.0
                val random = hash2(gx * 17 + (angleIndex % 8), gz)
                val radius = size * (0.71 + 0.35 * random)
                val diameter = when (tier) {
                    0 -> radius
                    1 -> radius * (0.70 + 0.08 * random)
                    else -> radius * (0.25 + 0.07 * random)
                }
                val height = when (tier) {
                    0 -> base + size * 0.018
                    1 -> base + rockHeight * (0.52 + 0.09 * random)
                    else -> base + rockHeight * (0.88 + 0.08 * random)
                }
                return Point(x + cos(angle) * diameter, height, z + sin(angle) * diameter)
            }
            fun triangle(a: Point, b: Point, c: Point) {
                val ux = b.x - a.x; val uy = b.y - a.y; val uz = b.z - a.z
                val vx = c.x - a.x; val vy = c.y - a.y; val vz = c.z - a.z
                var nx = uy * vz - uz * vy
                var ny = uz * vx - ux * vz
                var nz = ux * vy - uy * vx
                val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-12)
                nx /= len; ny /= len; nz /= len
                triVertex(a.x, a.y, a.z, nx, ny, nz, rockTone)
                triVertex(b.x, b.y, b.z, nx, ny, nz, rockTone)
                triVertex(c.x, c.y, c.z, nx, ny, nz, rockTone)
            }
            val top = Point(tipX, base + rockHeight, tipZ)
            for (side in 0 until 8) {
                val lowA = point(side, 0); val lowB = point(side + 1, 0)
                val midA = point(side, 1); val midB = point(side + 1, 1)
                val highA = point(side, 2); val highB = point(side + 1, 2)
                // Faceted eroded boulder: broad shoulders, uneven cut facets,
                // chipped upper ridge; physically raised, not a pyramid decal.
                triangle(lowA, midA, lowB)
                triangle(lowB, midA, midB)
                triangle(midA, highA, midB)
                triangle(midB, highA, highB)
                triangle(highA, top, highB)
            }
            stones++
        }
        return Rocks(data.toFloatArray(), stones)
    }
}
