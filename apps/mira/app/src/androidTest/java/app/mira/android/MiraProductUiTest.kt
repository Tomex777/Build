package app.mira.android

import android.graphics.BitmapFactory
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class MiraProductUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun navigationAndMiraIdentityRenderRealContent() {
        compose.onNodeWithText("Your library is empty").assertIsDisplayed()
        capture("library")
        compose.onNodeWithText("Browse").performClick()
        compose.onNodeWithText("Browse").assertIsDisplayed()
        capture("browse")
        compose.onNodeWithText("More").performClick()
        capture("more")
        compose.onNodeWithText("Sources & extensions").performClick()
        compose.onNodeWithText("Sources").assertIsDisplayed()
        capture("sources")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Downloads").performClick()
        capture("downloads")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Incognito").assertIsDisplayed()
        capture("settings")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("About Mira").performClick()
        compose.onNodeWithText("Your movie and TV library").assertIsDisplayed()
        capture("about")
        compose.onAllNodesWithText("Nami", substring = true).assertCountEquals(0)
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "visual-evidence").apply { mkdirs() }
        val output = File(directory, "mira-$name.png")
        assertTrue(UiDevice.getInstance(instrumentation).takeScreenshot(output))
        val bitmap = BitmapFactory.decodeFile(output.absolutePath)
        val colors = mutableSetOf<Int>()
        for (y in 0 until bitmap.height step 12) for (x in 0 until bitmap.width step 12) colors += bitmap.getPixel(x, y)
        assertTrue("Screenshot lacks rendered product content", colors.size > 12)
        bitmap.recycle()
    }
}
