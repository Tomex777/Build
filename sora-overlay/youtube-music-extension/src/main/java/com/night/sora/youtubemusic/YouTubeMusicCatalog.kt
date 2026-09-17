package com.night.sora.youtubemusic

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_MUSIC
import org.json.JSONArray
import org.json.JSONObject

object YouTubeMusicCatalog {
    private const val SOURCE_ID = "youtube.music"

    suspend fun browse(sourceId: String): String {
        requireSource(sourceId)
        return search(sourceId, "top songs")
    }

    suspend fun search(sourceId: String, query: String): String {
        requireSource(sourceId)
        ensureVisitorData()
        val clean = query.trim().ifBlank { "top songs" }
        val result = YouTube.search(clean, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
        val songs = result.items.filterIsInstance<SongItem>().take(30)
        return JSONArray().apply {
            songs.forEach { song ->
                val artists = song.artists.joinToString(", ") { it.name }
                val album = song.album?.name.orEmpty()
                val subtitle = listOf(artists, album).filter { it.isNotBlank() }.joinToString(" · ")
                put(
                    JSONObject()
                        .put("id", song.id)
                        .put("title", song.title)
                        .put("subtitle", subtitle)
                        .put("artworkUrl", song.thumbnail)
                        .put("durationSeconds", song.duration ?: 0)
                        .put("explicit", song.explicit)
                )
            }
        }.toString()
    }

    suspend fun details(sourceId: String, id: String): String {
        requireSource(sourceId)
        ensureVisitorData()
        val response = YouTube.player(
            videoId = id,
            client = ANDROID_MUSIC,
        ).getOrThrow()
        val details = response.videoDetails
        return JSONObject()
            .put("description", listOfNotNull(details?.author, details?.title).joinToString(" · "))
            .put("status", response.playabilityStatus.status)
            .put("durationSeconds", details?.lengthSeconds?.toLongOrNull() ?: 0L)
            .toString()
    }

    suspend fun streams(sourceId: String, id: String): String {
        requireSource(sourceId)
        ensureVisitorData()
        val response = YouTube.player(
            videoId = id,
            client = ANDROID_MUSIC,
        ).getOrThrow()
        if (response.playabilityStatus.status != "OK") {
            error(response.playabilityStatus.reason ?: "YouTube Music returned ${response.playabilityStatus.status}")
        }

        val audio = response.streamingData?.adaptiveFormats
            .orEmpty()
            .asSequence()
            .filter { it.isAudio && it.isOriginal && !it.url.isNullOrBlank() }
            .sortedByDescending { format ->
                (format.averageBitrate ?: format.bitrate) +
                    if (format.mimeType.startsWith("audio/webm")) 10_000 else 0
            }
            .toList()

        if (audio.isEmpty()) error("YouTube Music returned no direct audio stream on the native fast path")

        return JSONArray().apply {
            audio.forEach { format ->
                put(
                    JSONObject()
                        .put("label", qualityLabel(format.mimeType, format.averageBitrate ?: format.bitrate))
                        .put("url", format.url)
                        .put("mimeType", format.mimeType.substringBefore(';'))
                        .put("bitrate", format.averageBitrate ?: format.bitrate)
                        .put("durationMs", format.approxDurationMs?.toLongOrNull() ?: 0L)
                )
            }
        }.toString()
    }

    private suspend fun ensureVisitorData() {
        if (YouTube.visitorData.isNullOrBlank()) {
            YouTube.visitorData = YouTube.visitorData().getOrNull()
        }
    }

    private fun requireSource(sourceId: String) {
        require(sourceId == SOURCE_ID) { "Unsupported YouTube Music source: $sourceId" }
    }

    private fun qualityLabel(mimeType: String, bitrate: Int): String {
        val codec = when {
            mimeType.contains("opus", ignoreCase = true) -> "Opus"
            mimeType.contains("mp4a", ignoreCase = true) -> "AAC"
            else -> "Audio"
        }
        val kbps = (bitrate / 1000).coerceAtLeast(1)
        return "YouTube Music · $codec · $kbps kbps"
    }
}
