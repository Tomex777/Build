package com.example.whatsapp.data.night

import android.content.Context
import android.media.MediaRecorder
import java.io.File

class NightVoiceRecorder(
    private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt: Long = 0L

    val isRecording: Boolean
        get() = recorder != null

    fun start(): File {
        check(recorder == null) { "Already recording." }

        val dir = File(context.cacheDir, "night_voice").apply { mkdirs() }
        val file = File(dir, "voice_" + System.currentTimeMillis() + ".m4a")

        @Suppress("DEPRECATION")
        val mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        recorder = mediaRecorder
        output = file
        startedAt = System.currentTimeMillis()
        return file
    }

    fun stop(): RecordedVoice? {
        val mediaRecorder = recorder ?: return null
        val file = output
        val durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)

        return try {
            mediaRecorder.stop()
            mediaRecorder.release()
            recorder = null
            output = null
            startedAt = 0L

            if (file == null || !file.exists() || file.length() == 0L) {
                file?.delete()
                null
            } else {
                RecordedVoice(file, durationMs)
            }
        } catch (_: Throwable) {
            runCatching { mediaRecorder.release() }
            recorder = null
            output?.delete()
            output = null
            startedAt = 0L
            null
        }
    }

    fun cancel() {
        val mediaRecorder = recorder ?: return
        runCatching { mediaRecorder.stop() }
        runCatching { mediaRecorder.release() }
        output?.delete()
        recorder = null
        output = null
        startedAt = 0L
    }
}

data class RecordedVoice(
    val file: File,
    val durationMs: Long,
)
