package com.night.cortex

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class CortexReleaseVisualTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun capturesRenderedReleaseHome() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cortex").assertIsDisplayed()
        composeRule.onNodeWithText("Console").assertIsDisplayed()
        composeRule.onNodeWithText("Connect Cortex Agent").assertIsDisplayed()

        val bitmap = composeRule.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        check(
            android.graphics.Color.red(pixel) < 220 &&
                android.graphics.Color.green(pixel) < 220 &&
                android.graphics.Color.blue(pixel) < 220 &&
                android.graphics.Color.alpha(pixel) > 0
        ) { "Release home did not render a dark Cortex surface: #%06X".format(pixel and 0x00FFFFFF) }

        val testContext = InstrumentationRegistry.getInstrumentation().context
        val file = File(testContext.cacheDir, "cortex-release-home.png")
        FileOutputStream(file).use { stream ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)) {
                "Unable to encode release home screenshot"
            }
        }
        check(file.length() > 0L) { "Release home screenshot is empty" }
    }
}
