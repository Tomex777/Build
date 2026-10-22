package com.nice.juicewrld

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import java.io.File

class FanSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(rule.activity.getExternalFilesDir(null), "screenshots")
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
        screenshot("home")
        rule.onAllNodesWithText("Love")[0].performClick()
        rule.onNodeWithText("Love songs").assertExists()
        screenshot("love")
        rule.onAllNodesWithText("Sad")[0].performClick()
        rule.onNodeWithText("Sad songs").assertExists()
        screenshot("sad")
    }

    @Test fun songOpensAndOffersGenuineFileImportWithoutFakeStreaming() {
        rule.onNodeWithText("Lucid Dreams").performScrollTo().performClick()
        rule.onNodeWithText("Lucid Dreams").assertExists()
        rule.onNodeWithText("Add audio file").assertExists()
        rule.onNodeWithText("Listen on YouTube Music").assertExists()
        screenshot("song-sheet")
    }
}
