package com.tomex777.annie

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatHistoryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
        compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
        hideEmulatorKeyboard(compose.activity)
        // The reply can immediately auto-scroll to the newest bubble, so the outgoing
        // animation node may legitimately leave the viewport. Its continued presence proves
        // the sent message used the animated path; persistence/reopen is asserted below.
        compose.onNodeWithTag("sent_message_animation").assertExists()
        val saved = ChatHistoryStore.read(context).first { it.title == "My saved conversation" }
        assertTrue(saved.messages.any { it.fromUser && it.text == "My saved conversation" })

        compose.onNodeWithTag("chat_history_button").performClick()
        // Finish drawer transitions before checking visibility or reopening it.
        compose.mainClock.advanceTimeBy(320)
        compose.waitForIdle()
        compose.waitUntil(4_000) {
            runCatching {
                compose.onNodeWithTag("drawer_new_chat").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        compose.onNodeWithTag("drawer_new_chat").performClick()
        compose.mainClock.advanceTimeBy(260)
        compose.waitForIdle()
        compose.onNodeWithTag("chat_history_button").performClick()
        compose.mainClock.advanceTimeBy(320)
        compose.waitForIdle()
        compose.onNodeWithTag("drawer_chat_${saved.id}").assertIsDisplayed().performClick()
        hideEmulatorKeyboard(compose.activity)
        compose.onNodeWithTag("conversation")
            .performScrollToNode(hasText("My saved conversation"))
        compose.onNodeWithText("My saved conversation").assertIsDisplayed()
    }

    @Test fun manageChatRenamesAndDeletesWithConfirmation() {
        clearSavedChats()
        compose.setContent { AnnieChat() }
        compose.onNodeWithTag("composer_input").performTextInput("Rename me")
        compose.onNodeWithTag("send_message").performClick()
        compose.waitForIdle()
        val saved = ChatHistoryStore.read(context).first { it.messages.any { row -> row.text == "Rename me" } }
        compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
        hideEmulatorKeyboard(compose.activity)
        compose.onNodeWithTag("composer_tools").performClick()
        compose.onNodeWithTag("quick_action_manage_chat").performClick()
        compose.onNodeWithTag("rename_chat_${saved.id}").performClick()
        compose.onNodeWithTag("rename_chat_input").performTextClearance()
        compose.onNodeWithTag("rename_chat_input").performTextInput("My workspace")
        compose.onNodeWithTag("rename_chat_confirm").performClick()
        compose.runOnIdle { assertEquals("My workspace", ChatHistoryStore.read(context).first { it.id == saved.id }.title) }
        compose.onNodeWithTag("delete_chat_${saved.id}").performClick()
        compose.onNodeWithTag("delete_chat_confirm").performClick()
        compose.runOnIdle { assertTrue(ChatHistoryStore.read(context).none { it.id == saved.id }) }
    }

    @Test fun navigationDrawerOpensLibraryAndRoutesToDownloads() {
        clearSavedChats()
        compose.setContent { AnnieChat() }

        compose.onNodeWithTag("chat_history_button").assertIsDisplayed()
        saveEmulatorScreenshot("annie-main-chat-closed")
        compose.onNodeWithTag("chat_history_button").performClick()
        compose.mainClock.advanceTimeBy(320)
        compose.waitForIdle()
        compose.onNodeWithTag("drawer_new_chat").assertIsDisplayed()
        compose.onNodeWithTag("annie_navigation_drawer").assertExists()
        compose.onNodeWithTag("drawer_scrim").assertExists()
        compose.onNodeWithTag("conversation").assertExists()
        val drawerBounds = compose.onAllNodesWithTag("navigation_drawer_panel").fetchSemanticsNodes().single().boundsInRoot
        val conversationBounds = compose.onAllNodesWithTag("conversation").fetchSemanticsNodes().single().boundsInRoot
        assertTrue(
            "Navigation drawer must leave a visible strip of the active chat",
            drawerBounds.right < conversationBounds.right,
        )
        compose.onNodeWithTag("drawer_profile").assertIsDisplayed()
        saveEmulatorScreenshot("annie-navigation-drawer")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(260)
        compose.waitForIdle()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("navigation_drawer_panel").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithTag("drawer_scrim").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("conversation").assertIsDisplayed()
        compose.onNodeWithTag("chat_history_button").performClick()
        compose.mainClock.advanceTimeBy(320)
        compose.waitForIdle()
        compose.onNodeWithTag("navigation_drawer_panel").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(260)
        compose.waitForIdle()
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("navigation_drawer_panel").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithTag("drawer_scrim").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("conversation").assertIsDisplayed()
        compose.onNodeWithTag("chat_history_button").performClick()
        compose.mainClock.advanceTimeBy(320)
        compose.waitForIdle()
        compose.onNodeWithTag("drawer_library").assertIsDisplayed().performClick()
        compose.onNodeWithTag("library_content").assertIsDisplayed()
        compose.onNodeWithTag("library_empty").assertDoesNotExist()
        compose.onNodeWithTag("library_manga").assertIsDisplayed()
        compose.onNodeWithTag("library_packages").assertDoesNotExist()
        saveEmulatorScreenshot("annie-library")

        compose.onNodeWithTag("library_downloads").assertIsDisplayed().performClick()
        compose.onNodeWithText("Downloads", substring = false).assertIsDisplayed()
    }

    @Test fun profilePickerUsesApprovedAvatarLibraryAndCanResetToAnnieMark() {
        val profilePrefs = context.getSharedPreferences(AnnieProfileAvatars.PREFERENCES, 0)
        profilePrefs.edit().clear().commit()
        clearSavedChats()
        compose.setContent { AnnieChat() }

        compose.onNodeWithTag("chat_history_button").performClick()
        compose.onNodeWithTag("drawer_profile").assertIsDisplayed().performClick()
        compose.onNodeWithText("Choose a profile image").assertIsDisplayed()
        compose.onNodeWithTag("profile_avatar_001").assertIsDisplayed()
        compose.onNodeWithTag("profile_avatar_076").assertIsDisplayed()
        compose.onNodeWithTag("profile_avatar_default").assertIsDisplayed()
        saveEmulatorScreenshot("annie-profile-picker")

        compose.onNodeWithTag("profile_avatar_006").performClick()
        compose.runOnIdle {
            assertEquals(R.drawable.annie_profile_006, profilePrefs.getInt(AnnieProfileAvatars.KEY, 0))
        }

        compose.onNodeWithTag("drawer_profile").performClick()
        compose.onNodeWithTag("profile_avatar_default").performClick()
        compose.runOnIdle {
            assertTrue(!profilePrefs.contains(AnnieProfileAvatars.KEY))
        }
        profilePrefs.edit().clear().commit()
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
