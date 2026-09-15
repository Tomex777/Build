package com.night.sora.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.night.sora.model.AiConversation
import com.night.sora.model.AiMessage
import com.night.sora.model.ContentType
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.model.LibraryEntry
import com.night.sora.model.ListeningSignal
import org.json.JSONArray
import org.json.JSONObject

class CoreRepository(context: Context) {
    private val prefs = context.getSharedPreferences("sora_core", Context.MODE_PRIVATE)

    val aiConversations = mutableStateListOf<AiConversation>()
    var activeAiConversationId by mutableLongStateOf(0L)
        private set
    val library = mutableStateListOf<LibraryEntry>()
    val listeningSignals = mutableStateListOf<ListeningSignal>()

    val activeAiMessages: List<AiMessage>
        get() = aiConversations.firstOrNull { it.id == activeAiConversationId }?.messages.orEmpty()

    init {
        loadConversationsOrMigrateMessages()
        loadLibrary()
        loadListeningSignals()
        sanitizeOldFoundationLibraryRows()

        if (aiConversations.isEmpty()) {
            val now = System.currentTimeMillis()
            aiConversations += AiConversation(
                id = now,
                title = "New chat",
                updatedAt = now,
                messages = listOf(AiMessage(now + 1, AiMessage.Role.ASSISTANT, "What do you want to work on?")),
            )
            activeAiConversationId = now
            persistConversations()
        } else if (activeAiConversationId == 0L || aiConversations.none { it.id == activeAiConversationId }) {
            activeAiConversationId = aiConversations.maxByOrNull { it.updatedAt }?.id ?: 0L
            persistConversations()
        }
    }

    fun newAiConversation() {
        val now = System.currentTimeMillis()
        val conversation = AiConversation(
            id = now,
            title = "New chat",
            updatedAt = now,
            messages = listOf(AiMessage(now + 1, AiMessage.Role.ASSISTANT, "What do you want to work on?")),
        )
        aiConversations.add(0, conversation)
        activeAiConversationId = conversation.id
        persistConversations()
    }

    fun selectAiConversation(id: Long) {
        if (aiConversations.any { it.id == id }) {
            activeAiConversationId = id
            persistConversations()
        }
    }

