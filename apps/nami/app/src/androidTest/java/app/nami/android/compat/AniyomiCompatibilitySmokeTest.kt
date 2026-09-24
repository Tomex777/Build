package app.nami.android.compat

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nami.compat.aniyomi.AniyomiExtensionRegistry
import app.nami.domain.AnimeRef
import app.nami.runtime.CompositeNamiSourceRegistry
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
    fun realAnimeTakeExtensionAndNativeJikanShareNamiPipeline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val start = System.nanoTime()
        val installed = AniyomiExtensionRegistry(context).installedSources()
        val animeTake = installed.firstOrNull {
            it.metadata.extensionPackage == "eu.kanade.tachiyomi.animeextension.en.animetake"
        }
        assertNotNull("AnimeTake v16.20 must be discovered from its installed APK", animeTake)
        animeTake!!
        assertEquals("16.20", animeTake.metadata.extensionVersion)
        assertEquals(16, animeTake.metadata.extensionApiVersion)

        val jikan = JikanAnimeSource()
        val combined = CompositeNamiSourceRegistry(
            NamiSourceRegistry { installed },
            NamiSourceRegistry { listOf(jikan) },
        )
        val query = "Bleach"
        val search = withTimeout(90_000) { GlobalAnimeSearch(combined).search(query) }
        val extensionResults = search.resultsBySource[animeTake.metadata.id].orEmpty()
        val nativeResults = search.resultsBySource[jikan.metadata.id].orEmpty()
        assertTrue("AnimeTake returned no real results for $query", extensionResults.isNotEmpty())
        assertTrue("Jikan returned no real results for $query", nativeResults.isNotEmpty())

        val anime = extensionResults.firstOrNull { it.title.contains(query, ignoreCase = true) }
            ?: throw AssertionError("AnimeTake results did not contain $query")
        val details = withTimeout(60_000) { animeTake.details(anime.ref) }
        assertTrue("Anime details title is empty", details.title.isNotBlank())
        val episodes = withTimeout(60_000) { animeTake.episodes(anime.ref) }
        assertTrue("AnimeTake returned no episodes", episodes.isNotEmpty())

        var resolvedCount = 0
        for (episode in episodes.take(3)) {
            resolvedCount = withTimeout(60_000) { animeTake.resolve(episode.ref).size }
            if (resolvedCount > 0) break
        }
        assertTrue("AnimeTake did not resolve a stream from the first three episodes", resolvedCount > 0)

        val nativeAnime = nativeResults.firstOrNull { it.title.contains(query, ignoreCase = true) }
            ?: throw AssertionError("Jikan results did not contain $query")
        val nativeDetails = withTimeout(60_000) { jikan.details(nativeAnime.ref) }
        val nativeEpisodes = withTimeout(60_000) { jikan.episodes(AnimeRef(jikan.metadata.id, nativeDetails.ref.sourceAnimeId)) }
        assertTrue("Jikan details did not normalize into Nami models", nativeDetails.title.isNotBlank())
        assertTrue("Jikan did not return episode metadata", nativeEpisodes.isNotEmpty())

        val elapsed = (System.nanoTime() - start) / 1_000_000
        Log.i(
            "NamiSourceSmoke",
            "query=$query extension=${animeTake.metadata.extensionPackage} " +
                "extensionResults=${extensionResults.size} nativeResults=${nativeResults.size} " +
                "episodes=${episodes.size} resolvedStreams=$resolvedCount nativeEpisodes=${nativeEpisodes.size} " +
                "failures=${search.failures.map { it.sourceId + ":" + it.cause.javaClass.simpleName }} " +
                "elapsedMs=$elapsed",
        )
    }
}
