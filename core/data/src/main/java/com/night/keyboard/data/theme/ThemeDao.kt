package com.night.keyboard.data.theme

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ThemeDao {
    @Query("SELECT * FROM themes ORDER BY active DESC, updatedAt DESC") fun observeThemes(): Flow<List<ThemeEntity>>
    @Query("SELECT * FROM themes WHERE active = 1 LIMIT 1") fun observeActive(): Flow<ThemeEntity?>
    @Query("SELECT * FROM themes WHERE active = 1 LIMIT 1") suspend fun activeOnce(): ThemeEntity?
    @Query("SELECT * FROM themes WHERE id = :id LIMIT 1") suspend fun byId(id: Long): ThemeEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(theme: ThemeEntity): Long
    @Update suspend fun update(theme: ThemeEntity)
    @Query("UPDATE themes SET active = 0") suspend fun clearActive()
}
