package com.nice.offlinegames

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineFlowTest {
    @Test fun hubNativeAndBundledGameWorkWithoutNetworkPermission() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val granted = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty()
        assertFalse("App must never request INTERNET", Manifest.permission.INTERNET in granted)

        val device = UiDevice.getInstance(instrument)
        ActivityScenario.launch(MainActivity::class.java).use {
            assertNotNull("Hub is not visible", device.wait(Until.findObject(By.text("Offline collection")), 15000))
            saveScreenshot(device, context, "01-hub.png")

            val entry2048 = device.wait(Until.findObject(By.text("2048")), 6000)
            assertNotNull("2048 missing from hub", entry2048)
            entry2048.click()
            assertNotNull("Native 2048 failed to open", device.wait(Until.findObject(By.text("New game")), 7000))
            saveScreenshot(device, context, "02-2048.png")

            device.pressBack()
            assertNotNull("Did not return from 2048", device.wait(Until.findObject(By.text("Offline collection")), 7000))

            val mines = device.wait(Until.findObject(By.text("Minesweeper")), 7000)
            assertNotNull("Minesweeper missing from hub", mines)
            mines.click()
            assertNotNull(
                "Bundled offline arcade did not render",
                device.wait(Until.findObject(By.text("One tap at a time, clear the field.")), 18000)
            )
            saveScreenshot(device, context, "03-minesweeper.png")

            device.pressBack()
            assertNotNull("Did not return from arcade", device.wait(Until.findObject(By.text("Offline collection")), 7000))
        }
    }

    private fun saveScreenshot(device: UiDevice, context: android.content.Context, filename: String) {
        val directory = context.getExternalFilesDir(null) ?: return
        device.takeScreenshot(File(directory, filename))
    }
}
