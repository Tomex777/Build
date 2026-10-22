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
import com.night.endless.engine.render.EndlessGLView
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android GLES3 spacecraft-camera evidence, NOT synthetic mockup PNGs.
 * The previous UI suite rendered only the terrestrial planets and could
 * never catch blank Hubble textures or a clipped oblate Saturn ring.
 */
@RunWith(AndroidJUnit4::class)
class GasGiantRuntimeTest {
    @Test fun jupiterAndSaturnFocusedViewsRenderWithoutLosingNavigation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ui = UiDevice.getInstance(instrumentation)
        val glView = AtomicReference<EndlessGLView?>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                glView.set(findGlView(activity.window.decorView))
            }
            await("3D view created") { glView.get() != null }
            ui.findObject(By.text("Got it"))?.click()
            val renderer = checkNotNull(glView.get()).endlessRenderer
            await("initial OpenGL scenes", 40_000) {
                renderer.completedFrameCount() >= 5L
            }
            for ((planet, zoom) in listOf("jupiter" to .48f, "saturn" to .55f)) {
                val before = renderer.completedFrameCount()
                renderer.focus(planet)
                renderer.zoomBy(zoom)
                await("fully centred $planet after the interplanetary fly", 75_000) {
                    val state = renderer.snapshotState()
                    state.selectedId == planet &&
                        !state.overview &&
                        state.distance < 6.2 &&
                        renderer.completedFrameCount() >= before + 20L &&
                        renderer.labelSnapshots().any { label ->
                            label.id == planet && label.visible &&
                                kotlin.math.abs(label.xPx - checkNotNull(glView.get()).width/2f) <
                                    checkNotNull(glView.get()).width*.10f &&
                                kotlin.math.abs(label.yPx - checkNotNull(glView.get()).height/2f) <
                                    checkNotNull(glView.get()).height*.10f
                        }
                }
                assertEquals(null, renderer.surfaceBodyId())
                // Jupiter and Saturn are cloud atmospheres, not rocky worlds.
                // Never allow them through a generic surface-landing path.
                assertFalse("$planet must not permit a solid-surface landing",
                    renderer.landOnExtendedBody(planet))
                val projected = checkNotNull(renderer.labelSnapshots()
                    .firstOrNull { it.id == planet && it.visible })
                capture(instrumentation, "orbital-$planet-oblate")
                val storage = checkNotNull(instrumentation.targetContext.getExternalFilesDir(null))
                File(storage, "endless-runtime/orbital-$planet-centre.txt").writeText(
                    "${projected.xPx} ${projected.yPx} " +
                        "${checkNotNull(glView.get()).width} " +
                        "${checkNotNull(glView.get()).height}"
                )
            }
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

    private fun await(name: String, timeoutMs: Long = 15_000, success: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (success()) return
            SystemClock.sleep(70)
        }
        assertTrue("Timed out waiting for $name", success())
    }

    private fun capture(instrumentation: android.app.Instrumentation, name: String) {
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val base = checkNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val file = File(base, "endless-runtime/$name.png")
        file.parentFile?.mkdirs()
        FileOutputStream(file).use {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
        assertTrue("Missing true Android screenshot $name", file.length() > 10_000)
    }
}
