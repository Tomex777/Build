package studio.artistscene.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import studio.artistscene.core.SceneProjectSummary

private val BrowserBackground = Color(0xFF15191F)
private val BrowserCard = Color(0xFF222832)
private val BrowserText = Color(0xFFF2F5F8)
private val BrowserMuted = Color(0xFFAAB4C2)

@Composable
internal fun ProjectBrowser(
    projects: List<SceneProjectSummary>,
    message: String?,
    onCreate: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SceneProjectSummary?>(null) }
    var deleting by remember { mutableStateOf<SceneProjectSummary?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
        color = BrowserBackground,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Mise", color = BrowserText, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("Artist Scene Studio", color = BrowserMuted, fontSize = 14.sp)
            }

            Button(
                onClick = { creating = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("create-project"),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New scene")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent projects", color = BrowserText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${projects.size}", color = BrowserMuted, fontSize = 12.sp)
            }

            if (message != null) {
                Text(message, color = Color(0xFFFFB4AB), fontSize = 12.sp, modifier = Modifier.testTag("browser-message"))
            }

            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).background(BrowserCard, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Your next scene starts here", color = BrowserText, fontWeight = FontWeight.SemiBold)
                        Text("Create a scene, then add a character, prop, or environment model.", color = BrowserMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onOpen = { onOpen(project.id) },
                            onRename = { editing = project },
                            onDelete = { deleting = project },
                        )
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
            title = { Text("Delete ${project.name}?") },
            text = { Text("This removes the saved scene from Mise. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { deleting = null; onDelete(project.id) },
                    modifier = Modifier.testTag("confirm-delete-project"),
                ) { Text("Delete") }
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
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(BrowserCard, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(project.name, color = BrowserText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    "${project.actorCount} objects · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(project.modifiedAtEpochMs))}",
                    color = BrowserMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onRename, modifier = Modifier.size(40.dp).testTag("rename-project-${project.id}")) {
                Icon(Icons.Default.Edit, contentDescription = "Rename ${project.name}", tint = BrowserMuted)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp).testTag("delete-project-${project.id}")) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${project.name}", tint = Color(0xFFFFB4AB))
            }
        }
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).testTag("project-open-${project.id}"),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Open scene") }
    }
}

@Composable
private fun ProjectNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                label = { Text("Scene name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().testTag("project-name-input"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm-project-name"),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
