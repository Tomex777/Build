package com.tomex777.annie

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnieMediaDownloaderTest {
    @Test fun directContainerExtensionIsPreserved() {
        assertEquals("mkv", AnnieDownloadNaming.extensionFor("video/x-matroska", "https://cdn.example/show/file.mkv?token=1"))
        assertEquals("webm", AnnieDownloadNaming.extensionFor("video/webm", "https://cdn.example/file.webm"))
        assertEquals("ts", AnnieDownloadNaming.extensionFor("video/mp2t", "https://cdn.example/file.ts"))
        assertEquals("mp4", AnnieDownloadNaming.extensionFor("video/mp4", "https://cdn.example/file.mp4"))
    }

    @Test fun transportStreamHlsPlansOneTsOutput() {
        val playlist = """
            |#EXTM3U
            |#EXT-X-TARGETDURATION:4
            |#EXTINF:4,
            |segment-001.ts
            |#EXTINF:4,
            |segment-002.ts
            |#EXT-X-ENDLIST
        """.trimMargin()
        val plan = AnnieHlsPlanner.mediaPlan("https://cdn.example/path/media.m3u8", playlist)
        assertEquals("ts", plan.extension)
        assertEquals("video/mp2t", plan.mimeType)
        assertEquals(
            listOf(
                "https://cdn.example/path/segment-001.ts",
                "https://cdn.example/path/segment-002.ts",
            ),
            plan.parts,
        )
    }

    @Test fun fragmentedMp4HlsKeepsMp4Container() {
        val playlist = """
            |#EXTM3U
            |#EXT-X-MAP:URI="init.mp4"
            |#EXTINF:4,
            |segment-001.m4s
            |#EXTINF:4,
            |segment-002.m4s
            |#EXT-X-ENDLIST
        """.trimMargin()
        val plan = AnnieHlsPlanner.mediaPlan("https://cdn.example/path/media.m3u8", playlist)
        assertEquals("mp4", plan.extension)
        assertEquals("video/mp4", plan.mimeType)
        assertEquals("https://cdn.example/path/init.mp4", plan.parts.first())
    }

    @Test fun masterPlaylistSelectsHighestBandwidthVariant() {
        val playlist = """
            |#EXTM3U
            |#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=1280x720
            |720/index.m3u8
            |#EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1920x1080
            |1080/index.m3u8
        """.trimMargin()
        assertEquals(
            "https://cdn.example/video/1080/index.m3u8",
            AnnieHlsPlanner.selectMasterVariant("https://cdn.example/video/master.m3u8", playlist),
        )
    }

    @Test fun scriptVideoResolutionKeepsHeadersAndChoosesBestQuality() {
        val data = JSONObject()
            .put("type", "video")
            .put("title", "Proof")
            .put("headers", JSONObject().put("Referer", "https://site.example/watch"))
            .put(
                "qualities",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("label", "720p")
                            .put("url", "https://cdn.example/720/master.m3u8")
                    )
                    .put(
                        JSONObject()
                            .put("label", "1080p")
                            .put("url", "https://cdn.example/1080/master.m3u8")
                            .put("headers", JSONObject().put("Origin", "https://site.example"))
                    )
            )

        val source = requireNotNull(ScriptVideoDownloadSource.from(data))
        assertEquals("1080p", source.quality)
        assertEquals("https://cdn.example/1080/master.m3u8", source.url)
        assertEquals("https://site.example/watch", source.headers["Referer"])
        assertEquals("https://site.example", source.headers["Origin"])
        assertTrue(AnnieDownloadNaming.isHls(source.url, source.mimeType))
    }
}
