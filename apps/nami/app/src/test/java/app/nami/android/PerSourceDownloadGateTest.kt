package app.nami.android

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerSourceDownloadGateTest {

    @Test
    fun eachSourceGetsTwoIndependentSlots() = runBlocking {
        val gate = PerSourceDownloadGate(maxPerSource = 2)
        val ids = listOf(
            "A1", "A2", "A3", "A4",
            "B1", "B2", "B3", "B4",
            "C1", "C2", "C3", "C4",
        )
        fun sourceOf(id: String) = id.take(1)

        val blockers = ids.associateWith { CompletableDeferred<Unit>() }
        val active = mapOf(
            "A" to AtomicInteger(),
            "B" to AtomicInteger(),
            "C" to AtomicInteger(),
        )
        val maxActive = mapOf(
            "A" to AtomicInteger(),
            "B" to AtomicInteger(),
            "C" to AtomicInteger(),
        )
        val started = ConcurrentHashMap.newKeySet<String>()
        val startedEvents = Channel<String>(Channel.UNLIMITED)

        val jobs = ids.map { id ->
            launch {
                val source = sourceOf(id)
                gate.withPermit(source) {
                    val now = active.getValue(source).incrementAndGet()
                    maxActive.getValue(source).updateAndGet { current ->
                        maxOf(current, now)
                    }
                    started += id
                    startedEvents.send(id)
                    try {
                        blockers.getValue(id).await()
                    } finally {
                        active.getValue(source).decrementAndGet()
                    }
                }
            }
        }

        withTimeout(5_000) {
            repeat(6) { startedEvents.receive() }
        }

        assertEquals(2, active.getValue("A").get())
        assertEquals(2, active.getValue("B").get())
        assertEquals(2, active.getValue("C").get())
        assertEquals(6, active.values.sumOf { it.get() })
        assertEquals(2, started.count { it.startsWith("A") })
        assertEquals(2, started.count { it.startsWith("B") })
        assertEquals(2, started.count { it.startsWith("C") })

        // Free one A slot. The next A item must start without disturbing B or C.
        val activeA = started.first { it.startsWith("A") }
        blockers.getValue(activeA).complete(Unit)
        withTimeout(5_000) {
            while (started.count { it.startsWith("A") } < 3) {
                startedEvents.receive()
            }
        }

        assertEquals(2, active.getValue("A").get())
        assertEquals(2, active.getValue("B").get())
        assertEquals(2, active.getValue("C").get())

        blockers.values.forEach { it.complete(Unit) }
        jobs.joinAll()

        assertTrue(maxActive.values.all { it.get() == 2 })
    }
}
