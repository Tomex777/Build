package com.tomex777.annie

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VoiceHoldButton(controller, onNeedPermission = { error("Microphone permission was granted") })
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
}
