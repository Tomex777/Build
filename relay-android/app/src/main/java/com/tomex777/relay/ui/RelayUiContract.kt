package com.tomex777.relay.ui

enum class RelayThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class RelayTaskUi(
    val id: String,
    val title: String,
    val note: String = "",
    val isCompleted: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class RelayActivityUi(
    val id: String,
    val title: String,
    val detail: String = "",
    val timestampMillis: Long,
)

data class RelaySettingsUi(
    val showCompleted: Boolean = true,
    val themeMode: RelayThemeMode = RelayThemeMode.SYSTEM,
)

data class RelayUiState(
    val isLoading: Boolean = false,
    val tasks: List<RelayTaskUi> = emptyList(),
    val activity: List<RelayActivityUi> = emptyList(),
    val settings: RelaySettingsUi = RelaySettingsUi(),
    val errorMessage: String? = null,
)

data class RelayActions(
    val onCreateTask: (title: String, note: String) -> Unit = { _, _ -> },
    val onEditTask: (id: String, title: String, note: String) -> Unit = { _, _, _ -> },
    val onDeleteTask: (id: String) -> Unit = {},
    val onSetTaskCompleted: (id: String, completed: Boolean) -> Unit = { _, _ -> },
    val onShowCompletedChanged: (Boolean) -> Unit = {},
    val onThemeModeChanged: (RelayThemeMode) -> Unit = {},
)
