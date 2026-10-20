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

/** On-device only. Keeps the v1 JSON structure to migrate existing browsing trees in place. */
class JourneyStore(context: Context) {
    private val prefs = context.getSharedPreferences("night_data_v1", Context.MODE_PRIVATE)
    var journeys by mutableStateOf(readJourneys())
        private set
    var bookmarks by mutableStateOf(readBookmarks())
        private set
    var savedPages by mutableStateOf(readSavedPages())
        private set

    private fun fallbackJourney() =
        Journey(UUID.randomUUID().toString(), "My browsing", emptyList())

    private fun readJourneys(): List<Journey> = runCatching {
        val array = JSONArray(prefs.getString("journeys", "[]"))
        (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val raw = obj.optJSONArray("nodes") ?: JSONArray()
            val nodes = (0 until raw.length()).mapNotNull { n ->
                val x = raw.getJSONObject(n)
                val safe = JourneyGraph.safeUrl(x.optString("url")) ?: return@mapNotNull null
                JourneyNode(
                    x.getString("id"), safe, x.optString("title"),
                    x.optString("parent").ifBlank { null }, x.optLong("time")
                )
            }
            Journey(obj.getString("id"), obj.optString("name").ifBlank { "Untitled journey" },
                JourneyGraph.repair(nodes))
        }
    }.getOrDefault(emptyList()).ifEmpty { listOf(fallbackJourney()) }

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
        val item = Journey(UUID.randomUUID().toString(),
            title.trim().take(60).ifBlank { "Untitled journey" }, emptyList())
        journeys = listOf(item) + journeys
        persist()
        return item
    }

    fun renameJourney(id: String, title: String) {
        val name = title.trim().take(60)
        if (name.isBlank()) return
        journeys = journeys.map { if (it.id == id) it.copy(name = name) else it }
        persist()
    }

    fun deleteJourney(id: String) {
        if (journeys.none { it.id == id }) return
        journeys = journeys.filterNot { it.id == id }.ifEmpty { listOf(fallbackJourney()) }
        persist()
    }

    fun getNode(journeyId: String, nodeId: String?): JourneyNode? =
        journeys.firstOrNull { it.id == journeyId }?.nodes?.firstOrNull { it.id == nodeId }

    /**
     * Return the new node ID, or the existing ID if a duplicate callback arrives.
     * Validate parent against this Journey: no cross-Journey or pruned edges.
     */
    fun record(id: String, url: String, title: String, parent: String?): String? {
        val safe = JourneyGraph.safeUrl(url) ?: return null
        val journey = journeys.find { it.id == id } ?: return null
        val checkedParent = parent?.takeIf { key -> journey.nodes.any { it.id == key } }
        val last = journey.nodes.lastOrNull()
        val now = System.currentTimeMillis()
        if (last != null && last.parent == checkedParent && last.url == safe &&
            now - last.timestamp in 0..2000
        ) return last.id

        val node = JourneyNode(UUID.randomUUID().toString(), safe,
            title.trim().take(180), checkedParent, now)
        journeys = journeys.map {
            if (it.id == id) it.copy(nodes = JourneyGraph.repair(it.nodes + node))
            else it
        }
        persist()
        return node.id
    }

    fun renameNode(journeyId: String, nodeId: String?, title: String) {
        val label = title.trim().take(180)
        if (nodeId == null || label.isBlank()) return
        journeys = journeys.map { journey ->
            if (journey.id != journeyId) journey
            else journey.copy(nodes = journey.nodes.map { node ->
                if (node.id == nodeId) node.copy(title = label) else node
            })
        }
        persist()
    }

    /** Delete node plus children, not just one row of the tree. */
    fun deleteSubtree(journeyId: String, nodeId: String) {
        journeys = journeys.map { journey ->
            if (journey.id != journeyId) journey else {
                val excluded = JourneyGraph.subtreeIds(journey.nodes, nodeId)
                journey.copy(nodes = journey.nodes.filterNot { it.id in excluded })
            }
        }
        persist()
    }

    /** Preserve node IDs and ancestry, including branches, during an explicit merge. */
    fun mergeJourneys(sourceId: String, destinationId: String) {
        if (sourceId == destinationId) return
        val source = journeys.firstOrNull { it.id == sourceId } ?: return
        val destination = journeys.firstOrNull { it.id == destinationId } ?: return
        val takenIds = destination.nodes.mapTo(HashSet()) { it.id }
        val aliases = source.nodes.associate { node ->
            node.id to if (node.id in takenIds) UUID.randomUUID().toString() else node.id
        }
        val imported = source.nodes.map { node ->
            node.copy(id = aliases.getValue(node.id), parent = node.parent?.let(aliases::get))
        }
        val merged = JourneyGraph.repair(destination.nodes + imported)
        journeys = journeys.filterNot { it.id == sourceId }.map {
            if (it.id == destinationId) it.copy(nodes = merged) else it
        }
        persist()
    }

    /** User-initiated export only; this data is never uploaded automatically. */
    fun exportJourneyJson(journeyId: String): String? {
        val journey = journeys.firstOrNull { it.id == journeyId } ?: return null
        val entries = JSONArray()
        journey.nodes.forEach { node ->
            entries.put(JSONObject().put("id", node.id).put("url", node.url)
                .put("title", node.title).put("parent", node.parent ?: JSONObject.NULL)
                .put("time", node.timestamp))
        }
        return JSONObject().put("format", "night-journey-v1")
            .put("name", journey.name).put("nodes", entries).toString(2)
    }

    fun bookmark(url: String, title: String) {
        if (!url.startsWith("http")) return
        bookmarks = (listOf(Bookmark(url, title.ifBlank { url })) +
            bookmarks.filterNot { it.url == url }).take(300)
        persist()
    }

    fun savePage(fileName: String, url: String, title: String) {
        savedPages = (listOf(SavedPage(fileName, url, title, System.currentTimeMillis())) +
            savedPages).take(200)
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
