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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtendedSurfaceRuntimeTest {
    @Test fun fourNewWorldsDrawAcceptWalkingInputAndTakeOff() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ui = UiDevice.getInstance(instrumentation)
        val glRef = AtomicReference<EndlessGLView>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { glRef.set(findGlView(it.window.decorView)) }
            await("OpenGL view ready") { glRef.get() != null }
            ui.findObject(By.text("Got it"))?.click()
            val gl = glRef.get()
            val renderer = gl.endlessRenderer
            await("Initial GL rendering", 45_000) { renderer.completedFrameCount() > 4L }
            for (id in listOf("mercury", "ceres", "europa", "titan")) {
                val baseline = renderer.completedFrameCount()
                val state = renderer.snapshotState().copy(
                    selectedId = id,
                    overview = false,
                    marsSurfaceMode = false,
                    moonSurfaceMode = false,
                    extendedSurfaceId = id,
                    extendedSurfaceX = 0.0,
                    extendedSurfaceZ = 0.0
                )
                renderer.restoreState(state)
                await("Rendered ${id} landscape", 45_000) {
                    renderer.renderedSurfaceBodyId() == id &&
                    renderer.completedFrameCount() >= baseline + 4L
                }
                val title = id.replaceFirstChar { it.uppercase() }
                await("Correct ${id} takeoff control", 15_000) {
                    ui.findObject(By.desc("Take off from $title")) != null
                }
                val before = renderer.surfaceCoordinates()
                renderer.walkSurface(1f,0f)
                val after = renderer.surfaceCoordinates()
                assertTrue("$id walking must update coordinates", before != after)
                // Actual emulator screenshots, not renders generated outside the game.
                val shot = instrumentation.uiAutomation.takeScreenshot()
                assertTrue("$id screenshot unavailable", shot != null)
                val root = checkNotNull(instrumentation.targetContext.getExternalFilesDir(null))
                val dest = File(root,"endless-runtime/extended-$id.png")
                dest.parentFile?.mkdirs()
                FileOutputStream(dest).use { out ->
                    assertTrue("PNG write failed", shot.compress(Bitmap.CompressFormat.PNG, 100, out))
                }
                shot.recycle()
                ui.findObject(By.desc("Take off from $title")).click()
                await("$id takeoff returns to orbit", 35_000) {
                    renderer.surfaceBodyId() == null &&
                        renderer.renderedSurfaceBodyId() == null
                }
                assertEquals(id, renderer.snapshotState().selectedId)
            }
        } finally {
            scenario.close()
        }
    }

    private fun findGlView(view: View): EndlessGLView? {
        if (view is EndlessGLView) return view
        if (view !is ViewGroup) return null
        for(i in 0 until view.childCount) {
            findGlView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun await(label: String, timeoutMs: Long = 15_000, done: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + timeoutMs
        while(SystemClock.uptimeMillis() < until) {
            if(done()) return
            SystemClock.sleep(80)
        }
        assertTrue("Timed out waiting for $label", done())
    }
}
