package com.night.sora.youtubemusic

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class SharedYouTubeEngineHostTest {
    @Test
    fun resolveRefreshAndResumeUseOneStableRepresentation() = runBlocking {
        val videoId = "dQw4w9WgXcQ"
        val first = JSONArray(SharedYouTubeAudioResolver.resolveStreams(videoId)).getJSONObject(0)
        val identity = first.getString("stableIdentity")
        assertTrue(identity.isNotBlank())
        assertEquals("shared-youtube-engine", first.getString("resolverClient"))
        assertTrue(first.getString("url").contains("googlevideo.com"))

        val refreshed = JSONObject(SharedYouTubeAudioResolver.refreshStream(videoId, identity))
        assertEquals(identity, refreshed.getString("stableIdentity"))
        assertEquals("shared-youtube-engine", refreshed.getString("resolverClient"))

        val bytes = proveRange(refreshed, startByte = 8192L, byteLimit = 4096)
        assertTrue("Refreshed host stream returned too few CDN bytes", bytes >= 512)

        println(
            "LYRA_ENGINE_HOST_PROOF status=SUPPORTED_AND_PROVEN " +
                "identity=$identity itag=${refreshed.optInt("itag")} " +
                "resumeStart=8192 bytes=$bytes"
        )
    }

    private fun proveRange(stream: JSONObject, startByte: Long, byteLimit: Int): Int {
        var connection: HttpURLConnection? = null
        try {
            val candidate = (URL(stream.getString("url")).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                setRequestProperty("Range", "bytes=$startByte-${startByte + byteLimit - 1}")
                val headers = stream.optJSONObject("headers")
                if (headers != null) {
                    val names = headers.keys()
                    while (names.hasNext()) {
                        val name = names.next()
                        headers.optString(name).takeIf(String::isNotBlank)?.let { setRequestProperty(name, it) }
                    }
                }
            }
            connection = candidate
            assertEquals("CDN ignored Lyra resume range", 206, candidate.responseCode)
            assertTrue(
                "CDN returned the wrong Lyra resume range",
                candidate.getHeaderField("Content-Range").orEmpty().startsWith("bytes $startByte-")
            )
            var total = 0
            candidate.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (total < byteLimit) {
                    val read = input.read(buffer, 0, minOf(buffer.size, byteLimit - total))
                    if (read < 0) break
                    total += read
                }
            }
            return total
        } finally {
            connection?.disconnect()
        }
    }
}
