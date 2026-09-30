package com.tomex.relay.data

fun interface RelaySubscription {
    fun close()
}

interface RelayDataSource {
    fun snapshot(): RelaySnapshot
    fun observe(listener: (RelaySnapshot) -> Unit): RelaySubscription
    fun createTask(title: String, notes: String = ""): RelayTask
    fun editTask(taskId: String, title: String, notes: String = ""): RelayTask?
    fun deleteTask(taskId: String): Boolean
    fun setTaskCompleted(taskId: String, completed: Boolean): RelayTask?
    fun updateSettings(settings: RelaySettings): RelaySettings
}

interface RelayPersistence {
    fun load(): RelayPersistedState?
    fun save(state: RelayPersistedState)
}

data class RelayPersistedState(
    val tasks: List<RelayTask> = emptyList(),
    val activity: List<RelayActivity> = emptyList(),
    val settings: RelaySettings = RelaySettings(),
)

fun interface RelayClock {
    fun nowEpochMs(): Long
}

fun interface RelayIdGenerator {
    fun nextId(): String
}
