package com.tomex777.annie

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.activity.ComponentActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnieBrowserFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun browserMessageVerifiesCookieBackedHttpSessionAndExpandsToFullBrowser() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val server = ControlledBrowserServer()
        val name = "browserflow${System.nanoTime().toString().takeLast(8)}"
        val files = ScriptFiles(context)
        val entry = files.createScript(name)
        files.writeFile(
            name,
            entry.name,
            """
                |const sessionId = "$name.main";
                |annie.actions.register("verifySource", async payload => {
                |  if (!payload.cookieNames.includes("cf_clearance")) {
                |    return annie.browser.verification("failed", "Browser clearance cookie is missing.");
                |  }
                |  const response = await annie.http.request({
                |    url: "${server.baseUrl}/protected",
                |    browserSession: sessionId,
                |    timeoutMs: 5000
                |  });
                |  return response.ok
                |    ? annie.browser.verification("verified", "Protected source request succeeded.")
                |    : annie.browser.verification("failed", "Protected request returned " + response.status);
                |});
                |annie.commands.register({
                |  name: "$name",
                |  async execute() {
                |    return annie.browser.open({
                |      title: "Verify controlled source",
                |      sessionId,
                |      url: "${server.baseUrl}/verify",
                |      allowedHosts: ["127.0.0.1"],
                |      restricted: true,
                |      verifyAction: "verifySource",
                |      verifyLabel: "Verify",
                |      userAgent: "AnnieBrowserProof/1.0",
                |      javaScriptEnabled: true,
                |      thirdPartyCookies: true
                |    });
                |  }
                |});
            """.trimMargin(),
        )
        val monitor = instrumentation.addMonitor(AnnieBrowserActivity::class.java.name, null, false)
        var browserActivity: AnnieBrowserActivity? = null
        try {
            compose.setContent { AnnieTheme { AnnieChat() } }
            compose.onNodeWithTag("composer_input").performTextInput("/$name")
            compose.waitUntil(8_000) {
                compose.onAllNodesWithTag("slash_command_/$name").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("slash_command_/$name").performClick()
            compose.onNodeWithTag("send_message").performClick()
            compose.runOnIdle { compose.activity.currentFocus?.clearFocus() }
            compose.waitUntil(12_000) {
                compose.onAllNodesWithTag("annie_browser_message").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("Verification page did not load its controlled readiness callback", server.awaitReady())
            compose.waitUntil(8_000) {
                AnnieBrowserSessionStore.get(context, "$name.main")?.currentUrl?.endsWith("/ready") == true
            }
            compose.onNodeWithTag("annie_browser_verify").performClick()
            compose.waitUntil(12_000) {
                compose.onAllNodesWithText("Verified", substring = false).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("Protected request did not receive the browser session cookie and UA", server.protectedRequestWasValid())
            compose.onAllNodesWithText("Protected source request succeeded.", substring = false)[0].assertExists()

            val downloadFinished = CountDownLatch(1)
            val downloaded = AtomicReference<DownloadItem?>()
            val downloader = AnnieMediaDownloader(context) { item ->
                if (item.id == "${name}_download") {
                    downloaded.set(item)
                    if (item.state == DownloadState.COMPLETE || item.state == DownloadState.FAILED) {
                        downloadFinished.countDown()
                    }
                }
            }
            val downloadItem = DownloadItem(
                id = "${name}_download",
                canonicalTitleId = "browser-proof",
                sourceId = "controlled-source",
                sourceName = "Controlled source",
                kind = DownloadMediaKind.MOVIE,
                title = "Browser session proof",
                unitTitle = "Session identity",
                state = DownloadState.QUEUED,
                sourceUrl = "${server.baseUrl}/media.mp4",
                sourceMimeType = "video/mp4",
                browserSessionId = "$name.main",
            )
            try {
                downloader.enqueue(downloadItem)
                assertTrue("Downloader did not finish the controlled protected resource", downloadFinished.await(12, TimeUnit.SECONDS))
                assertEquals(DownloadState.COMPLETE, downloaded.get()?.state)
                assertTrue("Downloader did not share the browser cookie and User-Agent", server.downloadRequestWasValid())
                assertEquals("browser-session-video", java.io.File(downloaded.get()!!.localPath).readText())
            } finally {
                downloader.close()
                downloaded.get()?.localPath?.takeIf(String::isNotBlank)?.let { runCatching { java.io.File(it).delete() } }
            }
            saveEmulatorScreenshot("annie-browser-session-verified")

            compose.onNodeWithTag("annie_browser_fullscreen").performClick()
            browserActivity = instrumentation.waitForMonitorWithTimeout(monitor, 8_000) as? AnnieBrowserActivity
            assertNotNull("Inline browser did not open Annie's full-screen browser activity", browserActivity)
        } finally {
            browserActivity?.finish()
            instrumentation.removeMonitor(monitor)
            server.close()
            runCatching { files.deleteProject(name) }
            AnnieBrowserSessionStore.clear(context, "$name.main")
        }
    }

    private class ControlledBrowserServer : AutoCloseable {
        private val listener = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
        private val ready = CountDownLatch(1)
        private val protected = CountDownLatch(1)
        private val download = CountDownLatch(1)
        private val validProtectedRequest = AtomicBoolean(false)
        private val validDownloadRequest = AtomicBoolean(false)
        @Volatile private var closed = false
        val baseUrl = "http://127.0.0.1:${listener.localPort}"
        private val worker = Thread({
            while (!closed) {
                val socket = runCatching { listener.accept() }.getOrNull() ?: break
                Thread({
                    try {
                        socket.use(::respond)
                    } catch (error: SocketException) {
                        val message = error.message.orEmpty().lowercase()
                        val clientClosedEarly = "broken pipe" in message || "connection reset" in message || "socket closed" in message
                        if (!clientClosedEarly && !closed) throw error
                    }
                }, "annie-browser-fixture-client").apply {
                    isDaemon = true
                    start()
                }
            }
        }, "annie-browser-fixture").apply { isDaemon = true; start() }

        fun awaitReady(): Boolean = ready.await(10, TimeUnit.SECONDS)
        fun protectedRequestWasValid(): Boolean = protected.await(3, TimeUnit.SECONDS) && validProtectedRequest.get()
        fun downloadRequestWasValid(): Boolean = download.await(3, TimeUnit.SECONDS) && validDownloadRequest.get()

        private fun respond(socket: Socket) {
            socket.soTimeout = 5_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
            val requestLine = reader.readLine().orEmpty()
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
            }
            val path = requestLine.split(' ').getOrNull(1).orEmpty().substringBefore('?')
            val body: String
            val code: Int
            val contentType: String
            var extraHeaders = ""
            when (path) {
                "/verify" -> {
                    code = 200
                    contentType = "text/html; charset=utf-8"
                    body = """
                        |<!doctype html><html><head><title>Controlled verification</title>
                        |<script>
                        |document.cookie = "cf_clearance=annie-ok; Path=/; SameSite=Lax";
                        |setTimeout(() => { location.href = "/ready"; }, 100);
                        |</script></head><body>Challenge completed</body></html>
                    """.trimMargin()
                }
                "/ready" -> {
                    ready.countDown()
                    code = 200
                    contentType = "application/json"
                    body = "{}"
                }
                "/protected" -> {
                    val ok = headers["cookie"].orEmpty().contains("cf_clearance=annie-ok") &&
                        headers["user-agent"] == "AnnieBrowserProof/1.0"
                    validProtectedRequest.set(ok)
                    protected.countDown()
                    code = if (ok) 200 else 403
                    contentType = "text/plain"
                    body = if (ok) "clearance-ok" else "browser identity missing"
                }
                "/media.mp4" -> {
                    val ok = headers["cookie"].orEmpty().contains("cf_clearance=annie-ok") &&
                        headers["user-agent"] == "AnnieBrowserProof/1.0"
                    validDownloadRequest.set(ok)
                    download.countDown()
                    code = if (ok) 200 else 403
                    contentType = "video/mp4"
                    body = if (ok) "browser-session-video" else "browser identity missing"
                    extraHeaders = "Content-Disposition: attachment; filename=proof.mp4\r\n"
                }
                else -> { code = 404; contentType = "text/plain"; body = "not found" }
            }
            val bytes = body.toByteArray(Charsets.UTF_8)
            val status = if (code == 200) "OK" else if (code == 403) "Forbidden" else "Not Found"
            val output = socket.getOutputStream()
            output.write("HTTP/1.1 $code $status\r\nContent-Type: $contentType\r\n${extraHeaders}Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
            output.write(bytes)
            output.flush()
        }

        override fun close() {
            closed = true
            runCatching { listener.close() }
            runCatching { worker.join(1_000) }
        }
    }
}
