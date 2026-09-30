package app.nami.android.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.nami.android.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class NamiReleaseLaunchUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentationContext by lazy {
        InstrumentationRegistry.getInstrumentation().context
    }

    @Test
    fun releaseCandidateRendersProductionHome() {
        waitForText("Library")
        waitForText("Browse")
        waitForText("More")
        composeRule.waitForIdle()

        val bitmap = composeRule.onRoot(useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(
            pixels,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height,
        )
        assertTrue(
            "Release visual proof must contain rendered product pixels",
            pixels.any { pixel -> pixel and 0x00FFFFFF != 0 },
        )

        val destination = File(instrumentationContext.filesDir, "nami-release-launch-compose.png")
        FileOutputStream(destination).use { output ->
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
        assertTrue(destination.isFile && destination.length() > 0L)
    }

    private fun waitForText(text: String, timeoutMillis: Long = 30_000L) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }
}
