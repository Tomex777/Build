package com.tomex777.annie

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal fun saveEmulatorScreenshot(name: String): File {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val safe = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    val file = File(context.filesDir, "ci-screenshot-$safe.png")
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
