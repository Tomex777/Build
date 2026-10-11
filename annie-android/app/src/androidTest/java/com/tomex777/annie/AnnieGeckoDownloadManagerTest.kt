package com.tomex777.annie

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AnnieGeckoDownloadManagerTest {
    @Test fun progressIsClampedAndSafeForUnknownLength() {
        val known = AnnieGeckoDownloadEntry("known", "test.png", "image/png", 200, bytesDone = 125)
        assertEquals(0.625f, requireNotNull(known.progress), 0.001f)
        assertEquals(1f, requireNotNull(known.copy(bytesDone = 300).progress), 0.001f)
        assertNull(known.copy(totalBytes = null).progress)
        assertNull(known.copy(totalBytes = 0).progress)
    }

    @Test fun cancelAndCompleteTransitionsDoNotReviveCancelledFile() {
        val id = UUID.randomUUID().toString()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        AnnieGeckoDownloads.started(id, "video.mp4", "video/mp4", 1024)
        instrumentation.waitForIdleSync()
        AnnieGeckoDownloads.track(id) { cancelled.set(true) }
        AnnieGeckoDownloads.progress(id, 200)
        instrumentation.waitForIdleSync()
        assertEquals(200L, AnnieGeckoDownloads.current.first { it.id == id }.bytesDone)
        AnnieGeckoDownloads.cancel(id)
        instrumentation.waitForIdleSync()
        assertTrue(cancelled.get())
        AnnieGeckoDownloads.completed(id, Uri.parse("content://media/download/42"))
        instrumentation.waitForIdleSync()
        assertEquals(AnnieGeckoDownloadStatus.CANCELLED,
            AnnieGeckoDownloads.current.first { it.id == id }.status)
    }
}
