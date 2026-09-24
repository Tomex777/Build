package com.night.sora.memeextension

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

object RedditMemeCatalog {
    private const val CACHE_MS = 2 * 60 * 1000L
    private const val USER_AGENT = "android:com.night.sora.ext.memes.reddit:v0.1.0 (Sora Reddit meme source)"
    private const val CLIENT_ID_REQUIRED = "Reddit requires an installed-app client ID. Open More → Extensions → Reddit Memes to configure it."
    private data class CacheEntry(val at: Long, val body: String)
    private data class TokenEntry(val value: String, val expiresAt: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val tokens = ConcurrentHashMap<String, TokenEntry>()

    fun browse(clientId: String): String = mapListing(
        get("https://oauth.reddit.com/r/memes/hot.json?raw_json=1&limit=50", clientId)
    )

    fun search(query: String, clientId: String): String {
        val q = query.trim()
        if (q.isBlank()) return browse(clientId)
        val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8.toString())
        return mapListing(
            get("https://oauth.reddit.com/r/memes/search.json?raw_json=1&restrict_sr=1&sort=relevance&t=all&limit=50&q=$encoded", clientId)
        )
    }

    fun details(id: String, clientId: String): String {
        val postId = id.removePrefix("t3_").substringBefore('|')
        if (postId.isBlank()) return JSONObject().put("description", "Post unavailable.").toString()
        val raw = get("https://oauth.reddit.com/comments/$postId.json?raw_json=1&limit=1", clientId)
        val listings = JSONArray(raw)
        val child = listings.optJSONObject(0)
            ?.optJSONObject("data")
            ?.optJSONArray("children")
            ?.optJSONObject(0)
            ?.optJSONObject("data")
            ?: return JSONObject().put("description", "Post unavailable.").toString()
        return detailObject(child).toString()
    }

    private fun accessToken(clientId: String): String {
        val normalizedId = clientId.trim()
        require(normalizedId.isNotBlank()) { CLIENT_ID_REQUIRED }
        tokens[normalizedId]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.value }
        synchronized(tokens) {
            tokens[normalizedId]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.value }
            val connection = URI("https://www.reddit.com/api/v1/access_token").toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val basic = Base64.getEncoder().encodeToString("$normalizedId:".toByteArray(StandardCharsets.UTF_8))
            connection.setRequestProperty("Authorization", "Basic $basic")
            try {
                connection.outputStream.bufferedWriter(StandardCharsets.UTF_8).use {
                    it.write("grant_type=https%3A%2F%2Foauth.reddit.com%2Fgrants%2Finstalled_client&device_id=DO_NOT_TRACK_THIS_DEVICE")
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) error(redditHttpError(code))
                val json = JSONObject(body)
                val value = json.optString("access_token").takeIf { it.isNotBlank() }
                    ?: error("Reddit did not issue an access token. Check the installed-app client ID.")
                val lifetimeMs = (json.optLong("expires_in", 3600L).coerceAtLeast(120L) - 60L) * 1000L
                tokens[normalizedId] = TokenEntry(value, System.currentTimeMillis() + lifetimeMs)
                return value
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun get(url: String, clientId: String, retryUnauthorized: Boolean = true): String {
        val token = accessToken(clientId)
        val now = System.currentTimeMillis()
        val cacheKey = "$clientId|$url"
        cache[cacheKey]?.takeIf { now - it.at < CACHE_MS }?.let { return it.body }
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return try {
            val code = connection.responseCode
            if (code == 401 && retryUnauthorized) {
                tokens.remove(clientId.trim())
                return get(url, clientId, retryUnauthorized = false)
            }
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error(redditHttpError(code))
            cache[cacheKey] = CacheEntry(now, body)
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun redditHttpError(code: Int): String = when (code) {
        401, 403 -> "Reddit rejected this app's credentials (HTTP $code). Check the installed-app client ID."
        429 -> "Reddit is rate-limiting this source. Wait a moment and retry."
        else -> "Reddit returned HTTP $code. Retry or check the source configuration."
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
}
