package com.night.endless

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.night.endless.engine.render.EndlessGLView
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoonSurfaceRuntimeTest {
    @Test
    fun approachLowOrbitLandWalkRestoreAndTakeoff() {
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

            var glView = checkNotNull(glRef.get())
            var renderer = glView.endlessRenderer
            await("OpenGL surface becomes valid", 30_000) { glView.holder.surface?.isValid == true }
            val firstFrame = renderer.completedFrameCount()
            await("OpenGL renderer submits frames", 30_000) {
                renderer.completedFrameCount() >= firstFrame + 3L
            }

            checkNotNull(device.findObject(By.textContains("Overview"))).click()
            device.waitForIdle()
            assertTrue(
                "Moon label was not exposed in overview",
                device.wait(androidx.test.uiautomator.Until.hasObject(By.text("Moon")), 8_000)
            )
            checkNotNull(device.findObject(By.text("Moon"))).click()
            device.waitForIdle()
            await("Moon is selected") { renderer.approachSnapshot().bodyId == "moon" }

            assertTrue(
                "Approach Moon control was not exposed",
                device.wait(androidx.test.uiautomator.Until.hasObject(By.textContains("Approach Moon")), 5_000)
            )
            checkNotNull(device.findObject(By.textContains("Approach Moon"))).click()
            device.waitForIdle()
            await("Moon reaches close approach", 20_000) {
                renderer.approachSnapshot().stage == "CLOSE APPROACH"
            }
            capture(instrumentation, "moon-close-approach", glView)

            assertTrue(
                "Low-orbit control was not exposed",
                device.wait(androidx.test.uiautomator.Until.hasObject(By.textContains("Enter low orbit")), 5_000)
            )
            checkNotNull(device.findObject(By.textContains("Enter low orbit"))).click()
            device.waitForIdle()
            await("Moon reaches low orbit", 20_000) {
                renderer.approachSnapshot().stage == "LOW ORBIT"
            }
            capture(instrumentation, "moon-low-orbit", glView)

            assertTrue(
                "Surface-skim control was not exposed",
                device.wait(androidx.test.uiautomator.Until.hasObject(By.textContains("Surface skim")), 5_000)
            )
            checkNotNull(device.findObject(By.textContains("Surface skim"))).click()
            device.waitForIdle()
            await("Moon reaches surface skim", 15_000) {
                renderer.approachSnapshot().stage == "SURFACE SKIM"
            }

            assertTrue(
                "Land on Moon control was not exposed",
                device.wait(androidx.test.uiautomator.Until.hasObject(By.textContains("Land on Moon")), 5_000)
            )
            checkNotNull(device.findObject(By.textContains("Land on Moon"))).click()
            device.waitForIdle()
            await("Lunar surface mode starts") { renderer.surfaceBodyId() == "moon" }
            await("Lunar movement controls are visible") {
                device.findObject(By.text("↑")) != null &&
                    device.findObject(By.textContains("Take off")) != null
            }
            SystemClock.sleep(700)
            capture(instrumentation, "moon-surface", glView)

            val before = renderer.surfaceCoordinates()
            val forward = checkNotNull(device.findObject(By.text("↑")))
            repeat(7) {
                forward.click()
                SystemClock.sleep(70)
            }
            val after = renderer.surfaceCoordinates()
            assertNotEquals("Lunar movement did not change location", before, after)
            capture(instrumentation, "moon-movement", glView)

            val beforeRecreate = renderer.surfaceCoordinates()
            val orientationBeforeRecreate = renderer.surfaceOrientation()
            val clockBeforeRecreate = renderer.currentTimeMillis()
            val previousView = glView
            glRef.set(null)

            scenario.recreate()
            scenario.onActivity { activity -> glRef.set(findGlView(activity.window.decorView)) }
            await("OpenGL view is recreated") { glRef.get() != null && glRef.get() !== previousView }

            glView = checkNotNull(glRef.get())
            renderer = glView.endlessRenderer
            await("Recreated OpenGL surface becomes valid", 30_000) { glView.holder.surface?.isValid == true }
            val recreatedBaseline = renderer.completedFrameCount()
            await("Recreated renderer submits frames", 30_000) {
                renderer.completedFrameCount() >= recreatedBaseline + 3L
            }
            await("Moon surface state survives recreation") { renderer.surfaceBodyId() == "moon" }

            assertEquals("Moon X changed across recreation", beforeRecreate.first, renderer.surfaceCoordinates().first, 0.000001)
            assertEquals("Moon Z changed across recreation", beforeRecreate.second, renderer.surfaceCoordinates().second, 0.000001)
            assertEquals("Moon yaw changed across recreation", orientationBeforeRecreate.first, renderer.surfaceOrientation().first, 0.000001)
            assertEquals("Moon pitch changed across recreation", orientationBeforeRecreate.second, renderer.surfaceOrientation().second, 0.000001)
            assertTrue("UniverseClock moved backwards across Moon recreation", renderer.currentTimeMillis() >= clockBeforeRecreate)
            capture(instrumentation, "moon-recreated", glView)

            val beforeBackground = renderer.surfaceCoordinates()
            val frameBeforeBackground = renderer.completedFrameCount()
            scenario.moveToState(Lifecycle.State.STARTED)
            SystemClock.sleep(500)
            scenario.moveToState(Lifecycle.State.RESUMED)
            await("Moon renderer resumes after background/foreground", 30_000) {
                renderer.completedFrameCount() >= frameBeforeBackground + 3L
            }
            assertEquals("Moon surface body changed while backgrounded", "moon", renderer.surfaceBodyId())
            assertEquals("Moon X changed while backgrounded", beforeBackground.first, renderer.surfaceCoordinates().first, 0.000001)
            assertEquals("Moon Z changed while backgrounded", beforeBackground.second, renderer.surfaceCoordinates().second, 0.000001)
            capture(instrumentation, "moon-resumed", glView)

            checkNotNull(device.findObject(By.textContains("Take off"))).click()
            device.waitForIdle()
            await("Moon takeoff returns to orbital renderer") { renderer.surfaceBodyId() == null }
            await("Moon takeoff returns to close approach", 15_000) {
                renderer.approachSnapshot().bodyId == "moon" &&
                    renderer.approachSnapshot().stage == "CLOSE APPROACH"
            }
            capture(instrumentation, "moon-takeoff", glView)
        } finally {
            scenario.close()
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

    private fun capture(instrumentation: android.app.Instrumentation, name: String, scene: EndlessGLView) {
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val base = checkNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        write(screenshot, File(base, "endless-runtime/$name.png"), name)
        screenshot.recycle()

        val rendered = pixelCopy(scene, name)
        write(rendered, File(base, "endless-runtime/$name-render.png"), "$name-render")
        assertRenderedPixels(rendered, name)
        rendered.recycle()
    }

    private fun pixelCopy(scene: EndlessGLView, name: String): Bitmap {
        assertTrue("GL surface width is zero for $name", scene.width > 0)
        assertTrue("GL surface height is zero for $name", scene.height > 0)
        var lastResult = PixelCopy.ERROR_UNKNOWN

        repeat(10) { attempt ->
            val bitmap = Bitmap.createBitmap(scene.width, scene.height, Bitmap.Config.ARGB_8888)
            val latch = CountDownLatch(1)
            val result = intArrayOf(PixelCopy.ERROR_UNKNOWN)
            PixelCopy.request(scene, bitmap, { code -> result[0] = code; latch.countDown() }, Handler(Looper.getMainLooper()))
            assertTrue("PixelCopy timed out for $name", latch.await(5, TimeUnit.SECONDS))
            lastResult = result[0]
            if (lastResult == PixelCopy.SUCCESS) return bitmap
            bitmap.recycle()
            if (lastResult != PixelCopy.ERROR_SOURCE_NO_DATA) {
                assertTrue("PixelCopy failed for $name with code $lastResult", false)
            }
            SystemClock.sleep(200L + attempt * 80L)
        }

        assertTrue("PixelCopy never received source data for $name (code $lastResult)", false)
        throw AssertionError("unreachable")
    }

    private fun write(bitmap: Bitmap, target: File, label: String) {
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { stream ->
            assertTrue("Failed to write screenshot $label", bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        assertTrue("Screenshot $label is empty", target.length() > 10_000)
    }

    private fun assertRenderedPixels(bitmap: Bitmap, name: String) {
        val left = (bitmap.width * 0.08f).toInt()
        val top = (bitmap.height * 0.08f).toInt()
        val right = (bitmap.width * 0.92f).toInt().coerceAtLeast(left + 1)
        val bottom = (bitmap.height * 0.92f).toInt().coerceAtLeast(top + 1)
        val stepX = ((right - left) / 80).coerceAtLeast(1)
        val stepY = ((bottom - top) / 45).coerceAtLeast(1)
        val colors = HashSet<Int>()
        var minLuma = 255
        var maxLuma = 0
        var nonBlack = 0
        var samples = 0

        for (y in top until bottom step stepY) for (x in left until right step stepX) {
            val color = bitmap.getPixel(x, y)
            colors += color and 0x00ffffff
            val luma = (
                android.graphics.Color.red(color) * 3 +
                    android.graphics.Color.green(color) * 6 +
                    android.graphics.Color.blue(color)
                ) / 10
            minLuma = minOf(minLuma, luma)
            maxLuma = maxOf(maxLuma, luma)
            if (luma > 6) nonBlack++
            samples++
        }

        assertTrue(
            "$name OpenGL surface appears blank/unrendered " +
                "(${colors.size} colors, $nonBlack/$samples lit samples)",
            colors.size >= 8 && nonBlack >= 3
        )
        assertTrue("$name OpenGL surface has no visible scene contrast", maxLuma - minLuma >= 10)
    }
}
