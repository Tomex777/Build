package com.tomex.aether

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiGatewayClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun memeAction(baseUrl: String, post: MemePost, action: AiAction): AiResult = withContext(Dispatchers.IO) {
        require(baseUrl.isNotBlank()) { "AI server is not configured" }
        val endpoint = when (action) {
            AiAction.CAPTION -> "caption"
            AiAction.EXPLAIN -> "explain"
            AiAction.TAGS -> "tags"
            AiAction.SIMILAR -> "similar"
        }
        val payload = JSONObject().apply {
            put("title", post.title)
            put("subreddit", post.subreddit)
            put("mediaUrl", post.mediaUrl)
            val visualUrl = if (post.kind == MediaKind.VIDEO) post.posterUrl.orEmpty() else post.mediaUrl
            if (visualUrl.isNotBlank()) put("imageUrl", visualUrl)
        }
        val obj = postJson("${baseUrl.trimEnd('/')}/api/ai/$endpoint", payload)
        AiResult(
            action = action,
            text = obj.optString("text", obj.optString("caption")),
            tags = obj.optJSONArray("tags").toStringList(),
        )
    }

    suspend fun discoverIntent(baseUrl: String, prompt: String): DiscoveryIntent = withContext(Dispatchers.IO) {
        require(baseUrl.isNotBlank()) { "AI server is not configured" }
        val obj = postJson("${baseUrl.trimEnd('/')}/api/discover", JSONObject().put("prompt", prompt))
        DiscoveryIntent(
            categoryName = obj.optString("categoryName"),
            queries = obj.optJSONArray("queries").toStringList(),
        )
    }

    suspend fun vibe(baseUrl: String, mood: String, categories: List<FeedCategory>): VibeResult = withContext(Dispatchers.IO) {
        require(baseUrl.isNotBlank()) { "AI server is not configured" }
        val payload = JSONObject().apply {
            put("mood", mood)
            put("categories", JSONArray(categories.map { it.name }))
        }
        val obj = postJson("${baseUrl.trimEnd('/')}/api/vibe", payload)
        VibeResult(
            category = obj.optString("category"),
            reason = obj.optString("reason"),
        )
    }

    private fun postJson(url: String, json: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody(jsonType))
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
                error(message.ifBlank { "AI server ${response.code}" })
            }
            return JSONObject(body)
        }
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList { for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let(::add) }
}
