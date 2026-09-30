package com.tomex.relay.data

data class RelayTask(
    val id: String,
    val title: String,
    val notes: String = "",
    val isCompleted: Boolean = false,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
)

enum class ActivityKind {
    CREATED,
    EDITED,
    COMPLETED,
    UNCOMPLETED,
    DELETED,
}

data class RelayActivity(
    val id: String,
    val taskId: String,
    val kind: ActivityKind,
    val taskTitle: String,
    val timestampEpochMs: Long,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class RelaySettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showCompleted: Boolean = true,
)

data class RelaySnapshot(
    val tasks: List<RelayTask>,
    val activity: List<RelayActivity>,
    val settings: RelaySettings,
    val revision: Long,
)
