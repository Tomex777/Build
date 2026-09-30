package com.night.endless.engine.scene

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic, lightweight point sets for the scale-transition views.
 * They are structural visualizations, not a replacement for catalog/mission data.
 */
object CosmicPointClouds {
    private class Rng(var state: Long) {
        fun next(): Double {
            state = (state * 1664525L + 1013904223L) and 0xffffffffL
            return state.toDouble() / 0xffffffffL.toDouble()
        }
        fun centered(): Double = next() - 0.5
    }

    fun localStars(count: Int = 720): FloatArray {
        require(count > 0)
        val rng = Rng(0x51A75EEDL)
        val out = FloatArray(count * 3)
        repeat(count) { i ->
            val u = rng.next()
            val v = rng.next()
            val w = rng.next()
            val radius = 3.0 + 15.0 * Math.cbrt(u)
            val z = 2.0 * v - 1.0
            val angle = 2.0 * PI * w
            val q = sqrt((1.0 - z * z).coerceAtLeast(0.0))
            out[i * 3] = (radius * q * cos(angle)).toFloat()
            out[i * 3 + 1] = (radius * z).toFloat()
            out[i * 3 + 2] = (radius * q * sin(angle)).toFloat()
        }
        return out
    }

    fun milkyWay(count: Int = 5_600): FloatArray {
        require(count > 0)
        val rng = Rng(0x6A1A7EEDL)
        val out = FloatArray(count * 3)
        repeat(count) { i ->
            val bulge = i < count / 7
            if (bulge) {
                val radius = 1.0 + 7.0 * Math.cbrt(rng.next())
                val z = rng.centered()
                val angle = rng.next() * 2.0 * PI
                val q = sqrt((1.0 - z * z).coerceAtLeast(0.0))
                out[i * 3] = (radius * q * cos(angle)).toFloat()
                out[i * 3 + 1] = (radius * z * 0.55).toFloat()
                out[i * 3 + 2] = (radius * q * sin(angle)).toFloat()
            } else {
                val arm = i % 4
                val radius = 3.5 + 24.0 * sqrt(rng.next())
                val angle = arm * (PI / 2.0) + radius * 0.32 + rng.centered() * 0.42
                val thickness = (0.20 + radius * 0.012) * rng.centered()
                out[i * 3] = (cos(angle) * radius + rng.centered() * 0.45).toFloat()
                out[i * 3 + 1] = thickness.toFloat()
                out[i * 3 + 2] = (sin(angle) * radius + rng.centered() * 0.45).toFloat()
            }
        }
        return out
    }

    fun observableUniverse(count: Int = 4_200): FloatArray {
        require(count > 0)
        val rng = Rng(0xC05C0B5EL)
        val clusters = Array(24) {
            val z = rng.centered() * 34.0
            val angle = rng.next() * 2.0 * PI
            val radial = 8.0 + rng.next() * 24.0
            doubleArrayOf(cos(angle) * radial, z, sin(angle) * radial)
        }
        val out = FloatArray(count * 3)
        repeat(count) { i ->
            val a = clusters[i % clusters.size]
            val b = clusters[(i * 7 + 5) % clusters.size]
            val along = rng.next()
            val jitter = 1.7 + rng.next() * 1.8
            out[i * 3] = (a[0] + (b[0] - a[0]) * along + rng.centered() * jitter).toFloat()
            out[i * 3 + 1] = (a[1] + (b[1] - a[1]) * along + rng.centered() * jitter).toFloat()
            out[i * 3 + 2] = (a[2] + (b[2] - a[2]) * along + rng.centered() * jitter).toFloat()
        }
        return out
    }
}
