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
            .put("headers", JSONObject().put("Referer", REQUIRED_