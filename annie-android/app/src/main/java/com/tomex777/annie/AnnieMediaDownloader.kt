package com.tomex777.annie

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Environment
import android.webkit.CookieManager
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

internal data class AnnieDownloadSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val quality: String = "",
    val browserSessionId: String? = null,
)

internal object ScriptVideoDownloadSource {
    private val height = Regex("(\\d{3,4})\\s*p", RegexOption.IGNORE_CASE)

    fun from(data: JSONObject): AnnieDownloadSource? {
        val topHeaders = data.optJSONObject("headers").stringMap()
        val browserSessionId = data.optString("browserSession").takeIf(String::isNotBlank)
        val qualities = data.optJSONArray("qualities")
        val candidates = buildList {
            if (qualities != null) {
                for (index in 0 until qualities.length()) {
                    val row = qualities.optJSONObject(index) ?: continue
                    val url = listOf(
                        row.optString("uri"),
                        row.optString("url"),
                        row.optString("streamUrl"),
                    ).firstOrNull(String::isNotBlank) ?: continue
                    val quality = row.optString("label")
                        .ifBlank { row.optString("quality") }
                        .ifBlank { "Source ${index + 1}" }
                    add(
                        AnnieDownloadSource(
                            url = url,
                            headers = topHeaders + row.optJSONObject("headers").stringMap(),
                            mimeType = row.optString("mimeType").takeIf(String::isNotBlank),
                            quality = quality,
                            browserSessionId = row.optString("browserSession").takeIf(String::isNotBlank) ?: browserSessionId,
                        )
                    )
                }
            }
        }
        if (candidates.isNotEmpty()) {
            return candidates.maxWithOrNull(
                compareBy<AnnieDownloadSource> {
                    height.find(it.quality)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
                }.thenBy { it.quality }
            ) ?: candidates.first()
        }

        val direct = listOf(
            data.optString("uri"),
            data.optString("url"),
            data.optString("streamUrl"),
        ).firstOrNull(String::isNotBlank) ?: return null
        return AnnieDownloadSource(
            url = direct,
            headers = topHeaders,
            mimeType = data.optString("mimeType").takeIf(String::isNotBlank),
            quality = data.optString("quality"),
            browserSessionId = browserSessionId,
        )
    }

    private fun JSONObject?.stringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        val value = this
        return buildMap {
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val entry = value.optString(key)
                if (key.isNotBlank() && entry.isNotBlank()) put(key, entry)
            }
        }
    }
}

internal object AnnieDownloadNaming {
    fun isHls(url: String, mimeType: String?): Boolean {
        val mime = mimeType?.lowercase().orEmpty()
        return url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
            mime.contains("mpegurl") ||
            mime.contains("m3u8")
    }

    fun extensionFor(mimeType: String?, url: String): String {
        val fromUrl = runCatching { URI(url).path.substringAfterLast('.', "").lowercase() }
            .getOrDefault("")
        if (fromUrl in setOf(
                "mp4", "mkv", "webm", "ts", "m4v", "avi", "mov", "3gp", "3g2", "ogv", "flv",
                "aac", "mp3", "m4a", "ogg", "opus", "flac", "wav", "aiff", "wma",
            )
        ) {
            return fromUrl
        }
        return when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
            "video/mp4" -> "mp4"
            "video/x-matroska", "video/mkv" -> "mkv"
            "video/webm" -> "webm"
            "video/mp2t", "video/mpegts" -> "ts"
            "video/quicktime" -> "mov"
            "video/3gpp", "audio/3gpp" -> "3gp"
            "video/3gpp2" -> "3g2"
            "video/ogg" -> "ogv"
            "video/x-flv" -> "flv"
            "audio/aac", "audio/x-aac" -> "aac"
            "audio/mpeg" -> "mp3"
            "audio/mp4", "audio/x-m4a" -> "m4a"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/aiff", "audio/x-aiff" -> "aiff"
            "audio/x-ms-wma" -> "wma"
            else -> "video"
        }
    }

    fun sanitize(value: String): String {
        val cleaned = value
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.')
        return cleaned.ifBlank { "Video" }
    }
}

internal data class AnnieHlsPlan(
    val initSegmentUrl: String?,
    val segmentUrls: List<String>,
    val mimeType: String,
    val extension: String,
) {
    val parts: List<String>
        get() = buildList {
            initSegmentUrl?.let(::add)
            addAll(segmentUrls)
        }
}

