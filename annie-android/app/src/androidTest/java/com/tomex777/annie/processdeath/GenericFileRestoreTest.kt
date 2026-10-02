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
        // Drive the actual restored app, not a reconstructed download composable.
        // This completes the device flow: process death -> Downloads -> Open -> recipient.
        context.startActivity(android.content.Intent(context, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val navigation = androidx.test.uiautomator.By.desc("Open navigation")
        assertTrue("Restored Annie did not render navigation", device.wait(androidx.test.uiautomator.Until.hasObject(navigation), 10000))
        device.findObject(navigation).click()
        val downloads = androidx.test.uiautomator.By.text("Downloads")
        assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(downloads), 5000))
        device.findObject(downloads).click()
        val filename = androidx.test.uiautomator.By.text("sample.blorp")
        assertTrue("Completed filename was absent after process death", device.wait(androidx.test.uiautomator.Until.hasObject(filename), 8000))
        saveEmulatorScreenshot("generic-file-restored-in-downloads")
        var row = device.findObject(filename)
        var open = row.findObject(androidx.test.uiautomator.By.text("Open"))
        repeat(5) {
            if (open == null && row.parent != null) {
                row = row.parent
                open = row.findObject(androidx.test.uiautomator.By.text("Open"))
            }
        }
        assertNotNull("Restored file had no Open action", open)
        open!!.click()
        val option = androidx.test.uiautomator.By.textContains("Annie file proof viewer")
        val opened = androidx.test.uiautomator.By.textContains("File opened safely")
        val openDeadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < openDeadline && !device.hasObject(option) && !device.hasObject(opened)) Thread.sleep(100)
        assertTrue("Restored file did not reach Android opening", device.hasObject(option) || device.hasObject(opened))
        saveEmulatorScreenshot("generic-file-restored-open-with")
        if (!device.hasObject(opened)) {
            device.findObject(option).click()
            device.findObject(androidx.test.uiautomator.By.text("Just once"))?.click()
        }
        assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(opened), 8000))
        val hash = MessageDigest.getInstance("SHA-256").digest(File(complete.localPath).readBytes()).joinToString("") { "%02x".format(it) }
        assertTrue("External app read different bytes after restart", device.hasObject(androidx.test.uiautomator.By.textContains(hash)))
        saveEmulatorScreenshot("generic-file-restored-external-read")
        device.pressBack()
        assertPayload(File(complete.localPath), 65536)
        device.pressBack() // close Downloads
        // The chat navigation remains in the accessibility tree behind the sheet.
        // Wait for its dismissal before tapping through the outgoing window.
        assertTrue("Downloads did not close", device.wait(androidx.test.uiautomator.Until.gone(filename), 5000))
        device.waitForIdle()
        assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(navigation), 5000))
        device.findObject(navigation).click()
        val about = androidx.test.uiautomator.By.text("About")
        assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(about), 5000))
        device.findObject(about).click()
        assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("libVLC 3.7.4 · VideoLAN")), 5000))
        saveEmulatorScreenshot("annie-about-attribution")
        device.findObject(androidx.test.uiautomator.By.text("License")).click()
        assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("GNU LGPL 2.1")), 5000))
        saveEmulatorScreenshot("annie-offline-libvlc-license")
        device.pressBack()
        device.pressBack()
        println("GENERIC_RESTART_PROOF: completed and resumed files intact; SHA-256 matched; native Downloads Open granted bytes to external recipient")
    }
}

internal fun seedGenericFiles() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val item = DownloadItem(
        id = "ci-generic-completed", canonicalTitleId = "ci-generic-completed", sourceId = "ci", sourceName = "File source",
        kind = DownloadMediaKind.FILE, title = "Restart file proof", unitTitle = "sample.blorp", state = DownloadState.QUEUED,
        sourceUrl = "http://127.0.0.1:18765/sample.blorp",
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
        assertEquals("Host fixture download failed: ${latest.get().failureReason}", DownloadState.COMPLETE, latest.get().state)
        assertPayload(File(latest.get().localPath), 65536)
    } finally { downloader.close() }
    val active = item.copy(id = "ci-generic-active", canonicalTitleId = "ci-generic-active", title = "Resume file proof", sourceUrl = "http://127.0.0.1:18765/resume.blorp")
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

