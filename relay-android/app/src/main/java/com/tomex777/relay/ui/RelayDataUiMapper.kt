package com.tomex777.relay.ui

import com.tomex.relay.data.ActivityKind
import com.tomex.relay.data.RelaySnapshot
import com.tomex.relay.data.ThemeMode

internal fun RelaySnapshot.toUiState(): RelayUiState = RelayUiState(
    isLoading = false,
    tasks = tasks.map { task ->
        RelayTaskUi(
            id = task.id,
            title = task.title,
            note = task.notes,
            isCompleted = task.isCompleted,
            createdAtMillis = task.createdAtEpochMs,
            updatedAtMillis = task.updatedAtEpochMs,
        )
    },
    activity = activity.map { entry ->
        RelayActivityUi(
            id = entry.id,
            title = entry.kind.activityLabel(),
            detail = entry.taskTitle,
            timestampMillis = entry.timestampEpochMs,
        )
    },
    settings = RelaySettingsUi(
        showCompleted = settings.showCompleted,
        themeMode = when (settings.themeMode) {
            ThemeMode.SYSTEM -> RelayThemeMode.SYSTEM
            ThemeMode.LIGHT -> RelayThemeMode.LIGHT
            ThemeMode.DARK -> RelayThemeMode.DARK
        },
    ),
)

internal fun RelayThemeMode.toDataThemeMode(): ThemeMode = when (this) {
    RelayThemeMode.SYSTEM -> ThemeMode.SYSTEM
    RelayThemeMode.LIGHT -> ThemeMode.LIGHT
    RelayThemeMode.DARK -> ThemeMode.DARK
}

private fun ActivityKind.activityLabel(): String = when (this) {
    ActivityKind.CREATED -> "Task created"
    ActivityKind.EDITED -> "Task edited"
    ActivityKind.COMPLETED -> "Task completed"
    ActivityKind.UNCOMPLETED -> "Task reopened"
    ActivityKind.DELETED -> "Task deleted"
}
