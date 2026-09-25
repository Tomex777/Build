package com.tomex777.annie

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

internal fun saveEmulatorScreenshot(name: String): File {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context: Context = instrumentation.targetContext
    val directory = File(context.filesDir, "ci-screenshots").apply { mkdirs() }
    val safe = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    val file = File(directory, "$safe.png")
    check(UiDevice.getInstance(instrumentation).takeScreenshot(file)) {
        "Could not capture emulator screenshot: $safe"
    }
    return file
}
