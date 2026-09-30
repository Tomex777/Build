package com.night.endless

import android.os.Build
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
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatibilityRuntimeTest {
    @Test
    fun api26LaunchRenderScaleAndRecreate() {
        assertEquals("This compatibility test must run on API 26", 26, Build.VERSION.SDK_INT)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val glRef = AtomicReference<EndlessGLView?>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            scenario.onActivity { activity -> glRef.set(findGlView(activity.window.decorView)) }
            assertTrue("Endless shell did not launch on API 26", device.wait(Until.hasObject(By.text("ENDLESS")), 25_000))
            val renderer = checkNotNull(glRef.get()).endlessRenderer
            assertTrue(
                "OpenGL renderer did not present frames on API 26",
                await(15_000) { renderer.completedFrameCount() >= 3L }
            )
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "api26-solar-system.png")

            clickTextContains(device, "Scale")
            clickDesc(device, "Scale Milky Way")
            assertTrue("Cosmic scale failed on API 26", device.wait(Until.hasObject(By.text("Milky Way")), 8_000))
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "api26-milky-way.png")

            scenario.recreate()
            glRef.set(null)
            scenario.onActivity { activity -> glRef.set(findGlView(activity.window.decorView)) }
            assertTrue(
                "Saved cosmic scale did not survive recreation on API 26",
                device.wait(Until.hasObject(By.text("Milky Way")), 12_000)
            )
            val restored = checkNotNull(glRef.get()).endlessRenderer
            assertTrue(
                "Renderer did not resume after recreation on API 26",
                await(12_000) { restored.completedFrameCount() >= 3L }
            )

            clickTextContains(device, "History")
            assertTrue("Deep Time did not open on API 26", device.wait(Until.hasObject(By.text("DEEP TIME")), 8_000))
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "api26-deep-time.png")
        } finally {
            scenario.close()
        }
    }

    private fun clickTextContains(device: UiDevice, text: String, timeoutMs: Long = 7_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val target = device.findObject(By.textContains(text))
            if (target != null) {
                try {
                    target.click()
                    device.waitForIdle()
                    return
                } catch (_: androidx.test.uiautomator.StaleObjectException) {
                }
            }
            SystemClock.sleep(120)
        }
        assertTrue("Could not click text containing $text", false)
    }

    private fun clickDesc(device: UiDevice, description: String, timeoutMs: Long = 7_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val target = device.findObject(By.desc(description))
            if (target != null) {
                try {
                    target.click()
                    device.waitForIdle()
                    return
                } catch (_: androidx.test.uiautomator.StaleObjectException) {
                }
            }
            SystemClock.sleep(120)
        }
        assertTrue("Could not click $description", false)
    }

    private fun await(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return true
            SystemClock.sleep(50)
        }
        return predicate()
    }

    private fun saveScreenshot(device: UiDevice, externalRoot: File?, name: String) {
        val directory = File(externalRoot, "endless-runtime").apply { mkdirs() }
        assertTrue("Could not save $name", device.takeScreenshot(File(directory, name)))
    }

    private fun findGlView(view: View): EndlessGLView? {
        if (view is EndlessGLView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findGlView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
