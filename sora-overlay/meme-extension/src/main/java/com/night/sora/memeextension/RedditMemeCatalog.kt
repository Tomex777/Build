package com.night.sora.memeextension

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

object RedditMemeCatalog {
    private const val CACHE_MS = 2 * 60 * 1000L
    private data class CacheEntry(val at: Long, val body: String)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun browse(): String = mapListing(
        get("https://www.reddit.com/r/memes/hot.json?raw_json=1&limit=50")
    )

    fun search(query: String): String {
        val q = query.trim()
        if (q.isBlank()) return browse()
        val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.toString())
        return mapListing(
            get("https://www.reddit.com/r/memes/search.json?raw_json=1&restrict_sr=1&sort=relevance&t=all&limit=50&q=$encoded")
        )
    }

    fun details(id: String): String {
        val postId = id.removePrefix("t3_").substringBefore('|')
        if (postId.isBlank()) return JSONObject().put("description", "Post unavailable.").toString()
        val raw = get("https://www.reddit.com/comments/$postId.json?raw_json=1&limit=1")
        val listings = JSONArray(raw)
        val child = listings.optJSONObject(0)
            ?.optJSONObject("data")
            ?.optJSONArray("children")
            ?.optJSONObject(0)
            ?.optJSONObject("data")
            ?: return JSONObject().put("description", "Post unavailable.").toString()
        return detailObject(child).toString()
    }

    private fun mapListing(raw: String): String {
        val children = JSONObject(raw)
            .optJSONObject("data")
            ?.optJSONArray("children")
            ?: JSONArray()
        return JSONArray().apply {
            for (i in 0 until children.length()) {
                val data = children.optJSONObject(i)?.optJSONObject("data") ?: continue
                if (data.optBoolean("over_18", false)) continue
                val image = imageUrl(data) ?: continue
                val title = data.optString("title").trim()
                if (title.isBlank()) continue
                put(
                    JSONObject()
                        .put("id", data.optString("name").ifBlank { "t3_${data.optString("id")}" })
                        .put("title", title)
                        .put("subtitle", subtitle(data))
                        .put("artworkUrl", image)
                )
            }
        }.toString()
    }

    private fun detailObject(data: JSONObject): JSONObject {
        val permalink = data.optString("permalink")
        val body = data.optString("selftext").trim()
        return JSONObject()
            .put("description", body.ifBlank { data.optString("title") })
            .put("subreddit", data.optString("subreddit_name_prefixed").ifBlank { "r/${data.optString("subreddit")}" })
            .put("author", data.optString("author"))
            .put("score", data.optInt("score"))
            .put("comments", data.optInt("num_comments"))
            .put("createdUtc", data.optLong("created_utc"))
            .put("permalink", if (permalink.isBlank()) "" else "https://www.reddit.com$permalink")
            .put("imageUrl", imageUrl(data).orEmpty())
    }

    private fun subtitle(data: JSONObject): String {
        val subreddit = data.optString("subreddit_name_prefixed").ifBlank { "r/${data.optString("subreddit")}" }
        val score = data.optInt("score")
        val comments = data.optInt("num_comments")
        return listOf(
            subreddit,
            if (score > 0) "$score points" else null,
            if (comments > 0) "$comments comments" else null,
        ).filterNotNull().joinToString(" · ")
    }

    private fun imageUrl(data: JSONObject): String? {
        val direct = data.optString("url_overridden_by_dest")
            .takeIf { looksLikeImage(it) }
            ?: data.optString("url").takeIf { looksLikeImage(it) }
        if (!direct.isNullOrBlank()) return decodeHtml(direct)

        val preview = data.optJSONObject("preview")
            ?.optJSONArray("images")
            ?.optJSONObject(0)
            ?.optJSONObject("source")
            ?.optString("url")
            .orEmpty()
        return preview.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let(::decodeHtml)
    }

    private fun looksLikeImage(url: String): Boolean {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return false
        val clean = url.substringBefore('?').lowercase()
        return clean.endsWith(".jpg") || clean.endsWith(".jpeg") || clean.endsWith(".png") || clean.endsWith(".webp") ||
            url.contains("i.redd.it") || url.contains("preview.redd.it")
    }

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

    private fun get(url: String): String {
        val now = System.currentTimeMillis()
        cache[url]?.takeIf { now - it.at < CACHE_MS }?.let { return it.body }
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "android:com.night.sora.ext.memes.reddit:0.1 (Sora private media client)")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Reddit returned HTTP $code")
            cache[url] = CacheEntry(now, body)
            body
        } finally {
            connection.disconnect()
        }
    }
}
