package com.tomex777.annie

import android.net.Uri
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalVideoPlaybackTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var playerScenario: ActivityScenario<AnniePlayerActivity>? = null

    @After fun closePlayerActivity() {
        playerScenario?.close()
        playerScenario = null
    }

    @Test fun localLibraryVideoDecodesAdvancesAndPauses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resumePrefs = context.getSharedPreferences("annie_video_resume", 0)
        resumePrefs.edit().remove("9001:Local playback test").commit()
        val fixture = File(context.cacheDir, "annie-playback-test.mp4")
        val connection = URL(
            "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
        ).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 90_000
        connection.instanceFollowRedirects = true
        try {
            assertTrue("Test video request failed: HTTP ${connection.responseCode}", connection.responseCode == 200)
            connection.inputStream.use { input ->
                fixture.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        assertTrue("Downloaded test video is empty", fixture.length() > 100_000)

        val item = CatalogItem(
            id = 9001, mediaType = "ANIME", title = "Local playback test",
            image = "", year = 2024, status = "COMPLETE", episodes = 1, chapters = null,
        )
        val playerIntent = Intent(context, AnniePlayerActivity::class.java).apply {
            putExtra(AnniePlayerActivity.EXTRA_ID, item.id)
            putExtra(AnniePlayerActivity.EXTRA_MEDIA_TYPE, item.mediaType)
            putExtra(AnniePlayerActivity.EXTRA_TITLE, item.title)
            putExtra(AnniePlayerActivity.EXTRA_MODE, PlayerMode.OFFLINE.name)
            putExtra(AnniePlayerActivity.EXTRA_MEDIA_URI, Uri.fromFile(fixture).toString())
        }
        playerScenario = ActivityScenario.launch(playerIntent)

        compose.waitForIdle()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val fullScreenNotice = By.text("Got it")
        if (device.wait(Until.hasObject(fullScreenNotice), 1_500)) {
            device.findObject(fullScreenNotice)?.click()
            device.wait(Until.gone(fullScreenNotice), 1_500)
            compose.waitForIdle()
        }

        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("—:—").fetchSemanticsNodes().isEmpty()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("00:00").fetchSemanticsNodes().isEmpty()
        }
        compose.waitUntil(60_000) {
            val activelyPlaying = compose.onAllNodesWithContentDescription("Pause video").fetchSemanticsNodes().isNotEmpty()
            if (!activelyPlaying && compose.onAllNodesWithTag("player_title", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                compose.onNodeWithTag("media_player").performTouchInput { click(center) }
            }
            activelyPlaying
        }
        val screenshotFile = saveEmulatorScreenshot("annie-vlc-visible-frame")
        saveEmulatorScreenshot("annie-full-player")
        val screenshot = checkNotNull(context.contentResolver.openInputStream(screenshotFile)?.use(BitmapFactory::decodeStream)) {
            "Could not reopen VLC playback screenshot"
        }
        val left = screenshot.width / 4
        val right = screenshot.width * 3 / 4
        val top = screenshot.height / 4
        val bottom = screenshot.height * 3 / 4
        var sampledPixels = 0
        var visibleVideoPixels = 0
        for (y in top until bottom step 8) {
            for (x in left until right step 8) {
                val pixel = screenshot.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                sampledPixels++
                if (maxOf(red, green, blue) > 60 && maxOf(red, green, blue) - minOf(red, green, blue) > 12) {
                    visibleVideoPixels++
                }
            }
        }
        screenshot.recycle()
        compose.onNodeWithTag("player_title", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("player_seek", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("player_seek", useUnmergedTree = true).assertIsEnabled()
        compose.waitUntil(6_000) {
            compose.onAllNodesWithTag("player_title", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        saveEmulatorScreenshot("annie-full-player-controls-hidden")
        compose.onNodeWithTag("media_player").performTouchInput { click(center) }
        compose.waitUntil(2_000) {
            compose.onAllNodesWithTag("player_title", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        saveEmulatorScreenshot("annie-player-controls-restored-on-tap")
        compose.onNodeWithTag("player_lock").performClick()
        compose.onNodeWithTag("player_unlock").assertExists().performClick()
        compose.onNodeWithTag("player_seek", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("player_audio").performClick()
        compose.onNodeWithText("Default audio").assertExists()
        saveEmulatorScreenshot("annie-player-audio-options")
        compose.onNodeWithTag("player_subtitles").performClick()
        compose.onNodeWithText("Off").assertExists()
        saveEmulatorScreenshot("annie-player-subtitle-options")
        compose.onNodeWithTag("player_play_pause").performClick()
        compose.waitUntil(2_500) {
            compose.onAllNodesWithContentDescription("Play video").fetchSemanticsNodes().isNotEmpty()
        }
        saveEmulatorScreenshot("annie-full-player-paused")
        assertTrue("Player exposed implementation-only offline labels",
            compose.onAllNodesWithText("Offline video").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithText("OFFLINE").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("player_title", useUnmergedTree = true).assertExists()
        assertTrue(
            "VLC advanced but the captured video surface stayed black ($visibleVideoPixels/$sampledPixels colored samples)",
            visibleVideoPixels > sampledPixels / 100,
        )

        playerScenario?.close()
        playerScenario = null
        val savedPosition = resumePrefs.getLong("9001:Local playback test", 0L)
        assertTrue("Closing the player did not persist its resume position", savedPosition > 0L)
        playerScenario = ActivityScenario.launch(playerIntent)
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription("Pause video").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("00:00").fetchSemanticsNodes().isEmpty()
        }
    }
}
