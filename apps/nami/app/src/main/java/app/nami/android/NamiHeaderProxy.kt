package app.nami.android

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Loopback transport for source-owned HTTP headers that libVLC cannot express
 * directly (notably Origin and arbitrary extension headers).
 *
 * HLS child playlists, encryption keys and media segments are rewritten back
 * through this proxy so the same exact headers survive every nested request.
 */
internal class NamiHeaderProxy : NanoHTTPD("127.0.0.1", 0) {
    private data class Target(
        val url: String,
        val headers: Map<String, String>,
    )

    private val nextId = AtomicLong(0)
    private val targets = ConcurrentHashMap<String, Target>()
    private val targetIds = ConcurrentHashMap<Target, String>()

    init {
        start(SOCKET_READ_TIMEOUT, true)
    }

    fun wrap(url: String, headers: Map<String, String>): String {
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) {
            return url
        }
        if (headers.isEmpty()) return url

        val target = Target(url, headers.toMap())
        val existing = targetIds[target]
        if (existing != null) return localUrl(existing)

        val candidate = nextId.incrementAndGet().toString(36)
        val id = targetIds.putIfAbsent(target, candidate) ?: candidate
        if (id == candidate) targets[id] = target
        return localUrl(id)
    }

    override fun serve(session: IHTTPSession): Response {
        val id = session.uri.removePrefix("/p/").substringBefore('/')
        val target = targets[id]
            ?: return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Unknown Nami media target.",
            )

        var connection: HttpURLConnection? = null
        return try {
            val upstream = open(target, session)
            connection = upstream
            val status = Response.Status.lookup(upstream.responseCode)
                ?: when {
                    upstream.responseCode in 200..299 -> Response.Status.OK
                    upstream.responseCode >= 500 -> Response.Status.SERVICE_UNAVAILABLE
                    else -> Response.Status.BAD_REQUEST
                }
            val contentType = upstream.contentType
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "application/octet-stream"

            if (session.method != Method.HEAD && isHls(upstream.url.toString(), contentType)) {
                val baseUrl = upstream.url.toString()
                val input = if (upstream.responseCode >= 400) {
                    upstream.errorStream
                } else {
                    upstream.inputStream
                } ?: ByteArrayInputStream(ByteArray(0))
                val body = input.bufferedReader().use { it.readText() }
                upstream.disconnect()
                connection = null
                newFixedLengthResponse(
                    status,
                    "application/vnd.apple.mpegurl",
                    rewritePlaylist(body, baseUrl, target.headers),
                ).apply {
                    addHeader("Cache-Control", "no-store")
                }
            } else {
                val body = when {
                    session.method == Method.HEAD -> ByteArrayInputStream(ByteArray(0))
                    upstream.responseCode >= 400 -> upstream.errorStream
                    else -> upstream.inputStream
                } ?: ByteArrayInputStream(ByteArray(0))
                val stream = DisconnectingInputStream(body, upstream)
                connection = null
                val response = if (
                    stream.connectionLength >= 0L &&
                    session.method != Method.HEAD
                ) {
                    newFixedLengthResponse(
                        status,
                        contentType,
                        stream,
                        stream.connectionLength,
                    )
                } else {
                    newChunkedResponse(status, contentType, stream)
                }
                copyResponseHeader(upstream, response, "Content-Range")
                copyResponseHeader(upstream, response, "Accept-Ranges")
                copyResponseHeader(upstream, response, "ETag")
                copyResponseHeader(upstream, response, "Last-Modified")
                response
            }
        } catch (failure: Throwable) {
            connection?.disconnect()
            newFixedLengthResponse(
                Response.Status.SERVICE_UNAVAILABLE,
                MIME_PLAINTEXT,
                failure.message ?: "Nami media proxy request failed.",
            )
        }
    }

    override fun stop() {
        targets.clear()
        targetIds.clear()
        super.stop()
    }

    private fun localUrl(id: String): String =
        "http://127.0.0.1:$listeningPort/p/$id"

    private fun open(target: Target, session: IHTTPSession): HttpURLConnection {
        val connection = URL(target.url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.requestMethod = if (session.method == Method.HEAD) "HEAD" else "GET"
        connection.setRequestProperty("Accept-Encoding", "identity")
        target.headers.forEach { (name, value) ->
            val safeName = name.replace("\r", "").replace("\n", "")
            val safeValue = value.replace("\r", "").replace("\n", "")
            if (safeName.isNotBlank() && safeValue.isNotBlank()) {
                connection.setRequestProperty(safeName, safeValue)
            }
        }
        session.headers["range"]?.takeIf { it.isNotBlank() }?.let {
            connection.setRequestProperty("Range", it)
        }
        connection.connect()
        return connection
    }

    private fun rewritePlaylist(
        playlist: String,
        baseUrl: String,
        headers: Map<String, String>,
    ): String {
        return playlist.lineSequence().joinToString("\n") { rawLine ->
            val line = rawLine.trimEnd('\r')
            when {
                line.isBlank() -> line
                line.startsWith("#") -> URI_ATTRIBUTE.replace(line) { match ->
                    val absolute = resolve(baseUrl, match.groupValues[1])
                    "URI=\"${wrap(absolute, headers)}\""
                }
                else -> wrap(resolve(baseUrl, line), headers)
            }
        }
    }

    private fun resolve(baseUrl: String, value: String): String =
        runCatching { URI(baseUrl).resolve(value).toString() }
            .getOrElse { value }

    private fun isHls(url: String, mimeType: String): Boolean =
        url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
            mimeType.contains("mpegurl", ignoreCase = true)

    private fun copyResponseHeader(
        connection: HttpURLConnection,
        response: Response,
        name: String,
    ) {
        connection.getHeaderField(name)?.takeIf { it.isNotBlank() }?.let {
            response.addHeader(name, it)
        }
    }

    private class DisconnectingInputStream(
        delegate: InputStream,
        val connection: HttpURLConnection,
    ) : FilterInputStream(delegate) {
        val connectionLength: Long = connection.contentLengthLong

        override fun close() {
            try {
                super.close()
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        val URI_ATTRIBUTE = Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE)
    }
}
