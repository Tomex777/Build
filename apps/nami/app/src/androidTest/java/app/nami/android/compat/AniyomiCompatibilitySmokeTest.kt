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
    fun installedV16AndV17ExtensionsPlusNativeSourceShareNamiContracts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val start = System.nanoTime()
        val installed = AniyomiExtensionRegistry(context).installedSources()

        val animeSogo = installed.firstOrNull {
            it.metadata.extensionPackage == "eu.kanade.tachiyomi.animeextension.en.animesogo"
        }
        assertNotNull("AnimeSogo v16.8 must be discovered from its installed APK", animeSogo)
        animeSogo!!
        assertEquals("16.8", animeSogo.metadata.extensionVersion)
        assertEquals(16, animeSogo.metadata.extensionApiVersion)

        val v17Fixture = installed.firstOrNull {
            it.metadata.extensionPackage == "app.nami.fixture.v17"
        }
        assertNotNull("The separately installed v17 fixture APK must be discovered", v17Fixture)
        v17Fixture!!
        assertEquals(17, v17Fixture.metadata.extensionApiVersion)

        println(
            "NamiSourceSmoke: discovered ${installed.size} sources; " +
                "v16=${animeSogo.metadata.id}; v17=${v17Fixture.metadata.id}",
        )

        val fixtureSearch = withTimeout(10_000) { v17Fixture.search("Bleach").items }
        assertEquals(1, fixtureSearch.size)
        assertTrue(fixtureSearch.single().title.contains("Bleach", ignoreCase = true))

        val fixtureDetails = withTimeout(10_000) {
            v17Fixture.details(fixtureSearch.single().ref)
        }
        assertEquals("Fixture Details", fixtureDetails.title)

        val fixtureEpisodes = withTimeout(10_000) {
            v17Fixture.episodes(fixtureSearch.single().ref)
        }
        assertEquals(1, fixtureEpisodes.size)
        assertEquals("Fixture Episode 1", fixtureEpisodes.single().title)

        val fixtureMedia = withTimeout(10_000) {
            v17Fixture.resolve(fixtureEpisodes.single().ref)
        }
        assertEquals(1, fixtureMedia.size)
        assertEquals("https://example.invalid/fixture-v17.mp4", fixtureMedia.single().url)
        println("NamiSourceSmoke: v17 fixture completed search/details/episodes/resolve")

        val jikan = JikanAnimeSource()
        val query = "Bleach"
        println("NamiSourceSmoke: extension-only global search started for $query")
        val search = withTimeout(90_000) {
            GlobalAnimeSearch(NamiSourceRegistry { installed }).search(query)
        }
        println(
            "NamiSourceSmoke: global search completed; " +
                "sources=${search.resultsBySource.keys}; " +
                "failures=${search.failures.map { it.sourceId + ":" + it.cause.javaClass.simpleName }}",
        )

        val extensionResults = search.resultsBySource[animeSogo.metadata.id].orEmpty()
        assertTrue("AnimeSogo returned no real results for $query", extensionResults.isNotEmpty())
        assertTrue(
            "Installed v17 fixture should participate in extension-only global search",
            search.resultsBySource.containsKey(v17Fixture.metadata.id),
        )
        assertTrue(
            "Extension-only global search leaked a native source",
            search.resultsBySource.keys.none { it == jikan.metadata.id },
        )

        println("NamiSourceSmoke: native Jikan search started separately")
        val nativeResults = withTimeout(60_000) { jikan.search(query).items }
        assertTrue("Jikan returned no real results for $query", nativeResults.isNotEmpty())

        val anime = extensionResults.firstOrNull { it.title.contains(query, ignoreCase = true) }
            ?: throw AssertionError("AnimeSogo results did not contain $query")
        println("NamiSourceSmoke: AnimeSogo result selected: ${anime.title}; loading details")
        val details = withTimeout(60_000) { animeSogo.details(anime.ref) }
        println("NamiSourceSmoke: details loaded: ${details.title}; loading episodes")
        assertTrue("Anime details title is empty", details.title.isNotBlank())

        val episodes = withTimeout(60_000) { animeSogo.episodes(anime.ref) }
        println("NamiSourceSmoke: episodes loaded: ${episodes.size}; resolving first three")
        assertTrue("AnimeSogo returned no episodes", episodes.isNotEmpty())

        var resolvedCount = 0
        for (episode in episodes.take(3)) {
            resolvedCount = withTimeout(60_000) { animeSogo.resolve(episode.ref).size }
            if (resolvedCount > 0) break
        }
        assertTrue("AnimeSogo did not resolve a stream from the first three episodes", resolvedCount > 0)
        println("NamiSourceSmoke: v16 resolved stream count=$resolvedCount")

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
            "query=$query extensionV16=${animeSogo.metadata.extensionPackage} " +
                "extensionV17=${v17Fixture.metadata.extensionPackage} " +
                "extensionResults=${extensionResults.size} nativeResults=${nativeResults.size} " +
                "episodes=${episodes.size} resolvedStreams=$resolvedCount " +
                "nativeEpisodes=${nativeEpisodes.size} " +
                "failures=${search.failures.map { it.sourceId + ":" + it.cause.javaClass.simpleName }} " +
                "elapsedMs=$elapsed",
        )
    }
}
