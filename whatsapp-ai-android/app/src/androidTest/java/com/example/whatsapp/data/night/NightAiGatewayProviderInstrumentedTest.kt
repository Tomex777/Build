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
    fun streamingReplyEmitsIncrementalSseTextDeltas() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n\n" +
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
        val chatId = "agent-stream-" + suffix
        val profileId = "stream-profile-" + suffix
        val modelId = "stream-model-" + suffix
        val now = System.currentTimeMillis()

        val profile = NightProviderProfileEntity(
            id = profileId,
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Streaming test",
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
            modelId = "stream-test-model",
            displayName = "Streaming test model",
            capabilities = "text",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )

        try {
            repository.ensureChat(chatId, "Streaming test", now)
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
                text = "Stream a short greeting.",
                now = now + 1,
            )

            val updates = mutableListOf<String>()
            val result = gateway.replyStreaming(
                chatId = chatId,
                displayName = "Tester",
            ) { updates += it }

            assertTrue(result.isSuccess)
            assertEquals("Hello world", result.getOrThrow())
            assertTrue(updates.contains("Hello"))
            assertTrue(updates.contains("Hello world"))
            assertTrue(updates.indexOf("Hello") < updates.indexOf("Hello world"))
            assertEquals(1, server.requestCount)

            val request = server.takeRequest()
            assertEquals("text/event-stream", request.getHeader("Accept"))
            val body = JSONObject(request.body.readUtf8())
            assertTrue(body.optBoolean("stream", false))
        } finally {
            repository.deleteChat(chatId)
            repository.deleteProviderModel(modelId)
            repository.deleteProviderProfile(profileId)
        }
    }

    @Test
    fun providerRequestKeepsMultiTurnExtensionAndReplyReferences() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"I can open the verification flow.\"}}]}\n\n" +
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
        val chatId = "context-reference-" + suffix
        val profileId = "context-profile-" + suffix
        val modelId = "context-model-" + suffix
        val extensionResultId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val profile = NightProviderProfileEntity(
            id = profileId,
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Context test",
            secretAlias = secretAlias,
            endpoint = server.url("/openai/v1").toString().trimEnd('/'),
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )
        val model = NightProviderModelEntity(
            id = modelId,
            profileId = profileId,
            providerType = "groq",
            modelId = "context-test-model",
            displayName = "Context test model",
            capabilities = "text",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )

        try {
            repository.ensureChat(chatId, "Context test", now)
            repository.upsertProviderProfile(profile)
            repository.upsertProviderModel(model)
            repository.setChatModel(chatId, "groq", profileId, modelId)
            repository.appendText(
                chatId = chatId,
                role = "user",
                text = "The AnimePahe extension still needs verification.",
                now = now,
            )
            repository.appendMessage(
                NightMessageEntity(
                    id = extensionResultId,
                    chatId = chatId,
                    role = "assistant",
                    type = "extension",
                    text = "AnimePahe verification needed",
                    createdAt = now,
                    payloadJson = JSONObject()
                        .put("extensionName", "AnimePahe")
                        .put("title", "AnimePahe verification needed")
                        .put("body", "Open AnimePahe in Night Browser and finish Cloudflare verification.")
                        .toString(),
                )
            )
            repeat(65) { index ->
                repository.appendText(
                    chatId = chatId,
                    role = "assistant",
                    text = "Unrelated intervening message $index.",
                    now = now,
                )
            }
            repository.appendText(
                chatId = chatId,
                role = "user",
                text = "Can the extension open it automatically?",
                replyToMessageId = extensionResultId,
                now = now,
            )

            val result = gateway.replyStreaming(chatId, "Tester") { }
            assertTrue(result.isSuccess)

            val request = server.takeRequest()
            val body = request.body.readUtf8()
            val requestMessages = JSONObject(body).getJSONArray("messages")
            val submittedContext = (0 until requestMessages.length())
                .map { requestMessages.getJSONObject(it).optString("content") }
                .joinToString("\n")
            assertTrue(submittedContext.contains("finish Cloudflare verification"))
            assertTrue(submittedContext.contains("Reply context"))
            assertTrue(submittedContext.contains("Can the extension open it automatically?"))
            assertTrue(submittedContext.contains("Night Extensions are integrations installed in the Night app"))
            assertTrue(submittedContext.contains("not Chrome, Firefox"))
            assertTrue(!body.contains("groq-key-a"))
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


    @Test
    fun chatReplyFallsBackToAnotherEnabledProviderAfterFailure() = runBlocking {
        val fallbackServer = MockWebServer()
        fallbackServer.start()

        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"message":"primary unavailable"}}""")
        )
        fallbackServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"Fallback answer.\"}}]}\n\n" +
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
        val chatId = "provider-fallback-" + suffix
        val primaryProfileId = "primary-profile-" + suffix
        val primaryModelId = "primary-model-" + suffix
        val fallbackProfileId = "fallback-profile-" + suffix
        val fallbackModelId = "fallback-model-" + suffix
        val now = System.currentTimeMillis()

        val primaryProfile = NightProviderProfileEntity(
            id = primaryProfileId,
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Primary failing provider",
            secretAlias = secretAlias,
            endpoint = server.url("/openai/v1").toString().trimEnd('/'),
            isEnabled = true,
            isDefault = false,
            createdAt = now,
            updatedAt = now,
        )
        val primaryModel = NightProviderModelEntity(
            id = primaryModelId,
            profileId = primaryProfileId,
            providerType = "groq",
            modelId = "primary-model",
            displayName = "Primary model",
            capabilities = "text",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )
        val fallbackProfile = NightProviderProfileEntity(
            id = fallbackProfileId,
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Fallback provider",
            secretAlias = secretAlias,
            endpoint = fallbackServer.url("/openai/v1").toString().trimEnd('/'),
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now + 1,
        )
        val fallbackModel = NightProviderModelEntity(
            id = fallbackModelId,
            profileId = fallbackProfileId,
            providerType = "groq",
            modelId = "fallback-model",
            displayName = "Fallback model",
            capabilities = "text",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now + 1,
        )

        try {
            repository.ensureChat(chatId, "Provider fallback", now)
            repository.upsertProviderProfile(primaryProfile)
            repository.upsertProviderModel(primaryModel)
            repository.upsertProviderProfile(fallbackProfile)
            repository.upsertProviderModel(fallbackModel)
            repository.setChatModel(
                chatId = chatId,
                provider = "groq",
                profileId = primaryProfileId,
                model = primaryModelId,
            )
            repository.appendText(
                chatId = chatId,
                role = "user",
                text = "Answer this through provider failover.",
                now = now + 2,
            )

            val updates = mutableListOf<String>()
            val result = gateway.replyStreaming(
                chatId = chatId,
                displayName = "Tester",
            ) { updates += it }

            assertTrue(result.isSuccess)
            assertEquals("Fallback answer.", result.getOrThrow())
            assertEquals(1, server.requestCount)
            assertEquals(1, fallbackServer.requestCount)
            assertTrue(updates.contains(""))
            assertTrue(updates.contains("Fallback answer."))
        } finally {
            repository.deleteChat(chatId)
            repository.deleteProviderModel(primaryModelId)
            repository.deleteProviderModel(fallbackModelId)
            repository.deleteProviderProfile(primaryProfileId)
            repository.deleteProviderProfile(fallbackProfileId)
            fallbackServer.shutdown()
        }
    }

    @Test
    fun completedSideEffectPreventsCrossProviderRetry() = runBlocking {
        val fallbackServer = MockWebServer()
        fallbackServer.start()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                        "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_options\",\"function\":{\"name\":\"create_options\",\"arguments\":\"{\\\"title\\\":\\\"Pick one\\\",\\\"options\\\":[\\\"A\\\",\\\"B\\\"],\\\"multiple\\\":true}\"}}]}}]}\n\n" +
                        "data: [DONE]\n\n"
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_options_repeat\",\"function\":{\"name\":\"create_options\",\"arguments\":\"{\\\"title\\\":\\\"Pick one\\\",\\\"options\\\":[\\\"A\\\",\\\"B\\\"],\\\"multiple\\\":true}\"}}]}}]}\n\n" +
                        "data: [DONE]\n\n"
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":{"message":"continuation failed"}}""")
        )
        fallbackServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"This must not run.\"}}]}\n\n" +
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
        val chatId = "side-effect-no-retry-" + suffix
        val primaryProfileId = "side-primary-profile-" + suffix
        val primaryModelId = "side-primary-model-" + suffix
        val fallbackProfileId = "side-fallback-profile-" + suffix
        val fallbackModelId = "side-fallback-model-" + suffix
        val now = System.currentTimeMillis()

        val primaryProfile = NightProviderProfileEntity(
            id = primaryProfileId,
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Primary side-effect provider",
            secretAlias = secretAlias,
            endpoint = server.url("/openai/v1").toString().trimEnd('/'),
            isEnabled = true,
            isDefault = false,
            createdAt = now,
            updatedAt = now,
        )
        val primaryModel = NightProviderModelEntity(
            id = primaryModelId,
            profileId = primaryProfileId,
            providerType = "groq",
            modelId = "side-primary-model",
            displayName = "Primary tools model",
            capabilities = "text,tools",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now,
        )
        val fallbackProfile = NightProviderProfileEntity(
            id = fallbackProfileId,
            providerType = "groq",
            serviceKind = "chat",
            displayName = "Fallback side-effect provider",
            secretAlias = secretAlias,
            endpoint = fallbackServer.url("/openai/v1").toString().trimEnd('/'),
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now + 1,
        )
        val fallbackModel = NightProviderModelEntity(
            id = fallbackModelId,
            profileId = fallbackProfileId,
            providerType = "groq",
            modelId = "side-fallback-model",
            displayName = "Fallback tools model",
            capabilities = "text,tools",
            isEnabled = true,
            isDefault = true,
            createdAt = now,
            updatedAt = now + 1,
        )

        try {
            repository.ensureChat(chatId, "Side effect retry guard", now)
            repository.upsertProviderProfile(primaryProfile)
            repository.upsertProviderModel(primaryModel)
            repository.upsertProviderProfile(fallbackProfile)
            repository.upsertProviderModel(fallbackModel)
            repository.setChatModel(
                chatId = chatId,
                provider = "groq",
                profileId = primaryProfileId,
                model = primaryModelId,
            )
            repository.appendText(
                chatId = chatId,
                role = "user",
                text = "Give me two options.",
                now = now + 2,
            )

            val result = gateway.replyStreaming(
                chatId = chatId,
                displayName = "Tester",
            ) { }

            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull()
                    ?.message
                    ?.contains("A Night action completed") == true
            )
            assertEquals(3, server.requestCount)
            assertEquals(0, fallbackServer.requestCount)

            val choiceMessages = repository.getMessages(chatId)
                .filter { it.type == "choice" }
            assertEquals(1, choiceMessages.size)
            assertEquals("Pick one", choiceMessages.single().text)
            assertTrue(JSONObject(choiceMessages.single().payloadJson).optBoolean("multiple"))
        } finally {
            repository.deleteChat(chatId)
            repository.deleteProviderModel(primaryModelId)
            repository.deleteProviderModel(fallbackModelId)
            repository.deleteProviderProfile(primaryProfileId)
            repository.deleteProviderProfile(fallbackProfileId)
            fallbackServer.shutdown()
        }
    }

}
