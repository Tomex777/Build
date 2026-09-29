package com.tomex777.annie.processdeath

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tomex777.annie.MainActivity
import com.tomex777.annie.ChatHistoryStore
import com.tomex777.annie.saveEmulatorScreenshot
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessDeathRestoreTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun conversationReturnsAfterForceStopAndColdLaunch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(
            "The conversation seed was not persisted before the force-stop",
            context.getSharedPreferences("annie_chat_history_v1", 0)
                .getBoolean("ci_process_death_seeded", false),
        )
        assertTrue(
            "The conversation is missing from persisted history after the cold launch",
            ChatHistoryStore.read(context).any { session ->
                session.messages.any { it.fromUser && it.text == PROCESS_DEATH_MESSAGE }
            },
        )
        saveEmulatorScreenshot("annie-process-death-cold-launch-before-assert")
        Log.i(
            "ProcessDeathRestore",
            compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().config.toString(),
        )
        compose.waitUntil(10_000) {
            runCatching {
                compose.onNodeWithText(PROCESS_DEATH_MESSAGE, substring = false).assertIsDisplayed()
            }.isSuccess
        }
        compose.onNodeWithText(PROCESS_DEATH_MESSAGE, substring = false).assertIsDisplayed()
        saveEmulatorScreenshot("annie-process-death-restored-chat")
    }
}
