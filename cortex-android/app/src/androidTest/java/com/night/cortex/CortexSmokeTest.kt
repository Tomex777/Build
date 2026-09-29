package com.night.cortex

import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class CortexSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensFamiliarServerPanelWithoutNetworkTab() {
        composeRule.onNodeWithText("Cortex").assertIsDisplayed()
        composeRule.onNodeWithText("Console").assertIsDisplayed()
        composeRule.onNodeWithText("Pairing").assertIsDisplayed()
        composeRule.onNodeWithText("Files").assertIsDisplayed()
        composeRule.onNodeWithText("Backups").assertIsDisplayed()
        composeRule.onNodeWithText("Startup").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Activity").assertIsDisplayed()
        composeRule.onNodeWithText("Connect Cortex Agent").assertIsDisplayed()

        composeRule.onNodeWithTag("server-tab-console").assertIsSelected()
        listOf("pairing", "files", "backups", "startup", "settings", "activity").forEach { tab ->
            composeRule.onNodeWithTag("server-tab-$tab").performClick().assertIsSelected()
            composeRule.onNodeWithText("Connect Cortex Agent").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("server-tab-console").performClick().assertIsSelected()
        saveHomeVisualEvidence()
    }

    @Test
    fun connectionSetupUsesDarkSurfacesAndReadableFields() {
        composeRule.onNodeWithTag("open-cortex-connection").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("connection-sheet-root").assertIsDisplayed()
        composeRule.onNodeWithText("HTTPS Agent URL").assertIsDisplayed()
        composeRule.onNodeWithText("Agent token").assertIsDisplayed()
        composeRule.onNodeWithText("Save connection").assertIsDisplayed()

        val node = composeRule.onNodeWithTag("connection-sheet-root", useUnmergedTree = true)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
                "Unable to capture Cortex connection setup on API ${Build.VERSION.SDK_INT}"
            }
        } else {
            node.captureToImage().asAndroidBitmap()
        }
        val screenshotPixel = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // The API 26 UI Automation capture includes the activity behind
            // the modal sheet. Sample inside the lower sheet content rather
            // than the uncovered page above it.
            bitmap.getPixel(bitmap.width / 2, (bitmap.height * 3 / 4).coerceAtMost(bitmap.height - 1))
        } else {
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        }
        val file = File(instrumentation.targetContext.cacheDir, "cortex-connection-setup-emulator.png")
        FileOutputStream(file).use { stream ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to encode Cortex connection setup screenshot"
            }
        }
        check(file.length() > 0L) { "Cortex connection setup screenshot is empty" }
        check(
            android.graphics.Color.red(screenshotPixel) < 220 &&
                android.graphics.Color.green(screenshotPixel) < 220 &&
                android.graphics.Color.blue(screenshotPixel) < 220
        ) { "Connection setup rendered a light fallback surface: #%06X".format(screenshotPixel and 0x00FFFFFF) }
    }

    private fun saveHomeVisualEvidence() {
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot(useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
        val file = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "cortex-home-compose.png",
        )
        FileOutputStream(file).use { stream ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to encode Cortex home visual evidence"
            }
        }
        check(file.length() > 0L) { "Cortex home visual evidence is empty" }
    }
}
