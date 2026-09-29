package com.tomex777.annie

import android.graphics.Bitmap
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream

/** Recover Android 16's transient System UI ANR dialog and then capture the real app surface. */
internal fun recoverSystemUiAnr() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    val dialog = By.textContains("System UI isn't responding")
    repeat(3) {
        if (!device.hasObject(dialog)) return@repeat
        val waitButton = device.findObject(By.text("Wait"))
        checkNotNull(waitButton) { "System UI ANR dialog has no Wait action" }.click()
        device.wait(Until.gone(dialog), 8_000)
        Thread.sleep(800)
    }
    check(!device.hasObject(dialog)) { "Android System UI stayed unresponsive after tapping Wait" }
    device.waitForIdle()
}

internal fun saveEmulatorScreenshot(name: String): Uri {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    recoverSystemUiAnr()
    val context = instrumentation.targetContext
    val safe = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
        "Android could not capture the current emulator display: $safe"
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        val pictureDirectory = checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)) {
            "App-specific picture storage is unavailable for emulator screenshot: $safe"
        }
        val directory = File(pictureDirectory, "AnnieCI")
        check(directory.mkdirs() || directory.isDirectory) {
            "Could not create app-specific screenshot directory: ${directory.absolutePath}"
        }
        val file = File(directory, "annie-ci-$safe.png")
        FileOutputStream(file).use { output ->
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Could not encode emulator screenshot: $safe"
            }
        }
        screenshot.recycle()
        return Uri.fromFile(file)
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "annie-ci-$safe.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AnnieCI")
    }
    val uri = checkNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
        "Could not allocate MediaStore image for emulator screenshot: $safe"
    }
    try {
        val output = checkNotNull(context.contentResolver.openOutputStream(uri, "w")) {
            "Could not open emulator screenshot output: $safe"
        }
        output.use {
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) {
                "Could not encode emulator screenshot: $safe"
            }
        }
    } catch (error: Throwable) {
        context.contentResolver.delete(uri, null, null)
        throw error
    }
    return uri
}
