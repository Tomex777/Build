package com.tomex777.annie

import androidx.compose.runtime.mutableStateListOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.async
import kotlinx.coroutines.yield

/** M0.5: host-owned message handles, in-place updates, change notifications, idempotent background appends. */
@RunWith(AndroidJUnit4::class)
class ChatMessageHandleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var saved: List<ChatSession>
    private val chatId = "handle-test-chat"

    @Before fun seed() {
        saved = ChatHistoryStore.read(context)
        ChatHistoryStore.write(context, listOf(ChatSession(chatId, mutableStateListOf(ChatEntry(1L, true, "hi")))))
    }

    @After fun restore() {
        ChatHistoryStore.write(context, saved)
    }

    private fun stored(): List<ChatEntry> = ChatHistoryStore.read(context).first { it.id == chatId }.messages.toList()
    private fun progress(value: Double) = JSONObject().put("type", "progress").put("title", "Work").put("value", value).toString()

    @Test fun sendReturnsAHostOwnedHandleAndPersistsTheMessage() {
        val handle = requireNotNull(ChatHistoryStore.sendScriptMessage(context, chatId, progress(0.1), "pkg.a"))
        assertTrue(handle.id.startsWith("h") && handle.id.length > 16)
        assertEquals("pkg.a", handle.packageId)
        assertEquals(chatId, handle.conversationId)
        val entry = stored().last()
        assertEquals(handle.id, entry.scriptHandleId)
        assertEquals("pkg.a", entry.scriptId)
        assertNull("unknown chats cannot be posted into", ChatHistoryStore.sendScriptMessage(context, "no-such-chat", progress(0.1), "pkg.a"))
    }

    @Test fun updateReplacesTheMessageInPlaceForItsOwnerOnly() {
        val handle = requireNotNull(ChatHistoryStore.sendScriptMessage(context, chatId, progress(0.1), "pkg.a"))
        val countBefore = stored().size
        val updated = requireNotNull(ChatHistoryStore.updateScriptMessage(context, handle.id, "pkg.a", progress(0.9)))
        assertEquals(handle.createdAt, updated.createdAt)
        assertEquals(countBefore, stored().size)
        assertEquals(0.9, JSONObject(stored().last().scriptMessageJson).getDouble("value"), 0.0001)

        assertNull("another package must not update it", ChatHistoryStore.updateScriptMessage(context, handle.id, "pkg.b", progress(0.0)))
        assertNull("unknown handle", ChatHistoryStore.updateScriptMessage(context, "hdeadbeef", "pkg.a", progress(0.0)))
        assertEquals(0.9, JSONObject(stored().last().scriptMessageJson).getDouble("value"), 0.0001)
    }

    @Test fun changesAreAnnouncedForSendAndUpdate() = runBlocking {
        val first = async { withTimeout(5_000) { ChatHistoryStore.changes.first() } }
        yield()
        val handle = requireNotNull(ChatHistoryStore.sendScriptMessage(context, chatId, progress(0.1), "pkg.a"))
        assertEquals(chatId, first.await())
        val second = async { withTimeout(5_000) { ChatHistoryStore.changes.first() } }
        yield()
        assertNotNull(ChatHistoryStore.updateScriptMessage(context, handle.id, "pkg.a", progress(0.5)))
        assertEquals(chatId, second.await())
    }

    @Test fun idempotentAppendNeverDuplicatesABackgroundResult() {
        val file = JSONObject().put("type", "text").put("text", "done").toString()
        assertTrue(ChatHistoryStore.appendScriptResult(context, chatId, file, "pkg.a", "download:1", idempotent = true))
        assertTrue(ChatHistoryStore.appendScriptResult(context, chatId, file, "pkg.a", "download:1", idempotent = true))
        assertEquals(1, stored().count { it.scriptCommandName == "download:1" })
        assertTrue(ChatHistoryStore.appendScriptResult(context, chatId, file, "pkg.a", "download:2", idempotent = true))
        assertEquals(1, stored().count { it.scriptCommandName == "download:2" })
        // Non-idempotent appends keep their old behaviour.
        ChatHistoryStore.appendScriptResult(context, chatId, file, "pkg.a", "download:3")
        ChatHistoryStore.appendScriptResult(context, chatId, file, "pkg.a", "download:3")
        assertEquals(2, stored().count { it.scriptCommandName == "download:3" })
    }
}
