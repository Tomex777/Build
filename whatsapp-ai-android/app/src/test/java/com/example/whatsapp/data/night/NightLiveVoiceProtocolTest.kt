package com.example.whatsapp.data.night

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightLiveVoiceProtocolTest {
    @Test
    fun foundryVoiceLiveEndpointUsesCurrentApiVersionAndModel() {
        val connection = NightLiveVoiceProtocol.connection(
            endpoint = "https://night.services.ai.azure.com",
            modelName = "gpt-realtime",
        )

        assertTrue(connection.voiceLiveApi)
        assertEquals(
            "wss://night.services.ai.azure.com/voice-live/realtime?api-version=2026-04-10&model=gpt-realtime",
            connection.url,
        )
    }

    @Test
    fun azureOpenAiEndpointUsesGaRealtimePath() {
        val connection = NightLiveVoiceProtocol.connection(
            endpoint = "https://night.openai.azure.com",
            modelName = "gpt-realtime",
        )

        assertFalse(connection.voiceLiveApi)
        assertEquals(
            "wss://night.openai.azure.com/openai/v1/realtime?model=gpt-realtime",
            connection.url,
        )
    }

    @Test
    fun voiceLiveSessionEnablesInputTranscriptionAndSemanticVad() {
        val update = NightLiveVoiceProtocol.sessionUpdate(
            displayName = "Dawson",
            voiceName = "alloy",
            chatSummary = "We were discussing Night.",
            modelName = "gpt-realtime",
            voiceLiveApi = true,
        )
        val session = update.getJSONObject("session")

        assertEquals("session.update", update.getString("type"))
        assertEquals("pcm16", session.getString("input_audio_format"))
        assertEquals("pcm16", session.getString("output_audio_format"))
        assertEquals(
            "whisper-1",
            session.getJSONObject("input_audio_transcription").getString("model"),
        )
        assertEquals(
            "azure_semantic_vad",
            session.getJSONObject("turn_detection").getString("type"),
        )
        assertTrue(session.getJSONObject("turn_detection").getBoolean("interrupt_response"))
    }

    @Test
    fun nonGptVoiceLiveUsesAzureSpeechForTranscript() {
        val update = NightLiveVoiceProtocol.sessionUpdate(
            displayName = "Dawson",
            voiceName = "en-US-AvaMultilingualNeural",
            chatSummary = "",
            modelName = "phi4-mm-realtime",
            voiceLiveApi = true,
        )

        assertEquals(
            "azure-speech",
            update
                .getJSONObject("session")
                .getJSONObject("input_audio_transcription")
                .getString("model"),
        )
    }

    @Test
    fun azureOpenAiRealtimeSessionUsesFlatGaAudioFields() {
        val session = NightLiveVoiceProtocol.sessionUpdate(
            displayName = "Dawson",
            voiceName = "alloy",
            chatSummary = "",
            modelName = "gpt-realtime",
            voiceLiveApi = false,
        ).getJSONObject("session")

        assertEquals("realtime", session.getString("type"))
        assertEquals("alloy", session.getString("voice"))
        assertEquals("pcm16", session.getString("input_audio_format"))
        assertEquals("pcm16", session.getString("output_audio_format"))
        assertEquals("server_vad", session.getJSONObject("turn_detection").getString("type"))
        assertFalse(session.has("audio"))
        assertFalse(session.has("output_modalities"))
    }
}
