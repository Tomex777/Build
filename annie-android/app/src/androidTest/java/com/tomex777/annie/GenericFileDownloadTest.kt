package com.tomex777.annie

import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenericFileDownloadTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun binaryMatrixKeepsNamesMimeBytesAndDurableCompletedState() {
        BinaryServer().use { server ->
            val cases = listOf(
                Triple("sample.pdf", "application/pdf", "sample.pdf"),
                Triple("sample.zip", "application/zip", "sample.zip"),
                Triple("sample.apk", "application/vnd.android.package-archive", "sample.apk"),
                Triple("sample.blorp", "application/octet-stream", "sample.blorp"),
                Triple("missing.epub", "", "missing.epub"),
                Triple("generic.zip", "application/octet-stream", "generic.zip"),
                Triple("disposition", "application/octet-stream", "server.blorp"),
                Triple("sample.docx", "", "sample.docx"),
                Triple("sample.7z", "", "sample.7z"),
                Triple("sample.srt", "", "sample.srt"),
            )
            for ((path, mime, filename) in cases) {
                server.mime = mime
                server.disposition = if (path == "disposition") "attachment; filename=\"server.blorp\"" else null
                val item = item(server.url(path))
                val completed = download(item)
                assertEquals(DownloadMediaKind.FILE, completed.kind)
                assertEquals(filename, completed.filename)
                assertEquals(filename, File(completed.localPath).name)
                assertBytes(completed)
                assertNotEquals("video", File(completed.localPath).extension)
                assertEquals(completed, DownloadStore.find(context, item.id))
                val intent = DownloadedFileRouter.viewIntent(context, completed)
                assertEquals(Intent.ACTION_VIEW, intent.action)
                assertEquals("content", intent.data?.scheme)
                assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                context.contentResolver.openInputStream(requireNotNull(intent.data))!!.use { assertArrayEquals(BinaryServer.bytes, it.readBytes()) }
                assertEquals(DownloadOpenRoute.EXTERNAL, DownloadedFileRouter.route(completed))
                val share = DownloadedFileRouter.shareIntent(context, completed)
                assertEquals(Intent.ACTION_SEND, share.action)
                assertEquals(intent.type, share.type)
                File(completed.localPath).delete(); DownloadStore.remove(context, completed.id)
            }
        }
    }

    @Test fun interruptedUnknownFileResumesWithoutDuplicateBytes() {
        BinaryServer(dropOnce = true).use { server ->
            val finished = download(item(server.url("resume.blorp")))
            assertTrue("Interrupted transfer never requested a range", server.ranges.get() > 0)
            assertBytes(finished)
            File(finished.localPath).delete(); DownloadStore.remove(context, finished.id)
        }
    }

    @Test fun ignoredAndMismatchedRangesRestartWithoutCorruption() {
        for (mode in listOf("ignore", "mismatch", "changed")) BinaryServer(rangeMode = mode).use { server ->
            val item = item(server.url("$mode.blorp"))
            File(context.filesDir, "download-partials/${item.id}.part").apply { parentFile!!.mkdirs(); writeBytes(BinaryServer.bytes.copyOfRange(0, 4096)) }
            context.getSharedPreferences("annie_download_transfer_v1", 0).edit().putString("validator_${item.id}", "\"v1\"").commit()
            val finished = download(item)
            assertTrue(server.ranges.get() > 0)
            assertBytes(finished)
            File(finished.localPath).delete(); DownloadStore.remove(context, finished.id)
        }
    }

    @Test fun unknownCompletedFileHasOpenShareExportAndConfirmedDelete() {
        BinaryServer().use { server ->
            val completed = download(item(server.url("sample.blorp")))
            var opened = false
            compose.setContent {
                DownloadsManagerContent(listOf(completed), {}, { _, _ -> }, onPlay = { opened = true })
            }
            compose.onNodeWithTag("download_group_FILE").performClick()
            compose.onNodeWithText("sample.blorp").assertExists()
            saveEmulatorScreenshot("generic-file-completed")
            compose.onNodeWithTag("download_action_open").performClick()
            assertTrue(opened)
            compose.onNodeWithTag("download_action_more").performClick()
            compose.onNodeWithText("Share").assertExists()
            compose.onNodeWithText("Save a copy").assertExists()
            compose.onNodeWithText("Delete").assertExists()
            saveEmulatorScreenshot("generic-file-actions")
            assertBytes(completed)
            File(completed.localPath).delete(); DownloadStore.remove(context, completed.id)
        }
    }

    @Test fun unknownFileOpenUsesRealAndroidChooserAndReadGrant() {
        BinaryServer().use { server ->
            val completed = download(item(server.url("sample.blorp")))
            val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            try {
                compose.setContent {
                    DownloadsManagerContent(listOf(completed), {}, { _, _ -> }, onPlay = { DownloadedFileRouter.openExternal(context, it) })
                }
                compose.onNodeWithTag("download_group_FILE").performClick()
                compose.onNodeWithTag("download_action_open").performClick()
                assertTrue("Android resolver was not shown", device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.textContains("Annie file proof viewer")), 8000))
                saveEmulatorScreenshot("unknown-file-open-with")
                device.findObject(androidx.test.uiautomator.By.textContains("Annie file proof viewer")).click()
                device.findObject(androidx.test.uiautomator.By.text("Just once"))?.click()
                assertTrue("External recipient could not read the granted file", device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.textContains("File opened safely")), 8000))
                val expected = MessageDigest.getInstance("SHA-256").digest(BinaryServer.bytes).joinToString("") { "%02x".format(it) }
                assertTrue(device.hasObject(androidx.test.uiautomator.By.textContains(expected)))
                saveEmulatorScreenshot("unknown-file-external-read-grant")
                device.pressBack()
                val noHandler = completed.copy(sourceMimeType = "application/x-annie-unhandled-proof")
                val launched = AtomicReference(true)
                compose.runOnIdle { launched.set(DownloadedFileRouter.openExternal(context, noHandler)) }
                assertFalse("No-handler open should report failure independently of the transfer", launched.get())
                assertBytes(completed)
                assertEquals(DownloadState.COMPLETE, DownloadStore.find(context, completed.id)?.state)
                saveEmulatorScreenshot("unknown-file-no-handler-intact")
            } finally {
                File(completed.localPath).delete(); DownloadStore.remove(context, completed.id)
            }
        }
    }

    private fun item(url: String) = DownloadItem(
        id = "generic-${System.nanoTime()}", canonicalTitleId = "generic-${System.nanoTime()}", sourceId = "binary-proof",
        sourceName = "File source", kind = DownloadMediaKind.FILE, title = "File download", unitTitle = "File", state = DownloadState.QUEUED,
        sourceUrl = url,
    )

    private fun download(item: DownloadItem): DownloadItem {
        val latest = AtomicReference(item)
        val completed = CountDownLatch(1)
        val downloader = AnnieMediaDownloader(context) { changed ->
            latest.set(changed); DownloadStore.update(context, changed)
            if (changed.state in setOf(DownloadState.COMPLETE, DownloadState.FAILED)) completed.countDown()
        }
        try {
            downloader.enqueue(item)
            assertTrue("Transfer timed out: ${latest.get()}", completed.await(25, TimeUnit.SECONDS))
            assertEquals("Transfer failed: ${latest.get().failureReason}", DownloadState.COMPLETE, latest.get().state)
            return latest.get()
        } finally { downloader.close() }
    }

    private fun assertBytes(item: DownloadItem) {
        assertEquals(BinaryServer.bytes.size.toLong(), File(item.localPath).length())
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(BinaryServer.bytes), MessageDigest.getInstance("SHA-256").digest(File(item.localPath).readBytes()))
    }

    private class BinaryServer(val dropOnce: Boolean = false, val rangeMode: String = "normal") : AutoCloseable {
        val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newCachedThreadPool()
        val ranges = AtomicInteger()
        val deliveries = AtomicInteger()
        @Volatile var mime = "application/octet-stream"
        @Volatile var disposition: String? = null
        @Volatile var running = true
        init {
            executor.execute {
                while (running) runCatching { server.accept() }.onSuccess { socket -> executor.execute { runCatching { serve(socket) } } }
            }
        }
        fun url(path: String) = "http://127.0.0.1:${server.localPort}/$path"
        fun serve(socket: Socket) = socket.use {
            val reader = it.getInputStream().bufferedReader()
            reader.readLine() ?: return@use
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
            }
            val requested = headers["range"]?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull()
            if (requested != null) ranges.incrementAndGet()
            val partial = requested != null && rangeMode != "ignore"
            val start = if (!partial) 0 else if (rangeMode == "mismatch") (requested!! + 7).coerceAtMost(bytes.lastIndex) else requested!!
            val body = bytes.copyOfRange(start, bytes.size)
            val output = it.getOutputStream()
            val response = buildString {
                append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                append("Content-Length: ${body.size}\r\nETag: ${if (rangeMode == "changed") "\"v2\"" else "\"v1\""}\r\n")
                if (partial) append("Content-Range: bytes $start-${bytes.lastIndex}/${bytes.size}\r\n")
                if (mime.isNotEmpty()) append("Content-Type: $mime\r\n")
                disposition?.let { append("Content-Disposition: $it\r\n") }
                append("Connection: close\r\n\r\n")
            }
            output.write(response.toByteArray())
            val delivery = deliveries.incrementAndGet()
            // Downloader probes headers once before opening the byte stream.
            output.write(if (dropOnce && delivery == 2) body.copyOfRange(0, 4096) else body)
            output.flush()
        }
        override fun close() { running = false; server.close(); executor.shutdownNow() }
        companion object { val bytes = ByteArray(65536) { ((it * 37 + 11) and 255).toByte() } }
    }
}
