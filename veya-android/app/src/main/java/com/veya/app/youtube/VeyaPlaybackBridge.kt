package com.veya.app.youtube

import dev.tomex.youtube.api.MediaFormat
import dev.tomex.youtube.api.ResolverFailure
import dev.tomex.youtube.api.VerifiedMedia
import dev.tomex.youtube.api.YouTubeEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

class VeyaPlaybackBridge(
    private val engine: YouTubeEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tracks = ConcurrentHashMap<String, TrackSource>()
    private val sessions = ConcurrentHashMap<String, Set<String>>()
    private val server = ServerSocket()

    val port: Int

    init {
        server.reuseAddress = true
        server.bind(
            InetSocketAddress(
                InetAddress.getByName(LOOPBACK_HOST),
                0
            )
        )
        port = server.localPort
        scope.launch {
            while (isActive) {
                val socket = try {
                    server.accept()
                } catch (t: Throwable) {
                    if (!isActive) break
                    continue
                }
                launch { handle(socket) }
            }
        }
    }

    fun register(selection: VeyaPlaybackSelection): VeyaBridgeSession {
        check(selection.proven)

        val sessionId = UUID.randomUUID().toString()
        val videoToken = UUID.randomUUID().toString()
        val audioToken = UUID.randomUUID().toString()

        tracks[videoToken] = TrackSource.from(selection.videoId, selection.video)
        tracks[audioToken] = TrackSource.from(selection.videoId, selection.audio)
        sessions[sessionId] = setOf(videoToken, audioToken)

        return VeyaBridgeSession(
            id = sessionId,
            videoUrl = localUrl(videoToken),
            audioUrl = localUrl(audioToken)
        )
    }

    fun unregister(sessionId: String) {
        sessions.remove(sessionId)?.forEach(tracks::remove)
    }

    fun close() {
        runCatching { server.close() }
        tracks.clear()
        sessions.clear()
        scope.cancel()
    }

    private fun localUrl(token: String): String =
        "http://localhost:$port/media/$token"

    private suspend fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 30_000
            val input = client.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
            val requestLine = input.readLine() ?: return
            val request = requestLine.split(' ')
            if (request.size < 2) return

            val method = request[0].uppercase()
            val path = runCatching { URI(request[1]).path }.getOrNull() ?: request[1]
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase()] =
                        line.substring(colon + 1).trim()
                }
            }

            if (method != "GET" && method != "HEAD") {
                respondSimple(client, 405, "Method Not Allowed")
                return
            }

            val token = path.removePrefix("/media/").takeIf {
                path.startsWith("/media/") && it.isNotBlank() && !it.contains('/')
            }
            val source = token?.let(tracks::get)
            if (source == null) {
                respondSimple(client, 404, "Not Found")
                return
            }
            if (source.invalidReason.get() != null) {
                respondSimple(client, 409, "Media Changed")
                return
            }

            val total = source.totalBytes
            if (total == null || total <= 0L) {
                respondSimple(client, 503, "Length Unavailable")
                return
            }

            val range = parseRange(headers["range"], total)
            if (range == null && headers.containsKey("range")) {
                respondRangeNotSatisfiable(client, total)
                return
            }

            val start = range?.first ?: 0L
            val endInclusive = range?.last ?: (total - 1L)
            val partial = range != null
            val length = endInclusive - start + 1L

            val out = BufferedOutputStream(client.getOutputStream(), 256 * 1024)
            writeHeaders(
                out = out,
                status = if (partial) 206 else 200,
                statusText = if (partial) "Partial Content" else "OK",
                mimeType = source.mimeType,
                contentLength = length,
                contentRange = if (partial) "bytes $start-$endInclusive/$total" else null
            )
            out.flush()

            if (method == "HEAD") return

            var offset = start
            var remaining = length
            try {
                while (remaining > 0L) {
                    val limit = min(MAX_ENGINE_CHUNK.toLong(), remaining).toInt()
                    val format = source.format.get()
                    val chunk = engine.fetchChunkWithRefresh(
                        source.videoId,
                        format,
                        offset,
                        limit
                    )

                    chunk.totalBytes?.let { refreshedTotal ->
                        if (refreshedTotal != total) {
                            source.invalidReason.compareAndSet(
                                null,
                                "content length changed from $total to $refreshedTotal"
                            )
                            throw ResolverFailure.ContentLengthChanged(
                                "Refreshed media length changed"
                            )
                        }
                    }

                    source.format.set(chunk.format)
                    if (chunk.bytes.isEmpty()) break

                    val allowed = min(chunk.bytes.size.toLong(), remaining).toInt()
                    out.write(chunk.bytes, 0, allowed)
                    out.flush()
                    offset += allowed
                    remaining -= allowed
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: java.io.IOException) {
                // Seeking closes the old range request; the next VLC request opens a new one.
            } finally {
                runCatching { out.flush() }
            }
        }
    }

    private fun parseRange(raw: String?, total: Long): LongRange? {
        if (raw == null) return null
        val match = RANGE.matchEntire(raw.trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val requestedEnd = match.groupValues[2].takeIf(String::isNotBlank)?.toLongOrNull()
        if (start < 0L || start >= total) return null
        val end = (requestedEnd ?: total - 1L).coerceAtMost(total - 1L)
        if (end < start) return null
        return start..end
    }

    private fun writeHeaders(
        out: BufferedOutputStream,
        status: Int,
        statusText: String,
        mimeType: String,
        contentLength: Long,
        contentRange: String?
    ) {
        val text = buildString {
            append("HTTP/1.1 $status $statusText\r\n")
            append("Content-Type: ${mimeType.substringBefore(';')}\r\n")
            append("Content-Length: $contentLength\r\n")
            append("Accept-Ranges: bytes\r\n")
            contentRange?.let { append("Content-Range: $it\r\n") }
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        out.write(text.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun respondSimple(socket: Socket, status: Int, text: String) {
        val body = text.toByteArray(StandardCharsets.UTF_8)
        val out = BufferedOutputStream(socket.getOutputStream())
        val header = buildString {
            append("HTTP/1.1 $status $text\r\n")
            append("Content-Type: text/plain; charset=utf-8\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.US_ASCII))
        out.write(body)
        out.flush()
    }

    private fun respondRangeNotSatisfiable(socket: Socket, total: Long) {
        val out = BufferedOutputStream(socket.getOutputStream())
        val header = buildString {
            append("HTTP/1.1 416 Range Not Satisfiable\r\n")
            append("Content-Range: bytes " + "*" + "/$total\r\n")
            append("Content-Length: 0\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(StandardCharsets.US_ASCII))
        out.flush()
    }

    private data class TrackSource(
        val videoId: String,
        val stableIdentity: String,
        val mimeType: String,
        val totalBytes: Long?,
        val format: AtomicReference<MediaFormat>,
        val invalidReason: AtomicReference<String?> = AtomicReference(null)
    ) {
        companion object {
            fun from(videoId: String, verified: VerifiedMedia): TrackSource =
                TrackSource(
                    videoId = videoId,
                    stableIdentity = verified.format.stableIdentity,
                    mimeType = verified.format.mimeType,
                    totalBytes = verified.format.totalBytesFrom(verified.proof),
                    format = AtomicReference(verified.format)
                )
        }
    }

    companion object {
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val MAX_ENGINE_CHUNK = 4 * 1024 * 1024
        private val RANGE = Regex("bytes=(\\d+)-(\\d*)", RegexOption.IGNORE_CASE)
    }
}

data class VeyaBridgeSession(
    val id: String,
    val videoUrl: String,
    val audioUrl: String
)
