package app.nami.android

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import app.nami.domain.AnimeDetails
import app.nami.domain.AnimeEpisode
import app.nami.domain.AnimeRef
import app.nami.domain.AnimeSearchResult
import app.nami.domain.EpisodeRef
import app.nami.domain.ResolvedMedia
import app.nami.runtime.NamiSourceRegistry
import app.nami.source.NamiAnimeSource
import app.nami.source.SourceCapabilities
import app.nami.source.SourceMetadata
import app.nami.source.SourceOrigin
import app.nami.source.SourcePage
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GlobalSearchOrderingUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completedSourcesMoveAboveStillLoadingSourcesAsTheyFinish() {
        val alpha = ControlledSource("alpha", "Alpha")
        val beta = ControlledSource("beta", "Beta")
        val zulu = ControlledSource("zulu", "Zulu")
        val registry = NamiSourceRegistry { listOf(zulu, alpha, beta) }

        composeRule.setContent {
            NamiTheme {
                GlobalSearchHome(
                    sourceRegistry = registry,
                    onOpenSource = { _, _ -> },
                    onOpenAnime = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("global-search-field").performTextInput("Bleach")
        composeRule.onNodeWithTag("global-search-field").performImeAction()

        waitForAllSources()
        assertAbove("Alpha", "Beta")
        assertAbove("Beta", "Zulu")

        zulu.complete()
        waitForOrder("Zulu", "Alpha", "Beta")

        beta.complete()
        waitForOrder("Zulu", "Beta", "Alpha")

        alpha.complete()
        waitForOrder("Zulu", "Beta", "Alpha")
    }

    private fun waitForAllSources() {
        composeRule.waitUntil(10_000) {
            listOf("Alpha", "Beta", "Zulu").all { name ->
                runCatching { sourceNode(name).fetchSemanticsNode() }.isSuccess
            }
        }
    }

    private fun waitForOrder(first: String, second: String, third: String) {
        composeRule.waitUntil(10_000) {
            val firstTop = sourceBounds(first)?.top ?: return@waitUntil false
            val secondTop = sourceBounds(second)?.top ?: return@waitUntil false
            val thirdTop = sourceBounds(third)?.top ?: return@waitUntil false
            firstTop < secondTop && secondTop < thirdTop
        }
        assertAbove(first, second)
        assertAbove(second, third)
    }

    private fun assertAbove(first: String, second: String) {
        val firstBounds = sourceBounds(first)
            ?: throw AssertionError("Missing source section $first")
        val secondBounds = sourceBounds(second)
            ?: throw AssertionError("Missing source section $second")
        assertTrue(
            "Expected $first above $second, but bounds were $firstBounds and $secondBounds",
            firstBounds.top < secondBounds.top,
        )
    }

    private fun sourceBounds(name: String): Rect? =
        runCatching {
            sourceNode(name).fetchSemanticsNode().boundsInRoot
        }.getOrNull()

    private fun sourceNode(name: String): SemanticsNodeInteraction =
        composeRule.onNodeWithContentDescription(
            "Global search source: $name",
            useUnmergedTree = true,
        )

    private class ControlledSource(
        id: String,
        name: String,
    ) : NamiAnimeSource {
        private val completion = CompletableDeferred<Unit>()

        override val metadata = SourceMetadata(
            id = id,
            name = name,
            language = "en",
            origin = SourceOrigin.ANIYOMI_COMPATIBLE,
            capabilities = SourceCapabilities(searchable = true),
        )

        fun complete() {
            completion.complete(Unit)
        }

        override suspend fun search(
            query: String,
            page: Int,
        ): SourcePage<AnimeSearchResult> {
            completion.await()
            return SourcePage(emptyList(), hasNextPage = false)
        }

        override suspend fun details(anime: AnimeRef): AnimeDetails =
            error("Not used by global search ordering test")

        override suspend fun episodes(anime: AnimeRef): List<AnimeEpisode> =
            error("Not used by global search ordering test")

        override suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia> =
            error("Not used by global search ordering test")
    }
}
