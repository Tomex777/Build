package com.night.endless

import android.graphics.Bitmap
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Real Android multi-pointer MotionEvents against the installed GLES view.
 * Covers a left physical joystick and independently owned right look finger;
 * does not pretend a compile or a synthetic preview is a device visual test.
 */
@RunWith(AndroidJUnit4::class)
class SurfaceControlRuntimeTest {
    @Test fun twoThumbMovementCameraHandoffAndFocusLoss() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ui = UiDevice.getInstance(instrumentation)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            var gl: EndlessGLView? = null
            scenario.onActivity { gl = findGlView(it.window.decorView) }
            await("Endless surface and GLSurfaceView layout") {
                gl != null && gl!!.width > 500 && gl!!.height > 250 &&
                    gl!!.holder.surface?.isValid == true
            }
            ui.findObject(By.text("Got it"))?.click()
            val view = checkNotNull(gl)
            val renderer = view.endlessRenderer
            await("GLES frames") { renderer.completedFrameCount() >= 3L }

            scenario.onActivity {
                // Test an actual, supported lunar landing without risking UI
                // Automator timings for unrelated orbital navigation.
                val previous = renderer.snapshotState()
                renderer.restoreState(previous.copy(
                    selectedId = "moon", overview = false,
                    marsSurfaceMode = false, moonSurfaceMode = true,
                    moonSurfaceX = 1.5, moonSurfaceZ = -0.5,
                    extendedSurfaceId = null
                ))
            }
            await("Moon is rendered as a walkable surface", 35_000) {
                renderer.renderedSurfaceBodyId() == "moon"
            }
            val density = view.resources.displayMetrics.density
            // Same dimensions as the single source of truth SurfaceJoystickMath.
            val moveX = 92f * density
            val moveY = view.height - 102f * density
            val lookX = view.width * .68f
            val lookY = view.height * .43f
            assertTrue(moveX > 0f && moveX < view.width * .5f)
            assertTrue(moveY > 0f && moveY < view.height)

            val before = renderer.surfaceCoordinates()
            val yawBefore = renderer.surfaceOrientation().first
            val firstFrame = renderer.completedFrameCount()
            val startedAt = SystemClock.elapsedRealtime()
            val down = SystemClock.uptimeMillis()

