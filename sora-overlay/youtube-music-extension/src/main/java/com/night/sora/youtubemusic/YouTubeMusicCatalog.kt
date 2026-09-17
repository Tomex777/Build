package com.night.sora.youtubemusic

import android.util.Log
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_MUSIC
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import com.metrolist.innertube.models.YouTubeClient.Companion.IOS
import com.metrolist.innertube.models.YouTubeClient.Companion.IOS_RECENT
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

object YouTubeMusicCatalog {
    private const val SOURCE_ID = "youtube.music"
    private const val TAG = "SoraYouTubeMusic"
    private const val PLAYER_ATTEMPT_TIMEOUT_MS = 15_000L

    private val nativePlaybackClients = listOf(
        ANDROID_MUSIC,
        IOS_RECENT,
        IOS,
        ANDROID_VR_1_65_10,
        ANDROID_VR_1_43_32,
    )

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
        val response = withTimeoutOrNull(PLAYER_ATTEMPT_TIMEOUT_MS) {
            YouTube.player(
                videoId = id,
                client = ANDROID_MUSIC,
            ).getOrThrow()
        } ?: error("YouTube Music metadata request timed out")
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

        val failures = mutableListOf<String>()
        for (client in nativePlaybackClients) {
            val identity = "${client.clientName}/${client.clientVersion}"
            Log.i(TAG, "player start id=$id client=$identity")
            val response = withTimeoutOrNull(PLAYER_ATTEMPT_TIMEOUT_MS) {
                YouTube.player(
                    videoId = id,
                    client = client,
                    authenticated = false,
                ).getOrNull()
            }

            if (response == null) {
                Log.w(TAG, "player timeout-or-null id=$id client=$identity")
                failures += "$identity: timeout/network"
                continue
            }

            val status = response.playabilityStatus.status
            val reason = response.playabilityStatus.reason.orEmpty()
            val returnedVideoId = response.videoDetails?.videoId
            val formats = response.streamingData?.adaptiveFormats.orEmpty()
            Log.i(
                TAG,
                "player result id=$id client=$identity status=$status reason=$reason returned=$returnedVideoId adaptive=${formats.size}",
            )

            if (returnedVideoId != null && returnedVideoId != id) {
                Log.w(TAG, "wrong-video id=$id client=$identity returned=$returnedVideoId")
                failures += "$identity: wrong video"
                continue
            }
            if (status != "OK") {
                failures += "$identity: $status${reason.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty()}"
                continue
            }

            val audio = formats
                .asSequence()
                .filter { it.isAudio && it.isOriginal && !it.url.isNullOrBlank() }
                .sortedByDescending { format ->
                    (format.averageBitrate ?: format.bitrate) +
                        if (format.mimeType.startsWith("audio/webm")) 10_000 else 0
                }
                .toList()

            Log.i(
                TAG,
                "direct audio id=$id client=$identity count=${audio.size} mime=${audio.firstOrNull()?.mimeType.orEmpty()}",
            )
            if (audio.isEmpty()) {
                failures += "$identity: no direct audio"
                continue
            }

            return JSONArray().apply {
                audio.forEach { format ->
                    val url = format.url.orEmpty()
                    val headers = YouTubeClient.forStreamUrl(url).mediaHeaders()
                    val headersJson = JSONObject().apply {
                        headers.forEach { (name, value) -> put(name, value) }
                    }
                    put(
                        JSONObject()
                            .put("label", qualityLabel(format.mimeType, format.averageBitrate ?: format.bitrate))
                            .put("url", url)
                            .put("headers", headersJson)
                            .put("mimeType", format.mimeType.substringBefore(';'))
                            .put("bitrate", format.averageBitrate ?: format.bitrate)
                            .put("durationMs", format.approxDurationMs?.toLongOrNull() ?: 0L)
                    )
                }
            }.toString().also {
                Log.i(TAG, "resolved id=$id client=$identity streams=${audio.size}")
            }
        }

        error("No native YouTube Music client returned direct audio: ${failures.joinToString("; ")}")
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
