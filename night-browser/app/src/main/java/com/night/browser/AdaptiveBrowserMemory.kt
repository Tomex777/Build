package com.night.browser

import android.content.ComponentCallbacks2
import android.content.res.Configuration

/**
 * Small, conservative memory-pressure policy for bitmap tab previews.
 * GeckoSession lifecycle stays under Agent 3/4 ownership: do not tear down
 * sessions or fake tab restoration when Android signals memory pressure.
 */
class AdaptiveBrowserMemory(
    private val tabs: () -> List<BrowserTab>,
    private val activeId: () -> String
) : ComponentCallbacks2 {
    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    override fun onLowMemory() {
        trimPreviews(dropAllInactive = true)
    }

    @Deprecated("Android framework callback, still used on API 26+")
    override fun onTrimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ->
                trimPreviews(dropAllInactive = true)
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ->
                trimPreviews(dropAllInactive = false)
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ->
                trimPreviews(dropAllInactive = false)
        }
    }

    private fun trimPreviews(dropAllInactive: Boolean) {
        val inactive = tabs().filter { it.id != activeId() }
        val discard = if (dropAllInactive) inactive else inactive.dropLast(2)
        // Clearing the observable reference lets Compose and the GC release
        // the bitmap safely. Bitmap.recycle() would risk an in-flight draw.
        discard.forEach { it.thumbnail = null }
    }
}
