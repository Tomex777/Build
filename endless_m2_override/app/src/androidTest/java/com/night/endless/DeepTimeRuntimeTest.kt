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

            device.findObject(By.textContains("History")).click()
            assertTrue("Deep Time panel was not opened", device.wait(Until.hasObject(By.text("DEEP TIME")), 5_000))
            assertTrue("Earth history markers are missing", device.wait(Until.hasObject(By.text("Earth forms")), 5_000))
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-present.png")

            device.findObject(By.text("Molten early Earth")).click()
            device.waitForIdle()
            assertTrue("Earth formation epoch was not applied to renderer", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 4.48) < .001 })
            assertTrue("Deep-time epoch label is missing", device.wait(Until.hasObject(By.textContains("4.48 Ga")), 5_000))
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-molten-earth.png")

            device.findObject(By.text("System")).click()
            device.findObject(By.text("Protoplanetary disk")).click()
            device.waitForIdle()
            assertTrue("System epoch did not use the same renderer clock", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 4.56) < .001 })

            scenario.recreate()
            glRef.set(null)
            scenario.onActivity { activity -> glRef.set(findGlView(activity.window.decorView)) }
            assertTrue("History was not restored after recreation", device.wait(Until.hasObject(By.text("DEEP TIME")), 10_000))
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

    private fun saveScreenshot(device: UiDevice, externalRoot: File?, name: String) {
        val directory = File(externalRoot, "endless-runtime").apply { mkdirs() }
        assertTrue("Could not save $name", device.takeScreenshot().save(File(directory, name)))
    }

    private fun findGlView(view: View): EndlessGLView? {
        if (view is EndlessGLView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findGlView(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
