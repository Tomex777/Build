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
    fun interruptedTransfersRecoverAsErrorsButTerminalStatesStayStable() {
        assertEquals(
            NamiDownloadState.ERROR,
            DownloadRecoveryPolicy.recoverState(NamiDownloadState.QUEUED),
        )
        assertEquals(
            NamiDownloadState.ERROR,
            DownloadRecoveryPolicy.recoverState(NamiDownloadState.DOWNLOADING),
        )
        assertEquals(
            NamiDownloadState.DOWNLOADED,
            DownloadRecoveryPolicy.recoverState(NamiDownloadState.DOWNLOADED),
        )
        assertEquals(
            NamiDownloadState.ERROR,
            DownloadRecoveryPolicy.recoverState(NamiDownloadState.ERROR),
        )

        assertTrue(
            DownloadRecoveryPolicy.shouldDiscardPartialTarget(NamiDownloadState.DOWNLOADING),
        )
        assertFalse(
            DownloadRecoveryPolicy.shouldDiscardPartialTarget(NamiDownloadState.DOWNLOADED),
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
        assertEquals(
            "mkv",
            DownloadMediaNaming.extensionFor(
                mimeType = "application/octet-stream",
                url = "https://drive.usercontent.google.com/download?id=file",
                contentDisposition = "attachment; filename=\"ReZero Episode 16.mkv\"",
            ),
        )
        assertEquals(
            "ReZero Episode 16.mkv",
            DownloadMediaNaming.fileNameFromContentDisposition(
                "attachment; filename=\"ReZero Episode 16.mkv\"",
            ),
        )
    }

    @Test
    fun googleDriveConfirmationFormKeepsIdentityAndConfirmationFields() {
        val first =
            "https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQrStUvWxYz/view?usp=sharing"
        assertTrue(GoogleDriveDownloadPlanner.isDriveDownload(first))
        assertEquals(
            "https://drive.google.com/uc?export=download&id=1AbCdEfGhIjKlMnOpQrStUvWxYz",
            GoogleDriveDownloadPlanner.directDownloadUrl(first),
        )

        val html = """
            <form id="download-form" action="https://drive.usercontent.google.com/download" method="get">
              <input type="hidden" name="id" value="1AbCdEfGhIjKlMnOpQrStUvWxYz">
              <input type="hidden" name="export" value="download">
              <input type="hidden" name="confirm" value="t">
              <input type="hidden" name="uuid" value="drive-confirmation-token">
            </form>
        """.trimIndent()

        val confirmed = GoogleDriveDownloadPlanner.confirmationUrl(
            html,
            "https://drive.google.com/uc?export=download&id=1AbCdEfGhIjKlMnOpQrStUvWxYz",
        ) ?: error("Expected confirmation URL")

        assertTrue(confirmed.startsWith("https://drive.usercontent.google.com/download?"))
        assertTrue(confirmed.contains("id=1AbCdEfGhIjKlMnOpQrStUvWxYz"))
        assertTrue(confirmed.contains("confirm=t"))
        assertTrue(confirmed.contains("uuid=drive-confirmation-token"))
    }
    @Test
    fun legacyPublicMoviesRequiresPermissionOnlyThroughApi28() {
        assertTrue(
            DownloadStoragePolicy.requiresLegacyWritePermission(
                sdkInt = 26,
                permissionGranted = false,
            ),
        )
        assertFalse(
            DownloadStoragePolicy.requiresLegacyWritePermission(
                sdkInt = 28,
                permissionGranted = true,
            ),
        )
        assertFalse(
            DownloadStoragePolicy.requiresLegacyWritePermission(
                sdkInt = 29,
                permissionGranted = false,
            ),
        )
        assertFalse(
            DownloadStoragePolicy.requiresLegacyWritePermission(
                sdkInt = 36,
                permissionGranted = false,
            ),
        )
    }


    @Test
    fun retryPlannerReconstructsStatefulRequestFromPersistedStatus() {
        val status = NamiDownloadStatus(
            sourceId = "source",
            sourceAnimeId = "/anime",
            sourceEpisodeId = "/episode-7",
            extensionName = "Fixture",
            animeTitle = "Fixture Anime",
            episodeTitle = "Episode 7",
            animeSourceState = """{"memo":{"token":"anime"}}""",
            episodeSourceState = """{"memo":{"token":"episode"}}""",
            relativePath = "Fixture/Fixture Anime/Season 01",
            state = NamiDownloadState.ERROR,
            errorMessage = "Interrupted",
        )

        val request = DownloadRetryPlanner.create(status)
            ?: error("Expected retry request")

        assertEquals(status.sourceId, request.anime.ref.sourceId)
        assertEquals(status.sourceAnimeId, request.anime.ref.sourceAnimeId)
        assertEquals(status.animeTitle, request.anime.title)
        assertEquals(status.animeSourceState, request.anime.sourceState)
        assertEquals(status.sourceId, request.episode.ref.sourceId)
        assertEquals(status.sourceAnimeId, request.episode.ref.sourceAnimeId)
        assertEquals(status.sourceEpisodeId, request.episode.ref.sourceEpisodeId)
        assertEquals(status.episodeTitle, request.episode.title)
        assertEquals(status.episodeSourceState, request.episode.sourceState)
        assertEquals(status.relativePath, request.relativeDirectory)
    }

    @Test
    fun retryPlannerRejectsNonErrorAndMalformedRecords() {
        val base = NamiDownloadStatus(
            sourceId = "source",
            sourceAnimeId = "anime",
            sourceEpisodeId = "episode",
            extensionName = "Fixture",
            animeTitle = "Anime",
            episodeTitle = "Episode",
            relativePath = "Fixture/Anime",
            state = NamiDownloadState.DOWNLOADED,
        )

        assertEquals(null, DownloadRetryPlanner.create(base))
        assertEquals(
            null,
            DownloadRetryPlanner.create(
                base.copy(
                    state = NamiDownloadState.ERROR,
                    sourceEpisodeId = "",
                ),
            ),
        )
    }


    @Test
    fun batchPolicyOnlyQueuesNewOrFailedEpisodes() {
        assertTrue(DownloadBatchPolicy.shouldEnqueue(null))
        assertTrue(DownloadBatchPolicy.shouldEnqueue(NamiDownloadState.ERROR))
        assertFalse(DownloadBatchPolicy.shouldEnqueue(NamiDownloadState.QUEUED))
        assertFalse(DownloadBatchPolicy.shouldEnqueue(NamiDownloadState.DOWNLOADING))
        assertFalse(DownloadBatchPolicy.shouldEnqueue(NamiDownloadState.DOWNLOADED))
    }


}
