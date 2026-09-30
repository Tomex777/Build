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

            clickEvent(device, "Molten early Earth")
            device.waitForIdle()
            assertTrue("Earth formation epoch was not applied to renderer", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 4.48) < .001 })
            awaitRenderedEpoch(renderer, 4.48)
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-molten-earth.png")

            scrollToEvent(device, "Earth", "Chicxulub impact")
            clickEvent(device, "Chicxulub impact")
            device.waitForIdle()
            assertTrue("Chicxulub event did not jump to its shared epoch", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - .066) < .001 })
            awaitRenderedEpoch(renderer, .066)
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-chicxulub.png")

            clickDesc(device, "History track Venus")
            device.waitForIdle()
            assertTrue(
                "Venus history track did not focus Venus",
                await(5_000) { renderer.snapshotState().selectedId == "venus" && !renderer.snapshotState().overview }
            )
            assertTrue(
                "Venus event chips were not exposed as accessible controls",
                device.wait(Until.hasObject(By.desc("Jump to Venus forms")), 5_000)
            )
            clickEvent(device, "Magma-ocean Venus")
            device.waitForIdle()
            assertTrue(
                "Venus magma epoch did not use the shared clock",
                await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 4.40) < .001 }
            )
            awaitRenderedEpoch(renderer, 4.40)
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-venus-magma.png")

            scrollToEvent(device, "Venus", "Widespread resurfacing")
            clickEvent(device, "Widespread resurfacing")
            device.waitForIdle()
            assertTrue(
                "Venus resurfacing epoch did not use the shared clock",
                await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 0.70) < .001 }
            )
            awaitRenderedEpoch(renderer, 0.70)
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-venus-resurfacing.png")

            clickDesc(device, "History track Mars")
            device.waitForIdle()
            assertTrue(
                "Mars history track did not focus Mars",
                await(5_000) { renderer.snapshotState().selectedId == "mars" && !renderer.snapshotState().overview }
            )
            assertTrue(
                "Mars history events did not replace the prior track",
                device.wait(Until.hasObject(By.desc("History events Mars")), 5_000)
            )
            // Keep this path deterministic on the emulator: after the Venus
            // resurfacing anchor (0.70 Ga), Mars resolves to Atmosphere thins
            // (1.0 Ga). Two previous-event presses reach Volcanic evolution
            // and then Early water environments without depending on the
            // horizontal chip viewport.
            clickText(device, "‹ Event")
            clickText(device, "‹ Event")
            device.waitForIdle()
            assertTrue("Mars wet epoch did not use the shared clock", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 3.70) < .001 })
            awaitRenderedEpoch(renderer, 3.70)
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-mars-wet.png")

            clickDesc(device, "History track Moon")
            device.waitForIdle()
            assertTrue(
                "Moon history track did not focus the Moon",
                await(5_000) { renderer.snapshotState().selectedId == "moon" && !renderer.snapshotState().overview }
            )
            assertTrue(
                "Moon event chips were not exposed as accessible controls",
                device.wait(Until.hasObject(By.desc("Jump to Moon forms")), 5_000)
            )
            // At 3.70 Ga the nearest lunar event is Basin-forming impacts (3.90 Ga).
            // Exercise the persistent previous/next controls as a second real UI path.
            clickText(device, "‹ Event")
            device.waitForIdle()
            assertTrue("Lunar magma-ocean epoch did not use the shared clock", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 4.40) < .001 })
            awaitRenderedEpoch(renderer, 4.40)
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-moon-magma.png")

            clickText(device, "Event ›")
            device.waitForIdle()
            assertTrue("Lunar bombardment epoch did not use the shared clock", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 3.90) < .001 })
            awaitRenderedEpoch(renderer, 3.90)
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-moon-bombardment.png")

            clickText(device, "Event ›")
            device.waitForIdle()
            assertTrue("Lunar mare epoch did not use the shared clock", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 3.50) < .001 })
            awaitRenderedEpoch(renderer, 3.50)
            saveScreenshot(device, instrumentation.targetContext.getExternalFilesDir(null), "history-moon-mare.png")

            clickDesc(device, "History track System")
            device.waitForIdle()
            assertTrue(
                "System history track did not restore the overview",
                await(5_000) { renderer.snapshotState().overview && renderer.snapshotState().selectedId == null }
            )
            clickEvent(device, "Protoplanetary disk")
            device.waitForIdle()
            assertTrue("System epoch did not use the same renderer clock", await(5_000) { kotlin.math.abs(renderer.deepTimeAgeGa() - 4.56) < .001 })
            awaitRenderedEpoch(renderer, 4.56)
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

    private fun awaitRenderedEpoch(
        renderer: com.night.endless.engine.render.EndlessRenderer,
        expectedAgeGa: Double
    ) {
        val baseline = renderer.completedFrameCount()
        assertTrue(
            "Renderer did not present deep-time epoch $expectedAgeGa",
            await(10_000) {
                kotlin.math.abs(renderer.deepTimeAgeGa() - expectedAgeGa) < .001 &&
                    kotlin.math.abs(renderer.renderedDeepTimeAgeGa() - expectedAgeGa) < .001 &&
                    renderer.completedFrameCount() > baseline
            }
        )
        // UiDevice screenshots can race SurfaceView/Compose presentation even after
        // the GL thread has completed. Give SurfaceFlinger one short presentation window.
        SystemClock.sleep(350)
    }

    private fun clickText(device: UiDevice, text: String, timeoutMs: Long = 5_000) {
        clickMatching(device, timeoutMs, text) { device.findObject(By.text(text)) }
    }

    private fun clickTextContains(device: UiDevice, text: String, timeoutMs: Long = 5_000) {
        clickMatching(device, timeoutMs, text) { device.findObject(By.textContains(text)) }
    }

    private fun clickEvent(device: UiDevice, title: String, timeoutMs: Long = 5_000) {
        clickMatching(device, timeoutMs, title) {
            device.findObject(By.desc("Jump to $title")) ?: device.findObject(By.text(title))
        }
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

    private fun scrollToEvent(device: UiDevice, domain: String, target: String) {
        val stripDescription = "History events $domain"

        // Normalize to the oldest end first. Domain changes should already create
        // a fresh ScrollState, but these real touch gestures make the test robust
        // against one-frame stale accessibility geometry from Surface/Compose.
        repeat(4) {
            val bounds = device.findObject(By.desc(stripDescription))
                ?.visibleBounds
                ?.takeIf { it.width() > 80 && it.height() > 20 }
            if (bounds != null) {
                val y = bounds.centerY()
                val startX = (device.displayWidth * .26f).toInt()
                val endX = (device.displayWidth * .84f).toInt()
                if (endX > startX + 40) {
                    device.swipe(startX, y, endX, y, 12)
                }
            }
            SystemClock.sleep(80)
        }

        repeat(16) {
            val targetObject = device.findObject(By.desc("Jump to $target"))
                ?: device.findObject(By.text(target))
            if (targetObject != null && targetObject.visibleBounds.width() > 20) return

            val stripBounds = device.findObject(By.desc(stripDescription))
                ?.visibleBounds
                ?.takeIf { it.width() > 80 && it.height() > 20 }

            val fallbackBounds = com.night.endless.engine.scene.DeepTimeHistory.events(domain)
                .asSequence()
                .mapNotNull { event ->
                    device.findObject(By.desc("Jump to ${event.title}"))
                        ?: device.findObject(By.text(event.title))
                }
                .map { it.visibleBounds }
                .firstOrNull { it.width() > 20 && it.height() > 10 }

            val bounds = stripBounds ?: fallbackBounds
            if (bounds != null) {
                val y = bounds.centerY()
                val startX = (device.displayWidth * .84f).toInt()
                val endX = (device.displayWidth * .26f).toInt()
                if (startX > endX + 40) {
                    device.swipe(startX, y, endX, y, 18)
                }
            }
            SystemClock.sleep(120)
        }

        val targetObject = device.findObject(By.desc("Jump to $target"))
            ?: device.findObject(By.text(target))
        assertTrue(
            "Could not scroll the $domain event strip to $target",
            targetObject?.visibleBounds?.width()?.let { it > 20 } == true
        )
    }

    private fun findGlView(view: View): EndlessGLView? {
        if (view is EndlessGLView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findGlView(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
