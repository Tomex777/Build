package com.night.offlinegames

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** A complete native puzzle solved by actual Android touch input, not just engine calls. */
@RunWith(AndroidJUnit4::class)
class NativeLightsWinFlowTest {
    @Test fun solveEveryLightThenAdvanceLevel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = instrumentation.targetContext
        context.getSharedPreferences("native_lights", Context.MODE_PRIVATE)
            .edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            assertNotNull(device.wait(Until.findObject(By.text("Offline collection")), 12000))
            val search = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
            assertNotNull(search)
            search!!.text = "Lights Out"
            val open = device.wait(Until.findObject(By.desc("Open Lights Out")), 6000)
            assertNotNull(open)
            open!!.click()
            assertNotNull(device.wait(Until.findObject(By.text("LEVEL 1")), 7000))
            val initial = NativeLightsEngine.newGame()
            val solution = NativeLightsEngine.solve(initial.board)
            assertNotNull("Generated native puzzle has no solution", solution)
            assertTrue("Puzzle was already solved", solution!!.isNotEmpty())
            for (index in solution) {
                val row = index / 5 + 1
                val col = index % 5 + 1
                val cell = device.wait(Until.findObject(
                    By.descContains("Light row $row, column $col:")), 5000)
                assertNotNull("Cell $index disappeared during play", cell)
                cell!!.click()
            }
            assertNotNull("Winning native puzzle did not show completion",
                device.wait(Until.findObject(By.text("All lights off. Puzzle solved!")), 8000))
            val saved = NativeLightsEngine.decode(context.getSharedPreferences(
                "native_lights", Context.MODE_PRIVATE).getString("board", null))
            assertNotNull(saved)
            assertTrue("Solved board not saved", saved!!.won)
            assertNotNull("Solved HUD did not show zero lit cells",
                device.wait(Until.findObject(By.text("LIT 0")), 5000))
            SystemClock.sleep(400)
            capture(device, context, "19-native-lights-completed.png")
            val next = device.wait(Until.findObject(By.text("Next level")), 5000)
            assertNotNull(next)
            next!!.click()
            assertNotNull("Next level did not advance",
                device.wait(Until.findObject(By.text("LEVEL 2")), 6000))
            SystemClock.sleep(250)
            capture(device, context, "20-native-lights-next-level.png")
        }
    }

    private fun capture(device: UiDevice, context: Context, name: String) {
        val directory = context.getExternalFilesDir(null)
        assertNotNull(directory)
        val file = File(directory!!, name)
        assertTrue(device.takeScreenshot(file))
        assertTrue(file.length() > 1000)
    }
}
