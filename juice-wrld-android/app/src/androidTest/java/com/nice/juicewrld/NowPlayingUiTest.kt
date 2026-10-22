package com.nice.juicewrld

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Device-rendered Now Playing screenshot and touch-control smoke test. */
class NowPlayingUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun fullScreenPlayerShowsRealControls() {
        var nextPressed = false
        var closed = false
        val track = requireNotNull(FanSongs.find("lucid-dreams"))
        rule.setContent {
            NowPlayingScreen(
                song = track,
                state = FanPlayerState(
                    activeSongId = track.id,
                    isPlaying = true,
                    durationMs = 250_000,
                    positionMs = 75_000
                ),
                onClose = { closed = true },
                onTogglePlay = {},
                onPrevious = {},
                onNext = { nextPressed = true },
                onSeek = {},
                onFavorite = {},
                onShuffle = {},
                onRepeat = {}
            )
        }
        rule.onNodeWithText("NOW PLAYING").assertExists()
        rule.onNodeWithText("Lucid Dreams").assertExists()
        rule.onNodeWithContentDescription("Pause").assertExists()
        rule.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.takeScreenshot()
            ?: error("Could not capture Now Playing")
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "screenshots"
        ).apply { mkdirs() }
        File(directory, "now-playing.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        rule.onNodeWithContentDescription("Next song").performClick()
        assertTrue("Next song control did not work", nextPressed)
        rule.onNodeWithContentDescription("Back to songs").performClick()
        assertTrue("Close control did not work", closed)
    }
}
