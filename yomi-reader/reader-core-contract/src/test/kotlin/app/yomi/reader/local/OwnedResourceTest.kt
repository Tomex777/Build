package app.yomi.reader.local

import app.yomi.reader.core.loadOwnedResource
import java.io.Closeable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class OwnedResourceTest {
    private class Resource : Closeable {
        var closes = 0
        override fun close() { closes++ }
    }

    @Test fun `successful load transfers stream ownership to renderer`() = runTest {
        val resource = Resource()
        var accepted = false
        loadOwnedResource(StandardTestDispatcher(testScheduler), { resource }) {
            assertEquals(resource, it)
            accepted = true
        }
        assertTrue(accepted)
        assertEquals(0, resource.closes)
    }

    @Test fun `cancellation after file opens closes discarded stream`() = runTest {
        val resource = Resource()
        var accepted = false
        val job = launch {
            loadOwnedResource(StandardTestDispatcher(testScheduler), {
                // Simulate a blocking document-provider open finishing after
                // the reader has cancelled its page load.
                coroutineContext[kotlinx.coroutines.Job]!!.cancel()
                resource
            }) { accepted = true }
        }
        runCurrent()
        job.join()
        assertFalse(accepted)
        assertEquals(1, resource.closes)
    }

    @Test fun `renderer failure closes stream and preserves original error`() = runTest {
        val resource = Resource()
        assertFailsWith<IllegalArgumentException> {
            loadOwnedResource(StandardTestDispatcher(testScheduler), { resource }) {
                throw IllegalArgumentException("decoder rejected image")
            }
        }
        assertEquals(1, resource.closes)
    }
}
