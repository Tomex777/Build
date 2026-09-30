package com.veya.app.data

import android.content.Context
import com.veya.app.model.DownloadItem
import com.veya.app.model.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DownloadStore(context: Context) {
    private val file = File(context.filesDir, "downloads.json")
    private val lock = Any()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    fun get(id: String): DownloadItem? = _items.value.firstOrNull { it.id == id }

    fun upsert(item: DownloadItem) = synchronized(lock) {
        val next = _items.value.toMutableList()
        val index = next.indexOfFirst { it.id == item.id }
        if (index >= 0) next[index] = item else next.add(0, item)
        _items.value = next.sortedByDescending { it.createdAt }
        persist(_items.value)
    }

    fun update(id: String, transform: (DownloadItem) -> DownloadItem) = synchronized(lock) {
        val next = _items.value.map { if (it.id == id) transform(it).copy(updatedAt = System.currentTimeMillis()) else it }
        _items.value = next
        persist(next)
    }

    fun remove(id: String) = synchronized(lock) {
        val next = _items.value.filterNot { it.id == id }
        _items.value = next
        persist(next)
    }

    private fun load(): List<DownloadItem> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    DownloadItem(
                        id = o.getString("id"),
                        url = o.getString("url"),
                        title = o.getString("title"),
                        fileName = o.getString("fileName"),
                        mimeType = o.optString("mimeType", "application/octet-stream"),
                        totalBytes = o.optLong("totalBytes", -1),
                        downloadedBytes = o.optLong("downloadedBytes", 0),
                        status = runCatching { DownloadStatus.valueOf(o.optString("status")) }.getOrDefault(DownloadStatus.FAILED),
                        publicUri = o.optString("publicUri").takeIf { it.isNotBlank() },
                        error = o.optString("error").takeIf { it.isNotBlank() },
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun persist(items: List<DownloadItem>) {
        val arr = JSONArray()
        items.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id)
                put("url", d.url)
                put("title", d.title)
                put("fileName", d.fileName)
                put("mimeType", d.mimeType)
                put("totalBytes", d.totalBytes)
                put("downloadedBytes", d.downloadedBytes)
                put("status", d.status.name)
                put("publicUri", d.publicUri ?: "")
                put("error", d.error ?: "")
                put("createdAt", d.createdAt)
                put("updatedAt", d.updatedAt)
            })
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }
}
