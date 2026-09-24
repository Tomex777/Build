package com.tomex777.annie

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

internal data class ChatSession(
    val id: String,
    val messages: SnapshotStateList<ChatEntry>,
) {
    val title: String
        get() = messages.firstOrNull { it.fromUser }?.text?.takeIf(String::isNotBlank)
            ?.let { if (it.length > 36) it.take(33) + "…" else it }
            ?: "New chat"

    val preview: String
        get() = messages.lastOrNull()?.let { entry ->
            entry.text.takeIf(String::isNotBlank)
                ?: entry.selectedItem?.title
                ?: entry.menuTitle
                ?: entry.searchMedia?.let { "Search $it" }
        }.orEmpty()
}

internal object ChatHistoryStore {
    private const val PREFS = "annie_chat_history_v1"
    private const val KEY_SESSIONS = "sessions"

    fun read(context: Context): List<ChatSession> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SESSIONS, "[]") ?: "[]"
        val sessions = JSONArray(raw)
        buildList {
            for (sessionIndex in 0 until sessions.length()) {
                val json = sessions.optJSONObject(sessionIndex) ?: continue
                val id = json.optString("id").takeIf(String::isNotBlank) ?: continue
                val rows = json.optJSONArray("messages") ?: JSONArray()
                val messages = mutableStateListOf<ChatEntry>()
                for (messageIndex in 0 until rows.length()) {
                    rows.optJSONObject(messageIndex)?.let(::decodeMessage)?.let(messages::add)
                }
                if (messages.isNotEmpty()) add(ChatSession(id, messages))
            }
        }
    }.getOrDefault(emptyList())

    fun write(context: Context, sessions: List<ChatSession>) {
        val array = JSONArray()
        sessions.forEach { session ->
            val messages = JSONArray()
            session.messages.forEach { messages.put(encodeMessage(it)) }
            array.put(JSONObject().put("id", session.id).put("messages", messages))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSIONS, array.toString())
            .apply()
    }

    private fun encodeMessage(entry: ChatEntry) = JSONObject()
        .put("id", entry.id)
        .put("fromUser", entry.fromUser)
        .put("text", entry.text)
        .put("catalog", JSONArray().apply { entry.catalog.forEach { put(encodeCatalogItem(it)) } })
        .put("menuTitle", entry.menuTitle ?: JSONObject.NULL)
        .put("actions", JSONArray(entry.actions))
        .put("searchMedia", entry.searchMedia ?: JSONObject.NULL)
        .put("searchInitial", entry.searchInitial)
        .put("selectedItem", entry.selectedItem?.let(::encodeCatalogItem) ?: JSONObject.NULL)
        .put("selectedStage", entry.selectedStage ?: JSONObject.NULL)

    private fun decodeMessage(json: JSONObject): ChatEntry? = runCatching {
        val id = json.optLong("id")
        if (id <= 0L) return null
        val catalogJson = json.optJSONArray("catalog") ?: JSONArray()
        val catalog = buildList {
            for (index in 0 until catalogJson.length()) {
                catalogJson.optJSONObject(index)?.let(::decodeCatalogItem)?.let(::add)
            }
        }
        val actionsJson = json.optJSONArray("actions") ?: JSONArray()
        ChatEntry(
            id = id,
            fromUser = json.optBoolean("fromUser"),
            text = json.optString("text").takeUnless { it == "null" }.orEmpty(),
            catalog = catalog,
            menuTitle = json.nullableString("menuTitle"),
            actions = buildList { for (index in 0 until actionsJson.length()) actionsJson.optString(index).takeIf(String::isNotBlank)?.let(::add) },
            searchMedia = json.nullableString("searchMedia"),
            searchInitial = json.optString("searchInitial").takeUnless { it == "null" }.orEmpty(),
            selectedItem = json.optJSONObject("selectedItem")?.let(::decodeCatalogItem),
            selectedStage = json.nullableString("selectedStage"),
        )
    }.getOrNull()

    private fun encodeCatalogItem(item: CatalogItem) = JSONObject()
        .put("id", item.id)
        .put("mediaType", item.mediaType)
        .put("title", item.title)
        .put("image", item.image)
        .put("year", item.year ?: JSONObject.NULL)
        .put("status", item.status)
        .put("episodes", item.episodes ?: JSONObject.NULL)
        .put("chapters", item.chapters ?: JSONObject.NULL)
        .put("format", item.format)
        .put("seasons", JSONArray().apply { item.seasons.forEach { put(encodeSeason(it)) } })
        .put("creator", item.creator ?: JSONObject.NULL)
        .put("genres", JSONArray(item.genres))
        .put("summary", item.summary)
        .put("runtimeMinutes", item.runtimeMinutes ?: JSONObject.NULL)
        .put("sourceLabel", item.sourceLabel)
        .put("sourceUrl", item.sourceUrl)

    private fun decodeCatalogItem(json: JSONObject): CatalogItem? = runCatching {
        val id = json.optInt("id")
        val mediaType = json.optString("mediaType")
        val title = json.nullableString("title") ?: return null
        if (id <= 0 || mediaType.isBlank()) return null
        val seasonsJson = json.optJSONArray("seasons") ?: JSONArray()
        val seasons = buildList {
            for (index in 0 until seasonsJson.length()) {
                seasonsJson.optJSONObject(index)?.let(::decodeSeason)?.let(::add)
            }
        }
        val genresJson = json.optJSONArray("genres") ?: JSONArray()
        CatalogItem(
            id = id,
            mediaType = mediaType,
            title = title,
            image = json.optString("image").takeUnless { it == "null" }.orEmpty(),
            year = json.nullableInt("year"),
            status = json.optString("status").takeUnless { it == "null" }.orEmpty(),
            episodes = json.nullableInt("episodes"),
            chapters = json.nullableInt("chapters"),
            format = json.optString("format").takeUnless { it == "null" }.orEmpty(),
            seasons = seasons,
            creator = json.nullableString("creator"),
            genres = buildList { for (index in 0 until genresJson.length()) genresJson.optString(index).takeIf(String::isNotBlank)?.let(::add) },
            summary = json.optString("summary").takeUnless { it == "null" }.orEmpty(),
            runtimeMinutes = json.nullableInt("runtimeMinutes"),
            sourceLabel = json.optString("sourceLabel").takeIf(String::isNotBlank) ?: "AniList",
            sourceUrl = json.optString("sourceUrl").takeUnless { it == "null" }.orEmpty(),
        )
    }.getOrNull()

    private fun encodeSeason(season: SeasonItem) = JSONObject()
        .put("id", season.id).put("title", season.title).put("image", season.image)
        .put("year", season.year ?: JSONObject.NULL).put("episodes", season.episodes ?: JSONObject.NULL)

    private fun decodeSeason(json: JSONObject): SeasonItem? = runCatching {
        val id = json.optInt("id")
        val title = json.nullableString("title") ?: return null
        if (id <= 0) return null
        SeasonItem(id, title, json.optString("image").takeUnless { it == "null" }.orEmpty(), json.nullableInt("year"), json.nullableInt("episodes"))
    }.getOrNull()

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)?.takeUnless { it == "null" }

    private fun JSONObject.nullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key).takeIf { it > 0 }
}
