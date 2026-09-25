package com.tomex777.annie

import android.net.Uri
import android.graphics.BitmapFactory
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
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText("Ⅱ").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("00:00").fetchSemanticsNodes().isEmpty()
        }
        val screenshotUri = saveEmulatorScreenshot("annie-vlc-visible-frame")
        val screenshot = checkNotNull(context.contentResolver.openInputStream(screenshotUri)?.use(BitmapFactory::decodeStream)) {
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
        compose.onNodeWithTag("media_player").performClick()
        compose.onNodeWithTag("player_play_pause").performClick()
        compose.waitUntil(2_500) {
            compose.onAllNodesWithText("▶").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("Player never exposed its offline mode",
            compose.onAllNodesWithText("OFFLINE").fetchSemanticsNodes().isNotEmpty())
        assertTrue(
            "VLC advanced but the captured video surface stayed black ($visibleVideoPixels/$sampledPixels colored samples)",
            visibleVideoPixels > sampledPixels / 100,
        )
    }
}
