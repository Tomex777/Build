package com.tomex.relay.data

import java.util.UUID

class DefaultRelayDataSource(
    private val persistence: RelayPersistence,
    private val clock: RelayClock = RelayClock { System.currentTimeMillis() },
    private val ids: RelayIdGenerator = RelayIdGenerator { UUID.randomUUID().toString() },
) : RelayDataSource {
    private val lock = Any()
    private val listeners = LinkedHashSet<(RelaySnapshot) -> Unit>()
    private var state: RelayPersistedState = persistence.load() ?: RelayPersistedState()
    private var revision: Long = 0L

    override fun snapshot(): RelaySnapshot = synchronized(lock) {
        snapshotLocked()
    }

    override fun observe(listener: (RelaySnapshot) -> Unit): RelaySubscription {
        val initial = synchronized(lock) {
            listeners += listener
            snapshotLocked()
        }
        listener(initial)
        return RelaySubscription {
            synchronized(lock) {
                listeners -= listener
            }
        }
    }

    override fun createTask(title: String, notes: String): RelayTask {
        val cleanTitle = normalizeTitle(title)
        val cleanNotes = notes.trim()
        val now = clock.nowEpochMs()
        val task = RelayTask(
            id = ids.nextId(),
            title = cleanTitle,
            notes = cleanNotes,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        val activity = RelayActivity(
            id = ids.nextId(),
            taskId = task.id,
            kind = ActivityKind.CREATED,
            taskTitle = task.title,
            timestampEpochMs = now,
        )
        commit { old ->
            old.copy(
                tasks = old.tasks + task,
                activity = old.activity + activity,
            )
        }
        return task
    }

    override fun editTask(taskId: String, title: String, notes: String): RelayTask? {
        val cleanTitle = normalizeTitle(title)
        val cleanNotes = notes.trim()
        var result: RelayTask? = null

        commitIfChanged { old ->
            val existing = old.tasks.firstOrNull { it.id == taskId } ?: return@commitIfChanged old
            if (existing.title == cleanTitle && existing.notes == cleanNotes) {
                result = existing
                return@commitIfChanged old
            }

            val now = clock.nowEpochMs()
            val updated = existing.copy(
                title = cleanTitle,
                notes = cleanNotes,
                updatedAtEpochMs = now,
            )
            result = updated
            old.copy(
                tasks = old.tasks.map { if (it.id == taskId) updated else it },
                activity = old.activity + RelayActivity(
                    id = ids.nextId(),
                    taskId = taskId,
                    kind = ActivityKind.EDITED,
                    taskTitle = updated.title,
                    timestampEpochMs = now,
                ),
            )
        }
        return result
    }

    override fun deleteTask(taskId: String): Boolean {
        var deleted = false
        commitIfChanged { old ->
            val existing = old.tasks.firstOrNull { it.id == taskId } ?: return@commitIfChanged old
            val now = clock.nowEpochMs()
            deleted = true
            old.copy(
                tasks = old.tasks.filterNot { it.id == taskId },
                activity = old.activity + RelayActivity(
                    id = ids.nextId(),
                    taskId = taskId,
                    kind = ActivityKind.DELETED,
                    taskTitle = existing.title,
                    timestampEpochMs = now,
                ),
            )
        }
        return deleted
    }

    override fun setTaskCompleted(taskId: String, completed: Boolean): RelayTask? {
        var result: RelayTask? = null
        commitIfChanged { old ->
            val existing = old.tasks.firstOrNull { it.id == taskId } ?: return@commitIfChanged old
            if (existing.isCompleted == completed) {
                result = existing
                return@commitIfChanged old
            }

            val now = clock.nowEpochMs()
            val updated = existing.copy(
                isCompleted = completed,
                updatedAtEpochMs = now,
                completedAtEpochMs = if (completed) now else null,
            )
            result = updated
            old.copy(
                tasks = old.tasks.map { if (it.id == taskId) updated else it },
                activity = old.activity + RelayActivity(
                    id = ids.nextId(),
                    taskId = taskId,
                    kind = if (completed) ActivityKind.COMPLETED else ActivityKind.UNCOMPLETED,
                    taskTitle = updated.title,
                    timestampEpochMs = now,
                ),
            )
        }
        return result
    }

    override fun updateSettings(settings: RelaySettings): RelaySettings {
        commitIfChanged { old ->
            if (old.settings == settings) old else old.copy(settings = settings)
        }
        return settings
    }

    private fun normalizeTitle(title: String): String {
        val clean = title.trim()
        require(clean.isNotEmpty()) { "Task title must not be blank" }
        return clean
    }

    private fun commit(transform: (RelayPersistedState) -> RelayPersistedState) {
        val callbacks: List<(RelaySnapshot) -> Unit>
        val snapshot: RelaySnapshot
        synchronized(lock) {
            val next = transform(state)
            persistence.save(next)
            state = next
            revision += 1L
            snapshot = snapshotLocked()
            callbacks = listeners.toList()
        }
        callbacks.forEach { callback ->
            runCatching { callback(snapshot) }
        }
    }

    private fun commitIfChanged(transform: (RelayPersistedState) -> RelayPersistedState) {
        val callbacks: List<(RelaySnapshot) -> Unit>
        val snapshot: RelaySnapshot
        synchronized(lock) {
            val next = transform(state)
            if (next == state) return
            persistence.save(next)
            state = next
            revision += 1L
            snapshot = snapshotLocked()
            callbacks = listeners.toList()
        }
        callbacks.forEach { callback ->
            runCatching { callback(snapshot) }
        }
    }

    private fun snapshotLocked(): RelaySnapshot {
        val visibleTasks = state.tasks
            .asSequence()
            .filter { state.settings.showCompleted || !it.isCompleted }
            .sortedWith(
                compareBy<RelayTask> { it.isCompleted }
                    .thenByDescending { it.createdAtEpochMs }
                    .thenBy { it.id },
            )
            .toList()

        val recentActivity = state.activity.asReversed()

        return RelaySnapshot(
            tasks = visibleTasks,
            activity = recentActivity,
            settings = state.settings,
            revision = revision,
        )
    }
}
