package com.night.endless

import android.app.Activity
import android.graphics.Bitmap
import android.os.SystemClock
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

            scenario.onActivity { renderer.toggleOverview() }
            SystemClock.sleep(1_500)
            capture(instrumentation, "orbit")

            scenario.onActivity { renderer.focus("mars") }
            await("Mars is selected") { renderer.approachSnapshot().bodyId == "mars" }
            scenario.onActivity { renderer.approachSelected() }
            await("Mars approach reaches atmosphere", 20_000) {
                renderer.approachSnapshot().stage == "ATMOSPHERE" ||
                    renderer.approachSnapshot().stage == "SURFACE SKIM"
            }
            capture(instrumentation, "mars-approach")

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
            SystemClock.sleep(1_500)
            capture(instrumentation, "mars-takeoff")
            assertTrue("Orbital controls did not return",
                device.wait(androidx.test.uiautomator.Until.hasObject(By.text("INTERACTIVE 3D ORRERY")), 5_000))
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

    private fun capture(instrumentation: android.app.Instrumentation, name: String, renderedScene: View? = null) {
        val bitmap: Bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        renderedScene?.let { assertSceneHasRenderedPixels(bitmap, it, name) }
        val target = File(
            checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)),
            "endless-runtime/$name.png"
        )
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { stream ->
            assertTrue("Failed to write screenshot $name", bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        bitmap.recycle()
        assertTrue("Screenshot $name is empty", target.length() > 10_000)
    }

    private fun assertSceneHasRenderedPixels(bitmap: Bitmap, scene: View, name: String) {
        val location = IntArray(2)
        scene.getLocationOnScreen(location)
        val left = (location[0] + scene.width * 0.22f).toInt().coerceIn(0, bitmap.width - 1)
        val top = (location[1] + scene.height * 0.22f).toInt().coerceIn(0, bitmap.height - 1)
        val right = (location[0] + scene.width * 0.78f).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (location[1] + scene.height * 0.78f).toInt().coerceIn(top + 1, bitmap.height)
        val stepX = ((right - left) / 14).coerceAtLeast(1)
        val stepY = ((bottom - top) / 14).coerceAtLeast(1)
        val colors = HashSet<Int>()
        var minLuma = 255
        var maxLuma = 0
        for (y in top until bottom step stepY) {
            for (x in left until right step stepX) {
                val color = bitmap.getPixel(x, y)
                val rgb = color and 0x00ffffff
                colors += rgb
                val luma = (android.graphics.Color.red(color) * 3 +
                    android.graphics.Color.green(color) * 6 +
                    android.graphics.Color.blue(color)) / 10
                minLuma = minOf(minLuma, luma)
                maxLuma = maxOf(maxLuma, luma)
            }
        }
        assertTrue("Mars $name scene appears blank or unrendered (${colors.size} colors)", colors.size >= 12)
        assertTrue("Mars $name scene has no visible terrain/sky contrast", maxLuma - minLuma >= 18)
    }
}
