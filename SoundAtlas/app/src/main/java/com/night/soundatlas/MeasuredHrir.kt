package com.night.soundatlas

import java.io.InputStream
import kotlin.math.*

/**
 * Non-personalized measured MIT KEMAR horizontal-plane HRIR filters.
 *
 * Binary EHR2 is authored by the already-used EchoSpace audio preparation
 * pipeline: seven azimuths (0,30,60,90,120,150,180 degrees), each containing
 * L/R 128-tap PCM16 responses. EHR1 supports three legacy measurements.
 * Measurements are only interpolated; this is NOT a personalized HRTF.
 */
class MeasuredHrir private constructor(
    private val responses: Array<Array<FloatArray>>
) {
    companion object {
        const val TAPS=128
        fun read(stream:InputStream):MeasuredHrir {
            val bytes=stream.use{it.readBytes()}
            require(bytes.size>=4){"Missing HRIR header"}
            val signature=String(bytes,0,4,Charsets.US_ASCII)
            val positions=when(signature) {
                "EHR2"->7
                "EHR1"->3
                else->throw IllegalArgumentException("Unknown HRIR asset signature")
            }
            require(bytes.size==4+positions*2*TAPS*2){"Corrupt HRIR asset length"}
            val table=Array(positions){Array(2){FloatArray(TAPS)}}
            var offset=4
            for(position in 0 until positions)for(ear in 0..1)for(tap in 0 until TAPS) {
                val lo=bytes[offset++].toInt() and 255
                val hi=bytes[offset++].toInt() and 255
                table[position][ear][tap]=((hi shl 8) or lo).toShort().toFloat()/32768f
            }
            return MeasuredHrir(table)
        }
    }
    /** 0=front; ±PI/2=left/right; PI=behind. Writes existing arrays, no allocation. */
    fun kernels(azimuthRadians:Float,left:FloatArray,right:FloatArray) {
        require(left.size>=TAPS && right.size>=TAPS)
        val angle=atan2(sin(azimuthRadians),cos(azimuthRadians))
        val degrees=abs(Math.toDegrees(angle.toDouble())).toFloat().coerceIn(0f,180f)
        val step=180f/(responses.size-1)
        val low=(degrees/step).toInt().coerceIn(0,responses.size-2)
        val blend=((degrees-low*step)/step).coerceIn(0f,1f)
        val leftEar=if(angle<0)1 else 0
        val rightEar=1-leftEar
        val base=responses[low]
        val upper=responses[low+1]
        for(i in 0 until TAPS) {
            left[i]=base[leftEar][i]*(1f-blend)+upper[leftEar][i]*blend
            right[i]=base[rightEar][i]*(1f-blend)+upper[rightEar][i]*blend
        }
    }
}
