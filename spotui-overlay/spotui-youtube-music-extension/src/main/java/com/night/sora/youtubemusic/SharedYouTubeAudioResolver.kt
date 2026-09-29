package com.night.sora.youtubemusic

import android.net.Uri
import com.metrolist.innertube.YouTube
import dev.tomex.youtube.api.MediaFormat
import dev.tomex.youtube.api.MediaTransferCheckpoint
import dev.tomex.youtube.api.SessionProvider
import dev.tomex.youtube.api.VerifiedMedia
import dev.tomex.youtube.api.YouTubeEngine
import dev.tomex.youtube.core.YouTubeEngineFactory
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lyra's sole playback-resolution adapter.
 *
 * Catalog/search remain owned by the YouTube Music extension, while player discovery,
 * signature/n handling, client strategy, URL refresh and CDN verification stay inside
 * the reusable shared engine.
 */
object SharedYouTubeAudioResolver {
    private val engine: YouTubeEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        YouTubeEngineFactory.create(LyraYouTubeSession)
    }

    suspend fun resolveStreams(videoId: String): String {
        require(videoId.isNotBlank())
        val verified = engine.resolveVerifiedAudio(videoId, preferredContainer = "mp4")
        return JSONArray().put(toJson(verified)).toString()
    }

    suspend fun refreshStream(videoId: String, stableFormatIdentity: String): String {
        require(videoId.isNotBlank())
        require(stableFormatIdentity.isNotBlank())
        return toJson(engine.refreshMediaVerified(videoId, stableFormatIdentity)).toString()
    }

    /** Returns a real checkpoint the source host can persist with its download state. */
    suspend fun fetchChunkCheckpoint(
        videoId: String,
        stableFormatIdentity: String,
        startByte: Long,
        byteLimit: Int
    ): String {
        require(videoId.isNotBlank() && stableFormatIdentity.isNotBlank())
        require(startByte >= 0 && byteLimit in 512..1_048_576)
        val refreshed = engine.refreshMedia(videoId, stableFormatIdentity)
        val chunk = engine.fetchChunkWithRefresh(videoId, refreshed, startByte, byteLimit)
        val checkpoint = chunk.checkpoint(videoId)
        return JSONObject()
            .put("stableIdentity", refreshed.stableIdentity)
            .put("startByte", chunk.startByte)
            .put("bytesRead", chunk.bytes.size)
            .put("contentRange", chunk.contentRange)
            .put("checkpoint", JSONObject()
                .put("videoId", checkpoint.videoId)
                .put("stableFormatIdentity", checkpoint.stableFormatIdentity)
                .put("nextByteOffset", checkpoint.nextByteOffset)
                .put("totalBytes", checkpoint.totalBytes ?: JSONObject.NULL))
            .toString()
    }

    /** Recreates the engine from host-persisted checkpoint fields before fetching the next range. */
    suspend fun resumeFromCheckpoint(checkpointJson: String, byteLimit: Int): String {
        require(byteLimit in 512..1_048_576)
        val json = JSONObject(checkpointJson)
        val checkpoint = MediaTransferCheckpoint(
            videoId = json.getString("videoId"),
            stableFormatIdentity = json.getString("stableFormatIdentity"),
            nextByteOffset = json.getLong("nextByteOffset"),
            totalBytes = json.optLong("totalBytes").takeIf { it > 0 }
        )
        val recreatedEngine = YouTubeEngineFactory.create(LyraYouTubeSession)
        val chunk = recreatedEngine.fetchChunkFromCheckpoint(checkpoint, byteLimit)
        val nextCheckpoint = chunk.checkpoint(checkpoint.videoId)
        return JSONObject()
            .put("startByte", chunk.startByte)
            .put("bytesRead", chunk.bytes.size)
            .put("contentRange", chunk.contentRange)
            .put("stableIdentity", chunk.format.stableIdentity)
            .put("checkpoint", JSONObject()
                .put("videoId", nextCheckpoint.videoId)
                .put("stableFormatIdentity", nextCheckpoint.stableFormatIdentity)
                .put("nextByteOffset", nextCheckpoint.nextByteOffset)
                .put("totalBytes", nextCheckpoint.totalBytes ?: JSONObject.NULL))
            .toString()
    }

    private fun toJson(verified: VerifiedMedia): JSONObject {
        val format = verified.format
        return JSONObject()
            .put("url", format.url)
            .put("label", audioLabel(format))
            .put("mimeType", format.mimeType.substringBefore(';'))
            .put("contentLength", format.contentLength ?: JSONObject.NULL)
            .put("resolverClient", "shared-youtube-engine")
            .put("stableIdentity", format.stableIdentity)
            .put("expiresAtEpochSeconds", format.expiresAtEpochSeconds ?: JSONObject.NULL)
            .put("itag", format.itag)
            .put("headers", JSONObject(format.requiredHeaders))
    }

    private fun audioLabel(format: MediaFormat): String {
        val kbps = format.bitrate?.div(1000)
        val codec = format.codecs.orEmpty().ifBlank { format.container.orEmpty() }
        return listOfNotNull(
            kbps?.takeIf { it > 0 }?.let { "${it} kbps" },
            codec.takeIf(String::isNotBlank),
        ).joinToString(" · ").ifBlank { "Audio" }
    }

    private object LyraYouTubeSession : SessionProvider {
        override suspend fun visitorData(): String? =
            YouTube.visitorData?.trim()?.takeIf(String::isNotBlank)

        override suspend fun requestHeaders(url: String): Map<String, String> {
            val host = runCatching { Uri.parse(url).host.orEmpty().lowercase() }.getOrDefault("")
            if (host != "youtube.com" && !host.endsWith(".youtube.com")) return emptyMap()
            val cookie = YouTube.cookie.orEmpty().trim()
            return if (cookie.isBlank()) emptyMap() else mapOf("Cookie" to cookie)
        }
    }
}
