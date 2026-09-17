package com.night.sora.tumblrmemeextension

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

object TumblrMemeCatalog {
    private const val CACHE_MS = 2 * 60 * 1000L
    private const val DEFAULT_TAG = "memes"

    private data class CacheEntry(val at: Long, val body: String)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun browse(apiKey: String): String = tagged(DEFAULT_TAG, apiKey)

    fun search(query: String, apiKey: String): String {
        val tag = query.trim().removePrefix("#").ifBlank { DEFAULT_TAG }
        return tagged(tag, apiKey)
    }

    fun details(id: String, apiKey: String): String {
        requireApiKey(apiKey)
        val parts = id.split('|', limit = 2)
        val blog = parts.getOrNull(0).orEmpty()
        val postId = parts.getOrNull(1).orEmpty()
        if (blog.isBlank() || postId.isBlank()) {
            return JSONObject().put("description", "Tumblr post unavailable.").toString()
        }

        val blogPath = URLEncoder.encode(blog, StandardCharsets.UTF_8.toString())
        val encodedId = URLEncoder.encode(postId, StandardCharsets.UTF_8.toString())
        val key = URLEncoder.encode(apiKey, StandardCharsets.UTF_8.toString())
        val raw = get("https://api.tumblr.com/v2/blog/$blogPath/posts?id=$encodedId&api_key=$key&npf=true")
        val post = JSONObject(raw)
            .optJSONObject("response")
            ?.optJSONArray("posts")
            ?.optJSONObject(0)
            ?: return JSONObject().put("description", "Tumblr post unavailable.").toString()
        return detailObject(post).toString()
    }

    private fun tagged(tag: String, apiKey: String): String {
        requireApiKey(apiKey)
        val encodedTag = URLEncoder.encode(tag, StandardCharsets.UTF_8.toString())
        val key = URLEncoder.encode(apiKey, StandardCharsets.UTF_8.toString())
        val raw = get("https://api.tumblr.com/v2/tagged?tag=$encodedTag&limit=20&api_key=$key&npf=true")
        val posts = JSONObject(raw).optJSONArray("response") ?: JSONArray()
        return JSONArray().apply {
            for (i in 0 until posts.length()) {
                val post = posts.optJSONObject(i) ?: continue
                if (!safePost(post)) continue
                val image = imageUrl(post) ?: continue
                val blog = post.optString("blog_name").ifBlank { post.optString("blogName") }
                val postId = post.optString("id_string").ifBlank { post.opt("id")?.toString().orEmpty() }
                if (blog.isBlank() || postId.isBlank()) continue
                val title = postTitle(post, blog)
                put(
                    JSONObject()
                        .put("id", "$blog|$postId")
                        .put("title", title)
                        .put("subtitle", subtitle(post, tag))
                        .put("artworkUrl", image)
                )
            }
        }.toString()
    }

    private fun detailObject(post: JSONObject): JSONObject {
        val blog = post.optString("blog_name").ifBlank { post.optString("blogName") }
        val tag = firstTag(post).ifBlank { DEFAULT_TAG }
        return JSONObject()
            .put("description", postDescription(post).ifBlank { postTitle(post, blog) })
            .put("subreddit", "Tumblr · #$tag")
            .put("author", blog)
            .put("score", post.optInt("note_count", 0))
            .put("comments", 0)
            .put("permalink", post.optString("post_url"))
            .put("imageUrl", imageUrl(post).orEmpty())
    }

    private fun subtitle(post: JSONObject, requestedTag: String): String {
        val blog = post.optString("blog_name").ifBlank { "Tumblr" }
        val notes = post.optInt("note_count", 0)
        return listOf(
            "Tumblr · #$requestedTag",
            blog,
            if (notes > 0) "$notes notes" else null,
        ).filterNotNull().joinToString(" · ")
    }

    private fun postTitle(post: JSONObject, blog: String): String {
        val summary = post.optString("summary").trim()
        if (summary.isNotBlank()) return summary
        val text = firstTextBlock(post)
        if (text.isNotBlank()) return text.take(180)
        val legacy = stripHtml(
            post.optString("caption").ifBlank {
                post.optString("body").ifBlank { post.optString("description") }
            }
        )
        return legacy.ifBlank { "Post from $blog" }.take(180)
    }

    private fun postDescription(post: JSONObject): String {
        val text = buildList {
            val content = post.optJSONArray("content") ?: JSONArray()
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") != "text") continue
                val value = stripHtml(block.optString("text"))
                if (value.isNotBlank()) add(value)
            }
        }.joinToString("\n\n")
        if (text.isNotBlank()) return text
        return stripHtml(
            post.optString("caption").ifBlank {
                post.optString("body").ifBlank { post.optString("description") }
            }
        )
    }

    private fun firstTextBlock(post: JSONObject): String {
        val content = post.optJSONArray("content") ?: return ""
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") != "text") continue
            val value = stripHtml(block.optString("text"))
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun imageUrl(post: JSONObject): String? {
        val content = post.optJSONArray("content")
        if (content != null) {
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") != "image") continue
                val media = block.optJSONArray("media") ?: continue
                var bestUrl = ""
                var bestWidth = -1
                for (j in 0 until media.length()) {
                    val item = media.optJSONObject(j) ?: continue
                    val url = item.optString("url")
                    val width = item.optInt("width", 0)
                    if (url.startsWith("https://") && width >= bestWidth) {
                        bestUrl = url
                        bestWidth = width
                    }
                }
                if (bestUrl.isNotBlank()) return bestUrl
            }
        }

        val photos = post.optJSONArray("photos")
        if (photos != null && photos.length() > 0) {
            val first = photos.optJSONObject(0)
            val sizes = first?.optJSONArray("alt_sizes")
            if (sizes != null) {
                for (i in 0 until sizes.length()) {
                    val url = sizes.optJSONObject(i)?.optString("url").orEmpty()
                    if (url.startsWith("https://")) return url
                }
            }
            val original = first?.optJSONObject("original_size")?.optString("url").orEmpty()
            if (original.startsWith("https://")) return original
        }
        return null
    }

    private fun firstTag(post: JSONObject): String {
        val tags = post.optJSONArray("tags") ?: return ""
        for (i in 0 until tags.length()) {
            val tag = tags.optString(i).trim()
            if (tag.isNotBlank()) return tag
        }
        return ""
    }

    private fun safePost(post: JSONObject): Boolean {
        if (post.optBoolean("is_nsfw", false)) return false
        val tags = post.optJSONArray("tags") ?: return true
        val blocked = setOf("nsfw", "porn", "explicit", "18+")
        for (i in 0 until tags.length()) {
            if (tags.optString(i).trim().lowercase() in blocked) return false
        }
        return true
    }

    private fun stripHtml(value: String): String = value
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun requireApiKey(apiKey: String) {
        if (apiKey.isBlank()) {
            error("Tumblr API key is not configured. Build this extension with SORA_TUMBLR_API_KEY.")
        }
    }

    private fun get(url: String): String {
        val now = System.currentTimeMillis()
        cache[url]?.takeIf { now - it.at < CACHE_MS }?.let { return it.body }
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "SoraTumblrMemes/0.1 Android")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(body).optJSONObject("meta")?.optString("msg").orEmpty()
                }.getOrNull().orEmpty()
                error(message.ifBlank { "Tumblr returned HTTP $code" })
            }
            cache[url] = CacheEntry(now, body)
            body
        } finally {
            connection.disconnect()
        }
    }
}
