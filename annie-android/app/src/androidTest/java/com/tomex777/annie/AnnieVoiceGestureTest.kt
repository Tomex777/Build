package com.tomex777.annie

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnieVoiceGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun showMicrophone(onSent: (String, Long) -> Unit): VoiceNoteController {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}"
        ).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        lateinit var controller: VoiceNoteController
        compose.setContent {
            AnnieTheme {
                controller = rememberVoiceNoteController(onSent)
                Box(Modifier.fillMaxSize().testTag("voice_gesture_surface"), contentAlignment = Alignment.Center) {
                    if (controller.locked) VoiceRecordingBar(controller)
                    else VoiceHoldButton(controller, onNeedPermission = { error("Microphone permission was granted") })
                }
            }
        }
        compose.runOnIdle { assertTrue(controller.hasPermission()) }
        return controller
    }

    @Test fun cancelledFingerHoldDoesNotSendOrLeaveTheMicrophoneRunning() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, AnnieVoiceRecorder.VOICE_DIR)
        val originalFiles = directory.list()?.toSet().orEmpty()
        var sends = 0
        val controller = showMicrophone { _, _ -> sends++ }
        try {
            compose.onNodeWithTag("composer_voice_hold").performTouchInput { down(center) }
            compose.runOnIdle { assertTrue("Finger hold did not start microphone", controller.recording) }
            Thread.sleep(900)
            compose.onNodeWithTag("composer_voice_hold").performTouchInput { cancel() }
            compose.runOnIdle {
                assertTrue("Cancelled pointer stream kept the microphone running", !controller.recording)
                assertEquals("Cancelled finger hold sent audio", 0, sends)
                controller.finish()
                assertEquals(0, sends)
            }
            assertEquals(originalFiles, directory.list()?.toSet().orEmpty())
        } finally {
            compose.runOnIdle { controller.cancel() }
        }
    }

    @Test fun fingerHoldAndReleaseSendsExactlyOneRealAudioFile() {
        val sent = AtomicReference<Pair<String, Long>?>(null)
        var sends = 0
        val controller = showMicrophone { path, duration -> sends++; sent.set(path to duration) }
        try {
            compose.onNodeWithTag("composer_voice_hold").performTouchInput { down(center) }
            compose.runOnIdle { assertTrue(controller.recording) }
            Thread.sleep(1_100)
            compose.onNodeWithTag("composer_voice_hold").performTouchInput { up() }
            compose.runOnIdle {
                assertEquals(1, sends)
                assertTrue(!controller.recording)
                controller.finish()
                assertEquals("Delayed release sent twice", 1, sends)
            }
            assertNotNull(sent.get())
            val (path, duration) = sent.get()!!
            assertTrue(File(path).length() > 0)
            assertTrue(duration >= AnnieVoiceRecorder.MIN_DURATION_MS)
            assertEquals(48, AnnieVoiceWaveform.load(path).size)
        } finally {
            compose.runOnIdle { controller.cancel() }
            sent.get()?.first?.let { File("$it.wave").delete(); File(it).delete() }
        }
    }

    @Test fun slidingLeftCancelsTheActualRecordingWithoutSending() {
        var sends = 0
        val controller = showMicrophone { _, _ -> sends++ }
        val density = compose.activity.resources.displayMetrics.density
        try {
            compose.onNodeWithTag("composer_voice_hold").performTouchInput { down(center) }
            Thread.sleep(900)
            compose.onNodeWithTag("composer_voice_hold").performTouchInput {
                moveTo(center - Offset(150f * density, 0f))
                up()
            }
            compose.runOnIdle {
                assertTrue(!controller.recording)
                assertEquals(0, sends)
            }
        } finally { compose.runOnIdle { controller.cancel() } }
    }

    @Test fun slidingUpLocksAcrossButtonRemovalAndPauseResumeSendsOneAudioFile() {
        val sent = AtomicReference<Pair<String, Long>?>(null)
        var sends = 0
        val controller = showMicrophone { path, duration -> sends++; sent.set(path to duration) }
        val density = compose.activity.resources.displayMetrics.density
        try {
            compose.onNodeWithTag("composer_voice_hold").performTouchInput { down(center) }
            Thread.sleep(900)
            compose.onNodeWithTag("composer_voice_hold").performTouchInput {
                moveTo(center - Offset(0f, 100f * density))
            }
            // The chat replaces the hold button with locked recording controls.
            compose.onNodeWithTag("voice_gesture_surface").performTouchInput { up() }
            compose.runOnIdle {
                assertTrue("Removing the locked microphone button cancelled the draft", controller.recording)
                assertTrue(controller.locked)
                assertEquals(0, sends)
            }
            val density = compose.activity.resources.displayMetrics.density
            listOf("voice_delete", "voice_pause_resume", "voice_send").forEach { tag ->
                val target = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertTrue("$tag is too narrow for a reliable tap", target.width >= 48f * density - 1f)
                assertTrue("$tag is too short for a reliable tap", target.height >= 48f * density - 1f)
            }
            saveEmulatorScreenshot("annie-agent5-voice-locked-controls")
            compose.onNodeWithTag("voice_pause_resume").performClick()
            var pausedDuration = 0L
            compose.runOnIdle { assertTrue(controller.paused); pausedDuration = controller.elapsedMs }
            Thread.sleep(350)
            compose.runOnIdle { assertEquals(pausedDuration, controller.elapsedMs) }
            compose.onNodeWithTag("voice_pause_resume").performClick()
            compose.runOnIdle { assertTrue(!controller.paused) }
            Thread.sleep(400)
            compose.onNodeWithTag("voice_send").performClick()
            compose.runOnIdle { assertEquals(1, sends); assertTrue(!controller.recording) }
            assertTrue(File(sent.get()!!.first).length() > 0)
            assertEquals(48, AnnieVoiceWaveform.load(sent.get()!!.first).size)
        } finally {
            compose.runOnIdle { controller.cancel() }
            sent.get()?.first?.let { File("$it.wave").delete(); File(it).delete() }
        }
    }

    @Test fun switchingChatsDisposesLockedRecordingWithoutSendingOrLeavingAudio() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${context.packageName} ${Manifest.permission.RECORD_AUDIO}"
        ).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
        val dir = File(context.filesDir, AnnieVoiceRecorder.VOICE_DIR)
        val originalFiles = dir.list()?.toSet().orEmpty()
        val key = mutableStateOf("chat-first")
        var sends = 0
        lateinit var current: VoiceNoteController
        compose.setContent {
            AnnieTheme {
                current = rememberVoiceNoteController({ _, _ -> sends++ }, sessionKey = key.value)
                Box(Modifier.fillMaxSize()) {
                    VoiceHoldButton(current, onNeedPermission = { error("Microphone permission was granted") })
                }
            }
        }
        lateinit var previous: VoiceNoteController
        compose.runOnIdle {
            previous = current
            assertTrue(previous.begin())
            previous.locked = true
            key.value = "chat-second"
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("Previous chat microphone was not released", !previous.recording)
            assertTrue("New chat inherited an active microphone", !current.recording)
            assertTrue("Chat switch must replace the active controller", current !== previous)
            assertEquals("A cancelled recording leaked into a different chat", 0, sends)
        }
        assertEquals("Switching chats left a partial audio file", originalFiles, dir.list()?.toSet().orEmpty())
    }

}
