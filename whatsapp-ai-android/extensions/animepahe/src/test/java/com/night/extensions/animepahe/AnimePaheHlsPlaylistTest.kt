package com.night.extensions.animepahe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AnimePaheHlsPlaylistTest {
    @Test
    fun resolvesMasterVariantAndRelativeSegments() {
        val variants =
            AnimePaheHlsPlaylist
                .masterVariants(
                    content =
                        """
                        #EXTM3U
                        #EXT-X-STREAM-INF:BANDWIDTH=3500000
                        1080/index.m3u8
                        """.trimIndent(),
                    baseUrl =
                        "https://cdn.example/master.m3u8",
                )

        assertEquals(
            listOf(
                "https://cdn.example/1080/index.m3u8"
            ),
            variants,
        )

        val segments =
            AnimePaheHlsPlaylist
                .segments(
                    content =
                        """
                        #EXTM3U
                        #EXT-X-MEDIA-SEQUENCE:4
                        #EXTINF:6.0,
                        seg4.ts
                        #EXTINF:6.0,
                        seg5.ts
                        """.trimIndent(),
                    baseUrl =
                        "https://cdn.example/1080/index.m3u8",
                )

        assertEquals(2, segments.size)
        assertEquals(
            "https://cdn.example/1080/seg4.ts",
            segments.first().url,
        )
        assertEquals(
            6.0,
            segments.first().durationSeconds,
            0.0,
        )
    }

    @Test
    fun derivesSequenceIvForAes128() {
        val segments =
            AnimePaheHlsPlaylist
                .segments(
                    content =
                        """
                        #EXTM3U
                        #EXT-X-MEDIA-SEQUENCE:7
                        #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
                        #EXTINF:4.0,
                        part.ts
                        """.trimIndent(),
                    baseUrl =
                        "https://cdn.example/ep/index.m3u8",
                )

        assertEquals(1, segments.size)
        assertEquals(
            "https://cdn.example/ep/key.bin",
            segments.single().keyUrl,
        )
        val iv = segments.single().iv
        assertNotNull(iv)
        assertEquals(16, iv!!.size)
        assertEquals(7, iv.last().toInt())
    }
}
