package com.tomex777.annie

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import io.github.rosemoe.sora.langs.monarch.MonarchColorScheme
import io.github.rosemoe.sora.langs.monarch.registry.MonarchGrammarRegistry
import io.github.rosemoe.sora.langs.monarch.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.monarch.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.monarch.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.monarch.registry.model.ThemeSource
import io.github.rosemoe.sora.langs.monarch.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.langs.monarch.registry.dsl.monarchLanguages
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
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

private val monarchThemeLock = Any()
@Volatile private var annieMonarchThemeReady = false

private fun ensureAnnieMonarchTheme(context: android.content.Context): ThemeModel = synchronized(monarchThemeLock) {
    if (!annieMonarchThemeReady) {
        FileProviderRegistry.addProvider(AssetsFileResolver(context.applicationContext.assets))
        val model = ThemeModel(ThemeSource("textmate/annie-dark.json", "annie-dark")).apply {
            isDark = true
        }
        ThemeRegistry.loadTheme(model, false)
        annieMonarchThemeReady = true
    }
    check(ThemeRegistry.setTheme("annie-dark")) { "Could not load Annie Script Studio theme" }
    ThemeRegistry.currentTheme
}

private enum class StudioPage(val title: String) { FILES("Files"), EDITOR("Editor"), ENV("ENV"), API("API") }
private enum class FileAction { RENAME, SHARE, EXPORT, DELETE, ENABLE, DISABLE }
private enum class StudioGlyph { SAVE, CLOSE, ASSIST, RUN, FIND, UNDO, REDO, REFRESH, EXPAND, COLLAPSE }

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
    val scriptAssistant = remember { ScriptAssistant() }
    var assistOpen by remember { mutableStateOf(false) }
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
    var saving by remember { mutableStateOf(false) }
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
    var pendingSpecExport by remember { mutableStateOf<String?>(null) }
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
        if (saving) return
        val project = projects.firstOrNull { it.id == selectedProjectId } ?: run {
            status = "Choose a script first"
            return
        }
        val path = selectedPath ?: return
        val source = editorValue.text
        saving = true
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
                .also { saving = false }
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

    val exportSpec = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val source = pendingSpecExport
        pendingSpecExport = null
        if (uri != null && source != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(source) }
                        ?: error("Could not write the Annie scripting spec")
                }
            }.onSuccess { status = "Exported ${AnnieScriptSpec.FILE_NAME}" }
                .onFailure { status = it.message ?: "Spec export failed" }
        }
    }

    fun exportFile(name: String, source: String) {
        pendingExport = name to source
        exportScript.launch(name)
    }

    fun exportAiSpec() {
        val project = projects.firstOrNull { it.id == selectedProjectId }
        pendingSpecExport = AnnieScriptSpec.build(
            project = project,
            selectedPath = selectedPath,
            selectedSource = if (project != null && selectedPath != null) editorValue.text else null,
        )
        exportSpec.launch(AnnieScriptSpec.FILE_NAME)
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
            StudioAction(
                label = when { saving -> "Saving…"; dirty -> "Save"; else -> "Saved" },
                emphasized = dirty || saving,
                icon = StudioGlyph.SAVE,
                onClick = { saveScript() },
                enabled = !saving && dirty && selectedProject != null && selectedPath != null,
            )
            Spacer(Modifier.width(8.dp))
            StudioIconAction(StudioGlyph.CLOSE, "Close Script Studio", onClick = onClose)
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
                        StudioAction("AI spec", icon = StudioGlyph.ASSIST, onClick = { exportAiSpec() }, enabled = !saving)
                        Spacer(Modifier.width(7.dp))
                        StudioAction("Run", emphasized = true, icon = StudioGlyph.RUN, onClick = { saveScript(runAfterSave = true) }, enabled = !saving)
                    }
                    val matches = remember(query, editorValue.text) {
                        if (query.isBlank()) 0 else Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(editorValue.text).count()
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StudioInput(query, { query = it }, "Find in file", Modifier.weight(1f))
                        StudioAction("Find", icon = StudioGlyph.FIND, enabled = query.isNotBlank(), onClick = {
                            codeEditor?.searcher?.search(query, EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true))
                        })
                        Text(if (query.isBlank()) "" else "$matches", color = StudioMuted, fontSize = 11.sp)
                        StudioIconAction(StudioGlyph.UNDO, "Undo", enabled = codeEditor?.text?.canUndo() == true, onClick = { codeEditor?.undo() })
                        StudioIconAction(StudioGlyph.REDO, "Redo", enabled = codeEditor?.text?.canRedo() == true, onClick = { codeEditor?.redo() })
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

            StudioPage.ENV -> {
                val project = selectedProject
                if (project == null) {
                    EmptyStudioState("Choose a script", "Select a script project to view its ENV configuration.")
                } else {
                    ScriptEnvScreen(
                        workspace = workspace,
                        scriptId = project.id,
                        definition = workspace.envDefinition(project.id),
                        onStatus = { status = it },
                        onAction = { action ->
                            scope.launch {
                                val result = workspace.executeAction(
                                    scriptId = project.id,
                                    actionId = action,
                                    payloadJson = "{}",
                                    chatId = "script-env",
                                    messageId = System.nanoTime(),
                                )
                                status = result?.resultJson?.let { raw ->
                                    runCatching { org.json.JSONObject(raw).optString("text") }.getOrNull()
                                }?.takeIf(String::isNotBlank) ?: "ENV action completed"
                                logVersion++
                            }
                        },
                    )
                    StudioStatus(status, Modifier.padding(horizontal = 16.dp, vertical = 7.dp))
                }
            }

            StudioPage.API -> ApiReferenceScreen(apiSearch, { apiSearch = it }, onExportSpec = ::exportAiSpec)
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
        if (assistOpen && selectedProject != null && selectedPath != null) {
            ScriptAssistDialog(
                fileName = selectedPath.orEmpty(),
                currentSource = editorValue.text,
                assistant = scriptAssistant,
                onDismiss = { assistOpen = false },
                onApply = { proposed ->
                    editorValue = TextFieldValue(proposed, selection = TextRange(proposed.length))
                    status = "Assist proposal applied · review then Save"
                    assistOpen = false
                },
                onInsert = { proposed ->
                    val fragment = scriptAssistInsertedFragment(editorValue.text, proposed)
                    if (fragment.isBlank()) {
                        status = "Assist proposal has no insertable changes"
                    } else {
                        val start = minOf(editorValue.selection.start, editorValue.selection.end).coerceIn(0, editorValue.text.length)
                        val end = maxOf(editorValue.selection.start, editorValue.selection.end).coerceIn(start, editorValue.text.length)
                        val next = editorValue.text.replaceRange(start, end, fragment)
                        val caret = start + fragment.length
                        editorValue = TextFieldValue(next, selection = TextRange(caret))
                        status = "Assist change inserted · review then Save"
                    }
                    assistOpen = false
                },
                onCreateNew = { proposed ->
                    assistOpen = false
                    val owner = selectedProject?.takeIf { File(workspace.files.root, it.id).isDirectory }
                    askForText("New file from Assist", "generated.js") { rawName ->
                        runCatching {
                            val name = rawName.trim().let { if (it.endsWith(".js", true)) it else "$it.js" }
                            val file = if (owner == null) workspace.files.createScript(name)
                                else workspace.files.createFile(owner.id, name)
                            val id = if (owner == null) file.nameWithoutExtension else owner.id
                            val path = if (owner == null) file.name
                                else file.relativeTo(File(workspace.files.root, id)).invariantSeparatorsPath
                            workspace.files.writeFile(id, path, proposed)
                            id to path
                        }.onSuccess { (id, path) ->
                            refreshProjects(id, path)
                            page = StudioPage.EDITOR
                            status = "Created $path from Assist"
                            scope.launch { onCommandsReloaded(workspace.reload()) }
                        }.onFailure { status = it.message ?: "Could not create assisted file" }
                    }
                },
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
            StudioIconAction(StudioGlyph.REFRESH, "Refresh output", compact = true, onClick = onRefresh)
            StudioIconAction(if (collapsed) StudioGlyph.EXPAND else StudioGlyph.COLLAPSE, if (collapsed) "Expand output" else "Collapse output", compact = true, onClick = onToggle)
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
private fun ScriptEnvScreen(
    workspace: ScriptWorkspace,
    scriptId: String,
    definition: ScriptEnvDefinition?,
    onStatus: (String) -> Unit,
    onAction: (String) -> Unit,
) {
    if (definition == null) {
        EmptyStudioState(
            "No ENV declared",
            "Add annie.env.define({...}) to this enabled script, then Save/Run to generate its native configuration.",
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(definition.title, color = StudioText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (definition.description.isNotBlank()) {
            Text(definition.description, color = StudioMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Text(
            "Persistent configuration for this script. Secrets are never shown after saving.",
            color = StudioMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 10.dp),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(definition.fields, key = { it.key }) { field ->
                ScriptEnvFieldCard(workspace, scriptId, field, onStatus, onAction)
            }
        }
    }
}

@Composable
private fun ScriptEnvFieldCard(
    workspace: ScriptWorkspace,
    scriptId: String,
    field: ScriptEnvField,
    onStatus: (String) -> Unit,
    onAction: (String) -> Unit,
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth().testTag("script_env_${field.key}"),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(field.label, color = StudioText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (field.description.isNotBlank()) {
                        Text(field.description, color = StudioMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Text(field.type.wireName.uppercase(), color = StudioBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            when (field.type) {
                ScriptEnvFieldType.SWITCH -> {
                    var checked by remember(scriptId, field.key) {
                        mutableStateOf(workspace.envValue(scriptId, field.key) as? Boolean ?: false)
                    }
                    Switch(
                        checked = checked,
                        onCheckedChange = {
                            checked = it
                            runCatching { workspace.setEnvValue(scriptId, field.key, it) }
                                .onSuccess { onStatus("Saved ${field.label}") }
                                .onFailure { error -> onStatus(error.message ?: "ENV update failed") }
                        },
                        modifier = Modifier.testTag("script_env_switch_${field.key}"),
                    )
                }

                ScriptEnvFieldType.TEXT -> {
                    var value by remember(scriptId, field.key) {
                        mutableStateOf(workspace.envValue(scriptId, field.key)?.toString().orEmpty())
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            value = it
                            runCatching { workspace.setEnvValue(scriptId, field.key, it) }
                                .onFailure { error -> onStatus(error.message ?: "ENV update failed") }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(field.label) },
                    )
                }

                ScriptEnvFieldType.SECRET -> {
                    var value by remember(scriptId, field.key) { mutableStateOf("") }
                    var configured by remember(scriptId, field.key) { mutableStateOf(workspace.envHasSecret(scriptId, field.key)) }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth().testTag("script_env_secret_${field.key}"),
                        singleLine = true,
                        label = { Text(if (configured && value.isBlank()) "${field.label} · configured" else field.label) },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioAction(
                            "Save",
                            emphasized = value.isNotBlank(),
                            enabled = value.isNotBlank(),
                            onClick = {
                                runCatching { workspace.setEnvValue(scriptId, field.key, value) }
                                    .onSuccess {
                                        configured = true
                                        value = ""
                                        onStatus("Saved ${field.label}")
                                    }
                                    .onFailure { error -> onStatus(error.message ?: "Secret ENV update failed") }
                            },
                        )
                        if (configured) {
                            StudioAction(
                                "Clear",
                                onClick = {
                                    runCatching { workspace.setEnvValue(scriptId, field.key, "") }
                                        .onSuccess {
                                            configured = false
                                            value = ""
                                            onStatus("Cleared ${field.label}")
                                        }
                                        .onFailure { error -> onStatus(error.message ?: "Secret ENV update failed") }
                                },
                            )
                        }
                    }
                    if (configured) Text("Stored securely. Annie never reveals the saved value in Script Studio.", color = StudioMuted, fontSize = 10.sp)
                }

                ScriptEnvFieldType.NUMBER -> {
                    var value by remember(scriptId, field.key) {
                        mutableStateOf(workspace.envValue(scriptId, field.key)?.toString().orEmpty())
                    }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { next ->
                            value = next
                            next.toDoubleOrNull()?.let { number ->
                                runCatching { workspace.setEnvValue(scriptId, field.key, number) }
                                    .onFailure { error -> onStatus(error.message ?: "ENV update failed") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(field.label) },
                    )
                }

                ScriptEnvFieldType.SELECT -> {
                    var selected by remember(scriptId, field.key) {
                        mutableStateOf(workspace.envValue(scriptId, field.key)?.toString().orEmpty())
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        field.options.forEach { option ->
                            Surface(
                                color = if (option == selected) Color(0xFF16446A) else StudioSurface2,
                                shape = RoundedCornerShape(9.dp),
                                border = BorderStroke(1.dp, if (option == selected) StudioBlue else StudioBorder),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selected = option
                                    runCatching { workspace.setEnvValue(scriptId, field.key, option) }
                                        .onSuccess { onStatus("Saved ${field.label}") }
                                        .onFailure { error -> onStatus(error.message ?: "ENV update failed") }
                                },
                            ) {
                                Text(option, color = StudioText, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                            }
                        }
                    }
                }

                ScriptEnvFieldType.MULTI_SELECT -> {
                    val initial = remember(scriptId, field.key) {
                        val raw = workspace.envValue(scriptId, field.key) as? org.json.JSONArray
                        buildSet {
                            if (raw != null) for (index in 0 until raw.length()) raw.optString(index).takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                    var selected by remember(scriptId, field.key) { mutableStateOf(initial) }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        field.options.forEach { option ->
                            val active = option in selected
                            Surface(
                                color = if (active) Color(0xFF16446A) else StudioSurface2,
                                shape = RoundedCornerShape(9.dp),
                                border = BorderStroke(1.dp, if (active) StudioBlue else StudioBorder),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selected = if (active) selected - option else selected + option
                                    runCatching {
                                        workspace.setEnvValue(scriptId, field.key, org.json.JSONArray(selected.toList()))
                                    }.onSuccess { onStatus("Saved ${field.label}") }
                                        .onFailure { error -> onStatus(error.message ?: "ENV update failed") }
                                },
                            ) {
                                Text((if (active) "✓ " else "") + option, color = StudioText, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                            }
                        }
                    }
                }

                ScriptEnvFieldType.SLIDER -> {
                    val min = (field.min ?: 0.0).toFloat()
                    val max = (field.max ?: 1.0).toFloat().coerceAtLeast(min + 0.0001f)
                    var value by remember(scriptId, field.key) {
                        mutableStateOf((workspace.envValue(scriptId, field.key) as? Number)?.toFloat()?.coerceIn(min, max) ?: min)
                    }
                    Text("%.2f".format(value), color = StudioMuted, fontSize = 11.sp)
                    Slider(
                        value = value,
                        onValueChange = {
                            value = it
                            runCatching { workspace.setEnvValue(scriptId, field.key, it.toDouble()) }
                                .onFailure { error -> onStatus(error.message ?: "ENV update failed") }
                        },
                        valueRange = min..max,
                    )
                }

                ScriptEnvFieldType.ACTION -> {
                    StudioAction(
                        label = field.label,
                        emphasized = true,
                        onClick = {
                            val action = field.action
                            if (action.isNullOrBlank()) onStatus("ENV action ${field.key} has no action handler")
                            else onAction(action)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiReferenceScreen(search: String, onSearch: (String) -> Unit, onExportSpec: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val entries = remember {
        listOf(
            ApiEntry("Commands", "annie.commands.register({...})", "Register a slash command with a name, description, and async execute(ctx) handler.", "annie.commands.register({\n  name: \"hello\",\n  async execute(ctx) {\n    return { type: \"text\", text: \"Hi!\" };\n  }\n});"),
            ApiEntry("HTTP", "annie.http.request(request)", "Make a native HTTP request. The bridge returns status, headers, and response text.", "const result = await annie.http.request({\n  url: \"https://example.com\",\n  method: \"GET\"\n});"),
            ApiEntry("Browser", "annie.browser.open/fetch(spec)", "Open a native browser message, then run fetch in that live browser page context with its cookies and browser network stack. Read or clear its session with session(id) and clear(id).", "const browser = annie.browser.open({\n  url: \"https://example.com\",\n  sessionId: \"catalog\"\n});\nconst response = await annie.browser.fetch({\n  sessionId: browser.sessionId,\n  url: \"https://example.com/api\"\n});"),
            ApiEntry("Storage", "annie.storage.get/set(key, value)", "Persistent storage isolated to this script project.", "await annie.storage.set(\"lastSearch\", query);\nconst saved = await annie.storage.get(\"lastSearch\");"),
            ApiEntry("Files", "annie.files.readText/writeText/list(path)", "Read and write files inside this script’s private data directory.", "const files = await annie.files.list(\"\");"),
            ApiEntry("ENV", "annie.env.define/get/set/secret/values", "Declare persistent per-script user configuration. Secret fields use Keystore-backed encrypted storage and are excluded from values().", "annie.env.define({ fields: [{ key: \"enabled\", type: \"switch\", label: \"Enabled\", default: true }] });"),
            ApiEntry("Messages", "Return { type, ... }", "Return structured data. Annie renders registered first-party native message types.", "return { type: \"image\", uri, caption: \"Result\" };"),
            ApiEntry("Forms", "annie.messages.form({...})", "Temporary native conversation input. Submit routes the collected values back to the owning JavaScript action; use ENV for persistent configuration.", "return annie.messages.form({ title: \"Options\", fields: [{ id: \"quality\", type: \"select\", label: \"Quality\", options: [\"720p\", \"1080p\"] }], submit: { label: \"Continue\", action: \"submit-options\" } });"),
            ApiEntry("Schedules", "annie.schedule.create/list/cancel/enable/disable", "Persist deferrable background actions without keeping QuickJS alive. Recurring jobs use WorkManager and must be at least 15 minutes apart.", "await annie.schedule.create({ id: \"daily-check\", every: \"day\", at: \"19:00\", action: \"check\", payload: {} });"),
            ApiEntry("Tasks", "annie.tasks.start/list/cancel/retry", "Durable one-shot WorkManager actions with persisted lifecycle state. QuickJS is recreated for the action and closed after result delivery.", "await annie.tasks.start({ id: \"job\", title: \"Processing…\", action: \"run-job\", payload: {} });"),
            ApiEntry("Console", "annie.log.info/warn/error(...)", "Write diagnostics to the editor’s integrated Output panel.", "annie.log.info(\"Loaded results\", results.length);"),
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 15.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("API reference", color = StudioText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Search Annie’s script interfaces and export a portable AI-ready spec.", color = StudioMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
            }
            StudioAction("Export AI spec", emphasized = true, onClick = onExportSpec)
        }
        StudioInput(search, onSearch, "Search APIs", Modifier.fillMaxWidth().padding(top = 11.dp))
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
                setCompleterKeywords(
                    arrayOf(
                        "annie.commands", "annie.commands.register",
                        "annie.actions", "annie.sessions",
                        "annie.http", "annie.http.request", "annie.browser",
                        "annie.storage", "annie.storage.get", "annie.storage.set",
                        "annie.env", "annie.env.define", "annie.env.get", "annie.env.set", "annie.env.secret", "annie.env.values",
                        "annie.schedule", "annie.schedule.create", "annie.schedule.list", "annie.schedule.cancel", "annie.schedule.enable", "annie.schedule.disable",
                        "annie.tasks", "annie.tasks.start", "annie.tasks.list", "annie.tasks.cancel", "annie.tasks.retry",
                        "annie.messages", "annie.messages.form", "annie.files", "annie.log",
                    )
                )
            }
            CodeEditor(context).apply {
                // Monarch emits dynamic foreground ids after async tokenization. A regular
                // EditorColorScheme (including SchemeDarcula) does not resolve those ids,
                // which can make the code turn transparent while the caret still works.
                // Keep the language and color scheme paired so document, spans and paint
                // always describe the same visible editor state.
                val annieTheme = ensureAnnieMonarchTheme(context)
                setColorScheme(MonarchColorScheme.create(annieTheme))
                setEditorLanguage(language)
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
    icon: StudioGlyph? = null,
    onClick: () -> Unit,
) {
    val semanticModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }.testTag(contentDescription)
    } else Modifier
    Surface(
        color = when { !enabled -> Color(0xFF15202D); emphasized -> Color(0xFF16446A); else -> StudioSurface2 },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (emphasized) StudioBlue.copy(alpha = .55f) else StudioBorder),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).then(semanticModifier),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                StudioGlyphCanvas(icon, if (!enabled) Color(0xFF65778B) else if (emphasized) StudioBlue else StudioText)
            }
            Text(
                label,
                color = if (!enabled) Color(0xFF65778B) else if (emphasized) StudioBlue else StudioText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StudioIconAction(
    icon: StudioGlyph,
    contentDescription: String,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        color = if (compact) Color.Transparent else StudioSurface2,
        shape = RoundedCornerShape(10.dp),
        border = if (compact) null else BorderStroke(1.dp, StudioBorder),
        modifier = Modifier
            .size(if (compact) 34.dp else 40.dp)
            .semantics { this.contentDescription = contentDescription }
            .testTag(contentDescription)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            StudioGlyphCanvas(icon, if (enabled) StudioText else Color(0xFF65778B))
        }
    }
}

@Composable
private fun StudioGlyphCanvas(icon: StudioGlyph, color: Color) {
    Canvas(Modifier.size(19.dp)) {
        val stroke = 1.9.dp.toPx()
        val left = size.width * .18f
        val right = size.width * .82f
        val top = size.height * .18f
        val bottom = size.height * .82f
        val cx = size.width / 2f
        val cy = size.height / 2f

        when (icon) {
            StudioGlyph.SAVE -> {
                drawRoundRect(color, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(right - left, bottom - top), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()), style = Stroke(stroke))
                drawLine(color, Offset(size.width * .34f, top), Offset(size.width * .66f, top), stroke)
                drawLine(color, Offset(size.width * .34f, top), Offset(size.width * .34f, size.height * .40f), stroke)
                drawLine(color, Offset(size.width * .66f, top), Offset(size.width * .66f, size.height * .40f), stroke)
                drawRoundRect(color, topLeft = Offset(size.width * .33f, size.height * .56f), size = androidx.compose.ui.geometry.Size(size.width * .34f, size.height * .20f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()), style = Stroke(stroke))
            }
            StudioGlyph.CLOSE -> {
                drawLine(color, Offset(left, top), Offset(right, bottom), stroke)
                drawLine(color, Offset(right, top), Offset(left, bottom), stroke)
            }
            StudioGlyph.RUN -> {
                val p = Path().apply {
                    moveTo(size.width * .31f, size.height * .20f)
                    lineTo(size.width * .79f, cy)
                    lineTo(size.width * .31f, size.height * .80f)
                    close()
                }
                drawPath(p, color)
            }
            StudioGlyph.FIND -> {
                drawCircle(color, radius = size.width * .25f, center = Offset(size.width * .43f, size.height * .42f), style = Stroke(stroke))
                drawLine(color, Offset(size.width * .62f, size.height * .62f), Offset(size.width * .82f, size.height * .82f), stroke)
            }
            StudioGlyph.UNDO, StudioGlyph.REDO -> {
                val reverse = icon == StudioGlyph.REDO
                val startX = if (reverse) size.width * .75f else size.width * .25f
                val endX = if (reverse) size.width * .25f else size.width * .75f
                drawArc(color, startAngle = if (reverse) 205f else 155f, sweepAngle = if (reverse) -220f else 220f, useCenter = false, topLeft = Offset(size.width * .22f, size.height * .27f), size = androidx.compose.ui.geometry.Size(size.width * .56f, size.height * .48f), style = Stroke(stroke))
                drawLine(color, Offset(startX, size.height * .30f), Offset(startX, size.height * .58f), stroke)
                drawLine(color, Offset(startX, size.height * .30f), Offset(endX, size.height * .34f), stroke)
            }
            StudioGlyph.ASSIST -> {
                drawLine(color, Offset(cx, top), Offset(cx, bottom), stroke)
                drawLine(color, Offset(left, cy), Offset(right, cy), stroke)
                drawLine(color, Offset(size.width * .29f, size.height * .29f), Offset(size.width * .71f, size.height * .71f), stroke)
                drawLine(color, Offset(size.width * .71f, size.height * .29f), Offset(size.width * .29f, size.height * .71f), stroke)
            }
            StudioGlyph.REFRESH -> {
                drawArc(color, 35f, 285f, false, Offset(size.width * .18f, size.height * .18f), androidx.compose.ui.geometry.Size(size.width * .64f, size.height * .64f), style = Stroke(stroke))
                drawLine(color, Offset(size.width * .75f, size.height * .19f), Offset(size.width * .82f, size.height * .39f), stroke)
                drawLine(color, Offset(size.width * .75f, size.height * .19f), Offset(size.width * .57f, size.height * .25f), stroke)
            }
            StudioGlyph.EXPAND, StudioGlyph.COLLAPSE -> {
                val y1 = if (icon == StudioGlyph.EXPAND) size.height * .58f else size.height * .42f
                val y2 = if (icon == StudioGlyph.EXPAND) size.height * .38f else size.height * .62f
                drawLine(color, Offset(size.width * .28f, y1), Offset(cx, y2), stroke)
                drawLine(color, Offset(cx, y2), Offset(size.width * .72f, y1), stroke)
            }
        }
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
