package com.tomex777.annie.processdeath

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tomex777.annie.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenericFileRestoreTest {
    @Test fun verifyFilesAfterHostForceStopAndColdLaunch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val complete = requireNotNull(DownloadStore.find(context, "ci-generic-completed"))
        assertEquals("sample.blorp", complete.filename)
        assertEquals(DownloadState.COMPLETE, complete.state)
        assertPayload(File(complete.localPath), 65536)
        assertEquals("content", DownloadedFileRouter.viewIntent(context, complete).data?.scheme)
        DownloadTransferService.restore(context)
        val deadline = System.currentTimeMillis() + 45000
        while (System.currentTimeMillis() < deadline && DownloadStore.find(context, "ci-generic-active")?.state != DownloadState.COMPLETE) Thread.sleep(250)
        val resumed = requireNotNull(DownloadStore.find(context, "ci-generic-active"))
        assertEquals("Interrupted generic download failed to restore: ${resumed.failureReason}", DownloadState.COMPLETE, resumed.state)
        assertEquals("resume.blorp", resumed.filename)
        assertPayload(File(resumed.localPath), 4 * 1024 * 1024)
        println("GENERIC_RESTART_PROOF: completed file intact; active direct file restored; SHA-256 matched")
    }
}

internal fun seedGenericFiles() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val item = DownloadItem(
        id = "ci-generic-completed", canonicalTitleId = "ci-generic-completed", sourceId = "ci", sourceName = "File source",
        kind = DownloadMediaKind.FILE, title = "Restart file proof", unitTitle = "sample.blorp", state = DownloadState.QUEUED,
        sourceUrl = "http://10.0.2.2:18765/sample.blorp",
    )
    val latest = AtomicReference(item)
    val done = CountDownLatch(1)
    val downloader = AnnieMediaDownloader(context) { changed ->
        latest.set(changed); DownloadStore.update(context, changed)
        if (changed.state in setOf(DownloadState.COMPLETE, DownloadState.FAILED)) done.countDown()
    }
    try {
        downloader.enqueue(item)
        assertTrue(done.await(20, TimeUnit.SECONDS))
        assertEquals(DownloadState.COMPLETE, latest.get().state)
        assertPayload(File(latest.get().localPath), 65536)
    } finally { downloader.close() }
    val active = item.copy(id = "ci-generic-active", canonicalTitleId = "ci-generic-active", title = "Resume file proof", sourceUrl = "http://10.0.2.2:18765/resume.blorp")
    DownloadTransferService.enqueue(context, active)
    val deadline = System.currentTimeMillis() + 15000
    while (System.currentTimeMillis() < deadline) {
        val saved = DownloadStore.find(context, active.id)
        if (saved != null && saved.bytesDone >= 32768 && saved.state == DownloadState.DOWNLOADING) {
            assertTrue("Seed transfer completed before process termination", saved.bytesDone < saved.bytesTotal)
            assertTrue(File(context.filesDir, "download-partials/${active.id}.part").length() > 0)
            return
        }
        Thread.sleep(100)
    }
    fail("Could not seed an in-progress generic download")
}

private fun assertPayload(file: File, length: Int) {
    assertTrue(file.isFile)
    assertEquals(length.toLong(), file.length())
    val expected = ByteArray(length) { ((it * 37 + 11) and 255).toByte() }
    assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(expected), MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
}
