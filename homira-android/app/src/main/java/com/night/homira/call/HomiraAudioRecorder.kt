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

        val next = MediaPlayer()
        player = next

        runCatching {
            next.setDataSource(file.absolutePath)
            next.setOnCompletionListener {
                stop()
                runCatching { onCompletion() }
            }
            next.setOnErrorListener { _, _, _ ->
                stop()
                runCatching { onCompletion() }
                true
            }
            next.prepare()
            next.start()
        }.onFailure {
            if (player === next) player = null
            runCatching { next.reset() }
            runCatching { next.release() }
            runCatching { onCompletion() }
        }
    }

    fun pause() {
        runCatching {
            player?.takeIf { it.isPlaying }?.pause()
        }
    }

    fun resume() {
        runCatching {
            player?.takeIf { !it.isPlaying }?.start()
        }
    }

    fun isPlaying(): Boolean =
        runCatching { player?.isPlaying == true }.getOrDefault(false)

    fun stop() {
        val active = player ?: return
        player = null
        runCatching { active.stop() }
        runCatching { active.reset() }
        runCatching { active.release() }
    }
}
