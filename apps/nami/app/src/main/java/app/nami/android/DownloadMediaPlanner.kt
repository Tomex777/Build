package app.nami.android

import app.nami.data.local.DownloadDirectoryLayout
import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
import app.nami.domain.AnimeRef
import app.nami.domain.EpisodeRef
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

internal data class HlsMediaPlan(
    val initSegmentUrl: String?,
    val segmentUrls: List<String>,
    val mimeType: String,
    val extension: String,
) {
    val allPartUrls: List<String>
        get() = buildList {
            initSegmentUrl?.let(::add)
            addAll(segmentUrls)
        }
}

/**
 * Pure HLS planning logic. Android/network/file I/O stays in [NamiDownloadManager].
 */
internal object HlsPlaylistPlanner {

    fun selectMasterVariant(baseUrl: String, playlist: String): String? {
        val lines = playlist.lineSequence().map(String::trim).toList()
        val candidates = mutableListOf<Pair<Long, String>>()

        lines.forEachIndexed { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF:")) return@forEachIndexed

            val bandwidth = Regex("""BANDWIDTH=(\d+)""")
                .find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?: 0L

            val next = lines.drop(index + 1)
                .firstOrNull { it.isNotBlank() && !it.startsWith('#') }
                ?: return@forEachIndexed

            candidates += bandwidth to resolveUrl(baseUrl, next)
        }

        return candidates.maxByOrNull { it.first }?.second
    }

    fun mediaPlan(baseUrl: String, playlist: String): HlsMediaPlan {
        val lines = playlist.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()

        val encrypted = lines.any { line ->
            line.startsWith("#EXT-X-KEY:") &&
                !line.contains("METHOD=NONE", ignoreCase = true)
        }
        require(!encrypted) {
            "Encrypted HLS downloads are not supported yet."
        }

        val initSegment = lines
            .firstOrNull { it.startsWith("#EXT-X-MAP:") }
            ?.let(::extractQuotedUri)
            ?.let { resolveUrl(baseUrl, it) }

        val segments = lines
            .filter { !it.startsWith('#') }
            .map { resolveUrl(baseUrl, it) }

        require(segments.isNotEmpty()) {
            "The HLS playlist did not contain any media segments."
        }

        val fragmentedMp4 = initSegment != null ||
            segments.first().substringBefore('?').endsWith(".m4s", ignoreCase = true)

        return HlsMediaPlan(
            initSegmentUrl = initSegment,
            segmentUrls = segments,
            mimeType = if (fragmentedMp4) "video/mp4" else "video/mp2t",
            extension = if (fragmentedMp4) "mp4" else "ts",
        )
    }

    internal fun resolveUrl(baseUrl: String, value: String): String =
        runCatching { URI(baseUrl).resolve(value).toString() }
            .getOrDefault(value)

    private fun extractQuotedUri(line: String): String? =
        Regex("""URI="([^"]+)"""")
            .find(line)
            ?.groupValues
            ?.getOrNull(1)
}

internal object DownloadMediaNaming {

    fun isHls(url: String, mimeType: String?): Boolean {
        val mime = mimeType?.lowercase().orEmpty()
        return url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
            mime.contains("mpegurl") ||
            mime.contains("m3u8")
    }

    fun extensionFor(
        mimeType: String,
        url: String,
        contentDisposition: String? = null,
    ): String {
        val fromDisposition = contentDisposition
            ?.let(::fileNameFromContentDisposition)
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()
        if (fromDisposition in setOf("mp4", "mkv", "webm", "ts", "m4v", "avi")) {
            return fromDisposition
        }

        val fromUrl = runCatching {
            URI(url).path.substringAfterLast('.', "").lowercase()
        }.getOrDefault("")

        if (fromUrl in setOf("mp4", "mkv", "webm", "ts", "m4v", "avi")) {
            return fromUrl
        }

        return when (mimeType.lowercase()) {
            "video/mp4" -> "mp4"
            "video/x-matroska", "video/mkv" -> "mkv"
            "video/webm" -> "webm"
            "video/mp2t" -> "ts"
            else -> "video"
        }
    }

    fun videoMimeFor(
        reportedMime: String,
        extension: String,
    ): String {
        val normalized = reportedMime.substringBefore(';').trim().lowercase()
        if (normalized.startsWith("video/")) return normalized

        return when (extension.lowercase()) {
            "mkv" -> "video/x-matroska"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "ts" -> "video/mp2t"
            "avi" -> "video/x-msvideo"
            else -> normalized
        }
    }

    fun episodeFileName(
        episode: AnimeEpisode,
        extension: String,
    ): String {
        val numberPrefix = episode.number?.let {
            if (it % 1.0 == 0.0) {
                "Episode " + it.toInt().toString().padStart(3, '0')
            } else {
                "Episode " + it
            }
        }

        val titleWithoutExtension = episode.title
            .removeSuffix("." + extension)
            .removeSuffix("." + extension.uppercase())
        val base = if (
            numberPrefix != null &&
            !titleWithoutExtension.contains(numberPrefix, ignoreCase = true)
        ) {
            numberPrefix + " - " + titleWithoutExtension
        } else {
            titleWithoutExtension
        }

        return DownloadDirectoryLayout.sanitize(base) + "." + extension
    }

