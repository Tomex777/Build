package com.example.whatsapp.data.night

import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

internal data class NightLiveVoiceConnection(
    val url: String,
    val voiceLiveApi: Boolean,
)

internal object NightLiveVoiceProtocol {
    const val SAMPLE_RATE = 24_000

    fun connection(
        endpoint: String,
        modelName: String,
    ): NightLiveVoiceConnection {
        var base = endpoint.trim().trimEnd('/')

        base = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
            else -> base
        }

        val encodedModel = URLEncoder.encode(modelName, "UTF-8")
        val voiceLiveApi =
            base.contains(".services.ai.azure.com", ignoreCase = true) ||
                (
                    base.contains(".cognitiveservices.azure.com", ignoreCase = true) &&
                        !base.contains(".openai.azure.com", ignoreCase = true)
                )

        if (voiceLiveApi) {
            val root = if (base.contains("/voice-live/")) {
                base.substringBefore("/voice-live/")
            } else {
                base
            }
            return NightLiveVoiceConnection(
                url = root +
                    "/voice-live/realtime?api-version=2026-04-10&model=" +
                    encodedModel,
                voiceLiveApi = true,
            )
        }

        val openAiBase = when {
            base.endsWith("/openai/v1") -> base
            base.contains("/openai/v1/") -> base.substringBefore("/openai/v1/") + "/openai/v1"
            else -> base + "/openai/v1"
        }

        return NightLiveVoiceConnection(
            url = openAiBase + "/realtime?model=" + encodedModel,
            voiceLiveApi = false,
        )
    }

    fun sessionUpdate(
        displayName: String,
        voiceName: String,
        chatSummary: String,
        modelName: String,
        voiceLiveApi: Boolean,
    ): JSONObject {
        val instructions = buildString {
            append("You are Night, the user's private AI assistant. ")
            append("The user's preferred name is ")
            append(displayName)
            append(". Speak naturally and concisely. This is a live voice conversation. ")
            if (chatSummary.isNotBlank()) {
                append("Conversation summary: ")
                append(chatSummary.take(3000))
            }
        }

        val session = if (voiceLiveApi) {
            JSONObject()
                .put("modalities", JSONArray().put("text").put("audio"))
                .put(
                    "voice",
                    JSONObject()
                        .put("type", "openai")
                        .put("name", voiceName)
                )
                .put("instructions", instructions)
                .put("input_audio_format", "pcm16")
                .put("output_audio_format", "pcm16")
                .put("input_audio_sampling_rate", SAMPLE_RATE)
                .put(
                    "input_audio_transcription",
                    JSONObject().put(
                        "model",
                        if (modelName.startsWith("gpt-realtime", ignoreCase = true)) {
                            "whisper-1"
                        } else {
                            "azure-speech"
                        },
                    )
                )
                .put(
                    "turn_detection",
                    JSONObject()
                        .put("type", "azure_semantic_vad")
                        .put("threshold", 0.5)
                        .put("prefix_padding_ms", 420)
                        .put("silence_duration_ms", 500)
                        .put("create_response", true)
                        .put("interrupt_response", true)
                )
        } else {
            JSONObject()
                .put("type", "realtime")
                .put("modalities", JSONArray().put("text").put("audio"))
                .put("voice", voiceName)
                .put("instructions", instructions)
                .put("input_audio_format", "pcm16")
                .put("output_audio_format", "pcm16")
                .put(
                    "turn_detection",
                    JSONObject()
                        .put("type", "server_vad")
                        .put("threshold", 0.5)
                        .put("prefix_padding_ms", 300)
                        .put("silence_duration_ms", 500)
                        .put("create_response", true)
                )
        }

        return JSONObject()
            .put("type", "session.update")
            .put("session", session)
    }
}
