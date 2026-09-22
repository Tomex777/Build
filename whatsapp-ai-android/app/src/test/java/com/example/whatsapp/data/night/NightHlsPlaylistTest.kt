package com.example.whatsapp.data.night

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NightHlsPlaylistTest {
    @Test
    fun resolvesMasterVariantsAndRelativeSegments() {
        val variants = NightHlsPlaylist.masterVariants(
            content = """
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=1200000
                720/index.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=3500000
                1080/index.m3u8
            """.trimIndent(),
            baseUrl = "https://cdn.example/master.m3u8",
        )

        assertEquals(
            listOf(
                "https://cdn.example/720/index.m3u8",
                "https://cdn.example/1080/index.m3u8",
            ),
            variants,
        )

        val segments = NightHlsPlaylist.segments(
            content = """
                #EXTM3U
                #EXT-X-MEDIA-SEQUENCE:4
                #EXTINF:6.0,
                seg4.ts
                #EXTINF:4.5,
                ../shared/seg5.ts
            """.trimIndent(),
            baseUrl = "https://cdn.example/1080/index.m3u8",
        )

        assertEquals(2, segments.size)
        assertEquals("https://cdn.example/1080/seg4.ts", segments[0].url)
        assertEquals("https://cdn.example/shared/seg5.ts", segments[1].url)
        assertEquals(6.0, segments[0].durationSeconds, 0.0)
        assertEquals(4.5, segments[1].durationSeconds, 0.0)
    }

    @Test
    fun derivesSequenceIvForAes128AndAdvancesIt() {
        val segments = NightHlsPlaylist.segments(
            content = """
                #EXTM3U
                #EXT-X-MEDIA-SEQUENCE:7
                #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
                #EXTINF:4.0,
                part7.ts
                #EXTINF:4.0,
                part8.ts
            """.trimIndent(),
            baseUrl = "https://cdn.example/ep/index.m3u8",
        )

        assertEquals(2, segments.size)
        assertEquals("https://cdn.example/ep/key.bin", segments[0].keyUrl)
        assertNotNull(segments[0].iv)
        assertNotNull(segments[1].iv)
        assertEquals(16, segments[0].iv!!.size)
        assertEquals(7, segments[0].iv!!.last().toInt() and 0xFF)
        assertEquals(8, segments[1].iv!!.last().toInt() and 0xFF)
    }

    @Test
    fun honorsExplicitAesIvAndClearsEncryptionOnMethodNone() {
        val explicit = ByteArray(16) { index -> (index + 1).toByte() }
        val segments = NightHlsPlaylist.segments(
            content = """
                #EXTM3U
                #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x0102030405060708090A0B0C0D0E0F10
                #EXTINF:4.0,
                encrypted.ts
                #EXT-X-KEY:METHOD=NONE
                #EXTINF:4.0,
                clear.ts
            """.trimIndent(),
            baseUrl = "https://cdn.example/ep/index.m3u8",
        )

        assertEquals(2, segments.size)
        assertArrayEquals(explicit, segments[0].iv)
        assertEquals(null, segments[1].keyUrl)
        assertEquals(null, segments[1].iv)
    }
}
