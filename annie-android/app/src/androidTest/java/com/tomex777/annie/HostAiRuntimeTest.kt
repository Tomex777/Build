package com.tomex777.annie

import androidx.compose.runtime.mutableStateListOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostAiRuntimeTest {
    @Test fun aiStreamsIntoOneStoredMessageAndHonoursRevocation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = ChatHistoryStore.read(context)
        val chat = "host-ai-proof" + System.nanoTime()
        var grants = setOf("ai.generate", "messages.post")
        val frames = mutableListOf<String>()
        val backend = object : HostAiBackend {
            override suspend fun generate(prompt: String, onDelta: suspend (String) -> Unit): String {
                for (chunk in listOf("Hello", " world")) {
                    onDelta(chunk)
                    frames += ChatHistoryStore.read(context).single { it.id == chat }.messages.last().text
                }
                return "Hello world"
            }
        }
        try {
            ChatHistoryStore.write(context, previous + ChatSession(chat, mutableStateListOf(ChatEntry(1L, true, "hi"))))
            val registry = OperationRegistry().apply { register(HostAiOperationProvider(context, backend) { grants }) }
            val invocation = OperationInvocation("ai-proof", true, setOf("ai", "messages"), grants, grants, "project-ai-proof", chat, apiVersion = 2)
            registry.invoke("ai.stream", invocation, "{\"prompt\":\"hello\"}")
            assertEquals(listOf("Hello", "Hello world"), frames)
            val entries = ChatHistoryStore.read(context).single { it.id == chat }.messages
            assertEquals(2, entries.size)
            assertFalse(JSONObject(entries.last().scriptMessageJson).getBoolean("streaming"))
            grants = emptySet()
            try { registry.invoke("ai.generate", invocation, "{\"prompt\":\"hello\"}"); fail("Revoked AI permission accepted") }
            catch (failure: AnnieError) { assertEquals(AnnieErrorCode.NOT_GRANTED, failure.code) }
        } finally { ChatHistoryStore.write(context, previous) }
    }
}
