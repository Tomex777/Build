package com.tomex777.annie

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MusicPlaybackServiceTest {
    @Test
    fun playbackLivesInForegroundMediaSessionAndRespondsToPauseResume() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audioFile = File(context.cacheDir, "music-service-test.wav")
        writeTestTone(audioFile)
        val connected = CountDownLatch(1)
        var service: MusicPlaybackService? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as? MusicPlaybackService.LocalBinder)?.service()
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        try {
            MusicPlaybackService.start(context, MusicPlaybackService.ACTION_PLAY) {
                putExtra(MusicPlaybackService.EXTRA_STREAM, Uri.fromFile(audioFile).toString())
                putExtra(MusicPlaybackService.EXTRA_TITLE, "Service test tone")
                putExtra(MusicPlaybackService.EXTRA_ARTIST, "Annie test")
            }
            assertTrue(context.bindService(Intent(context, MusicPlaybackService::class.java), connection,
                Context.BIND_AUTO_CREATE))
            assertTrue("Playback service did not bind", connected.await(5, TimeUnit.SECONDS))
            assertTrue("Playback did not prepare and start", awaitState(service!!) { it.prepared && it.playing })
            assertEquals("Service test tone", service!!.currentSnapshot().title)
            assertTrue("MediaSession did not publish playing", awaitSessionState(service!!,
                android.media.session.PlaybackState.STATE_PLAYING))

            MusicPlaybackService.start(context, MusicPlaybackService.ACTION_PAUSE)
            assertTrue("Pause command was not applied", awaitState(service!!) { it.prepared && !it.playing })
            assertTrue("MediaSession did not publish paused", awaitSessionState(service!!,
                android.media.session.PlaybackState.STATE_PAUSED))

            MusicPlaybackService.start(context, MusicPlaybackService.ACTION_TOGGLE)
            assertTrue("Resume command was not applied", awaitState(service!!) { it.playing })
        } finally {
            MusicPlaybackService.start(context, MusicPlaybackService.ACTION_STOP)
            runCatching { context.unbindService(connection) }
            audioFile.delete()
        }
    }

    private fun awaitState(service: MusicPlaybackService, predicate: (MusicPlaybackService.Snapshot) -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 8_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate(service.currentSnapshot())) return true
            SystemClock.sleep(100)
        }
        return false
    }

    private fun awaitSessionState(service: MusicPlaybackService, state: Int): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 3_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (service.sessionPlaybackStateForTest() == state) return true
            SystemClock.sleep(50)
        }
        return false
    }

    private fun writeTestTone(file: File) {
        val sampleRate = 44_100
        val seconds = 8
        val sampleCount = sampleRate * seconds
        val dataBytes = sampleCount * 2
        FileOutputStream(file).use { output ->
            output.write("RIFF".toByteArray(Charsets.US_ASCII))
            output.write(le32(36 + dataBytes))
            output.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            output.write(le32(16))
            output.write(le16(1))
            output.write(le16(1))
            output.write(le32(sampleRate))
            output.write(le32(sampleRate * 2))
            output.write(le16(2))
            output.write(le16(16))
            output.write("data".toByteArray(Charsets.US_ASCII))
            output.write(le32(dataBytes))
            for (i in 0 until sampleCount) {
                val sample = (12_000 * kotlin.math.sin(2.0 * Math.PI * 440.0 * i / sampleRate)).toInt()
                output.write(le16(sample))
            }
        }
    }

    private fun le16(value: Int) = byteArrayOf(value.toByte(), (value ushr 8).toByte())
    private fun le32(value: Int) = byteArrayOf(
        value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte()
    )
}
