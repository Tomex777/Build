package com.tomex777.annie

import android.content.Context
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal fun saveEmulatorScreenshot(name: String): File {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context: Context = instrumentation.context
    val directory = File(context.filesDir, "ci-screenshots").apply {
        check(isDirectory || mkdirs()) { "Could not create screenshot directory: $absolutePath" }
    }
    val safe = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    val file = File(directory, "$safe.png")
    val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
        "Android could not capture the current emulator display: $safe"
    }
    file.outputStream().use { output ->
        check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            "Could not encode emulator screenshot: $safe"
        }
    }
    check(file.isFile && file.length() > 0L) {
        "Emulator screenshot was not written: $safe"
    }
    return file
}
