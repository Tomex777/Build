package com.night.sora.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.night.sora.model.AiMessage
import com.night.sora.model.ContentType
import com.night.sora.model.ExtensionMediaSelection
import com.night.sora.model.LibraryEntry
import com.night.sora.model.ListeningSignal
import org.json.JSONArray
import org.json.JSONObject

class CoreRepository(context: Context) {
    private val prefs = context.getSharedPreferences("sora_core", Context.MODE_PRIVATE)

    val aiMessages = mutableStateListOf<AiMessage>()
    val library = mutableStateListOf<LibraryEntry>()
    val listeningSignals = mutableStateListOf<ListeningSignal>()

    init {
        loadMessages()
        loadLibrary()
        loadListeningSignals()

        if (aiMessages.isEmpty()) {
            aiMessages += AiMessage(1, AiMessage.Role.ASSISTANT, "What do you want to work on?")
            persistMessages()
        }
        if (library.isEmpty()) {
            library += listOf(
                LibraryEntry("ref-extension-api", "Extension API v1", "Reference", "Saved for Sora AI"),
                LibraryEntry("collection-images", "Generated images", "Collection", "Private to Sora"),
                LibraryEntry("collection-files", "Files & screenshots", "Collection", "Private to Sora"),
            )
            persistLibrary()
        }
    }

    fun sendAiText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        aiMessages += AiMessage(System.currentTimeMillis(), AiMessage.Role.USER, clean)
        persistMessages()
    }

    fun sendVoicePlaceholder() {
        aiMessages += AiMessage(System.currentTimeMillis(), AiMessage.Role.USER, "Voice message")
        persistMessages()
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

    private fun loadMessages() {
        val raw = prefs.getString(KEY_MESSAGES, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                aiMessages += AiMessage(
                    id = item.getLong("id"),
                    role = AiMessage.Role.valueOf(item.getString("role")),
                    text = item.getString("text"),
                )
            }
        }
    }

    private fun persistMessages() {
        val array = JSONArray()
        aiMessages.forEach { message ->
            array.put(JSONObject().apply {
                put("id", message.id)
                put("role", message.role.name)
                put("text", message.text)
            })
        }
        prefs.edit().putString(KEY_MESSAGES, array.toString()).apply()
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
        const val KEY_LIBRARY = "library_v1"
        const val KEY_LISTENING = "listening_v1"
    }
}
