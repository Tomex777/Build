package com.veya.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class WatchProgress(
    val videoId: String,
    val title: String,
    val thumbnail: String?,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
    val completed: Boolean
)

class WatchHistoryStore(context: Context) {
    private val file = File(context.filesDir, "watch-history.json")
    private val lock = Any()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<WatchProgress>> = _items.asStateFlow()

    fun position(videoId: String): Long =
        _items.value.firstOrNull { it.videoId == videoId && !it.completed }?.positionMs ?: 0L

    fun update(
        videoId: String,
        title: String,
        thumbnail: String?,
        positionMs: Long,
        durationMs: Long
    ) = synchronized(lock) {
        val completed = durationMs > 0L &&
            (positionMs >= durationMs - 30_000L || positionMs >= (durationMs * 0.95).toLong())

        val next = _items.value.filterNot { it.videoId == videoId }.toMutableList()
        next.add(
            0,
            WatchProgress(
                videoId = videoId,
                title = title,
                thumbnail = thumbnail,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                lastWatchedAt = System.currentTimeMillis(),
                completed = completed
            )
        )

        val trimmed = next.take(120)
        _items.value = trimmed
        persist(trimmed)
    }

    fun clear() = synchronized(lock) {
        _items.value = emptyList()
        runCatching { file.delete() }
    }

    private fun load(): List<WatchProgress> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        buildList {
            for (index in 0 until array.length()) {
                val o = array.getJSONObject(index)
                add(
                    WatchProgress(
                        videoId = o.getString("videoId"),
                        title = o.optString("title", "Video"),
                        thumbnail = o.optString("thumbnail").takeIf { it.isNotBlank() },
                        positionMs = o.optLong("positionMs", 0L),
                        durationMs = o.optLong("durationMs", 0L),
                        lastWatchedAt = o.optLong("lastWatchedAt", 0L),
                        completed = o.optBoolean("completed", false)
                    )
                )
            }
        }.sortedByDescending { it.lastWatchedAt }
    }.getOrDefault(emptyList())

    private fun persist(items: List<WatchProgress>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("videoId", item.videoId)
                put("title", item.title)
                put("thumbnail", item.thumbnail ?: "")
                put("positionMs", item.positionMs)
                put("durationMs", item.durationMs)
                put("lastWatchedAt", item.lastWatchedAt)
                put("completed", item.completed)
            })
        }

        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(file)) {
            file.writeText(array.toString())
            temp.delete()
        }
    }
}
