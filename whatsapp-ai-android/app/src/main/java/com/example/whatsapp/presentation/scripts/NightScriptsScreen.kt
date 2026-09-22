package com.example.whatsapp.presentation.scripts

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsapp.data.scripts.NightScriptWorkspace
import com.example.whatsapp.data.scripts.NightWorkspaceArea
import com.example.whatsapp.data.scripts.NightWorkspaceFile
import com.example.whatsapp.data.scripts.NightWorkspaceProject
import java.text.DateFormat
import java.util.Date

private val ScriptsBg = Color(0xFF0B0F11)
private val ScriptsSurface = Color(0xFF171C1F)
private val ScriptsSurfaceStrong = Color(0xFF20272A)
private val ScriptsText = Color(0xFFE7EAEC)
private val ScriptsMuted = Color(0xFF9CA5A9)

private enum class WorkspaceMode {
    Scripts,
    Projects,
}

@Composable
fun NightScriptsScreen(
    onBack: () -> Unit,
    tabMode: Boolean = false,
) {
    val context = LocalContext.current
    val workspace = remember { NightScriptWorkspace.get(context.applicationContext) }
    var revision by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(WorkspaceMode.Scripts) }
    var selectedProject by remember { mutableStateOf<NightWorkspaceProject?>(null) }
    var editingFile by remember { mutableStateOf<NightWorkspaceFile?>(null) }
    var pendingExport by remember { mutableStateOf<NightWorkspaceFile?>(null) }

    val scripts = remember(revision, mode) { workspace.listScripts() }
    val projects = remember(revision, mode) { workspace.listProjects() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            workspace.importScript(uri)
            revision += 1
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val file = pendingExport
        pendingExport = null
        if (uri != null && file != null) {
            workspace.exportFile(file, uri)
        }
    }

    if (editingFile != null) {
        val active = editingFile ?: return
        NightWorkspaceEditor(
            file = active,
            tabMode = tabMode,
            initialText = remember(active.relativePath, revision) {
                workspace.readFile(active)
            },
            onBack = {
                editingFile = null
                revision += 1
            },
            onSave = { text ->
                editingFile = workspace.writeFile(active, text)
                revision += 1
            },
            onRename = { name ->
                editingFile = workspace.renameFile(active, name)
                revision += 1
            },
            onExport = {
                pendingExport = active
                exportLauncher.launch(active.name)
            },
            onDelete = {
                workspace.deleteFile(active)
                editingFile = null
                revision += 1
            },
        )
        return
    }

    if (selectedProject != null) {
        val project = selectedProject ?: return
        val files = remember(project.relativePath, revision) {
            workspace.listProjectFiles(project.relativePath)
        }

        BackHandler { selectedProject = null }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScriptsBg)
                .then(
                    if (tabMode) Modifier else Modifier
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ),
        ) {
            WorkspaceTopBar(
                title = project.name,
                subtitle = "HTML, CSS and JavaScript project",
                onBack = { selectedProject = null },
            )

            if (files.isEmpty()) {
                WorkspaceEmpty(
                    icon = Icons.Default.Folder,
                    title = "Project is empty",
                    body = "Project files will appear here.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(files, key = { it.relativePath }) { file ->
                        WorkspaceFileRow(
                            file = file,
                            onClick = { editingFile = file },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(20.dp)) }
                }
            }
        }
        return
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScriptsBg)
            .then(
                if (tabMode) Modifier else Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ),
    ) {
        if (!tabMode) {
            WorkspaceTopBar(
                title = "Workspace",
                subtitle = "Scripts and local web projects",
                onBack = onBack,
            )
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkspaceChip(
                text = "Scripts",
                selected = mode == WorkspaceMode.Scripts,
                onClick = { mode = WorkspaceMode.Scripts },
            )
            WorkspaceChip(
                text = "Projects",
                selected = mode == WorkspaceMode.Projects,
                onClick = { mode = WorkspaceMode.Projects },
            )
        }

        when (mode) {
            WorkspaceMode.Scripts -> {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    WorkspaceAction(
                        label = "New script",
                        icon = Icons.Default.Code,
                        onClick = {
                            editingFile = workspace.createScript()
                            revision += 1
                        },
                    )
                    WorkspaceAction(
                        label = "Import",
                        icon = Icons.Default.FileUpload,
                        onClick = {
                            importLauncher.launch(
                                arrayOf(
                                    "text/javascript",
                                    "application/javascript",
                                    "text/plain",
                                    "application/octet-stream",
                                )
                            )
                        },
                    )
                }

                if (scripts.isEmpty()) {
                    WorkspaceEmpty(
                        icon = Icons.Default.Code,
                        title = "No scripts yet",
                        body =
                            "Create JavaScript here or import a .js file. Scripts stay in Night's private workspace and can be exported whenever you want.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(
                                text = "JavaScript",
                                color = ScriptsMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(
                                    start = 18.dp,
                                    top = 8.dp,
                                    bottom = 2.dp,
                                ),
                            )
                        }
                        items(scripts, key = { it.relativePath }) { file ->
                            WorkspaceFileRow(
                                file = file,
                                onClick = { editingFile = file },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(20.dp)) }
                    }
                }
            }

            WorkspaceMode.Projects -> {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    WorkspaceAction(
                        label = "New web project",
                        icon = Icons.Default.Folder,
                        onClick = {
                            selectedProject = workspace.createProject()
                            revision += 1
                        },
                    )
                }

                if (projects.isEmpty()) {
                    WorkspaceEmpty(
                        icon = Icons.Default.Folder,
                        title = "No projects yet",
                        body =
                            "Create a project and Night will give it index.html, styles.css and app.js inside the private Projects workspace.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(
                                text = "Web projects",
                                color = ScriptsMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(
                                    start = 18.dp,
                                    top = 8.dp,
                                    bottom = 2.dp,
                                ),
                            )
                        }
                        items(projects, key = { it.relativePath }) { project ->
                            Surface(
                                color = ScriptsSurface,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .padding(horizontal = 14.dp)
                                    .fillMaxWidth()
                                    .clickable { selectedProject = project },
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        color = ScriptsSurfaceStrong,
                                        shape = RoundedCornerShape(13.dp),
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(25.dp),
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = project.name,
                                            color = ScriptsText,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text =
                                                "index.html • styles.css • app.js",
                                            color = ScriptsMuted,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = ScriptsText,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ScriptsText,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = ScriptsMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable
private fun WorkspaceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                ScriptsSurface
            },
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    ScriptsMuted
                },
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun WorkspaceAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        color = ScriptsSurface,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                color = ScriptsText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun WorkspaceFileRow(
    file: NightWorkspaceFile,
    onClick: () -> Unit,
) {
    Surface(
        color = ScriptsSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = ScriptsSurfaceStrong,
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector =
                            if (file.name.endsWith(".js") || file.name.endsWith(".mjs")) {
                                Icons.Default.Code
                            } else {
                                Icons.Default.Description
                            },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = ScriptsText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        formatWorkspaceSize(file.sizeBytes) +
                            " • " +
                            DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM,
                                DateFormat.SHORT,
                            ).format(Date(file.modifiedAt)),
                    color = ScriptsMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                if (file.relativePath != file.name) {
                    Text(
                        text = file.relativePath,
                        color = ScriptsMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceEmpty(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(34.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = ScriptsSurface,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.size(82.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Text(
                text = title,
                color = ScriptsText,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = body,
                color = ScriptsMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun NightWorkspaceEditor(
    file: NightWorkspaceFile,
    tabMode: Boolean,
    initialText: String,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    onRename: (String) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(file.relativePath) { mutableStateOf(initialText) }
    var renameMode by remember(file.relativePath) { mutableStateOf(false) }
    var nameDraft by remember(file.relativePath) { mutableStateOf(file.name) }
    var dirty by remember(file.relativePath) { mutableStateOf(false) }

    BackHandler {
        if (dirty) onSave(text)
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScriptsBg)
            .then(
                if (tabMode) Modifier else Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (dirty) onSave(text)
                    onBack()
                },
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = ScriptsText,
                )
            }

            if (renameMode) {
                Surface(
                    color = ScriptsSurfaceStrong,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                ) {
                    BasicTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = ScriptsText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    )
                }
                IconButton(
                    onClick = {
                        onRename(nameDraft)
                        renameMode = false
                    },
                ) {
                    Icon(
                        Icons.Default.Done,
                        contentDescription = "Rename",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            nameDraft = file.name
                            renameMode = true
                        },
                ) {
                    Text(
                        text = file.name,
                        color = ScriptsText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            if (file.area == NightWorkspaceArea.Scripts) {
                                "Night script • tap name to rename"
                            } else {
                                "Project file • tap name to rename"
                            },
                        color = ScriptsMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }

                IconButton(
                    onClick = {
                        nameDraft = file.name
                        renameMode = true
                    },
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename",
                        tint = ScriptsMuted,
                    )
                }
            }

            IconButton(
                onClick = {
                    onSave(text)
                    dirty = false
                },
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = "Save",
                    tint = if (dirty) MaterialTheme.colorScheme.primary else ScriptsMuted,
                )
            }

            IconButton(onClick = onExport) {
                Icon(
                    Icons.Default.FileDownload,
                    contentDescription = "Export",
                    tint = ScriptsMuted,
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFFF8791),
                )
            }
        }

        Surface(
            color = ScriptsSurface,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 4.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    dirty = true
                },
                textStyle = TextStyle(
                    color = ScriptsText,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }
}

private fun formatWorkspaceSize(bytes: Long): String =
    when {
        bytes >= 1024L * 1024L ->
            String.format("%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024L ->
            String.format("%.0f KB", bytes / 1024f)
        else -> "$bytes B"
    }
