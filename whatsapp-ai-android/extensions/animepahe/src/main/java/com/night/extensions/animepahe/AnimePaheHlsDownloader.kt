package com.night.extensions.animepahe

import android.content.ContentValues
import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import okhttp3.OkHttpClient
import okhttp3.Request

data class AnimePaheDownloadRequest(
    val manifestUrl: String,
    val headers: Map<String, String>,
    val fileName: String,
    val workKey: String,
    val parallelism: Int,
)

data class AnimePaheDownloadResult(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val muxedToMkv: Boolean,
)

class AnimePaheHlsDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun download(
        request: AnimePaheDownloadRequest,
        onProgress: suspend (Float) -> Unit,
    ): AnimePaheDownloadResult {
        var manifestUrl = request.manifestUrl
        var manifest =
            requestBytes(
                manifestUrl,
                request.headers,
            ).toString(Charsets.UTF_8)

        val variants =
            AnimePaheHlsPlaylist.masterVariants(
                manifest,
                manifestUrl,
            )
        if (variants.isNotEmpty()) {
            manifestUrl = variants.last()
            manifest =
                requestBytes(
                    manifestUrl,
                    request.headers,
                ).toString(Charsets.UTF_8)
        }

        val segments =
            AnimePaheHlsPlaylist.segments(
                manifest,
                manifestUrl,
            )
        if (segments.isEmpty()) {
            throw IOException(
                "The HLS playlist did not contain any media segments."
            )
        }

        val expectedDurationUs =
            segments.sumOf {
                (it.durationSeconds * 1_000_000.0).toLong()
            }.coerceAtLeast(0L)

        val tempDir =
            File(
                context.filesDir,
                "animepahe_downloads/" + request.workKey,
            )
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw IOException(
                "Could not create the persistent download workspace."
            )
        }

        val marker = File(tempDir, "manifest.meta")
        val markerValue =
            listOf(
                segments.size.toString(),
                expectedDurationUs.toString(),
                manifestUrl,
            ).joinToString("|")

        if (
            marker.takeIf { it.exists() }
                ?.readText()
                .orEmpty() != markerValue
        ) {
            tempDir.listFiles()?.forEach { file ->
                if (
                    file.name.endsWith(".ts") ||
                    file.name.endsWith(".part") ||
                    file.name == "joined.ts" ||
                    file.name == "episode.mkv"
                ) {
                    file.delete()
                }
            }
            marker.writeText(markerValue)
        }

        val segmentFiles =
            segments.indices.map { index ->
                File(
                    tempDir,
                    "%06d.ts".format(index),
                )
            }

        val existingCount =
            segmentFiles.count {
                it.exists() && it.length() > 0L
            }
        onProgress(
            existingCount.toFloat() /
                segments.size.toFloat()
        )

        val missingIndexes =
            segments.indices.filter { index ->
                val file = segmentFiles[index]
                !file.exists() || file.length() <= 0L
            }

        if (missingIndexes.isNotEmpty()) {
            val keyCache =
                missingIndexes
                    .mapNotNull { index ->
                        segments[index].keyUrl
                    }
                    .distinct()
                    .associateWith { keyUrl ->
                        requestBytes(
                            keyUrl,
                            request.headers,
                        )
                    }

            val limiter =
                Semaphore(
                    request.parallelism.coerceIn(1, 6)
                )
            val completed =
                AtomicInteger(existingCount)

            coroutineScope {
                missingIndexes.map { index ->
                    async {
                        limiter.withPermit {
                            val segment = segments[index]
                            var bytes =
                                requestBytes(
                                    segment.url,
                                    request.headers,
                                )

                            if (
                                segment.keyUrl != null &&
                                segment.iv != null
                            ) {
                                val key =
                                    keyCache[segment.keyUrl]
                                        ?: throw IOException(
                                            "Missing AES key for HLS segment."
                                        )
                                bytes =
                                    decryptAes128(
                                        bytes,
                                        key,
                                        segment.iv,
                                    )
                            }

                            val finalFile =
                                segmentFiles[index]
                            val partFile =
                                File(
                                    tempDir,
                                    "%06d.part".format(index),
                                )

                            partFile.writeBytes(bytes)
                            if (finalFile.exists()) {
                                finalFile.delete()
                            }
                            if (!partFile.renameTo(finalFile)) {
                                partFile.copyTo(
                                    finalFile,
                                    overwrite = true,
                                )
                                partFile.delete()
                            }

                            val done =
                                completed.incrementAndGet()
                            onProgress(
                                done.toFloat() /
                                    segments.size.toFloat()
                            )
                        }
                    }
                }.awaitAll()
            }
        }

        val completedFiles =
            segmentFiles.filter {
                it.exists() && it.length() > 0L
            }
        if (completedFiles.size != segments.size) {
            throw IOException(
                "Only " +
                    completedFiles.size +
                    " of " +
                    segments.size +
                    " HLS segments are available; the download can resume later."
            )
        }

        val joinedTs = File(tempDir, "joined.ts")
        if (joinedTs.exists()) {
            joinedTs.delete()
        }
        joinedTs.outputStream()
            .buffered()
            .use { out ->
                completedFiles.forEach { file ->
                    file.inputStream()
                        .buffered()
                        .use { input ->
                            input.copyTo(out)
                        }
                }
            }

        val inputBytes =
            completedFiles.sumOf { it.length() }
        if (joinedTs.length() < inputBytes) {
            throw IOException(
                "The joined episode file is incomplete."
            )
        }

        val muxedMkv =
            File(tempDir, "episode.mkv")
        if (muxedMkv.exists()) {
            muxedMkv.delete()
        }

        val mkvReady =
            muxJoinedTransportStreamToMkv(
                input = joinedTs,
                output = muxedMkv,
                expectedInputBytes = inputBytes,
            )

        val sourceFile =
            if (mkvReady) muxedMkv else joinedTs
        val extension =
            if (mkvReady) "mkv" else "ts"
        val mime =
            if (mkvReady) {
                "video/x-matroska"
            } else {
                "video/mp2t"
            }

        val displayName =
            request.fileName
                .substringBeforeLast(".", request.fileName)
                .ifBlank { "AnimePahe episode" }
                .take(150) +
                "." +
                extension

        val uri =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveModernDownload(
                    sourceFile,
                    displayName,
                    mime,
                )
            } else {
                saveLegacyDownload(
                    sourceFile,
                    displayName,
                )
            }

        tempDir.deleteRecursively()

        return AnimePaheDownloadResult(
            uri = uri,
            displayName = displayName,
            mimeType = mime,
            muxedToMkv = mkvReady,
        )
    }

    private fun requestBytes(
        url: String,
        headers: Map<String, String>,
    ): ByteArray {
        val builder =
            Request.Builder()
                .url(url)
        headers.forEach { (key, value) ->
            if (
                key.isNotBlank() &&
                value.isNotBlank()
            ) {
                builder.header(key, value)
            }
        }

        client.newCall(builder.build())
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        "HTTP " +
                            response.code +
                            " from " +
                            response.request.url.host
                    )
                }
                return response.body
                    ?.bytes()
                    ?: ByteArray(0)
            }
    }

    private fun decryptAes128(
        data: ByteArray,
        key: ByteArray,
        iv: ByteArray,
    ): ByteArray {
        val cipher =
            Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(iv),
        )
        return cipher.doFinal(data)
    }

    private suspend fun muxJoinedTransportStreamToMkv(
        input: File,
        output: File,
        expectedInputBytes: Long,
    ): Boolean {
        if (
            !input.exists() ||
            input.length() <= 0L
        ) {
            return false
        }

        val command =
            listOf(
                "-i \"${input.absolutePath}\"",
                "-map 0:v",
                "-map 0:a?",
                "-map 0:s?",
                "-map 0:t?",
                "-f matroska",
                "-c:a copy",
                "-c:v copy",
                "-c:s copy",
                "\"${output.absolutePath}\"",
                "-y",
            ).joinToString(" ")

        val arguments =
            FFmpegKitConfig.parseArguments(command)

        return suspendCancellableCoroutine { continuation ->
            val session =
                FFmpegKit.executeWithArgumentsAsync(
                    arguments,
                    { completed ->
                        val valid =
                            completed.returnCode.isValueSuccess &&
                                output.exists() &&
                                output.length() > 0L &&
                                (
                                    expectedInputBytes <= 0L ||
                                        output.length() >=
                                            expectedInputBytes * 55L / 100L
                                    )

                        if (!valid) {
                            output.delete()
                        }
                        if (continuation.isActive) {
                            continuation.resume(valid)
                        }
                    },
                )

            continuation.invokeOnCancellation {
                session.cancel()
                output.delete()
            }
        }
    }

    private fun saveModernDownload(
        sourceFile: File,
        displayName: String,
        mime: String,
    ): Uri {
        val values =
            ContentValues().apply {
                put(
                    MediaStore.Downloads.DISPLAY_NAME,
                    displayName,
                )
                put(
                    MediaStore.Downloads.MIME_TYPE,
                    mime,
                )
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS +
                        "/Night/AnimePahe",
                )
                put(
                    MediaStore.Downloads.IS_PENDING,
                    1,
                )
            }

        val resolver =
            context.contentResolver
        val uri =
            resolver.insert(
                MediaStore.Downloads
                    .EXTERNAL_CONTENT_URI,
                values,
            )
                ?: throw IOException(
                    "Android could not create the output file."
                )

        try {
            resolver.openOutputStream(
                uri,
                "w",
            )?.use { out ->
                sourceFile.inputStream()
                    .buffered()
                    .use { input ->
                        input.copyTo(out)
                    }
            } ?: throw IOException(
                "Android could not open the output file."
            )

            values.clear()
            values.put(
                MediaStore.Downloads.IS_PENDING,
                0,
            )
            resolver.update(
                uri,
                values,
                null,
                null,
            )
            return uri
        } catch (error: Exception) {
            resolver.delete(
                uri,
                null,
                null,
            )
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacyDownload(
        sourceFile: File,
        displayName: String,
    ): Uri {
        val downloads =
            Environment
                .getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
        val outputDir =
            File(
                downloads,
                "Night/AnimePahe",
            )
        if (
            !outputDir.exists() &&
            !outputDir.mkdirs()
        ) {
            throw IOException(
                "Could not create Downloads/Night/AnimePahe."
            )
        }

        val output =
            File(
                outputDir,
                displayName,
            )
        sourceFile.inputStream()
            .buffered()
            .use { input ->
                output.outputStream()
                    .buffered()
                    .use { out ->
                        input.copyTo(out)
                    }
            }
        return Uri.fromFile(output)
    }
}
