package com.nice.juicewrld

import android.graphics.Bitmap
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class FanSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) {
        rule.waitForIdle()
        // Capture the complete Android window, including ModalBottomSheet/dialog windows.
        // A Compose root image cannot capture a separate sheet owner and fails on API 26/35.
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("Android could not capture $name screenshot")
        val dir = File(rule.activity.filesDir, "screenshots")
        dir.mkdirs()
        File(dir, name + ".png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun fanHomeHasNoDiscoverOrSavedTabAndMoodsWork() {
        rule.onNodeWithText("JUICE WRLD").assertExists()
        rule.onNodeWithText("THE 999 FAN SPACE").assertExists()
        rule.onNodeWithText("Discover").assertDoesNotExist()
        rule.onNodeWithText("Saved").assertDoesNotExist()
        rule.onNodeWithText("THE MUSIC").assertDoesNotExist()
        rule.onNodeWithText("Every song, every mood").assertDoesNotExist()
        screenshot("home")
        rule.onNodeWithTag("moods").performScrollToNode(hasText("Love"))
        rule.onNodeWithText("Love").performClick()
        rule.onNodeWithText("Love songs").assertExists()
        screenshot("love")
        rule.onNodeWithTag("moods").performScrollToNode(hasText("Sad"))
        rule.onNodeWithText("Sad").performClick()
        rule.onNodeWithText("Sad songs").assertExists()
        screenshot("sad")
    }

    @Test fun favoritesAreAccessibleWithoutAnotherNavigationTab() {
        rule.onNodeWithText("Favorites").performClick()
        rule.onNodeWithTag("songs").performScrollToNode(hasText("Your favorites"))
        rule.onNodeWithText("Your favorites").assertExists()
        rule.onNodeWithText("No favorites yet").assertExists()
        screenshot("favorites")
        rule.onNodeWithTag("songs").performScrollToNode(hasText("Pick a feeling"))
        rule.onNodeWithText("Favorites").performClick()
        rule.onNodeWithTag("songs").performScrollToNode(hasText("All songs"))
    }

    @Test fun songOpensOfficialPlaybackWithoutPretendingToDownloadUnlicensedAudio() {
        rule.onNodeWithTag("songs").performScrollToNode(hasText("Lucid Dreams"))
        rule.onNodeWithText("Lucid Dreams").performClick()
        rule.onNodeWithText("Import").assertDoesNotExist()
        rule.onNodeWithText("Listen on YouTube Music").assertExists()
        rule.onNodeWithText("Download").assertDoesNotExist()
        screenshot("song-sheet")
    }
}
