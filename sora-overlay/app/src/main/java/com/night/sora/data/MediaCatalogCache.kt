package com.night.sora.data

import android.content.Context
import com.night.sora.model.ContentType
import org.json.JSONArray
import org.json.JSONObject

data class CachedMediaRecord(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val sourceId: String,
    val extensionPackage: String,
)

data class CachedMediaSnapshot(
    val rows: List<CachedMediaRecord>,
    val fetchedAt: Long = 0L,
) {
    fun isStale(now: Long = System.currentTimeMillis(), maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Boolean =
        fetchedAt <= 0L || now - fetchedAt > maxAgeMs

    companion object {
        const val DEFAULT_MAX_AGE_MS = 6L * 60L * 60L * 1000L
    }
}

/**
 * Sora-owned last-good catalog cache.
 *
 * Only structured catalog metadata already returned by a real provider is
 * stored here. Feed timestamps let the UI say when it is rendering saved data
 * instead of implying a stale snapshot was freshly fetched.
 */
class MediaCatalogCache(context: Context) {
    private val prefs = context.getSharedPreferences("sora_media_catalog_v1", Context.MODE_PRIVATE)

    fun read(type: ContentType): List<CachedMediaRecord> = readSnapshot(type).rows

    fun readSnapshot(type: ContentType, feed: String = DEFAULT_FEED): CachedMediaSnapshot =
        decodeSnapshot(prefs.getString(key(type, feed), null))

    fun search(type: ContentType, query: String): List<CachedMediaRecord> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return read(type)
        return read(type).filter { row ->
            row.title.lowercase().contains(q) || row.subtitle.lowercase().contains(q)
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun write(type: ContentType, rows: List<CachedMediaRecord>) = write(type, DEFAULT_FEED, rows)

    fun write(type: ContentType, feed: String, rows: List<CachedMediaRecord>, fetchedAt: Long = System.currentTimeMillis()) {
        val clean = rows.filterNot(::isLegacyDiagnosticRecord)
        if (clean.isEmpty()) return
        val array = JSONArray()
        clean.take(MAX_ROWS).forEach { row ->
            array.put(JSONObject().apply {
                put("id", row.id)
                put("title", row.title)
                put("subtitle", row.subtitle)
                put("artworkUrl", row.artworkUrl ?: JSONObject.NULL)
                put("sourceId", row.sourceId)
                put("extensionPackage", row.extensionPackage)
            })
        }
        val root = JSONObject()
            .put("fetchedAt", fetchedAt)
            .put("items", array)
        prefs.edit().putString(key(type, feed), root.toString()).apply()
    }

    private fun decodeSnapshot(raw: String?): CachedMediaSnapshot {
        if (raw.isNullOrBlank()) return CachedMediaSnapshot(emptyList())
        return runCatching {
            if (raw.trimStart().startsWith("[")) {
                // Backward compatibility with the old untimestamped v1 cache.
                CachedMediaSnapshot(decodeRows(JSONArray(raw)).filterNot(::isLegacyDiagnosticRecord), 0L)
            } else {
                val root = JSONObject(raw)
                CachedMediaSnapshot(
                    rows = decodeRows(root.optJSONArray("items") ?: JSONArray()).filterNot(::isLegacyDiagnosticRecord),
                    fetchedAt = root.optLong("fetchedAt", 0L),
                )
            }
        }.getOrDefault(CachedMediaSnapshot(emptyList()))
    }

    private fun decodeRows(array: JSONArray): List<CachedMediaRecord> = buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val title = item.optString("title")
            if (id.isBlank() || title.isBlank()) continue
            add(
                CachedMediaRecord(
                    id = id,
                    title = title,
                    subtitle = item.optString("subtitle"),
                    artworkUrl = item.optString("artworkUrl").takeIf { it.isNotBlank() && it != "null" },
                    sourceId = item.optString("sourceId"),
                    extensionPackage = item.optString("extensionPackage"),
                )
            )
        }
    }

    private fun isLegacyDiagnosticRecord(row: CachedMediaRecord): Boolean =
        row.extensionPackage.contains(".demo", ignoreCase = true) || row.sourceId.startsWith("jikan.", ignoreCase = true)

    private fun key(type: ContentType, feed: String): String {
        val cleanFeed = feed.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_").ifBlank { DEFAULT_FEED }
        val base = "catalog_${type.name.lowercase()}"
        return if (cleanFeed == DEFAULT_FEED) base else "${base}_$cleanFeed"
    }

    private companion object {
        const val MAX_ROWS = 80
        const val DEFAULT_FEED = "default"
    }
}
