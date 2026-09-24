package com.tomex.aether

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class RedditClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    data class FeedPage(val posts: List<MemePost>, val after: String?)

    private val userAgent = "android:com.tomex.aether:0.1 (Aether meme browser)"

    suspend fun fetchSubreddit(
        subreddit: String,
        sort: SortMode,
        includeVideos: Boolean,
        after: String? = null,
        limit: Int = 35,
        searchTerms: List<String> = emptyList(),
    ): FeedPage = withContext(Dispatchers.IO) {
        val safeSub = subreddit.removePrefix("r/").trim()
        val query = buildString {
            append("limit=$limit&raw_json=1")
            if (!after.isNullOrBlank()) append("&after=${enc(after)}")
            if (sort == SortMode.TOP) append("&t=week")
            if (searchTerms.isNotEmpty()) {
                append("&restrict_sr=on&q=${enc(searchTerms.joinToString(" OR "))}")
                append("&sort=${sort.wire}")
            }
        }
        val endpoint = if (searchTerms.isEmpty()) sort.wire else "search"
        val json = getJson("https://www.reddit.com/r/${encPath(safeSub)}/$endpoint.json?$query")
        val data = json.optJSONObject("data") ?: return@withContext FeedPage(emptyList(), null)
        val children = data.optJSONArray("children") ?: JSONArray()
        val posts = buildList {
            for (i in 0 until children.length()) {
                val d = children.optJSONObject(i)?.optJSONObject("data") ?: continue
                parsePost(d, includeVideos)?.let(::add)
            }
        }
        FeedPage(posts, data.optString("after").takeIf { it.isNotBlank() && it != "null" })
    }

    suspend fun fetchComments(postId: String): List<RedditComment> = withContext(Dispatchers.IO) {
        val raw = getText("https://www.reddit.com/comments/${encPath(postId)}.json?limit=100&depth=8&sort=top&raw_json=1")
        val root = JSONArray(raw)
        if (root.length() < 2) return@withContext emptyList()
        val children = root.optJSONObject(1)?.optJSONObject("data")?.optJSONArray("children") ?: JSONArray()
        val output = mutableListOf<RedditComment>()
        parseComments(children, 0, output)
        output
    }

    suspend fun searchSubreddits(query: String, limit: Int = 20): List<SubredditCandidate> = withContext(Dispatchers.IO) {
        val json = getJson("https://www.reddit.com/subreddits/search.json?q=${enc(query)}&limit=$limit&raw_json=1")
        val children = json.optJSONObject("data")?.optJSONArray("children") ?: JSONArray()
        buildList {
            for (i in 0 until children.length()) {
                val d = children.optJSONObject(i)?.optJSONObject("data") ?: continue
                val name = d.optString("display_name")
                if (name.isBlank()) continue
                add(
                    SubredditCandidate(
                        name = name,
                        title = decode(d.optString("title")),
                        subscribers = d.optLong("subscribers", 0),
                        description = decode(d.optString("public_description")),
                        verified = true,
                        over18 = d.optBoolean("over18", false),
                    )
                )
            }
        }
    }

    suspend fun validateSubreddit(name: String): SubredditCandidate? = withContext(Dispatchers.IO) {
        val safe = name.removePrefix("r/").trim()
        if (safe.isBlank()) return@withContext null
        runCatching {
            val json = getJson("https://www.reddit.com/r/${encPath(safe)}/about.json?raw_json=1")
            val d = json.optJSONObject("data") ?: return@runCatching null
            SubredditCandidate(
                name = d.optString("display_name", safe),
                title = decode(d.optString("title")),
                subscribers = d.optLong("subscribers", 0),
                description = decode(d.optString("public_description")),
                verified = true,
                over18 = d.optBoolean("over18", false),
            )
        }.getOrNull()
    }

    suspend fun profileCandidate(candidate: SubredditCandidate, query: String): SubredditCandidate = withContext(Dispatchers.IO) {
        val page = runCatching { fetchSubreddit(candidate.name, SortMode.HOT, includeVideos = true, limit = 30) }
            .getOrDefault(FeedPage(emptyList(), null))
        val recent = page.posts
        val imageGifCount = recent.count { it.kind == MediaKind.IMAGE || it.kind == MediaKind.GIF }
        val mediaFit = if (recent.isEmpty()) 0f else imageGifCount.toFloat() / recent.size.toFloat()
        val text = (candidate.name + " " + candidate.title + " " + candidate.description).lowercase()
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
        val relevance = if (terms.isEmpty()) 0.4f else terms.count { text.contains(it) }.toFloat() / terms.size
        val activity = (recent.size.coerceAtMost(30) / 30f)
        val subscriberSignal = when {
            candidate.subscribers >= 1_000_000 -> 1f
            candidate.subscribers >= 100_000 -> .85f
            candidate.subscribers >= 10_000 -> .65f
            candidate.subscribers > 0 -> .45f
            else -> .2f
        }
        val score = (relevance * .42f + mediaFit * .33f + activity * .15f + subscriberSignal * .10f)
            .coerceIn(0f, 1f)
        candidate.copy(mediaFit = mediaFit, recentPosts = recent.size, matchScore = score)
    }

    private fun parsePost(d: JSONObject, includeVideos: Boolean): MemePost? {
        if (d.optBoolean("stickied", false)) return null
        if (d.optBoolean("over_18", false)) return null
        val id = d.optString("id")
        if (id.isBlank()) return null

        val redditVideo = d.optJSONObject("secure_media")?.optJSONObject("reddit_video")
            ?: d.optJSONObject("media")?.optJSONObject("reddit_video")
        val isVideo = d.optBoolean("is_video", false) && redditVideo != null
        if (isVideo) {
            if (!includeVideos) return null
            val mediaUrl = redditVideo.optString("fallback_url").decodeAmp().takeIf { it.startsWith("http") } ?: return null
            val poster = previewSource(d)
            return basePost(d, MediaKind.VIDEO, mediaUrl, poster)
        }

        val direct = d.optString("url_overridden_by_dest", d.optString("url")).decodeAmp()
        val gifVariant = d.optJSONObject("preview")
            ?.optJSONArray("images")
            ?.optJSONObject(0)
            ?.optJSONObject("variants")
            ?.optJSONObject("gif")
            ?.optJSONObject("source")
            ?.optString("url")
            ?.decodeAmp()
            ?.takeIf { it.startsWith("http") }

        val lower = direct.substringBefore('?').lowercase()
        val kind = when {
            lower.endsWith(".gif") || gifVariant != null -> MediaKind.GIF
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") -> MediaKind.IMAGE
            d.optString("post_hint") == "image" -> MediaKind.IMAGE
            else -> return null
        }
        val mediaUrl = if (kind == MediaKind.GIF) gifVariant ?: direct else direct.takeIf { it.startsWith("http") } ?: previewSource(d)
        if (mediaUrl.isNullOrBlank()) return null
        return basePost(d, kind, mediaUrl, null)
    }

    private fun basePost(d: JSONObject, kind: MediaKind, mediaUrl: String, poster: String?): MemePost = MemePost(
        id = d.optString("id"),
        title = decode(d.optString("title")),
        subreddit = d.optString("subreddit"),
        permalink = "https://www.reddit.com${d.optString("permalink")}",
        sourceUrl = d.optString("url_overridden_by_dest", d.optString("url")).decodeAmp(),
        mediaUrl = mediaUrl.decodeAmp(),
        posterUrl = poster,
        kind = kind,
        score = d.optInt("score", 0),
        comments = d.optInt("num_comments", 0),
        createdUtc = d.optLong("created_utc", 0),
    )

    private fun previewSource(d: JSONObject): String? = d.optJSONObject("preview")
        ?.optJSONArray("images")
        ?.optJSONObject(0)
        ?.optJSONObject("source")
        ?.optString("url")
        ?.decodeAmp()
        ?.takeIf { it.startsWith("http") }

    private fun parseComments(children: JSONArray, depth: Int, out: MutableList<RedditComment>) {
        for (i in 0 until children.length()) {
            val child = children.optJSONObject(i) ?: continue
            if (child.optString("kind") != "t1") continue
            val d = child.optJSONObject("data") ?: continue
            val body = decode(d.optString("body"))
            if (body.isBlank() || body == "[deleted]" || body == "[removed]") continue
            out += RedditComment(
                id = d.optString("id"),
                author = d.optString("author", "[deleted]"),
                body = body,
                score = d.optInt("score", 0),
                createdUtc = d.optLong("created_utc", 0),
                depth = depth.coerceAtMost(6),
            )
            val replies = d.opt("replies")
            if (replies is JSONObject) {
                val nested = replies.optJSONObject("data")?.optJSONArray("children")
                if (nested != null) parseComments(nested, depth + 1, out)
            }
        }
    }

    private fun getJson(url: String): JSONObject = JSONObject(getText(url))

    private fun getText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Reddit ${response.code}: ${response.message}")
            return response.body?.string() ?: error("Empty Reddit response")
        }
    }

    private fun decode(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
    private fun String.decodeAmp(): String = replace("&amp;", "&")
    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun encPath(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
