package com.night.spotui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable local catalog state. The UI reads this first and refreshes from the
 * extension in the background, so reopening Auri never starts from a blank Home.
 */
class HomeCacheStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("auri_home_cache_v1", Context.MODE_PRIVATE)

    fun load(): List<Track> = decodeTracks(prefs.getString(KEY_TRACKS, "[]").orEmpty())

    fun save(tracks: List<Track>) {
        prefs.edit()
            .putString(KEY_TRACKS, encodeTracks(tracks.take(60)).toString())
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun savedAt(): Long = prefs.getLong(KEY_SAVED_AT, 0L)

    private companion object {
        const val KEY_TRACKS = "tracks"
        const val KEY_SAVED_AT = "saved_at"
    }
}

class LibraryStore(context: Context) {
    // Keep the old file/key so existing liked songs survive the rebrand.
    private val prefs = context.applicationContext
        .getSharedPreferences("spotui_library_v1", Context.MODE_PRIVATE)

    fun all(): List<Track> = decodeTracks(prefs.getString(KEY_TRACKS, "[]").orEmpty())

    fun toggle(track: Track) {
        val current = all().toMutableList()
        val index = current.indexOfFirst { it.id == track.id }
        if (index >= 0) current.removeAt(index) else current.add(0, track)
        prefs.edit().putString(KEY_TRACKS, encodeTracks(current).toString()).apply()
    }

    fun albums(): List<AlbumSummary> = runCatching {
        val array = JSONArray(prefs.getString(KEY_ALBUMS, "[]") ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val title = item.optString("title")
                if (id.isBlank() || title.isBlank()) continue
                add(
                    AlbumSummary(
                        id = id,
                        title = title,
                        artist = item.optString("artist"),
                        artistId = item.optString("artistId"),
                        year = item.optInt("year"),
                        type = item.optString("type", "Album"),
                        artworkUrl = item.optString("artworkUrl").takeIf(String::isNotBlank),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun isAlbumSaved(id: String): Boolean = albums().any { it.id == id }

    fun toggleAlbum(album: AlbumSummary) {
        val current = albums().toMutableList()
        val index = current.indexOfFirst { it.id == album.id }
        if (index >= 0) current.removeAt(index) else current.add(0, album)
        val array = JSONArray()
        current.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("artist", item.artist)
                    .put("artistId", item.artistId)
                    .put("year", item.year)
                    .put("type", item.type)
                    .put("artworkUrl", item.artworkUrl ?: "")
            )
        }
        prefs.edit().putString(KEY_ALBUMS, array.toString()).apply()
    }

    private companion object {
        const val KEY_TRACKS = "liked"
        const val KEY_ALBUMS = "albums"
    }
}

internal fun encodeTracks(tracks: List<Track>): JSONArray = JSONArray().apply {
    tracks.forEach { item ->
        put(
            JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("artist", item.artist)
                .put("artistId", item.artistId)
                .put("album", item.album)
                .put("albumId", item.albumId)
                .put("artworkUrl", item.artworkUrl ?: "")
                .put("durationSeconds", item.durationSeconds)
                .put("explicit", item.explicit)
        )
    }
}

internal fun decodeTracks(raw: String): List<Track> = runCatching {
    val array = JSONArray(raw.ifBlank { "[]" })
    buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val title = item.optString("title")
            if (id.isBlank() || title.isBlank()) continue
            add(
                Track(
                    id = id,
                    title = title,
                    artist = item.optString("artist"),
                    artistId = item.optString("artistId"),
                    album = item.optString("album"),
                    albumId = item.optString("albumId"),
                    artworkUrl = item.optString("artworkUrl").takeIf(String::isNotBlank),
                    durationSeconds = item.optLong("durationSeconds"),
                    explicit = item.optBoolean("explicit"),
                )
            )
        }
    }
}.getOrDefault(emptyList())
