package com.night.mirrorchess.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Original, procedurally generated wood-like chess cues. No copyrighted samples,
 * network access, or permissions needed. SoundPool plays asynchronously on the
 * game/sonification audio stream; UI rendering never waits for playback.
 */
class ChessSoundEngine(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val ids = mutableMapOf<ChessSoundCue, Int>()
    private val ready = mutableSetOf<Int>()
    private var closed = false

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(this) {
                if (!closed) ready.add(sampleId)
            }
        }
        val folder = File(context.cacheDir, "mirrorchess-sounds-v1")
        if (folder.isDirectory || folder.mkdirs()) {
            ChessSoundCue.entries.forEach { cue ->
                runCatching {
                    val file = File(folder, cue.name.lowercase() + ".wav")
                    if (!file.isFile || file.length() < 48) file.writeBytes(chessCueWav(cue))
                    ids[cue] = pool.load(file.absolutePath, 1)
                }
            }
        }
    }

    @Synchronized
    fun play(cue: ChessSoundCue) {
        if (closed) return
        val sample = ids[cue] ?: return
        if (sample in ready) pool.play(sample, 0.40f, 0.40f, 1, 0, 1f)
    }

    @Synchronized
    fun close() {
        if (!closed) {
            closed = true
            ready.clear()
            pool.release()
        }
    }
}

/** 22.05kHz mono PCM WAV, intentionally short and low in volume. */
internal fun chessCueWav(cue: ChessSoundCue): ByteArray {
    val sampleRate = 22_050
    val duration = when (cue) {
        ChessSoundCue.MOVE -> 72
        ChessSoundCue.CAPTURE -> 108
        ChessSoundCue.CASTLE -> 145
        ChessSoundCue.CHECK -> 180
        ChessSoundCue.GAME_END -> 320
    }
    val count = sampleRate * duration / 1000
    val buffer = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
    fun ascii(s: String) { buffer.put(s.toByteArray(Charsets.US_ASCII)) }
    ascii("RIFF"); buffer.putInt(36 + count * 2); ascii("WAVE")
    ascii("fmt "); buffer.putInt(16)
    buffer.putShort(1.toShort()); buffer.putShort(1.toShort())
    buffer.putInt(sampleRate); buffer.putInt(sampleRate * 2)
    buffer.putShort(2.toShort()); buffer.putShort(16.toShort())
    ascii("data"); buffer.putInt(count * 2)
    var noiseState = 0x5A17 + cue.ordinal * 1103
    repeat(count) { i ->
        val t = i.toDouble() / sampleRate
        val fraction = i.toDouble() / count
        noiseState = noiseState * 1664525 + 1013904223
        val noise = ((noiseState ushr 16) and 0xFFFF) / 32768.0 - 1.0
        val attack = (fraction * 65).coerceAtMost(1.0)
        val release = exp(-fraction * when (cue) {
            ChessSoundCue.MOVE -> 6.5
            ChessSoundCue.CAPTURE -> 5.0
            ChessSoundCue.CASTLE -> 4.8
            ChessSoundCue.CHECK -> 3.6
            ChessSoundCue.GAME_END -> 2.7
        })
        val tone = when (cue) {
            ChessSoundCue.MOVE ->
                0.46 * sin(2 * PI * 560 * t) + 0.20 * sin(2 * PI * 290 * t) + 0.15 * noise
            ChessSoundCue.CAPTURE ->
                0.42 * sin(2 * PI * 245 * t) + 0.25 * sin(2 * PI * 125 * t) + 0.32 * noise
            ChessSoundCue.CASTLE -> {
                val second = if (t >= 0.065) exp(-(t - 0.065) * 42) else 0.0
                0.44 * sin(2 * PI * 370 * t) + 0.20 * noise + 0.30 * second * sin(2 * PI * 440 * t)
            }
            ChessSoundCue.CHECK ->
                0.36 * sin(2 * PI * 700 * t) + 0.25 * sin(2 * PI * 940 * t) + 0.06 * noise
            ChessSoundCue.GAME_END ->
                0.25 * sin(2 * PI * 523.25 * t) + 0.24 * sin(2 * PI * 659.25 * t) +
                    0.22 * sin(2 * PI * 783.99 * t)
        }
        val value = (tone * attack * release * 13_000).roundToInt().coerceIn(-32768, 32767)
        buffer.putShort(value.toShort())
    }
    return buffer.array()
}
