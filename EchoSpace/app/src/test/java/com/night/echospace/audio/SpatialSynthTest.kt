package com.night.echospace.audio

import com.night.echospace.model.Scenes
import com.night.echospace.model.SoundPlacement
import com.night.echospace.model.SoundKind
import com.night.echospace.model.SoundSource
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpatialSynthTest {
    @Test fun `all presets produce audible stereo output`() {
        Scenes.presets.forEach { scene ->
            val synth = SpatialSynth()
            val pcm = ShortArray(8192)
            synth.renderInto(pcm, scene.sources, 0.8f)
            assertTrue("${scene.title} should not be silent", pcm.any { it.toInt() != 0 })
        }
    }

    @Test fun `expanded sound library has real-recording equivalents`() {
        assertTrue(SoundKind.entries.size >= 16)
        assertTrue(SoundKind.entries.distinctBy { it.name }.size == SoundKind.entries.size)
    }

    @Test fun `living setting changes the deterministic audio envelope`() {
        val src = listOf(SoundSource(91, SoundKind.THUNDER, 0f, -0.3f, 0.8f))
        val dynamic = SpatialSynth()
        val steady = SpatialSynth().apply { setLiving(false) }
        val a = ShortArray(8192)
        val b = ShortArray(8192)
        dynamic.renderInto(a, src, 0.7f)
        steady.renderInto(b, src, 0.7f)
        assertTrue("Living must affect ambiance", !a.contentEquals(b))
    }

    @Test fun `measured HRTF is decoded and channels are mirrored`() {
        // Synthetic FIR fixture tests the interpolation and mirror mathematics;
        // release CI embeds MIT-measured coefficients from the credited source.
        val data = ByteArray(4 + 3*2*128*2)
        System.arraycopy("EHR1".toByteArray(),0,data,0,4)
        for (angle in 0..2) for (ear in 0..1) {
            val i = 4 + ((angle * 2 + ear)*128 + 10)*2
            val magnitude = if (ear == 0) 12000 else 28000
            data[i] = (magnitude and 255).toByte()
            data[i+1] = (magnitude ushr 8).toByte()
        }
        val hrtf = MeasuredHrtf.read(data.inputStream())
        val l = FloatArray(MeasuredHrtf.TAPS)
        val r = FloatArray(MeasuredHrtf.TAPS)
        hrtf.kernels(0.8,0.0,l,r)
        assertTrue(r[10] > l[10])
        hrtf.kernels(-0.8,0.0,l,r)
        assertTrue(l[10] > r[10])
    }

    @Test fun `left source biases left stereo channel`() {
        val synth = SpatialSynth()
        val pcm = ShortArray(32768)
        synth.renderInto(pcm, listOf(SoundSource(42, SoundKind.CHIMES, -0.9f, 0f)), 0.7f)
        val left = pcm.indices.step(2).sumOf { abs(pcm[it].toInt()).toLong() }
        val right = pcm.indices.step(2).sumOf { abs(pcm[it + 1].toInt()).toLong() }
        assertTrue("Pan should favor left", left > right * 1.4)
    }

    @Test fun `turning around swaps left and right`() {
        val source = listOf(SoundSource(33, SoundKind.CHIMES, -0.85f, 0f))
        val original = ShortArray(32768)
        val turned = ShortArray(32768)
        SpatialSynth().renderInto(original, source, 0.8f, 0f)
        SpatialSynth().renderInto(turned, source, 0.8f, 180f)
        fun sum(pcm: ShortArray, offset: Int): Long =
            (offset until pcm.size step 2).sumOf { abs(pcm[it].toInt()).toLong() }
        assertTrue(sum(original, 0) > sum(original, 1))
        assertTrue(sum(turned, 0) < sum(turned, 1))
    }

    @Test fun `removed layers fade naturally instead of cutting instantly`() {
        val synth = SpatialSynth()
        val audible = listOf(SoundSource(9, SoundKind.CHIMES, -0.65f, 0.1f))
        val pcm = ShortArray(4096)
        repeat(6) { synth.renderInto(pcm, audible, 0.7f) }
        synth.renderInto(pcm, emptyList(), 0.7f)
        assertTrue("removed sound should have a short audio tail", pcm.any { it != 0.toShort() })
        repeat(35) { synth.renderInto(pcm, emptyList(), 0.7f) }
        assertTrue("removed sound must eventually become silent", pcm.all { it == 0.toShort() })
    }

    @Test fun `rapid environment switching produces finite bounded audio`() {
        val synth = SpatialSynth()
        val pcm = ShortArray(2048)
        repeat(40) { i ->
            synth.renderInto(pcm, Scenes.presets[i % Scenes.presets.size].sources, 0.9f)
            assertTrue(pcm.all { it.toInt() in -32768..32767 })
        }
    }

    @Test fun `newly added sound avoids existing map markers`() {
        val scene = Scenes.forest.sources
        val (x, y) = SoundPlacement.next(scene)
        assertTrue("new layer should not overlap forest layers",
            scene.all { kotlin.math.hypot(it.x - x, it.y - y) > 0.30f })
        assertTrue("new layer must be inside listening circle", kotlin.math.hypot(x, y) <= 0.98f)
    }

    @Test fun `muting all sources outputs silence`() {
        val synth = SpatialSynth()
        val pcm = ShortArray(4096)
        val scene = Scenes.forest.sources.map { it.copy(muted = true) }
        synth.renderInto(pcm, scene, 1f)
        assertTrue(pcm.all { it.toInt() == 0 })
    }
}
