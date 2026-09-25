package com.tomex777.annie

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.webkit.CookieManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaDownloadPlaybackTest {
    @get:Rule val compose = createComposeRule()

    private val servers = Collections.synchronizedList(mutableListOf<FixtureServer>())
    private val downloaders = Collections.synchronizedList(mutableListOf<AnnieMediaDownloader>())

    @After fun cleanup() {
        downloaders.forEach { runCatching { it.close() } }
        servers.forEach { runCatching { it.close() } }
        downloaders.clear()
        servers.clear()
    }

    @Test fun redirectedDirectMkvKeepsMkvAndPlaysOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val server = FixtureServer().also { servers += it }
        installCookie(server.baseUrl)

        val payload = JSONObject()
            .put("type", "video")
            .put("title", "Annie direct MKV proof")
            .put("url", server.url("/redirect-mkv"))
            .put("headers", JSONObject().put("Referer", REQUIRED_REFERER))
        val source = requireNotNull(ScriptVideoDownloadSource.from(payload))
        val item = downloadItem("direct-mkv", source)
        val latest = AtomicReference(item)
        val complete = CountDownLatch(1)
        val downloader = AnnieMediaDownloader(context) { changed ->
            latest.set(changed)
            if (changed.state == DownloadState.COMPLETE) complete.countDown()
        }.also { downloaders += it }

        downloader.enqueue(item)
        assertTrue("Direct MKV download did not complete", complete.await(20, TimeUnit.SECONDS))
        val finished = latest.get()
        assertEquals(DownloadState.COMPLETE, finished.state)
        assertTrue("Redirected MKV lost its .mkv extension: ${finished.localPath}", finished.localPath.endsWith(".mkv"))
        val file = File(finished.localPath)
        assertTrue("Downloaded MKV is missing", file.isFile)
        assertEquals(FixtureServer.DIRECT_MKV.size.toLong(), file.length())
        assertEquals(0, server.authorizationFailures.get())
        assertTrue("Redirect endpoint was not exercised", server.requestCount("/redirect-mkv") >= 2)
        assertTrue("Final MKV endpoint was not reached", server.requestCount("/direct.mkv") >= 2)

        assertOfflineVlcVisible(file, "Downloaded MKV", "annie-downloaded-mkv")
    }

    @Test fun hlsPausesResumesAsTsAndPlaysOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val server = FixtureServer().also { servers += it }
        installCookie(server.baseUrl)

        val payload = JSONObject()
            .put("type", "video")
            .put("title", "Annie HLS proof")
            .put("url", server.url("/master.m3u8"))
            .put("mimeType", "application/vnd.apple.mpegurl")
            .put("headers", JSONObject().put("Referer", REQUIRED_REFERER))
        val source = requireNotNull(ScriptVideoDownloadSource.from(payload))
        val item = downloadItem("hls-ts", source)
        val latest = AtomicReference(item)
        val complete = CountDownLatch(1)
        val downloader = AnnieMediaDownloader(context) { changed ->
            latest.set(changed)
            if (changed.state == DownloadState.COMPLETE) complete.countDown()
        }.also { downloaders += it }

        downloader.enqueue(item)
        assertTrue(
            "HLS did not reach the throttled second segment",
            server.secondSegmentStarted.await(15, TimeUnit.SECONDS),
        )
        downloader.pause(latest.get())
        waitUntil(5_000) { latest.get().state == DownloadState.PAUSED }
        assertEquals(DownloadState.PAUSED, latest.get().state)

        downloader.resume(latest.get())
        assertTrue("Resumed HLS download did not complete", complete.await(25, TimeUnit.SECONDS))
        val finished = latest.get()
        assertEquals(DownloadState.COMPLETE, finished.state)
        assertTrue("HLS was not finalized as MPEG-TS: ${finished.localPath}", finished.localPath.endsWith(".ts"))
        assertFalse("A playlist was incorrectly exposed as the final download", finished.localPath.endsWith(".m3u8"))
        val file = File(finished.localPath)
        assertTrue("Downloaded TS is missing", file.isFile)
        assertTrue("Downloaded TS is unexpectedly small", file.length() > 3_000L)
        assertEquals(0, server.authorizationFailures.get())
        assertTrue("Highest-bandwidth 1080 variant was not selected", server.requestCount("/1080/index.m3u8") >= 1)
        assertEquals("Lower-bandwidth variant should not be fetched", 0, server.requestCount("/720/index.m3u8"))
        assertEquals("Completed first HLS segment should not be downloaded again", 1, server.requestCount("/1080/seg000.ts"))
        assertTrue("Interrupted second segment should be retried after Resume", server.requestCount("/1080/seg001.ts") >= 2)

        assertOfflineVlcVisible(file, "Downloaded HLS TS", "annie-downloaded-hls-ts")
    }

    private fun downloadItem(id: String, source: AnnieDownloadSource) = DownloadItem(
        id = "media-proof-$id-${System.nanoTime()}",
        canonicalTitleId = "fixture:$id",
        sourceId = "instrumentation-fixture",
        sourceName = "Annie fixture server",
        kind = DownloadMediaKind.MOVIE,
        title = "Annie download proof",
        unitTitle = id,
        state = DownloadState.QUEUED,
        quality = source.quality,
        sourceUrl = source.url,
        headersJson = JSONObject(source.headers).toString(),
        sourceMimeType = source.mimeType,
    )

    private fun installCookie(baseUrl: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setCookie(baseUrl, "annie_session=ok; Path=/")
                flush()
            }
        }
    }

    private fun assertOfflineVlcVisible(file: File, title: String, screenshotName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val item = CatalogItem(
            id = file.absolutePath.hashCode(), mediaType = "MOVIE", title = title,
            image = "", year = 2026, status = "COMPLETE", episodes = 1, chapters = null,
        )
        compose.setContent {
            AnnieTheme {
                MediaPlayerScreen(
                    item = item,
                    mode = PlayerMode.OFFLINE,
                    sourceAvailable = true,
                    mediaUri = Uri.fromFile(file),
                    onBack = {},
                    immersive = false,
                )
            }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("—:—").fetchSemanticsNodes().isEmpty()
        }
        compose.waitUntil(45_000) {
            compose.onAllNodesWithText("Ⅱ").fetchSemanticsNodes().isNotEmpty()
        }
        val screenshotUri = saveEmulatorScreenshot(screenshotName)
        val screenshot = checkNotNull(
            context.contentResolver.openInputStream(screenshotUri)?.use(BitmapFactory::decodeStream)
        ) { "Could not reopen $title screenshot" }
        val left = screenshot.width / 4
        val right = screenshot.width * 3 / 4
        val top = screenshot.height / 4
        val bottom = screenshot.height * 3 / 4
        var sampled = 0
        var colored = 0
        for (y in top until bottom step 8) {
            for (x in left until right step 8) {
                val pixel = screenshot.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                sampled++
                if (maxOf(red, green, blue) > 60 && maxOf(red, green, blue) - minOf(red, green, blue) > 12) colored++
            }
        }
        screenshot.recycle()
        assertTrue("$title stayed visually black ($colored/$sampled colored samples)", colored > sampled / 100)
    }

    private fun waitUntil(timeoutMs: Long, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(25)
        }
    }

    private class FixtureServer : AutoCloseable {
        private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        private val pool = Executors.newCachedThreadPool()
        private val running = AtomicBoolean(true)
        private val counts = ConcurrentHashMap<String, AtomicInteger>()
        val authorizationFailures = AtomicInteger(0)
        val secondSegmentStarted = CountDownLatch(1)
        val baseUrl = "http://127.0.0.1:${server.localPort}"

        init {
            pool.execute {
                while (running.get()) {
                    try {
                        val socket = server.accept()
                        pool.execute { handle(socket) }
                    } catch (_: Throwable) {
                        if (running.get()) throw AssertionError("Fixture server accept failed")
                    }
                }
            }
        }

        fun url(path: String) = baseUrl + path
        fun requestCount(path: String) = counts[path]?.get() ?: 0

        private fun handle(socket: Socket) {
            socket.use { client ->
                client.soTimeout = 10_000
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1))
                val request = reader.readLine() ?: return
                val path = request.split(' ').getOrNull(1)?.substringBefore('?') ?: return
                val headers = linkedMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) headers[line.substring(0, colon).trim().lowercase(Locale.US)] = line.substring(colon + 1).trim()
                }
                val count = counts.computeIfAbsent(path) { AtomicInteger(0) }.incrementAndGet()

                if (path == "/redirect-mkv") {
                    writeResponse(client, 302, "text/plain", ByteArray(0), mapOf("Location" to url("/direct.mkv")))
                    return
                }
                if (!authorized(headers)) {
                    authorizationFailures.incrementAndGet()
                    writeResponse(client, 403, "text/plain", "forbidden".toByteArray())
                    return
                }

                when (path) {
                    "/direct.mkv" -> writeResponse(client, 200, "video/x-matroska", DIRECT_MKV)
                    "/master.m3u8" -> writeResponse(client, 200, "application/vnd.apple.mpegurl", MASTER.toByteArray())
                    "/720/index.m3u8" -> writeResponse(client, 200, "application/vnd.apple.mpegurl", MEDIA.toByteArray())
                    "/1080/index.m3u8" -> writeResponse(client, 200, "application/vnd.apple.mpegurl", MEDIA.toByteArray())
                    "/1080/seg000.ts" -> writeResponse(client, 200, "video/mp2t", SEGMENTS[0])
                    "/1080/seg001.ts" -> if (count == 1) writeSlowSegment(client, SEGMENTS[1]) else writeResponse(client, 200, "video/mp2t", SEGMENTS[1])
                    "/1080/seg002.ts" -> writeResponse(client, 200, "video/mp2t", SEGMENTS[2])
                    else -> writeResponse(client, 404, "text/plain", "missing".toByteArray())
                }
            }
        }

        private fun authorized(headers: Map<String, String>): Boolean =
            headers["referer"] == REQUIRED_REFERER && headers["cookie"].orEmpty().contains("annie_session=ok")

        private fun writeSlowSegment(socket: Socket, body: ByteArray) {
            val output = socket.getOutputStream()
            val header = "HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
            output.write(header.toByteArray(StandardCharsets.ISO_8859_1))
            val first = minOf(512, body.size)
            output.write(body, 0, first)
            output.flush()
            secondSegmentStarted.countDown()
            Thread.sleep(3_000)
            output.write(body, first, body.size - first)
            output.flush()
        }

        private fun writeResponse(socket: Socket, code: Int, mime: String, body: ByteArray, extraHeaders: Map<String, String> = emptyMap()) {
            val reason = when (code) { 200 -> "OK"; 302 -> "Found"; 403 -> "Forbidden"; else -> "Not Found" }
            val output = socket.getOutputStream()
            val header = buildString {
                append("HTTP/1.1 $code $reason\r\n")
                append("Content-Type: $mime\r\n")
                append("Content-Length: ${body.size}\r\n")
                extraHeaders.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
                append("Connection: close\r\n\r\n")
            }
            output.write(header.toByteArray(StandardCharsets.ISO_8859_1))
            output.write(body)
            output.flush()
        }

        override fun close() {
            running.set(false)
            runCatching { server.close() }
            pool.shutdownNow()
        }

        companion object {
            val DIRECT_MKV: ByteArray = Base64.decode("GkXfo6NChoEBQveBAULygQRC84EIQoKIbWF0cm9za2FCh4EEQoWBAhhTgGcBAAAAAAAFIRFNm3TAv4TOEY9yTbuLU6uEFUmpZlOsgaFNu4tTq4QWVK5rU6yB7027jFOrhBJUw2dTrIIBhk27jFOrhBxTu2tTrIIFBewBAAAAAAAAUwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFUmpZsm/hNaCgswq17GDD0JATYCMTGF2ZjYxLjcuMTAzV0GMTGF2ZjYxLjcuMTAzc6SQwrxBGMJqlbAN5E7O7QnEkkSJiECPQAAAAAAAFlSua0CRv4RG4qlBrgEAAAAAAACC14EBc8WID8N1wHyUU5qcgQAitZyDdW5kiIEAho9WX01QRUc0L0lTTy9BVkODgQEj44OEC+vCAOCQsIFguoE2moECVbCEVbmBAVXugQDsAQAAAAAAAAIAAGOipwFCwAr/4QAXZ0LACtoYn5sBEAAAAwAQAAADAKDxImoBAAVozgOcgBJUw2dAgr+ErxZq6XNzn2PAgGfImUWjh0VOQ09ERVJEh4xMYXZmNjEuNy4xMDNzc9djwItjxYgPw3XAfJRTmmfIokWjh0VOQ09ERVJEh5VMYXZjNjEuMTkuMTAxIGxpYngyNjRnyKFFo4hEVVJBVElPTkSHkzAwOjAwOjAxLjAwMDAwMDAwMAAfQ7Z1QvG/hKkFXzjngQCjQoOBAACAAAACUwYF//9P3EXpvebZSLeWLNgg2SPu73gyNjQgLSBjb3JlIDE2NCByMzEwOCAzMWUxOWY5IC0gSC4yNjQvTVBFRy00IEFWQyBjb2RlYyAtIENvcHlsZWZ0IDIwMDMtMjAyMyAtIGh0dHA6Ly93d3cudmlkZW9sYW4ub3JnL3gyNjQuaHRtbCAtIG9wdGlvbnM6IGNhYmFjPTAgcmVmPTEgZGVibG9jaz0wOjA6MCBhbmFseXNlPTA6MCBtZT1kaWEgc3VibWU9MCBwc3k9MSBwc3lfcmQ9MS4wMDowLjAwIG1peGVkX3JlZj0wIG1lX3JhbmdlPTE2IGNocm9tYV9tZT0xIHRyZWxsaXM9MCA4eDhkY3Q9MCBjcW09MCBkZWFkem9uZT0yMSwxMSBmYXN0X3Bza2lwPTEgY2hyb21hX3FwX29mZnNldD0wIHRocmVhZHM9MiBsb29rYWhlYWRfdGhyZWFkcz0xIHNsaWNlZF90aHJlYWRzPTAgbnI9MCBkZWNpbWF0ZT0xIGludGVybGFjZWQ9MCBibHVyYXlfY29tcGF0PTAgY29uc3RyYWluZWRfaW50cmE9MCBiZnJhbWVzPTAgd2VpZ2h0cD0wIGtleWludD0yNTAga2V5aW50X21pbj01IHNjZW5lY3V0PTAgaW50cmFfcmVmcmVzaD0wIHJjPWNyZiBtYnRyZWU9MCBjcmY9NDAuMCBxY29tcD0wLjYwIHFwbWluPTAgcXBtYXg9NjkgcXBzdGVwPTQgaXBfcmF0aW89MS40MCBhcT0wAIAAAAAkZYiEOhGKAAIAMcAAjHAAEBaTk5OTk6666666666666666668o7CBAMgAAAAAKEGaIBOvUdvqO31Hb6jt9R2+o7Z/P5/P5/P5/P5/P5/P5/P5/P4CjjoEBkAAAAAAGQZpAE6DMo46BAlgAAAAABkGaYBOgzKOOgQMgAAAAAAZBmoAUoMwcU7trl7+EEKbvn7uPs4EAt4r3gQHxggIO8IEJ", Base64.DEFAULT)
            val SEGMENTS: List<ByteArray> = listOf(
                Base64.decode("R0AREABC8CUAAcEAAP8B/wAB/IAUSBIBBkZGbXBlZwlTZXJ2aWNlMDF3fEPK//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9HQAAQAACwDQABwQAAAAHwACqxBLL//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////0dQABAAArASAAHBAADhAPAAG+EA8AAVvU1W////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////R0EAMAdQAAB7DH4AAAAB4AAAgIAFIQAH2GEAAAABCfAAAAABZ0LACtoYn5sBEAAAAwAQAAADAKDxImoAAAABaM4DnIAAAAEGBf//TdxF6b3m2Ui3lizYINkj7u94MjY0IC0gY29yZSAxNjQgcjMxMDggMzFlMTlmOSAtIEguMjY0L01QRUctNCBBVkMgY29kZWMgLSBDb3B5bGVmdCAyMDAzLTIwMjMgLSBodHRwOi8vd3d3LnZpZGVvbGFHAQARbi5vcmcveDI2NC5odG1sIC0gb3B0aW9uczogY2FiYWM9MCByZWY9MSBkZWJsb2NrPTA6MDowIGFuYWx5c2U9MDowIG1lPWRpYSBzdWJtZT0wIHBzeT0xIHBzeV9yZD0xLjAwOjAuMDAgbWl4ZWRfcmVmPTAgbWVfcmFuZ2U9MTYgY2hyb21hX21lPTEgdHJlbGxpcz0wIDh4OGRjdD0wIGNxbT0wIGRlYWR6b25lPTIxLDExIGZhc0cBABJ0X3Bza2lwPTEgY2hyb21hX3FwX29mZnNldD0wIHRocmVhZHM9MiBsb29rYWhlYWRfdGhyZWFkcz0xIHNsaWNlZF90aHJlYWRzPTAgbnI9MCBkZWNpbWF0ZT0xIGludGVybGFjZWQ9MCBibHVyYXlfY29tcGF0PTAgY29uc3RyYWluZWRfaW50cmE9MCBiZnJhbWVzPTAgd2VpZ2h0cD0wIGtleWludD01IGtleWludF9taW49MyBzRwEAMyQA//////////////////////////////////////////////9jZW5lY3V0PTAgaW50cmFfcmVmcmVzaD0wIHJjPWNyZiBtYnRyZWU9MCBjcmY9NDAuMCBxY29tcD0wLjYwIHFwbWluPTAgcXBtYXg9NjkgcXBzdGVwPTQgaXBfcmF0aW89MS40MCBhcT0wAIAAAAABZYiEOhGKAAIDscAAQFo4Mm5OTk5Ot111111111111111115HQQA0dBAAAJ40fgD/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////AAAB4AAAgIAFIQAJZQEAAAABCfAAAAABQZogE68KXd3d3d3d3d3R3E+J8T4nxPn8/n8/n8/n8/n8/n8/n8/n8/n8/kdBADWZEAAAwVx+AP//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////AAAB4AAAgIAFIQAJ8aEAAAABCfAAAAABQZpAE6DMR0EANpkQAADkhH4A//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////8AAAHgAACAgAUhAAt+QQAAAAEJ8AAAAAFBmmAToMxHQQA3mRAAAQesfgD//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////wAAAeAAAICABSEADQrhAAAAAQnwAAAAAUGagBSgzA==", Base64.DEFAULT),
                Base64.decode("R0AREQBC8CUAAcEAAP8B/wAB/IAUSBIBBkZGbXBlZwlTZXJ2aWNlMDF3fEPK//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9HQAARAACwDQABwQAAAAHwACqxBLL//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////0dQABEAArASAAHBAADhAPAAG+EA8AAVvU1W////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////R0EAOFNQAAEq1H4A/////////////////////////////////////////////////////////////////////////////////////////////////////wAAAeAAAICABSEADZeBAAAAAQnwAAAAAWdCwAraGJ+bARAAAAMAEAAAAwCg8SJqAAAAAWjOA5yAAAAAAWWIggE6EYoAAi2xwABDejgACALEd5OTk5ObvXXXXXXXXXXXXXXXXXhHQQA5lxAAAU38fgD///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////8AAAHgAACAgAUhAA8kIQAAAAEJ8AAAAAFBmiATr1eGIEdBADqZEAABcSR+AP//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////AAAB4AAAgIAFIQAPsMEAAAABCfAAAAABQZpAE6DMR0EAO5kQAAGUTH4A//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////8AAAHgAACAgAUhABE9YQAAAAEJ8AAAAAFBmmAToMxHQQA8mRAAAbd0fgD//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////wAAAeAAAICABSEAEcoBAAAAAQnwAAAAAUGagBSgzA==", Base64.DEFAULT),
                Base64.decode("R0AREgBC8CUAAcEAAP8B/wAB/IAUSBIBBkZGbXBlZwlTZXJ2aWNlMDF3fEPK//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////9HQAASAACwDQABwQAAAAHwACqxBLL//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////0dQABIAArASAAHBAADhAPAAG+EA8AAVvU1W////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////R0EAPVVQAAHanH4A////////////////////////////////////////////////////////////////////////////////////////////////////////AAAB4AAAgIAFIQATVqEAAAABCfAAAAABZ0LACtoYn5sBEAAAAwAQAAADAKDxImoAAAABaM4DnIAAAAABZYiEBShGKAAIxccAAQ8o4AAgFycnJycnXXXXXXXXXXXXXXXXXXhHQQA+mRAAAf3EfgD//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////wAAAeAAAICABSEAE+NBAAAAAQnwAAAAAUGaIBOgzEdBAD+ZEAACIOx+AP//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////AAAB4AAAgIAFIQAVb+EAAAABCfAAAAABQZpAE6DMR0EAMJkQAAJEFH4A//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////8AAAHgAACAgAUhABX8gQAAAAEJ8AAAAAFBmmAToMxHQQAxmRAAAmc8fgD//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////wAAAeAAAICABSEAF4khAAAAAQnwAAAAAUGagBSgzA==", Base64.DEFAULT),
            )
            const val MASTER = """#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=500000,RESOLUTION=160x90
720/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=1500000,RESOLUTION=160x90
1080/index.m3u8
"""
            const val MEDIA = """#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:1
#EXT-X-MEDIA-SEQUENCE:0
#EXT-X-PLAYLIST-TYPE:VOD
#EXTINF:1.0,
seg000.ts
#EXTINF:1.0,
seg001.ts
#EXTINF:1.0,
seg002.ts
#EXT-X-ENDLIST
"""
        }
    }

    companion object { const val REQUIRED_REFERER = "https://annie.test/watch" }
}
