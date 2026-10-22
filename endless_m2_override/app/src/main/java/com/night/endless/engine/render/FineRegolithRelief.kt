package com.night.endless.engine.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Sub-decimetre, on-foot regolith geometry for measured NASA MOLA/LOLA
 * landscape triangles. These features are procedural *illustrations*.
 * Render units = 3km. The 1.05m moving foreground mesh resolves their
 * wavelengths; they are deliberately absent from coarse orbital DEMs.
 *
 * Invariants: geographic coordinates only, no time/camera dependence;
 * smooth waves/pit lips; amplitude within ±0.16m and unaffected by LOD.
 */
internal object FineRegolithRelief {
    private fun hash(x: Int, z: Int, salt: Int): Double {
        var k = (x * 374761393) xor (z * 668265263) xor salt
        k = (k xor (k ushr 13)) * 1274126177
        return ((k xor (k ushr 16)).toLong() and 0xffffffffL) / 4294967295.0
    }

    /**
     * Small wind-aligned dust ripples with long warped crests and intervening
     * basaltic gravel. MOLA has no ability to resolve any of these features.
     * The fundamental period is >= 10m, safely above foreground grid Nyquist.
     */
    fun mars(x: Double, z: Double): Double {
        val warp = .55 * sin(x * 150.0 + z * 85.0) +
            .22 * sin(x * 260.0 - z * 119.0)
        val phase = x * 860.0 + z * 340.0 + warp
        val broad = .51 + .39 * sin(x * 97.0 - z * 83.0)
        val directional = sin(phase) * (.55 + .15 * sin(phase * .5))
        val sparse = sin(x * 1670.0 - z * 580.0) * cos(z * 710.0)
        return .000024 * broad * directional + .000007 * sparse
    }

    /**
     * Meteoric gardened lunar regolith interrupted by soft, shallow
     * micrometeorite pits. Scattered pit positions are geographically
     * deterministic, with no regularly spaced cones or floating stones.
     */
    fun moon(x: Double, z: Double): Double {
        val cell = .009 // ~27m. One sparse impact candidate per cell.
        val gx = floor(x / cell).toInt()
        val gz = floor(z / cell).toInt()
        var pits = 0.0
        for (iz in gz - 1..gz + 1) for (ix in gx - 1..gx + 1) {
            if (hash(ix, iz, 731) < .47) continue
            val px = (ix + .10 + .80 * hash(ix, iz, 733)) * cell
            val pz = (iz + .10 + .80 * hash(ix, iz, 739)) * cell
            val radius = .00048 + .00048 * hash(ix, iz, 743)
            val d = hypot(x - px, z - pz) / radius
            if (d >= 1.4) continue
            // Small depressed bowl with displaced rims, all slopes smooth.
            val inside = (1 - d * d).coerceAtLeast(0.0)
            val rim = (1 - abs(d - 1.05) / .23).coerceAtLeast(0.0)
            pits += -.000016 * inside * inside + .000005 * rim * rim
        }
        val fines = .000009 * sin(x * 1280.0 + .18 * sin(z * 650.0)) *
            cos(z * 970.0 - x * 230.0)
        return (pits + fines).coerceIn(-.000051, .000051)
    }
}