internal object AnnieHlsPlanner {
    fun selectMasterVariant(baseUrl: String, playlist: String): String? {
        val lines = playlist.lineSequence().map(String::trim).toList()
        val variants = mutableListOf<Pair<Long, String>>()
        lines.forEachIndexed { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) return@forEachIndexed
            val bandwidth = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
                .find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            val next = lines.drop(index + 1)
                .firstOrNull { it.isNotBlank() && !it.startsWith('#') }
                ?: return@forEachIndexed
            variants += bandwidth to resolve(baseUrl, next)
        }
        return variants.maxByOrNull { it.first }?.second
    }

    fun mediaPlan(baseUrl: String, playlist: String): AnnieHlsPlan {
        val lines = playlist.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val encrypted = lines.any {
            it.startsWith("#EXT-X-KEY:", ignoreCase = true) &&
                !it.contains("METHOD=NONE", ignoreCase = true)
        }
        require(!encrypted) { "Encrypted HLS downloads are not supported yet." }

        val init = lines.firstOrNull { it.startsWith("#EXT-X-MAP:", ignoreCase = true) }
            ?.let { Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1) }
            ?.let { resolve(baseUrl, it) }
        val segments = lines.filter { !it.startsWith('#') }.map { resolve(baseUrl, it) }
        require(segments.isNotEmpty()) { "The HLS playlist did not contain media segments." }

        val fragmentedMp4 = init != null ||
            segments.first().substringBefore('?').endsWith(".m4s", ignoreCase = true)
        return AnnieHlsPlan(
            initSegmentUrl = init,
            segmentUrls = segments,
            mimeType = if (fragmentedMp4) "video/mp4" else "video/mp2t",
            extension = if (fragmentedMp4) "mp4" else "ts",
        )
    }

    private fun resolve(baseUrl: String, value: String): String =
        runCatching { URI(baseUrl).resolve(value).toString() }.getOrDefault(value)
}

