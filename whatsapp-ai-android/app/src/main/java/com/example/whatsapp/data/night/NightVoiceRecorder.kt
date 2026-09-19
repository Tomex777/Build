package com.example.whatsapp.data.night

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NightVoiceRecorder(
    private val context: Context,
) {
    private var audioRecord: AudioRecord? = null
    private var rawFile: File? = null
    private var wavFile: File? = null
    private var writerThread: Thread? = null
    @Volatile private var recording = false
    private var startedAt: Long = 0L

    val isRecording: Boolean
        get() = recording

    fun start(): File {
        check(!recording) { "Already recording." }

        val dir = File(context.cacheDir, "night_voice").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val raw = File(dir, "voice_" + stamp + ".pcm")
        val wav = File(dir, "voice_" + stamp + ".wav")

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBuffer > 0) { "Audio recording is not available." }

        @Suppress("MissingPermission")
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2,
        )

        require(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "Could not initialize microphone."
        }

        rawFile = raw
        wavFile = wav
        audioRecord = recorder
        recording = true
        startedAt = System.currentTimeMillis()

        recorder.startRecording()

        writerThread = Thread {
            val buffer = ByteArray(minBuffer)
            FileOutputStream(raw).use { output ->
                while (recording) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        output.write(buffer, 0, read)
                    }
                }
            }
        }.apply {
            name = "NightVoiceRecorder"
            start()
        }

        return wav
    }

    fun stop(): RecordedVoice? {
        val recorder = audioRecord ?: return null
        val raw = rawFile
        val wav = wavFile
        val durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)

        recording = false
        runCatching { recorder.stop() }
        runCatching { writerThread?.join(1500L) }
        runCatching { recorder.release() }

        audioRecord = null
        writerThread = null
        rawFile = null
        wavFile = null
        startedAt = 0L

        if (raw == null || wav == null || !raw.exists() || raw.length() == 0L) {
            raw?.delete()
            wav?.delete()
            return null
        }

        return runCatching {
            writeWave(raw, wav)
            raw.delete()
            RecordedVoice(wav, durationMs)
        }.getOrElse {
            raw.delete()
            wav.delete()
            null
        }
    }

    fun cancel() {
        val recorder = audioRecord
        recording = false
        runCatching { recorder?.stop() }
        runCatching { writerThread?.join(800L) }
        runCatching { recorder?.release() }
        rawFile?.delete()
        wavFile?.delete()
        audioRecord = null
        writerThread = null
        rawFile = null
        wavFile = null
        startedAt = 0L
    }

    private fun writeWave(raw: File, wav: File) {
        val dataLength = raw.length()
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        val totalLength = dataLength + 36L

        FileOutputStream(wav).use { out ->
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            out.write(leInt(totalLength.toInt()))
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            out.write(leInt(16))
            out.write(leShort(1))
            out.write(leShort(CHANNELS))
            out.write(leInt(SAMPLE_RATE))
            out.write(leInt(byteRate))
            out.write(leShort(CHANNELS * BITS_PER_SAMPLE / 8))
            out.write(leShort(BITS_PER_SAMPLE))
            out.write("data".toByteArray(Charsets.US_ASCII))
            out.write(leInt(dataLength.toInt()))

            FileInputStream(raw).use { input ->
                input.copyTo(out)
            }
        }
    }

    private fun leInt(value: Int): ByteArray =
        ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(value)
            .array()

    private fun leShort(value: Int): ByteArray =
        ByteBuffer.allocate(2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(value.toShort())
            .array()

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNELS = 1
        private const val BITS_PER_SAMPLE = 16
    }
}

data class RecordedVoice(
    val file: File,
    val durationMs: Long,
)
