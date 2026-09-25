package app.nami.android

import fi.iki.elonen.NanoHTTPD
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NamiHeaderProxyTest {
    @Test
    fun forwardsArbitraryHeadersAndRewritesHlsChildren() {
        val requests = CopyOnWriteArrayList<Map<String, String>>()
        val upstream = object : NanoHTTPD(0) {
            override fun serve(session: IHTTPSession): Response {
                requests += session.headers.toMap()
                return when (session.uri) {
                    "/master.m3u8" -> newFixedLengthResponse(
                        Response.Status.OK,
                        "application/vnd.apple.mpegurl",
                        "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1200000\nchild.m3u8",
                    )
                    "/child.m3u8" -> newFixedLengthResponse(
                        Response.Status.OK,
                        "application/vnd.apple.mpegurl",
                        "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"\n#EXTINF:10,\nsegment.ts",
                    )
                    "/segment.ts" -> newFixedLengthResponse(
                        Response.Status.OK,
                        "video/mp2t",
                        "video-bytes",
                    )
                    "/key.bin" -> newFixedLengthResponse(
                        Response.Status.OK,
                        "application/octet-stream",
                        "key-bytes",
                    )
                    else -> newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        MIME_PLAINTEXT,
                        "missing",
                    )
                }
            }
        }
        upstream.start()

        val proxy = NamiHeaderProxy()
        try {
            val headers = mapOf(
                "Origin" to "https://megaplay.example",
                "Referer" to "https://megaplay.example/",
                "User-Agent" to "Nami-Test-Agent",
                "X-Nami-Fixture" to "exact-header",
            )
            val master = proxy.wrap(
                "http://127.0.0.1:${upstream.listeningPort}/master.m3u8",
                headers,
            )
            val rewrittenMaster = URL(master).readText()
            assertTrue(rewrittenMaster.contains("127.0.0.1:${proxy.listeningPort}/p/"))
            assertTrue(!rewrittenMaster.contains("\nchild.m3u8"))

            val childUrl = rewrittenMaster.lineSequence()
                .first { it.startsWith("http://127.0.0.1:") }
            val rewrittenChild = URL(childUrl).readText()
            assertTrue(rewrittenChild.contains("URI=\"http://127.0.0.1:"))

            val segmentUrl = rewrittenChild.lineSequence()
                .first { it.startsWith("http://127.0.0.1:") }
            assertEquals("video-bytes", URL(segmentUrl).readText())

            assertTrue(requests.size >= 3)
            requests.forEach { seen ->
                assertEquals("https://megaplay.example", seen["origin"])
                assertEquals("https://megaplay.example/", seen["referer"])
                assertEquals("Nami-Test-Agent", seen["user-agent"])
                assertEquals("exact-header", seen["x-nami-fixture"])
            }
        } finally {
            proxy.stop()
            upstream.stop()
        }
    }
}
