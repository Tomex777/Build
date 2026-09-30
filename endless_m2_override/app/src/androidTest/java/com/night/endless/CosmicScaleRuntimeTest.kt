package com.night.endless

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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CosmicScaleRuntimeTest {
    @Test
    fun cosmicScalesShareHistoryAndSurviveRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val glRef = AtomicReference<EndlessGLView?>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            scenario.onActivity { activity -> glRef.set(findGlView(activity.window.decorView)) }
            assertTrue("Endless shell did not launch", device.wait(Until.hasObject(By.text("ENDLESS")), 20_000))
            val renderer = checkNotNull(glRef.get()).endlessRenderer
            val orbitalTimeBefore = renderer.currentTimeMillis()
            assertTrue(
                "Renderer did not present initial frames",
                await(12_000) { renderer.completedFrameCount() >= 3L }
            )

            openScalePicker(device)
            clickDesc(device, "Scale Milky Way")
            assertTrue("Milky Way scale did not open", device.wait(Until.hasObject(By.text("Milky Way")), 5_000))
            assertTrue("Milky Way did not expose master history", device.wait(Until.hasObject(By.descStartsWith("Cosmic master history")), 5_000))
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "cosmic-milky-way.png")

            openScalePicker(device)
            clickDesc(device, "Scale Local Group")
            assertTrue("Local Group scale did not open", device.wait(Until.hasObject(By.text("Local Group")), 5_000))
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "cosmic-local-group.png")

            openScalePicker(device)
            clickDesc(device, "Scale Observable Universe")
            assertTrue(
                "Observable Universe scale did not open",
                device.wait(Until.hasObject(By.text("Observable Universe")), 5_000)
            )
            assertTrue(
                "Observable Universe disclaimer missing",
                device.wait(Until.hasObject(By.text("SCHEMATIC SCALE VIEW")), 5_000)
            )
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "cosmic-observable-universe.png")

            scenario.recreate()
            glRef.set(null)
            scenario.onActivity { activity -> glRef.set(findGlView(activity.window.decorView)) }
            assertTrue(
                "Cosmic scale did not survive activity recreation",
                device.wait(Until.hasObject(By.text("Observable Universe")), 10_000)
            )
            val restoredRenderer = checkNotNull(glRef.get()).endlessRenderer
            assertTrue(
                "Renderer stopped after cosmic-scale recreation",
                await(10_000) { restoredRenderer.completedFrameCount() >= 3L }
            )
            assertTrue(
                "UniverseClock moved backwards across recreation",
                restoredRenderer.currentTimeMillis() >= orbitalTimeBefore
            )

            clickTextContains(device, "History")
            assertTrue("Deep Time did not open from cosmic scale", device.wait(Until.hasObject(By.text("DEEP TIME")), 5_000))
            clickDesc(device, "History track Earth")
            assertTrue(
                "Selecting a body history did not return to the solar-system renderer",
                device.wait(Until.hasObject(By.desc("Focus Earth")), 8_000) ||
                    await(8_000) { restoredRenderer.snapshotState().selectedId == "earth" }
            )
            assertTrue(
                "Earth history did not share renderer epoch state",
                await(5_000) { restoredRenderer.deepTimeAgeGa().isFinite() }
            )
        } finally {
            scenario.close()
        }
    }

    private fun openScalePicker(device: UiDevice) {
        clickTextContains(device, "Scale")
        assertTrue(
            "Scale picker did not open",
            device.wait(Until.hasObject(By.desc("Scale Milky Way")), 5_000)
        )
    }

    private fun clickDesc(device: UiDevice, description: String, timeoutMs: Long = 5_000) {
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
            SystemClock.sleep(100)
        }
        assertTrue("Could not click $description", false)
    }

    private fun clickTextContains(device: UiDevice, text: String, timeoutMs: Long = 5_000) {
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
            SystemClock.sleep(100)
        }
        assertTrue("Could not click text containing $text", false)
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
