package com.night.endless.engine.render

import kotlin.math.floor
import kotlin.math.hypot

/**
 * A compact, seamless four-channel ground micro-material. This is synthetic
 * regolith, not falsely labelled rover photography. Generating a deterministic
 * 256px texture once per GL context costs no downloaded assets and replaces
 * expensive per-fragment hash octaves with hardware-filtered mipmaps.
 *
 * R: fine albedo, G: micro-relief, B: mineral inclusion, A: roughness.
 * The GPU repeats two differently rotated/frequency-scaled samples: a ~9 m
 * structure pass and a ~2 m grain pass. No high-frequency mip shimmering.
 */
internal object SurfaceMicroMaterial {
    const val SIDE = 256
    const val CHANNELS = 4

    private fun hash(x: Int, z: Int, salt: Int): Double {
        var k = (x * 374761393) xor (z * 668265263) xor salt
        k = (k xor (k ushr 13)) * 1274126177
        return ((k xor (k ushr 16)).toLong() and 0xffffffffL).toDouble() / 4294967295.0
    }

    private fun noise(x: Double, z: Double, cell: Int, salt: Int): Double {
        val gx = floor(x / cell).toInt()
        val gz = floor(z / cell).toInt()
        val u = x / cell - gx
        val v = z / cell - gz
        val sx = u * u * (3.0 - 2.0 * u)
        val sz = v * v * (3.0 - 2.0 * v)
        // Power-of-two cell periods wrap exactly at the 256px tile edge.
        val count = SIDE / cell
        fun sample(ix: Int, iz: Int) = hash(ix and (count - 1), iz and (count - 1), salt)
        val a = sample(gx, gz) * (1.0 - sx) + sample(gx + 1, gz) * sx
        val b = sample(gx, gz + 1) * (1.0 - sx) + sample(gx + 1, gz + 1) * sx
        return a * (1.0 - sz) + b * sz
    }

    private fun clast(x: Int, z: Int): Double {
        // Soft, sparse tiny stones (not a field of identically sized boulders).
        // Distance wraps at tile boundaries, so the atlas remains seamless.
        val cx = x / 16
        val cz = z / 16
        var coverage = 0.0
        for (dz in -1..1) for (dx in -1..1) {
            val tx = (cx + dx) and 15
            val tz = (cz + dz) and 15
            if (hash(tx, tz, 71) < .51) continue
            val px = cx * 16 + dx * 16 + 3.0 + hash(tx, tz, 43) * 10.0
            val pz = cz * 16 + dz * 16 + 3.0 + hash(tx, tz, 97) * 10.0
            val radius = 1.0 + hash(tx, tz, 19) * 2.8
            val d = hypot(x - px, z - pz)
            if (d < radius) coverage = maxOf(coverage, 1.0 - d / radius)
        }
        return coverage
    }

    private fun byteOf(value: Double) =
        (value.coerceIn(0.0, 1.0) * 255.0 + .5).toInt().toByte()

    fun pixels(): ByteArray {
        val result = ByteArray(SIDE * SIDE * CHANNELS)
        var at = 0
        for (z in 0 until SIDE) for (x in 0 until SIDE) {
            val px = x.toDouble()
            val pz = z.toDouble()
            val large = noise(px, pz, 64, 11)
            val medium = noise(px, pz, 16, 23)
            val fine = noise(px, pz, 4, 31)
            val mineral = noise(px, pz, 8, 59)
            val stone = clast(x, z)
            val albedo = .25 + .45 * (.43 * large + .36 * medium + .21 * fine) - .14 * stone
            val height = .15 + .70 * (.25 * large + .30 * medium + .45 * fine) + .13 * stone
            val inclusion = .12 + .67 * mineral + .18 * stone
            val roughness = .69 + .24 * medium - .16 * stone
            result[at++] = byteOf(albedo)
            result[at++] = byteOf(height)
            result[at++] = byteOf(inclusion)
            result[at++] = byteOf(roughness)
        }
        return result
    }
}
