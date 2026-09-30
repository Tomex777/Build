package com.tomex777.annie

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class WatchHistoryEntry(
    val mediaId: Int,
    val mediaType: String,
    val title: String,
    val image: String,
    val year: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val mediaUri: String,
    val videoConfigJson: String?,
    val mode: String,
) {
    val completed: Boolean
        get() = WatchHistoryStore.isCompleted(positionMs, durationMs)

    fun catalogItem(): CatalogItem = CatalogItem(
        id = mediaId,
        mediaType = mediaType,
        title = title,
        image = image,
        year = year,
        status = "",
        episodes = null,
        chapters = null,
    )

    fun playerMode(): PlayerMode =
        runCatching { PlayerMode.valueOf(mode) }.getOrDefault(PlayerMode.STREAMING)
}

internal object WatchHistoryStore {
    internal const val MIN_RESUME_MS = 2_000L
    private const val PREFS = "annie_watch_history"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 50

    fun read(context: Context): List<WatchHistoryEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null)
            .orEmpty()
        if (raw.isBlank()) return emptyList()
        return decode(raw)
    }

    fun latestFor(context: Context, item: CatalogItem): WatchHistoryEntry? =
        read(context).firstOrNull { entry ->
            entry.mediaId == item.id &&
                entry.mediaType.equals(item.mediaType, ignoreCase = true) &&
                entry.title == item.title
        }

    fun continueWatching(context: Context, mediaTypes: Set<String>? = null): List<WatchHistoryEntry> {
        val filter = mediaTypes?.map { it.uppercase(Locale.ROOT) }?.toSet()
        return read(context)
            .asSequence()
            .filter { it.positionMs >= MIN_RESUME_MS && it.mediaUri.isNotBlank() && !it.completed }
            .filter { filter == null || it.mediaType.uppercase(Locale.ROOT) in filter }
            .sortedByDescending { it.updatedAt }
            .toList()
    }

    fun record(
        context: Context,
        item: CatalogItem,
        positionMs: Long,
        durationMs: Long,
        mediaUri: String,
        videoConfigJson: String?,
        mode: PlayerMode,
        updatedAt: Long = System.currentTimeMillis(),
        synchronous: Boolean = false,
    ): WatchHistoryEntry? {
        if (positionMs < MIN_RESUME_MS || mediaUri.isBlank()) return null
        val entry = WatchHistoryEntry(
            mediaId = item.id,
            mediaType = item.mediaType.ifBlank { "VIDEO" },
            title = item.title.ifBlank { "Video" },
            image = item.image,
            year = item.year,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAt = updatedAt,
            mediaUri = mediaUri,
            videoConfigJson = videoConfigJson,
            mode = mode.name,
        )
        val entries = read(context).toMutableList()
        entries.removeAll { sameIdentity(it, entry) }
        entries.add(0, entry)
        write(context, entries.take(MAX_ENTRIES), synchronous)
        return entry
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    fun actionLabel(entry: WatchHistoryEntry): String {
        val compactTitle = entry.title.replace(Regex("\\s+"), " ").trim().let {
            if (it.length <= 34) it else it.take(31).trimEnd() + "…"
        }
        return "Resume · $compactTitle · ${formatPosition(entry.positionMs)}"
    }

    fun lastWatchedLabel(entry: WatchHistoryEntry?): String = when {
        entry == null -> "Last watched: Not started"
        entry.completed -> "Last watched: Finished"
        entry.durationMs > 0L ->
            "Last watched: ${formatPosition(entry.positionMs)} of ${formatPosition(entry.durationMs)}"
        else -> "Last watched: ${formatPosition(entry.positionMs)}"
    }

    fun isCompleted(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0L &&
            positionMs >= MIN_RESUME_MS &&
            positionMs.toDouble() / durationMs.toDouble() >= 0.95

    internal fun decode(raw: String): List<WatchHistoryEntry> = runCatching {
        val rows = JSONArray(raw)
        buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val title = row.optString("title").trim()
                val mediaUri = row.optString("mediaUri").trim()
                if (title.isBlank() || mediaUri.isBlank()) continue
                add(
                    WatchHistoryEntry(
                        mediaId = row.optInt("mediaId"),
                        mediaType = row.optString("mediaType", "VIDEO"),
                        title = title,
                        image = row.optString("image"),
                        year = row.optInt("year", -1).takeIf { it >= 0 },
                        positionMs = row.optLong("positionMs").coerceAtLeast(0L),
                        durationMs = row.optLong("durationMs").coerceAtLeast(0L),
                        updatedAt = row.optLong("updatedAt").coerceAtLeast(0L),
                        mediaUri = mediaUri,
                        videoConfigJson = if (row.isNull("videoConfigJson")) null
                            else row.optString("videoConfigJson").takeIf(String::isNotBlank),
                        mode = row.optString("mode", PlayerMode.STREAMING.name),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    internal fun encode(entries: List<WatchHistoryEntry>): String {
        val rows = JSONArray()
        entries.forEach { entry ->
            rows.put(
                JSONObject()
                    .put("mediaId", entry.mediaId)
                    .put("mediaType", entry.mediaType)
                    .put("title", entry.title)
                    .put("image", entry.image)
                    .put("year", entry.year ?: JSONObject.NULL)
                    .put("positionMs", entry.positionMs)
                    .put("durationMs", entry.durationMs)
                    .put("updatedAt", entry.updatedAt)
                    .put("mediaUri", entry.mediaUri)
                    .put("videoConfigJson", entry.videoConfigJson ?: JSONObject.NULL)
                    .put("mode", entry.mode)
            )
        }
        return rows.toString()
    }

    private fun write(context: Context, entries: List<WatchHistoryEntry>, synchronous: Boolean = false) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, encode(entries))
        if (synchronous) editor.commit() else editor.apply()
    }

    private fun sameIdentity(left: WatchHistoryEntry, right: WatchHistoryEntry): Boolean =
        left.mediaId == right.mediaId &&
            left.mediaType.equals(right.mediaType, ignoreCase = true) &&
            left.title == right.title

    private fun formatPosition(milliseconds: Long): String {
        val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
        } else {
            "%d:%02d".format(Locale.ROOT, minutes, seconds)
        }
    }
}
