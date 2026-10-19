package com.night.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class JourneyNode(
    val id: String, val url: String, val title: String,
    val parent: String?, val timestamp: Long
)
data class Journey(val id: String, val name: String, val nodes: List<JourneyNode>)
data class Bookmark(val url: String, val title: String)
data class SavedPage(val fileName: String, val url: String, val title: String, val timestamp: Long)

/** Minimal local-first store. Never uploads browsing history or page content. */
class JourneyStore(context: Context) {
    private val prefs = context.getSharedPreferences("night_data_v1", Context.MODE_PRIVATE)
    var journeys by mutableStateOf(readJourneys())
        private set
    var bookmarks by mutableStateOf(readBookmarks())
        private set
    var savedPages by mutableStateOf(readSavedPages())
        private set

    private fun readJourneys(): List<Journey> = runCatching {
        val json = JSONArray(prefs.getString("journeys", "[]"))
        (0 until json.length()).map { index ->
            val obj = json.getJSONObject(index)
            val arr = obj.optJSONArray("nodes") ?: JSONArray()
            Journey(obj.getString("id"), obj.getString("name"),
                (0 until arr.length()).map { n ->
                    val x = arr.getJSONObject(n)
                    JourneyNode(x.getString("id"), x.getString("url"),
                        x.optString("title"), x.optString("parent").ifBlank { null },
                        x.optLong("time"))
                })
        }
    }.getOrDefault(emptyList()).ifEmpty {
        listOf(Journey(UUID.randomUUID().toString(), "My browsing", emptyList()))
    }

    private fun readBookmarks(): List<Bookmark> = runCatching {
        val array = JSONArray(prefs.getString("bookmarks", "[]"))
        (0 until array.length()).map {
            val x = array.getJSONObject(it)
            Bookmark(x.getString("url"), x.optString("title"))
        }
    }.getOrDefault(emptyList())

    private fun readSavedPages(): List<SavedPage> = runCatching {
        val array = JSONArray(prefs.getString("pages", "[]"))
        (0 until array.length()).map {
            val x = array.getJSONObject(it)
            SavedPage(x.getString("file"), x.getString("url"),
                x.optString("title"), x.optLong("time"))
        }
    }.getOrDefault(emptyList())

    fun newJourney(title: String): Journey {
        val journey = Journey(UUID.randomUUID().toString(),
            title.trim().take(60).ifBlank { "Untitled journey" }, emptyList())
        journeys = listOf(journey) + journeys
        persist()
        return journey
    }

    fun record(id: String, url: String, title: String, parent: String?): String? {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return null
        val journey = journeys.find { it.id == id } ?: return null
        val node = JourneyNode(UUID.randomUUID().toString(), url, title, parent, System.currentTimeMillis())
        journeys = journeys.map { if (it.id == id) journey.copy(nodes = (journey.nodes + node).takeLast(600)) else it }
        persist()
        return node.id
    }

    fun renameNode(journeyId: String, nodeId: String?, title: String) {
        if (nodeId == null || title.isBlank()) return
        journeys = journeys.map { journey ->
            if (journey.id != journeyId) journey
            else journey.copy(nodes = journey.nodes.map { node ->
                if (node.id == nodeId) node.copy(title = title.take(180)) else node
            })
        }
        persist()
    }

    fun bookmark(url: String, title: String) {
        if (!url.startsWith("http")) return
        bookmarks = (listOf(Bookmark(url, title.ifBlank { url })) + bookmarks.filterNot { it.url == url }).take(300)
        persist()
    }

    fun savePage(fileName: String, url: String, title: String) {
        savedPages = (listOf(SavedPage(fileName, url, title, System.currentTimeMillis())) + savedPages).take(200)
        persist()
    }

    private fun persist() {
        val journeyJson = JSONArray()
        journeys.forEach { j ->
            val arr = JSONArray()
            j.nodes.forEach { n ->
                arr.put(JSONObject().put("id", n.id).put("url", n.url).put("title", n.title)
                    .put("parent", n.parent ?: "").put("time", n.timestamp))
            }
            journeyJson.put(JSONObject().put("id", j.id).put("name", j.name).put("nodes", arr))
        }
        val marks = JSONArray()
        bookmarks.forEach { marks.put(JSONObject().put("url", it.url).put("title", it.title)) }
        val pages = JSONArray()
        savedPages.forEach {
            pages.put(JSONObject().put("file", it.fileName).put("url", it.url)
                .put("title", it.title).put("time", it.timestamp))
        }
        prefs.edit().putString("journeys", journeyJson.toString())
            .putString("bookmarks", marks.toString())
            .putString("pages", pages.toString()).apply()
    }
}
