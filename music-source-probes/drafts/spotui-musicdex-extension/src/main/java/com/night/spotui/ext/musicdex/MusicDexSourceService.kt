package com.night.spotui.ext.musicdex

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

class MusicDexSourceService : Service() {
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
                        .ifBlank { "{}" }
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
                        MusicSourceContract.Method.SUGGESTIONS -> suggestions(payload.optString("query"))
                        MusicSourceContract.Method.ARTIST -> artist(
                            query = payload.optString("query"),
                            artistId = payload.optString("artistId").takeIf(String::isNotBlank),
                        )
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
                                it.message ?: "MusicDex source error",
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
            .put("artist")
            .put("album")
            .put("streams")
        return JSONObject()
            .put("id", "spotui.musicdex.extension")
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

    private fun browse(): String =
        tracksFromPage(getJson("/tracks?perPage=30")).toString()

    private fun search(query: String): String {
        val clean = query.trim()
        if (clean.isBlank()) return JSONArray().toString()
        return tracksFromPage(
            getJson("/tracks?searchQuery=" + enc(clean) + "&perPage=30"),
        ).toString()
    }

    private fun suggestions(query: String): String {
        val clean = query.trim()
        if (clean.isBlank()) return JSONArray().toString()
        val tracks = tracksFromPage(
            getJson("/tracks?searchQuery=" + enc(clean) + "&perPage=12"),
        )
        val result = JSONArray()
        val seen = linkedSetOf<String>()
        for (index in 0 until tracks.length()) {
            val track = tracks.optJSONObject(index) ?: continue
            listOf(track.optString("title"), track.optString("artist"))
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach { value ->
                    if (seen.add(value.lowercase())) result.put(value)
                }
            if (result.length() >= 8) break
        }
        return result.toString()
    }

    private fun artist(query: String, artistId: String?): String {
        val artistObject = when {
            !artistId.isNullOrBlank() -> getJson("/artists/" + enc(artistId)).optJSONObject("artist")
            else -> firstPageItem(
                getJson("/artists?searchQuery=" + enc(query.trim()) + "&perPage=1"),
            )
        } ?: error("Artist not found")

        val id = artistObject.optString("id")
        require(id.isNotBlank()) { "Artist has no id" }

        val songs = tracksFromPage(getJson("/artists/" + enc(id) + "/tracks"))
        val releasesPage = getJson("/artists/" + enc(id) + "/albums")
        val releases = JSONArray()
        val releaseData = pageData(releasesPage)
        for (index in 0 until releaseData.length()) {
            val item = releaseData.optJSONObject(index) ?: continue
            releases.put(albumSummary(item))
        }

        return JSONObject()
            .put("id", id)
            .put("name", artistObject.optString("name"))
            .put("artworkUrl", absoluteUrl(artistObject.optString("image_small")))
            .put("songs", songs)
            .put("releases", releases)
            .toString()
    }

    private fun album(query: String, albumId: String?): String {
        val albumObject = when {
            !albumId.isNullOrBlank() -> getJson("/albums/" + enc(albumId)).optJSONObject("album")
            else -> firstPageItem(
                getJson("/albums?searchQuery=" + enc(query.trim()) + "&perPage=1"),
            )
        } ?: error("Album not found")

        val id = albumObject.optString("id")
        require(id.isNotBlank()) { "Album has no id" }

        val artists = albumObject.optJSONArray("artists") ?: JSONArray()
        val firstArtist = artists.optJSONObject(0)
        val artistId = firstArtist?.optString("id").orEmpty()
        val artistName = firstArtist?.optString("name").orEmpty()
        val songs = if (artistId.isNotBlank()) {
            tracksForAlbumFromArtist(artistId, id, albumObject)
        } else {
            JSONArray()
        }

        return JSONObject()
            .put("id", id)
            .put("title", albumObject.optString("name"))
            .put("artist", artistName)
            .put("artistId", artistId)
            .put("year", albumObject.optString("release_date").take(4).toIntOrNull() ?: 0)
            .put("artworkUrl", absoluteUrl(albumObject.optString("image")))
            .put("songs", songs)
            .toString()
    }