    internal fun fileNameFromContentDisposition(value: String): String? {
        val utf8 = Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { encoded ->
                runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault(encoded)
            }
        if (!utf8.isNullOrBlank()) return utf8

        return Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE)
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}

internal object GoogleDriveDownloadPlanner {
    private val driveHosts = setOf(
        "drive.google.com",
        "docs.google.com",
        "drive.usercontent.google.com",
    )

    fun isDriveDownload(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.host?.lowercase() in driveHosts && fileId(url) != null
    }

    fun directDownloadUrl(url: String): String {
        val id = fileId(url) ?: return url
        return "https://drive.google.com/uc?export=download&id=" +
            URLEncoder.encode(id, "UTF-8")
    }

    fun fileId(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        Regex("""/file/d/([A-Za-z0-9_-]{10,})""")
            .find(uri.path.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }

        return uri.rawQuery.orEmpty()
            .split('&')
            .mapNotNull { part ->
                if (part.substringBefore('=') != "id") return@mapNotNull null
                val value = part.substringAfter('=', "")
                value.takeIf { it.isNotBlank() }?.let {
                    runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
                }
            }
            .firstOrNull()
    }

    fun confirmationUrl(html: String, baseUrl: String): String? {
        Regex("""href=["']([^"']*/uc\?export=download[^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return resolve(baseUrl, decodeHtml(it)) }

        val form = Regex(
            """<form[^>]+id=["']download-form["'][^>]*action=["']([^"']+)["'][^>]*>(.*?)</form>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)
        if (form != null) {
            val action = decodeHtml(form.groupValues[1])
            val fields = Regex(
                """<input[^>]+type=["']hidden["'][^>]*>""",
                RegexOption.IGNORE_CASE,
            ).findAll(form.groupValues[2]).mapNotNull { input ->
                val tag = input.value
                val name = Regex("""name=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .find(tag)?.groupValues?.getOrNull(1)
                val value = Regex("""value=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
                    .find(tag)?.groupValues?.getOrNull(1)
                if (name.isNullOrBlank() || value == null) {
                    null
                } else {
                    name to decodeHtml(value)
                }
            }.toList()

            val separator = if (action.contains('?')) '&' else '?'
            val query = fields.joinToString("&") { (name, value) ->
                URLEncoder.encode(name, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
            }
            return if (query.isBlank()) action else action + separator + query
        }

        Regex(""""downloadUrl":"([^"]+)"""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("""\u003d""", "=")
            ?.replace("""\u0026""", "&")
            ?.let { return decodeHtml(it) }

        return null
    }

    private fun decodeHtml(value: String): String =
        value.replace("&amp;", "&")

    private fun resolve(baseUrl: String, value: String): String =
        runCatching { URI(baseUrl).resolve(value).toString() }.getOrDefault(value)
}


internal object DownloadRecoveryPolicy {
    const val INTERRUPTED_MESSAGE = "Download was interrupted. Tap retry to continue."

    fun recoverState(state: NamiDownloadState): NamiDownloadState = when (state) {
        NamiDownloadState.QUEUED,
        NamiDownloadState.DOWNLOADING,
        -> NamiDownloadState.ERROR

        NamiDownloadState.DOWNLOADED,
        NamiDownloadState.ERROR,
        -> state
    }

    fun shouldDiscardPartialTarget(state: NamiDownloadState): Boolean =
        state == NamiDownloadState.QUEUED || state == NamiDownloadState.DOWNLOADING
}


internal object DownloadStoragePolicy {
    fun requiresLegacyWritePermission(
        sdkInt: Int,
        permissionGranted: Boolean,
    ): Boolean = sdkInt <= 28 && !permissionGranted
}


internal data class RetryDownloadRequest(
    val anime: AnimeDetails,
    val episode: AnimeEpisode,
    val relativeDirectory: String,
)

internal object DownloadRetryPlanner {
    fun create(status: NamiDownloadStatus): RetryDownloadRequest? {
        if (status.state != NamiDownloadState.ERROR) return null
        if (status.sourceId.isBlank() || status.sourceAnimeId.isBlank() || status.sourceEpisodeId.isBlank()) {
            return null
        }

        return RetryDownloadRequest(
            anime = AnimeDetails(
                ref = AnimeRef(
                    sourceId = status.sourceId,
                    sourceAnimeId = status.sourceAnimeId,
                ),
                title = status.animeTitle,
                sourceState = status.animeSourceState,
            ),
            episode = AnimeEpisode(
                ref = EpisodeRef(
                    sourceId = status.sourceId,
                    sourceAnimeId = status.sourceAnimeId,
                    sourceEpisodeId = status.sourceEpisodeId,
                ),
                title = status.episodeTitle,
                sourceState = status.episodeSourceState,
            ),
            relativeDirectory = status.relativePath,
        )
    }
}


internal object DownloadBatchPolicy {
    fun shouldEnqueue(state: NamiDownloadState?): Boolean =
        state == null || state == NamiDownloadState.ERROR
}
