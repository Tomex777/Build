package com.night.spotui.ext.animethemes

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.night.spotui.source.api.MusicSourceContract
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

class AnimeThemesSourceService : Service() {
    private val executor = Executors.newFixedThreadPool(3)

    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != MusicSourceContract.MSG_REQUEST) return
            val callers = packageManager.getPackagesForUid(msg.sendingUid).orEmpty().toSet()
            if (HOST_PACKAGE !in callers) {
                respond(msg, Result.failure(IllegalAccessException("Caller is not Lyra")))
                return
            }

            val requestId = msg.data.getString(MusicSourceContract.KEY_REQUEST_ID).orEmpty()
            val method = msg.data.getString(MusicSourceContract.KEY_METHOD).orEmpty()
            val payload = runCatching {
                JSONObject(
                    msg.data.getString(MusicSourceContract.KEY_PAYLOAD_JSON)
                        .orEmpty()
                        .ifBlank { "{}" },
                )
            }.getOrDefault(JSONObject())

            executor.execute {
                val result = runCatching {
                    require(payload.optString("sourceId").ifBlank { SOURCE_ID } == SOURCE_ID) {
                        "Unsupported source"
                    }
                    when (method) {
                        MusicSourceContract.Method.MANIFEST -> manifestJson()
                        MusicSourceContract.Method.BROWSE -> browse()
                        MusicSourceContract.Method.SEARCH -> search(payload.optString("query"))
                        MusicSourceContract.Method.SUGGESTIONS ->
                            suggestions(payload.optString("query"))
                        MusicSourceContract.Method.ALBUM -> album(
                            query = payload.optString("query"),
                            albumId = payload.optString("albumId").takeIf(String::isNotBlank),
                        )
                        MusicSourceContract.Method.STREAMS -> streams(payload.optString("id"))
                        else -> error("Unsupported method: $method")
                    }
                }

                val response = Message.obtain(null, MusicSourceContract.MSG_RESPONSE).apply {
                    data = Bundle().apply {
                        putString(MusicSourceContract.KEY_REQUEST_ID, requestId)
                        putBoolean(MusicSourceContract.KEY_OK, result.isSuccess)
                        result.onSuccess {
                            putString(MusicSourceContract.KEY_RESULT_JSON, it)
                        }
                        result.onFailure {
                            putString(
                                MusicSourceContract.KEY_ERROR,
                                it.message ?: "AnimeThemes source error",
                            )
                            putString(
                                MusicSourceContract.KEY_ERROR_CODE,
                                MusicSourceContract.ERROR_CODE_GENERIC,
                            )
                        }
                    }
                }
                runCatching { msg.replyTo?.send(response) }
            }
        }
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun manifestJson(): String {
        val capabilities = JSONArray()
            .put("browse")
            .put("search")
            .put("suggestions")
            .put("album")
            .put("streams")
        return JSONObject()
            .put("id", "spotui.animethemes.extension")
            .put("name", SOURCE_NAME)
            .put("version", "0.1.0")
            .put("apiVersion", MusicSourceContract.API_VERSION)
            .put("contentTypes", JSONArray().put("music"))
            .put("capabilities", capabilities)
            .put(
                "sources",
                JSONArray().put(
                    JSONObject()
                        .put("id", SOURCE_ID)
                        .put("name", SOURCE_NAME)
                        .put("contentTypes", JSONArray().put("music"))
                        .put("capabilities", capabilities),
                ),
            )
            .toString()
    }

    private fun browse(): String {
        val root = getJson(
            "/anime?page%5Bsize%5D=12&sort=-year&include=" +
                enc(INCLUDE),
        )
        return flattenAnimeList(root.optJSONArray("anime") ?: JSONArray()).toString()
    }

    private fun search(query: String): String {
        val clean = query.trim()
        if (clean.isBlank()) return JSONArray().toString()
        val root = getJson(
            "/anime?q=" + enc(clean) +
                "&page%5Bsize%5D=12&include=" + enc(INCLUDE),
        )
        return flattenAnimeList(root.optJSONArray("anime") ?: JSONArray()).toString()
    }

    private fun suggestions(query: String): String {
        val clean = query.trim()
        if (clean.isBlank()) return JSONArray().toString()
        val root = getJson("/anime?q=" + enc(clean) + "&page%5Bsize%5D=8")
        val result = JSONArray()
        val seen = linkedSetOf<String>()
        val anime = root.optJSONArray("anime") ?: JSONArray()
        for (index in 0 until anime.length()) {
            val name = anime.optJSONObject(index)?.optString("name").orEmpty().trim()
            if (name.isNotBlank() && seen.add(name.lowercase())) result.put(name)
        }
        return result.toString()
    }

    private fun album(query: String, albumId: String?): String {
        val animeObject = if (!albumId.isNullOrBlank()) {
            getJson("/anime/" + enc(albumId) + "?include=" + enc(INCLUDE))
                .optJSONObject("anime")
        } else {
            val root = getJson(
                "/anime?q=" + enc(query.trim()) +
                    "&page%5Bsize%5D=1&include=" + enc(INCLUDE),
            )
            root.optJSONArray("anime")?.optJSONObject(0)
        } ?: error("Anime not found")

        val slug = animeObject.optString("slug")
        val name = animeObject.optString("name")
        val year = animeObject.optInt("year", 0)
        val songs = flattenAnime(animeObject)

        return JSONObject()
            .put("id", slug.ifBlank { animeObject.optString("id") })
            .put("title", name)
            .put("artist", "Various Artists")
            .put("artistId", "")
            .put("year", year)
            .put("type", "Anime Themes")
            .put("artworkUrl", "")
            .put("songs", songs)
            .toString()
    }

    private fun streams(themeId: String): String {
        require(themeId.isNotBlank()) { "Theme id is required" }
        val theme = getJson(
            "/animetheme/" + enc(themeId) +
                "?include=song.artists,anime,animethemeentries.videos.audio",
        ).optJSONObject("animetheme") ?: error("Theme not found")

        val candidates = audioCandidates(theme)
        require(candidates.length() > 0) { "Theme has no audio" }
        return candidates.toString()
    }

    private fun flattenAnimeList(anime: JSONArray): JSONArray {
        val mapped = JSONArray()
        for (index in 0 until anime.length()) {
            val item = anime.optJSONObject(index) ?: continue
            val tracks = flattenAnime(item)
            for (trackIndex in 0 until tracks.length()) {
                mapped.put(tracks.getJSONObject(trackIndex))
            }
        }
        return mapped
    }

    private fun flattenAnime(anime: JSONObject): JSONArray {
        val mapped = JSONArray()
        val themes = anime.optJSONArray("animethemes") ?: JSONArray()
        for (index in 0 until themes.length()) {
            val theme = themes.optJSONObject(index) ?: continue
            val track = trackJson(anime, theme) ?: continue
            mapped.put(track)
        }
        return mapped
    }

    private fun trackJson(anime: JSONObject, theme: JSONObject): JSONObject? {
        val song = theme.optJSONObject("song") ?: return null
        val title = song.optString("title").trim()
        if (title.isBlank()) return null

        val artists = song.optJSONArray("artists") ?: JSONArray()
        val names = buildList {
            for (index in 0 until artists.length()) {
                artists.optJSONObject(index)
                    ?.optString("name")
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }

        val themeType = theme.optString("type")
        val sequence = theme.optInt("sequence", 0)
        val themeLabel = buildString {
            append(themeType)
            if (sequence > 0) append(sequence)
        }.ifBlank { theme.optString("slug") }

        val slug = anime.optString("slug")
        return JSONObject()
            .put("id", theme.optString("id"))
            .put(
                "title",
                if (themeLabel.isBlank()) title else "$title · $themeLabel",
            )
            .put("artist", names.joinToString(", "))
            .put("artistId", artists.optJSONObject(0)?.optString("id").orEmpty())
            .put("album", anime.optString("name"))
            .put("albumId", slug.ifBlank { anime.optString("id") })
            .put("artworkUrl", "")
            .put("durationSeconds", 0)
            .put("explicit", false)
    }

    private fun audioCandidates(theme: JSONObject): JSONArray {
        val result = JSONArray()
        val seen = linkedSetOf<String>()
        val entries = theme.optJSONArray("animethemeentries") ?: JSONArray()
        for (entryIndex in 0 until entries.length()) {
            val entry = entries.optJSONObject(entryIndex) ?: continue
            val videos = entry.optJSONArray("videos") ?: JSONArray()
            for (videoIndex in 0 until videos.length()) {
                val video = videos.optJSONObject(videoIndex) ?: continue
                val audio = video.optJSONObject("audio") ?: continue
                val url = audio.optString("link").trim()
                if (url.isBlank() || !seen.add(url)) continue
                val size = audio.optLong("size", -1L)
                result.put(
                    JSONObject()
                        .put("url", url.replaceFirst("http://", "https://"))
                        .put("label", "OGG")
                        .put("mimeType", "audio/ogg")
                        .apply {
                            if (size > 0L) put("contentLength", size)
                        },
                )
            }
        }
        return result
    }

    private fun getJson(path: String): JSONObject {
        val connection = URI(BASE_URL + path).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(status in 200..299) { "AnimeThemes HTTP $status" }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun respond(msg: Message, result: Result<String>) {
        val response = Message.obtain(null, MusicSourceContract.MSG_RESPONSE).apply {
            data = Bundle().apply {
                putString(
                    MusicSourceContract.KEY_REQUEST_ID,
                    msg.data.getString(MusicSourceContract.KEY_REQUEST_ID).orEmpty(),
                )
                putBoolean(MusicSourceContract.KEY_OK, result.isSuccess)
                result.onSuccess {
                    putString(MusicSourceContract.KEY_RESULT_JSON, it)
                }
                result.onFailure {
                    putString(MusicSourceContract.KEY_ERROR, it.message ?: "Source error")
                    putString(
                        MusicSourceContract.KEY_ERROR_CODE,
                        MusicSourceContract.ERROR_CODE_GENERIC,
                    )
                }
            }
        }
        runCatching { msg.replyTo?.send(response) }
    }

    companion object {
        private const val HOST_PACKAGE = "com.night.spotui"
        private const val SOURCE_ID = "animethemes.anime"
        private const val SOURCE_NAME = "AnimeThemes"
        private const val BASE_URL = "https://api.animethemes.moe"
        private const val USER_AGENT = "Lyra/1.0 (Android; AnimeThemes source)"
        private const val INCLUDE =
            "animethemes.song.artists,animethemes.animethemeentries.videos.audio"
    }
}
