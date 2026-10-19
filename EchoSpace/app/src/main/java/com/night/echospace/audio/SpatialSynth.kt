package com.night.echospace.audio

import com.night.echospace.model.SoundKind
import com.night.echospace.model.SoundSource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline field-recording engine, optional measured MIT KEMAR binaural FIR, and
 * procedural fallback for source assets not yet installed. Orientation is manual.
 * HRTF filtering is generic, NOT personalized; no hardware head tracking.
 */
class SpatialSynth(
    private val sampleRate: Int = 44_100,
    private val recordings: Map<SoundKind, ShortArray> = emptyMap(),
    private val measuredHrtf: MeasuredHrtf? = null
) {
    @Volatile private var living = true
    fun setLiving(enabled: Boolean) { living = enabled }
    private val voices = mutableMapOf<Int, Voice>()
    private data class OutgoingVoice(val voice: Voice, val source: SoundSource, var framesRemaining: Int)
    private val outgoing = mutableListOf<OutgoingVoice>()
    private var previousSources: Map<Int, SoundSource> = emptyMap()
    private val crossfadeFrames = (sampleRate * 0.42).toInt()
    private val twoPi = 2.0 * PI
    private fun coFor(degrees: Float) = cos(degrees * PI / 180.0)
    private fun siFor(degrees: Float) = sin(degrees * PI / 180.0)

    fun renderInto(
        out: ShortArray,
        sources: List<SoundSource>,
        masterGain: Float,
        headingDegrees: Float = 0f
    ) {
        require(out.size % 2 == 0) { "Stereo output requires an even buffer length" }
        val active = sources.filterNot { it.muted || it.gain <= 0f }.take(16)
        val upcoming = active.associateBy { it.id }
        // Presets reuse layer IDs. Keep old voices alive briefly when their kind
        // changes, and ramp down removed/muted layers instead of hard cutting.
        for ((id, voice) in voices.toMap()) {
            if (upcoming[id]?.kind != voice.kind) {
                previousSources[id]?.let { outgoing.add(OutgoingVoice(voice, it, crossfadeFrames)) }
                voices.remove(id)
            }
        }
        active.forEach { source ->
            if (voices[source.id] == null) {
                voices[source.id] = Voice(source.kind, source.id * 1_013_904_223L + 1L, recordings[source.kind], measuredHrtf)
            }
        }
        // Calculate target measured HRIR once per audio block (not per sample).
        if (measuredHrtf != null) for (source in active) {
            val x = source.x.toDouble() * coFor(headingDegrees) + source.y.toDouble() * siFor(headingDegrees)
            val y = -source.x.toDouble() * siFor(headingDegrees) + source.y.toDouble() * coFor(headingDegrees)
            voices[source.id]?.hrtf?.direction(x,y)
        }
        active.forEach { voices[it.id]?.updateEnvironment(sampleRate, living) }
        outgoing.forEach { it.voice.updateEnvironment(sampleRate, living) }
        previousSources = upcoming
        // Keep the audio callback bounded even with rapid scene switching.
        if (outgoing.size > 24) outgoing.subList(0, outgoing.size - 24).clear()
        val normalize = 1.45 / sqrt(max(1, active.size).toDouble())
        val gain = masterGain.coerceIn(0f, 1f).toDouble() * normalize
        val angle = headingDegrees.coerceIn(-180f, 180f) * PI / 180.0
        val co = cos(angle)
        val si = sin(angle)

        for (i in out.indices step 2) {
            var left = 0.0
            var right = 0.0
            for (source in active) {
                val v = voices.getValue(source.id)
                val worldX = source.x.coerceIn(-1f, 1f)
                val worldY = source.y.coerceIn(-1f, 1f)
                val targetX = worldX * co + worldY * si
                val targetY = -worldX * si + worldY * co
                // Per-sample smoothing makes marker drags and rotation silent and fluid.
                v.smoothX += (targetX - v.smoothX) * 0.003
                v.smoothY += (targetY - v.smoothY) * 0.003
                v.smoothGain += (source.gain.coerceIn(0f, 1f) - v.smoothGain) * 0.003
                val x = v.smoothX
                val y = v.smoothY
                val distance = hypot(x, y)
                val attenuation = 1.0 / (1.0 + 1.28 * distance)

                val raw = v.nextSample(sampleRate, twoPi)
                // Behind-the-head cues are intentionally subtle, not true HRTF elevation.
                val filter = if (y > 0) 0.13 + (1.0 - y.coerceIn(0.0, 1.0)) * 0.39 else 0.66
                v.toneFilter += (raw - v.toneFilter) * filter
                val mono = v.toneFilter * attenuation * v.smoothGain * gain
                if (v.hrtf != null) {
                    v.hrtf.sample(mono)
                    left += v.hrtf.outputL
                    right += v.hrtf.outputR
                } else {
                    // Procedural/legacy fallback when no measured filters are bundled.
                    val pan = x.coerceIn(-1.0, 1.0)
                    val theta = (pan + 1.0) * PI / 4.0
                    val leftGain = cos(theta)
                    val rightGain = sin(theta)
                    val itd = (abs(pan) * sampleRate * 0.00052).toInt().coerceAtMost(v.delay.size - 2)
                    v.delay[v.delayPos] = mono
                    val delayed = v.delay[(v.delayPos - itd + v.delay.size) % v.delay.size]
                    v.delayPos = (v.delayPos + 1) % v.delay.size
                    if (pan >= 0) {
                        left += delayed * leftGain
                        right += mono * rightGain
                    } else {
                        left += mono * leftGain
                        right += delayed * rightGain
                    }
                }
            }
            // Outgoing layers decay over 420 ms as the new environment emerges.
            // This also makes mute/delete actions soft instead of clicky.
            for (tail in outgoing) {
                if (tail.framesRemaining <= 0) continue
                val sound = tail.source
                val voice = tail.voice
                val x = (sound.x * co + sound.y * si).coerceIn(-1.0, 1.0)
                val y = (-sound.x * si + sound.y * co).coerceIn(-1.0, 1.0)
                val distance = hypot(x, y)
                val fade = tail.framesRemaining.toDouble() / crossfadeFrames
                val raw = voice.nextSample(sampleRate, twoPi)
                val filter = if (y > 0) 0.13 + (1.0 - y.coerceIn(0.0, 1.0)) * 0.39 else 0.66
                voice.toneFilter += (raw - voice.toneFilter) * filter
                val amp = voice.toneFilter * fade * fade *
                    (1.0 / (1.0 + 1.28 * distance)) * voice.smoothGain * gain
                if (voice.hrtf != null) {
                    voice.hrtf.sample(amp)
                    left += voice.hrtf.outputL
                    right += voice.hrtf.outputR
                } else {
                    val theta = (x + 1.0) * PI / 4.0
                    left += amp * cos(theta)
                    right += amp * sin(theta)
                }
                tail.framesRemaining--
            }
            // Soft saturation controls unexpectedly dense scenes without hard clipping.
            out[i] = (softClip(left) * 32767.0).toInt().toShort()
            out[i + 1] = (softClip(right) * 32767.0).toInt().toShort()
        }
        outgoing.removeAll { it.framesRemaining <= 0 }
    }

    private fun softClip(x: Double): Double = (x / (1.0 + abs(x) * 0.32)).coerceIn(-1.0, 1.0)

    private class Voice(val kind: SoundKind, seed: Long,
                        private val clip: ShortArray?, bank: MeasuredHrtf?) {
        val hrtf = bank?.let { HrtfVoice(it) }
        private var clipPhase = if (clip == null || clip.isEmpty()) 0.0 else
            (abs(seed % 31).toDouble() / 37.0) * (clip.size - 1)
        // Choose a new, nonidentical entry on each loop; overlap at the seam.
        private var nextLoopStart = if (clip == null || clip.isEmpty()) 0 else
            ((abs(seed % 13).toDouble() / 47.0) * clip.size).toInt()
        private var loopSpeed = 0.99 + (abs(seed % 29).toDouble() / 29.0) * 0.02
        private var brown = 0.0
        private var pink = 0.0
        private var fanPhase = 0.0
        var smoothX = 0.0
        var smoothY = 0.0
        var smoothGain = 0.0
        var toneFilter = 0.0
        var low = 0.0
        var low2 = 0.0
        var high = 0.0
        var event = 0.0
        var phase = 0.0
        var frames = 0L
        var randomState: Long = if (seed == 0L) 1L else seed
        val delay = DoubleArray(64)
        var delayPos = 0

        private fun noise(): Double {
            var x = randomState
            x = x xor (x shl 13)
            x = x xor (x ushr 7)
            x = x xor (x shl 17)
            randomState = x
            return ((x ushr 40).toDouble() / 8_388_608.0) - 1.0
        }

        private var envelopeTarget = 1.0
        private var envelope = 1.0
        fun updateEnvironment(sampleRate: Int, living: Boolean) {
            if (!living) { envelopeTarget = 1.0; return }
            val t = frames.toDouble() / sampleRate
            val slow = sin(t * 0.053 + kind.ordinal * 1.73) * 0.12 +
                sin(t * 0.029 + kind.ordinal * 2.91) * 0.08 +
                sin(t * 0.011 + kind.ordinal * 0.47) * 0.07
            val cadence = when (kind) {
                SoundKind.THUNDER -> {
                    val section = (t + kind.ordinal * 3) % 37.0
                    if (section < 4.0) sin(PI * section / 4.0).let { it * it } * 0.85 else 0.025
                }
                SoundKind.OWL -> {
                    val section = (t + 10.0) % 29.0
                    if (section < 5.5) 0.80 else 0.018
                }
                SoundKind.CHIMES -> {
                    val section = (t + 3.0) % 16.0
                    if (section < 4.0) 0.70 else 0.10
                }
                SoundKind.OCEAN -> 0.55 + 0.45 * ((sin(t * 0.8 + 1.2)+1.0)*0.5)
                else -> 1.0
            }
            envelopeTarget = (1.0 + slow) * cadence
        }

        fun nextSample(sampleRate: Int, twoPi: Double): Double {
            val t = frames.toDouble() / sampleRate
            frames++
            // A low-pass envelope smoothly follows the block-rate living arrangement.
            envelope += (envelopeTarget - envelope) * 0.0008
            val movement = envelope
            if (clip != null && clip.size > 2205) {
                // Overlap the end of each recording with its beginning to hide
                // seams; avoid replaying exactly the same rhythmic event abruptly.
                // Extended 48s recordings, with randomized overlap starts
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
                return sample * movement / 32768.0
            }
            val n = noise()
            return movement * when (kind) {
                SoundKind.RAIN -> {
                    low += (n - low) * 0.027
                    high += (n - low - high) * 0.37
                    if (n > 0.993) event = 1.0
                    event *= 0.94
                    high * 0.72 + event * n * 0.22
                }
                SoundKind.THUNDER -> {
                    low += (n - low) * 0.007
                    low2 += (low - low2) * 0.017
                    val swell = 0.16 + 0.84 * (0.5 + 0.5 * sin(twoPi * t / 12.5)).let { it * it }
                    val bass = sin(twoPi * 39.0 * t) * 0.12 + sin(twoPi * 52.0 * t) * 0.09
                    (low2 * 4.2 + bass) * swell
                }
                SoundKind.OCEAN -> {
                    low += (n - low) * 0.045
                    low2 += (low - low2) * 0.009
                    val swell = 0.12 + 0.88 * (0.5 + 0.5 * sin(twoPi * t / 7.3)).let { it * it }
                    (low2 * 3.0 + low * 1.35 + n * 0.13) * swell
                }
                SoundKind.WIND -> {
                    low += (n - low) * 0.013
                    low2 += (low - low2) * 0.067
                    val gust = 0.14 + 0.86 * (0.5 + 0.5 * sin(twoPi * t / 8.3 + 0.8))
                    (low2 * 3.2 + (n - low) * 0.10) * gust
                }
                SoundKind.FIRE -> {
                    low += (n - low) * 0.052
                    if (n > 0.987) event = 1.0
                    event *= 0.89
                    low * 1.5 + event * (0.5 * n + 0.5) * 0.76
                }
                SoundKind.CHIMES -> {
                    val notes = CHIME_NOTES
                    val duration = 1.85
                    val index = ((t / duration).toInt() % notes.size)
                    val noteAge = t % duration
                    phase += twoPi * notes[index] / sampleRate
                    if (phase >= twoPi) phase -= twoPi
                    val fade = exp(-2.0 * noteAge)
                    (sin(phase) * 0.60 + sin(phase * 2.01) * 0.23 + sin(phase * 3.98) * 0.12) * fade * 0.62
                }
                SoundKind.BIRDS -> {
                    low += (n - low) * 0.026
                    val cycle = t % 3.35
                    val chirpTime = cycle % 0.45
                    val active = if (cycle < 1.7 && chirpTime < 0.22) 1.0 else 0.0
                    val profile = if (active > 0.0) sin(PI * chirpTime / 0.22).let { it * it } else 0.0
                    val hz = 1420.0 + 590.0 * sin(twoPi * chirpTime / 0.24) + 240.0 * sin(twoPi * chirpTime / 0.065)
                    phase += twoPi * hz / sampleRate
                    if (phase >= twoPi) phase -= twoPi
                    sin(phase) * profile * 0.27 + low * 0.42
                }
                SoundKind.NIGHT -> {
                    low += (n - low) * 0.017
                    val cycle = t % 0.78
                    val chirp = if (cycle < 0.31) sin(PI * cycle / 0.31).let { it * it } else 0.0
                    phase += twoPi * (3250.0 + 130.0 * sin(twoPi * t * 15.0)) / sampleRate
                    if (phase >= twoPi) phase -= twoPi
                    (sin(phase) * 0.17 + sin(phase * 1.02) * 0.09) * chirp + low * 0.28
                }
                SoundKind.WATERFALL, SoundKind.LEAVES, SoundKind.OWL, SoundKind.FROGS,
                SoundKind.TRAFFIC, SoundKind.DRIZZLE, SoundKind.SLEET,
                SoundKind.CAFE, SoundKind.TRAIN -> {
                    low += (n - low) * 0.019
                    low2 += (low - low2) * 0.06
                    (low * 2.0 + low2 + n * 0.09) * 0.68
                }
                SoundKind.WHITE_NOISE -> n * 0.21
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
                SoundKind.STREAM -> {
                    low += (n - low) * 0.035
                    low2 += (n - low2) * 0.22
                    val bubbling = 0.76 + 0.24 * sin(twoPi * t / 1.6)
                    if (n > 0.994) event = 1.0
                    event *= 0.96
                    (low * 1.85 + low2 * 0.53 + event * n * 0.16) * bubbling
                }
            }
        }

        companion object {
            private val CHIME_NOTES = doubleArrayOf(261.63, 329.63, 392.0, 523.25, 440.0, 392.0, 329.63, 293.66)
        }
    }
}
