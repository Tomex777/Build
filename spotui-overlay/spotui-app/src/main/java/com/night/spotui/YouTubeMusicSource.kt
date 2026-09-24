package com.night.spotui

import android.content.Context
import com.night.sora.youtubemusic.YouTubeMusicCatalog
import com.night.sora.youtubemusic.YouTubeMusicSession
import com.night.sora.youtubemusic.normalizeYouTubeCookieHeader
import org.json.JSONArray
import org.json.JSONObject

data class BrowserSessionSpec(
    val url: String,
    val title: String,
    val scripts: Map<String, String>,
)

interface MusicSource {
    val name: String
    suspend fun home(): Result<List<Track>>
    suspend fun search(query: String): Result<List<Track>>
    suspend fun resolve(track: Track): Result<ResolvedAudio>
    fun loginUrl(): String
    suspend fun storeBrowserSession(
        cookieHeader: String,
        userAgent: String,
        visitorData: String = "",
        dataSyncId: String = "",
        authUser: String = "0",
    ): Result<Boolean>
}

class YouTubeMusicSource(context: Context) : MusicSource {
    private val appContext = context.applicationContext

    init {
        YouTubeMusicCatalog.initialize(appContext)
        YouTubeMusicSession.restore(appContext)
    }

    override val name: String = "YouTube Music"

    override suspend fun home(): Result<List<Track>> = runCatching {
        parseTracks(YouTubeMusicCatalog.browse(SOURCE_ID))
    }

    override suspend fun search(query: String): Result<List<Track>> = runCatching {
        parseTracks(YouTubeMusicCatalog.search(SOURCE_ID, query))
    }

    override suspend fun resolve(track: Track): Result<ResolvedAudio> = runCatching {
        val array = JSONArray(YouTubeMusicCatalog.streams(SOURCE_ID, track.id))
        val item = (0 until array.length())
            .mapNotNull(array::optJSONObject)
            .firstOrNull { it.optString("url").startsWith("http") }
            ?: error("YouTube Music returned no playable audio stream")
        val headers = buildMap {
            val objectHeaders = item.optJSONObject("headers")
            if (objectHeaders != null) {
                val keys = objectHeaders.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    objectHeaders.optString(key).takeIf(String::isNotBlank)?.let { put(key, it) }
                }
            }
        }
        ResolvedAudio(
            url = item.getString("url"),
            label = item.optString("label", "Audio"),
            mimeType = item.optString("mimeType").takeIf(String::isNotBlank),
            headers = headers,
        )
    }.recoverCatching { cause ->
        val detail = cause.message.orEmpty()
        val challenged = detail.contains("LOGIN_REQUIRED", ignoreCase = true) ||
            detail.contains("sign in", ignoreCase = true) ||
            detail.contains("not a bot", ignoreCase = true) ||
            detail.contains("confirm you", ignoreCase = true)
        if (challenged) {
            error("YouTube Music needs sign-in on this network. Sign in once, then SpotUI will retry this song.")
        }
        throw cause
    }

    fun browserSession(): BrowserSessionSpec {
        val raw = JSONObject(YouTubeMusicSession.browserSession())
        val scriptObject = raw.optJSONObject("sessionScripts")
        val scripts = buildMap {
            if (scriptObject != null) {
                val keys = scriptObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    scriptObject.optString(key).takeIf(String::isNotBlank)?.let { put(key, it) }
                }
            }
        }
        return BrowserSessionSpec(
            url = raw.getString("url"),
            title = raw.optString("title", "YouTube Music sign in"),
            scripts = scripts,
        )
    }

    override fun loginUrl(): String = browserSession().url

    override suspend fun storeBrowserSession(
        cookieHeader: String,
        userAgent: String,
        visitorData: String,
        dataSyncId: String,
        authUser: String,
    ): Result<Boolean> = runCatching {
        val response = JSONObject(
            YouTubeMusicCatalog.storeSession(
                sourceId = SOURCE_ID,
                cookieHeader = normalizeYouTubeCookieHeader(cookieHeader),
                userAgent = userAgent,
            )
        )
        val signedIn = response.optBoolean("signedIn")
        YouTubeMusicSession.storePageContext(
            appContext,
            JSONObject()
                .put("visitorData", visitorData)
                .put("dataSyncId", dataSyncId)
                .put("authUser", authUser),
            signedIn,
        )
        signedIn
    }

    private fun parseTracks(raw: String): List<Track> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val title = item.optString("title")
                if (id.isBlank() || title.isBlank()) continue
                val subtitle = item.optString("subtitle")
                val parts = subtitle.split(" · ", limit = 2)
                add(
                    Track(
                        id = id,
                        title = title,
                        artist = parts.getOrNull(0).orEmpty().ifBlank { "YouTube Music" },
                        album = parts.getOrNull(1).orEmpty(),
                        artworkUrl = item.optString("artworkUrl").takeIf(String::isNotBlank),
                        durationSeconds = item.optLong("durationSeconds"),
                        explicit = item.optBoolean("explicit"),
                    )
                )
            }
        }
    }

    companion object {
        private const val SOURCE_ID = "youtube.music"
    }
}
