package app.mira.android

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mira.domain.ContentKind
import app.mira.domain.ContentRef
import app.mira.domain.ContentSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MinSdk26SmokeTest {
    @Test
    fun api26CanInitializeMira() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val application = context.applicationContext as MiraApplication
        assertEquals(26, Build.VERSION.SDK_INT)
        assertEquals("app.mira.android", context.packageName)
        assertEquals(2, application.sources.size)
        assertTrue(application.sources.map { it.metadata.id }.distinct().size == application.sources.size)
    }

    @Test
    fun librarySurvivesStoreRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("mira_library", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        try {
            val movie = ContentSearchResult(
                ref = ContentRef(
                    sourceId = "fixture-movies",
                    sourceContentId = "movie-1",
                    kind = ContentKind.MOVIE,
                ),
                title = "Fixture Movie",
                posterUrl = "https://example.invalid/movie.jpg",
                year = 1999,
                description = "Movie persistence proof",
                sourceState = "movie-state",
            )
            val series = ContentSearchResult(
                ref = ContentRef(
                    sourceId = "fixture-tv",
                    sourceContentId = "series-1",
                    kind = ContentKind.SERIES,
                ),
                title = "Fixture Series",
                year = 2004,
                sourceState = "series-state",
            )

            val first = MiraLibraryStore(context)
            first.add(movie)
            first.add(series)
            assertTrue(first.contains(movie.ref))
            assertTrue(first.contains(series.ref))

            val restored = MiraLibraryStore(context)
            assertEquals(2, restored.items.value.size)
            assertEquals(movie, restored.items.value.first { it.ref == movie.ref })
            assertEquals(series, restored.items.value.first { it.ref == series.ref })

            restored.remove(movie.ref)
            val afterRemove = MiraLibraryStore(context)
            assertFalse(afterRemove.contains(movie.ref))
            assertTrue(afterRemove.contains(series.ref))
            assertEquals(1, afterRemove.items.value.size)
        } finally {
            context.getSharedPreferences("mira_library", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }
}
