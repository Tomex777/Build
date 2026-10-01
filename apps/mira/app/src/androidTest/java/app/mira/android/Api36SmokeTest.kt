package app.mira.android

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Api36SmokeTest {
    @Test
    fun appStartsWithIndependentMiraState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = context.applicationContext as MiraApplication
        assertTrue(Build.VERSION.SDK_INT >= 36)
        assertEquals("app.mira.android", context.packageName)
        assertTrue(application.sources.any { it.metadata.capabilities.movies })
        assertTrue(application.sources.any { it.metadata.capabilities.series })
        assertTrue(application.downloadManager.statuses.value.values.none {
            it.sourceId.startsWith("app.nami")
        })
    }
}
