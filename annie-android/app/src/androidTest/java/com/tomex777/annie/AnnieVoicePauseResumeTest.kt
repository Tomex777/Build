package com.tomex777.annie
import android.graphics.Color
import android.media.MediaPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnieVoicePauseResumeTest {
    private fun fixture(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val samples = 8_000 * 3
        val pcmBytes = samples * 2
        val bytes = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(pcmBytes + 36)
        bytes.put("WAVEfmt ".toByteArray()).putInt(16)
        bytes.putShort(1).putShort(1)
        bytes.putInt(8_000).putInt(16_000).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(pcmBytes)
        repeat(samples) { bytes.putShort(0) }
        return File(context.cacheDir, "annie-pause-voice.wav").apply { writeBytes(bytes.array()) }
    }

    @Test fun pauseSeekAndResumePreservesPlaybackPosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sound = fixture()
        try {
            instrumentation.runOnMainSync { AnnieVoicePlayer.toggle(sound.absolutePath) }
            assertEquals(sound.absolutePath, AnnieVoicePlayer.playingPath)
            assertTrue(AnnieVoicePlayer.isPlaying)
            instrumentation.runOnMainSync { AnnieVoicePlayer.seek(sound.absolutePath, 0.5f) }
            Thread.sleep(250)
            instrumentation.runOnMainSync { AnnieVoicePlayer.toggle(sound.absolutePath) }
            assertFalse(AnnieVoicePlayer.isPlaying)
            val pausedMs = AnnieVoicePlayer.positionMs
            assertTrue("Pause lost the seek position", pausedMs >= 500f)
            instrumentation.runOnMainSync { AnnieVoicePlayer.toggle(sound.absolutePath) }
            assertTrue(AnnieVoicePlayer.isPlaying)
            assertEquals(sound.absolutePath, AnnieVoicePlayer.playingPath)
            assertTrue(AnnieVoicePlayer.positionMs >= pausedMs - 100f)
        } finally {
            instrumentation.runOnMainSync { AnnieVoicePlayer.stop() }
            sound.delete()
        }
    }
}
