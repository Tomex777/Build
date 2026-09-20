package com.night.homira.call

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class HomiraAudioRecorder(
    private val context: Context
) {
    private var recorder: MediaRecorder? = null

    fun start(outputFile: File) {
        stop()

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        @Suppress("DEPRECATION")
        val nextRecorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

        recorder = nextRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(48_000)
            setAudioEncodingBitRate(64_000)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
    }

    fun stop(): Boolean {
        val active = recorder ?: return false
        recorder = null

        return try {
            active.stop()
            true
        } catch (_: RuntimeException) {
            false
        } finally {
            runCatching { active.reset() }
            runCatching { active.release() }
        }
    }

    fun cancel() {
        val active = recorder ?: return
        recorder = null
        runCatching { active.stop() }
        runCatching { active.reset() }
        runCatching { active.release() }
    }
}

class HomiraAudioPlayer {
    private var player: MediaPlayer? = null

    fun play(
        file: File,
        onCompletion: () -> Unit
    ) {
        stop()

        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                stop()
                onCompletion()
            }
            setOnErrorListener { _, _, _ ->
                stop()
                onCompletion()
                true
            }
            prepare()
            start()
        }
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
    }

    fun resume() {
        player?.takeIf { !it.isPlaying }?.start()
    }

    fun isPlaying(): Boolean = player?.isPlaying == true

    fun stop() {
        val active = player ?: return
        player = null
        runCatching { active.stop() }
        runCatching { active.reset() }
        runCatching { active.release() }
    }
}
