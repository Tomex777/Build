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

/**
 * Sora-owned last-good catalog cache.
 *
 * Extensions can disappear, fail, or be offline without taking the UI away.
 * This cache stores only structured catalog metadata that Sora has already
 * received; provider code and credentials never enter Core storage.
 */
class MediaCatalogCache(context: Context) {
    private val prefs = context.getSharedPreferences("sora_media_catalog_v1", Context.MODE_PRIVATE)

    fun read(type: ContentType): List<CachedMediaRecord> =
        decode(prefs.getString(key(type), null)).filterNot(::isLegacyDiagnosticRecord)

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

    fun write(type: ContentType, rows: List<CachedMediaRecord>) {
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
        prefs.edit().putString(key(type), array.toString()).apply()
    }

    private fun decode(raw: String?): List<CachedMediaRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        CachedMediaRecord(
                            id = item.optString("id"),
                            title = item.optString("title", "Untitled"),
                            subtitle = item.optString("subtitle"),
                            artworkUrl = item.optString("artworkUrl").takeIf { it.isNotBlank() && it != "null" },
                            sourceId = item.optString("sourceId"),
                            extensionPackage = item.optString("extensionPackage"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Migration cleanup for the old API-test APK. New diagnostic extensions are
     * excluded before they reach the cache, but older builds may already have
     * stored demo rows. Never surface those rows in the normal product UI.
     */
    private fun isLegacyDiagnosticRecord(row: CachedMediaRecord): Boolean =
        row.extensionPackage.contains(".demo", ignoreCase = true)

    private fun key(type: ContentType) = "catalog_${type.name.lowercase()}"

    private companion object {
        const val MAX_ROWS = 80
    }
}