internal class AnnieMediaDownloader(
    private val context: Context,
    private val onChanged: (DownloadItem) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val state = context.getSharedPreferences("annie_download_transfer_v1", Context.MODE_PRIVATE)

    fun enqueue(item: DownloadItem) {
        if (item.sourceUrl.isBlank()) return
        val queued = item.copy(state = DownloadState.QUEUED, failureReason = "")
        val job = synchronized(jobs) {
            val previous = jobs[item.id]
            if (previous != null && !previous.isCompleted) return
            publish(queued)
            scope.launch(start = CoroutineStart.LAZY) {
                publish(queued.copy(state = DownloadState.DOWNLOADING))
                try {
                    downloadWithRecovery(queued)
                } catch (_: CancellationException) {
                    // pause/cancel decides the persisted state.
                } catch (failure: Throwable) {
                    publish(
                        queued.copy(
                            state = DownloadState.FAILED,
                            failureReason = failure.message ?: failure.javaClass.simpleName,
                        )
                    )
                } finally {
                    val self = currentCoroutineContext()[Job]
                    if (self != null) jobs.remove(item.id, self)
                }
            }.also { jobs[item.id] = it }
        }
        job.start()
    }

    fun pause(item: DownloadItem) {
        jobs[item.id]?.cancel()
        publish(item.copy(state = DownloadState.PAUSED, failureReason = ""))
    }

    fun resume(item: DownloadItem) {
        val queued = item.copy(state = DownloadState.QUEUED, failureReason = "")
        val previous = jobs[item.id]
        if (previous == null || previous.isCompleted) {
            enqueue(queued)
        } else {
            scope.launch {
                previous.cancelAndJoin()
                enqueue(queued)
            }
        }
    }

    fun remove(item: DownloadItem, onRemoved: () -> Unit = {}) {
        val previous = jobs[item.id]
        if (previous == null || previous.isCompleted) {
            removeFiles(item)
            onRemoved()
        } else {
            scope.launch {
                previous.cancelAndJoin()
                removeFiles(item)
                onRemoved()
            }
        }
    }

    private fun removeFiles(item: DownloadItem) {
        tempFile(item).delete()
        state.edit().remove(hlsIndexKey(item.id)).remove(hlsBytesKey(item.id)).apply()
        item.localPath.takeIf(String::isNotBlank)?.let { runCatching { File(it).delete() } }
    }

    fun close() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        scope.cancel()
    }

    private suspend fun download(item: DownloadItem) {
        val headers = runCatching {
            val json = JSONObject(item.headersJson.ifBlank { "{}" })
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.optString(key)
                    if (key.isNotBlank() && value.isNotBlank()) put(key, value)
                }
            }
        }.getOrDefault(emptyMap())

        val browserSession = item.browserSessionId?.let { id ->
            val session = AnnieBrowserSessionStore.get(context, id)
                ?: error("Unknown Annie browser session: $id")
            require(AnnieBrowserSessionStore.allows(session, item.sourceUrl)) {
                "Download URL is outside this browser session's allowed sites"
            }
            session
        }
        val sessionHeaders = browserSession?.let { session ->
            if (headers.keys.none { it.equals("User-Agent", true) }) {
                headers + ("User-Agent" to (session.userAgent ?: android.webkit.WebSettings.getDefaultUserAgent(context)))
            } else headers
        } ?: headers

        val first = open(item.sourceUrl, sessionHeaders, browserSession = browserSession)
        try {
            val responseMime = first.contentType?.substringBefore(';')?.trim()
            if (AnnieDownloadNaming.isHls(first.url.toString(), item.sourceMimeType ?: responseMime)) {
                val playlist = first.inputStream.bufferedReader().use { it.readText() }
                downloadHls(item, first.url.toString(), playlist, sessionHeaders, browserSession)
            } else {
                downloadDirect(item, first, responseMime, sessionHeaders, browserSession)
            }
        } finally {
            first.disconnect()
        }
    }

    private suspend fun downloadWithRecovery(item: DownloadItem) {
        var retry = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                download(item)
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (!isRecoverable(failure)) throw failure
                retry++
                val currentItem = DownloadStore.find(context, item.id) ?: item
                if (!hasValidatedInternet()) {
                    publish(currentItem.copy(state = DownloadState.WAITING_FOR_CONNECTION, failureReason = "Waiting for connection"))
                    awaitValidatedInternet()
                    retry = 0
                } else {
                    val waitMs = (1_000L shl (retry - 1).coerceAtMost(5)).coerceAtMost(30_000L)
                    publish(currentItem.copy(state = DownloadState.DOWNLOADING, failureReason = "Connection interrupted · retrying"))
                    delay(waitMs)
                }
            }
        }
    }

    private fun isRecoverable(failure: Throwable): Boolean = when (failure) {
        is DownloadHttpException -> failure.statusCode == 408 || failure.statusCode == 425 ||
            failure.statusCode == 429 || failure.statusCode in 500..599
        else -> generateSequence(failure) { it.cause }.any {
            it is SocketException || it is SocketTimeoutException || it is UnknownHostException ||
                it is ConnectException || it is NoRouteToHostException || it is java.io.EOFException ||
                it is javax.net.ssl.SSLException
        }
    }

    private fun hasValidatedInternet(network: Network? = null): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = network ?: manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private suspend fun awaitValidatedInternet() = suspendCancellableCoroutine<Unit> { continuation ->
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val completed = AtomicBoolean(false)
        val registered = AtomicBoolean(false)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (hasValidatedInternet(network)) resume()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                ) resume()
            }

            private fun resume() {
                if (!completed.compareAndSet(false, true)) return
                if (registered.get()) runCatching { manager.unregisterNetworkCallback(this) }
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        if (hasValidatedInternet()) {
            completed.set(true)
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        manager.registerDefaultNetworkCallback(callback)
        registered.set(true)
        continuation.invokeOnCancellation {
            if (registered.get()) runCatching { manager.unregisterNetworkCallback(callback) }
        }
        if (hasValidatedInternet()) callback.onAvailable(manager.activeNetwork ?: return@suspendCancellableCoroutine)
    }

    private suspend fun downloadDirect(
        item: DownloadItem,
        initial: HttpURLConnection,
        responseMime: String?,
        requestHeaders: Map<String, String>,
        browserSession: AnnieBrowserSession?,
    ) {
        val resolvedUrl = initial.url.toString()
        initial.disconnect()
        val extension = AnnieDownloadNaming.extensionFor(item.sourceMimeType ?: responseMime, resolvedUrl)
        val finalFile = finalFile(item, extension)
        val temp = tempFile(item)
        temp.parentFile?.mkdirs()

        val existing = temp.length().coerceAtLeast(0L)
        var connection = open(item.sourceUrl, requestHeaders, existing.takeIf { it > 0L }, browserSession)
        try {
            var append = existing > 0L && connection.responseCode == HttpURLConnection.HTTP_PARTIAL &&
                contentRangeStart(connection.getHeaderField("Content-Range")) == existing
            if (existing > 0L && connection.responseCode == HttpURLConnection.HTTP_PARTIAL && !append) {
                connection.disconnect()
                temp.delete()
                connection = open(item.sourceUrl, requestHeaders, browserSession = browserSession)
            }
            if (!append && existing > 0L) temp.delete()
            val start = if (append) existing else 0L
            val expected = contentRangeTotal(connection.getHeaderField("Content-Range"))
                ?: connection.contentLengthLong.takeIf { it > 0L }?.plus(start)
            connection.inputStream.use { input ->
                FileOutputStream(temp, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = start
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val progress = expected?.let {
                            (copied.toDouble() / it.toDouble()).toFloat().coerceIn(0f, 0.99f)
                        } ?: 0f
                        publish(
                            item.copy(
                                state = DownloadState.DOWNLOADING,
                                progress = progress,
                                bytesDone = copied,
                                bytesTotal = expected ?: 0L,
                                quality = item.quality,
                            )
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        finishFile(item, temp, finalFile, item.sourceMimeType ?: responseMime)
    }

    private suspend fun downloadHls(
        item: DownloadItem,
        initialUrl: String,
        initialPlaylist: String,
        headers: Map<String, String>,
        browserSession: AnnieBrowserSession?,
    ) {
        var playlistUrl = initialUrl
        var playlist = initialPlaylist
        AnnieHlsPlanner.selectMasterVariant(playlistUrl, playlist)?.let { variant ->
            val child = open(variant, headers, browserSession = browserSession)
            try {
                playlist = child.inputStream.bufferedReader().use { it.readText() }
                playlistUrl = child.url.toString()
            } finally {
                child.disconnect()
            }
        }

        val plan = AnnieHlsPlanner.mediaPlan(playlistUrl, playlist)
        val finalFile = finalFile(item, plan.extension)
        val temp = tempFile(item)
        temp.parentFile?.mkdirs()
        val parts = plan.parts
        var completed = state.getInt(hlsIndexKey(item.id), 0).coerceIn(0, parts.size)
        val committedBytes = state.getLong(hlsBytesKey(item.id), -1L)
        if (completed > 0 && (!temp.exists() || committedBytes < 0L || committedBytes > temp.length())) {
            completed = 0
            state.edit().putInt(hlsIndexKey(item.id), 0).putLong(hlsBytesKey(item.id), 0L).commit()
        } else if (completed > 0) {
            RandomAccessFile(temp, "rw").use { it.setLength(committedBytes) }
        } else {
            temp.delete()
            state.edit().putInt(hlsIndexKey(item.id), 0).putLong(hlsBytesKey(item.id), 0L).commit()
        }

        FileOutputStream(temp, completed > 0).use { output ->
            for (index in completed until parts.size) {
                currentCoroutineContext().ensureActive()
                val boundary = temp.length()
                val connection = open(parts[index], headers, browserSession = browserSession)
                try {
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                    output.flush()
                } catch (failure: Throwable) {
                    runCatching { RandomAccessFile(temp, "rw").use { it.setLength(boundary) } }
                    throw failure
                } finally {
                    connection.disconnect()
                }

                completed = index + 1
                state.edit().putInt(hlsIndexKey(item.id), completed).putLong(hlsBytesKey(item.id), temp.length()).commit()
                publish(
                    item.copy(
                        state = DownloadState.DOWNLOADING,
                        progress = (completed.toFloat() / parts.size.toFloat()).coerceIn(0f, 0.99f),
                        bytesDone = temp.length(),
                        bytesTotal = 0L,
                        quality = item.quality,
                    )
                )
            }
        }

        state.edit().remove(hlsIndexKey(item.id)).remove(hlsBytesKey(item.id)).apply()
        finishFile(item, temp, finalFile, plan.mimeType)
    }

    private fun finishFile(item: DownloadItem, temp: File, finalFile: File, mimeType: String?) {
        finalFile.parentFile?.mkdirs()
        if (finalFile.exists()) finalFile.delete()
        if (!temp.renameTo(finalFile)) {
            temp.inputStream().use { input ->
                FileOutputStream(finalFile).use { output -> input.copyTo(output) }
            }
            temp.delete()
        }
        publish(
            item.copy(
                state = DownloadState.COMPLETE,
                progress = 1f,
                bytesDone = finalFile.length(),
                bytesTotal = finalFile.length(),
                localPath = finalFile.absolutePath,
                failureReason = "",
                sourceMimeType = mimeType ?: item.sourceMimeType,
            )
        )
    }

    private fun finalFile(item: DownloadItem, extension: String): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "movies")
        val group = File(root, "Annie/" + AnnieDownloadNaming.sanitize(item.title))
        val base = buildString {
            if (item.unitNumber.isNotBlank()) append(item.unitNumber).append(" - ")
            append(AnnieDownloadNaming.sanitize(item.unitTitle.ifBlank { item.title }))
        }
        return File(group, "$base.$extension")
    }

    private fun tempFile(item: DownloadItem): File =
        File(context.cacheDir, "annie-downloads/${AnnieDownloadNaming.sanitize(item.id)}.part")

    private fun open(
        url: String,
        headers: Map<String, String>,
        rangeStart: Long? = null,
        browserSession: AnnieBrowserSession? = null,
    ): HttpURLConnection {
        var currentUrl = url
        var currentHeaders = headers
        repeat(if (browserSession == null) 1 else 9) { redirectCount ->
            if (browserSession != null) require(AnnieBrowserSessionStore.allows(browserSession, currentUrl)) {
                "Download redirect is outside this browser session's allowed sites"
            }
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = browserSession == null
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            currentHeaders.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotBlank()) connection.setRequestProperty(name, value)
            }
            if (currentHeaders.keys.none { it.equals("Cookie", ignoreCase = true) }) {
                CookieManager.getInstance().getCookie(currentUrl)?.takeIf(String::isNotBlank)?.let {
                    connection.setRequestProperty("Cookie", it)
                }
            }
            rangeStart?.takeIf { it > 0L }?.let { connection.setRequestProperty("Range", "bytes=$it-") }
            connection.connect()
            val code = connection.responseCode
            if (browserSession != null) {
                connection.headerFields.entries
                    .filter { it.key.equals("Set-Cookie", true) }
                    .flatMap { it.value.orEmpty() }
                    .forEach { CookieManager.getInstance().setCookie(currentUrl, it) }
                CookieManager.getInstance().flush()
            }
            val location = connection.getHeaderField("Location")
            if (browserSession != null && code in setOf(301, 302, 303, 307, 308) && !location.isNullOrBlank()) {
                if (redirectCount >= 8) {
                    connection.disconnect()
                    error("Download exceeded the browser session redirect limit.")
                }
                val next = URI(currentUrl).resolve(location).toString()
                if (!sameOrigin(currentUrl, next)) {
                    currentHeaders = currentHeaders.filterKeys { !it.equals("Cookie", true) && !it.equals("Authorization", true) }
                }
                connection.disconnect()
                currentUrl = next
                return@repeat
            }
            if (code !in 200..299) {
                connection.disconnect()
                throw DownloadHttpException(code)
            }
            return connection
        }
        error("Download redirect did not resolve to a resource.")
    }

    private fun sameOrigin(left: String, right: String): Boolean = runCatching {
        val a = URI(left); val b = URI(right)
        a.scheme.equals(b.scheme, true) && a.host.equals(b.host, true) && a.port == b.port
    }.getOrDefault(false)

    private fun contentRangeStart(value: String?): Long? =
        Regex("""bytes\s+(\d+)-\d+/(?:\d+|\*)""", RegexOption.IGNORE_CASE)
            .find(value.orEmpty())?.groupValues?.getOrNull(1)?.toLongOrNull()

    private fun contentRangeTotal(value: String?): Long? =
        Regex("""bytes\s+\d+-\d+/(\d+)""", RegexOption.IGNORE_CASE)
            .find(value.orEmpty())?.groupValues?.getOrNull(1)?.toLongOrNull()

    private fun hlsIndexKey(id: String) = "hls_index_$id"
    private fun hlsBytesKey(id: String) = "hls_bytes_$id"

    private class DownloadHttpException(val statusCode: Int) : RuntimeException("Download request failed with HTTP $statusCode.")

    private fun publish(item: DownloadItem) {
        CoroutineScope(Dispatchers.Main).launch { onChanged(item) }
    }
}
