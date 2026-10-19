package com.night.echospace.audio

import java.io.InputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

/**
 * Real measured MIT KEMAR impulse responses, 44.1kHz, horizontal plane.
 * Gardner & Martin (MIT Media Lab, 1994). Redistribution requires citation.
 * Seven measured azimuths (0, 30, 60, 90, 120, 150, 180°) are INTERPOLATED; not personalized.
 * No head tracking, no claim of vertical elevation localization.
 */
class MeasuredHrtf private constructor(private val filters: Array<Array<FloatArray>>) {
    fun kernels(x: Double, y: Double, left: FloatArray, right: FloatArray) {
        val absolute = abs(atan2(x, -y) * 180.0 / PI).coerceIn(0.0, 180.0)
        val step = 180.0 / (filters.size - 1).coerceAtLeast(1)
        val bracket = (absolute / step).toInt().coerceIn(0, filters.size - 2)
        val t = ((absolute - bracket * step) / step).toFloat().coerceIn(0f, 1f)
        val a = filters[bracket]
        val b = filters[bracket + 1]
        val swap = x < 0.0
        for (i in left.indices) {
            val l = (a[0][i] * (1f-t) + b[0][i] * t)
            val r = (a[1][i] * (1f-t) + b[1][i] * t)
            left[i] = if (swap) r else l
            right[i] = if (swap) l else r
        }
    }

    companion object {
        const val TAPS = 96 // leading taps contain ITD/pinna cues; truncate tail to save Android CPU
        fun read(stream: InputStream): MeasuredHrtf {
            val bytes = stream.use { it.readBytes() }
            val signature = String(bytes, 0, 4)
            val positions = when (signature) {
                "EHR2" -> 7
                "EHR1" -> 3
                else -> throw IllegalArgumentException("Unrecognized MIT KEMAR HRIR")
            }
            require(bytes.size == 4 + positions*2*128*2) {
                "Corrupt MIT KEMAR HRIR asset"
            }
            var pos = 4
            val table = Array(positions) { Array(2) { FloatArray(TAPS) } }
            for (direction in 0 until positions) for (ear in 0 until 2) {
                for (sample in 0 until 128) {
                    val lo = bytes[pos++].toInt() and 255
                    val hi = bytes[pos++].toInt()
                    val value = ((hi shl 8) or lo).toShort().toInt()
                    if (sample < TAPS) table[direction][ear][sample] = value / 32768f
                }
            }
            return MeasuredHrtf(table)
        }
    }
}

/** A lightweight FIR spatializer per moving source. No native libraries or network. */
class HrtfVoice(private val bank: MeasuredHrtf) {
    private val ring = FloatArray(MeasuredHrtf.TAPS)
    private val left = FloatArray(MeasuredHrtf.TAPS)
    private val right = FloatArray(MeasuredHrtf.TAPS)
    private val targetL = FloatArray(MeasuredHrtf.TAPS)
    private val targetR = FloatArray(MeasuredHrtf.TAPS)
    private var initialized = false
    private var pos = 0

    // Called at block rate, not per sample; 0.4 coefficient interpolation is smooth and cheap.
    fun direction(x: Double, y: Double) {
        bank.kernels(x,y,targetL,targetR)
        val easing = if (initialized) 0.36f else 1f
        for (i in left.indices) {
            left[i] += (targetL[i] - left[i]) * easing
            right[i] += (targetR[i] - right[i]) * easing
        }
        initialized = true
    }

    var outputL = 0.0
        private set
    var outputR = 0.0
        private set
    fun sample(input: Double) {
        ring[pos] = input.toFloat()
        var l = 0.0
        var r = 0.0
        var n = pos
        for (k in ring.indices) {
            val s = ring[n]
            l += s * left[k]
            r += s * right[k]
            n--
            if (n < 0) n = ring.size-1
        }
        pos++
        if (pos == ring.size) pos=0
        outputL = l
        outputR = r
    }
}
