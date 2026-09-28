package com.night.endless

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.night.endless.engine.render.EndlessGLView
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrbitalNavigationRuntimeTest {
    @Test
    fun moonMarsEarthRoundTripKeepsRendererStateHealthy() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val glRef = AtomicReference<EndlessGLView?>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            scenario.onActivity { activity -> glRef.set(findGlView(activity.window.decorView)) }
            await("OpenGL view is created") { glRef.get() != null }

            device.findObject(By.text("Got it"))?.let { prompt ->
                prompt.click()
                device.waitForIdle()
                SystemClock.sleep(400)
            }

            val glView = checkNotNull(glRef.get())
            val renderer = glView.endlessRenderer
            await("OpenGL surface becomes valid", 30_000) { glView.holder.surface?.isValid == true }
            val firstFrame = renderer.completedFrameCount()
            await("OpenGL renderer submits frames", 30_000) {
                renderer.completedFrameCount() >= firstFrame + 3L
            }

            val initialTime = renderer.currentTimeMillis()

            openOverview(device, renderer)
            focusBodyViaLabel(device, "Moon") { renderer.approachSnapshot().bodyId == "moon" }
            await("Moon orbital controls appear") {
                device.findObject(By.textContains("Approach Moon")) != null
            }
            assertEquals("Surface mode leaked into Moon orbital navigation", null, renderer.surfaceBodyId())
            awaitFrames(renderer.completedFrameCount(), renderer)
            capture(instrumentation, "navigation-moon-focus")

            val afterMoon = renderer.currentTimeMillis()
            assertTrue("UniverseClock moved backwards while focusing Moon", afterMoon >= initialTime)

            openOverview(device, renderer)
            capture(instrumentation, "navigation-overview-after-moon")
            focusBodyViaLabel(device, "Mars") { renderer.approachSnapshot().bodyId == "mars" }
            await("Mars orbital controls appear") {
                device.findObject(By.textContains("Approach Mars")) != null
            }
            assertEquals("Surface mode leaked into Mars orbital navigation", null, renderer.surfaceBodyId())
            awaitFrames(renderer.completedFrameCount(), renderer)
            capture(instrumentation, "navigation-mars-focus")

            val afterMars = renderer.currentTimeMillis()
            assertTrue("UniverseClock moved backwards while switching Moon to Mars", afterMars >= afterMoon)

            openOverview(device, renderer)
            focusBodyViaLabel(device, "Earth") { renderer.approachSnapshot().bodyId == "earth" }
            await("Earth focus panel appears") {
                device.findObject(By.text("Earth")) != null
            }
            assertEquals("Surface mode leaked into Earth orbital navigation", null, renderer.surfaceBodyId())
            awaitFrames(renderer.completedFrameCount(), renderer)
            capture(instrumentation, "navigation-earth-return")

            val afterEarth = renderer.currentTimeMillis()
            assertTrue("UniverseClock moved backwards while returning to Earth", afterEarth >= afterMars)

            openOverview(device, renderer)
            focusBodyViaLabel(device, "Moon") { renderer.approachSnapshot().bodyId == "moon" }
            awaitFrames(renderer.completedFrameCount(), renderer)
            assertEquals("Moon round-trip unexpectedly entered surface mode", null, renderer.surfaceBodyId())
            capture(instrumentation, "navigation-moon-roundtrip")
        } finally {
            scenario.close()
        }
    }

    private fun openOverview(
        device: UiDevice,
        renderer: com.night.endless.engine.render.EndlessRenderer
    ) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var sawControl = false
        while (SystemClock.uptimeMillis() < deadline) {
            if (renderer.approachSnapshot().bodyId == null) break
            val overview = device.findObject(By.textContains("Overview"))
            if (overview != null) {
                sawControl = true
                overview.click()
                device.waitForIdle()
                if (renderer.approachSnapshot().bodyId == null) break
            }
            SystemClock.sleep(250)
        }
        assertTrue("Overview control disappeared before navigation", sawControl)
        assertTrue(
            "Overview tap never reached the renderer",
            renderer.approachSnapshot().bodyId == null
        )
        assertTrue(
            "Overview did not expose orbital body labels",
            device.wait(Until.hasObject(By.descStartsWith("Focus ")), 8_000)
        )
    }

    private fun focusBodyViaLabel(device: UiDevice, label: String, selected: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var sawTarget = false
        while (SystemClock.uptimeMillis() < deadline) {
            val target = device.findObject(By.desc("Focus $label")) ?: device.findObject(By.text(label))
            if (target != null) {
                sawTarget = true
                target.click()
                device.waitForIdle()
                if (selected()) return
            }
            SystemClock.sleep(250)
        }
        assertTrue("$label focus target disappeared before selection", sawTarget)
        assertTrue("$label did not become selected after repeated real UI taps", selected())
    }

    private fun awaitFrames(baseline: Long, renderer: com.night.endless.engine.render.EndlessRenderer) {
        await("Renderer advances after orbital navigation", 20_000) {
            renderer.completedFrameCount() >= baseline + 3L
        }
    }

    private fun findGlView(view: View): EndlessGLView? {
        if (view is EndlessGLView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findGlView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun await(label: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue("Timed out waiting for: $label", condition())
    }

    private fun capture(instrumentation: android.app.Instrumentation, name: String) {
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val base = checkNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val target = File(base, "endless-runtime/$name.png")
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { stream ->
            assertTrue(
                "Failed to write screenshot $name",
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream)
            )
        }
        screenshot.recycle()
        assertTrue("Screenshot $name is empty", target.length() > 10_000)
    }
}
