package studio.artistscene.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val MAX_WINDOW_COPY_ATTEMPTS = 4
private const val COPY_RETRY_DELAY_MS = 120L

/**
 * Filament renders asynchronously after the Android document picker closes. A PixelCopy request
 * can temporarily report SOURCE_NO_DATA or TIMEOUT while the surface is being recreated. Retry
 * only those recoverable results, with a strict bound: permanent errors should be reported rather
 * than hidden behind an indefinite export spinner.
 *
 * [attempt] is zero-based so a failure on the last attempt never schedules another request.
 */
internal fun shouldRetryWindowPixelCopy(result: Int, attempt: Int): Boolean =
    attempt in 0 until (MAX_WINDOW_COPY_ATTEMPTS - 1) &&
        (result == PixelCopy.ERROR_SOURCE_NO_DATA || result == PixelCopy.ERROR_TIMEOUT)

private suspend fun copyCurrentWindowFrame(activity: Activity, bitmap: Bitmap) {
    for (attempt in 0 until MAX_WINDOW_COPY_ATTEMPTS) {
        // Keep the bitmap alive until PixelCopy completes. suspendCoroutine deliberately waits
        // for the native callback even if the calling export coroutine is cancelled.
        val result = suspendCoroutine<Int> { continuation ->
            PixelCopy.request(
                activity.window,
                bitmap,
                { copyResult -> continuation.resume(copyResult) },
                Handler(Looper.getMainLooper()),
            )
        }
        if (result == PixelCopy.SUCCESS) return
        check(shouldRetryWindowPixelCopy(result, attempt)) {
            "PixelCopy failed with code $result after ${attempt + 1} attempt(s)"
        }
        delay(COPY_RETRY_DELAY_MS * (attempt + 1))
    }
}

/**
 * Captures the *actual Filament-rendered activity window* while StudioScreen is in clean
 * reference mode (the editor/pose controls are hidden). PixelCopy is API 26+, Mise's minSdk.
 *
 * Do not draw the Compose UI to a Canvas: a SceneView/Filament texture is not captured that way.
 */
internal suspend fun captureWindowPng(activity: Activity, destination: Uri) {
    val decor = activity.window.decorView
    val width = decor.width
    val height = decor.height
    require(width > 0 && height > 0) { "Window has no drawable size" }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        copyCurrentWindowFrame(activity, bitmap)

        withContext(Dispatchers.IO) {
            // "wt" explicitly truncates a preexisting document. Android SAF's plain "w" mode
            // does not consistently truncate across DocumentProviders, leaving stale PNG bytes.
            activity.contentResolver.openOutputStream(destination, "wt")?.use { output ->
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
        .replace(Regex("(?i)\\.png$"), "")
        .ifBlank { "Mise Scene" }
        .replace(Regex("[^A-Za-z0-9._ -]+"), "")
        .replace(Regex("\\s+"), " ")
        .trim().trim('.').trim()
        .take(60)
        .ifBlank { "Mise Scene" }
    return "$base.png"
}
