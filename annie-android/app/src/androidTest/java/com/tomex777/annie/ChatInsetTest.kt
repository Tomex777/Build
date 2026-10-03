package com.tomex777.annie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
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
        compose.onNodeWithTag("drawer_new_chat").assertIsDisplayed().performClick()
        compose.onNodeWithTag("conversation").assertIsDisplayed()
        compose.onNodeWithTag("composer_input").performClick().performTextInput("/scr")
        compose.onNodeWithTag("slash_suggestions").assertIsDisplayed()
        saveEmulatorScreenshot("annie-composer-keyboard")
        compose.onNodeWithText("/scripts", substring = false).performClick()
        compose.onNodeWithTag("composer_input").performTextInput("test")
        compose.onNodeWithTag("composer_input").assertTextEquals("/scripts test")
        assertEquals(0, compose.onAllNodesWithText("Ready when you are").fetchSemanticsNodes().size)

        val top = compose.onNodeWithTag("top_bar").fetchSemanticsNode().boundsInRoot
        val conversation = compose.onNodeWithTag("conversation").fetchSemanticsNode().boundsInRoot
        val composer = compose.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot
        val input = compose.onNodeWithTag("composer_input").fetchSemanticsNode().boundsInRoot
        val latestMessage = compose.onAllNodesWithTag("chat_message")[0].fetchSemanticsNode().boundsInRoot
        assertTrue("Top bar must start below the status bar inset", top.top > 0f)
        assertTrue("Conversation must start below the status-bar-safe top bar", conversation.top >= top.bottom)
        assertTrue("Conversation must end at the composer, without a blank gap", kotlin.math.abs(composer.top - conversation.bottom) <= 2f)
        val startOffsetPx = latestMessage.top - conversation.top
        assertTrue("Conversation messages should begin below the header and flow down from the top ($startOffsetPx px)",
            startOffsetPx in 0f..180f)
        assertTrue("Conversation message content must remain above the composer", latestMessage.bottom < composer.top)
        assertTrue("Composer must remain above the keyboard while focused", input.bottom <= composer.bottom)
    }

    @Test fun drawerDismissesKeyboardAndRestoresComposerTyping() {
        val imeFlag = android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        compose.onNodeWithTag("composer_input").performClick().performTextInput("Drawer keyboard proof")
        compose.onNodeWithTag("chat_history_button").performClick()
        compose.onNodeWithTag("drawer_about").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue("Drawer window must not target the keyboard", (compose.activity.window.attributes.flags and imeFlag) != 0)
        }
        saveEmulatorScreenshot("annie-drawer-keyboard-dismissed")
        val device = androidx.test.uiautomator.UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        )
        // The panel covers the left 82% of the screen; tap its exposed scrim.
        device.click((device.displayWidth * 0.95f).toInt(), device.displayHeight / 2)
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithTag("navigation_drawer_panel").fetchSemanticsNodes().isEmpty()
        }
        compose.runOnIdle {
            assertEquals("Closing the drawer must restore keyboard targeting", 0, compose.activity.window.attributes.flags and imeFlag)
        }
        compose.onNodeWithTag("composer_input").performClick().performTextInput(" restored")
        compose.onNodeWithTag("composer_input").assertTextEquals("Drawer keyboard proof restored")
        saveEmulatorScreenshot("annie-composer-after-drawer")
    }
}