            // Android ACTION_POINTER_DOWN (second finger), not two sequential
            // one-finger swipes. Both axes remain live at the same time.
            send(view, down, MotionEvent.ACTION_DOWN,
                intArrayOf(0), floatArrayOf(moveX), floatArrayOf(moveY))
            send(view, down, MotionEvent.ACTION_POINTER_DOWN or
                (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                intArrayOf(0, 1), floatArrayOf(moveX, lookX),
                floatArrayOf(moveY, lookY))
            SystemClock.sleep(110)
            send(view, down, MotionEvent.ACTION_MOVE,
                intArrayOf(0, 1),
                floatArrayOf(moveX + 10f * density, lookX + 110f),
                floatArrayOf(moveY - 51f * density, lookY - 65f))
            SystemClock.sleep(280)
            // A slow software GLES frame need not have rendered yet. A real
            // move event commits held input independently of frame cadence.
            send(view, down, MotionEvent.ACTION_MOVE,
                intArrayOf(0, 1),
                floatArrayOf(moveX + 10f * density, lookX + 110f),
                floatArrayOf(moveY - 51f * density, lookY - 65f))

            val moved = renderer.surfaceCoordinates()
            val yawAfter = renderer.surfaceOrientation().first
            assertTrue("Stick did not move walker while look was held",
                hypot(moved.first - before.first, moved.second - before.second) > 0.00001)
            assertTrue("Look did not rotate independently from movement",
                abs(yawAfter - yawBefore) > .005)
            capture(instrumentation, "controls-two-thumb-moon")

            // Releasing movement must stop the walker while the surviving
            // right finger continues to aim. No ghost stick input.
            send(view, down, MotionEvent.ACTION_POINTER_UP or
                (0 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                intArrayOf(0, 1),
                floatArrayOf(moveX + 10f * density, lookX + 110f),
                floatArrayOf(moveY - 51f * density, lookY - 65f))
            val stopped = renderer.surfaceCoordinates()
            val yawAtRelease = renderer.surfaceOrientation().first
            SystemClock.sleep(150)
            send(view, down, MotionEvent.ACTION_MOVE, intArrayOf(1),
                floatArrayOf(lookX + 150f), floatArrayOf(lookY - 95f))
            SystemClock.sleep(130)
            assertEquals("Walking continued with only camera thumb",
                stopped.first, renderer.surfaceCoordinates().first, 1e-10)
            assertEquals("Walking continued with only camera thumb",
                stopped.second, renderer.surfaceCoordinates().second, 1e-10)
            assertTrue("Right-thumb aiming stopped after left finger lifted",
                abs(renderer.surfaceOrientation().first - yawAtRelease) > .003)
            send(view, down, MotionEvent.ACTION_UP, intArrayOf(1),
                floatArrayOf(lookX + 150f), floatArrayOf(lookY - 95f))

            // Android may remove window focus without ACTION_CANCEL. Simulate
            // the notification-shade path and assert movement does not persist.
            val focusDown = SystemClock.uptimeMillis()
            send(view, focusDown, MotionEvent.ACTION_DOWN, intArrayOf(2),
                floatArrayOf(moveX), floatArrayOf(moveY - 51f * density))
            SystemClock.sleep(110)
            scenario.onActivity { view.onWindowFocusChanged(false) }
            val focusStopped = renderer.surfaceCoordinates()
            SystemClock.sleep(190)
            assertEquals("Movement stuck after window-focus loss",
                focusStopped.first, renderer.surfaceCoordinates().first, 1e-10)
            assertEquals("Movement stuck after window-focus loss",
                focusStopped.second, renderer.surfaceCoordinates().second, 1e-10)
            send(view, focusDown, MotionEvent.ACTION_CANCEL, intArrayOf(2),
                floatArrayOf(moveX), floatArrayOf(moveY - 51f * density))

            // A fresh ACTION_DOWN after interruption must work, not inherit
            // old pointer ownership or a stale camera delta.
            val restartAt = SystemClock.uptimeMillis()
            send(view, restartAt, MotionEvent.ACTION_DOWN, intArrayOf(3),
                floatArrayOf(moveX), floatArrayOf(moveY - 51f * density))
            SystemClock.sleep(160)
            send(view, restartAt, MotionEvent.ACTION_UP, intArrayOf(3),
                floatArrayOf(moveX), floatArrayOf(moveY - 51f * density))
            val afterRestart = renderer.surfaceCoordinates()
            assertTrue("Input could not be reacquired after focus loss",
                afterRestart != focusStopped)

            // Two left fingers: the first thumb can lift without cancelling
            // the second thumb that is already resting inside the joystick.
            // This exercises ACTION_POINTER_UP ownership *promotion*.
            val handoffAt = SystemClock.uptimeMillis()
            val secondX = moveX + 8f * density
            val secondY = moveY - 37f * density
            send(view, handoffAt, MotionEvent.ACTION_DOWN, intArrayOf(4),
                floatArrayOf(moveX), floatArrayOf(moveY - 51f * density))
            send(view, handoffAt, MotionEvent.ACTION_POINTER_DOWN or
                (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                intArrayOf(4, 5), floatArrayOf(moveX, secondX),
                floatArrayOf(moveY - 51f * density, secondY))
            SystemClock.sleep(100)
            send(view, handoffAt, MotionEvent.ACTION_POINTER_UP or
                (0 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                intArrayOf(4, 5), floatArrayOf(moveX, secondX),
                floatArrayOf(moveY - 51f * density, secondY))
            val beforeHandoff = renderer.surfaceCoordinates()
            SystemClock.sleep(210)
            send(view, handoffAt, MotionEvent.ACTION_MOVE, intArrayOf(5),
                floatArrayOf(secondX), floatArrayOf(secondY))
            assertTrue("Second left thumb did not inherit movement",
                renderer.surfaceCoordinates() != beforeHandoff)
            send(view, handoffAt, MotionEvent.ACTION_UP, intArrayOf(5),
                floatArrayOf(secondX), floatArrayOf(secondY))
            val endedHandoff = renderer.surfaceCoordinates()
            SystemClock.sleep(170)
            assertEquals("Movement kept going after final stick finger lifted",
                endedHandoff.first, renderer.surfaceCoordinates().first, 1e-10)
            assertEquals("Movement kept going after final stick finger lifted",
                endedHandoff.second, renderer.surfaceCoordinates().second, 1e-10)

            // Right-thumb-first is equally common on phones: it must not
            // prevent a later left joystick capture or mix up pointer IDs.
            val lookFirst = SystemClock.uptimeMillis()
            val rightBefore = renderer.surfaceOrientation().first
            val leftBefore = renderer.surfaceCoordinates()
            send(view, lookFirst, MotionEvent.ACTION_DOWN, intArrayOf(11),
                floatArrayOf(lookX), floatArrayOf(lookY))
            send(view, lookFirst, MotionEvent.ACTION_POINTER_DOWN or
                (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                intArrayOf(11, 12), floatArrayOf(lookX, moveX),
                floatArrayOf(lookY, moveY))
            send(view, lookFirst, MotionEvent.ACTION_MOVE,
                intArrayOf(11, 12), floatArrayOf(lookX + 90f, moveX),
                floatArrayOf(lookY - 45f, moveY - 51f * density))
            SystemClock.sleep(230)
            send(view, lookFirst, MotionEvent.ACTION_MOVE,
                intArrayOf(11, 12), floatArrayOf(lookX + 90f, moveX),
                floatArrayOf(lookY - 45f, moveY - 51f * density))
            assertTrue("Look-first gesture lost later left-thumb movement",
                renderer.surfaceCoordinates() != leftBefore)
            assertTrue("Look-first gesture did not turn camera",
                abs(renderer.surfaceOrientation().first - rightBefore) > .003)
            send(view, lookFirst, MotionEvent.ACTION_POINTER_UP or
                (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                intArrayOf(11, 12), floatArrayOf(lookX + 90f, moveX),
                floatArrayOf(lookY - 45f, moveY - 51f * density))
            send(view, lookFirst, MotionEvent.ACTION_UP, intArrayOf(11),
                floatArrayOf(lookX + 90f), floatArrayOf(lookY - 45f))

            // Real emulator telemetry; not a lab-grade phone GPU benchmark.
            val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1L)
            val frames = renderer.completedFrameCount() - firstFrame
            val perf = File(
                checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)),
                "endless-runtime/controls-qa-device.txt"
            )
            perf.parentFile?.mkdirs()
            perf.writeText(
                "device=${Build.MODEL}\nsdk=${Build.VERSION.SDK_INT}\n" +
                    "elapsedMs=${elapsed}\ncompletedFrames=${frames}\n" +
                    "fpsOverWholeTest=${frames * 1000.0 / elapsed}\n" +
                    "processPssKiB=${Debug.getPss()}\n" +
                    "method=Android GLES runtime; includes test waits and screenshot\n"
            )
        } finally {
            scenario.close()
        }
    }

    private fun send(
        view: EndlessGLView, downTime: Long, action: Int,
        ids: IntArray, xs: FloatArray, ys: FloatArray
    ) {
        assertEquals(ids.size, xs.size)
        assertEquals(ids.size, ys.size)
        val properties = Array(ids.size) { index ->
            MotionEvent.PointerProperties().apply {
                id = ids[index]
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(ids.size) { index ->
            MotionEvent.PointerCoords().apply {
                x = xs[index]
                y = ys[index]
                pressure = 1f
                size = 1f
            }
        }
        val event = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, ids.size,
            properties, coords, 0, 0, 1f, 1f,
            0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                view.dispatchTouchEvent(event)
            }
        } finally {
            event.recycle()
        }
    }

    private fun findGlView(view: View): EndlessGLView? {
        if (view is EndlessGLView) return view
        if (view !is ViewGroup) return null
        for (i in 0 until view.childCount) {
            findGlView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun await(label: String, timeoutMs: Long = 25_000, test: () -> Boolean) {
        val limit = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < limit) {
            if (test()) return
            SystemClock.sleep(80)
        }
        assertTrue("Timed out waiting for $label", test())
    }

    private fun capture(instrumentation: android.app.Instrumentation, name: String) {
        val shot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val base = checkNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val dest = File(base, "endless-runtime/$name.png")
        dest.parentFile?.mkdirs()
        FileOutputStream(dest).use { out ->
            assertTrue("Unable to save screenshot $name",
                shot.compress(Bitmap.CompressFormat.PNG, 100, out))
        }
        shot.recycle()
        assertTrue("Empty Android screenshot $name", dest.length() > 10_000)
    }
}
