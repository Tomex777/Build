package app.nami.android.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
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
import app.nami.source.SourceCapabilities
import app.nami.source.SourceMetadata
import app.nami.source.SourceOrigin
import app.nami.source.SourcePage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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
        val source = FixtureNamiSource()
        val registry = NamiSourceRegistry { listOf(source) }
        val enablement = object : SourceEnablementStore {
            override fun isEnabled(sourceId: String) = true
            override fun setEnabled(sourceId: String, enabled: Boolean) = Unit
        }
        val downloadManager = NamiDownloadManager(context, database, registry)

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
                positionMs = 412_000L,
                durationMs = 1_320_000L,
                completed = false,
            )

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

            clickNavigationIcon("More tab")
            waitForText("More")
            capture("02-more.png")

            composeRule.onNodeWithText("Downloads").performClick()
            waitForText("No downloads")
            capture("03-downloads.png")
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

            composeRule.onNodeWithText("Settings").performClick()
            waitForText("Sources & extensions")
            waitForText("Fixture Source")
            capture("06-sources.png")
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
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
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
            runCatching { composeRule.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess
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

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val directory = File(targetContext.filesDir, "nami-product-screenshots")
        assertTrue(directory.mkdirs() || directory.isDirectory)
        assertTrue(device.takeScreenshot(File(directory, name)))
    }


    private class FixtureNamiSource : NamiAnimeSource {
        override val metadata = SourceMetadata(
            id = "native:fixture",
            name = "Fixture Source",
            language = "en",
            origin = SourceOrigin.NATIVE_NAMI,
            extensionName = "Nami UI Fixture",
            extensionVersion = "1.0",
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

        override suspend fun search(query: String, page: Int) =
            SourcePage(listOf(searchResult()), false)

        override suspend fun popular(page: Int) =
            SourcePage(listOf(searchResult()), false)

        override suspend fun latest(page: Int) =
            SourcePage(listOf(searchResult()), false)

        override suspend fun details(anime: AnimeRef): AnimeDetails = detailsFixture

        override suspend fun episodes(anime: AnimeRef): List<AnimeEpisode> = episodesFixture

        override suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia> =
            listOf(
                ResolvedMedia(
                    url = "https://example.invalid/nami-fixture.mp4",
                    quality = "720p",
                ),
            )

        private fun searchResult() = AnimeSearchResult(
            ref = animeRef,
            title = detailsFixture.title,
            description = detailsFixture.description,
            sourceState = detailsFixture.sourceState,
        )
    }
}
