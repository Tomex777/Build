package com.tomex777.annie

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnieVoiceTouchTargetsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun voicePlaybackAndSeekTargetsAreReachableOnPhone() {
        // Only a file-presence fixture; this test does not pretend to exercise audio decoding.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sound = File(context.cacheDir, "agent5-touch-fixture.m4a")
        sound.writeBytes(byteArrayOf(1, 2, 3, 4))
        try {
            compose.setContent {
                AnnieTheme { VoiceNoteBubble(sound.absolutePath, 1250L, fromUser = true) }
            }
            val density = compose.activity.resources.displayMetrics.density
            listOf("voice_play_pause", "voice_waveform").forEach { tag ->
                val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue("$tag must have a 48dp touch height: $bounds", bounds.height >= 48f * density - 1f)
                assertTrue("$tag must be at least 48dp wide: $bounds", bounds.width >= 48f * density - 1f)
            }
            saveEmulatorScreenshot("annie-agent5-voice-playback")
        } finally {
            sound.delete()
            File("${sound.absolutePath}.wave").delete()
        }
    }
}
