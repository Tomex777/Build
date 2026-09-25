package app.nami.android.compat

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nami.compat.aniyomi.AniyomiExtensionRegistry
import app.nami.data.local.NamiDatabase
import app.nami.domain.AnimeDetails
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
import java.io.File

@RunWith(AndroidJUnit4::class)
class AniyomiCompatibilitySmokeTest {

    @Test
    fun nativeJikanSearchDetailsAndEpisodesUseNamiContracts() = runBlocking<Unit> {
        val start = System.nanoTime()
        val jikan = JikanAnimeSource()
        val query = "Bleach"
        val nativeResults = try {
            withTimeout(60_000) { jikan.search(query).items }
        } catch (failure: Exception) {
            if (failure.message.orEmpty().contains("HTTP 504")) {
                Log.w("NamiSourceSmoke", "Native Jikan probe unavailable (HTTP 504); skipping live metadata assertions")
                org.junit.Assume.assumeNoException("Jikan returned HTTP 504", failure)
            }
            throw failure
        }
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
            "native=${jikan.metadata.id} query=$query results=${nativeResults.size} " +
                "episodes=${nativeEpisodes.size} elapsedMs=$elapsed",
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
        println("NamiSourceSmoke: discovered ${installed.size} sources; v16=${animeSogo.metadata.id}")

        val jikan = JikanAnimeSource()
        val query = "Bleach"
        println("NamiSourceSmoke: combined global search started for $query")
        val search = withTimeout(120_000) {
            GlobalAnimeSearch(NamiSourceRegistry { installed }).search(query)
        }
        Log.i(
            "NamiSourceSmoke",
            "globalSearch sources=${search.resultsBySource.keys} " +
                "counts=${search.resultsBySource.mapValues { it.value.size }} " +
                "failures=${search.failures.map { it.sourceId + ":" + it.stage + ":" + it.cause.javaClass.simpleName + ":" + it.cause.message }}",
        )
        search.failures.forEach { failure ->
            Log.e(
                "NamiSourceSmoke",
                "source=${failure.sourceId} stage=${failure.stage}",
                failure.cause,
            )
        }

        val extensionResults = search.resultsBySource[animeSogo.metadata.id].orEmpty()
        assertTrue("AnimeSogo returned no real global-search results for $query", extensionResults.isNotEmpty())

        val anime = extensionResults.firstOrNull { it.title.contains(query, ignoreCase = true) }
            ?: throw AssertionError("AnimeSogo results did not contain $query")
        println("NamiSourceSmoke: AnimeSogo result selected: ${anime.title}; loading details")
        val details = withTimeout(60_000) { animeSogo.details(anime.ref) }
        assertTrue("Anime details title is empty", details.title.isNotBlank())
        println("NamiSourceSmoke: details loaded: ${details.title}; loading episodes")

        val episodes = withTimeout(60_000) { animeSogo.episodes(anime.ref) }
        assertTrue("AnimeSogo returned no episodes", episodes.isNotEmpty())
        assertTrue("AnimeSogo episode identifiers are blank", episodes.all { it.ref.sourceEpisodeId.isNotBlank() })
        assertEquals(
            "AnimeSogo episode identifiers are not unique",
            episodes.size,
            episodes.map { it.ref.sourceEpisodeId }.distinct().size,
        )
        assertTrue("AnimeSogo episode names did not normalize", episodes.all { it.title.isNotBlank() })
        assertTrue(
            "AnimeSogo episode numbering did not normalize",
            episodes.any { episode -> episode.number?.let { it >= 0.0 } == true },
        )
        val episodeIdsAgain = withTimeout(60_000) { animeSogo.episodes(anime.ref) }
            .map { it.ref.sourceEpisodeId }
        assertEquals("AnimeSogo episode IDs changed between fetches", episodes.map { it.ref.sourceEpisodeId }, episodeIdsAgain)
        println("NamiSourceSmoke: episodes loaded: ${episodes.size}; resolving first three")

        var resolvedEpisode: app.nami.domain.AnimeEpisode? = null
        var resolvedStreams = emptyList<app.nami.domain.ResolvedMedia>()
        for (episode in episodes.take(3)) {
            val candidates = withTimeout(60_000) { animeSogo.resolve(episode.ref) }
            if (candidates.any { it.url.startsWith("http://") || it.url.startsWith("https://") }) {
                resolvedEpisode = episode
                resolvedStreams = candidates
                break
            }
        }
        assertTrue("AnimeSogo did not resolve a stream from the first three episodes", resolvedStreams.isNotEmpty())
        assertTrue(
            "Resolved stream candidate did not contain an HTTP URL",
            resolvedStreams.any { it.url.startsWith("http://") || it.url.startsWith("https://") },
        )

        val elapsed = (System.nanoTime() - start) / 1_000_000
        Log.i(
            "NamiSourceSmoke",
            "query=$query extensionV16=${animeSogo.metadata.extensionPackage} " +
                "extensionResults=${extensionResults.size} anime=${details.title} " +
                "episodes=${episodes.size} firstEpisode=${episodes.first().title} " +
                "firstNumber=${episodes.first().number} firstId=${episodes.first().ref.sourceEpisodeId} " +
                "resolvedEpisode=${resolvedEpisode?.title} resolvedNumber=${resolvedEpisode?.number} " +
                "resolvedStreams=${resolvedStreams.size} hosters=${resolvedStreams.mapNotNull { it.hosterName }.distinct()} " +
                "quality=${resolvedStreams.mapNotNull { it.quality }.distinct()} " +
                "mediaTypes=${resolvedStreams.mapNotNull { it.mimeType }.distinct()} " +
                "headerNames=${resolvedStreams.flatMap { it.headers.keys }.distinct()} " +
                "subtitles=${resolvedStreams.sumOf { it.subtitles.size }} " +
                "audioTracks=${resolvedStreams.sumOf { it.audioTracks.size }} " +
                "failures=${search.failures.map { it.sourceId + ":" + it.cause.javaClass.simpleName }} " +
                "elapsedMs=$elapsed",
        )
        Unit
    }

