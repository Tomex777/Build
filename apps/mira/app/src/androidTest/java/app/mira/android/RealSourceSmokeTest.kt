package app.mira.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mira.domain.ContentKind
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val media = streams.first()
        withContext(Dispatchers.IO) {
            val connection = URL(media.url).openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Range", "bytes=0-1023")
            media.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            try {
                assertEquals("Media CDN must honor byte ranges", 206, connection.responseCode)
                assertTrue("Invalid content range", connection.getHeaderField("Content-Range").orEmpty().startsWith("bytes 0-"))
                val prefix = connection.inputStream.use { input ->
                    val bytes = ByteArray(1024)
                    var count = 0
                    while (count < bytes.size) {
                        val read = input.read(bytes, count, bytes.size - count)
                        if (read < 0) break
                        count += read
                    }
                    bytes.copyOf(count)
                }
                assertTrue("Media response is too short", prefix.size >= 16)
                val mp4 = prefix.copyOfRange(4, 8).contentEquals("ftyp".toByteArray(Charsets.US_ASCII))
                val ebml = prefix.take(4).map { it.toInt() and 255 } == listOf(0x1a, 0x45, 0xdf, 0xa3)
                assertTrue("Resolved URL did not return an MP4/WebM/Matroska container", mp4 || ebml)
            } finally {
                connection.disconnect()
            }
        }


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
