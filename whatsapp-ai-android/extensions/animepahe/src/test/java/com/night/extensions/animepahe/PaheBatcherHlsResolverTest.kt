package com.night.extensions.animepahe

import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PaheBatcherHlsResolverTest {
    private lateinit var server: MockWebServer
    private lateinit var session: FakeSession
    private lateinit var resolver: PaheBatcherHlsResolver

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        session = FakeSession()
        resolver =
            PaheBatcherHlsResolver(
                session = session,
                client = OkHttpClient.Builder().build(),
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun resolvesAnimePaheKwikEmbedToHlsLikeProvenPaheBatcherFlow() {
        val hls = "https://cdn.example/stream/master.m3u8"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    <html>
                      <script>
                        const source='$hls';
                      </script>
                    </html>
                    """.trimIndent()
                )
        )

        val kwikUrl =
            server.url("/e/Qc04STuVaA4f")
                .toString()

        val result =
            resolver.resolve(
                source =
                    AnimePaheSource(
                        kwikUrl = kwikUrl,
                        downloadPageUrl = null,
                        resolution = 1080,
                        audio = "jpn",
                        fansub = "SubsPlease",
                    ),
                playUrl = "https://animepahe.pw/play/a/e",
            )

        assertEquals(hls, result.url)
        assertEquals(
            "application/vnd.apple.mpegurl",
            result.mimeType,
        )
        assertEquals(
            kwikUrl,
            result.headers["Referer"],
        )
        assertEquals("Night PaheBatcher Test", result.headers["User-Agent"])

        val request = server.takeRequest()
        assertEquals("/e/Qc04STuVaA4f", request.path)
        assertEquals(
            "https://animepahe.pw/play/a/e",
            request.getHeader("Referer"),
        )
    }

    @Test
    fun extractsHlsFromNestedEvalWithoutUsingKwikDownloadFormRoute() {
        val hls = "https://cdn.example/episode/uwu.m3u8"
        val html =
            """<script>eval("const source='$hls';")</script>"""

        assertEquals(hls, resolver.extractM3u8(html))
    }

    private class FakeSession : AnimePaheSession {
        override fun baseUrl(): String = "https://animepahe.pw"
        override fun quality(): String = "1080"
        override fun audio(): String = "sub"
        override fun parallelDownloads(): Int = 2
        override fun userAgent(): String = "Night PaheBatcher Test"
        override fun cookieForUrl(url: String): String = ""
        override fun saveBrowserSession(host: String, cookieHeader: String) = Unit
        override fun saveConfiguration(values: JSONObject) = Unit
        override fun absorbResponseCookies(response: Response) = Unit
    }
}
