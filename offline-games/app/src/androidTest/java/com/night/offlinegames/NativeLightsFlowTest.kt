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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test

/** Real Android touch, lifecycle persistence and screenshot for native Lights Out. */
@RunWith(AndroidJUnit4::class)
class NativeLightsFlowTest {
    @Test fun lightsOutBoardRespondsToTouchesAndPersistsOffline() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = instrumentation.targetContext
        context.getSharedPreferences("native_lights", Context.MODE_PRIVATE)
            .edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            val hub = device.wait(Until.findObject(By.text("Offline collection")), 12000)
            assertNotNull("Offline hub did not launch", hub)
            val search = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
            assertNotNull("Hub search unavailable", search)
            search!!.text = "Lights Out"
            val open = device.wait(Until.findObject(By.desc("Open Lights Out")), 7000)
            assertNotNull("Lights Out missing from hub", open)
            open!!.click()
            assertNotNull("Lights Out native HUD absent",
                device.wait(Until.findObject(By.text("MOVES 0")), 8000))
            capture(device, context, "13-native-lights-opening.png")
            val center = device.wait(
                Until.findObject(By.descContains("Light row 3, column 3:")), 6000)
            assertNotNull("Lights Out cell not accessible", center)
            center!!.click()
            assertNotNull("Lights Out native touch did not update HUD",
                device.wait(Until.findObject(By.text("MOVES 1")), 7000))
            SystemClock.sleep(450)
            val saved = NativeLightsEngine.decode(
                context.getSharedPreferences("native_lights", Context.MODE_PRIVATE)
                    .getString("board", null))
            assertNotNull("Lights Out did not persist a valid state", saved)
            assertTrue("Tap did not reach the native engine", saved!!.moves == 1)
            capture(device, context, "14-native-lights-after-touch.png")
            val undo = device.wait(Until.findObject(By.text("Undo")), 4000)
            assertNotNull("Undo not exposed", undo)
            undo!!.click()
            assertNotNull("Undo did not restore previous move count",
                device.wait(Until.findObject(By.text("MOVES 0")), 5000))
            val hint = device.wait(Until.findObject(By.text("Hint")), 4000)
            assertNotNull("Hint not exposed", hint)
            hint!!.click()
            assertNotNull("Hint did not render",
                device.wait(Until.findObject(By.textContains("outlined light")), 5000))
            capture(device, context, "15-native-lights-hint.png")
            device.pressBack()
            assertNotNull("Back did not return to offline hub",
                device.wait(Until.findObject(By.text("Offline collection")), 7000))
        }
    }

    private fun capture(device: UiDevice, context: Context, name: String) {
        val directory = context.getExternalFilesDir(null)
        assertNotNull("Screenshot storage missing", directory)
        val file = File(directory!!, name)
        assertTrue("Failed to capture real emulator frame", device.takeScreenshot(file))
        assertTrue("Screenshot file empty", file.length() > 1000)
    }
}
