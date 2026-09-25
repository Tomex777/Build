package com.tomex777.annie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import android.content.pm.ActivityInfo

class PlayerActivityTest {
    @get:Rule val compose = createAndroidComposeRule<AnniePlayerActivity>()

    @Test fun playerActivityLaunchesInLandscapeWithThePlayerSurface() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, compose.activity.requestedOrientation)
        compose.onNodeWithTag("media_player").assertIsDisplayed()
        compose.onNodeWithTag("player_source_unavailable", useUnmergedTree = true).assertIsDisplayed()
    }
}
