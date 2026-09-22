package com.example.whatsapp.data.night

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NightChatHistoryInstrumentedTest {
    @Test
    fun visibleHistoryRetainsMessagesPastModelContextWindowAndOrdersTimestampTies() = runBlocking {
        val repository = NightRepository.get(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        val chatId = "history-regression-" + UUID.randomUUID()
        val now = System.currentTimeMillis()
        val count = 128

        try {
            repository.ensureChat(chatId, "History regression", now)
            repeat(count) { index ->
                repository.appendText(
                    chatId = chatId,
                    role = if (index % 2 == 0) "user" else "assistant",
                    text = "history-marker-$index",
                    now = now,
                )
            }

            val visibleHistory = repository.getMessages(chatId)
            val observedHistory = repository.observeMessages(chatId).first()
            assertEquals(count, visibleHistory.size)
            assertEquals((0 until count).map { "history-marker-$it" }, visibleHistory.map { it.text })
            assertEquals(visibleHistory.map { it.id }, observedHistory.map { it.id })
            assertTrue(visibleHistory.zipWithNext().all { (before, after) ->
                before.createdAt < after.createdAt
            })
        } finally {
            repository.deleteChat(chatId)
        }
    }

    @Test
    fun completedStreamingReplyMovesAfterMessagesEmittedByItsToolTurn() = runBlocking {
        val repository = NightRepository.get(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        val chatId = "stream-order-regression-" + UUID.randomUUID()
        val now = System.currentTimeMillis()
        val replyId = UUID.randomUUID().toString()
        val toolResultId = UUID.randomUUID().toString()

        try {
            repository.ensureChat(chatId, "Streaming order regression", now)
            repository.appendText(chatId, "user", "Open the browser result.", now = now)
            repository.appendMessage(
                NightMessageEntity(
                    id = replyId,
                    chatId = chatId,
                    role = "assistant",
                    type = "text",
                    text = "…",
                    createdAt = now,
                    deliveryState = "sending",
                )
            )
            repository.appendMessage(
                NightMessageEntity(
                    id = toolResultId,
                    chatId = chatId,
                    role = "assistant",
                    type = "browser",
                    text = "Browser result",
                    createdAt = now,
                    payloadJson = """{"browser":{"title":"Browser result","url":"https://example.test"}}""",
                )
            )
            repository.finishStreamingMessage(
                NightMessageEntity(
                    id = replyId,
                    chatId = chatId,
                    role = "assistant",
                    type = "text",
                    text = "Here is what I found.",
                    createdAt = now,
                    deliveryState = "sent",
                )
            )

            val history = repository.getMessages(chatId)
            assertEquals(listOf(toolResultId, replyId), history.takeLast(2).map { it.id })
            assertEquals("Here is what I found.", history.last().text)
            assertTrue(history.zipWithNext().all { (before, after) ->
                before.createdAt < after.createdAt
            })
        } finally {
            repository.deleteChat(chatId)
        }
    }
}
