#!/usr/bin/env python3
"""Upgrade actual EchoSpace Android audio source after archive extraction.

Executed by both Android QA and signed ARM64 build, after UI v0.6/v0.7 patches.
Never patches audio from third-party binaries and fails closed if source drifts.
"""
from pathlib import Path
import re

ROOT=Path("EchoSpace")
AUDIO=ROOT/"app/src/main/java/com/nexa/echospace/audio"
MODEL=ROOT/"app/src/main/java/com/nexa/echospace/model/Scene.kt"
PREP=ROOT/"tools/prepare_audio.py"
TEST=ROOT/"app/src/test/java/com/nexa/echospace/audio/SpatialImprovementTest.kt"
GRADLE=ROOT/"app/build.gradle.kts"

def swap(path, old, new, label):
    data=path.read_text()
    if data.count(old)!=1:
        raise RuntimeError(f"{path.name}: {label}, expected unique anchor, found {data.count(old)}")
    path.write_text(data.replace(old,new,1))

# Two additional independently verified CC0 immersive environments, plus
# useful procedural noise colors and a fan which never repeat short clips.
swap(MODEL,
    '    SLEET("Sleet", "❄", "Winter storm")',
    '''    SLEET("Sleet", "❄", "Winter storm"),
    CAFE("Café", "☕", "Distant café ambience"),
    TRAIN("Train", "🚆", "Gentle railway motion"),
    FAN("Fan", "◉", "Steady air circulation"),
    WHITE_NOISE("White noise", "◉", "Wideband hiss"),
    PINK_NOISE("Pink noise", "◉", "Warm balanced noise"),
    BROWN_NOISE("Brown noise", "◉", "Deep low-frequency noise")''',
    "six more sound kinds")

swap(PREP,
    " 'sleet':      ('Ambient-X',677545),\n",
    """ 'sleet':      ('Ambient-X',677545),
 'cafe':       ('aidansamuel',540299),
 'train':      ('marion3825',510822),
""",
    "CC0 cafe/train recordings")
swap(PREP,
    "            duration=12 if kind in EVENTS else 24",
    "            duration=20 if kind in EVENTS else 48",
    "longer source field recordings")
swap(PREP,
    "                            filename=dest.name,bytes=dest.stat().st_size))",
    """                            filename=dest.name,bytes=dest.stat().st_size,
                            seconds=round(dest.stat().st_size / 44100.0, 2)))""",
    "record output duration for provenance")
swap(PREP,
    "    targets=['H0e000a.wav','H0e090a.wav','H0e180a.wav']",
    """    # Seven actual measured azimuths, rather than three for the entire 180° arc.
    targets=['H0e000a.wav','H0e030a.wav','H0e060a.wav','H0e090a.wav',
             'H0e120a.wav','H0e150a.wav','H0e180a.wav']""",
    "seven-position measured HRTF")
swap(PREP,
    "    output=bytearray(b'EHR1')",
    "    output=bytearray(b'EHR2')",
    "new HRTF asset format")

M=AUDIO/"MeasuredHrtf.kt"
swap(M," * Three measured positions (front, side, back) are INTERPOLATED, not personalized.",
       " * Seven measured azimuths (0, 30, 60, 90, 120, 150, 180°) are INTERPOLATED; not personalized.",
       "HRTF doc")
swap(M,
    """        val bracket = if (absolute < 90.0) 0 else 1
        val t = ((absolute - bracket * 90.0) / 90.0).toFloat().coerceIn(0f, 1f)""",
    """        val step = 180.0 / (filters.size - 1).coerceAtLeast(1)
        val bracket = (absolute / step).toInt().coerceIn(0, filters.size - 2)
        val t = ((absolute - bracket * step) / step).toFloat().coerceIn(0f, 1f)""",
    "30 degree HRTF interpolation")
swap(M,
    """            require(bytes.size == 4 + 3*2*128*2 && String(bytes, 0, 4) == "EHR1") {
                "Corrupt MIT KEMAR HRIR asset"
            }
            var pos = 4
            val table = Array(3) { Array(2) { FloatArray(TAPS) } }
            for (direction in 0 until 3) for (ear in 0 until 2) {""",
    """            val signature = String(bytes, 0, 4)
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
            for (direction in 0 until positions) for (ear in 0 until 2) {""",
    "HRTF reader handles seven positions")

S=AUDIO/"SpatialSynth.kt"
swap(S,
    """        var clipPhase = if (clip == null || clip.isEmpty()) 0.0 else
            (abs(seed % 31).toDouble() / 37.0) * (clip.size - 1)
""",
    """        private var clipPhase = if (clip == null || clip.isEmpty()) 0.0 else
            (abs(seed % 31).toDouble() / 37.0) * (clip.size - 1)
        // Choose a new, nonidentical entry on each loop; overlap at the seam.
        private var nextLoopStart = if (clip == null || clip.isEmpty()) 0 else
            ((abs(seed % 13).toDouble() / 47.0) * clip.size).toInt()
        private var loopSpeed = 0.99 + (abs(seed % 29).toDouble() / 29.0) * 0.02
        private var brown = 0.0
        private var pink = 0.0
        private var fanPhase = 0.0
""",
    "voice clip phase and randomized playback")
