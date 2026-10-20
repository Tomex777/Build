package studio.artistscene.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import studio.artistscene.core.SceneProjectSummary

private val BrowserBackground = Color(0xFF10171C)
private val BrowserCard = Color(0xFF1B2931)
private val BrowserInputBackground = Color(0xFF142128)
private val BrowserBorder = Color(0xFF37515A)
private val BrowserText = Color(0xFFF3FAFA)
private val BrowserMuted = Color(0xFFA7BBC0)
private val BrowserAccent = Color(0xFF87DBC9)

/** Keep a stable, most-recent-first list even when files are returned out of order. */
internal fun filterRecentScenes(projects: List<SceneProjectSummary>, query: String): List<SceneProjectSummary> {
    val term = query.trim()
    return projects.asSequence()
        .filter { term.isEmpty() || it.name.contains(term, ignoreCase = true) }
        .sortedWith(compareByDescending<SceneProjectSummary> { it.modifiedAtEpochMs }.thenBy { it.id })
        .toList()
}

@Composable
internal fun ProjectBrowser(
    projects: List<SceneProjectSummary>,
    message: String?,
    onCreate: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SceneProjectSummary?>(null) }
    var deleting by remember { mutableStateOf<SceneProjectSummary?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val visibleProjects = remember(projects, searchQuery) { filterRecentScenes(projects, searchQuery) }
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
        color = BrowserBackground,
    ) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(
                modifier = Modifier.widthIn(max = 760.dp)
                    .fillMaxWidth().fillMaxHeight()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Mise", color = BrowserText, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        Text("Scenes", color = BrowserMuted, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { creating = true },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("create-project"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("New scene")
                    }
                }

                // Keep the search field available until its query is cleared, even after deletions.
                if (projects.size >= 4 || searchQuery.isNotBlank()) {
                    BrowserTextInput(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Find a scene",
                        label = "Search scenes",
                        modifier = Modifier.fillMaxWidth().testTag("search-projects"),
                        showSearchIcon = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", color = BrowserText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (searchQuery.isBlank()) "${projects.size} scenes" else "${visibleProjects.size} of ${projects.size}",
                        color = BrowserMuted,
                        fontSize = 12.sp,
                    )
                }

                if (message != null) {
                    Text(message, color = Color(0xFFFFB4AB), fontSize = 12.sp, modifier = Modifier.testTag("browser-message"))
                }

                if (visibleProjects.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f)
                            .background(BrowserCard, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                if (projects.isEmpty()) "No scenes yet" else "No matching scenes",
                                color = BrowserText, fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (projects.isEmpty()) "Create a scene to begin." else "Try a different name.",
                                color = BrowserMuted, fontSize = 12.sp,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        items(visibleProjects, key = { it.id }) { project ->
                            ProjectCard(
                                project = project,
                                onOpen = { focusManager.clearFocus(); onOpen(project.id) },
                                onRename = { editing = project },
                                onDuplicate = { onDuplicate(project.id) },
                                onDelete = { deleting = project },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        ProjectNameDialog(
            title = "Create a scene",
            initialName = "Untitled Scene",
            confirmLabel = "Create",
            onDismiss = { creating = false },
            onConfirm = { name -> creating = false; onCreate(name) },
        )
    }
    editing?.let { project ->
        ProjectNameDialog(
            title = "Rename scene",
            initialName = project.name,
            confirmLabel = "Save name",
            onDismiss = { editing = null },
            onConfirm = { name -> editing = null; onRename(project.id, name) },
        )
    }
    deleting?.let { project ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            title = { Text("Delete ${project.name}?") },
            text = { Text("This permanently deletes the saved scene. It cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { deleting = null; onDelete(project.id) },
                    modifier = Modifier.testTag("confirm-delete-project"),
                ) { Text("Delete", color = Color(0xFFFFB4AB)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProjectCard(
    project: SceneProjectSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(BrowserCard, RoundedCornerShape(14.dp))
            .border(1.dp, BrowserBorder.copy(alpha = .55f), RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "Open ${project.name}", onClick = onOpen)
            .testTag("project-open-${project.id}")
            .padding(start = 14.dp, end = 7.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).heightIn(min = 48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                project.name, color = BrowserText, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${project.actorCount} objects · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(project.modifiedAtEpochMs))}",
                color = BrowserMuted, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(48.dp).testTag("project-menu-${project.id}"),
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Scene options", tint = BrowserMuted)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier.semantics { testTagsAsResourceId = true },
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                    onClick = { menuOpen = false; onRename() },
                    modifier = Modifier.testTag("rename-project-${project.id}"),
                )
                DropdownMenuItem(
                    text = { Text("Duplicate") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                    onClick = { menuOpen = false; onDuplicate() },
                    modifier = Modifier.testTag("duplicate-project-${project.id}"),
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFFFB4AB)) },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { menuOpen = false; onDelete() },
                    modifier = Modifier.testTag("delete-project-${project.id}"),
                )
            }
        }
    }
}

/** The browser uses an actual Compose BasicTextField rather than a default Android box. */
@Composable
internal fun BrowserTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    modifier: Modifier = Modifier,
    showSearchIcon: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = BrowserText, fontSize = 15.sp),
        cursorBrush = SolidColor(BrowserAccent),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier
            .background(BrowserInputBackground, RoundedCornerShape(12.dp))
            .border(1.dp, BrowserBorder, RoundedCornerShape(12.dp))
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showSearchIcon) {
                    Icon(
                        Icons.Default.Search, contentDescription = null,
                        modifier = Modifier.size(20.dp), tint = BrowserMuted,
                    )
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = BrowserMuted, fontSize = 15.sp)
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
private fun ProjectNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val canSubmit = name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.imePadding().semantics { testTagsAsResourceId = true },
        title = { Text(title) },
        text = {
            BrowserTextInput(
                value = name,
                onValueChange = { name = it.take(80) },
                placeholder = "Scene name",
                label = "Scene name",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (canSubmit) onConfirm(name.trim())
                }),
                modifier = Modifier.fillMaxWidth().testTag("project-name-input"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (canSubmit) onConfirm(name.trim()) },
                enabled = canSubmit,
                modifier = Modifier.testTag("confirm-project-name"),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
