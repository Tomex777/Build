package com.nice.juicewrld

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Actual Android MediaPlayer acceptance test, not a fake play button or
 * synthesized mock of the player. A short original WAV is generated locally:
 * no copyrighted music, network access, external storage or permissions.
 */
@RunWith(AndroidJUnit4::class)
class FanAudioSmokeTest {
    @Test fun importedLocalWavPlaysSeeksPausesAndAdvancesQueue() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val first = File(app.cacheDir, "fan-smoke-lucid.wav")
        val second = File(app.cacheDir, "fan-smoke-robbery.wav")
        val prefs = app.getSharedPreferences("juice-fan", Context.MODE_PRIVATE)
        val originalLucid = prefs.getString("file:lucid-dreams", null)
        val originalRobbery = prefs.getString("file:robbery", null)
        writeWav(first, frequencyHz = 440.0)
        writeWav(second, frequencyHz = 523.25)
        // Pre-seeding the *same* persisted URI slots used by SAF isolates media
        // playback from the system document picker's UI and avoids special access.
        prefs.edit()
            .putString("file:lucid-dreams", Uri.fromFile(first).toString())
            .putString("file:robbery", Uri.fromFile(second).toString())
            .commit()

        var player: FanPlayer? = null
        try {
            instrumentation.runOnMainSync { player = FanPlayer(app) }
            val current = requireNotNull(player)
            instrumentation.runOnMainSync { current.playInMood("lucid-dreams", Mood.SAD) }
            awaitPlaying(current, "lucid-dreams")
            assertTrue("Expected a decoded WAV duration", current.state.value.durationMs >= 3_000)
            assertEquals(listOf("lucid-dreams", "robbery"), current.state.value.queueIds)

            // Clicking "Play mood" again should leave audio playing, not act
            // like a hidden pause toggle.
            instrumentation.runOnMainSync { current.playMood(Mood.SAD) }
            assertTrue("Play mood unexpectedly paused audio", current.state.value.isPlaying)

            instrumentation.runOnMainSync {
                current.seekTo(0.5f)
                current.pause()
            }
            assertFalse("Pause must stop actual playback", current.state.value.isPlaying)
            assertTrue("Seek must update playback position", current.state.value.positionMs >= 1_000)

            instrumentation.runOnMainSync { current.play("lucid-dreams") }
            awaitPlaying(current, "lucid-dreams")
            instrumentation.runOnMainSync { current.skip(1) }
            awaitPlaying(current, "robbery")
            assertTrue("Next audio clip should decode and play", current.state.value.durationMs >= 3_000)
        } finally {
            player?.let { current ->
                instrumentation.runOnMainSync { current.pause() }
            }
            val restore = prefs.edit()
            if (originalLucid == null) restore.remove("file:lucid-dreams")
            else restore.putString("file:lucid-dreams", originalLucid)
            if (originalRobbery == null) restore.remove("file:robbery")
            else restore.putString("file:robbery", originalRobbery)
            restore.commit()
            first.delete()
            second.delete()
        }
    }

    private fun awaitPlaying(player: FanPlayer, songId: String) {
        val deadline = SystemClock.elapsedRealtime() + 12_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val state = player.state.value
            if (state.activeSongId == songId && state.isPlaying) return
            if (state.error != null) throw AssertionError("Native audio failure: " + state.error)
            Thread.sleep(100)
        }
        throw AssertionError("Timed out waiting for native MediaPlayer: " + songId +
            ", state=" + player.state.value)
    }

    /** 4-second mono PCM16 RIFF; generated test tones, never bundled audio. */
    private fun writeWav(file: File, frequencyHz: Double) {
        val sampleRate = 16_000
        val samples = sampleRate * 4
        val pcmBytes = samples * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII))
            .putInt(36 + pcmBytes)
            .put("WAVE".toByteArray(Charsets.US_ASCII))
            .put("fmt ".toByteArray(Charsets.US_ASCII))
            .putInt(16).putShort(1.toShort()).putShort(1.toShort())
            .putInt(sampleRate).putInt(sampleRate * 2)
            .putShort(2.toShort()).putShort(16.toShort())
            .put("data".toByteArray(Charsets.US_ASCII))
            .putInt(pcmBytes).array()
        file.outputStream().buffered().use { out ->
            out.write(header)
            val chunk = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
            for (index in 0 until samples) {
                val wave = (sin(2.0 * PI * frequencyHz * index / sampleRate) * 2000).toInt()
                chunk.putShort(wave.toShort())
                if (!chunk.hasRemaining()) {
                    out.write(chunk.array())
                    chunk.clear()
                }
            }
            if (chunk.position() > 0) out.write(chunk.array(), 0, chunk.position())
        }
    }
}
