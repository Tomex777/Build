package com.night.extensions.animepahe

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    val remuxedToMp4: Boolean,
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
                    file.name == "episode.mp4"
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

        val remuxedMp4 =
            File(tempDir, "episode.mp4")
        if (remuxedMp4.exists()) {
            remuxedMp4.delete()
        }

        val mp4Ready =
            remuxJoinedTransportStreamToMp4(
                input = joinedTs,
                output = remuxedMp4,
                expectedDurationUs = expectedDurationUs,
                expectedInputBytes = inputBytes,
            )

        val sourceFile =
            if (mp4Ready) remuxedMp4 else joinedTs
        val extension =
            if (mp4Ready) "mp4" else "ts"
        val mime =
            if (mp4Ready) {
                "video/mp4"
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
            remuxedToMp4 = mp4Ready,
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

    private fun remuxJoinedTransportStreamToMp4(
        input: File,
        output: File,
        expectedDurationUs: Long,
        expectedInputBytes: Long,
    ): Boolean {
        if (
            !input.exists() ||
            input.length() <= 0L
        ) {
            return false
        }

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        return try {
            extractor.setDataSource(
                input.absolutePath
            )

            val sourceToMux =
                linkedMapOf<Int, Int>()
            val muxableFormats =
                mutableListOf<
                    Pair<Int, android.media.MediaFormat>
                >()

            for (
                track in
                    0 until extractor.trackCount
            ) {
                val format =
                    extractor.getTrackFormat(track)
                val mime =
                    format.getString(
                        android.media.MediaFormat.KEY_MIME
                    ).orEmpty()
                if (
                    mime.startsWith("video/") ||
                    mime.startsWith("audio/")
                ) {
                    muxableFormats +=
                        track to format
                }
            }

            if (muxableFormats.isEmpty()) {
                return false
            }

            muxer =
                MediaMuxer(
                    output.absolutePath,
                    MediaMuxer.OutputFormat
                        .MUXER_OUTPUT_MPEG_4,
                )

            muxableFormats.forEach {
                    (sourceTrack, format),
                ->
                sourceToMux[sourceTrack] =
                    muxer.addTrack(format)
                extractor.selectTrack(sourceTrack)
            }

            muxer.start()
            muxerStarted = true

            val buffer =
                ByteBuffer.allocateDirect(
                    4 * 1024 * 1024
                )
            val info =
                MediaCodec.BufferInfo()

            var globalBaseUs: Long? = null
            val ptsOffsetByTrack =
                mutableMapOf<Int, Long>()
            val lastOutputPtsByTrack =
                mutableMapOf<Int, Long>()
            val lastRawPtsByTrack =
                mutableMapOf<Int, Long>()

            while (true) {
                val sourceTrack =
                    extractor.sampleTrackIndex
                if (sourceTrack < 0) {
                    break
                }

                val muxTrack =
                    sourceToMux[sourceTrack]
                if (muxTrack == null) {
                    extractor.advance()
                    continue
                }

                buffer.clear()
                val size =
                    extractor.readSampleData(
                        buffer,
                        0,
                    )
                if (size < 0) {
                    break
                }

                val rawPtsUs =
                    extractor.sampleTime
                        .coerceAtLeast(0L)
                if (globalBaseUs == null) {
                    globalBaseUs = rawPtsUs
                }
                val baseUs =
                    globalBaseUs ?: rawPtsUs

                var offsetUs =
                    ptsOffsetByTrack[sourceTrack]
                        ?: 0L
                val previousRawUs =
                    lastRawPtsByTrack[sourceTrack]
                val previousOutputUs =
                    lastOutputPtsByTrack[sourceTrack]

                if (
                    previousRawUs != null &&
                    previousOutputUs != null &&
                    rawPtsUs + 250_000L <
                        previousRawUs
                ) {
                    val normalizedRawUs =
                        (rawPtsUs - baseUs)
                            .coerceAtLeast(0L)
                    offsetUs =
                        previousOutputUs +
                            1L -
                            normalizedRawUs
                    ptsOffsetByTrack[sourceTrack] =
                        offsetUs
                }

                var outputPtsUs =
                    (rawPtsUs - baseUs)
                        .coerceAtLeast(0L) +
                        offsetUs
                if (
                    previousOutputUs != null &&
                    outputPtsUs <=
                        previousOutputUs
                ) {
                    outputPtsUs =
                        previousOutputUs + 1L
                }

                info.set(
                    0,
                    size,
                    outputPtsUs,
                    extractor.sampleFlags,
                )
                muxer.writeSampleData(
                    muxTrack,
                    buffer,
                    info,
                )

                lastRawPtsByTrack[sourceTrack] =
                    rawPtsUs
                lastOutputPtsByTrack[sourceTrack] =
                    outputPtsUs
                extractor.advance()
            }

            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null

            validateRemuxedMp4(
                output = output,
                expectedDurationUs =
                    expectedDurationUs,
                expectedInputBytes =
                    expectedInputBytes,
            )
        } catch (_: Exception) {
            false
        } finally {
            runCatching {
                extractor.release()
            }
            if (muxerStarted) {
                runCatching {
                    muxer?.stop()
                }
            }
            runCatching {
                muxer?.release()
            }
            if (
                !output.exists() ||
                output.length() == 0L
            ) {
                output.delete()
            }
        }
    }

    private fun validateRemuxedMp4(
        output: File,
        expectedDurationUs: Long,
        expectedInputBytes: Long,
    ): Boolean {
        if (
            !output.exists() ||
            output.length() <= 0L
        ) {
            return false
        }

        if (
            expectedInputBytes > 0L &&
            output.length() <
                (
                    expectedInputBytes *
                        55L /
                        100L
                    )
        ) {
            output.delete()
            return false
        }

        if (expectedDurationUs > 5_000_000L) {
            val actualDurationUs =
                mediaDurationUs(output)
            if (
                actualDurationUs <= 0L ||
                actualDurationUs <
                    (
                        expectedDurationUs *
                            9L /
                            10L
                        ) ||
                actualDurationUs >
                    (
                        expectedDurationUs *
                            11L /
                            10L
                        )
            ) {
                output.delete()
                return false
            }
        }

        return true
    }

    private fun mediaDurationUs(
        file: File,
    ): Long {
        val retriever =
            MediaMetadataRetriever()
        return try {
            retriever.setDataSource(
                file.absolutePath
            )
            val durationMs =
                retriever.extractMetadata(
                    MediaMetadataRetriever
                        .METADATA_KEY_DURATION
                )
                    ?.toLongOrNull()
                    ?: 0L
            durationMs * 1_000L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching {
                retriever.release()
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
