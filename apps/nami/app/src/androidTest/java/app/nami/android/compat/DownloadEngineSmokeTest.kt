package app.nami.android.compat

import android.content.Context
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nami.android.NamiDownloadManager
import app.nami.android.NamiDownloadState
import app.nami.android.NamiDownloadStatus
import app.nami.android.NamiPauseReason
import app.nami.data.local.NamiDatabase
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
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadEngineSmokeTest {

    @Test
    fun individualPauseKeepsPartialAndResumeUsesHttpRange() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "nami-range-resume-" + System.nanoTime() + ".db"
        context.deleteDatabase(databaseName)
        val database = NamiDatabase(context, databaseName)
        val payload = ByteArray(2 * 1024 * 1024) { index -> (index % 239).toByte() }
        val observedRanges = CopyOnWriteArrayList<Long>()

        val server = object : NanoHTTPD(0) {
            override fun serve(session: IHTTPSession): Response {
                val start = parseRangeStart(session.headers["range"])
                if (start > 0L) observedRanges += start
                val boundedStart = start.coerceIn(0L, payload.size.toLong())
                val length = payload.size.toLong() - boundedStart
                val body = SlowByteArrayInputStream(
                    payload = payload,
                    start = boundedStart.toInt(),
                    delayMillis = 2L,
                )
                return newFixedLengthResponse(
                    if (boundedStart > 0L) {
                        Response.Status.PARTIAL_CONTENT
                    } else {
                        Response.Status.OK
                    },
                    "video/mp4",
                    body,
                    length,
                ).apply {
                    addHeader("Accept-Ranges", "bytes")
                    if (boundedStart > 0L) {
                        addHeader(
                            "Content-Range",
                            "bytes $boundedStart-${payload.size - 1}/${payload.size}",
                        )
                    }
                    addHeader("ETag", "\"nami-range-fixture\"")
                }
            }
        }

        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            val source = fixtureSource(
                id = "range-fixture",
                mediaUrl = "http://127.0.0.1:${server.listeningPort}/episode.mp4",
            )
            val manager = NamiDownloadManager(
                context,
                database,
                NamiSourceRegistry { listOf(source) },
            )
            manager.resumeAll()

            val anime = fixtureAnime(source, "Range Resume")
            val episode = fixtureEpisode(source, anime, 1)
            val key = manager.key(source.metadata.id, episode.ref.sourceEpisodeId)
            manager.enqueue(source, anime, episode)

            val transferring = withTimeout(20_000) {
                waitForStatus(manager, key) {
                    it.state == NamiDownloadState.DOWNLOADING &&
                        it.bytesDownloaded >= 64 * 1024L &&
                        !it.tempPath.isNullOrBlank()
                }
            }
            manager.pause(transferring)

            val paused = withTimeout(10_000) {
                waitForStatus(manager, key) {
                    it.state == NamiDownloadState.PAUSED &&
                        it.pauseReason == NamiPauseReason.USER
                }
            }
            val partial = File(paused.tempPath!!)
            delay(350)
            val stableLength = partial.length()
            delay(350)
            assertEquals(
                "Paused download continued writing after cancellation settled",
                stableLength,
                partial.length(),
            )
            assertTrue("Pause discarded the partial download", stableLength > 0L)

            manager.resume(paused)
            val completed = withTimeout(30_000) {
                waitForStatus(manager, key) {
                    it.state == NamiDownloadState.DOWNLOADED
                }
            }

            assertTrue(
                "Resume restarted from zero instead of issuing HTTP Range",
                observedRanges.any { it > 0L },
            )
            assertEquals(100, completed.progress)
            assertTrue("Completed resume did not publish media", !completed.contentUri.isNullOrBlank())

            val finalSize = context.contentResolver.query(
                android.net.Uri.parse(completed.contentUri),
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else -1L
            } ?: -1L
            assertEquals(
                "HTTP Range resume produced a corrupt final size",
                payload.size.toLong(),
                finalSize,
            )

            manager.remove(completed)
            withTimeout(10_000) {
                while (manager.statuses.value.containsKey(key)) delay(50)
            }
        } finally {
            server.stop()
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun perSourceSlotsAndGlobalPauseResumeRemainIndependent() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "nami-per-source-" + System.nanoTime() + ".db"
        context.deleteDatabase(databaseName)
        val database = NamiDatabase(context, databaseName)

        val active = ConcurrentHashMap<String, AtomicInteger>()
        val maximum = ConcurrentHashMap<String, AtomicInteger>()
        val started = ConcurrentHashMap<String, AtomicInteger>()
        val totalBytes = 32L * 1024L * 1024L

        val server = object : NanoHTTPD(0) {
            override fun serve(session: IHTTPSession): Response {
                val sourceId = session.uri.trim('/').substringBefore('/')
                val start = parseRangeStart(session.headers["range"]).coerceIn(0L, totalBytes)
                val activeNow = active
                    .computeIfAbsent(sourceId) { AtomicInteger() }
                    .incrementAndGet()
                started.computeIfAbsent(sourceId) { AtomicInteger() }.incrementAndGet()
                maximum.computeIfAbsent(sourceId) { AtomicInteger() }
                    .updateAndGet { previous -> maxOf(previous, activeNow) }

                val closed = AtomicBoolean(false)
                val body = GeneratedSlowInputStream(
                    totalBytes = totalBytes - start,
                    delayMillis = 4L,
                    onClose = {
                        if (closed.compareAndSet(false, true)) {
                            active[sourceId]?.decrementAndGet()
                        }
                    },
                )
                return newFixedLengthResponse(
                    if (start > 0L) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
                    "video/mp4",
                    body,
                    totalBytes - start,
                ).apply {
                    addHeader("Accept-Ranges", "bytes")
                    if (start > 0L) {
                        addHeader(
                            "Content-Range",
                            "bytes $start-${totalBytes - 1}/$totalBytes",
                        )
                    }
                }
            }
        }

        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            val sourceIds = listOf("A", "B", "C")
            val sources = sourceIds.associateWith { id ->
                fixtureSource(
                    id = id,
                    mediaUrl = "http://127.0.0.1:${server.listeningPort}/$id/video.mp4",
                )
            }
            val manager = NamiDownloadManager(
                context,
                database,
                NamiSourceRegistry { sources.values.toList() },
            )
            manager.resumeAll()

            sources.forEach { (id, source) ->
                val anime = fixtureAnime(source, "Fixture $id")
                val episodes = (1..4).map { fixtureEpisode(source, anime, it) }
                manager.enqueueAll(source, anime, episodes)
            }

            withTimeout(20_000) {
                while (sourceIds.any { (active[it]?.get() ?: 0) < 2 }) {
                    delay(50)
                }
            }
            assertEquals(
                "Three sources should have six transfers active at two slots each",
                6,
                sourceIds.sumOf { active[it]?.get() ?: 0 },
            )
            sourceIds.forEach { id ->
                assertTrue(
                    "Source $id exceeded its independent two-download limit",
                    (maximum[id]?.get() ?: 0) <= 2,
                )
            }

            val manuallyPaused = manager.statuses.value.values.first {
                it.sourceId == "A" && it.state == NamiDownloadState.DOWNLOADING
            }
            manager.pause(manuallyPaused)
            val manualKey = manager.key(
                manuallyPaused.sourceId,
                manuallyPaused.sourceEpisodeId,
            )
            withTimeout(10_000) {
                waitForStatus(manager, manualKey) {
                    it.state == NamiDownloadState.PAUSED &&
                        it.pauseReason == NamiPauseReason.USER
                }
            }
            withTimeout(15_000) {
                while (
                    (active["A"]?.get() ?: 0) < 2 ||
                    (started["A"]?.get() ?: 0) < 3
                ) {
                    delay(50)
                }
            }
            assertEquals("Pausing A must not reduce B slots", 2, active["B"]?.get() ?: 0)
            assertEquals("Pausing A must not reduce C slots", 2, active["C"]?.get() ?: 0)

            manager.pauseAll()
            withTimeout(15_000) {
                while (
                    active.values.sumOf { it.get().coerceAtLeast(0) } != 0 ||
                    manager.statuses.value.values.any {
                        it.state == NamiDownloadState.DOWNLOADING ||
                            it.state == NamiDownloadState.QUEUED ||
                            it.state == NamiDownloadState.WAITING_FOR_NETWORK
                    }
                ) {
                    delay(50)
                }
            }
            assertTrue("Global pause flag was not persisted in memory", manager.globalPaused.value)

            val stillManual = manager.statuses.value[manualKey]
                ?: throw AssertionError("Manually paused item disappeared")
            assertEquals(NamiDownloadState.PAUSED, stillManual.state)
            assertEquals(NamiPauseReason.USER, stillManual.pauseReason)

            manager.resumeAll()
            withTimeout(20_000) {
                while (sourceIds.any { (active[it]?.get() ?: 0) < 2 }) {
                    delay(50)
                }
            }
            assertTrue("Resume All left the global pause flag set", !manager.globalPaused.value)
            val afterResumeManual = manager.statuses.value[manualKey]
                ?: throw AssertionError("Manually paused item disappeared after Resume All")
            assertEquals(NamiDownloadState.PAUSED, afterResumeManual.state)
            assertEquals(NamiPauseReason.USER, afterResumeManual.pauseReason)
            assertEquals(
                "Resume All did not restore two independent slots per source",
                6,
                sourceIds.sumOf { active[it]?.get() ?: 0 },
            )

            manager.statuses.value.values.toList().forEach(manager::remove)
            withTimeout(20_000) {
                while (manager.statuses.value.isNotEmpty()) delay(50)
            }
        } finally {
            server.stop()
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private suspend fun waitForStatus(
        manager: NamiDownloadManager,
        key: String,
        predicate: (NamiDownloadStatus) -> Boolean,
    ): NamiDownloadStatus {
        while (true) {
            val status = manager.statuses.value[key]
            if (status != null && predicate(status)) return status
            if (status?.state == NamiDownloadState.ERROR) {
                throw AssertionError("Download entered ERROR: " + status.errorMessage)
            }
            delay(50)
        }
    }

    private fun fixtureSource(
        id: String,
        mediaUrl: String,
    ): NamiAnimeSource = object : NamiAnimeSource {
        override val metadata = SourceMetadata(
            id = id,
            name = "Fixture $id",
            origin = SourceOrigin.NATIVE_NAMI,
            capabilities = SourceCapabilities(downloadable = true),
        )

        override suspend fun search(
            query: String,
            page: Int,
        ): SourcePage<AnimeSearchResult> = SourcePage(emptyList(), false)

        override suspend fun details(anime: AnimeRef): AnimeDetails = error("Not used")
        override suspend fun episodes(anime: AnimeRef): List<AnimeEpisode> = error("Not used")

        override suspend fun resolve(episode: EpisodeRef): List<ResolvedMedia> =
            listOf(
                ResolvedMedia(
                    url = mediaUrl,
                    mimeType = "video/mp4",
                    quality = "fixture",
                ),
            )
    }

    private fun fixtureAnime(
        source: NamiAnimeSource,
        title: String,
    ): AnimeDetails = AnimeDetails(
        ref = AnimeRef(source.metadata.id, "/anime-" + source.metadata.id),
        title = title,
    )

    private fun fixtureEpisode(
        source: NamiAnimeSource,
        anime: AnimeDetails,
        number: Int,
    ): AnimeEpisode = AnimeEpisode(
        ref = EpisodeRef(
            sourceId = source.metadata.id,
            sourceAnimeId = anime.ref.sourceAnimeId,
            sourceEpisodeId = "/episode-$number",
        ),
        title = "Episode $number",
        number = number.toDouble(),
    )

    private fun parseRangeStart(value: String?): Long =
        value
            ?.let { Regex("""(?i)^bytes=(\\d+)-$""").find(it.trim()) }
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: 0L

    private class SlowByteArrayInputStream(
        private val payload: ByteArray,
        start: Int,
        private val delayMillis: Long,
    ) : InputStream() {
        private var position = start

        override fun read(): Int {
            val one = ByteArray(1)
            val count = read(one, 0, 1)
            return if (count < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= payload.size) return -1
            if (delayMillis > 0L) Thread.sleep(delayMillis)
            val count = minOf(length, 4096, payload.size - position)
            payload.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
    }

    private class GeneratedSlowInputStream(
        private var totalBytes: Long,
        private val delayMillis: Long,
        private val onClose: () -> Unit,
    ) : InputStream() {
        private val closed = AtomicBoolean(false)

        override fun read(): Int {
            val one = ByteArray(1)
            val count = read(one, 0, 1)
            return if (count < 0) -1 else 0x4e
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (totalBytes <= 0L) {
                close()
                return -1
            }
            if (delayMillis > 0L) Thread.sleep(delayMillis)
            val count = minOf(length.toLong(), 8192L, totalBytes).toInt()
            java.util.Arrays.fill(buffer, offset, offset + count, 0x4e.toByte())
            totalBytes -= count
            return count
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) onClose()
        }
    }
}
