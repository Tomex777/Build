package com.tomex.aether

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.aetherDataStore by preferencesDataStore(name = "aether")

class SettingsStore(private val context: Context) {
    private object Keys {
        val categories = stringPreferencesKey("categories_json")
        val selectedCategory = stringPreferencesKey("selected_category")
        val includeVideos = booleanPreferencesKey("include_videos")
        val autoplayVideos = booleanPreferencesKey("autoplay_videos")
        val sortMode = stringPreferencesKey("sort_mode")
        val seenIds = stringSetPreferencesKey("seen_ids")
        val savedPosts = stringPreferencesKey("saved_posts_json")
        val aiBaseUrl = stringPreferencesKey("ai_base_url")
    }

    val categories: Flow<List<FeedCategory>> = context.aetherDataStore.data.map { prefs ->
        decodeCategories(prefs[Keys.categories]).ifEmpty { defaultCategories() }
    }

    val selectedCategoryId: Flow<String> = context.aetherDataStore.data.map { prefs ->
        prefs[Keys.selectedCategory] ?: defaultCategories().first().id
    }

    val seenIds: Flow<Set<String>> = context.aetherDataStore.data.map { it[Keys.seenIds] ?: emptySet() }

    val savedPosts: Flow<List<MemePost>> = context.aetherDataStore.data.map { decodePosts(it[Keys.savedPosts]) }

    val appSettings: Flow<AppSettings> = context.aetherDataStore.data.map { prefs ->
        AppSettings(
            includeVideos = prefs[Keys.includeVideos] ?: false,
            autoplayVideos = prefs[Keys.autoplayVideos] ?: false,
            sortMode = runCatching { SortMode.valueOf(prefs[Keys.sortMode] ?: SortMode.HOT.name) }.getOrDefault(SortMode.HOT),
            aiBaseUrl = prefs[Keys.aiBaseUrl].orEmpty(),
        )
    }

    suspend fun setSelectedCategory(id: String) = context.aetherDataStore.edit { it[Keys.selectedCategory] = id }
    suspend fun setIncludeVideos(value: Boolean) = context.aetherDataStore.edit { it[Keys.includeVideos] = value }
    suspend fun setAutoplayVideos(value: Boolean) = context.aetherDataStore.edit { it[Keys.autoplayVideos] = value }
    suspend fun setSortMode(value: SortMode) = context.aetherDataStore.edit { it[Keys.sortMode] = value.name }
    suspend fun setAiBaseUrl(value: String) = context.aetherDataStore.edit { it[Keys.aiBaseUrl] = value.trim().trimEnd('/') }

    suspend fun setCategories(value: List<FeedCategory>) = context.aetherDataStore.edit { prefs ->
        prefs[Keys.categories] = encodeCategories(value)
    }

    suspend fun markSeen(id: String) = context.aetherDataStore.edit { prefs ->
        val next = (prefs[Keys.seenIds] ?: emptySet()).toMutableSet()
        next += id
        // Bound storage growth while retaining a very large no-repeat window.
        prefs[Keys.seenIds] = if (next.size <= 12_000) next else next.toList().takeLast(10_000).toSet()
    }

    suspend fun clearSeen() = context.aetherDataStore.edit { it.remove(Keys.seenIds) }

    suspend fun setSavedPosts(posts: List<MemePost>) = context.aetherDataStore.edit { prefs ->
        prefs[Keys.savedPosts] = encodePosts(posts)
    }

    private fun encodeCategories(items: List<FedCategory>): String {
        val arr = JSONArray()
        items.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("subreddits", JSONArray(c.subreddits))
                put("tags", JSONArray(c.tags))
            })
        }
        return arr.toString()
    }

    private fun decodeCategories(raw: String?): List<FeedCategory> = runCatching {
        if (raw.isNullOrBlank()) return@runCatching emptyList()
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    FeedCategory(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        subreddits = o.optJSONArray("subreddits").toStringList(),
                        tags = o.optJSONArray("tags").toStringList(),
                     )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun encodePosts(items: List<MemePost>): String {
        val arr = JSONArray()
        items.take(500).forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id); put("title", p.title); put("subreddit", p.subreddit)
                put("permalink", p.permalink); put("sourceUrl", p.sourceUrl); put("mediaUrl", p.mediaUrl)
                put("posterUrl", p.posterUrl); put("kind", p.kind.name); put("score", p.score)
                put("comments", p.comments); put("createdUtc", p.createdUtc)
            })
        }
        return arr.toString()
    }

    private fun decodePosts(raw: String?): List<MemePost> = runCatching {
        if (raw.isNullOrBlank()) return@runCatching emptyList()
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    MemePost(
                        id = o.optString("id"), title = o.optString("title"), subreddit = o.optString("subreddit"),
                        permalink = o.optString("permalink"), sourceUrl = o.optString("sourceUrl"), mediaUrl = o.optString("mediaUrl"),
                        posterUrl = o.optString("posterUrl").takeIf { it.isNotBlank() && it != "null" },
                        kind = runCatching { MediaKind.valueOf(o.optString("kind")) }.getOrDefault(MediaKind.IMAGE),
                        score = o.optInt("score"), comments = o.optInt("comments"), createdUtc = o.optLong("createdUtc"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList { for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let(::add) }
}
