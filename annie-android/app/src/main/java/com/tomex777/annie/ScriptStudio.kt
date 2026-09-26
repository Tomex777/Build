package com.tomex777.annie

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import io.github.dingyi222666.monarch.languages.TypescriptLanguage
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.langs.monarch.MonarchLanguage
import io.github.rosemoe.sora.langs.monarch.registry.MonarchGrammarRegistry
import io.github.rosemoe.sora.langs.monarch.registry.dsl.monarchLanguages
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.subscribeAlways
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StudioPanel = Color(0xFF091522)
private val StudioSurface = Color(0xFF102139)
private val StudioSurface2 = Color(0xFF172D46)
private val StudioBorder = Color(0xFF294562)
private val StudioText = Color(0xFFEEF5FF)
private val StudioMuted = Color(0xFF9CB2CC)
private val StudioBlue = Color(0xFF42B9F5)
private val StudioGreen = Color(0xFF54D6AE)
private val StudioDanger = Color(0xFFFF7586)

private enum class StudioPage(val title: String) { FILES("Files"), EDITOR("Editor"), API("API") }
private enum class FileAction { RENAME, SHARE, EXPORT, DELETE, ENABLE, DISABLE }

/** Full-screen, mobile-first local script workspace. The script runtime remains in ScriptWorkspace. */
@Composable
internal fun ScriptStudioSheet(
    workspace: ScriptWorkspace,
    onCommandsReloaded: (List<ScriptCommand>) -> Unit,
    onClose: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        ScriptStudioContent(workspace, onCommandsReloaded, onClose)
    }
}

