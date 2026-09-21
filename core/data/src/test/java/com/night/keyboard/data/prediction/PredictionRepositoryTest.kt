package com.night.keyboard.data.prediction

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionRepositoryTest {
    @Test
    fun learnsWordsLocallyAndIncrementsFrequency() = runBlocking {
        val dao = FakeLearnedWordDao()
        val repository = PredictionRepository(dao)

        repository.learn("Keyboard", now = 100L)
        repository.learn("keyboard", now = 200L)

        val learned = repository.learnedWords.first()
        assertEquals(2, learned["keyboard"])
        assertEquals(200L, dao.rows.value.single().lastUsed)
    }

    @Test
    fun ignoresNumbersAndSingleCharacterNoise() = runBlocking {
        val dao = FakeLearnedWordDao()
        val repository = PredictionRepository(dao)

        repository.learn("7")
        repository.learn("abc123")
        repository.learn("a")

        assertTrue(repository.learnedWords.first().isEmpty())
    }

    @Test
    fun preservesOrdinaryApostrophes() = runBlocking {
        val dao = FakeLearnedWordDao()
        val repository = PredictionRepository(dao)

        repository.learn("don't")

        assertFalse(repository.learnedWords.first().isEmpty())
        assertEquals(1, repository.learnedWords.first()["don't"])
    }
}

private class FakeLearnedWordDao : LearnedWordDao {
    val rows = MutableStateFlow<List<LearnedWordEntity>>(emptyList())

    override fun observeTop(limit: Int): Flow<List<LearnedWordEntity>> = rows

    override suspend fun insert(item: LearnedWordEntity): Long {
        if (rows.value.any { it.word == item.word }) return -1L
        rows.value = rows.value + item
        return rows.value.size.toLong()
    }

    override suspend fun increment(word: String, now: Long): Int {
        var changed = 0
        rows.value = rows.value.map {
            if (it.word == word) {
                changed = 1
                it.copy(frequency = it.frequency + 1, lastUsed = now)
            } else {
                it
            }
        }
        return changed
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }
}
