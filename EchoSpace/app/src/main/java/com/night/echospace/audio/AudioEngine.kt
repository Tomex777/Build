package com.night.echospace.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import com.night.echospace.model.SoundSource
import java.util.concurrent.atomic.AtomicBoolean

/** AudioTrack is always owned and released on the audio thread. */
class AudioEngine(private val context: Context) {
    @Volatile private var living = true
    fun setLiving(value: Boolean) { living = value }
    @Volatile private var currentSources: List<SoundSource> = emptyList()
    @Volatile private var currentVolume: Float = 0.8f
    @Volatile private var currentHeading: Float = 0f
    private val alive = AtomicBoolean(true)
    private val playing = AtomicBoolean(false)

    @Volatile var failure: String? = null
        private set

    private val worker = Thread(::audioLoop, "EchoSpace-Audio").apply {
        isDaemon = true
        start()
    }

    fun setSources(sources: List<SoundSource>) { currentSources = sources.toList() }
    fun setMasterVolume(volume: Float) { currentVolume = volume.coerceIn(0f, 1f) }
    fun setHeading(degrees: Float) { currentHeading = degrees.coerceIn(-180f, 180f) }
    fun setPlaying(shouldPlay: Boolean) { playing.set(shouldPlay && alive.get()) }

    fun close() {
        playing.set(false)
        alive.set(false)
        worker.interrupt()
    }

    private fun audioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        var track: AudioTrack? = null
        try {
            val sampleRate = 44_100
            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            val minBytes = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
            )
            require(minBytes > 0) { "Stereo audio output is unavailable on this device" }
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBytes, 512 * 2 * 2 * 4))
                .build()
            check(track.state == AudioTrack.STATE_INITIALIZED) { "Could not start Android audio output" }
            // Asset decode lives exclusively on the worker, away from Compose and UI gestures.
            val recordings = RecordedSounds.load(context.assets)
            val hrtf = RecordedSounds.hrtf(context.assets)
            val synth = SpatialSynth(sampleRate, recordings, hrtf)
            val governor=BinauralQualityBudget(512,sampleRate)
            val buffer = ShortArray(512 * 2)
            var active = false
            while (alive.get()) {
                if (!playing.get()) {
                    if (active) {
                        track.pause()
                        track.flush()
                        active = false
                    }
                    try { Thread.sleep(25) } catch (_: InterruptedException) { /* check alive */ }
                    continue
                }
                if (!active) {
                    track.play()
                    active = true
                }
                synth.setLiving(living)
                synth.setMeasuredVoiceLimit(governor.measuredVoiceLimit)
                val renderStart=System.nanoTime()
                synth.renderInto(buffer, currentSources, currentVolume, currentHeading)
                governor.record(System.nanoTime()-renderStart)
                var written = 0
                while (written < buffer.size && alive.get() && playing.get()) {
                    val count = track.write(buffer, written, buffer.size - written, AudioTrack.WRITE_BLOCKING)
                    if (count <= 0) {
                        throw IllegalStateException("Audio output write failed: $count")
                    }
                    written += count
                }
            }
        } catch (e: Exception) {
            if (alive.get()) failure = e.message ?: "Audio output failed"
            playing.set(false)
        } finally {
            try { track?.pause() } catch (_: Exception) {}
            try { track?.flush() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
        }
    }
}
