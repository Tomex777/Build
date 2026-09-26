package com.night.spotui.playback

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.EOFException

/**
 * Reads googlevideo-style streams in bounded ranges instead of one open-ended
 * response. This mirrors the buffering strategy used by the upstream player and
 * avoids CDN pacing that can leave Media3 stuck at 0:00 on some networks.
 */
@UnstableApi
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkBytes: Long,
) : DataSource {
    private var baseSpec: DataSpec? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var chunkRemaining = 0L
    private var chunkOpen = false
    private var passthrough = false
    private var chunkStart = 0L
    private var chunkRequested = 0L
    private var chunkReceived = 0L
    private var chunksOpened = 0

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        bytesRemaining = 0L
        chunkRemaining = 0L
        chunkOpen = false
        baseSpec = dataSpec
        position = dataSpec.position
        val uriLength = dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()
            ?.takeIf { it > 0L }
        val requestedLength = dataSpec.length.takeIf { it != C.LENGTH_UNSET.toLong() && it >= 0L }
        val availableLength = requestedLength
            ?: uriLength?.let { total -> (total - position).coerceAtLeast(0L) }

        if (availableLength == null) {
            // Non-Googlevideo sources may not expose a stable length. Preserve
            // ordinary Media3 behavior when a bounded request cannot be derived.
            passthrough = true
            chunkOpen = true
            return try {
                upstream.open(dataSpec)
            } catch (failure: Throwable) {
                logOpenFailure(dataSpec, failure)
                throw failure
            }
        }

        passthrough = false
        bytesRemaining = availableLength
        chunksOpened = 0
        if (bytesRemaining > 0L) openChunk()
        return bytesRemaining
    }

    private fun openChunk() {
        val length = minOf(chunkBytes, bytesRemaining)
        chunkStart = position
        chunkRequested = length
        chunkReceived = 0L
        val spec = requireNotNull(baseSpec).buildUpon()
            .setPosition(position)
            .setLength(length)
            .build()
        val openedLength = try {
            upstream.open(spec)
        } catch (failure: Throwable) {
            logOpenFailure(spec, failure)
            throw failure
        }
        chunkRemaining = if (openedLength == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            openedLength.coerceAtMost(length)
        }
        chunkOpen = true
        chunksOpened += 1
        if (chunksOpened == 1 || chunksOpened % 8 == 0) {
            Log.i(
                TAG,
                "range open host=${host(spec.uri)} start=$chunkStart requested=$length " +
                responseSummary(upstream.responseHeaders),
            )
        }
        if (chunkRemaining == 0L) {
            throw EOFException(
                "Audio range opened empty at $position with $bytesRemaining bytes remaining " +
                    "(host=${host(spec.uri)})",
            )
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) return upstream.read(buffer, offset, length)
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        repeat(3) {
            if (chunkRemaining == 0L) {
                closeChunk()
                openChunk()
            }
            val read = upstream.read(buffer, offset, minOf(length.toLong(), chunkRemaining).toInt())
            if (read != C.RESULT_END_OF_INPUT) {
                position += read
                chunkRemaining -= read
                bytesRemaining -= read
                chunkReceived += read
                if (chunkRemaining == 0L) {
                    Log.i(
                        TAG,
                        "range complete host=${host(requireNotNull(baseSpec).uri)} start=$chunkStart " +
                            "requested=$chunkRequested received=$chunkReceived " +
                            responseSummary(upstream.responseHeaders),
                    )
                }
                return read
            }
            Log.e(
                TAG,
                "short range host=${host(requireNotNull(baseSpec).uri)} start=$chunkStart " +
                    "requested=$chunkRequested received=$chunkReceived remaining=$bytesRemaining",
            )
            chunkRemaining = 0L
        }
        throw EOFException(
            "Audio range ended early at $position; $bytesRemaining bytes remain " +
                "(range start=$chunkStart requested=$chunkRequested received=$chunkReceived, " +
                "host=${host(requireNotNull(baseSpec).uri)})",
        )
    }

    private fun closeChunk() {
        if (chunkOpen) {
            upstream.close()
            chunkOpen = false
        }
    }

    override fun getUri(): Uri? = upstream.uri ?: baseSpec?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        closeChunk()
        baseSpec = null
        bytesRemaining = 0L
        chunkRemaining = 0L
    }

    private fun logOpenFailure(spec: DataSpec, failure: Throwable) {
        val response = generateSequence(failure) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()
        Log.e(
            TAG,
            "range open failed host=${host(spec.uri)} start=${spec.position} " +
                "requested=${spec.length} status=${response?.responseCode ?: -1} " +
                "responseHeaders=${responseSummary(response?.headerFields.orEmpty())} " +
                "causes=${generateSequence(failure) { it.cause }.take(4).joinToString(">") { it.javaClass.simpleName }}",
        )
    }

    private fun responseSummary(headers: Map<String, List<String>>): String {
        val allowed = setOf("content-length", "content-range", "content-type", "accept-ranges")
        return headers.entries
            .filter { it.key.lowercase() in allowed }
            .joinToString(" ") { (name, values) ->
                "${name.lowercase()}=${values.joinToString(",").take(120)}"
            }
            .ifBlank { "responseHeaders=none" }
    }

    private fun host(uri: Uri): String = uri.host.orEmpty().lowercase().take(100).ifBlank { "unknown" }

    class Factory(
        private val upstream: DataSource.Factory,
        private val chunkBytes: Long = 2L * 1024L * 1024L,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ChunkedDataSource(upstream.createDataSource(), chunkBytes)
    }

    companion object {
        private const val TAG = "LyraAudioRange"
    }
}
