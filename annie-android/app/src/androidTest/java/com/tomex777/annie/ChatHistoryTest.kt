package com.tomex777.annie

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatHistoryTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun clearSavedChats() {
        context.getSharedPreferences("annie_chat_history_v1", 0).edit().clear().commit()
    }

    @After fun removeTestChats() {
        clearSavedChats()
    }

    @Test fun chatsPersistAndHistoryCanCreateAndReopenConversations() {
        clearSavedChats()
        compose.setContent { AnnieChat() }

        compose.onNodeWithTag("composer_input").performTextInput("My saved conversation")
        compose.onNodeWithTag("send_message").performClick()
        compose.waitForIdle()
        // The reply can immediately auto-scroll to the newest bubble, so the outgoing
        // animation node may legitimately leave the viewport. Its continued presence proves
        // the sent message used the animated path; persistence/reopen is asserted below.
        compose.onNodeWithTag("sent_message_animation").assertExists()
        val saved = ChatHistoryStore.read(context).first { it.title == "My saved conversation" }
        assertTrue(saved.messages.any { it.fromUser && it.text == "My saved conversation" })

        compose.onNodeWithTag("chat_history_button").performClick()
        compose.onNodeWithTag("new_chat_button").assertIsDisplayed().performClick()
        compose.onNodeWithTag("chat_history_button").performClick()
        compose.onNodeWithTag("chat_history_${saved.id}").assertIsDisplayed().performClick()
        compose.onNodeWithText("My saved conversation").assertIsDisplayed()
    }

    @Test fun historyStoreRestoresCatalogCardsAndTheirActions() {
        clearSavedChats()
        val item = CatalogItem(
            id = 47,
            mediaType = "MANGA",
            title = "Moonlit Archive",
            image = "https://example.test/cover.jpg",
            year = 2024,
            status = "RELEASING",
            episodes = null,
            chapters = 28,
            creator = "Yuna Mori",
            genres = listOf("Fantasy", "Mystery"),
            summary = "A hidden archive appears beneath the moonlit city.",
        )
        val original = ChatSession(
            id = "saved-manga-chat",
            messages = mutableStateListOf(ChatEntry(101, false, "", selectedItem = item, selectedStage = "manga")),
        )

        ChatHistoryStore.write(context, listOf(original))
        val restored = ChatHistoryStore.read(context).single()

        assertEquals(original.id, restored.id)
        assertEquals(item, restored.messages.single().selectedItem)
        assertEquals("manga", restored.messages.single().selectedStage)
    }
}
