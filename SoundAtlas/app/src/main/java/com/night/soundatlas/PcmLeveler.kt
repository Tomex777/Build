package com.night.soundatlas

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Peak-safe RMS compensation for licensed offline recordings.
 *
 * A distant leaf field recording can be >25 dB quieter than a traffic recording.
 * Normalize once on the audio worker before rendering, never per frame.
 * Quiet recordings have a capped gain to avoid bringing up the noise floor.
 */
object PcmLeveler {
    fun normalize(samples: ShortArray, targetRms: Double = 0.04): ShortArray {
        require(targetRms in 0.001..0.15)
        if(samples.isEmpty())return samples
        var squares=0.0
        var peak=0.0
        for(sample in samples) {
            val a=abs(sample.toInt()) / 32768.0
            squares+=a*a
            if(a>peak)peak=a
        }
        val rms=sqrt(squares/samples.size)
        if(rms<0.000001 || peak==0.0)return samples
        val requested=(targetRms/rms).coerceIn(.02,12.0)
        val gain=min(requested,.95/peak)
        if(abs(gain-1.0)<0.001)return samples
        for(i in samples.indices) {
            samples[i]=(samples[i]*gain).toInt().coerceIn(-32768,32767).toShort()
        }
        return samples
    }
}
