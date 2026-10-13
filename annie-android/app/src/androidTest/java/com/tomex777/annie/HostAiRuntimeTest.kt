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
    @Test fun aiRateLimitIsSharedAcrossProviderInstances() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val project = "ai-rate-" + System.nanoTime()
        val grants = setOf("ai.generate")
        val backend = object : HostAiBackend {
            override suspend fun generate(prompt: String, onDelta: suspend (String) -> Unit): String = "ok"
        }
        val invocation = OperationInvocation("ai-rate", true, setOf("ai"), grants, grants, project, "rate-chat", apiVersion = 2)
        repeat(5) {
            val registry = OperationRegistry().apply { register(HostAiOperationProvider(context, backend) { grants }) }
            assertEquals("ok", JSONObject(registry.invoke("ai.generate", invocation, "{\"prompt\":\"test\"}")).getString("text"))
        }
        val registry = OperationRegistry().apply { register(HostAiOperationProvider(context, backend) { grants }) }
        try { registry.invoke("ai.generate", invocation, "{\"prompt\":\"test\"}"); fail("A new provider bypassed the request limit") }
        catch (failure: AnnieError) { assertEquals(AnnieErrorCode.RATE_LIMITED, failure.code) }
    }

    @Test fun hostCredentialsAreEncryptedAndOutsideScriptEnvironmentStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousKey = HostAiConfig.key(context)
        val previousModel = HostAiConfig.model(context)
        val testKey = "fake-host-ai-proof-" + System.nanoTime()
        try {
            HostAiConfig.save(context, testKey, "test-model")
            assertEquals(testKey, HostAiConfig.key(context))
            val stored = context.getSharedPreferences("annie_host_ai_credentials", android.content.Context.MODE_PRIVATE).getString("key", null)
            assertNotNull(stored)
            assertFalse(stored.orEmpty().contains(testKey))
            val scriptValue = ScriptEnvStore(context, "host_ai").secret(ScriptEnvField("key", ScriptEnvFieldType.SECRET, "Key"))
            assertNotEquals(testKey, scriptValue)
        } finally { HostAiConfig.save(context, previousKey.orEmpty(), previousModel) }
    }

    @Test fun permissionRevocationDuringGenerationPreservesPartialTextAndEndsStreaming() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = ChatHistoryStore.read(context)
        val chat = "ai-revoke-" + System.nanoTime()
        var grants = setOf("ai.generate", "messages.post")
        val backend = object : HostAiBackend {
            override suspend fun generate(prompt: String, onDelta: suspend (String) -> Unit): String {
                onDelta("Partial")
                grants = emptySet()
                onDelta(" must not appear")
                return "Partial must not appear"
            }
        }
        try {
            ChatHistoryStore.write(context, previous + ChatSession(chat, mutableStateListOf(ChatEntry(1L, true, "hi"))))
            val registry = OperationRegistry().apply { register(HostAiOperationProvider(context, backend) { grants }) }
            val invocation = OperationInvocation("ai-revoke", true, setOf("ai", "messages"), grants, grants, "ai-revoke-project", chat, apiVersion = 2)
            try { registry.invoke("ai.stream", invocation, "{\"prompt\":\"hello\"}"); fail("Revoked permission accepted") }
            catch (failure: AnnieError) { assertEquals(AnnieErrorCode.NOT_GRANTED, failure.code) }
            val entry = ChatHistoryStore.read(context).single { it.id == chat }.messages.last()
            assertEquals("Partial", entry.text)
            val message = JSONObject(entry.scriptMessageJson)
            assertFalse(message.getBoolean("streaming"))
            assertTrue(message.getBoolean("streamFailed"))
        } finally { ChatHistoryStore.write(context, previous) }
    }

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