    @Test
    fun realAnimeSogoV17SearchDetailsEpisodesAndMediaNormalizeIntoNami() = runBlocking<Unit> {
        val startedAt = System.nanoTime()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = AniyomiExtensionRegistry(context)
            .installedSources()
            .firstOrNull {
                it.metadata.extensionApiVersion == 17 &&
                    it.metadata.extensionPackage?.endsWith(".animesogov17") == true
            }
        assertNotNull("Source-built maintained AnimeSogo extensions-lib v17 APK must be discovered", source)
        source!!
        assertEquals(17, source.metadata.extensionApiVersion)

        val query = "Bleach"
        val page = withTimeout(90_000) { source.search(query).items }
        assertTrue("AnimeSogo v17 returned no real results for $query", page.isNotEmpty())
        val result = page.firstOrNull { it.title.contains(query, ignoreCase = true) }
            ?: throw AssertionError("AnimeSogo v17 search did not contain $query")
        val details = withTimeout(60_000) { source.details(result.ref, result.sourceState) }
        assertTrue("AnimeSogo v17 details title is empty", details.title.isNotBlank())

        val episodes = withTimeout(60_000) { source.episodes(details.ref, details.sourceState) }
        assertTrue("AnimeSogo v17 returned no real episodes", episodes.isNotEmpty())
        assertTrue("AnimeSogo v17 episode identifiers are blank", episodes.all {
            it.ref.sourceEpisodeId.isNotBlank()
        })
        assertEquals(
            "AnimeSogo v17 episode identifiers are not stable/unique",
            episodes.size,
            episodes.map { it.ref.sourceEpisodeId }.distinct().size,
        )
        assertTrue("AnimeSogo v17 episode names were not normalized", episodes.all { it.title.isNotBlank() })
        assertTrue(
            "AnimeSogo v17 episode numbering did not map into Nami models",
            episodes.any { episode -> (episode.number ?: -1.0) >= 0.0 },
        )

        var resolvedEpisode = episodes.first()
        var streams = emptyList<app.nami.domain.ResolvedMedia>()
        for (episode in episodes.take(5)) {
            val candidates = withTimeout(60_000) {
                source.resolve(episode.ref, episode.sourceState)
            }
            if (candidates.any { it.url.startsWith("http") }) {
                resolvedEpisode = episode
                streams = candidates.filter { it.url.startsWith("http") }
                break
            }
        }
        assertTrue("AnimeSogo v17 did not resolve any final HTTP stream", streams.isNotEmpty())

        val elapsed = (System.nanoTime() - startedAt) / 1_000_000
        Log.i(
            "NamiSourceSmoke",
            "realV17 package=${source.metadata.extensionPackage} version=${source.metadata.extensionVersion} " +
                "source=${source.metadata.name} api=17 query=$query results=${page.size} " +
                "anime=${details.title} episodes=${episodes.size} " +
                "episode=${resolvedEpisode.title} number=${resolvedEpisode.number} " +
                "streams=${streams.size} hosters=${streams.mapNotNull { it.hosterName }.distinct()} " +
                "quality=${streams.mapNotNull { it.quality }.distinct()} mediaTypes=${streams.mapNotNull { it.mimeType }.distinct()} " +
                "headerNames=${streams.flatMap { it.headers.keys }.distinct()} " +
                "subtitles=${streams.sumOf { it.subtitles.size }} audioTracks=${streams.sumOf { it.audioTracks.size }} " +
                "elapsedMs=$elapsed",
        )
    }

