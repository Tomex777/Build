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
    @Test fun actualDownloadPersistsBytesAndCanBeReadThroughReturnedUri() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "annie-gecko-verify-${UUID.randomUUID()}.txt"
        val sample = ByteArray(128 * 1024) { index -> (index % 239).toByte() }
        val updates = mutableListOf<Long>()
        val result = kotlinx.coroutines.runBlocking {
            AnnieGeckoDownloadStore.saveResponse(
                context, name, "text/plain", sample.inputStream(),
                onProgress = { updates.add(it) },
            )
        }
        try {
            val stored = context.contentResolver.openInputStream(result)?.use { it.readBytes() }
            org.junit.Assert.assertArrayEquals("The downloaded bytes were corrupted", sample, stored)
            assertEquals(sample.size.toLong(), updates.last())
            assertTrue(result.scheme == "content")
        } finally {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.delete(result, null, null)
            } else {
                val folder = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                java.io.File(folder, "Annie/$name").delete()
            }
        }
    }

    @Test fun storageFailureDoesNotLeaveIncompleteDownload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "annie-gecko-failure-${UUID.randomUUID()}.txt"
        val throwing = object : java.io.InputStream() {
            override fun read(): Int = throw java.io.IOException("fixture network interruption")
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                throw java.io.IOException("fixture network interruption")
        }
        val failure = runCatching {
            kotlinx.coroutines.runBlocking {
                AnnieGeckoDownloadStore.saveResponse(context, name, "text/plain", throwing)
            }
        }.exceptionOrNull()
        assertTrue("Expected an IO error from the fixture", failure is java.io.IOException)
        if (android.os.Build.VERSION.SDK_INT < 29) {
            val folder = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            assertTrue(!java.io.File(folder, "Annie/$name").exists())
        } else {
            val resolver = context.contentResolver
            resolver.query(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME),
                "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(name), null,
            )?.use { cursor ->
                assertTrue("Failed download was left in MediaStore", !cursor.moveToFirst())
            }
        }
    }

}
