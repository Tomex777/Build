package com.tomex777.annie.processdeath

import androidx.compose.runtime.mutableStateListOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tomex777.annie.AnnieCharacters
import com.tomex777.annie.ChatEntry
import com.tomex777.annie.ChatHistoryStore
import com.tomex777.annie.ChatSession
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessDeathSeedTest {
    @Test fun seedConversationForHostDrivenColdRestore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
            "The conversation seed marker was not durably written",
            context.getSharedPreferences("annie_chat_history_v1", 0)
                .edit()
                .putBoolean("ci_process_death_seeded", true)
                .commit(),
        )
        assertTrue(
            "The conversation seed could not be read before force-stop",
            ChatHistoryStore.read(context).any { session ->
                session.messages.any { it.fromUser && it.text == PROCESS_DEATH_MESSAGE }
            },
        )
    }
}

internal const val PROCESS_DEATH_MESSAGE = "Conversation restored after process death"