    @Test
    fun installedV17FixturePersistsOpaqueStateAcrossAdapterAndDatabaseRecreation() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val installed = AniyomiExtensionRegistry(context).installedSources()
        val fixture = installed.firstOrNull {
            it.metadata.extensionPackage == "app.nami.fixture.v17"
        }

        assertNotNull("The separately installed v17 fixture APK must be discovered", fixture)
        fixture!!
        assertEquals(17, fixture.metadata.extensionApiVersion)
        assertTrue(
            "v17 fixture should expose configurable source preferences",
            fixture.metadata.capabilities.configurable,
        )

        val result = withTimeout(10_000) { fixture.search("Bleach").items.single() }
        assertTrue(result.title.contains("Bleach", ignoreCase = true))
        assertTrue(
            "v17 search result did not expose opaque source state",
            !result.sourceState.isNullOrBlank(),
        )

        val details = withTimeout(10_000) {
            fixture.details(result.ref, result.sourceState)
        }
        assertEquals("Fixture Details", details.title)
        assertTrue(
            "v17 details did not carry updated opaque source state",
            !details.sourceState.isNullOrBlank(),
        )

        val episodes = withTimeout(10_000) {
            fixture.episodes(details.ref, details.sourceState)
        }
        assertEquals(1, episodes.size)
        assertEquals("Fixture Episode 1", episodes.single().title)

        val media = withTimeout(10_000) {
            fixture.resolve(episodes.single().ref, episodes.single().sourceState)
        }
        assertEquals(1, media.size)
        assertEquals("https://example.invalid/fixture-v17.mp4", media.single().url)
        assertEquals("1080p", media.single().quality)

        val database = NamiDatabase(context)
        try {
            database.removeFromLibrary(details.ref)
            database.addToLibrary(details)
        } finally {
            database.close()
        }

        val reopenedEntry = NamiDatabase(context).use { reopened ->
            reopened.getLibraryEntries().first {
                it.ref.sourceId == details.ref.sourceId &&
                    it.ref.sourceAnimeId == details.ref.sourceAnimeId
            }
        }
        assertTrue(
            "Library database did not persist opaque source state",
            !reopenedEntry.sourceState.isNullOrBlank(),
        )

        // Fresh registry => fresh adapter caches. Details/episodes can only succeed if DB state
        // reconstructs SAnime.memo correctly.
        val freshFixture = AniyomiExtensionRegistry(context)
            .installedSources()
            .first {
                it.metadata.extensionPackage == "app.nami.fixture.v17"
            }

        val reopenedDetails = withTimeout(10_000) {
            freshFixture.details(reopenedEntry.ref, reopenedEntry.sourceState)
        }
        assertEquals("Fixture Details", reopenedDetails.title)

        val reopenedEpisodes = withTimeout(10_000) {
            freshFixture.episodes(
                reopenedDetails.ref,
                reopenedDetails.sourceState ?: reopenedEntry.sourceState,
            )
        }
        val reopenedEpisode = reopenedEpisodes.single()
        assertEquals("Fixture Episode 1", reopenedEpisode.title)
        assertTrue(
            "v17 episode did not carry opaque source state",
            !reopenedEpisode.sourceState.isNullOrBlank(),
        )

