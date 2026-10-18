package com.nexa.echospace.audio

import com.nexa.echospace.model.SoundKind
import com.nexa.echospace.model.SoundSource
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.math.abs

class SpatialImprovementTest {
    @Test fun sevenAzimuthHrtfAssetInterpolatesAndSwapsEars() {
        // Data is a synthetic fixture; production filters use measured KEMAR responses.
        val bytes = java.io.ByteArrayOutputStream()
        bytes.write("EHR2".toByteArray())
        for (i in 0..6) for (ear in 0..1) for (tap in 0 until 128) {
            val value = if (tap == 0) (i * 500 + ear * 120) else 0
            bytes.write(value and 255)
            bytes.write((value ushr 8) and 255)
        }
        val bank = MeasuredHrtf.read(ByteArrayInputStream(bytes.toByteArray()))
        val l=FloatArray(MeasuredHrtf.TAPS)
        val r=FloatArray(MeasuredHrtf.TAPS)
        bank.kernels(1.0, -1.0, l,r)  // 45 degrees = interpolation 30/60
        assertEquals((750f)/32768f,l[0],0.00002f)
        assertEquals((870f)/32768f,r[0],0.00002f)
        bank.kernels(-1.0, -1.0, l,r) // left-right symmetry
        assertEquals((870f)/32768f,l[0],0.00002f)
        assertEquals((750f)/32768f,r[0],0.00002f)
    }

    @Test fun perLayerGainToZeroActuallyRemovesLayerFromOutput() {
        val sampleRate = 44100
        val synth = SpatialSynth(sampleRate)
        val full = listOf(SoundSource(51,SoundKind.WHITE_NOISE,0.6f,-0.5f,1f,false))
        val zero = full.map { it.copy(gain=0f) }
        val out=ShortArray(512*2)
        var energy=0L
        repeat(150) {
            synth.renderInto(out,full,1f)
            energy += out.sumOf { abs(it.toInt()).toLong() }
        }
        assertTrue("Full layer should generate audible samples",energy>1000L)
        // Fade 420ms after mute; after 1+ seconds output should be silent.
        repeat(160) { synth.renderInto(out,zero,1f) }
        assertTrue("Muted layer should be silent", out.all { it==0.toShort() })
    }
}
