package com.night.sora.testextension

import org.json.JSONArray
import org.json.JSONObject

object DemoCatalog {
    fun browse(type: String): String = JSONArray().apply {
        when (type) {
            "anime" -> {
                put(item("anime-1", "Paper Moons", "12 episodes · Drama"))
                put(item("anime-2", "Night Signal", "24 episodes · Mystery"))
                put(item("anime-3", "Blue Terminal", "8 episodes · Sci-fi"))
            }
            "manga" -> {
                put(item("manga-1", "After Rain", "42 chapters · Ongoing"))
                put(item("manga-2", "Glass City", "18 chapters · Drama"))
            }
            "movie" -> {
                put(item("movie-1", "The Last Platform", "2h 06m · Thriller"))
                put(item("movie-2", "Soft Static", "1h 48m · Drama"))
            }
            "tv" -> {
                put(item("tv-1", "North Hall", "3 seasons · Mystery"))
                put(item("tv-2", "Zero Floor", "2 seasons · Drama"))
            }
            "music" -> {
                put(item("track-1", "Low Light", "Aster · 3:42"))
                put(item("track-2", "Wake Slowly", "Nami · 4:10"))
                put(item("track-3", "Glassline", "Vela · 2:58"))
            }
            "memes" -> {
                put(item("meme-1", "When the build passes first try", "Developer memes"))
                put(item("meme-2", "Me opening one more tab", "Internet culture"))
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
        put("description", "Demo metadata supplied by a separately installed Sora extension.")
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

    fun streams(id: String): String = JSONArray()
        .put(JSONObject().put("label", "1080p").put("url", "https://example.invalid/$id/1080.m3u8"))
        .put(JSONObject().put("label", "720p").put("url", "https://example.invalid/$id/720.m3u8"))
        .toString()

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

    private fun item(id: String, title: String, subtitle: String) = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("subtitle", subtitle)
}
