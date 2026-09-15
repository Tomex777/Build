package com.night.keyboard.data.clipboard

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_items ORDER BY pinned DESC, orderIndex ASC, createdAt DESC")
    fun observeAll(): Flow<List<ClipboardEntity>>

    @Query("SELECT * FROM clipboard_items ORDER BY orderIndex ASC, createdAt DESC")
    suspend fun allOnce(): List<ClipboardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ClipboardEntity): Long

    @Update suspend fun update(item: ClipboardEntity)
    @Delete suspend fun delete(item: ClipboardEntity)
    @Query("DELETE FROM clipboard_items WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("DELETE FROM clipboard_items WHERE pinned = 0") suspend fun clearUnpinned()
    @Query("DELETE FROM clipboard_items WHERE pinned = 0 AND expiresAt IS NOT NULL AND expiresAt <= :now") suspend fun deleteExpired(now: Long)
    @Query("SELECT * FROM clipboard_items WHERE text = :text ORDER BY createdAt DESC LIMIT 1") suspend fun latestMatching(text: String): ClipboardEntity?
    @Query("SELECT COUNT(*) FROM clipboard_items WHERE pinned = 0") suspend fun unpinnedCount(): Int
    @Query("SELECT * FROM clipboard_items WHERE pinned = 0 ORDER BY createdAt ASC LIMIT :limit") suspend fun oldestUnpinned(limit: Int): List<ClipboardEntity>
}
