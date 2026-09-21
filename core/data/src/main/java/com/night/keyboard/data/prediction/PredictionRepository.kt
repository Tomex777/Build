package com.night.keyboard.data.prediction

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PredictionRepository @Inject constructor(
    private val dao: LearnedWordDao,
) {
    val learnedWords: Flow<Map<String, Int>> = dao.observeTop().map { rows ->
        rows.associate { it.word to it.frequency }
    }

    suspend fun learn(raw: String, now: Long = System.currentTimeMillis()) {
        val word = raw.trim()
            .lowercase()
            .trim { !it.isLetter() && it.code != 39 && it != '’' }

        if (word.length !in 2..40) return
        if (!word.any(Char::isLetter)) return
        if (word.any(Char::isDigit)) return
        dao.learn(word, now)
    }

    suspend fun clear() = dao.clear()
}
