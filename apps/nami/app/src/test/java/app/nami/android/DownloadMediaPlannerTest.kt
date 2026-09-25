package app.nami.android

import app.nami.domain.AnimeEpisode
import app.nami.domain.EpisodeRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadMediaPlannerTest {

    @Test
    fun masterPlaylistChoosesHighestBandwidthAndResolvesRelativeUrl() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=3200000,RESOLUTION=1920x1080
            ../1080/index.m3u8
        """.trimIndent()

        val selected = HlsPlaylistPlanner.selectMasterVariant(
            baseUrl = "https://cdn.example/master/main.m3u8",
            playlist = playlist,
        )

        assertEquals(
            "https://cdn.example/1080/index.m3u8",
            selected,
        )
    }

    @Test
    fun transportStreamPlaylistProducesTsPlan() {
        val plan = HlsPlaylistPlanner.mediaPlan(
            baseUrl = "https://cdn.example/show/playlist.m3u8",
            playlist = """
                #EXTM3U
                #EXT-X-TARGETDURATION:10
                #EXTINF:10,
                seg-001.ts
                #EXTINF:10,
                seg-002.ts?token=abc
            """.trimIndent(),
        )

        assertEquals("video/mp2t", plan.mimeType)
        assertEquals("ts", plan.extension)
        assertEquals(null, plan.initSegmentUrl)
        assertEquals(
            listOf(
                "https://cdn.example/show/seg-001.ts",
                "https://cdn.example/show/seg-002.ts?token=abc",
            ),
            plan.segmentUrls,
        )
    }

    @Test
    fun fragmentedMp4PlaylistKeepsInitializationSegmentFirst() {
        val plan = HlsPlaylistPlanner.mediaPlan(
            baseUrl = "https://cdn.example/show/playlist.m3u8",
            playlist = """
                #EXTM3U
                #EXT-X-MAP:URI="init.mp4"
                #EXTINF:4,
                chunk-001.m4s
                #EXTINF:4,
                chunk-002.m4s
            """.trimIndent(),
        )

        assertEquals("video/mp4", plan.mimeType)
        assertEquals("mp4", plan.extension)
        assertEquals("https://cdn.example/show/init.mp4", plan.initSegmentUrl)
        assertEquals(
            listOf(
                "https://cdn.example/show/init.mp4",
                "https://cdn.example/show/chunk-001.m4s",
                "https://cdn.example/show/chunk-002.m4s",
            ),
            plan.allPartUrls,
        )
    }

    @Test
    fun encryptedPlaylistIsRejectedBeforeAnyFileIo() {
        val error = assertFailsWith<IllegalArgumentException> {
            HlsPlaylistPlanner.mediaPlan(
                baseUrl = "https://cdn.example/show/playlist.m3u8",
                playlist = """
                    #EXTM3U
                    #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
                    #EXTINF:4,
                    chunk-001.ts
                """.trimIndent(),
            )
        }

        assertTrue(error.message.orEmpty().contains("Encrypted HLS"))
    }

    @Test
    fun hlsDetectionHandlesUrlAndMimeType() {
        assertTrue(
            DownloadMediaNaming.isHls(
                "https://cdn.example/master.m3u8?token=1",
                null,
            ),
        )
        assertTrue(
            DownloadMediaNaming.isHls(
                "https://cdn.example/stream",
                "application/vnd.apple.mpegurl",
            ),
        )
        assertFalse(
            DownloadMediaNaming.isHls(
                "https://cdn.example/video.mkv",
                "video/x-matroska",
            ),
        )
    }

    @Test
    fun episodeFilenameIsStableSanitizedAndContainerPreserving() {
        val episode = AnimeEpisode(
            ref = EpisodeRef(
                sourceId = "source",
                sourceAnimeId = "anime",
                sourceEpisodeId = "episode",
            ),
            title = "The / Beginning?",
            number = 1.0,
        )

        assertEquals(
            "Episode 001 - The _ Beginning_.mkv",
            DownloadMediaNaming.episodeFileName(episode, "mkv"),
        )
        assertEquals(
            "mkv",
            DownloadMediaNaming.extensionFor(
                mimeType = "application/octet-stream",
                url = "https://cdn.example/file.mkv?token=abc",
            ),
        )
    }
}
