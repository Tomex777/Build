package com.tomex777.annie

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry

internal fun saveEmulatorScreenshot(name: String): Uri {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val safe = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
        "Android could not capture the current emulator display: $safe"
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
