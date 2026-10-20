package com.night.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Local-only Library metadata. Old JourneyStore bookmarks are read, never rewritten or lost. */
data class LibraryBookmark(
    val id: String,
    val url: String,
    val title: String,
    val folder: String = "",
    val savedAt: Long = System.currentTimeMillis()
)

data class ReadingEntry(
    val url: String,
    val title: String,
    val savedAt: Long = System.currentTimeMillis()
)

data class HistoryVisit(
    val id: String,
    val journeyId: String,
    val title: String,
    val url: String,
    val time: Long
)

internal object LibraryRules {
    fun validWebUrl(url: String): Boolean = runCatching {
        val parsed = java.net.URI(url)
        (parsed.scheme.equals("https", true) || parsed.scheme.equals("http", true)) &&
            !parsed.host.isNullOrBlank() && parsed.userInfo == null
    }.getOrDefault(false)
    fun cleanTitle(title: String, url: String): String =
        title.trim().take(180).ifBlank { url.take(180) }
    fun cleanFolder(name: String): String = name.trim().replace("/", " / ").take(50)
    fun matches(query: String, vararg values: String): Boolean {
        val needle = query.trim()
        return needle.isBlank() || values.any { it.contains(needle, ignoreCase = true) }
    }
}

class LibraryRepository(context: Context, private val journeys: JourneyStore) {
    private val prefs = context.getSharedPreferences("night_library_v1", Context.MODE_PRIVATE)
    var editedBookmarks by mutableStateOf(loadBookmarks())
        private set
    var readingList by mutableStateOf(loadReading())
        private set
    // Hides legacy-only bookmarks deleted in the new Library, without mutating Agent 5's store.
    private var hiddenLegacy by mutableStateOf(loadHidden())

    val bookmarks: List<LibraryBookmark>
        get() {
            val overridden = editedBookmarks.map { it.url }.toSet()
            val legacy = journeys.bookmarks.filter {
                it.url !in overridden && it.url !in hiddenLegacy
            }.map {
                LibraryBookmark("legacy:" + it.url, it.url, it.title, savedAt = 0L)
            }
            return (editedBookmarks + legacy).sortedByDescending { it.savedAt }
        }

    val history: List<HistoryVisit>
        get() = journeys.journeys.flatMap { journey ->
            journey.nodes.map { node ->
                HistoryVisit(node.id, journey.id, node.title, node.url, node.timestamp)
            }
        }.sortedByDescending { it.time }

    fun saveBookmark(url: String, title: String, folder: String = ""): Boolean {
        if (!LibraryRules.validWebUrl(url)) return false
        val previous = bookmarks.firstOrNull { it.url == url }
        val entry = LibraryBookmark(
            previous?.id?.takeUnless { it.startsWith("legacy:") } ?: UUID.randomUUID().toString(),
            url, LibraryRules.cleanTitle(title, url), LibraryRules.cleanFolder(folder),
            previous?.savedAt ?: System.currentTimeMillis()
        )
        editedBookmarks = listOf(entry) + editedBookmarks.filterNot { it.url == url }
        hiddenLegacy = hiddenLegacy - url
        persist()
        return true
    }

    fun removeBookmark(url: String) {
        editedBookmarks = editedBookmarks.filterNot { it.url == url }
        hiddenLegacy = hiddenLegacy + url
        persist()
    }

    fun isReading(url: String): Boolean = readingList.any { it.url == url }

    fun toggleReading(url: String, title: String) {
        if (!LibraryRules.validWebUrl(url)) return
        readingList = if (isReading(url)) readingList.filterNot { it.url == url }
            else listOf(ReadingEntry(url, LibraryRules.cleanTitle(title, url))) + readingList
        persist()
    }

    fun clearReadingList() {
        readingList = emptyList()
        persist()
    }

    private fun loadBookmarks(): List<LibraryBookmark> = runCatching {
        val items = JSONArray(prefs.getString("marks", "[]"))
        (0 until items.length()).map { i ->
            val x = items.getJSONObject(i)
            LibraryBookmark(x.getString("id"), x.getString("url"), x.optString("title"),
                x.optString("folder"), x.optLong("time"))
        }
    }.getOrDefault(emptyList())

    private fun loadReading(): List<ReadingEntry> = runCatching {
        val arr = JSONArray(prefs.getString("reading", "[]"))
        (0 until arr.length()).map { i ->
            val x = arr.getJSONObject(i)
            ReadingEntry(x.getString("url"), x.optString("title"), x.optLong("time"))
        }
    }.getOrDefault(emptyList())

    private fun loadHidden(): Set<String> = runCatching {
        val arr = JSONArray(prefs.getString("hidden_legacy", "[]"))
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    }.getOrDefault(emptySet())

    private fun persist() {
        val marks = JSONArray()
        editedBookmarks.forEach {
            marks.put(JSONObject().put("id", it.id).put("url", it.url)
                .put("title", it.title).put("folder", it.folder).put("time", it.savedAt))
        }
        val reading = JSONArray()
        readingList.forEach {
            reading.put(JSONObject().put("url", it.url).put("title", it.title)
                .put("time", it.savedAt))
        }
        prefs.edit().putString("marks", marks.toString())
            .putString("reading", reading.toString())
            .putString("hidden_legacy", JSONArray(hiddenLegacy.toList()).toString())
            .apply()
    }
}
