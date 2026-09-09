package com.night.mirrorchess.ai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class ModelManager(private val context: Context) {
    companion object {
        const val MODEL_NAME = "maia3-5m.fp16.onnx"
        const val MODEL_URL = "https://huggingface.co/bqrio/maia3-onnx/resolve/f2582c005a63a034e493d93736ecdd6291dd82e7/maia3-5m.fp16.onnx?download=true"
        const val MODEL_SHA256 = "ca22fc3031975932e693f9758149302efc177749165443ed52de828add8864fa"
        const val MODEL_ASSET = "models/maia3-5m.fp16.onnx"
    }

    private val modelDir: File get() = File(context.filesDir, "models").apply { mkdirs() }
    val modelFile: File get() = File(modelDir, MODEL_NAME)
    @Volatile private var cachedVerification: VerificationStamp? = null

    private data class VerificationStamp(val length: Long, val modified: Long, val verified: Boolean)

    fun isInstalled(): Boolean = modelFile.isFile && modelFile.length() > 0L

    fun isVerifiedInstalled(): Boolean {
        if (!isInstalled()) {
            cachedVerification = null
            return false
        }
        val stamp = cachedVerification
        if (stamp != null && stamp.length == modelFile.length() && stamp.modified == modelFile.lastModified()) {
            return stamp.verified
        }
        val verified = verifyFile(modelFile)
        cachedVerification = VerificationStamp(modelFile.length(), modelFile.lastModified(), verified)
        return verified
    }

    fun bundledAssetExists(): Boolean = runCatching { context.assets.open(MODEL_ASSET).use { true } }.getOrDefault(false)

    suspend fun download(onProgress: suspend (Float) -> Unit = {}): Result<File> = withContext(Dispatchers.IO) {
        val temp = File(modelDir, "$MODEL_NAME.part")
        try {
            temp.delete()
            val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MirrorChess/1.2")
            }
            try {
                connection.connect()
                require(connection.responseCode in 200..299) { "Download failed: HTTP ${connection.responseCode}" }
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    temp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                        var copied = 0L
                        var lastReportedPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) {
                                val progress = (copied.toFloat() / total).coerceIn(0f, 1f)
                                val percent = (progress * 100).toInt()
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }
                require(verifyFile(temp)) { "Model checksum mismatch. The downloaded file was discarded." }
                installVerified(temp)
                onProgress(1f)
                Result.success(modelFile)
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            temp.delete()
            throw cancelled
        } catch (error: Throwable) {
            temp.delete()
            Result.failure(error)
        }
    }

    suspend fun importFrom(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        val temp = File(modelDir, "$MODEL_NAME.import")
        try {
            temp.delete()
            val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Could not open selected file." }
            input.use { source -> temp.outputStream().buffered().use { target -> source.copyTo(target) } }
            require(verifyFile(temp)) { "That file is not the expected Maia-3 5M model (SHA-256 mismatch)." }
            installVerified(temp)
            Result.success(modelFile)
        } catch (cancelled: CancellationException) {
            temp.delete()
            throw cancelled
        } catch (error: Throwable) {
            temp.delete()
            Result.failure(error)
        }
    }

    fun delete() {
        cachedVerification = null
        modelFile.delete()
        File(modelDir, "$MODEL_NAME.part").delete()
        File(modelDir, "$MODEL_NAME.import").delete()
        File(modelDir, "$MODEL_NAME.backup").delete()
    }

    private fun installVerified(temp: File) {
        val backup = File(modelDir, "$MODEL_NAME.backup")
        backup.delete()
        if (modelFile.exists()) {
            require(modelFile.renameTo(backup)) { "Could not prepare the existing model for replacement." }
        }
        try {
            require(temp.renameTo(modelFile)) { "Could not install the verified model." }
            cachedVerification = VerificationStamp(modelFile.length(), modelFile.lastModified(), true)
            backup.delete()
        } catch (error: Throwable) {
            modelFile.delete()
            if (backup.exists()) backup.renameTo(modelFile)
            cachedVerification = null
            throw error
        }
    }

    fun verifyFile(file: File): Boolean = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        actual.equals(MODEL_SHA256, ignoreCase = true)
    }.getOrDefault(false)
}
