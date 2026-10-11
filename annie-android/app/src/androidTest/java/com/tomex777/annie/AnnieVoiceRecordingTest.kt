package com.tomex777.annie

import android.Manifest
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnieVoiceRecordingTest {
    @Test fun lockedRecordingPausesMicrophoneAndSendsOneAacFile() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}"
        ).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        val sent = AtomicReference<Pair<String, Long>?>(null)
        lateinit var controller: VoiceNoteController
        var target: File? = null
        var initialized = false
        var startedAt = 0L
        var wallDuration = 0L
        try {
            instrumentation.runOnMainSync {
                controller = VoiceNoteController(context) { path, duration -> sent.set(path to duration) }
                initialized = true
                assertTrue("Emulator microphone failed to start", controller.begin())
                startedAt = android.os.SystemClock.elapsedRealtime()
                controller.locked = true
            }
            Thread.sleep(1_000)
            instrumentation.runOnMainSync { controller.tick(); controller.onHostStopped() }
            assertTrue("Locked voice recorder did not pause", controller.paused)
            instrumentation.runOnMainSync { controller.onHostStopped() }
            assertTrue("A repeated stop event resumed the microphone", controller.paused)
            val pausedDuration = controller.elapsedMs
            val peakCount = controller.waveformPeaks.size
            Thread.sleep(800)
            instrumentation.runOnMainSync { controller.tick() }
            assertEquals("Paused time was counted as audio", pausedDuration, controller.elapsedMs)
            assertEquals("Pause generated fake waveform samples", peakCount, controller.waveformPeaks.size)
            instrumentation.runOnMainSync { controller.togglePause() }
            assertTrue("Recorder stayed paused", !controller.paused)
            Thread.sleep(1_000)
            instrumentation.runOnMainSync {
                controller.tick()
                controller.finish()
                wallDuration = android.os.SystemClock.elapsedRealtime() - startedAt
            }
            val note = sent.get()
            assertNotNull("Recorder did not send a real voice note", note)
            target = File(note!!.first)
            assertTrue(target!!.length() > 0L)
            assertTrue("Recorded duration lost active audio", note.second >= 1_500L)
            assertTrue("Recorded duration includes paused time: ${note.second}/$wallDuration",
                note.second <= wallDuration - 700L)
            assertEquals(48, AnnieVoiceWaveform.load(note.first).size)
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(note.first)
                assertTrue("No audio track saved", extractor.trackCount > 0)
                val format = extractor.getTrackFormat(0)
                assertEquals("audio/mp4a-latm", format.getString(MediaFormat.KEY_MIME))
                val audioMs = format.getLong(MediaFormat.KEY_DURATION) / 1_000
                assertTrue("Saved AAC duration differs from active recording: $audioMs/${note.second}",
                    kotlin.math.abs(audioMs - note.second) < 500L)
                extractor.selectTrack(0)
                assertTrue("Saved AAC contains no packets", extractor.sampleTime >= 0L)
            } finally {
                extractor.release()
            }
        } finally {
            if (initialized) instrumentation.runOnMainSync { controller.cancel() }
            target?.let { File("${it.absolutePath}.wave").delete(); it.delete() }
        }
    }

    @Test fun backgroundInterruptionCancelsUnlockedDraftWithoutSendingOrLeavingAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}"
        ).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        val directory = File(context.filesDir, AnnieVoiceRecorder.VOICE_DIR)
        val originalFiles = directory.list()?.toSet().orEmpty()
        var sends = 0
        lateinit var controller: VoiceNoteController
        instrumentation.runOnMainSync {
            controller = VoiceNoteController(context) { _, _ -> sends++ }
        }
        try {
            repeat(2) {
                instrumentation.runOnMainSync { assertTrue(controller.begin()) }
                Thread.sleep(800)
                instrumentation.runOnMainSync {
                    controller.onHostStopped()
                    assertTrue("Background interruption kept recording", !controller.recording)
                    controller.finish() // A delayed gesture release must not send a cancelled draft.
                    assertEquals(0, sends)
                }
                assertEquals("Cancelled recording left an audio file", originalFiles, directory.list()?.toSet().orEmpty())
            }
        } finally {
            instrumentation.runOnMainSync { controller.cancel() }
        }
    }
}
