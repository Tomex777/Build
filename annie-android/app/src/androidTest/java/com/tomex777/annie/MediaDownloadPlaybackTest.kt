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
            compose.onA