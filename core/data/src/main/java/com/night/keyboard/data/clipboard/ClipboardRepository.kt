package com.night.keyboard.data.clipboard

import com.night.keyboard.data.prefs.KeyboardPreferences
import com.night.keyboard.model.ClipboardItem
import com.night.keyboard.model.ClipboardKind
import com.night.keyboard.model.RetentionPolicy
import com.night.keyboard.model.RetentionPreset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class ClipboardRepository @Inject constructor(
    private val dao: ClipboardDao,
    private val preferences: KeyboardPreferences,
) {
    val items: Flow<List<ClipboardItem>> = dao.observeAll().map { rows -> rows.map(ClipboardEntity::toModel) }

    suspend fun capture(text: String, now: Long = System.currentTimeMillis()) {
        val clean = text.trim()
        if (clean.isBlank()) return
        dao.deleteExpired(now)
        val previous = dao.latestMatching(clean)
        if (previous != null && now - previous.createdAt < 2_000L) return
        val prefs = preferences.state.first()
        val preset = runCatching { RetentionPreset.valueOf(prefs.defaultRetention) }.getOrDefault(RetentionPreset.TWO_HOURS)
        val expiry = RetentionPolicy.expiryFor(preset, now)
        val nextOrder = (dao.allOnce().maxOfOrNull { it.orderIndex } ?: 0L) + 1L
        dao.insert(ClipboardEntity(text = clean, kind = detectKind(clean), createdAt = now, expiresAt = expiry, pinned = false, orderIndex = nextOrder))
        trimHistory(prefs.maxHistory)
    }

    suspend fun setPinned(item: ClipboardItem, pinned: Boolean, now: Long = System.currentTimeMillis()) {
        val expiry = if (pinned) null else {
            val prefs = preferences.state.first()
            val preset = runCatching { RetentionPreset.valueOf(prefs.defaultRetention) }.getOrDefault(RetentionPreset.TWO_HOURS)
            RetentionPolicy.expiryFor(preset, now)
        }
        dao.update(item.toEntity().copy(pinned = pinned, expiresAt = expiry))
    }

    suspend fun setRetention(item: ClipboardItem, preset: RetentionPreset, now: Long = System.currentTimeMillis()) {
        dao.update(item.toEntity().copy(pinned = false, expiresAt = RetentionPolicy.expiryFor(preset, now)))
    }

    suspend fun delete(item: ClipboardItem) = dao.deleteById(item.id)
    suspend fun restore(item: ClipboardItem) { dao.insert(item.toEntity()) }
    suspend fun clearUnpinned() = dao.clearUnpinned()

    suspend fun move(item: ClipboardItem, direction: Int) {
        if (direction == 0) return
        val rows = dao.allOnce().sortedBy { it.orderIndex }.toMutableList()
        val from = rows.indexOfFirst { it.id == item.id }
        if (from == -1) return
        val to = (from + direction).coerceIn(0, rows.lastIndex)
        if (to == from) return
        val moved = rows.removeAt(from)
        rows.add(to, moved)
        rows.forEachIndexed { index, entity ->
            val desired = index.toLong()
            if (entity.orderIndex != desired) dao.update(entity.copy(orderIndex = desired))
        }
    }

    suspend fun purgeExpired(now: Long = System.currentTimeMillis()) = dao.deleteExpired(now)

    private suspend fun trimHistory(maxHistory: Int) {
        if (maxHistory <= 0) return
        val count = dao.unpinnedCount()
        val overflow = count - maxHistory
        if (overflow > 0) dao.oldestUnpinned(overflow).forEach { dao.delete(it) }
    }

    private fun detectKind(text: String): ClipboardKind = when {
        text.startsWith("http://", true) || text.startsWith("https://", true) -> ClipboardKind.LINK
        text.matches(Regex("^\\+?[0-9 ()-]{7,}$")) -> ClipboardKind.PHONE
        Regex("\\b(road|street|avenue|close|drive|lane|way|rd|st|ave)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> ClipboardKind.ADDRESS
        else -> ClipboardKind.TEXT
    }
}

private fun ClipboardItem.toEntity(): ClipboardEntity = ClipboardEntity(id, text, kind, createdAt, expiresAt, pinned, orderIndex)
