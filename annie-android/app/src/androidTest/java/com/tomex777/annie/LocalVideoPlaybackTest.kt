package com.tomex777.annie

import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalVideoPlaybackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun localLibraryVideoDecodesAdvancesAndPauses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.cacheDir, "annie-playback-test.mp4")
        val connection = URL(
            "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
        ).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        try {
            assertTrue("Test video request failed: HTTP ${connection.responseCode}", connection.responseCode == 200)
            connection.inputStream.use { input -> fixture.outputStream().use(input::copyTo) }
        } finally {
            connection.disconnect()
        }
        assertTrue("Downloaded test video is empty", fixture.length() > 100_000)

        val item = CatalogItem(
            id = 9001, mediaType = "ANIME", title = "Local playback test",
            image = "", year = 2024, status = "COMPLETE", episodes = 1, chapters = null,
        )
        compose.setContent {
            AnnieTheme {
                MediaPlayerScreen(
                    item = item,
                    mode = PlayerMode.OFFLINE,
                    sourceAvailable = true,
                    mediaUri = Uri.fromFile(fixture),
                    onBack = {},
                    immersive = false,
                )
            }
        }

        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("—:—").fetchSemanticsNodes().isEmpty()
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Ⅱ").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("player_play_pause").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("▶").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("Player never exposed its offline mode",
            compose.onAllNodesWithText("OFFLINE").fetchSemanticsNodes().isNotEmpty())
    }
}
