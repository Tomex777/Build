package com.night.endless.engine.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geology-dependent centimetre/metre-scale material treatment for the six
 * walkable worlds. Modifies the 256px repeatable RGBA atlas in place:
 * R = fine albedo, G = small-scale relief, B = inclusions, A = roughness.
 *
 * Scientific scope: deterministic ARTIST-INTERPRETED grain, not spacecraft
 * imagery, elemental abundance measurements or georeferenced terrain.
 * Orbital/landscape geology and the real MOLA/LOLA meshes remain independent.
 * Two rotated shader samples repeat over ~9 m and ~2 m; the fine material
 * is mip-filtered before it reaches the GPU.
 */
internal object PlanetaryGrainTreatment {
    private const val SIDE = SurfaceMicroMaterial.SIDE
    private const val TAU = 2.0 * PI
    val worlds = setOf("mars", "moon", "mercury", "ceres", "europa", "titan")

    private fun hash(x: Int, z: Int, salt: Int): Double {
        var h = (x * 374761393) xor (z * 668265263) xor salt
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)).toLong() and 0xffffffffL).toDouble() / 4294967295.0
    }

    /** Smooth periodic value noise. Cell sizes divide 256 exactly. */
    private fun noise(x: Double, z: Double, cell: Int, salt: Int): Double {
        val gx = floor(x / cell).toInt()
        val gz = floor(z / cell).toInt()
        val u = x / cell - gx
        val v = z / cell - gz
        val a = u * u * (3.0 - 2.0 * u)
        val b = v * v * (3.0 - 2.0 * v)
        val mask = SIDE / cell - 1
        fun h(ix: Int, iz: Int) = hash(ix and mask, iz and mask, salt)
        return (1 - b) * ((1 - a) * h(gx, gz) + a * h(gx + 1, gz)) +
            b * ((1 - a) * h(gx, gz + 1) + a * h(gx + 1, gz + 1))
    }

    /**
     * Locally uncommon impact pits with compressed rims rather than a repeated
     * circle sprite. Cells/centres wrap across both texture edges.
     */
    private fun impactPits(x: Int, z: Int, salt: Int): Double {
        val cell = 16
        val cx = x / cell
        val cz = z / cell
        var value = 0.0
        for (iz in cz - 1..cz + 1) for (ix in cx - 1..cx + 1) {
            val sx = ix and 15
            val sz = iz and 15
            if (hash(sx, sz, salt) < 0.69) continue
            val px = ix * cell + 2.0 + hash(sx, sz, salt + 7) * 12.0
            val pz = iz * cell + 2.0 + hash(sx, sz, salt + 19) * 12.0
            val radius = 0.9 + 1.5 * hash(sx, sz, salt + 41)
            val dx = x - px
            val dz = z - pz
            val d2 = dx * dx + dz * dz
            if (d2 > radius * radius * 3.1) continue
            val d = sqrt(d2)
            val bowl = -(1.0 - d2 / (radius * radius)).coerceAtLeast(0.0)
            val rim = (1.0 - abs(d - radius) / (radius * .34)).coerceAtLeast(0.0)
            value += bowl * .65 + rim * .28
        }
        return value.coerceIn(-.9, .5)
    }

    /**
     * Periodic irregular Voronoi micro-fractures. Unlike crossing sine waves
     * this follows nonuniform plate boundaries rather than forming an
     * artificial woven/checkerboard grid on Europa's ice.
     */
    private fun iceCrack(x: Int, z: Int): Double {
        val cell = 32
        // Domain-warp plate boundaries so they are not rigid hexagonal
        // paving patterns. Both warps wrap at exactly the atlas period.
        val wx = x + (noise(x.toDouble(), z.toDouble(), 32, 1201) - .5) * 8.0 +
            (noise(x.toDouble(), z.toDouble(), 8, 1211) - .5) * 1.6
        val wz = z + (noise(x.toDouble(), z.toDouble(), 32, 1223) - .5) * 8.0 +
            (noise(x.toDouble(), z.toDouble(), 8, 1237) - .5) * 1.6
        val cx = floor(wx / cell).toInt()
        val cz = floor(wz / cell).toInt()
        var nearest = Double.POSITIVE_INFINITY
        var runnerUp = Double.POSITIVE_INFINITY
        for (iz in cz - 1..cz + 1) for (ix in cx - 1..cx + 1) {
            val sx = ix and 7
            val sz = iz and 7
            val px = ix * cell + 4.0 + hash(sx, sz, 1181) * 24.0
            val pz = iz * cell + 4.0 + hash(sx, sz, 1187) * 24.0
            val dx = px - wx
            val dz = pz - wz
            val distance = dx * dx + dz * dz
            if (distance < nearest) {
                runnerUp = nearest
                nearest = distance
            } else if (distance < runnerUp) {
                runnerUp = distance
            }
        }
        val gap = sqrt(runnerUp) - sqrt(nearest)
        // <~2 px across; mipmaps soften from a standing eye viewpoint.
        return (1.0 - gap / 2.7).coerceIn(0.0, 1.0)
    }

    private fun read(bytes: ByteArray, i: Int) =
        (bytes[i].toInt() and 255) / 255.0

    private fun write(bytes: ByteArray, i: Int, value: Double) {
        bytes[i] = (value.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt().toByte()
    }

    fun apply(world: String, atlas: ByteArray) {
        require(world in worlds) { "Unsupported ground material: $world" }
        require(atlas.size == SIDE * SIDE * 4) { "RGBA texture has incorrect dimensions" }
        var offset = 0
        for (z in 0 until SIDE) for (x in 0 until SIDE) {
            val a = read(atlas, offset)
            val h = read(atlas, offset + 1)
            val inclusion = read(atlas, offset + 2)
            val rough = read(atlas, offset + 3)
            val fine = noise(x.toDouble(), z.toDouble(), 16, 129)
            val broad = noise(x.toDouble(), z.toDouble(), 64, 311)
            val jitter = (fine - 0.5) * 2.0
            val warp = (broad - .5) * 1.8 + (fine - .5) * .7
            val px = x.toDouble()
            val pz = z.toDouble()
            var albedo = a
            var relief = h
            var mineral = inclusion
            var roughness = rough
            when (world) {
                "mars" -> {
                    // Wind-driven fine dust crosses coarser, darker basaltic
                    // chips; ripples are low-amplitude, not raised square bars.
                    val ripple = sin(TAU * (11.0 * px + 3.0 * pz) / SIDE + warp)
                    val rippleEnvelope = .40 + .55 * broad
                    albedo += .041 * ripple * rippleEnvelope + .042 * jitter
                    relief += .062 * ripple * rippleEnvelope
                    mineral += .060 * (1.0 - broad)
                    roughness += .065 * fine
                }
                "moon" -> {
                    // Fresh ejecta and small micrometeorite pits interrupt
                    // otherwise densely gardened, angular impact regolith.
                    val pits = impactPits(x, z, 701)
                    albedo += .11 * pits + .045 * jitter
                    relief += .21 * pits
                    mineral += .075 * (1.0 - fine)
                    roughness += .045 * (1.0 - broad)
                }
                "mercury" -> {
                    // Widespread impact breccia and sparse dark glassy clasts;
                    // suppress the lunar fine bright rims for charcoal terrain.
                    val pits = impactPits(x, z, 811)
                    albedo += .13 * pits + .085 * jitter - .055
                    relief += .24 * pits + .035 * jitter
                    mineral += .12 * (1.0 - broad)
                    roughness += .04 * (1.0 - fine)
                }
                "ceres" -> {
                    // Dark hydrated fines dominate. Sparse pale grains evoke
                    // salts, but DO NOT imply observed Occator deposits here.
                    // Extremely rare pale inclusions: do not spray bright
                    // Occator-style spots over every few metres of ground.
                    val fleck = ((noise(px, pz, 8, 919) - .84) * 5.0)
                        .coerceIn(0.0, 1.0)
                    albedo += .10 * fleck - .035 + .035 * jitter
                    relief += .075 * jitter + .01 * fleck
                    mineral += .12 * fleck
                    roughness += .038 * broad
                }
                "europa" -> {
                    // Crosscutting micro-fractures with brighter ridge lips,
                    // against smoother water ice. Macroscopic plates/chaos
                    // live in the separate landscape terrain/shader model.
                    val dark = iceCrack(x, z) *
                        (.20 + .66 * broad) * (.55 + .45 * fine)
                    val lip = (1.0 - abs(dark - .20) / .22).coerceIn(0.0, 1.0)
                    albedo += .060 * lip - .16 * dark + .026 * jitter
                    relief += .065 * lip - .082 * dark
                    mineral += .21 * dark
                    roughness -= .11 * (1.0 - dark.coerceIn(0.0, 1.0))
                }
                "titan" -> {
                    // Fine, wind-aligned organic sand ripples and smoother
                    // lee slopes; no Earth-desert yellow sand assumption.
                    val phase = TAU * (13.0 * px + 3.0 * pz) / SIDE +
                        warp * 2.0 + .35 * sin(TAU * (px + 3.0 * pz) / SIDE)
                    val ripple = sin(phase)
                    val lee = (0.5 + .5 * ripple).coerceIn(0.0, 1.0)
                    val envelope = .40 + .52 * broad
                    albedo += .065 * ripple * envelope - .027 * lee + .035 * jitter
                    relief += .100 * ripple * envelope + .02 * cos(phase)
                    mineral += .07 * (1.0 - fine)
                    roughness += .055 * lee
                }
            }
            write(atlas, offset++, albedo)
            write(atlas, offset++, relief)
            write(atlas, offset++, mineral)
            write(atlas, offset++, roughness)
        }
    }
}