@Composable
private fun ScriptStudioContent(
    workspace: ScriptWorkspace,
    onCommandsReloaded: (List<ScriptCommand>) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var projects by remember { mutableStateOf(workspace.files.listProjects()) }
    var selectedProjectId by remember { mutableStateOf(projects.firstOrNull()?.id) }
    var selectedPath by remember { mutableStateOf(projects.firstOrNull()?.entryPath) }
    var editorValue by remember {
        val first = projects.firstOrNull()
        val initial = first?.entryPath?.let { first.files[it] }.orEmpty()
        mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
    }
    var savedSource by remember { mutableStateOf(editorValue.text) }
    var page by remember { mutableStateOf(StudioPage.FILES) }
    var status by remember { mutableStateOf("Ready") }
    var newFileName by remember { mutableStateOf("") }
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogValue by remember { mutableStateOf("") }
    var dialogAction by remember { mutableStateOf<(String) -> Unit>({}) }
    var query by remember { mutableStateOf("") }
    var codeEditor by remember { mutableStateOf<CodeEditor?>(null) }
    var logVersion by remember { mutableStateOf(0) }
    var consoleHeight by remember { mutableStateOf(166.dp) }
    var consoleCollapsed by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }
    var apiSearch by remember { mutableStateOf("") }

    fun refreshProjects(preferredProject: String? = selectedProjectId, preferredPath: String? = selectedPath) {
        projects = workspace.files.listProjects()
        val project = projects.firstOrNull { it.id == preferredProject } ?: projects.firstOrNull()
        selectedProjectId = project?.id
        val path = preferredPath?.takeIf { it in project?.files.orEmpty() } ?: project?.entryPath
        selectedPath = path
        val source = path?.let { project?.files?.get(it) }.orEmpty()
        val previous = editorValue
        val selection = if (previous.text == source) {
            TextRange(previous.selection.start.coerceIn(0, source.length), previous.selection.end.coerceIn(0, source.length))
        } else TextRange(source.length)
        editorValue = TextFieldValue(source, selection = selection)
        savedSource = source
    }

    fun selectFile(project: ScriptProject, path: String, openEditor: Boolean = true) {
        selectedProjectId = project.id
        selectedPath = path
        val source = project.files[path].orEmpty()
        editorValue = TextFieldValue(source, selection = TextRange(source.length))
        savedSource = source
        status = "Opened $path"
        if (openEditor) page = StudioPage.EDITOR
    }

    fun saveScript(runAfterSave: Boolean = false) {
        val project = projects.firstOrNull { it.id == selectedProjectId } ?: run {
            status = "Choose a script first"
            return
        }
        val path = selectedPath ?: return
        val source = editorValue.text
        scope.launch {
            runCatching {
                workspace.files.writeFile(project.id, path, source)
                val commands = workspace.reload()
                onCommandsReloaded(commands)
                if (runAfterSave) {
                    val command = commands.firstOrNull { it.scriptId == project.id }
                        ?: error("This script did not register a command")
                    workspace.execute(command.name, "/${command.name} test", "script-editor", System.nanoTime())
                } else null
            }.onSuccess { result ->
                savedSource = source
                refreshProjects(project.id, path)
                status = if (runAfterSave) {
                    val response = runCatching { org.json.JSONObject(result.orEmpty()) }.getOrNull()
                    response?.optString("text")?.takeIf(String::isNotBlank)
                        ?: response?.optString("type")?.let { "Test returned $it" }
                        ?: "Test completed"
                } else "Saved and reloaded"
                logVersion++
            }.onFailure { status = it.message ?: if (runAfterSave) "Run failed" else "Save failed"; logVersion++ }
        }
    }

    val importScript = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val source = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("Could not read the selected JavaScript file")
                }
                val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
                val base = displayName.substringAfterLast(':').substringAfterLast('/').removeSuffix(".js")
                    .replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').take(48).ifBlank { "imported" }
                var candidate = base
                var suffix = 2
                while (File(workspace.files.root, "$candidate.js").exists() || File(workspace.files.root, candidate).exists()) {
                    candidate = "${base}_$suffix"
                    suffix++
                }
                val file = workspace.files.createScript(candidate)
                workspace.files.writeFile(file.nameWithoutExtension, file.name, source)
                onCommandsReloaded(workspace.reload())
                refreshProjects(file.nameWithoutExtension, file.name)
                status = "Imported ${file.name}"
            }.onFailure { status = it.message ?: "Import failed" }
        }
    }

    val exportScript = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/javascript")) { uri ->
        val pending = pendingExport
        pendingExport = null
        if (uri != null && pending != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(pending.second) }
                        ?: error("Could not write the JavaScript file")
                }
            }.onSuccess { status = "Exported ${pending.first}" }
                .onFailure { status = it.message ?: "Export failed" }
        }
    }

    fun exportFile(name: String, source: String) {
        pendingExport = name to source
        exportScript.launch(name)
    }

    fun shareFile(name: String, source: String) {
        runCatching {
            val directory = File(context.cacheDir, "shared-scripts").apply { mkdirs() }
            val sharedFile = File(directory, name).apply { writeText(source) }
            val sharedUri = FileProvider.getUriForFile(context, "${context.packageName}.script-files", sharedFile)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/javascript"
                putExtra(Intent.EXTRA_STREAM, sharedUri)
                clipData = android.content.ClipData.newUri(context.contentResolver, name, sharedUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share script").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            status = "Sharing $name"
        }.onFailure { status = it.message ?: "Could not share script" }
    }

    fun askForText(title: String, initial: String = "", action: (String) -> Unit) {
        dialogTitle = title
        dialogValue = initial
        dialogAction = action
    }

    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }
    val dirty = editorValue.text != savedSource
    val logs = remember(logVersion) { workspace.logs().takeLast(250).reversed() }

    Column(
        Modifier.fillMaxSize().background(StudioPanel).statusBarsPadding().navigationBarsPadding()
            .imePadding().testTag("script_studio"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Script Studio", color = StudioText, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(selectedProject?.let { "${it.name} · ${selectedPath ?: it.entryPath}" } ?: "Your JavaScript workspace", color = StudioMuted, fontSize = 12.sp, maxLines = 1)
            }
            StudioAction(if (dirty) "Save" else "Saved", emphasized = dirty, onClick = { saveScript() }, enabled = selectedProject != null && selectedPath != null)
            Spacer(Modifier.width(8.dp))
            StudioAction("×", onClick = onClose, contentDescription = "Close Script Studio")
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(StudioSurface, RoundedCornerShape(13.dp)).padding(4.dp)) {
            StudioPage.entries.forEach { destination ->
                StudioTab(destination.title, page == destination, Modifier.weight(1f)) { page = destination }
            }
        }

        when (page) {
            StudioPage.FILES -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioAction("＋ New File", emphasized = true, onClick = {
                        val owner = selectedProject?.takeIf { File(workspace.files.root, it.id).isDirectory }
                        askForText(if (owner == null) "New script" else "New file", "") { rawName ->
                            runCatching {
                                val name = rawName.trim().let { if (it.endsWith(".js", true)) it else "$it.js" }
                                if (owner == null) workspace.files.createScript(name)
                                else workspace.files.createFile(owner.id, name)
                            }.onSuccess { file ->
                                val id = if (owner == null) file.nameWithoutExtension else owner!!.id
                                val path = if (owner == null) file.name else file.relativeTo(File(workspace.files.root, id)).invariantSeparatorsPath
                                refreshProjects(id, path)
                                status = "Created ${file.name}"
                                page = StudioPage.EDITOR
                            }.onFailure { status = it.message ?: "Could not create file" }
                        }
                    })
                    StudioAction("＋ Folder", onClick = {
                        askForText("New folder") { name ->
                            runCatching { workspace.files.createFolder(name) }
                                .onSuccess { folder -> refreshProjects(folder.name, "main.js"); status = "Created ${folder.name}" }
                                .onFailure { status = it.message ?: "Could not create folder" }
                        }
                    })
                    StudioAction("Import", onClick = {
                        importScript.launch(arrayOf("application/javascript", "text/javascript", "application/x-javascript", "text/plain"))
                    })
                }

                StudioInput(query, { query = it }, "Search files", Modifier.fillMaxWidth().padding(horizontal = 14.dp))
                if (projects.isEmpty()) {
                    EmptyStudioState("No scripts yet", "Create a file or folder to start a script project.")
                } else {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        projects.forEach { project ->
                            val isFolder = File(workspace.files.root, project.id).isDirectory
                            val shown = if (isFolder) project.name else project.entryPath
                            if (shown.contains(query, true)) {
                                item(key = "project:${project.id}") {
                                    ScriptFileRow(
                                        name = shown,
                                        subtitle = if (isFolder) "Folder · ${project.files.size} ${if (project.files.size == 1) "file" else "files"}" else "JavaScript",
                                        isFolder = isFolder,
                                        isEntry = true,
                                        selected = selectedProjectId == project.id && selectedPath == project.entryPath,
                                        onClick = { selectFile(project, project.entryPath) },
                                        actions = listOf(
                                            FileAction.RENAME,
                                            FileAction.SHARE,
                                            FileAction.EXPORT,
                                            if (project.enabled) FileAction.DISABLE else FileAction.ENABLE,
                                            FileAction.DELETE,
                                        ),
                                        onAction = { action ->
                                            when (action) {
                                                FileAction.RENAME -> askForText("Rename ${if (isFolder) "project" else "script"}", project.name) { next ->
                                                    runCatching { workspace.files.renameProject(project.id, next) }
                                                        .onSuccess { id -> refreshProjects(id, if (isFolder) "main.js" else "$id.js"); status = "Renamed to $id" }
                                                        .onFailure { status = it.message ?: "Rename failed" }
                                                }
                                                FileAction.SHARE -> shareFile(project.entryPath, project.files[project.entryPath].orEmpty())
                                                FileAction.EXPORT -> exportFile(project.entryPath, project.files[project.entryPath].orEmpty())
                                                FileAction.DELETE -> askForText("Type ${project.name} to delete", "") { confirm ->
                                                    if (confirm == project.name) {
                                                        runCatching { workspace.files.deleteProject(project.id) }
                                                            .onSuccess {
                                                                refreshProjects(null, null)
                                                                scope.launch { onCommandsReloaded(workspace.reload()) }
                                                                status = "Project deleted"
                                                            }
                                                            .onFailure { status = it.message ?: "Delete failed" }
                                                    } else status = "Delete cancelled"
                                                }
                                                FileAction.ENABLE, FileAction.DISABLE -> {
                                                    val enabled = action == FileAction.ENABLE
                                                    runCatching { workspace.files.setEnabled(project.id, enabled) }
                                                        .onSuccess {
                                                            refreshProjects(project.id, selectedPath)
                                                            scope.launch { onCommandsReloaded(workspace.reload()) }
                                                            status = if (enabled) "Script enabled" else "Script disabled"
                                                        }
                                                        .onFailure { status = it.message ?: "Could not update script" }
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                            if (isFolder) {
                                project.files.keys.sorted().filter { it.contains(query, true) }.forEach { path ->
                                    item(key = "file:${project.id}:$path") {
                                        ScriptFileRow(
                                            name = path,
                                            subtitle = "${project.name} / $path",
                                            isFolder = false,
                                            isEntry = path == project.entryPath,
                                            selected = selectedProjectId == project.id && selectedPath == path,
                                            indent = true,
                                            onClick = { selectFile(project, path) },
                                            actions = buildList {
                                                add(FileAction.RENAME)
                                                add(FileAction.SHARE)
                                                add(FileAction.EXPORT)
                                                if (path != project.entryPath) add(FileAction.DELETE)
                                            },
                                            onAction = { action ->
                                                when (action) {
                                                    FileAction.RENAME -> askForText("Rename or move file", path) { next ->
                                                        runCatching { workspace.files.renameFile(project.id, path, next) }
                                                            .onSuccess { moved -> refreshProjects(project.id, moved); status = "Moved to $moved" }
                                                            .onFailure { status = it.message ?: "Rename failed" }
                                                    }
                                                    FileAction.SHARE -> shareFile(path.substringAfterLast('/'), project.files[path].orEmpty())
                                                    FileAction.EXPORT -> exportFile(path.substringAfterLast('/'), project.files[path].orEmpty())
                                                    FileAction.DELETE -> runCatching { workspace.files.deleteFile(project.id, path) }
                                                        .onSuccess { refreshProjects(project.id, project.entryPath); status = "Deleted $path" }
                                                        .onFailure { status = it.message ?: "Delete failed" }
                                                    else -> Unit
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                StudioStatus(status, Modifier.padding(horizontal = 16.dp, vertical = 7.dp))
            }

            StudioPage.EDITOR -> {
                val project = selectedProject
                val path = selectedPath
                if (project == null || path == null) {
                    EmptyStudioState("Choose a file", "Open a script from Files to edit it here.")
                } else {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 11.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(path, color = StudioText, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 14.sp, maxLines = 1)
                                if (path == project.entryPath) EntryBadge()
                            }
                            Text(project.name, color = StudioMuted, fontSize = 11.sp)
                        }
                        StudioAction("Run", emphasized = true, onClick = { saveScript(runAfterSave = true) })
                    }
                    val matches = remember(query, editorValue.text) {
                        if (query.isBlank()) 0 else Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(editorValue.text).count()
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StudioInput(query, { query = it }, "Find in file", Modifier.weight(1f))
                        StudioAction("Find", enabled = query.isNotBlank(), onClick = {
                            codeEditor?.searcher?.search(query, EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true))
                        })
                        Text(if (query.isBlank()) "" else "$matches", color = StudioMuted, fontSize = 11.sp)
                        StudioAction("↶", enabled = codeEditor?.text?.canUndo() == true, onClick = { codeEditor?.undo() })
                        StudioAction("↷", enabled = codeEditor?.text?.canRedo() == true, onClick = { codeEditor?.redo() })
                    }
                    ScriptCodeEditor(
                        value = editorValue,
                        onValueChange = { editorValue = it },
                        onEditorReady = { codeEditor = it },
                        enabled = true,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).testTag("script_editor"),
                    )
                    ScriptConsolePanel(
                        logs = logs,
                        height = if (consoleCollapsed) 46.dp else consoleHeight,
                        collapsed = consoleCollapsed,
                        onRefresh = { logVersion++ },
                        onToggle = { consoleCollapsed = !consoleCollapsed },
                        onDrag = { delta ->
                            consoleHeight = (consoleHeight - delta.dp).coerceIn(100.dp, 340.dp)
                            consoleCollapsed = false
                        },
                        onLogClick = { row ->
                            val match = Regex("(?:line\\s+|:)(\\d+)(?::(\\d+))?", RegexOption.IGNORE_CASE).find(row.message)
                            val line = match?.groupValues?.getOrNull(1)?.toIntOrNull()
                            val column = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1
                            if (line != null) codeEditor?.setSelection((line - 1).coerceAtLeast(0), (column - 1).coerceAtLeast(0))
                        },
                    )
                    StudioStatus(status, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
            }

            StudioPage.API -> ApiReferenceScreen(apiSearch, { apiSearch = it })
        }

        if (dialogTitle != null) {
            AlertDialog(
                onDismissRequest = { dialogTitle = null },
                containerColor = StudioSurface,
                titleContentColor = StudioText,
                textContentColor = StudioMuted,
                title = { Text(dialogTitle.orEmpty()) },
                text = { StudioInput(dialogValue, { dialogValue = it }, "Name", Modifier.fillMaxWidth()) },
                confirmButton = {
                    TextButton(onClick = {
                        val value = dialogValue
                        dialogTitle = null
                        dialogAction(value)
                    }) { Text("Continue", color = StudioBlue) }
                },
                dismissButton = { TextButton(onClick = { dialogTitle = null }) { Text("Cancel", color = StudioMuted) } },
            )
        }
    }
}

@Composable
private fun ScriptFileRow(
    name: String,
    subtitle: String,
    isFolder: Boolean,
    isEntry: Boolean,
    selected: Boolean,
    indent: Boolean = false,
    actions: List<FileAction>,
    onClick: () -> Unit,
    onAction: (FileAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = if (selected) Color(0xFF14304C) else StudioSurface,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, if (selected) StudioBlue.copy(alpha = .65f) else StudioBorder.copy(alpha = .72f)),
        modifier = Modifier.fillMaxWidth().padding(start = if (indent) 18.dp else 0.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 13.dp, end = 8.dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (isFolder) "▰" else "JS", color = if (isFolder) StudioBlue else StudioGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = if (isFolder) FontFamily.Default else FontFamily.Monospace)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(name, color = StudioText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (isEntry) EntryBadge()
                }
                Text(subtitle, color = StudioMuted, fontSize = 11.sp, maxLines = 1)
            }
            Box {
                Text("⋮", color = StudioMuted, fontSize = 22.sp, modifier = Modifier.clickable { expanded = true }.padding(horizontal = 8.dp, vertical = 2.dp))
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    actions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label(), color = if (action == FileAction.DELETE) StudioDanger else StudioText) },
                            onClick = { expanded = false; onAction(action) },
                        )
                    }
                }
            }
        }
    }
}

