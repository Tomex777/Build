package com.tomex777.annie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatInsetTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun keyboardLayoutKeepsStatusBarConversationAndComposerInOrder() {
        compose.onNodeWithTag("top_bar").assertIsDisplayed()
        compose.onNodeWithTag("chat_history_button").performClick()
        compose.onNodeWithTag("new_chat_button").assertIsDisplayed().performClick()
        compose.onNodeWithTag("conversation").assertIsDisplayed()
        compose.onNodeWithTag("composer_input").performClick().performTextInput("/ani")
        compose.onNodeWithTag("slash_suggestions").assertIsDisplayed()
        compose.onNodeWithText("/anime", substring = false).performClick()
        compose.onNodeWithTag("composer_input").performTextInput("search")
        compose.onNodeWithTag("composer_input").assertTextEquals("/anime search")
        compose.onNodeWithText("Ready when you are").assertDoesNotExist()

        val top = compose.onNodeWithTag("top_bar").fetchSemanticsNode().boundsInRoot
        val conversation = compose.onNodeWithTag("conversation").fetchSemanticsNode().boundsInRoot
        val composer = compose.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot
        val input = compose.onNodeWithTag("composer_input").fetchSemanticsNode().boundsInRoot
        val latestMessage = compose.onNodeWithText("Hi, I’m Annie. What are you in the mood for? Type a command to start. Providers stay separate, and I’ll show clearly when one is unavailable.").fetchSemanticsNode().boundsInRoot
        assertTrue("Top bar must start below the status bar inset", top.top > 0f)
        assertTrue("Conversation must start below the status-bar-safe top bar", conversation.top >= top.bottom)
        assertTrue("Conversation must end at the composer, without a blank gap", kotlin.math.abs(composer.top - conversation.bottom) <= 2f)
        val startOffsetPx = latestMessage.top - conversation.top
        assertTrue("Conversation messages should begin below the header and flow down from the top ($startOffsetPx px)",
            startOffsetPx in 0f..180f)
        assertTrue("With only the welcome message, content should not be bottom anchored", latestMessage.bottom < composer.top - 180f)
        assertTrue("Composer must remain above the keyboard while focused", input.bottom <= composer.bottom)
    }
}
