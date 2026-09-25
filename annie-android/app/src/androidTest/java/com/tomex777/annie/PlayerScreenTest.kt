package com.tomex777.annie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerScreenTest {
    @get:Rule val compose = createComposeRule()

    private val item = CatalogItem(
        id = 77, mediaType = "ANIME", title = "Blue Abroad Days", image = "",
        year = 2024, status = "RELEASING", episodes = 37, chapters = null,
    )

    @Test fun streamingPlayerShowsLandscapeControlsAndUnavailableSourceState() {
        compose.setContent {
            MediaPlayerScreen(item, PlayerMode.STREAMING, sourceAvailable = false, onBack = {})
        }
        compose.onNodeWithTag("media_player").assertIsDisplayed()
        compose.onNodeWithText("Blue Abroad Days").assertIsDisplayed()
        compose.onNodeWithTag("player_mode").assertIsDisplayed()
        compose.onNodeWithText("STREAMING").assertIsDisplayed()
        compose.onNodeWithText("No streaming source is connected for this title.").assertIsDisplayed()
        compose.onNodeWithTag("player_play_pause").assertIsNotEnabled()
        compose.onNodeWithTag("player_seek").assertIsNotEnabled()
    }

    @Test fun streamingPlayerEnablesTransportAndPlaybackControlsWhenSourceExists() {
        compose.setContent {
            MediaPlayerScreen(item, PlayerMode.STREAMING, sourceAvailable = true, onBack = {})
        }
        compose.onNodeWithTag("player_cast").assertIsEnabled()
        compose.onNodeWithTag("player_quality").assertIsEnabled()
        compose.onNodeWithTag("player_seek").assertIsEnabled()
        compose.onNodeWithTag("player_play_pause").performClick()
        compose.onNodeWithText("Ⅱ").assertIsDisplayed()
    }

    @Test fun offlinePlayerDisablesStreamingOnlyControls() {
        compose.setContent {
            MediaPlayerScreen(item, PlayerMode.OFFLINE, sourceAvailable = true, onBack = {})
        }
        compose.onNodeWithText("OFFLINE").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("No offline video file is available for this title.").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("player_cast").assertIsNotEnabled()
        compose.onNodeWithTag("player_quality").assertIsNotEnabled()
        compose.onNodeWithTag("player_subtitles").assertIsEnabled()
        compose.onNodeWithTag("player_play_pause").assertIsEnabled()
    }
}
