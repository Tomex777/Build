package com.tomex777.annie

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
            MediaPlayerScreen(item, PlayerMode.STREAMING, sourceAvailable = false, onBack = {}, immersive = false)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("media_player").assertIsDisplayed()
        compose.onNodeWithTag("player_title", useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodesWithText("STREAMING").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("player_source_unavailable", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("player_play_pause").assertIsNotEnabled()
        compose.onNodeWithTag("player_seek", useUnmergedTree = true).assertIsNotEnabled()
    }

    @Test fun streamingPlayerEnablesTransportAndPlaybackControlsWhenSourceExists() {
        compose.setContent {
            MediaPlayerScreen(item, PlayerMode.STREAMING, sourceAvailable = true, onBack = {}, immersive = false)
        }
        compose.onNodeWithTag("player_quality").assertIsEnabled()
        compose.onNodeWithTag("player_speed").assertIsEnabled()
        compose.onNodeWithTag("player_aspect").assertIsEnabled()
        compose.onNodeWithTag("player_rotate").assertIsEnabled()
        compose.onNodeWithTag("player_seek", useUnmergedTree = true).assertIsEnabled()
        compose.onNodeWithTag("player_play_pause").performClick()
        compose.onNodeWithContentDescription("Pause video").assertIsDisplayed()
    }

    @Test fun offlinePlayerDisablesStreamingOnlyControls() {
        compose.setContent {
            MediaPlayerScreen(item, PlayerMode.OFFLINE, sourceAvailable = true, onBack = {}, immersive = false)
        }
        assertTrue(compose.onAllNodesWithText("OFFLINE").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Offline video").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("No offline video file is available for this title.").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithTag("player_quality").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("player_speed").assertIsEnabled()
        compose.onNodeWithTag("player_aspect").assertIsEnabled()
        compose.onNodeWithTag("player_subtitles").assertIsEnabled()
        compose.onNodeWithTag("player_play_pause").assertIsEnabled()
    }
}
