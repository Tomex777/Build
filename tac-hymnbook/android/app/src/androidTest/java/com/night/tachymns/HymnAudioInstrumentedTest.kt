package com.night.tachymns

import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Tests playback with a locally synthesized tone created ONLY inside test storage.
 * No third-party hymn recording is copied into either APK.
 */
@RunWith(AndroidJUnit4::class)
class HymnAudioInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun sectionAndVoiceKeysNeverCollide() {
        val a = AudioKey("main", 1, "yo", "solo")
        val b = AudioKey("akokun", 1, "yo", "solo")
        val c = AudioKey("main", 1, "en", "solo")
        val d = AudioKey("main", 1, "yo", "choir")
        assertEquals(4, listOf(a.token,b.token,c.token,d.token).distinct().size)
        assertEquals(b, AudioKey.parse(b.token))
        assertNull(AudioKey.parse("akokun:306:yo:solo"))
    }

    @Test
    fun importedTrackLinkIsRemovableWithoutDeletingOriginal() {
        val repository = HymnAudioRepository(context)
        val key = AudioKey("main", 50, "yo", "solo")
        val local = File(context.cacheDir, "never-delete-original.mp3")
        try {
            local.writeText("A user file is not owned by the hymnbook.")
            repository.import(key, Uri.parse("content://test.example/document/50"))
            assertTrue(repository.storedRecordings().any { it.key == key && it.kind == "Imported file" })
            assertTrue(repository.removeStoredAudio(StoredAudio(key, "Imported file", 0)))
            assertTrue(local.exists())
            assertNull(repository.source(key))
        } finally {
            repository.clearImport(key)
            local.delete()
        }
    }

    @Test
    fun nativePlayerCanPlayPauseAndSeekGeneratedAudio() {
        val audio = File(context.cacheDir, "playback-test.wav")
        audio.writeBytes(waveTone())
        val key = AudioKey("main", 1, "yo", "solo")
        val player = HymnAudioPlayer(context)
        try {
            instrumentation.runOnMainSync {
                player.toggle(key, AudioSource.Downloaded(audio))
            }
            var prepared = false
            for (step in 0..30) {
                SystemClock.sleep(150)
                instrumentation.runOnMainSync {
                    prepared = !player.loading && player.durationMs >= 1300
                }
                if (prepared) break
            }
            assertTrue("Synthetic PCM should prepare as playable audio", prepared)
            instrumentation.runOnMainSync {
                assertTrue("Playback should begin after preparation", player.playing)
                player.seekTo(650)
                assertTrue("Seeking should update displayed time", player.positionMs >= 650)
                player.toggle(key, AudioSource.Downloaded(audio))
                assertFalse("Playback should pause", player.playing)
                player.toggle(key, AudioSource.Downloaded(audio))
                assertTrue("Playback should resume", player.playing)
            }
        } finally {
            instrumentation.runOnMainSync { player.release() }
            audio.delete()
        }
    }

    private fun waveTone(): ByteArray {
        val sampleRate = 16000
        val sampleCount = sampleRate * 2
        val size = sampleCount * 2
        val buffer = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + size)
        buffer.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1.toShort())
        buffer.putShort(1.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)
        buffer.putShort(2.toShort())
        buffer.putShort(16.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(size)
        repeat(sampleCount) { n ->
            val wave = sin(2.0 * PI * 440.0 * n / sampleRate) * 11000.0
            buffer.putShort(wave.toInt().toShort())
        }
        return buffer.array()
    }
}
