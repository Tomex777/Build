package com.night.offlinegames

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test

/** Exercises genuinely native game mechanics on a real Android emulator/device. */
@RunWith(AndroidJUnit4::class)
class NativeGamesFlowTest {
    @Test fun waterSortPourAndSharkPauseWorkOfflineOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = instrumentation.targetContext
        ActivityScenario.launch(MainActivity::class.java).use {
            assertNotNull(device.wait(Until.findObject(By.text("Offline collection")), 15000))
            val search = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 8000)
            assertNotNull("Native hub search field inaccessible", search)
            search!!.text = "Water Sort"
            val water = device.wait(Until.findObject(By.desc("Open Water Sort Puzzle")), 9000)
            assertNotNull("Native Water Sort not found in hub", water)
            water!!.click()
            assertNotNull("Native water tubes did not appear", device.wait(Until.findObject(By.descContains("Tube 1,")), 9000))
            snap(device, context, "05-native-water-sort.png")

            // Each newly generated level has 4-layer source tubes and two empties.
            val source = device.wait(Until.findObject(By.descContains(", 4 layers")), 4000)
            val empty = device.wait(Until.findObject(By.descContains(", 0 layers")), 4000)
            assertNotNull("Filled native water tube not accessible", source)
            assertNotNull("Empty native water tube not accessible", empty)
            source!!.click()
            val emptyAfterSelection = device.wait(Until.findObject(By.descContains(", 0 layers")), 4000)
            assertNotNull(emptyAfterSelection)
            emptyAfterSelection!!.click()
            assertNotNull("Native pour didn't move any liquid",
                device.wait(Until.findObject(By.descContains(", 1 layers")), 6000))
            device.wait(Until.gone(By.text("Pouring...")), 6000)
            snap(device, context, "06-native-water-poured.png")

            device.pressBack()
            assertNotNull(device.wait(Until.findObject(By.text("Offline collection")), 9000))
            val searchAgain = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 8000)
            assertNotNull(searchAgain)
            searchAgain!!.text = "Shark"
            val shark = device.wait(Until.findObject(By.desc("Open Shark Survival")), 9000)
            assertNotNull("Native shark game isn't discoverable", shark)
            shark!!.click()
            assertNotNull(device.wait(Until.findObject(By.text("Shark Survival")), 9000))
            assertNotNull(device.wait(Until.findObject(By.text("Pause")), 6000))
            snap(device, context, "07-native-shark-playing.png")
            device.findObject(By.text("Pause")).click()
            assertNotNull(device.wait(Until.findObject(By.text("Paused")), 5000))
            snap(device, context, "08-native-shark-paused.png")
            device.findObjects(By.text("Resume")).first().click()
            assertNotNull(device.wait(Until.findObject(By.text("Pause")), 5000))

            // Screenshot and use real Android touch input on native Snake.
            device.pressBack()
            assertNotNull(device.wait(Until.findObject(By.text("Offline collection")), 9000))
            val snakeSearch = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
            assertNotNull("Search not exposed after shark", snakeSearch)
            snakeSearch!!.text = "Snake"
            val snake = device.wait(Until.findObject(By.desc("Open Snake")), 9000)
            assertNotNull("Native Snake missing from hub", snake)
            snake!!.click()
            assertNotNull("Native Snake score HUD missing", device.wait(Until.findObject(By.textStartsWith("SCORE")), 9000))
            snap(device, context, "09-native-snake-playing.png")
            device.swipe(
                device.displayWidth / 2, device.displayHeight * 2 / 3,
                device.displayWidth / 2, device.displayHeight / 3, 12
            )
            device.findObject(By.text("Pause")).click()
            assertNotNull(device.wait(Until.findObject(By.text("Paused")), 5000))
            snap(device, context, "10-native-snake-paused.png")
            device.pressBack()
            assertNotNull(device.wait(Until.findObject(By.text("Offline collection")), 9000))

            // Native Reversi is tested by tapping a real square, not by invoking its model.
            val reversiSearch = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
            assertNotNull(reversiSearch)
            reversiSearch!!.text = "Reversi"
            val reversi = device.wait(Until.findObject(By.desc("Open Reversi")), 9000)
            assertNotNull("Native Reversi missing from hub", reversi)
            reversi!!.click()
            val board = device.wait(Until.findObject(By.desc("Reversi board")), 9000)
            assertNotNull("Native Reversi Canvas is missing", board)
            snap(device, context, "11-native-reversi-opening.png")
            val bounds = board!!.visibleBounds
            val tapX = bounds.left + (bounds.width() * 3.5f / 8f).toInt()
            val tapY = bounds.top + (bounds.height() * 2.5f / 8f).toInt()
            device.click(tapX, tapY)
            val undo = device.wait(Until.findObject(By.text("Undo")), 5000)
            assertNotNull("Native Reversi move did not complete", undo)
            assertTrue("Native Reversi move not registered", undo!!.isEnabled)
            snap(device, context, "12-native-reversi-played.png")
            device.pressBack()
            assertNotNull(device.wait(Until.findObject(By.text("Offline collection")), 9000))
        }
    }

    private fun snap(device: UiDevice, context: android.content.Context, name: String) {
        val directory = context.getExternalFilesDir(null)
        assertNotNull("Android screenshot directory unavailable", directory)
        device.waitForIdle()
        val file = File(directory!!, name)
        assertTrue("Real device capture failed for $name", device.takeScreenshot(file))
        assertTrue("Screenshot was empty: $name", file.exists() && file.length() > 1000L)
    }
}
