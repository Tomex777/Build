package com.tomex777.annie

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnieVoiceChatLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun silentWav(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sampleCount = 8_000 * 3
        val pcmBytes = sampleCount * 2
        val bytes = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(pcmBytes + 36)
        bytes.put("WAVEfmt ".toByteArray()).putInt(16)
        bytes.putShort(1).putShort(1)
        bytes.putInt(8_000).putInt(16_000).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(pcmBytes)
        repeat(sampleCount) { bytes.putShort(0) }
        return File(context.cacheDir, "annie-voice-chat-switch.wav").apply { writeBytes(bytes.array()) }
    }

    @Test fun switchingChatsStopsInlineVoiceNotePlayback() {
        val sound = silentWav()
        try {
            compose.waitForIdle()
            compose.runOnIdle {
                AnnieVoicePlayer.toggle(sound.absolutePath)
                assertTrue("Voice note should start before chat switch", AnnieVoicePlayer.isPlaying)
            }
            compose.onNodeWithTag("chat_history_button").performClick()
            compose.onNodeWithTag("drawer_new_chat").performClick()
            compose.runOnIdle {
                assertFalse("Voice from an earlier chat was left playing", AnnieVoicePlayer.isPlaying)
                assertTrue(AnnieVoicePlayer.playingPath == null)
            }
        } finally {
            compose.runOnIdle { AnnieVoicePlayer.stop() }
            sound.delete()
        }
    }
}
