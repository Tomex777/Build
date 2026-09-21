package com.night.keyboard.data.prediction

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LearnedWordDao {
    @Query("SELECT * FROM learned_words ORDER BY frequency DESC, lastUsed DESC LIMIT :limit")
    fun observeTop(limit: Int = 256): Flow<List<LearnedWordEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: LearnedWordEntity): Long

    @Query(
        "UPDATE learned_words SET frequency = frequency + 1, lastUsed = :now WHERE word = :word",
    )
    suspend fun increment(word: String, now: Long): Int

    @Query("DELETE FROM learned_words")
    suspend fun clear()

    @Transaction
    suspend fun learn(word: String, now: Long) {
        val inserted = insert(LearnedWordEntity(word = word, lastUsed = now))
        if (inserted == -1L) increment(word, now)
    }
}
