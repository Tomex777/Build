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
            focusBodyViaOverview(device, glView, "Moon") { renderer.approachSnapshot().bodyId == "moon" }
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
            focusBodyViaOverview(device, glView, "Mars") { renderer.approachSnapshot().bodyId == "mars" }
            await("Mars orbital controls appear") {
                device.findObject(By.textContains("Approach Mars")) != null
            }
            assertEquals("Surface mode leaked into Mars orbital navigation", null, renderer.surfaceBodyId())
            awaitFrames(renderer.completedFrameCount(), renderer)
            capture(instrumentation, "navigation-mars-focus")

            val afterMars = renderer.currentTimeMillis()
            assertTrue("UniverseClock moved backwards while switching Moon to Mars", afterMars >= afterMoon)

            openOverview(device, renderer)
            focusBodyViaOverview(device, glView, "Earth") { renderer.approachSnapshot().bodyId == "earth" }
            await("Earth focus panel appears") {
                device.findObject(By.text("Earth")) != null
            }
            assertEquals("Surface mode leaked into Earth orbital navigation", null, renderer.surfaceBodyId())
            awaitFrames(renderer.completedFrameCount(), renderer)
            capture(instrumentation, "navigation-earth-return")

            val afterEarth = renderer.currentTimeMillis()
            assertTrue("UniverseClock moved backwards while returning to Earth", afterEarth >= afterMars)

            openOverview(device, renderer)
            focusBodyViaOverview(device, glView, "Moon") { renderer.approachSnapshot().bodyId == "moon" }
            awaitFrames(renderer.completedFrameCount(), renderer)
            assertEquals("Moon round-trip unexpectedly entered surface mode", null, renderer.surfaceBodyId())
            capture(instrumentation, "navigation-moon-roundtrip")

            openOverview(device, renderer)
            focusBodyViaOverview(device, glView, "Venus") { renderer.approachSnapshot().bodyId == "venus" }
            await("Venus exploration action appears") {
                device.findObject(By.textContains("Explore Venus")) != null
            }
            val venusFarAltitude = renderer.approachSnapshot().altitudeKm
            awaitFrames(renderer.completedFrameCount(), renderer)
            capture(instrumentation, "navigation-venus-orbit")
            checkNotNull(device.findObject(By.textContains("Explore Venus"))).click()
            device.waitForIdle()
            await("Venus close approach completes", 20_000) {
                val snapshot = renderer.approachSnapshot()
                snapshot.bodyId == "venus" &&
                    snapshot.stage == "CLOSE APPROACH" &&
                    snapshot.altitudeKm.isFinite() &&
                    snapshot.altitudeKm < 1000.0 &&
                    snapshot.altitudeKm < venusFarAltitude
            }
            awaitFrames(renderer.completedFrameCount(), renderer)
            capture(instrumentation, "navigation-venus-close")
            await("Venus cloud-top control appears") {
                device.findObject(By.textContains("Cloud tops")) != null
            }
            checkNotNull(device.findObject(By.textContains("Cloud tops"))).click()
            device.waitForIdle()
            await("Venus cloud-top view completes", 20_000) {
                val snapshot = renderer.approachSnapshot()
                snapshot.bodyId == "venus" &&
                    snapshot.stage == "CLOUD TOPS" &&
                    snapshot.altitudeKm.isFinite() &&
                    snapshot.altitudeKm < 250.0
            }
            awaitFrames(renderer.completedFrameCount(), renderer)
            capture(instrumentation, "navigation-venus-cloud-tops")

            openOverview(device, renderer)
            focusBodyViaOverview(device, glView, "Ceres") { renderer.approachSnapshot().bodyId == "ceres" }
            await("Ceres belt exploration control appears") {
                device.findObject(By.textContains("Approach Ceres")) != null
            }
            awaitFrames(renderer.completedFrameCount(), renderer)
            capture(instrumentation, "navigation-asteroid-belt-overview")
            val ceresFarAltitude = renderer.approachSnapshot().altitudeKm
            checkNotNull(device.findObject(By.textContains("Approach Ceres"))).click()
            device.waitForIdle()
            await("Ceres close approach completes", 20_000) {
                val snapshot = renderer.approachSnapshot()
                snapshot.bodyId == "ceres" &&
                    snapshot.altitudeKm.isFinite() &&
                    snapshot.altitudeKm < 500.0 &&
                    snapshot.altitudeKm < ceresFarAltitude
            }
            awaitFrames(renderer.completedFrameCount(), renderer)
            capture(instrumentation, "navigation-asteroid-belt")
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

    private fun focusBodyViaOverview(
        device: UiDevice,
        glView: EndlessGLView,
        label: String,
        selected: () -> Boolean
    ) {
        val deadline = SystemClock.uptimeMillis() + 18_000
        var sawTarget = false
        var sweep = 0
        val location = IntArray(2)
        glView.getLocationOnScreen(location)
        val centerY = location[1] + glView.height / 2
        val leftX = location[0] + (glView.width * 0.40f).toInt()
        val rightX = location[0] + (glView.width * 0.62f).toInt()

        while (SystemClock.uptimeMillis() < deadline) {
            val target = device.findObject(By.desc("Focus $label")) ?: device.findObject(By.text(label))
            if (target != null) {
                sawTarget = true
                try {
                    target.click()
                    device.waitForIdle()
                    if (selected()) return
                } catch (_: androidx.test.uiautomator.StaleObjectException) {
                    // Compose can replace orbital label nodes while the camera is
                    // moving. Re-query on the next loop instead of holding one.
                }
            } else {
                // Overview is a 3D orrery, so not every orbiting body is always
                // projected on-screen. Rotate it through the same touch path a
                // user would use until the requested body comes into view.
                val forward = (sweep / 12) % 2 == 0
                device.swipe(
                    if (forward) rightX else leftX,
                    centerY,
                    if (forward) leftX else rightX,
                    centerY,
                    8
                )
                sweep++
            }
            SystemClock.sleep(180)
        }

        assertTrue("$label never became visible while rotating Overview", sawTarget)
        assertTrue("$label did not become selected after real Overview gestures and taps", selected())
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
