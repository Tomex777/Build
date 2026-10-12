package com.tomex777.annie

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnieImageViewerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun tappingChatImageOpensGenuineScreenWidthViewer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val payload = JSONObject().put("type", "image")
            .put("uri", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAALUlEQVR4nGN8vVOXgZaAiaamj1owasGoBaMWjFowasGoBaMWjFowasGoBVQEAJS1AhGDSM2vAAAAAElFTkSuQmCC")
            .put("caption", "Annie photo")
        val monitor = instrumentation.addMonitor(AnnieImageViewerActivity::class.java.name, null, false)
        var viewer: AnnieImageViewerActivity? = null
        try {
            compose.setContent { AnnieTheme { ScriptImageMessage(payload, "") } }
            compose.onNodeWithTag("script_image_message").assertIsDisplayed()
            compose.onNodeWithTag("script_image_message").performClick()
            viewer = instrumentation.waitForMonitorWithTimeout(monitor, 8_000) as? AnnieImageViewerActivity
            assertNotNull("Photo opened no full-screen Activity", viewer)
            compose.waitUntil(8_000) {
                var width = 0
                instrumentation.runOnMainSync { width = viewer!!.window.decorView.width }
                width >= 200
            }
            compose.waitUntil(10_000) { viewer!!.imageLoaded || viewer!!.imageLoadError != null }
            assertTrue("Fullscreen image did not decode: ${viewer!!.imageLoadError}", viewer!!.imageLoaded)
            val presented = java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync {
                viewer!!.window.decorView.postOnAnimation {
                    viewer!!.window.decorView.postOnAnimation { presented.countDown() }
                }
            }
            assertTrue("Decoded image did not reach a display frame", presented.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val expected = viewer!!.resources.displayMetrics.widthPixels
            var actual = 0
            instrumentation.runOnMainSync {
                actual = viewer!!.window.decorView.width
            }
            assertTrue("Image viewer must use the device width, not dialog width: $actual/$expected",
                actual >= expected * 0.9f)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Loading image…").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("script_image_fullscreen").assertIsDisplayed()
            saveEmulatorScreenshot("annie-image-fullscreen-activity")
        } finally {
            viewer?.finish()
            instrumentation.removeMonitor(monitor)
        }
    }
}
