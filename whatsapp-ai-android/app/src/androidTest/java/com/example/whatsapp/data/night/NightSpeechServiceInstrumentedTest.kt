package com.example.whatsapp.data.night

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class NightSpeechServiceInstrumentedTest {
    @Test
    fun transcriptionPostsSavedWavToConfiguredAzureSpeechAndParsesTranscript() = runBlocking {
        val server = MockWebServer()
        server.start()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = NightRepository.get(context)
        val secrets = NightSecretStore.get(context)
        val suffix = UUID.randomUUID().toString()
        val profileId = "speech-profile-" + suffix
        val secretAlias = "speech-secret-" + suffix
        val audioFile = File(context.cacheDir, "speech-test-" + suffix + ".wav")
        val previousRoute = repository.capabilityRoute("stt")
        val now = System.currentTimeMillis()
        secrets.initializeProviderKeyPool(secretAlias, "speech-test-key", "Test")
        repository.upsertProviderProfile(
            NightProviderProfileEntity(
                id = profileId,
                providerType = "azure",
                serviceKind = "speech",
                displayName = "Speech test",
                secretAlias = secretAlias,
                endpoint = server.url("/").toString().trimEnd('/'),
                language = "en-US",
                isEnabled = true,
                isDefault = true,
                createdAt = now,
                updatedAt = now,
            )
        )
        repository.setCapabilityRoute(
            NightCapabilityRouteEntity(
                id = "stt",
                capability = "stt",
                providerProfileId = profileId,
                useSelectedChatModelFirst = false,
                updatedAt = now,
            )
        )
        val wavBytes = makeWavBytes()
        audioFile.writeBytes(wavBytes)

        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"RecognitionStatus":"Success","DisplayText":"open the AnimePahe browser"}""")
            )
            val service = NightSpeechService.createForTesting(
                context = context,
                http = OkHttpClient.Builder().build(),
            )

            val transcript = service.transcribe(audioFile.absolutePath).getOrThrow()
            val request = server.takeRequest()
            assertEquals("open the AnimePahe browser", transcript)
            assertEquals(
                "/stt/speech/recognition/conversation/cognitiveservices/v1",
                request.requestUrl?.encodedPath,
            )
            assertEquals("en-US", request.requestUrl?.queryParameter("language"))
            assertEquals("audio/wav; codecs=audio/pcm; samplerate=16000", request.getHeader("Content-Type"))
            assertEquals("speech-test-key", request.getHeader("Ocp-Apim-Subscription-Key"))
            assertEquals(wavBytes.size.toLong(), request.body.size)

            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"message":"subscription key rejected"}""")
            )
            val failed = service.transcribe(audioFile.absolutePath)
            assertTrue(failed.isFailure)
            assertTrue(failed.exceptionOrNull()?.message.orEmpty().contains("401"))
            assertTrue(audioFile.exists() && audioFile.length() > 44L)
        } finally {
            if (previousRoute == null) {
                repository.clearCapabilityRoute("stt")
            } else {
                repository.setCapabilityRoute(previousRoute)
            }
            repository.deleteProviderProfile(profileId)
            secrets.remove(secretAlias)
            audioFile.delete()
            server.shutdown()
        }
    }

    private fun makeWavBytes(): ByteArray {
        val sampleBytes = ByteArray(160)
        return ByteBuffer.allocate(44 + sampleBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII))
            .putInt(36 + sampleBytes.size)
            .put("WAVE".toByteArray(Charsets.US_ASCII))
            .put("fmt ".toByteArray(Charsets.US_ASCII))
            .putInt(16)
            .putShort(1)
            .putShort(1)
            .putInt(16_000)
            .putInt(32_000)
            .putShort(2)
            .putShort(16)
            .put("data".toByteArray(Charsets.US_ASCII))
            .putInt(sampleBytes.size)
            .put(sampleBytes)
            .array()
    }
}
