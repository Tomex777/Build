package app.nami.android.ui

import android.content.res.Configuration
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.nami.android.NamiApp
import app.nami.android.NamiDownloadManager
import app.nami.android.NamiTheme
import app.nami.data.local.NamiDatabase
import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
import app.nami.domain.AnimeRef
import app.nami.domain.AnimeSearchResult
import app.nami.domain.EpisodeRef
import app.nami.domain.ResolvedMedia
import app.nami.runtime.NamiSourceRegistry
import app.nami.runtime.SourceEnablementStore
import app.nami.source.NamiAnimeSource
import app.nami.source.NamiSourceErrorKind
import app.nami.source.NamiSourceException
import app.nami.source.SourceCapabilities
import app.nami.source.SourceMetadata
import app.nami.source.SourceOrigin
import app.nami.source.SourcePage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class NamiProductUiApi36Test {

    @get:Rule
    val composeRule = createComposeRule()

    private val instrumentation by lazy { InstrumentationRegistry.getInstrumentation() }
    private val device by lazy { UiDevice.getInstance(instrumentation) }
    private val targetContext by lazy { instrumentation.targetContext }

    @Test
    fun nativeNamiProductFlowProducesVisualEvidence() {
        val context = targetContext
        val databaseName = "nami-product-ui-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val database = NamiDatabase(context, databaseName)
        val playerClip = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "Nami/nami-player-fixture.mp4",
        )
        assertTrue(playerClip.parentFile?.mkdirs() == true || playerClip.parentFile?.isDirectory == true)
        instrumentation.context.assets.open("nami-player-fixture.mp4").use { input ->
            playerClip.outputStream().use { output -> input.copyTo(output) }
        }
        val playerClipUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.downloads",
            playerClip,
        ).toString()
        val posterFile = File(context.filesDir, "nami-ui-fixture-poster.png")
        instrumentation.context.assets.open("nami-fixture-poster.png").use { input ->
            posterFile.outputStream().use { output -> input.copyTo(output) }
        }
        val source = FixtureNamiSource(playerClipUri, Uri.fromFile(posterFile).toString())
        val registry = NamiSourceRegistry { listOf(source) }
        val enablement = object : SourceEnablementStore {
            override fun isEnabled(sourceId: String) = true
            override fun setEnabled(sourceId: String, enabled: Boolean) = Unit
        }
        try {
            database.addToLibrary(source.detailsFixture)
            database.upsertWatchProgress(
                sourceId = source.metadata.id,
                sourceAnimeId = source.animeRef.sourceAnimeId,
                sourceEpisodeId = source.episodesFixture.first().ref.sourceEpisodeId,
                animeTitle = source.detailsFixture.title,
                episodeTitle = source.episodesFixture.first().title,
                animeSourceState = source.detailsFixture.sourceState,
                episodeSourceState = source.episodesFixture.first().sourceState,
                positionMs = 12_000L,
                durationMs = 30_000L,
                completed = false,
            )
            database.upsertDownload(
                sourceId = source.metadata.id,
                extensionName = source.metadata.extensionName.orEmpty(),
                sourceAnimeId = source.animeRef.sourceAnimeId,
                sourceEpisodeId = source.episodesFixture[1].ref.sourceEpisodeId,
                animeTitle = source.detailsFixture.title,
                episodeTitle = source.episodesFixture[1].title,
                animeSourceState = source.detailsFixture.sourceState,
                episodeSourceState = source.episodesFixture[1].sourceState,
                relativePath = "Anime/Nami UI Fixture/Episode 2.mp4",
                state = "DOWNLOADED",
                progress = 100,
                displayName = "Episode 2.mp4",
                contentUri = playerClipUri,
                mimeType = "video/mp4",
                bytesDownloaded = playerClip.length(),
                totalBytes = playerClip.length(),
            )

            val downloadManager = NamiDownloadManager(context, database, registry)

            composeRule.setContent {
                NamiApp(
                            sourceRegistry = registry,
                            installedSourceRegistry = registry,
                            sourceEnablementStore = enablement,
                            database = database,
                            downloadManager = downloadManager,
                )
            }

            waitForText("Library")
            waitForText("Continue watching")
            waitForText("Nami Fixture")
            capture("01-library.png")

            composeRule.onNodeWithText("Episode 1").performClick()
            waitForText("Episodes")
            waitForText("Resume")
            composeRule.onNodeWithText("Resume").performClick()
            waitForDescription("Nami player video output active", timeoutMillis = 60_000)
            if (!hasDescription("Pause") && !hasDescription("Play")) {
                composeRule.onNodeWithContentDescription(
                    "Nami player video output active",
                ).performClick()
            }
            if (!hasDescription("Pause") && hasDescription("Play")) {
                runCatching {
                    composeRule.onNodeWithContentDescription(
                        "Play",
                        useUnmergedTree = true,
                    ).performClick()
                }.onFailure { error ->
                    // Playback may become active between the semantics read and tap.
                    if (!hasDescription("Pause")) throw error
                }
            }
            waitForDescription("Pause", timeoutMillis = 20_000)
            // The player auto-hides controls during playback; reveal them before
            // asserting the timeline so the UI proof is independent of startup timing.
            if (!hasTag("vlc-seek-bar")) {
                composeRule.onNodeWithContentDescription(
                    "Nami player video output active",
                ).performClick()
            }
            waitForTag("vlc-seek-bar", timeoutMillis = 5_000)
            waitForText("0:12", timeoutMillis = 5_000)
            capture("10-vlc-player.png")

            // Verify the real playback timer hides controls during active playback.
            waitForControlsHidden(timeoutMillis = 20_000)
            capture("10-vlc-player-clean.png")
            composeRule.onNodeWithContentDescription("Nami player video output active").performClick()
            waitForDescription("Pause", timeoutMillis = 15_000)

            composeRule.onNodeWithContentDescription("Fullscreen").performClick()
            waitForOrientation(Configuration.ORIENTATION_LANDSCAPE)
            capture("10-vlc-player-landscape.png")
            composeRule.onNodeWithContentDescription("Subtitles").performClick()
            waitForText("Subtitles")
            waitForText("Off")
            capture("10-vlc-player-subtitles.png")
            device.pressBack()
            waitForDescription("Pause")
            composeRule.onNodeWithContentDescription("Audio").performClick()
            waitForText("Audio")
            capture("10-vlc-player-audio.png")
            device.pressBack()
            waitForDescription("Pause")
            composeRule.onNodeWithContentDescription("Fullscreen").performClick()
            device.setOrientationNatural()
            waitForOrientation(Configuration.ORIENTATION_PORTRAIT)
            device.unfreezeRotation()

            composeRule.onNodeWithContentDescription("Pause").performClick()
            waitForDescription("Play", timeoutMillis = 15_000)
            capture("10-vlc-player-paused.png")
            composeRule.onNodeWithContentDescription("Play").performClick()
            waitForDescription("Pause", timeoutMillis = 15_000)
            composeRule.onNodeWithContentDescription("Seek forward 10 seconds").performClick()
            device.pressBack()
            waitForText("Episodes")
            device.pressBack()
            waitForText("Library")

            clickNavigationIcon("More tab")
            waitForText("More")
            capture("02-more.png")

            val progressBeforeIncognito = database.getWatchProgress(
                source.metadata.id,
                source.episodesFixture.first().ref.sourceEpisodeId,
            ) ?: throw AssertionError("Tracked episode progress disappeared before incognito test")
            composeRule.onNodeWithTag("incognito-toggle").performClick()
            clickNavigationIcon("Library tab")
            waitForText("Continue watching")
            composeRule.onNodeWithText("Episode 1").performClick()
            waitForText("Resume")
            composeRule.onNodeWithText("Resume").performClick()
            waitForDescription("Nami player video output active", timeoutMillis = 60_000)
            when {
                hasDescription("Play") -> {
                    composeRule.onNodeWithContentDescription("Play").performClick()
                }
                !hasDescription("Pause") -> {
                    composeRule.onNodeWithContentDescription(
                        "Nami player video output active",
                    ).performClick()
                }
            }
            waitForDescription("Pause", timeoutMillis = 20_000)
            composeRule.onNodeWithContentDescription("Seek forward 10 seconds").performClick()
            device.pressBack()
            waitForText("Episodes")
            device.pressBack()
            waitForText("Library")
            val progressAfterIncognito = database.getWatchProgress(
                source.metadata.id,
                source.episodesFixture.first().ref.sourceEpisodeId,
            ) ?: throw AssertionError("Tracked episode progress disappeared after incognito playback")
            assertTrue(
                "Incognito playback changed persisted watch position",
                progressAfterIncognito.positionMs == progressBeforeIncognito.positionMs,
            )
            assertTrue(
                "Incognito playback changed completion state",
                progressAfterIncognito.completed == progressBeforeIncognito.completed,
            )

            clickNavigationIcon("More tab")
            waitForText("More")
            composeRule.onNodeWithTag("incognito-toggle").performClick()

            composeRule.onNodeWithText("Downloads").performClick()
            waitForText("Episode 2")
            waitForText("Downloaded")
            capture("03-downloads.png")
            composeRule.onNodeWithText("Episode 2").performClick()
            waitForDescription("Nami player video output active", timeoutMillis = 60_000)
            when {
                hasDescription("Play") -> {
                    composeRule.onNodeWithContentDescription("Play").performClick()
                }
                !hasDescription("Pause") -> {
                    composeRule.onNodeWithContentDescription(
                        "Nami player video output active",
                    ).performClick()
                }
            }
            waitForDescription("Pause", timeoutMillis = 20_000)
            assertTrue(
                "Downloaded playback exposed a redundant stream quality control",
                !hasDescription("Quality"),
            )
            capture("04-offline-playback.png")
            device.pressBack()
            waitForText("Downloads")
            device.pressBack()
            waitForText("More")

            composeRule.onNodeWithText("History").performClick()
            waitForText("History")
            waitForText("Nami Fixture")
            capture("04-history.png")
            device.pressBack()
            waitForText("More")

            composeRule.onNodeWithText("Statistics").performClick()
            waitForText("Anime in library")
            capture("05-statistics.png")
            device.pressBack()
            waitForText("More")

            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Settings"))
            composeRule.onNodeWithText("Settings").performClick()
            waitForText("Sources & extensions")
            waitForText("Fixture Source")
            capture("06-sources.png")
            device.pressBack()
            waitForText("More")

            composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("About Nami"))
            composeRule.onNodeWithText("About Nami").performClick()
            waitForText("Playback engine")
            waitForText("Third-party notices")
            composeRule.onNodeWithText("Third-party notices").performClick()
            waitForText("Nami third-party notices")
            capture("06-about-licenses.png")
            device.pressBack()
            waitForText("About Nami")
            device.pressBack()
            waitForText("More")

            clickNavigationIcon("Browse tab")
            waitForTag("global-search-field")
            capture("07-browse.png")

            composeRule.onNodeWithTag("global-search-field").performTextInput("fixture")
            composeRule.onNodeWithTag("global-search-field").performImeAction()
            waitForDescription("Open anime: Nami Fixture")
            capture("08-search-results.png")

            composeRule.onNodeWithContentDescription("Open anime: Nami Fixture").performClick()
            waitForText("Episodes")
            waitForText("Episode 1")
            capture("09-details-episodes.png")

            composeRule.onNodeWithContentDescription("Play Episode 2")
                .performScrollTo()
                .performClick()
            waitForDescription("Nami player video output active", timeoutMillis = 60_000)
            when {
                hasDescription("Play") -> {
                    composeRule.onNodeWithContentDescription("Play").performClick()
                }
                !hasDescription("Pause") -> {
                    composeRule.onNodeWithContentDescription(
                        "Nami player video output active",
                    ).performClick()
                }
            }
            waitForDescription("Pause", timeoutMillis = 20_000)
            capture("10-vlc-player.png")
            composeRule.onNodeWithContentDescription("Pause").performClick()
            waitForDescription("Play", timeoutMillis = 15_000)
            composeRule.onNodeWithContentDescription("Play").performClick()
            waitForDescription("Pause", timeoutMillis = 15_000)
            composeRule.onNodeWithContentDescription("Seek forward 10 seconds").performClick()
            device.pressBack()
            waitForText("Episodes")

            device.pressBack()
            waitForTag("global-search-field")
            composeRule.onNodeWithTag("global-search-field").performTextClearance()
            composeRule.onNodeWithTag("global-search-field").performTextInput("network error")
            composeRule.onNodeWithTag("global-search-field").performImeAction()
            waitForText("Network error. Check your connection and try again.")
            waitForText("Retry")
            capture("11-source-error.png")
            composeRule.onNodeWithText("Retry").performClick()
            waitForText("Network error. Check your connection and try again.")
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
            playerClip.delete()
            posterFile.delete()
        }
    }

    private fun clickNavigationIcon(description: String) {
        waitForDescription(description)
        composeRule.onNodeWithContentDescription(
            description,
            useUnmergedTree = true,
        ).performClick()
    }

    private fun waitForText(text: String, timeoutMillis: Long = 20_000L) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000L) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            }.isSuccess
        }
    }

    private fun hasTag(tag: String): Boolean =
        runCatching {
            composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        }.isSuccess

    private fun waitForOrientation(
        orientation: Int,
        timeoutMillis: Long = 20_000L,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            targetContext.resources.configuration.orientation == orientation
        }
    }

    private fun waitForDescription(description: String, timeoutMillis: Long = 20_000L) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithContentDescription(
                description,
                useUnmergedTree = true,
            ).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun hasDescription(description: String): Boolean =
        composeRule.onAllNodesWithContentDescription(
            description,
            useUnmergedTree = true,
        ).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun waitForControlsHidden(timeoutMillis: Long = 15_000L) {
        composeRule.waitUntil(timeoutMillis) {
            !hasDescription("Pause") &&
                !hasDescription("Play") &&
                !hasDescription("Fullscreen")
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val directory = File(targetContext.filesDir, "nami-product-screenshots")
        assertTrue(directory.mkdirs() || directory.isDirectory)

        // AOSP ATD can return a syntactically valid all-black adb screencap even while
        // Compose semantics are alive. Capture the actual Compose root instead so visual
        // evidence proves rendered Nami UI rather than merely proving the display surface exists.
        val bitmap = composeRule.onRoot(useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(
            pixels,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height,
        )
        assertTrue(
            "Visual evidence must contain rendered product pixels",
            pixels.any { pixel -> pixel and 0x00FFFFFF != 0 },
        )

        val destination = File(directory, name)
        FileOutputStream(destination).use { output ->
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
        assertTrue(destination.isFile && destination.length() > 0L)
    }


    private class FixtureNamiSource(
        private val playerClipUri: String,
        private val posterUri: String,
    ) : NamiAnimeSource {
        override val metadata = SourceMetadata(
            id = "native:fixture",
            name = "Fixture Source",
            language = "en",
            origin = SourceOrigin.NATIVE_NAMI,
            extensionName = "Nami UI Fixture",
            extensionVersion = "1.0.0",
            extensionApiVersion = 1,
            capabilities = SourceCapabilities(
                searchable = true,
                browsable = true,
                popular = true,
                latest = true,
                details = true,
                episodes = true,
                streamable = true,
                downloadable = true,
            ),
        )

        val animeRef = AnimeRef(metadata.id, "fixture-anime")
        val detailsFixture = AnimeDetails(
            ref = animeRef,
            title = "Nami Fixture",
            coverUrl = posterUri,
            description = "A deterministic native Nami source used only for product UI validation.",
            metadata = mapOf("Status" to "Currently Airing", "Season" to "Test Season"),
            genres = listOf("Action", "Adventure"),
            sourceState = """{"fixture":"anime"}""",
        )
        val episodesFixture = listOf(
            AnimeEpisode(
                ref = EpisodeRef(metadata.id, animeRef.sourceAnimeId, "episode-1"),
                title = "Episode 1",
                number = 1.0,
                sourceState = """{"fixture":"episode-1"}""",
            ),
            AnimeEpisode(
                ref = EpisodeRef(metadata.id, animeRef.sourceAnimeId, "episode-2"),
                title = "Episode 2",
                number = 2.0,
                sourceState = """{"fixture":"episode-2"}""",
            ),
        )

        override suspend fun search(query: String, page: Int): SourcePage<AnimeSearchResult> {
            if (query.equals("network error", ignoreCase = true)) {
                throw NamiSourceException(
                    kind = NamiSourceErrorKind.NETWORK,
                    message = "Fixture source is offline.",
                )
            }
            return SourcePage(listOf(searchResult()), false)
        }

        override suspend fun popular(page: Int) =
            SourcePage(listOf(searchResult()), false)

        override suspend fun latest(page: Int) =
            SourcePage(listOf(searchResult()), false)

        override suspend fun details(anime: AnimeRef): AnimeDetails = detailsFixture

        override suspend fun episodes(anime: AnimeRef): List<AnimeEpisode> = episodesFixture

        override suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia> =
            listOf(
                ResolvedMedia(
                    url = playerClipUri,
                    mimeType = "video/mp4",
                    quality = "720p",
                ),
            )

        private fun searchResult() = AnimeSearchResult(
            ref = animeRef,
            title = detailsFixture.title,
            coverUrl = posterUri,
            description = detailsFixture.description,
            sourceState = detailsFixture.sourceState,
        )
    }
}
