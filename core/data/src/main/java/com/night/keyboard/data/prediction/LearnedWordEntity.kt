package com.night.keyboard.data.prediction

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learned_words")
data class LearnedWordEntity(
    @PrimaryKey val word: String,
    val frequency: Int = 1,
    val lastUsed: Long,
)
