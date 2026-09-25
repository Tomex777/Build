package app.nami.android

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response as OkHttpResponse

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
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

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

        var upstream: OkHttpResponse? = null
        return try {
            upstream = open(target, session)
            val status = Response.Status.lookup(upstream.code)
                ?: when {
                    upstream.code in 200..299 -> Response.Status.OK
                    upstream.code >= 500 -> Response.Status.SERVICE_UNAVAILABLE
                    else -> Response.Status.BAD_REQUEST
                }
            val contentType = upstream.body.contentType()
                ?.toString()
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "application/octet-stream"

            if (
                session.method != Method.HEAD &&
                isHls(upstream.request.url.toString(), contentType)
            ) {
                val baseUrl = upstream.request.url.toString()
                val body = upstream.body.string()
                upstream.close()
                upstream = null
                newFixedLengthResponse(
                    status,
                    "application/vnd.apple.mpegurl",
                    rewritePlaylist(body, baseUrl, target.headers),
                ).apply {
                    addHeader("Cache-Control", "no-store")
                }
            } else {
                val stream = if (session.method == Method.HEAD) {
                    upstream.close()
                    upstream = null
                    null
                } else {
                    ClosingInputStream(upstream.body.byteStream(), upstream).also {
                        upstream = null
                    }
                }

                val output = if (stream == null) {
                    newFixedLengthResponse(
                        status,
                        contentType,
                        ByteArrayInputStream(ByteArray(0)),
                        0L,
                    )
                } else if (stream.contentLength >= 0L) {
                    newFixedLengthResponse(
                        status,
                        contentType,
                        stream,
                        stream.contentLength,
                    )
                } else {
                    newChunkedResponse(status, contentType, stream)
                }
                stream?.response?.let { response ->
                    copyResponseHeader(response, output, "Content-Range")
                    copyResponseHeader(response, output, "Accept-Ranges")
                    copyResponseHeader(response, output, "ETag")
                    copyResponseHeader(response, output, "Last-Modified")
                }
                output
            }
        } catch (failure: Throwable) {
            upstream?.close()
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
        http.dispatcher.cancelAll()
        http.connectionPool.evictAll()
        super.stop()
    }

    private fun localUrl(id: String): String =
        "http://127.0.0.1:$listeningPort/p/$id"

    private fun open(target: Target, session: IHTTPSession): OkHttpResponse {
        val builder = Request.Builder().url(target.url)
        target.headers.forEach { (name, value) ->
            val safeName = name.replace("\r", "").replace("\n", "")
            val safeValue = value.replace("\r", "").replace("\n", "")
            if (safeName.isNotBlank() && safeValue.isNotBlank()) {
                builder.header(safeName, safeValue)
            }
        }
        builder.header("Accept-Encoding", "identity")
        session.headers["range"]?.takeIf { it.isNotBlank() }?.let {
            builder.header("Range", it)
        }
        if (session.method == Method.HEAD) builder.head() else builder.get()
        return http.newCall(builder.build()).execute()
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
        upstream: OkHttpResponse,
        response: Response,
        name: String,
    ) {
        upstream.header(name)?.takeIf { it.isNotBlank() }?.let {
            response.addHeader(name, it)
        }
    }

    private class ClosingInputStream(
        delegate: InputStream,
        val response: OkHttpResponse,
    ) : FilterInputStream(delegate) {
        val contentLength: Long = response.body.contentLength()

        override fun close() {
            try {
                super.close()
            } finally {
                response.close()
            }
        }
    }

    private companion object {
        val URI_ATTRIBUTE = Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE)
    }
}
