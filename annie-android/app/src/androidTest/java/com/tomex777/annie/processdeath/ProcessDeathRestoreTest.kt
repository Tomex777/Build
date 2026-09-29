package com.tomex777.annie.processdeath

import android.content.Intent
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.tomex777.annie.AnnieCharacters
import com.tomex777.annie.ChatEntry
import com.tomex777.annie.ChatHistoryStore
import com.tomex777.annie.ChatSession
import com.tomex777.annie.MainActivity
import com.tomex777.annie.saveEmulatorScreenshot
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessDeathRestoreTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var activity: ActivityScenario<MainActivity>? = null

    @After fun closeActivity() {
        activity?.close()
        activity = null
    }

    @Test fun conversationReturnsAfterForceStopAndColdLaunch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        // Start and close the app first so the test exercises a genuine app-process kill.
        activity = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        compose.waitForIdle()
        activity?.close()
        activity = null

        ChatHistoryStore.write(
            context,
            listOf(
                ChatSession(
                    id = "ci-process-death-proof",
                    messages = mutableStateListOf(
                        ChatEntry(975318642L, true, PROCESS_DEATH_MESSAGE),
                    ),
                    characterId = AnnieCharacters.default.id,
                ),
            ),
        )
        assertTrue(
            "The conversation seed was not durably written before the force-stop",
            context.getSharedPreferences("annie_chat_history_v1", 0)
                .edit()
                .putBoolean("ci_process_death_seeded", true)
                .commit(),
        )
        assertTrue(
            "The seeded conversation could not be read before the force-stop",
            ChatHistoryStore.read(context).any { session ->
                session.messages.any { it.fromUser && it.text == PROCESS_DEATH_MESSAGE }
            },
        )

        UiDevice.getInstance(instrumentation).executeShellCommand("am force-stop ${context.packageName}")
        activity = ActivityScenario.launch(Intent(context, MainActivity::class.java))

        // Keep a cold-launch screenshot even when a persisted-data or UI assertion fails.
        compose.waitForIdle()
        saveEmulatorScreenshot("annie-process-death-cold-launch")
        assertTrue(
            "The conversation seed was lost across app-process death",
            context.getSharedPreferences("annie_chat_history_v1", 0)
                .getBoolean("ci_process_death_seeded", false),
        )
        assertTrue(
            "The conversation is missing from persisted history after the cold launch",
            ChatHistoryStore.read(context).any { session ->
                session.messages.any { it.fromUser && it.text == PROCESS_DEATH_MESSAGE }
            },
        )
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithText(PROCESS_DEATH_MESSAGE, substring = false).assertIsDisplayed() }
                .isSuccess
        }
        compose.onNodeWithText(PROCESS_DEATH_MESSAGE, substring = false).assertIsDisplayed()
        saveEmulatorScreenshot("annie-process-death-restored-chat")
    }
}

internal const val PROCESS_DEATH_MESSAGE = "Conversation restored after process death"