        val freshResolver = AniyomiExtensionRegistry(context)
            .installedSources()
            .first {
                it.metadata.extensionPackage == "app.nami.fixture.v17"
            }
        val reopenedMedia = withTimeout(10_000) {
            freshResolver.resolve(reopenedEpisode.ref, reopenedEpisode.sourceState)
        }
        assertEquals("https://example.invalid/fixture-v17.mp4", reopenedMedia.single().url)

        NamiDatabase(context).use { cleanup ->
            cleanup.removeFromLibrary(reopenedEntry.ref)
        }

        Log.i(
            "NamiSourceSmoke",
            "v17Fixture persistedState=true source=${fixture.metadata.id} " +
                "episodes=${episodes.size} reopenedEpisodes=${reopenedEpisodes.size} " +
                "media=${media.size} reopenedMedia=${reopenedMedia.size}",
        )
    }


    @Test
    fun libraryDatabaseMigratesV3AndPersistsSourceState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "nami-migration-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)

        val legacy = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        legacy.execSQL(
            """CREATE TABLE library_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_id TEXT NOT NULL,
                source_anime_id TEXT NOT NULL,
                title TEXT NOT NULL,
                cover_url TEXT,
                added_at INTEGER NOT NULL,
                UNIQUE(source_id, source_anime_id)
            )""".trimIndent(),
        )
        legacy.version = 3
        legacy.close()

        val database = NamiDatabase(context, databaseName)
        try {
            val columns = database.writableDatabase
                .rawQuery("PRAGMA table_info(library_entries)", null)
                .use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    buildList {
                        while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                    }
                }

            assertTrue(
                "v3 -> v4 migration did not add source_state",
                "source_state" in columns,
            )

            val expected = AnimeDetails(
                ref = AnimeRef("fixture-source", "/fixture/anime"),
                title = "Fixture",
                sourceState = """{"memo":{"token":"migration-test"}}""",
            )
            database.addToLibrary(expected)

            val stored = database.getLibraryEntries().single()
            assertEquals(expected.ref, stored.ref)
            assertEquals(expected.sourceState, stored.sourceState)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }


    @Test
    fun installedV14FixtureCrossesLegacyRxBoundary() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixture = AniyomiExtensionRegistry(context)
            .installedSources()
            .firstOrNull { it.metadata.extensionPackage == "app.nami.fixture.v14" }

        assertNotNull("The separately installed v14 fixture APK must be discovered", fixture)
        fixture!!
        assertEquals(14, fixture.metadata.extensionApiVersion)

        val result = withTimeout(10_000) { fixture.search("Bleach").items.single() }
        assertEquals("Fixture14 Bleach", result.title)

        val details = withTimeout(10_000) {
            fixture.details(result.ref, result.sourceState)
        }
        assertEquals("Fixture14 Details", details.title)

        val episodes = withTimeout(10_000) {
            fixture.episodes(details.ref, details.sourceState ?: result.sourceState)
        }
        assertEquals(1, episodes.size)
        assertEquals("Fixture14 Episode 1", episodes.single().title)

        val media = withTimeout(10_000) { fixture.resolve(episodes.single().ref) }
        assertEquals(1, media.size)
        assertEquals("https://example.invalid/fixture-v14.mp4", media.single().url)
        assertEquals("720p", media.single().quality)

        Log.i(
            "NamiSourceSmoke",
            "v14Fixture source=${fixture.metadata.id} results=1 episodes=${episodes.size} media=${media.size}",
        )
    }


    @Test
    fun libraryRefreshPreservesRowIdentityAndCategoryMembership() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "nami-library-integrity-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)

        val database = NamiDatabase(context, databaseName)
        try {
            val ref = AnimeRef("fixture-source", "/fixture/library")
            database.addToLibrary(
                AnimeDetails(
                    ref = ref,
                    title = "Original title",
                    coverUrl = "https://example.invalid/original.jpg",
                    sourceState = """{"token":"first"}""",
                ),
            )

            val original = database.getLibraryEntries().single()
            val sqlite = database.writableDatabase
            sqlite.execSQL(
                "INSERT INTO categories(name, sort_order) VALUES(?, ?)",
                arrayOf("Favorites", 0),
            )
            val categoryId = sqlite.rawQuery(
                "SELECT id FROM categories WHERE name = ?",
                arrayOf("Favorites"),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getLong(0)
            }
            sqlite.execSQL(
                "INSERT INTO library_category_membership(library_entry_id, category_id) VALUES(?, ?)",
                arrayOf(original.id, categoryId),
            )

            database.addToLibrary(
                AnimeDetails(
                    ref = ref,
                    title = "Refreshed title",
                    coverUrl = "https://example.invalid/refreshed.jpg",
                    sourceState = """{"token":"second"}""",
                ),
            )

            val refreshed = database.getLibraryEntries().single()
            assertTrue(
                "Refreshing library metadata must update in place instead of replacing the row",
                original.id == refreshed.id,
            )
            assertEquals("Refreshed title", refreshed.title)
            assertEquals("""{"token":"second"}""", refreshed.sourceState)

            val membershipCount = sqlite.rawQuery(
                "SELECT COUNT(*) FROM library_category_membership " +
                    "WHERE library_entry_id = ? AND category_id = ?",
                arrayOf(original.id.toString(), categoryId.toString()),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
            assertTrue(
                "Refreshing a library entry must not cascade-delete its category membership",
                membershipCount == 1,
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }


    @Test
    fun legacyDownloadProviderProducesGrantableContentUriInsideNamiMovies() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val file = File(root, "Nami/Fixture/Show/Episode 001.mkv")

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".downloads",
            file,
        )

        assertEquals("content", uri.scheme)
        assertEquals(context.packageName + ".downloads", uri.authority)
        assertTrue(
            "Legacy download FileProvider should expose only the configured Nami Movies subtree",
            uri.path.orEmpty().contains("nami_movies"),
        )
    }


    @Test
    fun downloadDatabaseMigratesV4AndPersistsRestartRetryState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "nami-download-migration-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)

        val legacy = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        legacy.execSQL(
            """CREATE TABLE downloads (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_id TEXT NOT NULL,
                extension_name TEXT NOT NULL,
                source_anime_id TEXT NOT NULL,
                source_episode_id TEXT NOT NULL,
                relative_path TEXT NOT NULL,
                display_name TEXT,
                content_uri TEXT,
                mime_type TEXT,
                state TEXT NOT NULL,
                progress INTEGER NOT NULL DEFAULT 0,
                error_message TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL DEFAULT 0,
                UNIQUE(source_id, source_episode_id)
            )""".trimIndent(),
        )
        legacy.version = 4
        legacy.close()

        val database = NamiDatabase(context, databaseName)
        try {
            val columns = database.writableDatabase
                .rawQuery("PRAGMA table_info(downloads)", null)
                .use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                    }
                }

            assertTrue("v4 -> v5 migration did not add anime_title", "anime_title" in columns)
            assertTrue("v4 -> v5 migration did not add episode_title", "episode_title" in columns)
            assertTrue("v4 -> v5 migration did not add anime_source_state", "anime_source_state" in columns)
            assertTrue("v4 -> v5 migration did not add episode_source_state", "episode_source_state" in columns)

            database.upsertDownload(
                sourceId = "fixture-source",
                extensionName = "Fixture extension",
                sourceAnimeId = "/fixture/anime",
                sourceEpisodeId = "/fixture/episode-1",
                relativePath = "Fixture extension/Fixture title/Season 01",
                state = "ERROR",
                progress = 0,
                animeTitle = "Fixture title",
                episodeTitle = "Fixture Episode 1",
                animeSourceState = """{"memo":{"token":"anime-retry"}}""",
                episodeSourceState = """{"memo":{"token":"episode-retry"}}""",
                errorMessage = "Interrupted",
            )
        } finally {
            database.close()
        }

        val stored = NamiDatabase(context, databaseName).use { reopened ->
            reopened.getDownloads().single()
        }

        assertEquals("Fixture title", stored.animeTitle)
        assertEquals("Fixture Episode 1", stored.episodeTitle)
        assertEquals("""{"memo":{"token":"anime-retry"}}""", stored.animeSourceState)
        assertEquals("""{"memo":{"token":"episode-retry"}}""", stored.episodeSourceState)

        context.deleteDatabase(databaseName)
    }


}
