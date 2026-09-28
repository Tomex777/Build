package com.night.endless

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
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
            val renderer = checkNotNull(glRef.get()).endlessRenderer
            await("OpenGL surface begins rendering", 30_000) {
                renderer.approachSnapshot().stage.isNotBlank()
            }

            SystemClock.sleep(800)
            capture(instrumentation, "space-focus", checkNotNull(glRef.get()))

            scenario.onActivity { renderer.toggleOverview() }
            SystemClock.sleep(1_500)
            capture(instrumentation, "orbit-overview", checkNotNull(glRef.get()))

            scenario.onActivity { renderer.focus("mars") }
            await("Mars is selected") { renderer.approachSnapshot().bodyId == "mars" }
            scenario.onActivity { renderer.approachSelected() }
            await("Mars approach reaches atmosphere", 20_000) {
                renderer.approachSnapshot().stage == "ATMOSPHERE" ||
                    renderer.approachSnapshot().stage == "SURFACE SKIM"
            }
            capture(instrumentation, "mars-approach", checkNotNull(glRef.get()))

            scenario.onActivity { renderer.zoomBy(0.5f) }
            await("Mars reaches surface-skimming altitude") {
                renderer.approachSnapshot().stage == "SURFACE SKIM"
            }
            scenario.onActivity { assertTrue("Landing transition was rejected", renderer.landOnMars()) }
            await("Surface mode starts") { renderer.isSurfaceMode() }
            await("Surface controls are visible") {
                device.findObject(By.text("↑")) != null &&
                    device.findObject(By.textContains("Take off")) != null
            }
            SystemClock.sleep(800)
            capture(instrumentation, "mars-surface", checkNotNull(glRef.get()))

            val before = renderer.surfaceCoordinates()
            repeat(8) {
                scenario.onActivity { renderer.walkSurface(1f, 0f) }
                SystemClock.sleep(80)
            }
            val after = renderer.surfaceCoordinates()
            assertNotEquals("Surface movement did not change location", before, after)
            capture(instrumentation, "mars-movement", checkNotNull(glRef.get()))

            scenario.onActivity { assertTrue("Takeoff was rejected", renderer.takeOffMars()) }
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
        assertTrue("PixelCopy failed for $name with code ${result[0]}", result[0] == PixelCopy.SUCCESS)
        return bitmap
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

        // connectedDebugAndroidTest uninstalls the target APK after the test.
        // Persist frames before cleanup so CI artifacts contain the real render.
        val device = UiDevice.getInstance(instrumentation)
        val publicDir = "/sdcard/Download/endless-runtime"
        device.executeShellCommand("mkdir -p $publicDir")
        device.executeShellCommand("cp '${target.absolutePath}' '$publicDir/$publicName'")
        val publicBytes = device.executeShellCommand(
            "stat -c %s '$publicDir/$publicName'"
        ).trim().toLongOrNull() ?: 0L
        assertTrue("Public screenshot $publicName was not persisted", publicBytes > 10_000)
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
