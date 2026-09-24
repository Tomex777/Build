package com.tomex.aether

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
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
    private val authLock = Any()
    @Volatile private var clientId: String = ""
    @Volatile private var deviceId: String = ""
    private var accessToken: String = ""
    private var tokenExpiresAtMs: Long = 0L

    fun configure(clientId: String, deviceId: String) {
        val cleanClient = clientId.trim()
        val cleanDevice = deviceId.trim()
        synchronized(authLock) {
            if (this.clientId != cleanClient || this.deviceId != cleanDevice) {
                this.clientId = cleanClient
                this.deviceId = cleanDevice
                accessToken = ""
                tokenExpiresAtMs = 0L
            }
        }
    }

    fun isConfigured(): Boolean = clientId.isNotBlank() && deviceId.isNotBlank()

    suspend fun fetchSubreddit(
        subreddit: String,
        sort: SortMode,
        includeVideos: Boolean,
        after: String? = null,
        limit: Int = 35,
        searchTerms: List<String> = emptyList(),
    ): FeedPage = withContext(Dispatchers.IO) {
        requireConfigured()
        val safeSub = subreddit.removePrefix("r/").trim()
        val query = buildString {
            append("limit="); append(limit); append("&raw_json=1")
            if (!after.isNullOrBlank()) { append("&after="); append(enc(after)) }
            if (sort == SortMode.TOP) append("&t=week")
            if (searchTerms.isNotEmpty()) {
                append("&restrict_sr=on&q="); append(enc(searchTerms.joinToString(" OR ")))
                append("&sort="); append(sort.wire)
            }
        }
        val endpoint = if (searchTerms.isEmpty()) sort.wire else "search"
        val json = oauthJson("/r/" + encPath(safeSub) + "/" + endpoint + "?" + query)
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
        requireConfigured()
        val root = JSONArray(oauthText("/comments/" + encPath(postId) + "?limit=100&depth=8&sort=top&raw_json=1"))
        if (root.length() < 2) return@withContext emptyList()
        val children = root.optJSONObject(1)?.optJSONObject("data")?.optJSONArray("children") ?: JSONArray()
        val output = mutableListOf<RedditComment>()
        parseComments(children, 0, output)
        output
    }

    suspend fun searchSubreddits(query: String, limit: Int = 20): List<SubredditCandidate> = withContext(Dispatchers.IO) {
        requireConfigured()
        val json = oauthJson("/subreddits/search?q=" + enc(query) + "&limit=" + limit + "&raw_json=1")
        val children = json.optJSONObject("data")?.optJSONArray("children") ?: JSONArray()
        buildList {
            for (i in 0 until children.length()) {
                val d = children.optJSONObject(i)?.optJSONObject("data") ?: continue
                val name = d.optString("display_name")
                if (name.isBlank()) continue
                add(SubredditCandidate(
                    name = name,
                    title = decode(d.optString("title")),
                    subscribers = d.optLong("subscribers", 0L),
                    description = decode(d.optString("public_description")),
                    verified = true,
                    over18 = d.optBoolean("over18", false),
                ))
            }
        }
    }

    suspend fun validateSubreddit(name: String): SubredditCandidate? = withContext(Dispatchers.IO) {
        requireConfigured()
        val safe = name.removePrefix("r/").trim()
        if (safe.isBlank()) return@withContext null
        runCatching {
            val d = oauthJson("/r/" + encPath(safe) + "/about?raw_json=1").optJSONObject("data") ?: return@runCatching null
            val canonical = d.optString("display_name")
            if (canonical.isBlank()) return@runCatching null
            SubredditCandidate(
                name = canonical,
                title = decode(d.optString("title")),
                subscribers = d.optLong("subscribers", 0L),
                description = decode(d.optString("public_description")),
                verified = true,
                over18 = d.optBoolean("over18", false),
            )
        }.getOrNull()
    }

    suspend fun profileCandidate(candidate: SubredditCandidate, query: String): SubredditCandidate = withContext(Dispatchers.IO) {
        val recent = runCatching { fetchSubreddit(candidate.name, SortMode.HOT, includeVideos = true, limit = 30).posts }.getOrDefault(emptyList())
        val mediaFit = if (recent.isEmpty()) 0f else recent.count { it.kind == MediaKind.IMAGE || it.kind == MediaKind.GIF }.toFloat() / recent.size
        val terms = query.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(Regex("\\s+")).filter { it.length > 2 }
        val haystack = (candidate.name + " " + candidate.title + " " + candidate.description).lowercase()
        val relevance = if (terms.isEmpty()) .4f else terms.count { haystack.contains(it) }.toFloat() / terms.size
        val activity = recent.size.coerceAtMost(30) / 30f
        val subscriberSignal = when {
            candidate.subscribers >= 1_000_000 -> 1f
            candidate.subscribers >= 100_000 -> .85f
            candidate.subscribers >= 10_000 -> .65f
            candidate.subscribers > 0 -> .45f
            else -> .2f
        }
        val score = (relevance * .42f + mediaFit * .33f + activity * .15f + subscriberSignal * .10f).coerceIn(0f, 1f)
        candidate.copy(mediaFit = mediaFit, recentPosts = recent.size, matchScore = score)
    }

    private fun parsePost(d: JSONObject, includeVideos: Boolean): MemePost? {
        if (d.optBoolean("stickied", false)) return null
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


    private fun requireConfigured() {
        if (!isConfigured()) error("Reddit setup required. Add your Reddit installed-app Client ID in Aether Settings.")
    }

    private fun ensureToken(): String = synchronized(authLock) {
        val now = System.currentTimeMillis()
        if (accessToken.isNotBlank() && now < tokenExpiresAtMs - 60_000L) return@synchronized accessToken
        requireConfigured()
        val form = FormBody.Builder()
            .add("grant_type", "https://oauth.reddit.com/grants/installed_client")
            .add("device_id", deviceId)
            .build()
        val request = Request.Builder()
            .url("https://www.reddit.com/api/v1/access_token")
            .header("Authorization", Credentials.basic(clientId, ""))
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .post(form)
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Reddit OAuth " + response.code + ": " + body.take(180))
            val json = JSONObject(body)
            val token = json.optString("access_token")
            if (token.isBlank()) error("Reddit OAuth did not return an access token.")
            accessToken = token
            tokenExpiresAtMs = now + json.optLong("expires_in", 3600L) * 1000L
            token
        }
    }

    private fun oauthJson(path: String): JSONObject = JSONObject(oauthText(path))

    private fun oauthText(path: String): String {
        var lastCode = 0
        var lastBody = ""
        repeat(2) { attempt ->
            val token = ensureToken()
            val request = Request.Builder()
                .url("https://oauth.reddit.com" + path)
                .header("Authorization", "bearer " + token)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) return body
                lastCode = response.code
                lastBody = body
                if (response.code == 401 && attempt == 0) {
                    synchronized(authLock) { accessToken = ""; tokenExpiresAtMs = 0L }
                } else error("Reddit " + response.code + ": " + body.take(180))
            }
        }
        error("Reddit " + lastCode + ": " + lastBody.take(180))
    }

    private fun decode(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
    private fun String.decodeAmp(): String = replace("&amp;", "&")
    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun encPath(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
