package com.tomex.relay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRelayDataSourceTest {
    @Test
    fun createEditCompleteUncompleteDelete_recordsExpectedHistory() {
        val persistence = MemoryPersistence()
        val data = DefaultRelayDataSource(
            persistence = persistence,
            clock = SequenceClock(10, 20, 30, 40, 50),
            ids = SequenceIds(),
        )

        val created = data.createTask("  Buy milk  ", "  oat  ")
        assertEquals("Buy milk", created.title)
        assertEquals("oat", created.notes)

        val edited = data.editTask(created.id, "Buy milk today", "oat")
        assertNotNull(edited)
        assertEquals("Buy milk today", edited!!.title)

        val completed = data.setTaskCompleted(created.id, true)
        assertTrue(completed!!.isCompleted)
        assertEquals(30L, completed.completedAtEpochMs)

        val uncompleted = data.setTaskCompleted(created.id, false)
        assertFalse(uncompleted!!.isCompleted)
        assertNull(uncompleted.completedAtEpochMs)

        assertTrue(data.deleteTask(created.id))
        assertTrue(data.snapshot().tasks.isEmpty())
        assertEquals(
            listOf(
                ActivityKind.DELETED,
                ActivityKind.UNCOMPLETED,
                ActivityKind.COMPLETED,
                ActivityKind.EDITED,
                ActivityKind.CREATED,
            ),
            data.snapshot().activity.map { it.kind },
        )
    }

    @Test
    fun repositoryRecreation_restoresTasksHistoryAndSettings() {
        val persistence = MemoryPersistence()
        val ids = SequenceIds()
        val first = DefaultRelayDataSource(persistence, SequenceClock(100, 200), ids)
        val task = first.createTask("Persist me")
        first.setTaskCompleted(task.id, true)
        first.updateSettings(RelaySettings(themeMode = ThemeMode.DARK, showCompleted = false))

        val second = DefaultRelayDataSource(persistence, SequenceClock(300), ids)
        val hidden = second.snapshot()
        assertTrue(hidden.tasks.isEmpty())
        assertEquals(ThemeMode.DARK, hidden.settings.themeMode)
        assertEquals(2, hidden.activity.size)

        second.updateSettings(hidden.settings.copy(showCompleted = true))
        val restored = second.snapshot().tasks.single()
        assertEquals(task.id, restored.id)
        assertTrue(restored.isCompleted)
    }

    @Test
    fun repeatedCompletionState_isIdempotent() {
        val data = DefaultRelayDataSource(MemoryPersistence(), SequenceClock(1, 2, 3), SequenceIds())
        val task = data.createTask("Task")
        data.setTaskCompleted(task.id, true)
        val activityCount = data.snapshot().activity.size
        val revision = data.snapshot().revision

        data.setTaskCompleted(task.id, true)

        assertEquals(activityCount, data.snapshot().activity.size)
        assertEquals(revision, data.snapshot().revision)
    }

    @Test
    fun blankTitle_isRejected() {
        val data = DefaultRelayDataSource(MemoryPersistence(), SequenceClock(1), SequenceIds())
        assertThrows(IllegalArgumentException::class.java) {
            data.createTask("   ")
        }
    }

    @Test
    fun missingTaskMutations_doNotCreateHistory() {
        val data = DefaultRelayDataSource(MemoryPersistence(), SequenceClock(1), SequenceIds())
        assertNull(data.editTask("missing", "Title"))
        assertNull(data.setTaskCompleted("missing", true))
        assertFalse(data.deleteTask("missing"))
        assertTrue(data.snapshot().activity.isEmpty())
    }

    private class MemoryPersistence : RelayPersistence {
        var state: RelayPersistedState? = null

        override fun load(): RelayPersistedState? = state

        override fun save(state: RelayPersistedState) {
            this.state = state.copy(
                tasks = state.tasks.toList(),
                activity = state.activity.toList(),
            )
        }
    }

    private class SequenceClock(vararg values: Long) : RelayClock {
        private val queue = ArrayDeque(values.toList())
        override fun nowEpochMs(): Long = queue.removeFirst()
    }

    private class SequenceIds : RelayIdGenerator {
        private var value = 0
        override fun nextId(): String = "id-" + (++value)
    }
}
