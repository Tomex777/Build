package com.night.endless

import android.app.Activity
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarsSurfaceRuntimeTest {
    @Test
    fun orbitApproachLandWalkAndTakeoff() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val glRef = AtomicReference<EndlessGLView?>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity -> glRef.set(findGlView(activity.window.decorView)) }
            await("OpenGL view is created") { glRef.get() != null }

            // A fresh Android emulator can show the system immersive-mode
            // confirmation above the app. Dismiss it before judging renderer output.
            device.findObject(By.text("Got it"))?.let { prompt ->
                prompt.click()
                device.waitForIdle()
                SystemClock.sleep(400)
            }

            val glView = checkNotNull(glRef.get())
            var renderer = glView.endlessRenderer
            await("OpenGL surface becomes valid", 30_000) {
                glView.holder.surface?.isValid == true
            }
            val frameBaseline = renderer.completedFrameCount()
            await("OpenGL renderer submits frames", 30_000) {
                renderer.completedFrameCount() >= frameBaseline + 3L
            }

            SystemClock.sleep(300)
            capture(instrumentation, "space-focus", checkNotNull(glRef.get()))

            assertTrue(
                "Overview control was not exposed",
                device.wait(androidx.test.uiautomator.Until.hasObject(By.textContains("Overview")), 5_000)
            )
            checkNotNull(device.findObject(By.textContains("Overview"))).click()
            device.waitForIdle()
            SystemClock.sleep(1_500)
            capture(instrumentation, "orbit-overview", checkNotNull(glRef.get()))

            assertTrue(
                "Mars label was not exposed in overview",
                device.wait(androidx.test.uiautomator.Until.hasObject(By.text("Mars")), 8_000)
            )
            checkNotNull(device.findObject(By.text("Mars"))).click()
            device.waitForIdle()
            await("Mars is selected") { renderer.approachSnapshot().bodyId == "mars" }

            assertTrue(
                "Approach Mars control was not exposed",
                device.wait(androidx.test.uiautomator.Until.hasObject(By.textContains("Approach Mars")), 5_000)
            )
            checkNotNull(device.findObject(By.textContains("Approach Mars"))).click()
            device.waitForIdle()
            await("Mars approach reaches atmosphere", 20_000) {
                renderer.approachSnapshot().stage == "ATMOSPHERE"
            }
            capture(instrumentation, "mars-approach", checkNotNull(glRef.get()))

            assertTrue(
                "Atmospheric descent control was not exposed",
                device.wait(androidx.test.uiautomator.Until.hasObject(By.textContains("Descend to surface")), 5_000)
            )
            checkNotNull(device.findObject(By.textContains("Descend to surface"))).click()
            device.waitForIdle()
            await("Mars reaches surface-skimming altitude", 15_000) {
                renderer.approachSnapshot().stage == "SURFACE SKIM"
            }
            assertTrue(
                "Land on Mars control was not exposed",
                device.wait(androidx.test.uiautomator.Until.hasObject(By.textContains("Land on Mars")), 5_000)
            )
            checkNotNull(device.findObject(By.textContains("Land on Mars"))).click()
            device.waitForIdle()
            await("Surface mode starts") { renderer.isSurfaceMode() }
            await("Surface controls are visible") {
                device.findObject(By.text("↑")) != null &&
                    device.findObject(By.textContains("Take off")) != null
            }
            SystemClock.sleep(800)
            capture(instrumentation, "mars-surface", checkNotNull(glRef.get()))

            val before = renderer.surfaceCoordinates()
            val forwardControl = checkNotNull(device.findObject(By.text("↑")))
            repeat(8) {
                forwardControl.click()
                SystemClock.sleep(80)
            }
            val after = renderer.surfaceCoordinates()
            assertNotEquals("Surface movement did not change location", before, after)
            capture(instrumentation, "mars-movement", checkNotNull(glRef.get()))

            val lookBefore = renderer.surfaceOrientation()
            val location = IntArray(2)
            scenario.onActivity { checkNotNull(glRef.get()).getLocationOnScreen(location) }
            val scene = checkNotNull(glRef.get())
            val centerY = location[1] + scene.height / 2
            val startX = location[0] + (scene.width * 0.62f).toInt()
            val endX = location[0] + (scene.width * 0.38f).toInt()
            assertTrue("Surface look gesture could not be injected", device.swipe(startX, centerY, endX, centerY, 16))
            device.waitForIdle()
            await("Surface look gesture changes camera orientation") {
                renderer.surfaceOrientation() != lookBefore
            }
            capture(instrumentation, "mars-look", checkNotNull(glRef.get()))

            val coordinatesBeforeRecreate = renderer.surfaceCoordinates()
            val orientationBeforeRecreate = renderer.surfaceOrientation()
            val timeBeforeRecreate = renderer.currentTimeMillis()
            val previousView = checkNotNull(glRef.get())
            glRef.set(null)

            scenario.recreate()
            scenario.onActivity { activity -> glRef.set(findGlView(activity.window.decorView)) }
            await("OpenGL view is recreated") {
                glRef.get() != null && glRef.get() !== previousView
            }

            val restoredView = checkNotNull(glRef.get())
            renderer = restoredView.endlessRenderer
            await("Recreated OpenGL surface becomes valid", 30_000) {
                restoredView.holder.surface?.isValid == true
            }
            val restoredFrameBaseline = renderer.completedFrameCount()
            await("Recreated renderer submits frames", 30_000) {
                renderer.completedFrameCount() >= restoredFrameBaseline + 3L
            }
            await("Mars surface state survives recreation") { renderer.isSurfaceMode() }
            assertTrue(
                "Surface controls were not restored after recreation",
                device.wait(androidx.test.uiautomator.Until.hasObject(By.textContains("Take off")), 5_000)
            )

            val coordinatesAfterRecreate = renderer.surfaceCoordinates()
            val orientationAfterRecreate = renderer.surfaceOrientation()
            assertEquals(
                "Mars X coordinate changed across recreation",
                coordinatesBeforeRecreate.first,
                coordinatesAfterRecreate.first,
                0.000001
            )
            assertEquals(
                "Mars Z coordinate changed across recreation",
                coordinatesBeforeRecreate.second,
                coordinatesAfterRecreate.second,
                0.000001
            )
            assertEquals(
                "Surface yaw changed across recreation",
                orientationBeforeRecreate.first,
                orientationAfterRecreate.first,
                0.000001
            )
            assertEquals(
                "Surface pitch changed across recreation",
                orientationBeforeRecreate.second,
                orientationAfterRecreate.second,
                0.000001
            )
            assertTrue(
                "UniverseClock moved backwards across recreation",
                renderer.currentTimeMillis() >= timeBeforeRecreate
            )
            capture(instrumentation, "mars-recreated", restoredView)

            val coordinatesBeforeBackground = renderer.surfaceCoordinates()
            val orientationBeforeBackground = renderer.surfaceOrientation()
            val clockBeforeBackground = renderer.currentTimeMillis()
            val frameBeforeBackground = renderer.completedFrameCount()

            scenario.moveToState(Lifecycle.State.STARTED)
            SystemClock.sleep(500)
            scenario.moveToState(Lifecycle.State.RESUMED)

            await("Renderer resumes after background/foreground", 30_000) {
                renderer.completedFrameCount() >= frameBeforeBackground + 3L
            }
            assertTrue("Mars surface mode was lost while backgrounded", renderer.isSurfaceMode())
            assertEquals(
                "Mars X coordinate changed while backgrounded",
                coordinatesBeforeBackground.first,
                renderer.surfaceCoordinates().first,
                0.000001
            )
            assertEquals(
                "Mars Z coordinate changed while backgrounded",
                coordinatesBeforeBackground.second,
                renderer.surfaceCoordinates().second,
                0.000001
            )
            assertEquals(
                "Surface yaw changed while backgrounded",
                orientationBeforeBackground.first,
                renderer.surfaceOrientation().first,
                0.000001
            )
            assertEquals(
                "Surface pitch changed while backgrounded",
                orientationBeforeBackground.second,
                renderer.surfaceOrientation().second,
                0.000001
            )
            assertTrue(
                "UniverseClock moved backwards across background/foreground",
                renderer.currentTimeMillis() >= clockBeforeBackground
            )
            capture(instrumentation, "mars-resumed", restoredView)

            checkNotNull(device.findObject(By.textContains("Take off"))).click()
            device.waitForIdle()
            await("Takeoff returns to orbital renderer") { !renderer.isSurfaceMode() }
            await("Mars returns to orbital/approach state after takeoff", 15_000) {
                val snapshot = renderer.approachSnapshot()
                snapshot.bodyId == "mars" &&
                    (snapshot.stage == "CLOSE APPROACH" || snapshot.stage == "ORBIT")
            }
            assertTrue(
                "Surface controls remained visible after takeoff",
                device.wait(androidx.test.uiautomator.Until.gone(By.textContains("Take off")), 5_000)
            )
            assertTrue(
                "Mars orbital controls did not return after takeoff",
                device.wait(androidx.test.uiautomator.Until.hasObject(By.textContains("Approach Mars")), 5_000)
            )
            SystemClock.sleep(800)
            capture(instrumentation, "mars-takeoff", checkNotNull(glRef.get()))
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

    private fun capture(
        instrumentation: android.app.Instrumentation,
        name: String,
        renderedScene: EndlessGLView? = null
    ) {
        val screenshot: Bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val fullTarget = File(
            checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)),
            "endless-runtime/$name.png"
        )
        writeAndPersist(instrumentation, screenshot, fullTarget, "$name.png")

        renderedScene?.let { scene ->
            val rendered = pixelCopy(scene, name)
            val renderTarget = File(
                checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)),
                "endless-runtime/$name-render.png"
            )
            writeAndPersist(instrumentation, rendered, renderTarget, "$name-render.png")
            assertRenderedPixels(rendered, name)
            rendered.recycle()
        }

        screenshot.recycle()
    }

    private fun pixelCopy(scene: EndlessGLView, name: String): Bitmap {
        assertTrue("GL surface width is zero for $name", scene.width > 0)
        assertTrue("GL surface height is zero for $name", scene.height > 0)
        var lastResult = PixelCopy.ERROR_UNKNOWN

        repeat(10) { attempt ->
            val bitmap = Bitmap.createBitmap(scene.width, scene.height, Bitmap.Config.ARGB_8888)
            val latch = CountDownLatch(1)
            val result = intArrayOf(PixelCopy.ERROR_UNKNOWN)
            PixelCopy.request(
                scene,
                bitmap,
                { code ->
                    result[0] = code
                    latch.countDown()
                },
                Handler(Looper.getMainLooper())
            )
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

    private fun writeAndPersist(
        instrumentation: android.app.Instrumentation,
        bitmap: Bitmap,
        target: File,
        publicName: String
    ) {
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { stream ->
            assertTrue(
                "Failed to write screenshot $publicName",
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            )
        }
        assertTrue("Screenshot $publicName is empty", target.length() > 10_000)

        // The CI harness runs instrumentation manually and pulls the target
        // app's external-files directory before uninstalling it. Keeping render
        // evidence here avoids scoped-storage shell-copy failures on Android 16.
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

        for (y in top until bottom step stepY) {
            for (x in left until right step stepX) {
                val color = bitmap.getPixel(x, y)
                val rgb = color and 0x00ffffff
                colors += rgb
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
        }

        assertTrue(
            "$name OpenGL surface appears blank/unrendered " +
                "(${colors.size} colors, $nonBlack/$samples lit samples)",
            colors.size >= 8 && nonBlack >= 3
        )
        assertTrue(
            "$name OpenGL surface has no visible scene contrast",
            maxLuma - minLuma >= 10
        )
    }
}