swap(S,
    """                val crossfade = min(clip.size/4, 16538)
                val span = max(1, clip.size - crossfade)
                val n = clipPhase.toInt().coerceIn(0, clip.size-2)
                val frac = (clipPhase-n).toFloat()
                val main = clip[n] * (1f-frac) + clip[n+1] * frac
                val sample = if (clipPhase >= span) {
                    val fade = ((clipPhase - span)/crossfade).coerceIn(0.0,1.0)
                    val next = (clipPhase-span).toInt().coerceIn(0,clip.size-2)
                    main * (1.0-fade) + clip[next] * fade
                } else main.toDouble()
                clipPhase += 22050.0/sampleRate
                if (clipPhase >= clip.size) clipPhase -= span
                return sample * movement""",
    """                // Extended 48s recordings, with randomized overlap starts
                // so recognisable birds/chimes/events don't loop at fixed intervals.
                val crossfade = min(clip.size / 5, 19845) // up to 900 ms @22.05k
                val fadeStart = clip.size - crossfade
                val n = clipPhase.toInt().coerceIn(0, clip.size - 2)
                val frac = clipPhase - n
                val main = clip[n] * (1.0 - frac) + clip[n + 1] * frac
                val sample = if (clipPhase >= fadeStart) {
                    val fade = ((clipPhase - fadeStart) / crossfade).coerceIn(0.0, 1.0)
                    val nextPhase = (nextLoopStart + clipPhase - fadeStart)
                        .coerceIn(0.0, (clip.size - 2).toDouble())
                    val j = nextPhase.toInt()
                    val f = nextPhase - j
                    val entering = clip[j] * (1.0 - f) + clip[j + 1] * f
                    // Equal-power crossfade keeps ambience stable across seams.
                    main * cos(fade * PI / 2.0) + entering * sin(fade * PI / 2.0)
                } else main
                clipPhase += 22050.0 / sampleRate * loopSpeed
                if (clipPhase >= clip.size) {
                    clipPhase = nextLoopStart + crossfade + (clipPhase - clip.size)
                    // Local, reproducible pseudo-random jitter per unique voice.
                    val choice = abs(noise())
                    nextLoopStart = (choice * (clip.size - crossfade - 2) * 0.32).toInt()
                    loopSpeed = 0.987 + abs(noise()) * 0.026
                }
                return sample * movement""",
    "seamlessly varied randomized clip loops")
swap(S,
    """                SoundKind.WATERFALL, SoundKind.LEAVES, SoundKind.OWL, SoundKind.FROGS,
                SoundKind.TRAFFIC, SoundKind.DRIZZLE, SoundKind.SLEET -> {""",
    """                SoundKind.WATERFALL, SoundKind.LEAVES, SoundKind.OWL, SoundKind.FROGS,
                SoundKind.TRAFFIC, SoundKind.DRIZZLE, SoundKind.SLEET,
                SoundKind.CAFE, SoundKind.TRAIN -> {""",
    "fallback for newly bundled field recordings")
swap(S,
    """                SoundKind.STREAM -> {""",
    """                SoundKind.WHITE_NOISE -> n * 0.21
                SoundKind.PINK_NOISE -> {
                    pink += (n - pink) * 0.055
                    low += (n - low) * 0.006
                    (pink * 0.9 + low * 1.1 + n * 0.08) * 0.67
                }
                SoundKind.BROWN_NOISE -> {
                    brown = (brown * 0.992 + n * 0.013).coerceIn(-1.0, 1.0)
                    brown * 0.63
                }
                SoundKind.FAN -> {
                    low += (n - low) * 0.025
                    fanPhase += twoPi * 63.0 / sampleRate
                    if (fanPhase >= twoPi) fanPhase -= twoPi
                    (low * 1.15 + sin(fanPhase) * 0.14 + n * 0.025) * 0.64
                }
                SoundKind.STREAM -> {""",
    "new never-repeating synthetic ambience")

# A distinct unit regression test for the fidelity, volume and directional upgrade.
TEST.write_text(r'''package com.nexa.echospace.audio

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
''')

g=GRADLE.read_text()
assert 'versionCode = 9' in g and 'versionName = "0.7.0"' in g
GRADLE.write_text(g.replace('versionCode = 9','versionCode = 10').replace('versionName = "0.7.0"','versionName = "0.8.0"'))
print("ECHOSPACE_AUDIO_UPGRADE_APPLIED six sounds, 7 HRTF azimuths, 48s loops, v0.8.0",flush=True)