    fun sendAiText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        appendAiMessage(AiMessage(System.currentTimeMillis(), AiMessage.Role.USER, clean))
    }

    fun sendVoicePlaceholder() {
        appendAiMessage(AiMessage(System.currentTimeMillis(), AiMessage.Role.USER, "Voice message"))
    }

    private fun appendAiMessage(message: AiMessage) {
        var index = aiConversations.indexOfFirst { it.id == activeAiConversationId }
        if (index < 0) {
            newAiConversation()
            index = aiConversations.indexOfFirst { it.id == activeAiConversationId }
        }
        val current = aiConversations[index]
        val firstUserText = (current.messages + message)
            .firstOrNull { it.role == AiMessage.Role.USER && it.text.isNotBlank() }
            ?.text
            ?.take(42)
        aiConversations[index] = current.copy(
            title = firstUserText ?: current.title,
            updatedAt = System.currentTimeMillis(),
            messages = current.messages + message,
        )
        persistConversations()
    }

    fun isSaved(selection: ExtensionMediaSelection): Boolean =
        library.any { it.mediaId == selection.id && it.sourceId == selection.sourceId && it.extensionPackage == selection.extensionPackage }

    fun toggleSaved(selection: ExtensionMediaSelection) {
        val index = library.indexOfFirst {
            it.mediaId == selection.id && it.sourceId == selection.sourceId && it.extensionPackage == selection.extensionPackage
        }
        if (index >= 0) {
            library.removeAt(index)
        } else {
            library += LibraryEntry(
                id = "media:${selection.extensionPackage}:${selection.sourceId}:${selection.id}",
                label = selection.title,
                kind = selection.type.label,
                detail = selection.subtitle,
                mediaId = selection.id,
                sourceId = selection.sourceId,
                extensionPackage = selection.extensionPackage,
                contentType = selection.type,
                mediaSubtitle = selection.subtitle,
                artworkUrl = selection.artworkUrl,
            )
        }
        persistLibrary()
    }

    fun recordListening(
        artistId: String,
        artistName: String,
        completed: Boolean = false,
        skipped: Boolean = false,
        saved: Boolean? = null,
    ) {
        val index = listeningSignals.indexOfFirst { it.artistId == artistId }
        val current = if (index >= 0) listeningSignals[index] else ListeningSignal(artistId, artistName)
        val updated = current.copy(
            artistName = artistName,
            plays = current.plays + if (!skipped) 1 else 0,
            completions = current.completions + if (completed) 1 else 0,
            skips = current.skips + if (skipped) 1 else 0,
            saved = saved ?: current.saved,
            lastPlayedEpochMs = System.currentTimeMillis(),
        )
        if (index >= 0) listeningSignals[index] = updated else listeningSignals += updated
        persistListeningSignals()
    }

    private fun sanitizeOldFoundationLibraryRows() {
        val obsolete = setOf("ref-extension-api", "collection-images", "collection-files")
        val changed = library.removeAll { it.id in obsolete || it.label == "Files & screenshots" }
        if (changed) persistLibrary()
    }

    private fun loadConversationsOrMigrateMessages() {
        val raw = prefs.getString(KEY_CONVERSATIONS, null)
        if (raw != null) {
            runCatching {
                val root = JSONObject(raw)
                activeAiConversationId = root.optLong("activeId", 0L)
                val array = root.optJSONArray("conversations") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val messages = mutableListOf<AiMessage>()
                    val messageArray = item.optJSONArray("messages") ?: JSONArray()
                    for (j in 0 until messageArray.length()) {
                        val message = messageArray.getJSONObject(j)
                        messages += AiMessage(
                            id = message.getLong("id"),
                            role = AiMessage.Role.valueOf(message.getString("role")),
                            text = message.getString("text"),
                        )
                    }
                    aiConversations += AiConversation(
                        id = item.getLong("id"),
                        title = item.optString("title", "Chat"),
                        updatedAt = item.optLong("updatedAt", item.getLong("id")),
                        messages = messages,
                    )
                }
            }
            if (aiConversations.isNotEmpty()) return
        }

        val legacy = prefs.getString(KEY_MESSAGES, null) ?: return
        runCatching {
            val array = JSONArray(legacy)
            val messages = buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(AiMessage(item.getLong("id"), AiMessage.Role.valueOf(item.getString("role")), item.getString("text")))
                }
            }
            if (messages.isNotEmpty()) {
                val id = messages.first().id
                val title = messages.firstOrNull { it.role == AiMessage.Role.USER }?.text?.take(42) ?: "Sora chat"
                aiConversations += AiConversation(id, title, messages.last().id, messages)
                activeAiConversationId = id
                persistConversations()
            }
        }
    }

    private fun persistConversations() {
        val array = JSONArray()
        aiConversations.sortedByDescending { it.updatedAt }.forEach { conversation ->
            val messages = JSONArray()
            conversation.messages.forEach { message ->
                messages.put(JSONObject().apply {
                    put("id", message.id)
                    put("role", message.role.name)
                    put("text", message.text)
                })
            }
            array.put(JSONObject().apply {
                put("id", conversation.id)
                put("title", conversation.title)
                put("updatedAt", conversation.updatedAt)
                put("messages", messages)
            })
        }
        prefs.edit().putString(
            KEY_CONVERSATIONS,
            JSONObject().put("activeId", activeAiConversationId).put("conversations", array).toString(),
        ).apply()
    }

    private fun loadLibrary() {
        val raw = prefs.getString(KEY_LIBRARY, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                library += LibraryEntry(
                    id = item.getString("id"),
                    label = item.getString("label"),
                    kind = item.getString("kind"),
                    detail = item.optString("detail"),
                    mediaId = item.optNullableString("mediaId"),
                    sourceId = item.optNullableString("sourceId"),
                    extensionPackage = item.optNullableString("extensionPackage"),
                    contentType = item.optNullableString("contentType")?.let { ContentType.valueOf(it) },
                    mediaSubtitle = item.optString("mediaSubtitle"),
                    artworkUrl = item.optNullableString("artworkUrl"),
                )
            }
        }
    }

    private fun persistLibrary() {
        val array = JSONArray()
        library.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("label", entry.label)
                put("kind", entry.kind)
                put("detail", entry.detail)
                putNullable("mediaId", entry.mediaId)
                putNullable("sourceId", entry.sourceId)
                putNullable("extensionPackage", entry.extensionPackage)
                putNullable("contentType", entry.contentType?.name)
                put("mediaSubtitle", entry.mediaSubtitle)
                putNullable("artworkUrl", entry.artworkUrl)
            })
        }
        prefs.edit().putString(KEY_LIBRARY, array.toString()).apply()
    }

    private fun loadListeningSignals() {
        val raw = prefs.getString(KEY_LISTENING, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                listeningSignals += ListeningSignal(
                    artistId = item.getString("artistId"),
                    artistName = item.getString("artistName"),
                    plays = item.optInt("plays"),
                    completions = item.optInt("completions"),
                    skips = item.optInt("skips"),
                    saved = item.optBoolean("saved"),
                    lastPlayedEpochMs = item.optLong("lastPlayedEpochMs"),
                )
            }
        }
    }

    private fun persistListeningSignals() {
        val array = JSONArray()
        listeningSignals.forEach { signal ->
            array.put(JSONObject().apply {
                put("artistId", signal.artistId)
                put("artistName", signal.artistName)
                put("plays", signal.plays)
                put("completions", signal.completions)
                put("skips", signal.skips)
                put("saved", signal.saved)
                put("lastPlayedEpochMs", signal.lastPlayedEpochMs)
            })
        }
        prefs.edit().putString(KEY_LISTENING, array.toString()).apply()
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private companion object {
        const val KEY_MESSAGES = "ai_messages_v1"
        const val KEY_CONVERSATIONS = "ai_conversations_v2"
        const val KEY_LIBRARY = "library_v1"
        const val KEY_LISTENING = "listening_v1"
    }
}
