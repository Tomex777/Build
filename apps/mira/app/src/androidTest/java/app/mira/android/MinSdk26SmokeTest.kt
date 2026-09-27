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
class MinSdk26SmokeTest {
    @Test
    fun api26CanInitializeMira() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = context.applicationContext as MiraApplication
        assertEquals(26, Build.VERSION.SDK_INT)
        assertEquals("app.mira.android", context.packageName)
        assertEquals(2, application.sources.size)
        assertTrue(application.sources.map { it.metadata.id }.distinct().size == application.sources.size)
    }
}
