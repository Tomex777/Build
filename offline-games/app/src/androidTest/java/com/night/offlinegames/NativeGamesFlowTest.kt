package com.night.offlinegames

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.runner.RunWith
import org.junit.Test

/** Exercises the two genuinely native games through Android accessibility/touch. */
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
            val water = device.wait(Until.findObject(By.text("Water Sort Puzzle")), 9000)
            assertNotNull("Native Water Sort not found in hub", water)
            water!!.click()
            assertNotNull(device.wait(Until.findObject(By.text("Water Sort Puzzle")), 9000))
            snap(device, context, "05-native-water-sort.png")

            // Each newly generated level has 4-layer source tubes and two empties.
            val source = device.wait(Until.findObject(By.descContains(", 4 layers")), 4000)
            val empty = device.wait(Until.findObject(By.descContains(", 0 layers")), 4000)
            assertNotNull("Filled native water tube not accessible", source)
            assertNotNull("Empty native water tube not accessible", empty)
            source!!.click()
            empty!!.click()
            assertNotNull("Native pour didn't move any liquid",
                device.wait(Until.findObject(By.descContains(", 1 layers")), 6000))
            snap(device, context, "06-native-water-poured.png")

            device.pressBack()
            assertNotNull(device.wait(Until.findObject(By.text("Offline collection")), 9000))
            val searchAgain = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 8000)
            assertNotNull(searchAgain)
            searchAgain!!.text = "Shark"
            val shark = device.wait(Until.findObject(By.text("Shark Survival")), 9000)
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
        }
    }

    private fun snap(device: UiDevice, context: android.content.Context, name: String) {
        val directory = context.getExternalFilesDir(null) ?: return
        device.takeScreenshot(File(directory, name))
    }
}
