package com.tomex777.annie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import android.content.pm.ActivityInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

class PlayerActivityTest {
    @get:Rule val compose = createAndroidComposeRule<AnniePlayerActivity>()

    @Test fun playerActivityLaunchesInLandscapeWithThePlayerSurface() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, compose.activity.requestedOrientation)
        compose.onNodeWithTag("media_player").assertIsDisplayed()
        compose.onNodeWithTag("player_source_unavailable", useUnmergedTree = true).assertIsDisplayed()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val fullScreenNotice = By.text("Got it")
        if (device.wait(Until.hasObject(fullScreenNotice), 1_500)) {
            device.findObject(fullScreenNotice)?.click()
            device.wait(Until.gone(fullScreenNotice), 1_500)
            compose.waitForIdle()
        }
        saveEmulatorScreenshot("annie-player-activity-landscape")
    }
}
