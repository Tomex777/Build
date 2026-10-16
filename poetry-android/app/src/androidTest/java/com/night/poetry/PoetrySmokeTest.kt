package com.night.poetry

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual Android emulator screenshots for ABR visual verification. */
@RunWith(AndroidJUnit4::class)
class PoetrySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(screen: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = checkNotNull(compose.activity.getExternalFilesDir(null))
        val file = File(dir, "poetry-${screen}.png")
        file.outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Failed to capture ${screen}"
            }
        }
    }

    @Test fun writeThenDesign() {
        capture("home")
        compose.onNodeWithText("Write a poem").performClick()
        compose.onNodeWithTag("poem-title-input").performTextInput("Hardship")
        compose.onNodeWithTag("poem-body-input").performTextInput(
            "Some days are heavy, but the dawn still comes."
        )
        capture("writer")
        compose.onNodeWithText("Design a card", substring = true).performClick()
        compose.onNodeWithTag("poetry-card-stage").assertExists()
        compose.onNodeWithText("Export").assertExists()
        capture("studio")

        // Ensure search categories represent actual vector resources.
        compose.onNodeWithText("Elements").performClick()
        capture("elements")
        compose.onNodeWithText("Stars").performClick()
        compose.onNodeWithText("Sparkle").assertExists()
        compose.onNodeWithText("Love").assertDoesNotExist()
        compose.onNodeWithText("Sparkle").performClick()
        compose.onNodeWithText("Opacity").assertExists()
        capture("element-selected")
    }
}
