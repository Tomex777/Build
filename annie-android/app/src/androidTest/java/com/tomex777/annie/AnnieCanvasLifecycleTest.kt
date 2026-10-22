package com.tomex777.annie

import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumentation, not mock JS: two separate sandboxed native WebViews. */
@RunWith(AndroidJUnit4::class)
class AnnieCanvasLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun document(title: String) = JSONObject()
        .put("type", "canvas")
        .put("title", title)
        .put("height", 180)
        .put("html", "<div>Running Canvas</div>")
        .put("javascript", """
            window.ticks = 0;
            window.frames = 0;
            setInterval(function () { window.ticks++; }, 25);
            function frame() { window.frames++; requestAnimationFrame(frame); }
            requestAnimationFrame(frame);
        """.trimIndent())

    private fun allCanvasWebViews(): List<WebView> {
        val result = mutableListOf<WebView>()
        fun visit(v: View) {
            if (v is WebView) result.add(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            visit(compose.activity.window.decorView)
        }
        return result
    }

    private fun eval(view: WebView, script: String): String {
        val result = AtomicReference<String>()
        val signal = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.evaluateJavascript(script) { value -> result.set(value); signal.countDown() }
        }
        assertTrue("Canvas JS evaluation hung", signal.await(6, TimeUnit.SECONDS))
        return result.get()
    }

    @After fun tearDownAnimatingWebViews() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            allWebViewsOnThread(compose.activity.window.decorView).forEach {
                (it.parent as? ViewGroup)?.removeView(it)
                it.onPause()
            }
            if (Build.VERSION.SDK_INT == 26) compose.activity.finish()
        }
        if (Build.VERSION.SDK_INT == 26) {
            val deadline = SystemClock.uptimeMillis() + 8_000L
            while (!compose.activity.isDestroyed && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(20)
            }
        }
    }

    private fun allWebViewsOnThread(root: View): List<WebView> {
        val result = mutableListOf<WebView>()
        fun visit(v: View) {
            if (v is WebView) result.add(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))
        }
        visit(root)
        return result
    }

    @Test fun hostPauseIsPerCanvasResumeKeepsStateAndCloseReleasesTheView() {
        compose.setContent {
            AnnieTheme {
                Column {
                    AnnieCanvasMessage(document("One"))
                    AnnieCanvasMessage(document("Two"))
                }
            }
        }
        compose.waitUntil(10_000) { allCanvasWebViews().size == 2 }
        val first = allCanvasWebViews()[0]
        val second = allCanvasWebViews()[1]
        compose.waitUntil(12_000) {
            eval(first, "window.ticks || 0").toInt() > 3 &&
                eval(second, "window.ticks || 0").toInt() > 3
        }
        compose.onAllNodesWithTag("annie_canvas_pause")[0].performClick()
        compose.waitUntil(8_000) { eval(first, "window.__annieCanvasHost.paused") == "true" }
        saveEmulatorScreenshot("annie-agent4-two-canvases-paused")
        val firstTick = eval(first, "window.ticks").toInt()
        val firstFrame = eval(first, "window.frames").toInt()
        val secondTick = eval(second, "window.ticks").toInt()
        SystemClock.sleep(240)
        assertEquals("Paused message must not advance independent JS intervals",
            firstTick, eval(first, "window.ticks").toInt())
        assertEquals("Paused message must not advance rAF loops",
            firstFrame, eval(first, "window.frames").toInt())
        assertTrue("Pausing one Canvas must not pause the other",
            eval(second, "window.ticks").toInt() > secondTick)

        compose.onAllNodesWithTag("annie_canvas_pause")[0].performClick()
        compose.waitUntil(8_000) {
            eval(first, "window.__annieCanvasHost.paused") == "false" &&
                eval(first, "window.ticks").toInt() > firstTick
        }
        compose.onAllNodesWithTag("annie_canvas_close")[0].performClick()
        compose.onNodeWithTag("annie_canvas_closed").assertIsDisplayed()
        saveEmulatorScreenshot("annie-agent4-canvas-closed-card")
        assertTrue("Closed WebView must detach from chat", first.parent == null)
        assertEquals(1, allCanvasWebViews().size)

        compose.onNodeWithTag("annie_canvas_reopen").performClick()
        compose.waitUntil(10_000) { allCanvasWebViews().size == 2 }
        val replacement = allCanvasWebViews()[0]
        assertNotSame("Reopen must create a fresh WebView", first, replacement)
        compose.waitUntil(10_000) { eval(replacement, "window.ticks || 0").toInt() > 0 }
        assertEquals("true", eval(second, "window.__annieCanvasHost.paused === false"))
        saveEmulatorScreenshot("annie-agent4-canvas-reopened")
    }
    @Test fun pausedCanvasStaysPausedAcrossFullscreenAndReturnsToTheSameWebView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.setContent { AnnieTheme { AnnieCanvasMessage(document("Fullscreen lifecycle")) } }
        compose.waitUntil(10_000) { allCanvasWebViews().size == 1 }
        val web = allCanvasWebViews().single()
        compose.waitUntil(10_000) { eval(web, "window.frames || 0").toInt() > 3 }
        compose.onNodeWithTag("annie_canvas_pause").performClick()
        compose.waitUntil(8_000) { eval(web, "window.__annieCanvasHost.paused") == "true" }
        val before = eval(web, "window.frames").toInt()
        val monitor = instrumentation.addMonitor(AnnieCanvasActivity::class.java.name, null, false)
        var fullscreen: AnnieCanvasActivity? = null
        try {
            compose.onNodeWithTag("annie_canvas_fullscreen").performClick()
            fullscreen = instrumentation.waitForMonitorWithTimeout(monitor, 8_000) as? AnnieCanvasActivity
                ?: error("Fullscreen Canvas Activity never opened")
            compose.waitUntil(12_000) {
                val current = fullscreen
                var found = false
                if (current != null) instrumentation.runOnMainSync {
                    found = allWebViewsOnThread(current.window.decorView).any { it === web }
                }
                found
            }
            assertEquals("true", eval(web, "window.__annieCanvasHost.paused"))
            SystemClock.sleep(250)
            assertEquals("Host pause must survive inline-to-fullscreen handoff",
                before, eval(web, "window.frames").toInt())
            saveEmulatorScreenshot("annie-agent4-fullscreen-paused")
        } finally {
            fullscreen?.let { instrumentation.runOnMainSync { it.finish() } }
            instrumentation.removeMonitor(monitor)
        }
        compose.waitUntil(10_000) { allCanvasWebViews().any { it === web } }
        assertEquals("true", eval(web, "window.__annieCanvasHost.paused"))
        // Return to chat must not be confused with destructive Close Canvas.
        assertEquals("true", eval(web, "window.frames === " + before))
    }

    @Test fun hostLifecycleStopFreezesTimersWithoutUserPausingAndStartResumes() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle = registry
        }
        compose.runOnIdle {
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        compose.setContent {
            AnnieTheme {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    AnnieCanvasMessage(document("Background lifecycle"))
                }
            }
        }
        compose.waitUntil(10_000) { allCanvasWebViews().size == 1 }
        val view = allCanvasWebViews().single()
        compose.waitUntil(10_000) { eval(view, "window.ticks || 0").toInt() > 3 }
        compose.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        compose.waitUntil(8_000) { eval(view, "window.__annieCanvasHost.paused") == "true" }
        val frozen = eval(view, "window.ticks").toInt()
        SystemClock.sleep(160)
        assertEquals("Backgrounded Canvas must not advance", frozen,
            eval(view, "window.ticks").toInt())
        compose.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START) }
        compose.waitUntil(10_000) {
            eval(view, "window.__annieCanvasHost.paused") == "false" &&
                eval(view, "window.ticks").toInt() > frozen
        }
        assertTrue("Foreground restoration should retain its Canvas WebView",
            allCanvasWebViews().single() === view)
    }

    @Test fun canvasClippedByComposeViewportPausesWithoutBeingDestroyed() {
        val shifted = mutableStateOf(false)
        compose.setContent {
            AnnieTheme {
                Box(Modifier.fillMaxWidth().height(260.dp).clipToBounds()) {
                    Box(Modifier.offset(y = if (shifted.value) 300.dp else 0.dp)) {
                        AnnieCanvasMessage(document("Viewport clipping"))
                    }
                }
            }
        }
        compose.waitUntil(10_000) { allCanvasWebViews().size == 1 }
        val view = allCanvasWebViews().single()
        compose.waitUntil(10_000) { eval(view, "window.ticks || 0").toInt() > 3 }
        compose.runOnIdle { shifted.value = true }
        compose.waitUntil(10_000) { eval(view, "window.__annieCanvasHost.paused") == "true" }
        val frozen = eval(view, "window.ticks").toInt()
        SystemClock.sleep(160)
        assertEquals("Canvas clipped by Compose must freeze its JS timers",
            frozen, eval(view, "window.ticks").toInt())
        assertTrue("Clipping should not destroy the WebView",
            allCanvasWebViews().single() === view)
        compose.runOnIdle { shifted.value = false }
        compose.waitUntil(10_000) {
            eval(view, "window.__annieCanvasHost.paused") == "false" &&
                eval(view, "window.ticks").toInt() > frozen
        }
    }

    @Test fun userClosedCanvasRemainsClosedAfterItsLazyMessageControllerIsEvicted() {
        // Canvas's message ID is retained by rememberSaveable in keyed chat
        // LazyColumn. The native WebView must be destroyed on eviction, but the
        // explicit user's Close preference should survive until Open is tapped.
        val id = "canvas-lazy-close-regression-" + SystemClock.uptimeMillis()
        val first = AnnieCanvasSessions.get(id)
        first.close()
        first.destroy()
        val restored = AnnieCanvasSessions.get(id)
        assertTrue("Explicitly closed Canvas should not reopen upon recycler attach",
            restored.isClosed)
        restored.reopen()
        restored.destroy()
        val opened = AnnieCanvasSessions.get(id)
        assertTrue("Open must clear the old closed flag", !opened.isClosed)
        opened.destroy()
    }

    @Test fun manuallyPausedCanvasRemainsPausedAfterLazyItemEviction() {
        val id = "canvas-lazy-pause-regression-" + SystemClock.uptimeMillis()
        val first = AnnieCanvasSessions.get(id)
        first.togglePause()
        first.destroy()
        val restored = AnnieCanvasSessions.get(id)
        assertTrue("Host pause must survive Compose's offscreen item disposal",
            restored.pausedByUser)
        restored.togglePause()
        restored.destroy()
        val resumed = AnnieCanvasSessions.get(id)
        assertTrue("Explicit Resume must clear the retained pause preference",
            !resumed.pausedByUser)
        resumed.destroy()
    }

}