private fun FileAction.label(): String = when (this) {
    FileAction.RENAME -> "Rename"
    FileAction.SHARE -> "Share"
    FileAction.EXPORT -> "Export"
    FileAction.DELETE -> "Delete"
    FileAction.ENABLE -> "Enable"
    FileAction.DISABLE -> "Disable"
}

@Composable
private fun EntryBadge() {
    Surface(color = Color(0xFF173A36), shape = RoundedCornerShape(6.dp)) {
        Text("MAIN", color = StudioGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

@Composable
private fun ScriptConsolePanel(
    logs: List<ScriptLog>,
    height: Dp,
    collapsed: Boolean,
    onRefresh: () -> Unit,
    onToggle: () -> Unit,
    onDrag: (Float) -> Unit,
    onLogClick: (ScriptLog) -> Unit,
) {
    val density = LocalDensity.current.density
    Column(Modifier.fillMaxWidth().height(height).background(Color(0xFF0D1B2A))) {
        Box(
            Modifier.fillMaxWidth().height(12.dp).pointerInput(Unit) {
                detectVerticalDragGestures(onVerticalDrag = { _, amount -> onDrag(amount / density) })
            }.testTag("script_console_drag_handle"),
            contentAlignment = Alignment.Center,
        ) {
            Surface(color = StudioBorder, shape = CircleShape, modifier = Modifier.size(width = 42.dp, height = 4.dp)) {}
        }
        Row(Modifier.fillMaxWidth().padding(start = 15.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Output", color = StudioText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("${logs.size} lines", color = StudioMuted, fontSize = 10.sp)
            Spacer(Modifier.width(12.dp))
            Text("↻", color = StudioMuted, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onRefresh).padding(horizontal = 4.dp))
            Text(if (collapsed) "⌃" else "⌄", color = StudioBlue, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onToggle).padding(horizontal = 5.dp))
        }
        if (!collapsed) {
            if (logs.isEmpty()) {
                Text("Run the script to see output and errors here.", color = StudioMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp))
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(logs) { row ->
                        val tone = when (row.level) { "ERROR" -> StudioDanger; "WARN" -> Color(0xFFFFC86A); else -> StudioText }
                        val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
                        Text(
                            "${formatter.format(Date(row.atMillis))}  ${row.level}  ${row.message}",
                            color = tone,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onLogClick(row) }.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiReferenceScreen(search: String, onSearch: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val entries = remember {
        listOf(
            ApiEntry("Commands", "annie.commands.register({...})", "Register a slash command with a name, description, and async execute(ctx) handler.", "annie.commands.register({\n  name: \"hello\",\n  async execute(ctx) {\n    return { type: \"text\", text: \"Hi!\" };\n  }\n});"),
            ApiEntry("HTTP", "annie.http.request(request)", "Make a native HTTP request. The bridge returns status, headers, and response text.", "const result = await annie.http.request({\n  url: \"https://example.com\",\n  method: \"GET\"\n});"),
            ApiEntry("Browser", "annie.browser.open(spec)", "Create a browser request that can be returned as a native Annie browser message. Read or clear its shared session with session(id) and clear(id).", "return annie.messages.browser({\n  url: \"https://example.com\",\n  sessionId: \"catalog\"\n});"),
            ApiEntry("Storage", "annie.storage.get/set(key, value)", "Persistent storage isolated to this script project.", "await annie.storage.set(\"lastSearch\", query);\nconst saved = await annie.storage.get(\"lastSearch\");"),
            ApiEntry("Files", "annie.files.readText/writeText/list(path)", "Read and write files inside this script’s private data directory.", "const files = await annie.files.list(\"\");"),
            ApiEntry("Messages", "Return { type, ... }", "Return structured data. Annie renders native text, image, music, video, options, and progress messages.", "return { type: \"image\", uri, caption: \"Result\" };"),
            ApiEntry("Console", "annie.log.info/warn/error(...)", "Write diagnostics to the editor’s integrated Output panel.", "annie.log.info(\"Loaded results\", results.length);"),
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 15.dp, vertical = 12.dp)) {
        Text("API reference", color = StudioText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Search Annie’s script interfaces and copy examples.", color = StudioMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp, bottom = 11.dp))
        StudioInput(search, onSearch, "Search APIs", Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries.filter { search.isBlank() || listOf(it.category, it.signature, it.description, it.example).any { value -> value.contains(search, true) } }) { entry ->
                Surface(color = StudioSurface, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, StudioBorder), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(13.dp)) {
                        Text(entry.category.uppercase(), color = StudioBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(entry.signature, color = StudioText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 5.dp))
                        Text(entry.description, color = StudioMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                        Surface(color = Color(0xFF0A1420), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 9.dp)) {
                            Text(entry.example, color = Color(0xFFB8E9D6), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(10.dp))
                        }
                        Text("Copy example", color = StudioBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.End).clickable { clipboard.setText(AnnotatedString(entry.example)) }.padding(top = 9.dp, start = 8.dp, bottom = 2.dp))
                    }
                }
            }
        }
    }
}

private data class ApiEntry(val category: String, val signature: String, val description: String, val example: String)

@Composable
private fun EmptyStudioState(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = StudioText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(body, color = StudioMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
private fun StudioStatus(status: String, modifier: Modifier = Modifier) {
    Text(status, color = if (status.contains("fail", true) || status.contains("error", true)) StudioDanger else StudioMuted, fontSize = 11.sp, maxLines = 1, modifier = modifier)
}

@Composable
private fun ScriptCodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onEditorReady: (CodeEditor) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val registry = MonarchGrammarRegistry()
            registry.loadGrammars(
                monarchLanguages {
                    language("typescript") {
                        monarchLanguage = TypescriptLanguage
                        defaultScopeName()
                        languageConfiguration = "textmate/javascript/language-configuration.json"
                    }
                }
            )
            val grammar = registry.findGrammar("source.typescript")
                ?: registry.findGrammar("typescript")
                ?: error("Could not load JavaScript syntax grammar")
            val language = MonarchLanguage(grammar, registry.findLanguageConfiguration("source.typescript"), registry, true).apply {
                setCompleterKeywords(arrayOf("annie.commands", "annie.commands.register", "annie.http", "annie.http.request", "annie.browser", "annie.storage", "annie.storage.get", "annie.storage.set", "annie.sessions", "annie.actions", "annie.messages", "annie.files", "annie.log"))
            }
            CodeEditor(context).apply {
                setEditorLanguage(language)
                setColorScheme(SchemeDarcula())
                setTextSize(14f)
                setTabWidth(4)
                isLineNumberEnabled = true
                isWordwrap = false
                props.autoIndent = true
                props.symbolPairAutoCompletion = true
                setText(value.text)
                isEnabled = enabled
                subscribeAlways<ContentChangeEvent> {
                    val cursor = text.cursor
                    val start = text.getCharIndex(cursor.leftLine, cursor.leftColumn).coerceIn(0, text.length)
                    val end = text.getCharIndex(cursor.rightLine, cursor.rightColumn).coerceIn(start, text.length)
                    onValueChange(TextFieldValue(text.toString(), selection = TextRange(start, end)))
                }
                subscribeAlways<SelectionChangeEvent> {
                    val cursor = text.cursor
                    val start = text.getCharIndex(cursor.leftLine, cursor.leftColumn).coerceIn(0, text.length)
                    val end = text.getCharIndex(cursor.rightLine, cursor.rightColumn).coerceIn(start, text.length)
                    onValueChange(TextFieldValue(text.toString(), selection = TextRange(start, end)))
                }
                onEditorReady(this)
            }
        },
        update = { editor ->
            editor.isEnabled = enabled
            if (editor.text.toString() != value.text) {
                editor.setText(value.text)
                val safeOffset = value.selection.end.coerceIn(0, value.text.length)
                val prefix = value.text.substring(0, safeOffset)
                editor.setSelection(prefix.count { it == '\n' }, prefix.substringAfterLast('\n').length)
            }
        },
    )
}

@Composable
private fun StudioInput(value: String, onValueChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    Surface(color = StudioSurface, shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, StudioBorder), modifier = modifier.heightIn(min = 40.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = StudioText, fontSize = 13.sp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
            decorationBox = { inner -> Box { if (value.isBlank()) Text(hint, color = StudioMuted, fontSize = 12.sp); inner() } },
        )
    }
}

@Composable
private fun StudioAction(
    label: String,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        color = when { !enabled -> Color(0xFF15202D); emphasized -> Color(0xFF16446A); else -> StudioSurface2 },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (emphasized) StudioBlue.copy(alpha = .55f) else StudioBorder),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).then(if (contentDescription != null) Modifier.testTag("$contentDescription") else Modifier),
    ) {
        Text(label, color = if (!enabled) Color(0xFF65778B) else if (emphasized) StudioBlue else StudioText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp))
    }
}

@Composable
private fun StudioTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) Color(0xFF183553) else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.clickable(onClick = onClick).testTag("script_tab_${label.lowercase()}"),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(label, color = if (selected) StudioBlue else StudioMuted, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}
