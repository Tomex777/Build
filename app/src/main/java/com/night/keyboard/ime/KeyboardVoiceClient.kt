package com.night.keyboard.ime

import android.content.Context
import android.media.MediaRecorder
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class KeyboardVoiceRecorder(
    private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null

    fun start(): Result<Unit> = runCatching {
        check(recorder == null) { "A recording is already active." }
        val file = File(context.cacheDir, "keyboard-voice-${UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION")
        val next = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64_000)
            setAudioSamplingRate(16_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recordingFile = file
        recorder = next
    }

    fun stop(): Result<File> = runCatching {
        val active = recorder ?: error("No recording is active.")
        recorder = null
        try {
            active.stop()
        } finally {
            active.reset()
            active.release()
        }
        val file = recordingFile ?: error("Recording file is unavailable.")
        recordingFile = null
        require(file.exists() && file.length() > 0L) { "The recording was empty." }
        file
    }

    fun cancel() {
        val active = recorder
        recorder = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.reset() }
            runCatching { active.release() }
        }
        recordingFile?.delete()
        recordingFile = null
    }
}

class KeyboardVoiceClient(
    private val baseUrl: String,
) {
    suspend fun transcribe(file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val root = URL(baseUrl.trim().trimEnd('/'))
            require(root.protocol.equals("https", ignoreCase = true)) {
                "Keyboard voice input requires HTTPS."
            }
            require(file.exists() && file.length() > 0L) { "Recording file is empty." }

            val boundary = "KeyboardBoundary${System.currentTimeMillis()}"
            val endpoint = URL(root.toString().trimEnd('/') + "/v1/keyboard/voice")
            val connection = endpoint.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 12_000
                connection.readTimeout = 45_000
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary=$boundary",
                )
                connection.setRequestProperty("Accept", "application/json")

                connection.outputStream.buffered().use { output ->
                    fun write(text: String) {
                        output.write(text.toByteArray(Charsets.UTF_8))
                    }
                    write("--$boundary\r\n")
                    write(
                        "Content-Disposition: form-data; name=\"audio\"; filename=\"voice.m4a\"\r\n",
                    )
                    write("Content-Type: audio/mp4\r\n\r\n")
                    file.inputStream().use { it.copyTo(output) }
                    write("\r\n--$boundary--\r\n")
                }

                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                require(code in 200..299) {
                    "Server returned HTTP $code" + if (body.isBlank()) "" else ": " + body.take(180)
                }

                val parsed = runCatching { JSONObject(body) }.getOrNull()
                val transcript = parsed?.optString("text")
                    ?.takeIf(String::isNotBlank)
                    ?: parsed?.optString("transcript")?.takeIf(String::isNotBlank)
                    ?: body.trim()
                require(transcript.isNotBlank()) { "Server returned an empty transcript." }
                transcript
            } finally {
                connection.disconnect()
                file.delete()
            }
        }
    }
}
