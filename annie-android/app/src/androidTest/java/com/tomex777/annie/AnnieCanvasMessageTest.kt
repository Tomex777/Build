package com.tomex777.annie

import android.webkit.WebView
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnieCanvasMessageTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun interactiveCanvasRunsOfflineAndRetainsItsWebViewAcrossFullscreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val payload = JSONObject()
            .put("type", "canvas")
            .put("title", "Counter • Canvas")
            .put("height", 300)
            .put("html", "<button id='plus'>Tap</button><output id='count'>0</output>")
            .put("css", "button { background: teal; color: white; padding: 12px; }")
            .put("javascript", """
                window.tapCount = 0;
                document.getElementById('plus').addEventListener('click', () => {
                    document.getElementById('count').textContent = String(++window.tapCount);
                });
                window.canvasReady = true;
            """.trimIndent())
        val monitor = instrumentation.addMonitor(AnnieCanvasActivity::class.java.name, null, false)
        var fullscreen: AnnieCanvasActivity? = null
        try {
            compose.setContent { AnnieTheme { AnnieCanvasMessage(payload) } }
            compose.onNodeWithTag("annie_canvas_message").assertIsDisplayed()
            compose.onNodeWithTag("annie_canvas_webview").assertIsDisplayed()

            lateinit var inlineWebView: WebView
            compose.runOnIdle {
                inlineWebView = findCanvasWebView(compose.activity.window.decorView)
                    ?: error("Canvas WebView not attached")
            }
            compose.waitUntil(12_000) { eval(inlineWebView, "window.canvasReady") == "true" }
            assertEquals("\"undefined\"", eval(inlineWebView, "typeof window.annie"))
            // WebView.getSettings(), like every WebView API, must run on its owning UI thread.
            instrumentation.runOnMainSync {
                assertTrue("Canvas incorrectly allows network loads", inlineWebView.settings.blockNetworkLoads)
                assertTrue("Canvas incorrectly allows file reads", !inlineWebView.settings.allowFileAccess)
                assertTrue("Canvas incorrectly allows content reads", !inlineWebView.settings.allowContentAccess)
            }

            assertEquals("1", eval(inlineWebView, "(document.querySelector('#plus').click(), window.tapCount)"))
            saveEmulatorScreenshot("annie-canvas-inline-counter")

            compose.onNodeWithTag("annie_canvas_fullscreen").performClick()
            fullscreen = instrumentation.waitForMonitorWithTimeout(monitor, 8_000) as? AnnieCanvasActivity
            assertNotNull("Expand did not open the Canvas Activity", fullscreen)
            // Android launches the Activity before its Compose AndroidView is attached.
            // Wait for the actual interactive WebView, not merely Activity.onCreate().
            var fullscreenWebView: WebView? = null
            compose.waitUntil(12_000) {
                instrumentation.runOnMainSync {
                    fullscreenWebView = findCanvasWebView(fullscreen!!.window.decorView)
                }
                fullscreenWebView != null
            }
            assertNotNull("Fullscreen Canvas WebView not attached", fullscreenWebView)
            assertSame("Expanding Canvas must reuse the running WebView", inlineWebView, fullscreenWebView)
            assertEquals("1", eval(fullscreenWebView!!, "window.tapCount"))
            assertEquals("2", eval(fullscreenWebView!!, "(document.querySelector('#plus').click(), window.tapCount)"))
            saveEmulatorScreenshot("annie-canvas-fullscreen-counter")
            instrumentation.runOnMainSync { fullscreen?.finish() }
            compose.waitUntil(8_000) { inlineWebView.parent != null }
            assertEquals("2", eval(inlineWebView, "window.tapCount"))
            compose.onNodeWithTag("annie_canvas_restart").performClick()
            compose.waitUntil(12_000) { eval(inlineWebView, "window.canvasReady") == "true" && eval(inlineWebView, "window.tapCount") == "0" }
        } finally {
            fullscreen?.finish()
            instrumentation.removeMonitor(monitor)
        }
    }

    @Test fun builtInSnakeCommandProducesPlayableCanvasAndCapturesItsChatPreview() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val workspace = ScriptWorkspace(context)
        val payload = try {
            val commands = kotlinx.coroutines.runBlocking { workspace.reload() }
            assertTrue("The /snake starter command was not registered", commands.any { it.name == "snake" })
            JSONObject(kotlinx.coroutines.runBlocking {
                workspace.execute("snake", "/snake", "canvas-snake-proof", 730L)
            })
        } finally {
            workspace.close()
        }
        assertEquals("canvas", payload.optString("type"))
        compose.setContent { AnnieTheme { AnnieCanvasMessage(payload) } }
        compose.onNodeWithTag("annie_canvas_message").assertIsDisplayed()
        lateinit var snake: WebView
        compose.runOnIdle {
            snake = findCanvasWebView(compose.activity.window.decorView)
                ?: error("Snake Canvas WebView was not attached")
        }
        compose.waitUntil(12_000) { eval(snake, "window.annieCanvasSnakeReady") == "true" }
        assertEquals("300", eval(snake, "document.getElementById('board').width"))
        // Canvas Arcade steers by touch on its board; no D-pad buttons belong in the UI.
        assertEquals("true", eval(snake, "document.querySelectorAll('[data-dir]').length === 0"))
        assertEquals("false", eval(snake, "window.annieCanvasSnakeState().started"))
        swipeDownOnWebView(snake)
        compose.waitUntil(8_000) {
            eval(snake, "window.annieCanvasSnakeState().pending === 'down' && window.annieCanvasSnakeState().started") == "true"
        }
        saveEmulatorScreenshot("annie-canvas-snake-in-chat")
    }

    /** Send real Android touch input through the window, not synthetic JS events. */
    private fun swipeDownOnWebView(web: WebView) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val location = IntArray(2)
        var width = 0
        var height = 0
        instrumentation.runOnMainSync {
            web.getLocationOnScreen(location)
            width = web.width
            height = web.height
        }
        val x = location[0] + width / 2f
        val y1 = location[1] + height * 0.38f
        val y2 = location[1] + height * 0.63f
        val t = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float, at: Long) {
            val event = MotionEvent.obtain(t, at, action, x, y, 0)
            try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, y1, t)
        send(MotionEvent.ACTION_MOVE, (y1+y2)/2, t + 24)
        send(MotionEvent.ACTION_UP, y2, t + 48)
        instrumentation.waitForIdleSync()
    }

    private fun eval(web: WebView, script: String): String {
        val value = AtomicReference<String?>()
        val done = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            web.evaluateJavascript(script) { text -> value.set(text); done.countDown() }
        }
        assertTrue("Canvas script did not respond: $script", done.await(3, TimeUnit.SECONDS))
        return value.get().orEmpty()
    }

    private fun findCanvasWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findCanvasWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }
}
