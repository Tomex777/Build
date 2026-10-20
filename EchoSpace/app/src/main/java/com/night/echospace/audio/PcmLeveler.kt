package com.night.echospace.audio

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Equalizes CC0 recordings once on the background audio worker, before mixing.
 * Original field clips vary enormously in gain (traffic versus wind/leaves).
 * Peak headroom and a 12x ceiling avoid clipping and amplifying microphone hiss.
 * Per-source user gain and natural within-recording dynamics are untouched.
 */
object PcmLeveler {
    fun normalize(samples: ShortArray, targetRms: Double = 0.04): ShortArray {
        require(targetRms in 0.001..0.15)
        if (samples.isEmpty()) return samples
        var sumSquares = 0.0
        var peak = 0.0
        for (sample in samples) {
            val value = abs(sample.toInt()) / 32768.0
            sumSquares += value * value
            if (value > peak) peak = value
        }
        val rms = sqrt(sumSquares / samples.size)
        if (rms < 0.000001 || peak == 0.0) return samples
        val requested = (targetRms / rms).coerceIn(0.02, 12.0)
        val gain = min(requested, 0.95 / peak)
        if (abs(gain - 1.0) < 0.001) return samples
        for (i in samples.indices) {
            samples[i] = (samples[i] * gain).toInt()
                .coerceIn(-32768, 32767).toShort()
        }
        return samples
    }
}
