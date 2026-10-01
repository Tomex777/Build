package app.mira.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mira.domain.ContentKind
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealSourceSmokeTest {
    @Test
    fun realMovieAndTvContractsReturnUsableData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = context.applicationContext as MiraApplication
        val movieSource = application.sources.first { it.metadata.capabilities.movieStreaming }
        val tvSource = application.sources.first { it.metadata.capabilities.series }

        val movieResults = withTimeout(60_000) {
            movieSource.search("Night of the Living Dead", 1)
        }
        assertTrue("Internet Archive movie search returned nothing", movieResults.items.isNotEmpty())
        val movie = movieResults.items.first()
        assertEquals(ContentKind.MOVIE, movie.ref.kind)

        val movieDetails = withTimeout(60_000) {
            movieSource.details(movie.ref, movie.sourceState)
        }
        assertTrue(movieDetails.title.isNotBlank())

        val streams = withTimeout(60_000) {
            movieSource.resolveMovie(movie.ref, movieDetails.sourceState)
        }
        assertTrue("Movie source did not resolve a playable file", streams.isNotEmpty())
        assertTrue(streams.first().url.startsWith("https://archive.org/"))

        val tvResults = withTimeout(60_000) {
            tvSource.search("House", 1)
        }
        assertTrue("TVMaze search returned nothing", tvResults.items.isNotEmpty())
        val house = tvResults.items.firstOrNull {
            it.title.equals("House", ignoreCase = true)
        } ?: tvResults.items.first()
        assertEquals(ContentKind.SERIES, house.ref.kind)

        val tvDetails = withTimeout(60_000) {
            tvSource.details(house.ref, house.sourceState)
        }
        assertTrue(tvDetails.title.isNotBlank())

        val seasons = withTimeout(60_000) {
            tvSource.seasons(house.ref, tvDetails.sourceState)
        }
        assertTrue("TV source returned no seasons", seasons.isNotEmpty())

        val episodes = withTimeout(60_000) {
            tvSource.episodes(seasons.first().ref, seasons.first().sourceState)
        }
        assertTrue("TV source returned no episodes", episodes.isNotEmpty())
        assertTrue(episodes.first().title.isNotBlank())
    }
}
