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
class DeepTimeRuntimeTest {
    @Test
    fun historyScrubAndCrossScaleEpochSurviveActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val glRef = AtomicReference<EndlessGLView?>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity -> glRef.set(findGlView(activity.window.decorView)) }
            assertTrue("OpenGL view was not created", device.wait(Until.hasObject(By.textContains("History")), 20_000))
            val renderer = checkNotNull(glRef.get()).endlessRenderer
            val orbitalTimeBeforeHistory = renderer.currentTimeMillis()

            device.findObject(By.text("×"))?.click()
            clickTextContains(device, "History")
            assertTrue("Deep Time panel was not opened", device.wait(Until.hasObject(By.text("DEEP TIME")), 5_000))
            clickDesc(device, "History track Earth")
            assertTrue("Earth history markers are missing", device.wait(Until.hasObject(By.text("Earth forms")), 5_000))
            clickText(device, "Speed 1×")
            assertTrue("Timeline playback speed did not advance", device.wait(Until.hasObject(By.text("Speed 5×")), 3_000))
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-present.png")

            clickText(device, "Molten early Earth")
            device.waitForIdle()
            assertTrue("Earth formation epoch was not applied to renderer", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 4.48) < .001 })
            assertTrue("Deep-time epoch label is missing", device.wait(Until.hasObject(By.textContains("4.48 Ga")), 5_000))
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-molten-earth.png")

            scrollToEvent(device, "Earth forms", "Chicxulub impact")
            clickText(device, "Chicxulub impact")
            device.waitForIdle()
            assertTrue("Chicxulub event did not jump to its shared epoch", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - .066) < .001 })
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-chicxulub.png")

            clickDesc(device, "History track Mars")
            device.waitForIdle()
            assertTrue(
                "Mars history track did not focus Mars",
                await(5_000) { renderer.snapshotState().selectedId == "mars" && !renderer.snapshotState().overview }
            )
            scrollToEvent(device, "Mars forms", "Early water environments")
            clickText(device, "Early water environments")
            device.waitForIdle()
            assertTrue("Mars wet epoch did not use the shared clock", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 3.70) < .001 })
            awaitDeepTimeLabel(device, "3.7 Ga ago")
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-mars-wet.png")

            clickDesc(device, "History track Moon")
            device.waitForIdle()
            assertTrue(
                "Moon history track did not focus the Moon",
                await(5_000) { renderer.snapshotState().selectedId == "moon" && !renderer.snapshotState().overview }
            )
            scrollToEvent(device, "Moon forms", "Magma ocean")
            clickText(device, "Magma ocean")
            device.waitForIdle()
            assertTrue("Lunar magma-ocean epoch did not use the shared clock", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 4.40) < .001 })
            awaitDeepTimeLabel(device, "4.4 Ga ago")
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-moon-magma.png")

            scrollToEvent(device, "Moon forms", "Basin-forming impacts")
            clickText(device, "Basin-forming impacts")
            device.waitForIdle()
            assertTrue("Lunar bombardment epoch did not use the shared clock", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 3.90) < .001 })
            awaitDeepTimeLabel(device, "3.9 Ga ago")
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-moon-bombardment.png")

            scrollToEvent(device, "Moon forms", "Mare volcanism")
            clickText(device, "Mare volcanism")
            device.waitForIdle()
            assertTrue("Lunar mare epoch did not use the shared clock", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 3.50) < .001 })
            awaitDeepTimeLabel(device, "3.5 Ga ago")
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-moon-mare.png")

            clickDesc(device, "History track System")
            device.waitForIdle()
            assertTrue(
                "System history track did not restore the overview",
                await(5_000) { renderer.snapshotState().overview && renderer.snapshotState().selectedId == null }
            )
            clickText(device, "Protoplanetary disk")
            device.waitForIdle()
            assertTrue("System epoch did not use the same renderer clock", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 4.56) < .001 })
            awaitDeepTimeLabel(device, "4.56 Ga ago")
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-protoplanetary-disk.png")

            scenario.recreate()
            glRef.set(null)
            scenario.onActivity { activity -> glRef.set(findGlView(activity.window.decorView)) }
            assertTrue("History was not restored after recreation", device.wait(Until.hasObject(By.text("DEEP TIME")), 10_000))
            assertTrue("Timeline speed was not restored after recreation", device.wait(Until.hasObject(By.text("Speed 5×")), 5_000))
            assertTrue("Epoch was not restored after recreation", await(5_000) { kotlin.math.abs(checkNotNull(glRef.get()).endlessRenderer.deepTimeAgeGa() - 4.56) < .001 })
            assertTrue("Orbital clock was incorrectly replaced by the deep-time clock", checkNotNull(glRef.get()).endlessRenderer.currentTimeMillis() >= orbitalTimeBeforeHistory)
        } finally {
            scenario.close()
        }
    }

    private fun await(timeoutMs: Long, predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return true
            SystemClock.sleep(40)
        }
        return predicate()
    }

    private fun awaitDeepTimeLabel(device: UiDevice, ageLabel: String) {
        assertTrue(
            "Deep-time UI did not settle on $ageLabel",
            device.wait(Until.hasObject(By.desc("Deep time $ageLabel")), 5_000)
        )
        device.waitForIdle()
    }

    private fun clickText(device: UiDevice, text: String, timeoutMs: Long = 5_000) {
        clickMatching(device, timeoutMs, text) { device.findObject(By.text(text)) }
    }

    private fun clickTextContains(device: UiDevice, text: String, timeoutMs: Long = 5_000) {
        clickMatching(device, timeoutMs, text) { device.findObject(By.textContains(text)) }
    }

    private fun clickDesc(device: UiDevice, description: String, timeoutMs: Long = 5_000) {
        clickMatching(device, timeoutMs, description) { device.findObject(By.desc(description)) }
    }

    private fun clickMatching(
        device: UiDevice,
        timeoutMs: Long,
        label: String,
        find: () -> androidx.test.uiautomator.UiObject2?
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val target = find()
            if (target != null) {
                try {
                    target.click()
                    device.waitForIdle()
                    return
                } catch (_: androidx.test.uiautomator.StaleObjectException) {
                    // Compose may publish a replacement semantics node while the camera
                    // or panel is settling. Re-query instead of holding a stale object.
                }
            }
            SystemClock.sleep(100)
        }
        assertTrue("Could not click $label", false)
    }

    private fun saveScreenshot(device: UiDevice, externalRoot: File?, name: String) {
        val directory = File(externalRoot, "endless-runtime").apply { mkdirs() }
        assertTrue("Could not save $name", device.takeScreenshot(File(directory, name)))
    }

    private fun scrollToEvent(device: UiDevice, anchor: String, target: String) {
        val anchorObject = device.findObject(By.text(anchor))
        val y = anchorObject?.visibleBounds?.centerY() ?: (device.displayHeight * .88f).toInt()
        repeat(12) {
            val targetObject = device.findObject(By.text(target))
            if (targetObject != null && targetObject.visibleBounds.width() > 20) return
            device.swipe((device.displayWidth * .82f).toInt(), y, (device.displayWidth * .25f).toInt(), y, 18)
            device.waitForIdle()
        }
        assertTrue("Could not scroll the event strip to $target", device.findObject(By.text(target))?.visibleBounds?.width()?.let { it > 20 } == true)
    }

    private fun findGlView(view: View): EndlessGLView? {
        if (view is EndlessGLView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findGlView(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
