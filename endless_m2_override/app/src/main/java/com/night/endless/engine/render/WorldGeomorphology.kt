package com.night.endless.engine.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Morphology for **illustrative** Mercury/Ceres/Europa/Titan terrain.
 * Coordinates and elevations use Endless's 3-km world units. The functions
 * are continuous, deterministic and independent of camera/LOD, so near,
 * coarse and collision meshes can use the same heightfield.
 *
 * These are geomorphological approximations, NOT georeferenced MESSENGER,
 * Dawn, Galileo or Cassini digital elevation models.
 */
internal object WorldGeomorphology {
    /**
     * Asymmetric, terraced impact basins. A clean bell curve plus perfect
     * circular raised lip is immediately recognizable as procedural terrain.
     * Rim scalloping, incomplete ejecta and floor debris are deliberately
     * subdued: not every impact has identical textbook geometry.
     */
    fun impactCrater(
        x: Double, z: Double, cx: Double, cz: Double,
        radius: Double, depth: Double
    ): Double {
        val dx = x - cx
        val dz = z - cz
        val angle = atan2(dz, dx)
        val roughRim = 1.0 +
            .042 * sin(angle * 7.0 + cx * 2.2) +
            .028 * sin(angle * 13.0 - cz * 1.7) +
            .018 * cos(angle * 19.0 + cx + cz)
        val r = sqrt(dx * dx + dz * dz)
        val normalized = r / (radius * roughRim)
        val bowl = -depth * exp(-normalized * normalized / .48)
        // Subtle overlapping impact-wall slumps and a discontinuous rim.
        val rim = .28 * depth * exp(-((normalized - 1.0) *
            (normalized - 1.0)) / .014)
        val terrace = .058 * depth * exp(-((normalized - .67) *
            (normalized - .67)) / .007) *
            (0.6 + 0.4 * sin(5.0 * angle + radius))
        val rays = (.5 + .5 * sin(17.0 * angle + .7 * cos(angle * 5.0)))
        val rayBed = .038 * depth * rays * exp(-((normalized - 1.6) *
            (normalized - 1.6)) / .43)
        return bowl + rim + terrace + rayBed
    }

    /** Short section of Mercury-like contractional lobate scarp; not a
     * surveyed/placed MESSENGER feature, and only tens of metres in relief. */
    fun mercuryScarp(x: Double, z: Double): Double {
        val bend = .27 * sin(z * .37 + .2 * sin(x * .29))
        val distance = x * .86 + z * .28 + bend - 5.5
        val transition = distance / sqrt(distance * distance + .045)
        val extent = exp(-((z + .5) * (z + .5)) / 90.0)
        return .008 * transition * extent
    }

    /** Downslope debris and ridge-like landslide deposits in Ceres basins. */
    fun ceresSlump(x: Double, z: Double): Double {
        val cx = -3.2
        val cz = 2.3
        val dx = x - cx
        val dz = z - cz
        val r = sqrt(dx * dx + dz * dz)
        val fade = exp(-((r - 1.35) * (r - 1.35)) / 1.55)
        val elongated = sin(3.2 * dx + 1.1 * dz + .7 * sin(.5 * dz))
        val secondary = sin(6.2 * dz - 2.5 * dx)
        return .0065 * fade * (0.6 * elongated + 0.4 * secondary)
    }

    /**
     * Crosscut, paired ice-ridge lips separated by troughs, with variation in
     * spacing and interrupted height. Avoids implausibly regular sine stripes
     * and models narrow ridges as height features (NOT cracks with water).
     */
    fun europaDoubleRidges(x: Double, z: Double, warpX: Double, warpZ: Double): Double {
        fun ridge(phase: Double, strength: Double, faultWidth: Double): Double {
            val f = abs(sin(phase))
            val lipA = exp(-((f - .14) * (f - .14)) / faultWidth)
            val lipB = exp(-((f - .33) * (f - .33)) / (faultWidth * 1.2))
            val trough = exp(-(f * f) / (faultWidth * .55))
            return strength * (.64 * lipA + .36 * lipB - .30 * trough)
        }
        val primaryPhase = (x + warpX) * 2.22 + (z + warpZ) * .49 +
            .28 * sin(x * .61 - z * .47)
        val crossPhase = (z + warpZ) * 2.85 - (x + warpX) * .82 +
            .25 * sin(z * .43 + x * .39)
        // Interrupt some ridges, as happens where chaos fragments intersect.
        val first = (.70 + .30 * sin(x * .43 + z * .19).let { it * it })
        val second = (.66 + .34 * cos(z * .38 - x * .51).let { it * it })
        return first * ridge(primaryPhase, .040, .018) +
            second * ridge(crossPhase, .024, .025)
    }

    /**
     * Asymmetric, gently warped Titan dunes. Dune height envelope varies
     * geographically; waveforms avoid the artificial perfectly symmetric
     * sinusoidal ridge rows. Atmospheric appearance is handled separately.
     */
    fun titanDunes(x: Double, z: Double, warpX: Double, warpZ: Double): Double {
        val px = x + warpX
        val pz = z + warpZ
        val envelope = .65 + .35 * sin(px * .22 - pz * .13).let { it * it }
        val waviness = .43 * sin(px * .60 - pz * .38)
        val phase = px * 2.9 + pz * 5.9 + waviness
        val dune = sin(phase) + .22 * sin(2.0 * phase + .35)
        val secondary = sin(px * 7.8 + pz * 2.7 + .4 * sin(px * .8))
        val transverse = sin(px * 5.9 + pz * 11.7 + .3 * sin(pz * .8))
        return envelope * (.015 * dune + .007 * secondary + .005 * transverse)
    }
}
