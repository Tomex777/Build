package com.example.whatsapp.data.night

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request

data class NightMediaDownloadRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val fileName: String,
    val mimeType: String = "application/octet-stream",
    val workKey: String,
    val parallelism: Int = 3,
)

data class NightMediaDownloadResult(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val hls: Boolean,
    val muxedToMkv: Boolean,
)

class NightMediaDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun download(
        request: NightMediaDownloadRequest,
        onProgress: suspend (Float) -> Unit,
    ): NightMediaDownloadResult {
        require(request.url.startsWith("http://") || request.url.startsWith("https://")) {
            "Media URL must be HTTP or HTTPS."
        }

        val workspace = File(
            context.filesDir,
            "night_media_downloads/" + safeWorkKey(request.workKey),
        ).apply {
            if (!exists() && !mkdirs()) {
                throw IOException("Could not create the Night download workspace.")
            }
        }

        val hlsHint =
            request.mimeType.contains("mpegurl", ignoreCase = true) ||
                request.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
        val manifest =
            if (hlsHint) {
                requestBytes(request.url, request.headers).toString(Charsets.UTF_8)
            } else {
                val prefix = requestPrefix(request.url, request.headers, 2048)
                if (prefix.trimStart().startsWith("#EXTM3U")) {
                    requestBytes(request.url, request.headers).toString(Charsets.UTF_8)
                } else {
                    null
                }
            }

        return if (manifest != null) {
            downloadHls(
                request = request,
                workspace = workspace,
                initialManifest = manifest,
                onProgress = onProgress,
            )
        } else {
            downloadDirect(
                request = request,
                workspace = workspace,
                onProgress = onProgress,
            )
        }
    }

    private suspend fun downloadHls(
        request: NightMediaDownloadRequest,
        workspace: File,
        initialManifest: String,
        onProgress: suspend (Float) -> Unit,
    ): NightMediaDownloadResult {
        var manifestUrl = request.url
        var manifest = initialManifest

        val variants = NightHlsPlaylist.masterVariants(manifest, manifestUrl)
        if (variants.isNotEmpty()) {
            manifestUrl = variants.last()
            manifest = requestBytes(
                url = manifestUrl,
                headers = request.headers,
            ).toString(Charsets.UTF_8)
        }

        val segments = NightHlsPlaylist.segments(manifest, manifestUrl)
        if (segments.isEmpty()) {
            throw IOException("The HLS playlist did not contain media segments.")
        }

        val expectedDurationUs = segments.sumOf {
            (it.durationSeconds * 1_000_000.0).toLong()
        }.coerceAtLeast(0L)

        val marker = File(workspace, "manifest.meta")
        val markerValue = listOf(
            segments.size.toString(),
            expectedDurationUs.toString(),
            manifestUrl,
        ).joinToString("|")

        if (marker.takeIf { it.exists() }?.readText().orEmpty() != markerValue) {
            workspace.listFiles()?.forEach { file ->
                if (
                    file.name.endsWith(".seg") ||
                    file.name.endsWith(".part") ||
                    file.name == "joined.ts" ||
                    file.name == "output.mkv"
                ) {
                    file.delete()
                }
            }
            marker.writeText(markerValue)
        }

        val segmentFiles = segments.indices.map { index ->
            File(workspace, "%06d.seg".format(index))
        }
        val existingCount = segmentFiles.count {
            it.exists() && it.length() > 0L
        }
        onProgress(existingCount.toFloat() / segments.size.toFloat())

        val missingIndexes = segments.indices.filter { index ->
            val file = segmentFiles[index]
            !file.exists() || file.length() <= 0L
        }

        if (missingIndexes.isNotEmpty()) {
            val keyCache = missingIndexes
                .mapNotNull { segments[it].keyUrl }
                .distinct()
                .associateWith { keyUrl ->
                    requestBytes(keyUrl, request.headers)
                }

            val limiter = Semaphore(request.parallelism.coerceIn(1, 6))
            val completed = AtomicInteger(existingCount)

            coroutineScope {
                missingIndexes.map { index ->
                    async {
                        limiter.withPermit {
                            val segment = segments[index]
                            var bytes = requestBytes(segment.url, request.headers)
                            if (segment.keyUrl != null && segment.iv != null) {
                                val key = keyCache[segment.keyUrl]
                                    ?: throw IOException("Missing AES key for HLS segment.")
                                bytes = decryptAes128(bytes, key, segment.iv)
                            }

                            val finalFile = segmentFiles[index]
                            val partFile = File(workspace, "%06d.part".format(index))
                            partFile.writeBytes(bytes)
                            if (finalFile.exists()) finalFile.delete()
                            if (!partFile.renameTo(finalFile)) {
                                partFile.copyTo(finalFile, overwrite = true)
                                partFile.delete()
                            }

                            val done = completed.incrementAndGet()
                            onProgress(done.toFloat() / segments.size.toFloat())
                        }
                    }
                }.awaitAll()
            }
        }

        val completedFiles = segmentFiles.filter {
            it.exists() && it.length() > 0L
        }
        if (completedFiles.size != segments.size) {
            throw IOException(
                "Only " + completedFiles.size + " of " + segments.size +
                    " HLS segments are available; retry to resume."
            )
        }

        val joinedTs = File(workspace, "joined.ts")
        if (joinedTs.exists()) joinedTs.delete()
        joinedTs.outputStream().buffered().use { output ->
            completedFiles.forEach { file ->
                file.inputStream().buffered().use { input ->
                    input.copyTo(output)
                }
            }
        }

        val expectedBytes = completedFiles.sumOf { it.length() }
        if (joinedTs.length() < expectedBytes) {
            throw IOException("The joined HLS file is incomplete.")
        }

        val mkv = File(workspace, "output.mkv")
        if (mkv.exists()) mkv.delete()
        val muxed = muxTransportStreamToMkv(
            input = joinedTs,
            output = mkv,
            expectedInputBytes = expectedBytes,
        )
        val source = if (muxed) mkv else joinedTs
        val extension = if (muxed) "mkv" else "ts"
        val mime = if (muxed) "video/x-matroska" else "video/mp2t"
        val displayName = baseName(request.fileName)
            .ifBlank { "Night media" }
            .take(150) + "." + extension

        val uri = saveToDownloads(source, displayName, mime)
        workspace.deleteRecursively()

        return NightMediaDownloadResult(
            uri = uri,
            displayName = displayName,
            mimeType = mime,
            hls = true,
            muxedToMkv = muxed,
        )
    }

    private suspend fun downloadDirect(
        request: NightMediaDownloadRequest,
        workspace: File,
        onProgress: suspend (Float) -> Unit,
    ): NightMediaDownloadResult {
        val part = File(workspace, "direct.part")
        var existing = part.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L

        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                builder.header(key, value)
            }
        }
        if (existing > 0L) {
            builder.header("Range", "bytes=" + existing + "-")
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 416 && existing > 0L) {
                onProgress(1f)
            } else {
                if (!response.isSuccessful) {
                    throw IOException(
                        "HTTP " + response.code + " from " + response.request.url.host
                    )
                }

                val append = response.code == 206 && existing > 0L
                if (!append) {
                    FileOutputStream(part, false).use { }
                    existing = 0L
                }

                val body = response.body ?: throw IOException("Empty media response.")
                val remaining = body.contentLength().coerceAtLeast(0L)
                val total = if (remaining > 0L) existing + remaining else 0L

                FileOutputStream(part, append).buffered().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(128 * 1024)
                        var written = existing
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0L) {
                                onProgress(
                                    (written.toDouble() / total.toDouble())
                                        .toFloat()
                                        .coerceIn(0f, 1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!part.exists() || part.length() <= 0L) {
            throw IOException("The downloaded media file is empty.")
        }

        val safeName = safeFileName(
            request.fileName.ifBlank {
                "Night download" + extensionForMime(request.mimeType)
            }
        )
        val uri = saveToDownloads(part, safeName, request.mimeType)
        workspace.deleteRecursively()
        onProgress(1f)

        return NightMediaDownloadResult(
            uri = uri,
            displayName = safeName,
            mimeType = request.mimeType,
            hls = false,
            muxedToMkv = false,
        )
    }

    private fun requestPrefix(
        url: String,
        headers: Map<String, String>,
        maxBytes: Int,
    ): String {
        val builder = Request.Builder().url(url)
        headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                builder.header(key, value)
            }
        }
        builder.header("Range", "bytes=0-" + (maxBytes - 1).coerceAtLeast(0))
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    "HTTP " + response.code + " from " + response.request.url.host
                )
            }
            val source = response.body?.source() ?: return ""
            val bytes = source.readByteArray(maxBytes.toLong())
            return bytes.toString(Charsets.UTF_8)
        }
    }

    private fun requestBytes(
        url: String,
        headers: Map<String, String>,
    ): ByteArray {
        val builder = Request.Builder().url(url)
        headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                builder.header(key, value)
            }
        }
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    "HTTP " + response.code + " from " + response.request.url.host
                )
            }
            return response.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun decryptAes128(
        data: ByteArray,
        key: ByteArray,
        iv: ByteArray,
    ): ByteArray {
        require(key.size == 16) { "AES-128 key must be 16 bytes." }
        require(iv.size == 16) { "AES-128 IV must be 16 bytes." }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(iv),
        )
        return cipher.doFinal(data)
    }

    private suspend fun muxTransportStreamToMkv(
        input: File,
        output: File,
        expectedInputBytes: Long,
    ): Boolean {
        if (!input.exists() || input.length() <= 0L) return false

        val command = listOf(
            "-i \"" + input.absolutePath + "\"",
            "-map 0:v?",
            "-map 0:a?",
            "-map 0:s?",
            "-map 0:t?",
            "-f matroska",
            "-c copy",
            "\"" + output.absolutePath + "\"",
            "-y",
        ).joinToString(" ")
        val arguments = FFmpegKitConfig.parseArguments(command)

        return suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(
                arguments,
                { completed ->
                    val valid =
                        completed.returnCode.isValueSuccess &&
                            output.exists() &&
                            output.length() > 0L &&
                            (
                                expectedInputBytes <= 0L ||
                                    output.length() >= expectedInputBytes * 55L / 100L
                                )
                    if (!valid) output.delete()
                    if (continuation.isActive) continuation.resume(valid)
                },
            )
            continuation.invokeOnCancellation {
                session.cancel()
                output.delete()
            }
        }
    }

    private fun saveToDownloads(
        source: File,
        displayName: String,
        mimeType: String,
    ): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/Night",
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values,
            ) ?: throw IOException("Android could not create the output file.")

            try {
                resolver.openOutputStream(uri, "w")?.use { output ->
                    source.inputStream().buffered().use { input ->
                        input.copyTo(output)
                    }
                } ?: throw IOException("Android could not open the output file.")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val dir = File(root, "Night")
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("Could not create Downloads/Night.")
            }
            val output = File(dir, displayName)
            source.copyTo(output, overwrite = true)
            Uri.fromFile(output)
        }
    }

    private fun safeWorkKey(value: String): String {
        val clean = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        if (clean.isNotBlank()) return clean
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .take(10)
            .joinToString("") { "%02x".format(it) }
    }

    private fun safeFileName(value: String): String =
        value.replace(Regex("[\\/:*?\"<>|]+"), "_")
            .trim()
            .take(160)
            .ifBlank { "Night download" }

    private fun baseName(value: String): String {
        val safe = safeFileName(value)
        return safe.substringBeforeLast(".", safe)
    }

    private fun extensionForMime(mime: String): String = when {
        mime.contains("matroska", true) -> ".mkv"
        mime.contains("mp4", true) -> ".mp4"
        mime.contains("mpeg", true) -> ".mp3"
        mime.contains("ogg", true) -> ".ogg"
        mime.contains("webm", true) -> ".webm"
        else -> ""
    }
}