    private fun tracksForAlbumFromArtist(
        artistId: String,
        albumId: String,
        albumHint: JSONObject,
    ): JSONArray {
        val albums = pageData(getJson("/artists/" + enc(artistId) + "/albums"))
        for (index in 0 until albums.length()) {
            val candidate = albums.optJSONObject(index) ?: continue
            if (candidate.optString("id") != albumId) continue
            val rawTracks = candidate.optJSONArray("tracks") ?: JSONArray()
            val mapped = JSONArray()
            for (trackIndex in 0 until rawTracks.length()) {
                val track = rawTracks.optJSONObject(trackIndex) ?: continue
                mapped.put(trackJson(track, candidate))
            }
            return mapped
        }

        // The relation endpoint is authoritative. Return an empty list rather than
        // inventing album membership from the unfiltered global track endpoint.
        return JSONArray()
    }

    private fun streams(trackId: String): String {
        require(trackId.isNotBlank()) { "Track id is required" }
        val track = getJson("/tracks/" + enc(trackId)).optJSONObject("track")
            ?: error("Track not found")
        val mediaUrl = absoluteUrl(track.optString("url"))
            .takeIf(String::isNotBlank)
            ?: error("Track has no media URL")
        val length = contentLength(mediaUrl)

        return JSONArray()
            .put(
                JSONObject()
                    .put("url", mediaUrl)
                    .put("label", "MP3")
                    .put("mimeType", "audio/mpeg")
                    .apply {
                        if (length > 0L) put("contentLength", length)
                    },
            )
            .toString()
    }

    private fun tracksFromPage(root: JSONObject): JSONArray {
        val mapped = JSONArray()
        val data = pageData(root)
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            mapped.put(trackJson(item, item.optJSONObject("album")))
        }
        return mapped
    }

    private fun trackJson(item: JSONObject, albumHint: JSONObject?): JSONObject {
        val artists = item.optJSONArray("artists") ?: JSONArray()
        val firstArtist = artists.optJSONObject(0)
        val artistNames = buildList {
            for (index in 0 until artists.length()) {
                artists.optJSONObject(index)
                    ?.optString("name")
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }
        val album = item.optJSONObject("album") ?: albumHint
        val durationMs = item.optLong("duration")

        return JSONObject()
            .put("id", item.optString("id"))
            .put("title", item.optString("name"))
            .put("artist", artistNames.joinToString(", "))
            .put("artistId", firstArtist?.optString("id").orEmpty())
            .put("album", album?.optString("name").orEmpty())
            .put("albumId", album?.optString("id").orEmpty())
            .put(
                "artworkUrl",
                absoluteUrl(
                    item.optString("image").ifBlank { album?.optString("image").orEmpty() },
                ),
            )
            .put("durationSeconds", if (durationMs > 0L) durationMs / 1000L else 0L)
            .put("explicit", false)
    }

    private fun albumSummary(item: JSONObject): JSONObject {
        val artists = item.optJSONArray("artists") ?: JSONArray()
        val firstArtist = artists.optJSONObject(0)
        return JSONObject()
            .put("id", item.optString("id"))
            .put("title", item.optString("name"))
            .put("artist", firstArtist?.optString("name").orEmpty())
            .put("artistId", firstArtist?.optString("id").orEmpty())
            .put("year", item.optString("release_date").take(4).toIntOrNull() ?: 0)
            .put("type", "Album")
            .put("artworkUrl", absoluteUrl(item.optString("image")))
    }

    private fun firstPageItem(root: JSONObject): JSONObject? =
        pageData(root).optJSONObject(0)

    private fun pageData(root: JSONObject): JSONArray =
        root.optJSONObject("pagination")?.optJSONArray("data") ?: JSONArray()

    private fun getJson(path: String): JSONObject {
        val connection = URI(BASE_URL + path).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(status in 200..299) { "MusicDex HTTP $status" }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun contentLength(url: String): Long {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.responseCode
            connection.getHeaderFieldLong("Content-Length", -1L)
        } catch (_: Throwable) {
            -1L
        } finally {
            connection.disconnect()
        }
    }

    private fun absoluteUrl(value: String): String {
        val clean = value.trim()
        if (clean.isBlank()) return ""
        return if (clean.startsWith("http://") || clean.startsWith("https://")) {
            clean.replaceFirst("http://", "https://")
        } else {
            ORIGIN + "/" + clean.removePrefix("/")
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
        private const val SOURCE_ID = "musicdex.anime"
        private const val SOURCE_NAME = "MusicDex Anime"
        private const val ORIGIN = "https://musicdex.org"
        private const val BASE_URL = "$ORIGIN/secure"
        private const val USER_AGENT = "Lyra/1.0 (Android; MusicDex source)"
    }
}
