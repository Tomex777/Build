package com.tomex777.relay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

private enum class RelayDestination {
    HOME,
    ACTIVITY,
    SETTINGS,
}

private data class TaskEditorState(
    val taskId: String? = null,
    val title: String = "",
    val note: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayApp(
    state: RelayUiState,
    actions: RelayActions,
    modifier: Modifier = Modifier,
) {
    RelayTheme(mode = state.settings.themeMode) {
        var destinationName by rememberSaveable { mutableStateOf(RelayDestination.HOME.name) }
        val destination = RelayDestination.valueOf(destinationName)
        var editor by remember { mutableStateOf<TaskEditorState?>(null) }
        var deleteCandidate by remember { mutableStateOf<RelayTaskUi?>(null) }
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(state.errorMessage) {
            state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (destination) {
                                RelayDestination.HOME -> "Relay"
                                RelayDestination.ACTIVITY -> "Activity"
                                RelayDestination.SETTINGS -> "Settings"
                            },
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            bottomBar = {
                NavigationBar(windowInsets = WindowInsets.navigationBars) {
                    NavigationBarItem(
                        selected = destination == RelayDestination.HOME,
                        onClick = { destinationName = RelayDestination.HOME.name },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Tasks") },
                    )
                    NavigationBarItem(
                        selected = destination == RelayDestination.ACTIVITY,
                        onClick = { destinationName = RelayDestination.ACTIVITY.name },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("Activity") },
                    )
                    NavigationBarItem(
                        selected = destination == RelayDestination.SETTINGS,
                        onClick = { destinationName = RelayDestination.SETTINGS.name },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                    )
                }
            },
            floatingActionButton = {
                if (destination == RelayDestination.HOME && !state.isLoading) {
                    ExtendedFloatingActionButton(
                        onClick = { editor = TaskEditorState() },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("Add task") },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                when (destination) {
                    RelayDestination.HOME -> HomeScreen(
                        state = state,
                        onToggle = actions.onSetTaskCompleted,
                        onEdit = { task ->
                            editor = TaskEditorState(
                                taskId = task.id,
                                title = task.title,
                                note = task.note,
                            )
                        },
                        onDelete = { deleteCandidate = it },
                    )

                    RelayDestination.ACTIVITY -> ActivityScreen(state = state)
                    RelayDestination.SETTINGS -> SettingsScreen(
                        state = state,
                        onShowCompletedChanged = actions.onShowCompletedChanged,
                        onThemeModeChanged = actions.onThemeModeChanged,
                    )
                }
            }
        }

        editor?.let { current ->
            TaskEditorSheet(
                initial = current,
                onDismiss = { editor = null },
                onSave = { title, note ->
                    current.taskId?.let { id ->
                        actions.onEditTask(id, title, note)
                    } ?: actions.onCreateTask(title, note)
                    editor = null
                },
            )
        }

        deleteCandidate?.let { task ->
            AlertDialog(
                onDismissRequest = { deleteCandidate = null },
                title = { Text("Delete task?") },
                text = { Text("“${task.title}” will be removed from your task list.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            actions.onDeleteTask(task.id)
                            deleteCandidate = null
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteCandidate = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: RelayUiState,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (RelayTaskUi) -> Unit,
    onDelete: (RelayTaskUi) -> Unit,
) {
    when {
        state.isLoading -> LoadingState("Loading tasks")
        state.tasks.isEmpty() -> EmptyState(
            title = "Nothing to do yet",
            body = "Add a task when something needs your attention.",
        )
        else -> {
            val visibleTasks = if (state.settings.showCompleted) {
                state.tasks
            } else {
                state.tasks.filterNot { it.isCompleted }
            }
            val completedCount = state.tasks.count { it.isCompleted }

            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    top = 12.dp,
                    end = 16.dp,
                    bottom = 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = "${state.tasks.size - completedCount} open · $completedCount done",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }

                if (visibleTasks.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    "Completed tasks are hidden",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "You can show them again from Settings.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = visibleTasks,
                        key = { it.id },
                    ) { task ->
                        TaskRow(
                            task = task,
                            onToggle = { onToggle(task.id, it) },
                            onEdit = { onEdit(task) },
                            onDelete = { onDelete(task) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: RelayTaskUi,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = onToggle,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = task.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Task options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityScreen(state: RelayUiState) {
    when {
        state.isLoading -> LoadingState("Loading activity")
        state.activity.isEmpty() -> EmptyState(
            title = "No activity yet",
            body = "Task changes will appear here as you use Relay.",
        )
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 720.dp)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 32.dp,
            ),
        ) {
            items(
                items = state.activity,
                key = { it.id },
            ) { entry ->
                ActivityRow(entry)
                HorizontalDivider(modifier = Modifier.padding(start = 52.dp))
            }
        }
    }
}

@Composable
private fun ActivityRow(entry: RelayActivityUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp),
        )
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
            )
            if (entry.detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatTimestamp(entry.timestampMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    state: RelayUiState,
    onShowCompletedChanged: (Boolean) -> Unit,
    onThemeModeChanged: (RelayThemeMode) -> Unit,
) {
    if (state.isLoading) {
        LoadingState("Loading settings")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 720.dp)
            .fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingToggleRow(
                title = "Show completed tasks",
                body = "Keep finished items visible on the task list.",
                checked = state.settings.showCompleted,
                onCheckedChange = onShowCompletedChanged,
            )
        }
        item {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp),
            )
        }
        item {
            ThemeModePicker(
                selected = state.settings.themeMode,
                onSelected = onThemeModeChanged,
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun ThemeModePicker(
    selected: RelayThemeMode,
    onSelected: (RelayThemeMode) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            RelayThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    RelayThemeMode.SYSTEM -> "Use device setting"
                    RelayThemeMode.LIGHT -> "Light"
                    RelayThemeMode.DARK -> "Dark"
                }

                OutlinedButton(
                    onClick = { onSelected(mode) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = if (selected == mode) "✓  $label" else label,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (mode != RelayThemeMode.DARK) {
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorSheet(
    initial: TaskEditorState,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember(initial) { mutableStateOf(initial.title) }
    var note by remember(initial) { mutableStateOf(initial.note) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = if (initial.taskId == null) "New task" else "Edit task",
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Task") },
                singleLine = true,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note") },
                minLines = 3,
                maxLines = 5,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = { onSave(title.trim(), note.trim()) },
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun LoadingState(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTimestamp(timestampMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(timestampMillis))
