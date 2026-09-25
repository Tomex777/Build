package com.tomex777.annie

import android.content.Context
import android.os.Environment
import android.webkit.CookieManager
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class AnnieDownloadSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val quality: String = "",
)

internal object ScriptVideoDownloadSource {
    private val height = Regex("(\\d{3,4})\\s*p", RegexOption.IGNORE_CASE)

    fun from(data: JSONObject): AnnieDownloadSource? {
        val topHeaders = data.optJSONObject("headers").stringMap()
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
        if (fromUrl in setOf("mp4", "mkv", "webm", "ts", "m4v", "avi", "mov")) {
            return fromUrl
        }
        return when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
            "video/mp4" -> "mp4"
            "video/x-matroska", "video/mkv" -> "mkv"
            "video/webm" -> "webm"
            "video/mp2t", "video/mpegts" -> "ts"
            "video/quicktime" -> "mov"
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
        if (item.sourceUrl.isBlank() || jobs[item.id]?.isActive == true) return
        val queued = item.copy(state = DownloadState.QUEUED, failureReason = "")
        publish(queued)
        val job = scope.launch {
            publish(queued.copy(state = DownloadState.DOWNLOADING))
            try {
                download(queued)
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
                jobs.remove(item.id)
            }
        }
        jobs[item.id] = job
    }

    fun pause(item: DownloadItem) {
        jobs.remove(item.id)?.cancel()
        publish(item.copy(state = DownloadState.PAUSED, failureReason = ""))
    }

    fun resume(item: DownloadItem) {
        enqueue(item.copy(state = DownloadState.QUEUED, failureReason = ""))
    }

    fun remove(item: DownloadItem) {
        jobs.remove(item.id)?.cancel()
        tempFile(item).delete()
        state.edit().remove(hlsIndexKey(item.id)).apply()
        item.localPath.takeIf(String::isNotBlank)?.let { runCatching { File(it).delete() } }
    }

    fun close() {
        jobs.values.forEach(Job::cancel)
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

        val first = open(item.sourceUrl, headers)
        try {
            val responseMime = first.contentType?.substringBefore(';')?.trim()
            if (AnnieDownloadNaming.isHls(first.url.toString(), item.sourceMimeType ?: responseMime)) {
                val playlist = first.inputStream.bufferedReader().use { it.readText() }
                downloadHls(item, first.url.toString(), playlist, headers)
            } else {
                downloadDirect(item, first, responseMime)
            }
        } finally {
            first.disconnect()
        }
    }

    private suspend fun downloadDirect(
        item: DownloadItem,
        initial: HttpURLConnection,
        responseMime: String?,
    ) {
        val resolvedUrl = initial.url.toString()
        initial.disconnect()
        val extension = AnnieDownloadNaming.extensionFor(item.sourceMimeType ?: responseMime, resolvedUrl)
        val finalFile = finalFile(item, extension)
        val temp = tempFile(item)
        temp.parentFile?.mkdirs()

        val existing = temp.length().coerceAtLeast(0L)
        val connection = open(item.sourceUrl, headers(item), existing.takeIf { it > 0L })
        try {
            val append = existing > 0L && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (!append && existing > 0L) temp.delete()
            val start = if (append) existing else 0L
            val expected = connection.contentLengthLong.takeIf { it > 0L }?.plus(start)
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
    ) {
        var playlistUrl = initialUrl
        var playlist = initialPlaylist
        AnnieHlsPlanner.selectMasterVariant(playlistUrl, playlist)?.let { variant ->
            val child = open(variant, headers)
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
        if (!temp.exists() && completed > 0) {
            completed = 0
            state.edit().putInt(hlsIndexKey(item.id), 0).apply()
        }

        FileOutputStream(temp, completed > 0).use { output ->
            for (index in completed until parts.size) {
                currentCoroutineContext().ensureActive()
                val boundary = temp.length()
                val connection = open(parts[index], headers)
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
                state.edit().putInt(hlsIndexKey(item.id), completed).apply()
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

        state.edit().remove(hlsIndexKey(item.id)).apply()
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
    ): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                connection.setRequestProperty(name, value)
            }
        }
        if (headers.keys.none { it.equals("Cookie", ignoreCase = true) }) {
            CookieManager.getInstance().getCookie(url)?.takeIf(String::isNotBlank)?.let {
                connection.setRequestProperty("Cookie", it)
            }
        }
        rangeStart?.takeIf { it > 0L }?.let {
            connection.setRequestProperty("Range", "bytes=$it-")
        }
        connection.connect()
        if (connection.responseCode !in 200..299) {
            val code = connection.responseCode
            connection.disconnect()
            error("Download request failed with HTTP $code.")
        }
        return connection
    }

    private fun headers(item: DownloadItem): Map<String, String> = runCatching {
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

    private fun hlsIndexKey(id: String) = "hls_index_$id"

    private fun publish(item: DownloadItem) {
        CoroutineScope(Dispatchers.Main).launch { onChanged(item) }
    }
}
