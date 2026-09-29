package com.tomex777.annie

import androidx.compose.runtime.mutableStateListOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessDeathSeedTest {
    @Test fun seedConversationForColdProcessRestore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val marker = PROCESS_DEATH_MESSAGE
        ChatHistoryStore.write(
            context,
            listOf(
                ChatSession(
                    id = "ci-process-death-proof",
                    messages = mutableStateListOf(ChatEntry(975318642L, true, marker)),
                    characterId = AnnieCharacters.default.id,
                ),
            ),
        )
        assertTrue(
            context.getSharedPreferences("annie_chat_history_v1", 0)
                .edit()
                .putBoolean("ci_process_death_seeded", true)
                .commit(),
        )
        assertTrue(ChatHistoryStore.read(context).any { session ->
            session.messages.any { it.fromUser && it.text == marker }
        })
    }
}

internal const val PROCESS_DEATH_MESSAGE = "Conversation restored after process death"
