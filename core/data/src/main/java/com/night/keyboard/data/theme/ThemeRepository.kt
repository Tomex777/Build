package com.night.keyboard.data.theme

import com.night.keyboard.model.ThemeSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ThemeRepository @Inject constructor(private val dao: ThemeDao) {
    val activeTheme: Flow<ThemeSnapshot> = dao.observeActive().map { entity -> entity?.let { ThemeCodec.decode(it.snapshotJson).copy(id = it.id, name = it.name) } ?: ThemeSnapshot() }

    suspend fun saveAsActive(theme: ThemeSnapshot): Long {
        val now = System.currentTimeMillis()
        dao.clearActive()
        val encoded = ThemeCodec.encode(theme)
        return if (theme.id == 0L) {
            dao.insert(ThemeEntity(name = theme.name, snapshotJson = encoded, createdAt = now, updatedAt = now, active = true))
        } else {
            dao.update(ThemeEntity(theme.id, theme.name, encoded, now, now, true))
            theme.id
        }
    }
}
