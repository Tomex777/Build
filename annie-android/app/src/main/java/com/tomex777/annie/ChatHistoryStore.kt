package com.tomex777.annie

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

internal data class ChatSession(
    val id: String,
    val messages: SnapshotStateList<ChatEntry>,
    val characterId: String = AnnieCharacters.default.id,
    val customTitle: String? = null,
) {
    val title: String
        get() = customTitle?.trim()?.takeIf(String::isNotBlank)
            ?: messages.firstOrNull { it.fromUser }?.text?.takeIf(String::isNotBlank)
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
    private val changeFlow = MutableSharedFlow<String>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits the chat id whenever a message is appended or updated outside the UI (workers, handles). */
    val changes: SharedFlow<String> = changeFlow.asSharedFlow()

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
                if (messages.isNotEmpty()) {
                    val characterId = json.optString("characterId").takeIf(String::isNotBlank)
                        ?: AnnieCharacters.stableIdForExistingChat(id)
                    add(ChatSession(id, messages, characterId, json.optString("customTitle").takeIf { it.isNotBlank() && it != "null" }))
                }
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun write(context: Context, sessions: List<ChatSession>) {
        val array = JSONArray()
        sessions.forEach { session ->
            val messages = JSONArray()
            session.messages.forEach { messages.put(encodeMessage(it)) }
            array.put(
                JSONObject()
                    .put("id", session.id)
                    .put("characterId", session.characterId)
                    .put("customTitle", session.customTitle?.takeIf(String::isNotBlank) ?: JSONObject.NULL)
                    .put("messages", messages)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSIONS, array.toString())
            // Chat history must survive an immediate process kill. apply() updates the
            // in-memory preferences first and schedules disk I/O, so a force-stop right
            // after a user action can otherwise discard the newest conversation.
            .commit()
    }


    /**
     * Appends a script result to a stored chat. With [idempotent] a second append for the same
     * script + channel is a no-op that still reports success, so a retried background action cannot duplicate it.
     */
    @Synchronized
    fun appendScriptResult(
        context: Context,
        chatId: String,
        resultJson: String,
        scriptId: String,
        channel: String,
        idempotent: Boolean = false,
    ): Boolean = appendEntry(context, chatId, resultJson, scriptId, channel, null, idempotent) != null

    /** Posts a message the package can later update through the returned host-owned handle. */
    @Synchronized
    fun sendScriptMessage(context: Context, chatId: String, resultJson: String, scriptId: String): ScriptMessageHandle? {
        val handleId = "h" + ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val createdAt = System.currentTimeMillis()
        appendEntry(context, chatId, resultJson, scriptId, "message", handleId to createdAt, false) ?: return null
        return ScriptMessageHandle(handleId, scriptId, chatId, createdAt)
    }

    /** Replaces a handle's message in place. Null when the handle is unknown or belongs to another package. */
    @Synchronized
    fun updateScriptMessage(context: Context, handleId: String, scriptId: String, resultJson: String): ScriptMessageHandle? =
        runCatching {
            if (handleId.isBlank()) return null
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val sessions = JSONArray(prefs.getString(KEY_SESSIONS, "[]") ?: "[]")
            val result = JSONObject(resultJson)
            for (sessionIndex in 0 until sessions.length()) {
                val session = sessions.optJSONObject(sessionIndex) ?: continue
                val messages = session.optJSONArray("messages") ?: continue
                for (messageIndex in 0 until messages.length()) {
                    val row = messages.optJSONObject(messageIndex) ?: continue
                    if (row.optString("scriptHandleId") != handleId || row.optString("scriptId") != scriptId) continue
                    val entry = decodeMessage(row) ?: continue
                    val updated = entry.copy(
                        text = if (result.optString("type") == "text") result.optString("text") else "",
                        scriptMessageJson = resultJson,
                    )
                    messages.put(messageIndex, encodeMessage(updated))
                    prefs.edit().putString(KEY_SESSIONS, sessions.toString()).commit()
                    val chatId = session.optString("id")
                    changeFlow.tryEmit(chatId)
                    return ScriptMessageHandle(handleId, scriptId, chatId, entry.scriptHandleCreatedAt)
                }
            }
            null
        }.getOrNull()

    private fun appendEntry(
        context: Context,
        chatId: String,
        resultJson: String,
        scriptId: String,
        channel: String,
        handle: Pair<String, Long>?,
        idempotent: Boolean,
    ): Long? = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sessions = JSONArray(prefs.getString(KEY_SESSIONS, "[]") ?: "[]")
        val result = runCatching { JSONObject(resultJson) }.getOrNull() ?: return null
        for (sessionIndex in 0 until sessions.length()) {
            val session = sessions.optJSONObject(sessionIndex) ?: continue
            if (session.optString("id") != chatId) continue
            val messages = session.optJSONArray("messages") ?: JSONArray()
            if (idempotent) {
                for (messageIndex in 0 until messages.length()) {
                    val row = messages.optJSONObject(messageIndex) ?: continue
                    if (row.optString("scriptId") == scriptId && row.optString("scriptCommandName") == channel) {
                        return row.optLong("id")
                    }
                }
            }
            val type = result.optString("type")
            val error = type == "error"
            val entry = ChatEntry(
                id = System.nanoTime(),
                fromUser = false,
                text = when {
                    error -> "Script error\n" + result.optString("text", "Script failed").take(300)
                    type == "text" -> result.optString("text")
                    else -> ""
                },
                scriptMessageJson = if (error) null else resultJson,
                scriptId = if (error) null else scriptId,
                scriptCommandName = if (error) null else channel,
                scriptHandleId = handle?.first,
                scriptHandleCreatedAt = handle?.second ?: 0L,
            )
            messages.put(encodeMessage(entry))
            session.put("messages", messages)
            prefs.edit().putString(KEY_SESSIONS, sessions.toString()).commit()
            changeFlow.tryEmit(chatId)
            return entry.id
        }
        null
    }.getOrNull()

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
        .put("scriptMessageJson", entry.scriptMessageJson ?: JSONObject.NULL)
        .put("scriptId", entry.scriptId ?: JSONObject.NULL)
        .put("scriptCommandName", entry.scriptCommandName ?: JSONObject.NULL)
        .put("voiceNotePath", entry.voiceNotePath ?: JSONObject.NULL)
        .put("voiceNoteDurationMs", entry.voiceNoteDurationMs)
        .put("scriptHandleId", entry.scriptHandleId ?: JSONObject.NULL)
        .put("scriptHandleCreatedAt", entry.scriptHandleCreatedAt)

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
            scriptMessageJson = json.nullableString("scriptMessageJson"),
            scriptId = json.nullableString("scriptId"),
            scriptCommandName = json.nullableString("scriptCommandName"),
            voiceNotePath = json.nullableString("voiceNotePath"),
            voiceNoteDurationMs = json.optLong("voiceNoteDurationMs"),
            scriptHandleId = json.nullableString("scriptHandleId"),
            scriptHandleCreatedAt = json.optLong("scriptHandleCreatedAt"),
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
