package com.tomex777.annie

import android.os.Build
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
class AnnieGeckoBrowserEndToEndTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun geckoNavigatesBackAndDownloadsTheActualResponseForOfflineOpening() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val server = GeckoLocalFixture()
        lateinit var model: AnnieGeckoBrowserModel
        var initialized = false
        var entry: AnnieGeckoDownloadEntry? = null
        try {
            compose.runOnUiThread {
                model = AnnieGeckoBrowserModel(context)
                initialized = true
            }
            val tab = model.active!!
            compose.setContent {
                AndroidView(
                    factory = { GeckoView(it).apply { setSession(tab.session) } },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { it.releaseSession() },
                )
            }
            compose.runOnUiThread { assertTrue(model.load(tab, server.baseUrl + "/one")) }
            compose.waitUntil(30_000) { tab.title == "Gecko one" && !tab.loading }
            compose.runOnUiThread { assertTrue(model.load(tab, server.baseUrl + "/two")) }
            compose.waitUntil(20_000) { tab.title == "Gecko two" && tab.canBack && !tab.loading }
            compose.runOnUiThread { tab.session.goBack() }
            compose.waitUntil(20_000) { tab.url == server.baseUrl + "/one" && tab.canForward && !tab.loading }
            compose.runOnUiThread { tab.session.goForward() }
            compose.waitUntil(20_000) { tab.title == "Gecko two" && !tab.loading }
            compose.runOnUiThread { assertTrue(model.load(tab, server.baseUrl + "/download")) }
            compose.waitUntil(30_000) {
                entry = AnnieGeckoDownloads.current.firstOrNull { it.name == server.name }
                entry?.status == AnnieGeckoDownloadStatus.COMPLETED || entry?.status == AnnieGeckoDownloadStatus.FAILED
            }
            assertEquals("Live Gecko response download failed: ${entry?.reason}",
                AnnieGeckoDownloadStatus.COMPLETED, entry!!.status)
            assertTrue("Gecko did not request the controlled response", server.downloadRequested.get())
            val uri = android.net.Uri.parse(entry!!.contentUri)
            assertArrayEquals("Saved bytes differ from the original Gecko response", server.bytes,
                context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            server.close()
            val restored = AnnieGeckoDownloadHistory(context).read().first { it.id == entry!!.id }
            assertEquals(entry!!.contentUri, restored.contentUri)
            // Removal is metadata-only and must not erase a user's completed file.
            AnnieGeckoDownloads.forget(entry!!.id)
            compose.waitUntil(5_000) { AnnieGeckoDownloads.current.none { it.id == entry!!.id } }
            assertFalse(AnnieGeckoDownloadHistory(context).read().any { it.id == entry!!.id })
            assertArrayEquals(server.bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        } finally {
            if (initialized) compose.runOnUiThread { model.destroy() }
            entry?.let { completed ->
                AnnieGeckoDownloads.forget(completed.id)
                if (completed.contentUri != null && Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.delete(android.net.Uri.parse(completed.contentUri), null, null)
                } else {
                    val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        ?.let { File(it, "Annie") } ?: File(context.filesDir, "downloads/Annie")
                    File(root, server.name).delete()
                }
            }
            server.close()
        }
    }
}

private class GeckoLocalFixture : AutoCloseable {
    val name = "annie-gecko-live-${UUID.randomUUID()}.bin"
    val bytes = ByteArray(64 * 1024) { (it % 251).toByte() }
    val downloadRequested = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)
    private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val baseUrl = "http://127.0.0.1:${server.localPort}"
    private val worker = Thread {
        while (!stopped.get()) {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val input = socket.getInputStream().bufferedReader()
                    val path = input.readLine()?.split(' ')?.getOrNull(1).orEmpty()
                    while (!input.readLine().isNullOrBlank()) { }
                    val download = path == "/download"
                    val body = if (download) bytes else
                        "<html><head><title>Gecko ${if (path == "/one") "one" else "two"}</title></head><body>Local browser acceptance</body></html>".toByteArray()
                    if (download) downloadRequested.set(true)
                    val type = if (download) "application/octet-stream" else "text/html; charset=utf-8"
                    val disposition = if (download) "Content-Disposition: attachment; filename=\"$name\"\r\n" else ""
                    socket.getOutputStream().use { output ->
                        output.write(("HTTP/1.1 200 OK\r\nContent-Type: $type\r\n" + disposition +
                            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
                        output.write(body)
                        output.flush()
                    }
                }
            } catch (problem: java.io.IOException) {
                if (!stopped.get()) android.util.Log.w("AnnieGeckoFixture", "Fixture request failed", problem)
            }
        }
    }.apply { isDaemon = true; start() }

    override fun close() {
        stopped.set(true)
        server.close()
        worker.join(1_000)
    }
}
