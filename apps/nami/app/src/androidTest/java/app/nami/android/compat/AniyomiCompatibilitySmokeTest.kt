package app.nami.android.compat

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nami.compat.aniyomi.AniyomiExtensionRegistry
import app.nami.domain.AnimeRef
import app.nami.runtime.GlobalAnimeSearch
import app.nami.runtime.NamiSourceRegistry
import app.nami.source.jikan.JikanAnimeSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AniyomiCompatibilitySmokeTest {

    @Test
    fun nativeJikanSearchDetailsAndEpisodesUseNamiContracts() = runBlocking<Unit> {
        val start = System.nanoTime()
        val jikan = JikanAnimeSource()
        val query = "Bleach"
        val nativeResults = withTimeout(60_000) { jikan.search(query).items }
        assertTrue("Jikan returned no real results for $query", nativeResults.isNotEmpty())

        val nativeAnime = nativeResults.firstOrNull { it.title.contains(query, ignoreCase = true) }
            ?: throw AssertionError("Jikan results did not contain $query")
        val nativeDetails = withTimeout(60_000) { jikan.details(nativeAnime.ref) }
        val nativeEpisodes = withTimeout(60_000) {
            jikan.episodes(AnimeRef(jikan.metadata.id, nativeDetails.ref.sourceAnimeId))
        }
        assertTrue("Jikan details did not normalize into Nami models", nativeDetails.title.isNotBlank())
        assertTrue("Jikan did not return episode metadata", nativeEpisodes.isNotEmpty())

        val elapsed = (System.nanoTime() - start) / 1_000_000
        Log.i(
            "NamiSourceSmoke",
            "native=\${jikan.metadata.id} query=$query results=\${nativeResults.size} " +
                "episodes=\${nativeEpisodes.size} elapsedMs=$elapsed",
        )
    }

    @Test
    fun realAnimeSogoAndNativeJikanShareGlobalSearch() = runBlocking<Unit> {
        val start = System.nanoTime()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val installed = AniyomiExtensionRegistry(context).installedSources()
        val animeSogo = installed.firstOrNull {
            it.metadata.extensionPackage == "eu.kanade.tachiyomi.animeextension.en.animesogo"
        }
        assertNotNull("AnimeSogo v16.8 must be discovered from its installed APK", animeSogo)
        animeSogo!!
        assertEquals("16.8", animeSogo.metadata.extensionVersion)
        assertEquals(16, animeSogo.metadata.extensionApiVersion)
        println("NamiSourceSmoke: discovered \${installed.size} sources; v16=\${animeSogo.metadata.id}")

        val jikan = JikanAnimeSource()
        val query = "Bleach"
        println("NamiSourceSmoke: combined global search started for $query")
        val search = withTimeout(120_000) {
            GlobalAnimeSearch(NamiSourceRegistry { installed + jikan }).search(query)
        }
        Log.i(
            "NamiSourceSmoke",
            "globalSearch sources=\${search.resultsBySource.keys} " +
                "counts=\${search.resultsBySource.mapValues { it.value.size }} " +
                "failures=\${search.failures.map { it.sourceId + ":" + it.stage + ":" + it.cause.javaClass.simpleName + ":" + it.cause.message }}",
        )
        search.failures.forEach { failure ->
            Log.e(
                "NamiSourceSmoke",
                "source=\${failure.sourceId} stage=\${failure.stage}",
                failure.cause,
            )
        }

        val extensionResults = search.resultsBySource[animeSogo.metadata.id].orEmpty()
        val nativeResults = search.resultsBySource[jikan.metadata.id].orEmpty()
        assertTrue("AnimeSogo returned no real global-search results for $query", extensionResults.isNotEmpty())
        val jikanFailure = search.failures.firstOrNull { it.sourceId == jikan.metadata.id }
        assertTrue(
            "Global search did not record a native Jikan result or isolated source failure",
            nativeResults.isNotEmpty() || jikanFailure != null,
        )

        val anime = extensionResults.firstOrNull { it.title.contains(query, ignoreCase = true) }
            ?: throw AssertionError("AnimeSogo results did not contain $query")
        println("NamiSourceSmoke: AnimeSogo result selected: \${anime.title}; loading details")
        val details = withTimeout(60_000) { animeSogo.details(anime.ref) }
        assertTrue("Anime details title is empty", details.title.isNotBlank())
        println("NamiSourceSmoke: details loaded: \${details.title}; loading episodes")

        val episodes = withTimeout(60_000) { animeSogo.episodes(anime.ref) }
        assertTrue("AnimeSogo returned no episodes", episodes.isNotEmpty())
        println("NamiSourceSmoke: episodes loaded: \${episodes.size}; resolving first three")
        var resolvedCount = 0
        for (episode in episodes.take(3)) {
            resolvedCount = withTimeout(60_000) { animeSogo.resolve(episode.ref).size }
            if (resolvedCount > 0) break
        }
        assertTrue("AnimeSogo did not resolve a stream from the first three episodes", resolvedCount > 0)

        val elapsed = (System.nanoTime() - start) / 1_000_000
        Log.i(
            "NamiSourceSmoke",
            "query=$query extensionV16=\${animeSogo.metadata.extensionPackage} " +
                "extensionResults=\${extensionResults.size} nativeResults=\${nativeResults.size} " +
                "episodes=\${episodes.size} resolvedStreams=$resolvedCount " +
                "failures=\${search.failures.map { it.sourceId + ":" + it.cause.javaClass.simpleName }} " +
                "elapsedMs=$elapsed",
        )
        Unit
    }

}
