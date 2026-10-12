package com.night.sora.testextension

import org.json.JSONArray
import org.json.JSONObject

object DemoCatalog {
    fun browse(type: String): String = JSONArray().apply {
        when (type) {
            "anime" -> {
                put(item("anime-1", "Paper Moons", "12 episodes · Drama", "https://picsum.photos/seed/sora-anime-1/600/900"))
                put(item("anime-2", "Night Signal", "24 episodes · Mystery", "https://picsum.photos/seed/sora-anime-2/600/900"))
                put(item("anime-3", "Blue Terminal", "8 episodes · Sci-fi", "https://picsum.photos/seed/sora-anime-3/600/900"))
            }
            "manga" -> {
                put(item("manga-1", "After Rain", "42 chapters · Ongoing", "https://picsum.photos/seed/sora-manga-1/600/900"))
                put(item("manga-2", "Glass City", "18 chapters · Drama", "https://picsum.photos/seed/sora-manga-2/600/900"))
                put(item("manga-3", "White Noise", "31 chapters · Mystery", "https://picsum.photos/seed/sora-manga-3/600/900"))
            }
            "movie" -> {
                put(item("movie-1", "The Last Platform", "2h 06m · Thriller", "https://picsum.photos/seed/sora-movie-1/600/900"))
                put(item("movie-2", "Soft Static", "1h 48m · Drama", "https://picsum.photos/seed/sora-movie-2/600/900"))
                put(item("movie-3", "Southbound", "1h 56m · Mystery", "https://picsum.photos/seed/sora-movie-3/600/900"))
            }
            "tv" -> {
                put(item("tv-1", "North Hall", "3 seasons · Mystery", "https://picsum.photos/seed/sora-tv-1/600/900"))
                put(item("tv-2", "Zero Floor", "2 seasons · Drama", "https://picsum.photos/seed/sora-tv-2/600/900"))
                put(item("tv-3", "Long Weekend", "1 season · Comedy", "https://picsum.photos/seed/sora-tv-3/600/900"))
            }
            "music" -> {
                put(item("track-1", "Low Light", "Aster · 3:42", "https://picsum.photos/seed/sora-music-1/600/600"))
                put(item("track-2", "Wake Slowly", "Nami · 4:10", "https://picsum.photos/seed/sora-music-2/600/600"))
                put(item("track-3", "Glassline", "Vela · 2:58", "https://picsum.photos/seed/sora-music-3/600/600"))
                put(item("track-download", "Offline Proof", "Sora Test · 0:05", "https://picsum.photos/seed/sora-music-offline/600/600"))
            }
            "memes" -> {
                put(item("meme-1", "When the build passes first try", "Developer memes", "https://picsum.photos/seed/sora-meme-1/700/700"))
                put(item("meme-2", "Me opening one more tab", "Internet culture", "https://picsum.photos/seed/sora-meme-2/700/700"))
                put(item("meme-3", "The final final version", "Developer memes", "https://picsum.photos/seed/sora-meme-3/700/700"))
            }
        }
    }.toString()

    fun search(query: String, type: String): String {
        val q = query.trim().lowercase()
        val types = if (type.isBlank()) listOf("anime", "manga", "movie", "tv", "music", "memes") else listOf(type)
        val all = types.flatMap { contentType ->
            val array = JSONArray(browse(contentType))
            buildList { for (i in 0 until array.length()) add(array.getJSONObject(i)) }
        }
        return JSONArray(all.filter { item ->
            q.isBlank() || item.getString("title").lowercase().contains(q) || item.optString("subtitle").lowercase().contains(q)
        }).toString()
    }

    fun details(id: String): String = JSONObject().apply {
        put("id", id)
        put("title", id.replace('-', ' ').replaceFirstChar { it.uppercase() })
        put("description", "Demo metadata supplied by a separately installed Sora extension. The screen and saved state are owned by Sora Core.")
        put("status", "available")
    }.toString()

    fun episodes(id: String): String = JSONArray().apply {
        repeat(8) { index -> put(JSONObject().put("id", "$id-e${index + 1}").put("number", index + 1).put("title", "Episode ${index + 1}")) }
    }.toString()

    fun chapters(id: String): String = JSONArray().apply {
        repeat(6) { index -> put(JSONObject().put("id", "$id-c${index + 1}").put("number", index + 1).put("title", "Chapter ${index + 1}")) }
    }.toString()

    fun pages(chapterId: String): String = JSONArray().apply {
        repeat(42) { index -> put(JSONObject().put("index", index).put("url", "https://example.invalid/$chapterId/page-${index + 1}.jpg")) }
    }.toString()

    fun streams(id: String): String {
        if (id == "track-download") {
            return JSONArray().put(
                JSONObject()
                    .put("label", "Local proof MP3")
                    .put("url", "http://10.0.2.2:8765/proof.mp3")
                    .put("mimeType", "audio/mpeg")
                    .put("headers", JSONObject().put("X-Sora-Download-Proof", "allowed"))
            ).toString()
        }
        return JSONArray()
            .put(
                JSONObject()
                    .put("label", "Test MP3")
                    .put("url", "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3")
                    .put("mimeType", "audio/mpeg")
            )
            .toString()
    }

    fun lyrics(id: String): String = JSONObject()
        .put("trackId", id)
        .put("synced", false)
        .put("text", "Demo lyrics intentionally omitted. The extension hook is working.")
        .toString()

    fun relatedArtists(id: String): String = JSONArray()
        .put(JSONObject().put("id", "$id-related-1").put("name", "Nami"))
        .put(JSONObject().put("id", "$id-related-2").put("name", "Vela"))
        .toString()

    fun feed(): String = browse("memes")

    private fun item(id: String, title: String, subtitle: String, artworkUrl: String) = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("subtitle", subtitle)
        .put("artworkUrl", artworkUrl)
}
