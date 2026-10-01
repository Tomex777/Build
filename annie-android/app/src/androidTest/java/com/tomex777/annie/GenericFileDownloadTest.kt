package com.tomex777.annie

import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithTag
import kotlinx.coroutines.runBlocking
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
            compose.onNodeWithTag("download_file_${completed.id}").assertExists()
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
                compose.onNodeWithTag("download_file_${completed.id}").assertExists()
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

    @Test fun enabledPackageFileMessageDownloadsThroughTheRealChat() {
        BinaryServer().use { server ->
            val files = ScriptFiles(context)
            val script = files.importJavaScript("fileproof.js", """
                annie.commands.register({name: "fileproof", description: "File proof", async execute(ctx) {
                    return annie.messages.file({filename: "package.blorp", url: "${server.url("sample.blorp")}", size: 65536});
                }});
            """.trimIndent())
            files.setEnabled(script.nameWithoutExtension, true)
            try {
                compose.setContent { AnnieTheme { AnnieChat() } }
                compose.onNodeWithTag("composer_input").performTextInput("/fileproof")
                compose.waitUntil(10000) { compose.onAllNodesWithTag("slash_command_/fileproof").fetchSemanticsNodes().isNotEmpty() }
                saveEmulatorScreenshot("package-file-slash-command")
                compose.onNodeWithTag("send_message").performClick()
                compose.waitUntil(10000) { compose.onAllNodesWithTag("script_file_message").fetchSemanticsNodes().isNotEmpty() }
                saveEmulatorScreenshot("package-file-message")
                compose.onNodeWithTag("script_file_download").performClick()
                val deadline = System.currentTimeMillis() + 20000
                var saved: DownloadItem? = null
                while (System.currentTimeMillis() < deadline) {
                    saved = DownloadStore.read(context).firstOrNull { it.filename == "package.blorp" && it.state == DownloadState.COMPLETE }
                    if (saved != null) break
                    Thread.sleep(100)
                }
                val completed = requireNotNull(saved) { "FILE message did not reach the durable queue" }
                assertBytes(completed)
                compose.onNodeWithTag("chat_history_button").performClick()
                compose.onNodeWithTag("drawer_downloads").performClick()
                compose.onNodeWithTag("download_file_${completed.id}").assertExists()
                compose.onNodeWithText("package.blorp").assertExists()
                saveEmulatorScreenshot("package-file-in-downloads")
                DownloadTransferService.remove(context, completed)
            } finally { files.deleteProject(script.nameWithoutExtension) }
        }
    }

    @Test fun expiredGenericSourceRefreshesThroughItsEnabledPackageAndKeepsPartialBytes() {
        BinaryServer().use { server ->
            val files = ScriptFiles(context)
            val script = files.importJavaScript("refreshproof.js", """
                annie.actions.register("refresh-file", async ctx => annie.messages.file({url: "${server.url("fresh.blorp")}"}));
            """.trimIndent())
            val item = item(server.url("expired.blorp")).copy(ownerScriptId = script.nameWithoutExtension, refreshAction = "refresh-file")
            try {
                assertNull(runBlocking { ScriptDownloadSourceRefresh.refresh(context, item) })
                files.setEnabled(script.nameWithoutExtension, true)
                File(context.filesDir, "download-partials/${item.id}.part").apply { parentFile!!.mkdirs(); writeBytes(BinaryServer.bytes.copyOfRange(0, 4096)) }
                context.getSharedPreferences("annie_download_transfer_v1", 0).edit().putString("validator_${item.id}", "\"v1\"").commit()
                val completed = download(item)
                assertTrue(server.ranges.get() > 0)
                assertBytes(completed)
                assertTrue(completed.sourceUrl.endsWith("fresh.blorp"))
                File(completed.localPath).delete(); DownloadStore.remove(context, completed.id)
            } finally { files.deleteProject(script.nameWithoutExtension) }
        }
    }

    @Test fun contentUriSourceCopiesUnknownBytesWithoutConversion() {
        BinaryServer().use { server ->
            val original = download(item(server.url("original.blorp")))
            try {
                val uri = DownloadedFileRouter.viewIntent(context, original).data!!
                val copied = download(item(uri.toString()).copy(filename = "copy.blorp"))
                assertEquals("copy.blorp", copied.filename)
                assertBytes(copied)
                File(copied.localPath).delete(); DownloadStore.remove(context, copied.id)
            } finally { File(original.localPath).delete(); DownloadStore.remove(context, original.id) }
        }
    }

    @Test fun fileCategorySelectsNativeHandlersOnlyForSupportedFormats() {
        val base = item("https://files.example/")
        assertEquals(DownloadOpenRoute.VIDEO, DownloadedFileRouter.route(base.copy(localPath = "/tmp/movie.mkv")))
        assertEquals(DownloadOpenRoute.MUSIC, DownloadedFileRouter.route(base.copy(localPath = "/tmp/song.mp3")))
        assertEquals(DownloadOpenRoute.SCRIPT, DownloadedFileRouter.route(base.copy(localPath = "/tmp/code.js")))
        assertEquals(DownloadOpenRoute.PACKAGE, DownloadedFileRouter.route(base.copy(localPath = "/tmp/package.annie")))
        assertEquals(DownloadOpenRoute.MANGA, DownloadedFileRouter.route(base.copy(localPath = "/tmp/book.cbz")))
        assertEquals(DownloadOpenRoute.EXTERNAL, DownloadedFileRouter.route(base.copy(localPath = "/tmp/sample.zip")))
        assertEquals(DownloadOpenRoute.EXTERNAL, DownloadedFileRouter.route(base.copy(localPath = "/tmp/sample.blorp")))
    }

    @Test fun downloadedScriptOpensInStudioStagedDisabledWithoutExecution() {
        val name = "downloadproof_${System.nanoTime()}"
        val source = "throw new Error('Imported source must not execute');"
        val file = File(context.cacheDir, "$name.js").apply { writeText(source) }
        val workspace = ScriptWorkspace(context)
        val pending = androidx.compose.runtime.mutableStateOf<File?>(file)
        try {
            compose.setContent {
                AnnieTheme {
                    ScriptStudioSheet(workspace, {}, importFile = pending.value, onFileImportOpened = { pending.value = null })
                }
            }
            compose.waitUntil(10000) { File(workspace.files.root, "$name.js").isFile && pending.value == null }
            compose.onNodeWithTag("script_studio").assertExists()
            assertFalse("Downloaded script was enabled without review", workspace.files.isEnabled(name))
            assertEquals(source, File(workspace.files.root, "$name.js").readText())
            assertEquals(source, file.readText())
            saveEmulatorScreenshot("downloaded-script-staged-in-studio")
        } finally {
            workspace.files.deleteProject(name)
            workspace.close()
            file.delete()
        }
    }

    @Test fun knownMusicDownloadsExactlyAndPlaysFromTheSavedFile() {
        val samples = 16000 * 8
        val tone = java.nio.ByteBuffer.allocate(44 + samples * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(16000); putInt(32000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples * 2)
            for (i in 0 until samples) putShort((kotlin.math.sin(2 * Math.PI * 440 * i / 16000) * 6000).toInt().toShort())
        }.array()
        BinaryServer(payload = tone).use { server ->
            server.mime = "audio/wav"
            val completed = download(item(server.url("tone.wav")).copy(kind = DownloadMediaKind.MUSIC))
            val bound = CountDownLatch(1)
            val service = AtomicReference<MusicPlaybackService?>()
            val connection = object : android.content.ServiceConnection {
                override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
                    service.set((binder as? MusicPlaybackService.LocalBinder)?.service()); bound.countDown()
                }
                override fun onServiceDisconnected(name: android.content.ComponentName?) = Unit
            }
            try {
                assertEquals("tone.wav", completed.filename)
                assertArrayEquals(tone, File(completed.localPath).readBytes())
                assertEquals(DownloadOpenRoute.MUSIC, DownloadedFileRouter.route(completed))
                compose.setContent {
                    DownloadsManagerContent(listOf(completed), {}, { _, _ -> }, onPlay = {
                        MusicPlaybackService.start(context, MusicPlaybackService.ACTION_PLAY) {
                            putExtra(MusicPlaybackService.EXTRA_STREAM, android.net.Uri.fromFile(File(it.localPath)).toString())
                            putExtra(MusicPlaybackService.EXTRA_TITLE, "Downloaded tone")
                        }
                    })
                }
                compose.onNodeWithTag("download_group_MUSIC").performClick()
                compose.onNodeWithTag("download_action_play").performClick()
                assertTrue(context.bindService(Intent(context, MusicPlaybackService::class.java), connection, android.content.Context.BIND_AUTO_CREATE))
                assertTrue(bound.await(5, TimeUnit.SECONDS))
                val deadline = System.currentTimeMillis() + 8000
                while (System.currentTimeMillis() < deadline && service.get()?.currentSnapshot()?.playing != true) Thread.sleep(100)
                assertTrue("Downloaded music did not play", service.get()?.currentSnapshot()?.playing == true)
                assertTrue(service.get()!!.currentSnapshot().durationMs >= 7000)
                saveEmulatorScreenshot("downloaded-music-playing")
            } finally {
                MusicPlaybackService.start(context, MusicPlaybackService.ACTION_STOP)
                runCatching { context.unbindService(connection) }
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

    private class BinaryServer(val dropOnce: Boolean = false, val rangeMode: String = "normal", val payload: ByteArray = bytes) : AutoCloseable {
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
            val requestLine = reader.readLine() ?: return@use
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
            }
            if (requestLine.contains("/expired.blorp")) {
                it.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                it.getOutputStream().flush()
                return@use
            }
            val requested = headers["range"]?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull()
            if (requested != null) ranges.incrementAndGet()
            val partial = requested != null && rangeMode != "ignore"
            val start = if (!partial) 0 else if (rangeMode == "mismatch") (requested!! + 7).coerceAtMost(payload.lastIndex) else requested!!
            val body = payload.copyOfRange(start, payload.size)
            val output = it.getOutputStream()
            val response = buildString {
                append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                append("Content-Length: ${body.size}\r\nETag: ${if (rangeMode == "changed") "\"v2\"" else "\"v1\""}\r\n")
                if (partial) append("Content-Range: bytes $start-${payload.lastIndex}/${payload.size}\r\n")
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
