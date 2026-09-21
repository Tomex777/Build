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

}
