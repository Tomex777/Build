package com.night.keyboard.data.theme

import com.night.keyboard.model.ThemeSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ThemeRepository @Inject constructor(private val dao: ThemeDao) {
    val activeTheme: Flow<ThemeSnapshot> = dao.observeActive().map { entity ->
        entity?.let { ThemeCodec.decode(it.snapshotJson).copy(id = it.id, name = it.name) } ?: ThemeSnapshot()
    }

    private val saveMutex = Mutex()

    suspend fun saveAsActive(theme: ThemeSnapshot): Long = saveMutex.withLock {
        val now = System.currentTimeMillis()
        val existing = if (theme.id != 0L) dao.byId(theme.id) else dao.activeOnce()
        val targetId = when {
            theme.id != 0L -> theme.id
            existing != null -> existing.id
            else -> 0L
        }
        val normalized = theme.copy(id = targetId)
        val encoded = ThemeCodec.encode(normalized)

        dao.clearActive()
        if (targetId == 0L) {
            dao.insert(
                ThemeEntity(
                    name = theme.name,
                    snapshotJson = encoded,
                    createdAt = now,
                    updatedAt = now,
                    active = true,
                ),
            )
        } else {
            dao.update(
                ThemeEntity(
                    id = targetId,
                    name = theme.name,
                    snapshotJson = encoded,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    active = true,
                ),
            )
            targetId
        }
    }
}
