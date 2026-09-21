package com.example.whatsapp.data.night

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class NightAiGatewayProviderInstrumentedTest {
    private lateinit var server: MockWebServer
    private lateinit var secretStore: NightSecretStore
    private lateinit var secretAlias: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        secretStore = NightSecretStore.get(context)
        secretAlias = "provider_test_" + UUID.randomUUID()
        secretStore.initializeProviderKeyPool(
            alias = secretAlias,
            secret = "groq-key-a",
            label = "Primary",
        )
        secretStore.addProviderCredential(
            alias = secretAlias,
            secret = "groq-key-b",
            label = "Backup",
        )
    }

    @After
    fun tearDown() {
        secretStore.remove(secretAlias)
        server.shutdown()
    }

    @Test
    fun groq429RotatesToNextKeyAndSucceeds() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"message":"rate limited"}}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"choices":[{"message":{"role":"assistant","content":"NIGHT_OK"}}]}"""
                )
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gateway = NightAiGateway.createForTesting(
            context = context,
            http = OkHttpClient.Builder().build(),
        )
        val now = System.currentTimeMillis()
        val profile = NightProviderProfileEntity(
            id = "groq-test-profile",
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Groq test",
            secretAlias = secretAlias,
            endpoint = server.url("/openai/v1").toString().trimEnd('/'),
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )
        val model = NightProviderModelEntity(
            id = "groq-test-model",
            profileId = profile.id,
            providerType = "groq",
            modelId = "test-model",
            displayName = "Test model",
            capabilities = "text",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )

        val result = gateway.testModel(profile, model)

        assertTrue(result.isSuccess)
        assertEquals("NIGHT_OK", result.getOrThrow())

        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("Bearer groq-key-a", first.getHeader("Authorization"))
        assertEquals("Bearer groq-key-b", second.getHeader("Authorization"))
        assertEquals("/openai/v1/chat/completions", first.path)
        assertEquals("/openai/v1/chat/completions", second.path)
    }

    @Test
    fun nonRotatableServerErrorStopsAfterCurrentKey() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"message":"server error"}}""")
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gateway = NightAiGateway.createForTesting(
            context = context,
            http = OkHttpClient.Builder().build(),
        )
        val now = System.currentTimeMillis()
        val profile = NightProviderProfileEntity(
            id = "groq-test-profile-500",
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Groq test",
            secretAlias = secretAlias,
            endpoint = server.url("/openai/v1").toString().trimEnd('/'),
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )
        val model = NightProviderModelEntity(
            id = "groq-test-model-500",
            profileId = profile.id,
            providerType = "groq",
            modelId = "test-model",
            displayName = "Test model",
            capabilities = "text",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )

        val result = gateway.testModel(profile, model)

        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun groq401RotatesToNextKeyAndSucceeds() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"message":"invalid key"}}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"choices":[{"message":{"role":"assistant","content":"NIGHT_OK"}}]}"""
                )
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gateway = NightAiGateway.createForTesting(
            context = context,
            http = OkHttpClient.Builder().build(),
        )
        val now = System.currentTimeMillis()
        val profile = NightProviderProfileEntity(
            id = "groq-test-profile-401",
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Groq test",
            secretAlias = secretAlias,
            endpoint = server.url("/openai/v1").toString().trimEnd('/'),
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )
        val model = NightProviderModelEntity(
            id = "groq-test-model-401",
            profileId = profile.id,
            providerType = "groq",
            modelId = "test-model",
            displayName = "Test model",
            capabilities = "text",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )

        val result = gateway.testModel(profile, model)

        assertTrue(result.isSuccess)
        assertEquals("NIGHT_OK", result.getOrThrow())
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("Bearer groq-key-a", first.getHeader("Authorization"))
        assertEquals("Bearer groq-key-b", second.getHeader("Authorization"))
        assertEquals("/openai/v1/chat/completions", first.path)
        assertEquals("/openai/v1/chat/completions", second.path)
    }

    @Test
    fun groq403RotatesToNextKeyAndSucceeds() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"message":"forbidden key"}}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"choices":[{"message":{"role":"assistant","content":"NIGHT_OK"}}]}"""
                )
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gateway = NightAiGateway.createForTesting(
            context = context,
            http = OkHttpClient.Builder().build(),
        )
        val now = System.currentTimeMillis()
        val profile = NightProviderProfileEntity(
            id = "groq-test-profile-403",
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Groq test",
            secretAlias = secretAlias,
            endpoint = server.url("/openai/v1").toString().trimEnd('/'),
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )
        val model = NightProviderModelEntity(
            id = "groq-test-model-403",
            profileId = profile.id,
            providerType = "groq",
            modelId = "test-model",
            displayName = "Test model",
            capabilities = "text",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )

        val result = gateway.testModel(profile, model)

        assertTrue(result.isSuccess)
        assertEquals("NIGHT_OK", result.getOrThrow())
        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("Bearer groq-key-a", first.getHeader("Authorization"))
        assertEquals("Bearer groq-key-b", second.getHeader("Authorization"))
    }

    @Test
    fun groq429CooldownSkipsFailedKeyOnNextRequest() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"message":"rate limited"}}""")
        )
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"choices":[{"message":{"role":"assistant","content":"NIGHT_OK"}}]}"""
                    )
            )
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gateway = NightAiGateway.createForTesting(
            context = context,
            http = OkHttpClient.Builder().build(),
        )
        val now = System.currentTimeMillis()
        val profile = NightProviderProfileEntity(
            id = "groq-test-profile-cooldown",
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Groq test",
            secretAlias = secretAlias,
            endpoint = server.url("/openai/v1").toString().trimEnd('/'),
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )
        val model = NightProviderModelEntity(
            id = "groq-test-model-cooldown",
            profileId = profile.id,
            providerType = "groq",
            modelId = "test-model",
            displayName = "Test model",
            capabilities = "text",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )

        assertTrue(gateway.testModel(profile, model).isSuccess)
        assertTrue(gateway.testModel(profile, model).isSuccess)

        val first = server.takeRequest()
        val second = server.takeRequest()
        val third = server.takeRequest()
        assertEquals("Bearer groq-key-a", first.getHeader("Authorization"))
        assertEquals("Bearer groq-key-b", second.getHeader("Authorization"))
        assertEquals("Bearer groq-key-b", third.getHeader("Authorization"))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun nonGroq429DoesNotRotateCredentials() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"message":"rate limited"}}""")
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gateway = NightAiGateway.createForTesting(
            context = context,
            http = OkHttpClient.Builder().build(),
        )
        val now = System.currentTimeMillis()
        val profile = NightProviderProfileEntity(
            id = "deepseek-test-profile-429",
            providerType = "deepseek",
            serviceKind = "chat",
            displayName = "DeepSeek test",
            secretAlias = secretAlias,
            endpoint = server.url("/").toString().trimEnd('/'),
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )
        val model = NightProviderModelEntity(
            id = "deepseek-test-model-429",
            profileId = profile.id,
            providerType = "deepseek",
            modelId = "test-model",
            displayName = "Test model",
            capabilities = "text",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )

        val result = gateway.testModel(profile, model)

        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount)
        val first = server.takeRequest()
        assertEquals("Bearer groq-key-a", first.getHeader("Authorization"))
        assertEquals("/chat/completions", first.path)
    }


    @Test
    fun streamingAgentExecutesToolAndContinuesWithToolResult() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_time\",\"function\":{\"name\":\"get_current_time\",\"arguments\":\"{}\"}}]}}]}\n\n" +
                        "data: [DONE]\n\n"
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"Time checked successfully.\"}}]}\n\n" +
                        "data: [DONE]\n\n"
                )
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = NightRepository.get(context)
        val gateway = NightAiGateway.createForTesting(
            context = context,
            http = OkHttpClient.Builder().build(),
        )
        val suffix = UUID.randomUUID().toString()
        val chatId = "agent-tool-" + suffix
        val profileId = "agent-profile-" + suffix
        val modelId = "agent-model-" + suffix
        val now = System.currentTimeMillis()

        val profile = NightProviderProfileEntity(
            id = profileId,
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Agent tool test",
            secretAlias = secretAlias,
            endpoint = server.url("/openai/v1").toString().trimEnd('/'),
            isEnabled = true,
            isDefault = false,
            createdAt = now,
            updatedAt = now,
        )
        val model = NightProviderModelEntity(
            id = modelId,
            profileId = profileId,
            providerType = "groq",
            modelId = "agent-test-model",
            displayName = "Agent test model",
            capabilities = "text,tools",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )

        try {
            repository.ensureChat(chatId, "Agent tool test", now)
            repository.upsertProviderProfile(profile)
            repository.upsertProviderModel(model)
            repository.setChatModel(
                chatId = chatId,
                provider = "groq",
                profileId = profileId,
                model = modelId,
            )
            repository.appendText(
                chatId = chatId,
                role = "user",
                text = "What time is it?",
                now = now + 1,
            )

            val updates = mutableListOf<String>()
            val result = gateway.replyStreaming(
                chatId = chatId,
                displayName = "Tester",
            ) { updates += it }

            assertTrue(result.isSuccess)
            assertEquals("Time checked successfully.", result.getOrThrow())
            assertTrue(updates.contains("Time checked successfully."))
            assertEquals(2, server.requestCount)

            val firstBody = JSONObject(server.takeRequest().body.readUtf8())
            val secondBody = JSONObject(server.takeRequest().body.readUtf8())
            assertTrue(firstBody.optJSONArray("tools")?.length() ?: 0 > 0)

            val continuationMessages = secondBody.getJSONArray("messages")
            val toolMessage = (0 until continuationMessages.length())
                .map { continuationMessages.getJSONObject(it) }
                .firstOrNull { it.optString("role") == "tool" }
                ?: error("Expected tool result in continuation request.")
            assertEquals("call_time", toolMessage.optString("tool_call_id"))
            assertTrue(
                JSONObject(toolMessage.getString("content"))
                    .optBoolean("ok", false)
            )
        } finally {
            repository.deleteChat(chatId)
            repository.deleteProviderModel(modelId)
            repository.deleteProviderProfile(profileId)
        }
    }

    @Test
    fun azureChatUsesApiKeyHeaderAndDeploymentName() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"choices":[{"message":{"role":"assistant","content":"NIGHT_OK"}}]}"""
                )
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gateway = NightAiGateway.createForTesting(
            context = context,
            http = OkHttpClient.Builder().build(),
        )
        val now = System.currentTimeMillis()
        val profile = NightProviderProfileEntity(
            id = "azure-chat-" + UUID.randomUUID(),
            providerType = "azure",
            serviceKind = "chat",
            displayName = "Azure test",
            secretAlias = secretAlias,
            endpoint = server.url("/openai/v1").toString().trimEnd('/'),
            isEnabled = true,
            isDefault = false,
            createdAt = now,
            updatedAt = now,
        )
        val model = NightProviderModelEntity(
            id = "azure-model-" + UUID.randomUUID(),
            profileId = profile.id,
            providerType = "azure",
            modelId = "logical-model",
            displayName = "Azure deployment",
            deploymentName = "night-deployment",
            capabilities = "text",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )

        val result = gateway.testModel(profile, model)

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("groq-key-a", request.getHeader("api-key"))
        assertEquals(null, request.getHeader("Authorization"))
        assertEquals("/openai/v1/chat/completions", request.path)
        assertEquals(
            "night-deployment",
            JSONObject(request.body.readUtf8()).optString("model"),
        )
    }


    @Test
    fun deepSeekToolDiagnosticUsesBearerAndDisablesThinking() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"diag","type":"function","function":{"name":"night_diagnostic","arguments":"{}"}}]}}]}"""
                )
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gateway = NightAiGateway.createForTesting(
            context = context,
            http = OkHttpClient.Builder().build(),
        )
        val now = System.currentTimeMillis()
        val profile = NightProviderProfileEntity(
            id = "deepseek-tools-" + UUID.randomUUID(),
            providerType = "deepseek",
            serviceKind = "chat",
            displayName = "DeepSeek tools",
            secretAlias = secretAlias,
            endpoint = server.url("/").toString().trimEnd('/'),
            isEnabled = true,
            isDefault = false,
            createdAt = now,
            updatedAt = now,
        )
        val model = NightProviderModelEntity(
            id = "deepseek-tools-model-" + UUID.randomUUID(),
            profileId = profile.id,
            providerType = "deepseek",
            modelId = "deepseek-chat",
            displayName = "DeepSeek Chat",
            capabilities = "text,tools",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )

        val result = gateway.testModel(profile, model)

        assertTrue(result.isSuccess)
        assertEquals("NIGHT_OK", result.getOrThrow())

        val request = server.takeRequest()
        assertEquals("Bearer groq-key-a", request.getHeader("Authorization"))
        assertEquals("/chat/completions", request.path)

        val body = JSONObject(request.body.readUtf8())
        assertTrue(body.optJSONArray("tools")?.length() ?: 0 > 0)
        assertEquals(
            "disabled",
            body.getJSONObject("thinking").optString("type"),
        )
    }

}
