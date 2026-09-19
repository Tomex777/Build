package com.example.whatsapp.data.night

import android.content.Context
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class NightSpeechService private constructor(
    private val context: Context,
    private val repository: NightRepository,
    private val secrets: NightSecretStore,
    private val http: OkHttpClient,
) {
    suspend fun transcribe(
        localPath: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val profile = speechProfile("stt")
            val key = secret(profile)
            val file = File(localPath)
            require(file.exists()) { "Voice note is missing." }
            require(file.length() > 44L) { "Voice note is empty." }

            val language = URLEncoder.encode(
                profile.language.ifBlank { "en-US" },
                "UTF-8",
            )

            val url = sttBase(profile) +
                "?language=" + language +
                "&format=simple"

            val request = Request.Builder()
                .url(url)
                .post(
                    file.asRequestBody(
                        "audio/wav; codecs=audio/pcm; samplerate=16000".toMediaType()
                    )
                )
                .header("Ocp-Apim-Subscription-Key", key)
                .header("Accept", "application/json")
                .build()

            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Speech recognition failed (" + response.code + "): " + speechError(raw))
                }

                val json = JSONObject(raw)
                val status = json.optString("RecognitionStatus")
                if (status.isNotBlank() && status != "Success") {
                    error("Speech recognition status: " + status)
                }

                val text = json.optString("DisplayText").trim()
                if (text.isBlank()) error("Azure Speech returned no transcript.")
                text
            }
        }
    }

    suspend fun synthesize(
        text: String,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(text.isNotBlank()) { "Nothing to speak." }

            val profile = speechProfile("tts")
            val key = secret(profile)
            val voice = profile.voiceName
                ?.takeIf { it.isNotBlank() }
                ?: error("Set a TTS voice name on the Azure Speech profile.")

            val language = profile.language.ifBlank { "en-US" }
            val ssml = "<speak version=\"1.0\" xml:lang=\"" +
                xmlEscape(language) +
                "\"><voice name=\"" +
                xmlEscape(voice) +
                "\">" +
                xmlEscape(text.take(8000)) +
                "</voice></speak>"

            val request = Request.Builder()
                .url(ttsBase(profile))
                .post(
                    ssml.toRequestBody(
                        "application/ssml+xml; charset=utf-8".toMediaType()
                    )
                )
                .header("Ocp-Apim-Subscription-Key", key)
                .header("X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3")
                .header("User-Agent", "Night")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    error("Speech synthesis failed (" + response.code + "): " + raw.take(240))
                }

                val bytes = response.body?.bytes()
                    ?: error("Azure Speech returned no audio.")

                val dir = File(context.cacheDir, "night_tts").apply { mkdirs() }
                File(
                    dir,
                    "night_tts_" + System.currentTimeMillis() + ".mp3",
                ).apply {
                    writeBytes(bytes)
                }
            }
        }
    }

    private suspend fun speechProfile(capability: String): NightProviderProfileEntity {
        val route = repository.capabilityRoute(capability)
        val routed = route?.providerProfileId
            ?.let { repository.getProviderProfile(it) }
            ?.takeIf { it.providerType == "azure" && it.serviceKind == "speech" && it.isEnabled }

        return routed
            ?: repository.defaultProviderProfile("speech")
            ?.takeIf { it.providerType == "azure" && it.isEnabled }
            ?: error("No Azure Speech profile is configured for " + capability.uppercase() + ".")
    }

    private fun secret(profile: NightProviderProfileEntity): String =
        secrets.get(profile.secretAlias)
            ?: error("The saved Azure Speech key is missing.")

    private fun sttBase(profile: NightProviderProfileEntity): String {
        val endpoint = profile.endpoint?.trim()?.trimEnd('/').orEmpty()
        if (endpoint.isNotBlank()) {
            return if (endpoint.contains("/stt/speech/recognition/")) {
                endpoint
            } else {
                endpoint + "/stt/speech/recognition/conversation/cognitiveservices/v1"
            }
        }

        val region = profile.region?.trim()?.takeIf { it.isNotBlank() }
            ?: error("Azure Speech region is missing.")
        return "https://" + region +
            ".stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1"
    }

    private fun ttsBase(profile: NightProviderProfileEntity): String {
        val endpoint = profile.endpoint?.trim()?.trimEnd('/').orEmpty()
        if (endpoint.isNotBlank()) {
            return if (endpoint.endsWith("/tts/cognitiveservices/v1")) {
                endpoint
            } else {
                endpoint + "/tts/cognitiveservices/v1"
            }
        }

        val region = profile.region?.trim()?.takeIf { it.isNotBlank() }
            ?: error("Azure Speech region is missing.")
        return "https://" + region + ".tts.speech.microsoft.com/cognitiveservices/v1"
    }

    private fun xmlEscape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun speechError(raw: String): String =
        runCatching {
            val json = JSONObject(raw)
            json.optString("DisplayText")
                .takeIf { it.isNotBlank() }
                ?: json.optString("RecognitionStatus").takeIf { it.isNotBlank() }
                ?: raw.take(240)
        }.getOrElse { raw.take(240) }

    companion object {
        @Volatile private var instance: NightSpeechService? = null

        fun get(context: Context): NightSpeechService =
            instance ?: synchronized(this) {
                val app = context.applicationContext
                instance ?: NightSpeechService(
                    context = app,
                    repository = NightRepository.get(app),
                    secrets = NightSecretStore.get(app),
                    http = OkHttpClient.Builder().build(),
                ).also { instance = it }
            }
    }
}
