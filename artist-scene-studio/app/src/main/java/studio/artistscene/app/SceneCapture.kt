package studio.artistscene.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Captures the activity window so Filament's SurfaceView content is included. PixelCopy is
 * available from API 26, matching Mise's minimum SDK.
 */
internal suspend fun captureWindowPng(activity: Activity, destination: Uri) {
    val decor = activity.window.decorView
    val width = decor.width
    val height = decor.height
    require(width > 0 && height > 0) { "Window has no drawable size" }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        val result = suspendCoroutine<Int> { continuation ->
            PixelCopy.request(
                activity.window,
                bitmap,
                { copyResult -> continuation.resume(copyResult) },
                Handler(Looper.getMainLooper()),
            )
        }
        check(result == PixelCopy.SUCCESS) { "PixelCopy failed with code $result" }

        withContext(Dispatchers.IO) {
            activity.contentResolver.openOutputStream(destination, "w")?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "PNG encoder did not complete"
                }
                output.flush()
            } ?: error("Could not open export destination")
        }
    } finally {
        bitmap.recycle()
    }
}

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun scenePngFilename(projectName: String): String {
    val base = projectName.trim()
        .ifBlank { "Mise Scene" }
        .replace(Regex("[^A-Za-z0-9._ -]+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(60)
        .ifBlank { "Mise Scene" }
    return "$base.png"
}
