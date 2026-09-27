package app.mira.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mira.domain.ResolvedMedia
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
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
    fun directPauseResumeUsesRangeAndFinishesOfflineFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("mira_download_engine", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val payload = ByteArray(2 * 1024 * 1024) { index -> (index % 251).toByte() }
        val ranges = CopyOnWriteArrayList<Long>()
        val server = object : NanoHTTPD(0) {
            override fun serve(session: IHTTPSession): Response {
                val start = session.headers["range"]
                    ?.let { Regex("""bytes=(\d+)-""").find(it)?.groupValues?.get(1)?.toLong() }
                    ?: 0L
                if (start > 0L) ranges += start
                val bounded = start.coerceIn(0L, payload.size.toLong()).toInt()
                val bytes = payload.copyOfRange(bounded, payload.size)
                return newFixedLengthResponse(
                    if (bounded > 0) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
                    "video/mp4",
                    SlowInput(bytes),
                    bytes.size.toLong(),
                ).apply {
                    addHeader("Accept-Ranges", "bytes")
                    if (bounded > 0) {
                        addHeader(
                            "Content-Range",
                            "bytes $bounded-${payload.size - 1}/${payload.size}",
                        )
                    }
                }
            }
        }

        try {
            server.start(SOCKET_READ_TIMEOUT, false)
            val manager = MiraDownloadManager(context, serviceOwned = false)
            manager.enqueue(
                sourceId = "fixture",
                contentId = "movie",
                title = "Fixture movie",
                media = ResolvedMedia(
                    url = "http://127.0.0.1:${server.listeningPort}/movie.mp4",
                    mimeType = "video/mp4",
                ),
            )
            val id = manager.key("fixture", "movie", null)

            val transferring = withTimeout(20_000) {
                waitFor(manager, id) {
                    it.state == MiraDownloadState.DOWNLOADING &&
                        it.bytesDownloaded >= 64 * 1024
                }
            }
            manager.pause(transferring)

            val paused = withTimeout(10_000) {
                waitFor(manager, id) { it.state == MiraDownloadState.PAUSED }
            }
            assertTrue(paused.bytesDownloaded > 0L)
            delay(400)

            manager.resume(paused)
            val completed = withTimeout(40_000) {
                waitFor(manager, id) { it.state == MiraDownloadState.DOWNLOADED }
            }

            assertEquals(100, completed.progress)
            assertTrue("Resume never issued HTTP Range", ranges.any { it > 0L })
            assertTrue(
                "Offline file was not persisted",
                completed.finalPath?.let(::File)?.exists() == true,
            )
            assertTrue("Offline content URI missing", !completed.contentUri.isNullOrBlank())
            manager.remove(completed)
        } finally {
            server.stop()
            context.getSharedPreferences("mira_download_engine", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }

    private suspend fun waitFor(
        manager: MiraDownloadManager,
        id: String,
        predicate: (MiraDownloadStatus) -> Boolean,
    ): MiraDownloadStatus {
        while (true) {
            manager.statuses.value[id]?.let { if (predicate(it)) return it }
            delay(25)
        }
    }

    private class SlowInput(
        private val bytes: ByteArray,
    ) : ByteArrayInputStream(bytes) {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            Thread.sleep(2)
            return super.read(buffer, offset, length.coerceAtMost(8192))
        }
    }
}
