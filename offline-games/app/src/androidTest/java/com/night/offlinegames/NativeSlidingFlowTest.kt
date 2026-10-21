package com.night.offlinegames

import android.content.Context
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

@RunWith(AndroidJUnit4::class)
class NativeSlidingFlowTest {
    @Test fun realTileTapUndoAndOfflineSave() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("native_sliding", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            assertNotNull(device.wait(Until.findObject(By.text("Offline collection")), 12000))
            val search = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
            assertNotNull(search)
            search!!.text = "Sliding Puzzle"
            val open = device.wait(Until.findObject(By.desc("Open Sliding Puzzle")), 7000)
            assertNotNull(open)
            open!!.click()
            assertNotNull(device.wait(Until.findObject(By.text("MOVES 0")), 7000))
            capture(device, context, "16-native-sliding-opening.png")
            val initial = NativeSlidingEngine.newGame()
            val movable = initial.tiles[NativeSlidingEngine.adjacent(initial.empty).first()]
            val tile = device.wait(Until.findObject(By.desc("Tile $movable")), 5000)
            assertNotNull("Movable tile not accessible", tile)
            tile!!.click()
            assertNotNull("Native tile touch failed",
                device.wait(Until.findObject(By.text("MOVES 1")), 7000))
            val saved = NativeSlidingEngine.decode(prefs.getString("board", null))
            assertNotNull("Native puzzle not persisted", saved)
            assertEquals(1, saved!!.moves)
            assertEquals(movable, saved.tiles[initial.empty])
            capture(device, context, "17-native-sliding-moved.png")
            val undo = device.wait(Until.findObject(By.text("Undo")), 5000)
            assertNotNull(undo)
            undo!!.click()
            assertNotNull(device.wait(Until.findObject(By.text("MOVES 0")), 5000))
            assertEquals(initial.tiles,
                NativeSlidingEngine.decode(prefs.getString("board", null))!!.tiles)
            capture(device, context, "18-native-sliding-undo.png")
            device.pressBack()
            assertNotNull(device.wait(Until.findObject(By.text("Offline collection")), 7000))
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
