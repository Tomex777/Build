package dev.tomex.youtube.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

interface PlayerScriptLocator {
    suspend fun currentPlayerJavaScriptUrl(): String?
}

object NoPlayerScriptLocator : PlayerScriptLocator {
    override suspend fun currentPlayerJavaScriptUrl(): String? = null
}

/**
 * Discovers YouTube's current iframe player revision without executing remote JavaScript.
 *
 * The iframe bootstrap is bounded to a small response, redirects stay on HTTPS www.youtube.com,
 * and the returned player URL is normalized through the same strict player-script origin policy
 * used by the rest of the engine.
 */
class HttpIframePlayerScriptLocator(
    private val maxBytes: Int = 512 * 1024,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000
) : PlayerScriptLocator {
    init {
        require(maxBytes in 16 * 1024..2 * 1024 * 1024)
        require(connectTimeoutMs in 1_000..60_000)
        require(readTimeoutMs in 1_000..60_000)
    }

    override suspend fun currentPlayerJavaScriptUrl(): String? = withContext(Dispatchers.IO) {
        var current = "https://www.youtube.com/iframe_api"
        var connection: HttpURLConnection? = null
        var redirects = 0
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val parsed = runCatching { URL(current) }.getOrNull() ?: return@withContext null
                if (!trustedYouTube(parsed)) return@withContext null
                val candidate = (parsed.openConnection() as HttpURLConnection).apply {
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    setRequestProperty("Accept", "application/javascript,text/javascript,*/*;q=0.1")
                }
                connection = candidate
                val status = candidate.responseCode
                if (MediaRedirectPolicy.isRedirect(status)) {
                    val next = MediaRedirectPolicy.nextUrl(current, candidate.getHeaderField("Location"))
                        ?: return@withContext null
                    val nextUrl = runCatching { URL(next) }.getOrNull() ?: return@withContext null
                    if (!trustedYouTube(nextUrl)) return@withContext null
                    candidate.disconnect()
                    connection = null
                    redirects++
                    if (redirects > 3) return@withContext null
                    current = next
                    continue
                }
                if (status !in 200..299) return@withContext null
                val declared = candidate.contentLengthLong
                if (declared > maxBytes) return@withContext null
                val output = ByteArrayOutputStream(
                    if (declared in 1..maxBytes.toLong()) declared.toInt() else 32 * 1024
                )
                candidate.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (output.size() + read > maxBytes) return@withContext null
                        output.write(buffer, 0, read)
                    }
                }
                val bootstrap = output.toByteArray().toString(Charsets.UTF_8).replace("\\/", "/")
                val revision = Regex("""player/([A-Za-z0-9_-]{8,64})/""")
                    .find(bootstrap)
                    ?.groupValues
                    ?.get(1)
                    ?: return@withContext null
                return@withContext PlayerUrlTransforms.normalizePlayerJavaScriptUrl(
                    "/s/player/$revision/player_es6.vflset/en_US/base.js"
                )
            }
            @Suppress("UNREACHABLE_CODE")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun trustedYouTube(url: URL): Boolean =
        url.protocol.equals("https", ignoreCase = true) &&
            url.host.equals("www.youtube.com", ignoreCase = true) &&
            url.userInfo == null
}
