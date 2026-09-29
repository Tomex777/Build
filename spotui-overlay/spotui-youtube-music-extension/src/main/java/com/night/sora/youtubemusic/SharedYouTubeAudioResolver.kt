package com.night.sora.youtubemusic

import android.net.Uri
import com.metrolist.innertube.YouTube
import dev.tomex.youtube.api.MediaFormat
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
