package com.tomex777.annie

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val StudioPanel = Color(0xFF0B1726)
private val StudioSurface = Color(0xFF102139)
private val StudioSurface2 = Color(0xFF142A43)
private val StudioBorder = Color(0xFF294562)
private val StudioText = Color(0xFFEEF5FF)
private val StudioMuted = Color(0xFF9CB2CC)
private val StudioBlue = Color(0xFF42B9F5)
private val StudioGreen = Color(0xFF54D6AE)
private val StudioDanger = Color(0xFFFF7586)

/**
 * Mobile-first local script workspace. This deliberately stays inside Annie rather than
 * pretending to be a desktop IDE: project/file navigation, editor, hot reload and logs.
 */
@Composable
internal fun ScriptStudioSheet(
    workspace: ScriptWorkspace,
    onCommandsReloaded: (List<ScriptCommand>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf(workspace.files.listProjects()) }
    var selectedProjectId by remember { mutableStateOf(projects.firstOrNull()?.id) }
    var selectedPath by remember {
        mutableStateOf(projects.firstOrNull()?.entryPath)
    }
    var editorValue by remember {
        val first = projects.firstOrNull()
        val initial = first?.entryPath?.let { first.files[it] }.orEmpty()
        mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
    }
    var savedSource by remember { mutableStateOf(editorValue.text) }
    var tab by remember { mutableStateOf("Files") }
    var status by remember { mutableStateOf("Ready") }
    var newProjectName by remember { mutableStateOf("") }
    var newFileName by remember { mutableStateOf("") }
    var renameDraft by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var codeEditor by remember { mutableStateOf<CodeEditor?>(null) }
    var logVersion by remember { mutableStateOf(0) }

    fun refreshProjects(preferredProject: String? = selectedProjectId, preferredPath: String? = selectedPath) {
        projects = workspace.files.listProjects()
        val project = projects.firstOrNull { it.id == preferredProject } ?: projects.firstOrNull()
        selectedProjectId = project?.id
        val path = preferredPath?.takeIf { it in project?.files.orEmpty() } ?: project?.entryPath
        selectedPath = path
        val source = path?.let { project?.files?.get(it) }.orEmpty()
        editorValue = TextFieldValue(source, selection = TextRange(source.length))
        savedSource = source
        renameDraft = project?.name.orEmpty()
    }

    fun selectFile(project: ScriptProject, path: String) {
        selectedProjectId = project.id
        selectedPath = path
        val source = project.files[path].orEmpty()
        editorValue = TextFieldValue(source, selection = TextRange(source.length))
        savedSource = source
        renameDraft = project.name
        status = "Opened $path"
    }

    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }
    val projectIsFolder = selectedProjectId?.let { File(workspace.files.root, it).isDirectory } == true
    val dirty = editorValue.text != savedSource
    val apiSuggestions = remember(editorValue.text, editorValue.selection) {
        val cursor = editorValue.selection.end.coerceIn(0, editorValue.text.length)
        val before = editorValue.text.substring(0, cursor)
        if (before.substringAfterLast('\n').endsWith("annie.")) {
            listOf("commands", "http", "storage", "messages", "actions", "player", "downloads", "browser", "files", "image", "crypto", "notifications", "tasks", "log")
        } else emptyList()
    }

    Column(
        Modifier.fillMaxWidth().heightIn(min = 620.dp).background(StudioPanel)
            .padding(horizontal = 14.dp, vertical = 8.dp).testTag("script_studio"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Scripts", color = StudioText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    selectedProject?.let { "${it.name} · ${selectedPath ?: it.entryPath}" } ?: "Create a JavaScript project",
                    color = StudioMuted,
                    fontSize = 12.sp,
                )
            }
            StudioAction(if (dirty) "Save*" else "Save", enabled = selectedProject != null && selectedPath != null) {
                val projectId = selectedProjectId ?: return@StudioAction
                val path = selectedPath ?: return@StudioAction
                scope.launch {
                    runCatching {
                        workspace.files.writeFile(projectId, path, editorValue.text)
                        workspace.reload()
                    }.onSuccess { commands ->
                        savedSource = editorValue.text
                        onCommandsReloaded(commands)
                        status = "Saved + reloaded"
                        logVersion++
                        refreshProjects(projectId, path)
                    }.onFailure { error ->
                        status = error.message ?: "Save failed"
                    }
                }
            }
            StudioAction("Run/Test", enabled = selectedProject != null) {
                val project = selectedProject ?: return@StudioAction
                val path = selectedPath ?: return@StudioAction
                scope.launch {
                    runCatching {
                        workspace.files.writeFile(project.id, path, editorValue.text)
                        val commands = workspace.reload()
                        onCommandsReloaded(commands)
                        val command = commands.firstOrNull { it.scriptId == project.id }
                            ?: error("This script did not register a command")
                        workspace.execute(command.name, "/${command.name} test", "script-editor", System.nanoTime())
                    }.onSuccess { result ->
                        val response = runCatching { org.json.JSONObject(result.orEmpty()) }.getOrNull()
                        status = response?.optString("text")?.takeIf(String::isNotBlank)
                            ?: response?.optString("type")?.let { "Test returned $it" }
                            ?: "Test completed"
                        logVersion++
                        tab = "Console"
                    }.onFailure { status = it.message ?: "Test failed"; logVersion++ }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            listOf("Files", "Console", "Info").forEach { item ->
                StudioTab(item, selected = tab == item) {
                    tab = item
                    if (item == "Console") logVersion++
                }
            }
        }

        when (tab) {
            "Files" -> {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    projects.forEach { project ->
                        StudioProjectChip(project.name, project.id == selectedProjectId) {
                            selectFile(project, project.entryPath)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StudioInput(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        hint = "project name",
                        modifier = Modifier.weight(1f),
                    )
                    StudioAction("+ JS") {
                        runCatching { workspace.files.createScript(newProjectName) }
                            .onSuccess { file ->
                                newProjectName = ""
                                refreshProjects(file.nameWithoutExtension, file.name)
                                status = "Created ${file.name}"
                            }
                            .onFailure { status = it.message ?: "Create failed" }
                    }
                    StudioAction("+ Folder") {
                        runCatching { workspace.files.createFolder(newProjectName) }
                            .onSuccess { folder ->
                                newProjectName = ""
                                refreshProjects(folder.name, "main.js")
                                status = "Created ${folder.name}/"
                            }
                            .onFailure { status = it.message ?: "Create failed" }
                    }
                }

                if (selectedProject != null) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        selectedProject.files.keys.sorted().forEach { path ->
                            StudioProjectChip(path, path == selectedPath) { selectFile(selectedProject, path) }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StudioInput(
                            value = renameDraft,
                            onValueChange = { renameDraft = it },
                            hint = "rename project",
                            modifier = Modifier.weight(1f),
                        )
                        StudioAction("Rename") {
                            val id = selectedProjectId ?: return@StudioAction
                            runCatching { workspace.files.renameProject(id, renameDraft) }
                                .onSuccess { newId ->
                                    refreshProjects(newId, if (projectIsFolder) selectedPath else "$newId.js")
                                    status = "Project renamed"
                                }
                                .onFailure { status = it.message ?: "Rename failed" }
                        }
                        StudioAction("Delete", danger = true) {
                            val id = selectedProjectId ?: return@StudioAction
                            runCatching { workspace.files.deleteProject(id) }
                                .onSuccess {
                                    refreshProjects(null, null)
                                    scope.launch {
                                        onCommandsReloaded(workspace.reload())
                                        logVersion++
                                    }
                                    status = "Project deleted"
                                }
                                .onFailure { status = it.message ?: "Delete failed" }
                        }
                    }

                    if (projectIsFolder) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            StudioInput(
                                value = newFileName,
                                onValueChange = { newFileName = it },
                                hint = "helper.js or lib/api.js",
                                modifier = Modifier.weight(1f),
                            )
                            StudioAction("+ File") {
                                val id = selectedProjectId ?: return@StudioAction
                                runCatching { workspace.files.createFile(id, newFileName) }
                                    .onSuccess { file ->
                                        val relative = file.relativeTo(File(workspace.files.root, id)).invariantSeparatorsPath
                                        newFileName = ""
                                        refreshProjects(id, relative)
                                        status = "Created $relative"
                                    }
                                    .onFailure { status = it.message ?: "Create file failed" }
                            }
                            if (selectedPath != null && selectedPath != "main.js") {
                                StudioAction("Delete file", danger = true) {
                                    val id = selectedProjectId ?: return@StudioAction
                                    val path = selectedPath ?: return@StudioAction
                                    runCatching { workspace.files.deleteFile(id, path) }
                                        .onSuccess {
                                            refreshProjects(id, "main.js")
                                            status = "Deleted $path"
                                        }
                                        .onFailure { status = it.message ?: "Delete file failed" }
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    StudioInput(search, { search = it }, "Find in file", Modifier.weight(1f))
                    StudioInput(replacement, { replacement = it }, "Replace", Modifier.weight(1f))
                    StudioAction("Find", enabled = search.isNotBlank()) {
                        codeEditor?.searcher?.search(search, EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true))
                    }
                    StudioAction("Replace all", enabled = search.isNotBlank()) {
                        codeEditor?.searcher?.search(search, EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true))
                        codeEditor?.searcher?.replaceAll(replacement)
                    }
                    val matches = remember(search, editorValue.text) {
                        if (search.isBlank()) 0 else Regex(Regex.escape(search), RegexOption.IGNORE_CASE).findAll(editorValue.text).count()
                    }
                    Text(if (search.isBlank()) "" else "$matches match${if (matches == 1) "" else "es"}", color = StudioMuted, fontSize = 11.sp)
                    StudioAction("Undo", enabled = codeEditor?.text?.canUndo() == true) { codeEditor?.undo() }
                    StudioAction("Redo", enabled = codeEditor?.text?.canRedo() == true) { codeEditor?.redo() }
                }

                if (apiSuggestions.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        apiSuggestions.forEach { suggestion ->
                            StudioProjectChip("annie.$suggestion", false) {
                                val cursor = editorValue.selection.end.coerceIn(0, editorValue.text.length)
                                val next = editorValue.text.substring(0, cursor) + suggestion + editorValue.text.substring(cursor)
                                editorValue = TextFieldValue(next, selection = TextRange(cursor + suggestion.length))
                            }
                        }
                    }
                }

                ScriptCodeEditor(
                    value = editorValue,
                    onValueChange = { editorValue = it },
                    onEditorReady = { codeEditor = it },
                    enabled = selectedProject != null,
                )
            }

            "Console" -> {
                val logs = remember(logVersion) { workspace.logs().takeLast(250).reversed() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    StudioAction("Refresh") { logVersion++ }
                }
                if (logs.isEmpty()) {
                    Text("No script logs yet.", color = StudioMuted, modifier = Modifier.padding(vertical = 24.dp))
                } else {
                    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
                    LazyColumn(Modifier.fillMaxWidth().height(430.dp).testTag("script_console"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(logs) { row ->
                            val tone = when (row.level) {
                                "ERROR" -> StudioDanger
                                "WARN" -> Color(0xFFFFC86A)
                                else -> StudioText
                            }
                            Text(
                                "${formatter.format(Date(row.atMillis))} [${row.scriptId}] ${row.level}  ${row.message}",
                                color = tone,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            else -> {
                Column(
                    Modifier.fillMaxWidth().height(430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Annie JavaScript API", color = StudioText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Save reloads the local QuickJS projects immediately. Script commands feed Annie’s slash suggestions without rebuilding the APK.",
                        color = StudioMuted,
                        fontSize = 13.sp,
                    )
                    listOf(
                        "annie.commands.register(definition)" to "Register a slash command.",
                        "annie.http.request(request)" to "Use Annie’s native HTTP bridge.",
                        "annie.storage.get/set(key, value)" to "Persistent storage isolated to the script.",
                        "annie.log.info/warn/error(...)" to "Write redacted diagnostics to Console.",
                        "Return { type: \"text\", text: \"...\" }" to "Native text message.",
                        "Return { type: \"image\", uri, caption }" to "Native image message + fullscreen viewer.",
                        "Return { type: \"music\", title, artist, artwork, streamUrl, lyrics }" to "Rich in-chat music player.",
                        "Return { type: \"video\", title, thumbnail, uri }" to "Compact video message that opens Annie’s full player.",
                        "Return { type: \"options\", options: [...] }" to "Native selectable options.",
                        "Return { type: \"progress\", text, progress }" to "Native progress message.",
                    ).forEach { (api, description) ->
                        Surface(color = StudioSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(api, color = StudioBlue, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                Text(description, color = StudioMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }

        Text(status, color = if (status.contains("fail", true) || status.contains("error", true)) StudioDanger else StudioMuted, fontSize = 11.sp)
    }
}

@Composable
private fun ScriptCodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onEditorReady: (CodeEditor) -> Unit,
    enabled: Boolean,
) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(360.dp).testTag("script_editor"),
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
            val language = MonarchLanguage(
                grammar,
                registry.findLanguageConfiguration("source.typescript"),
                registry,
                true,
            ).apply {
                setCompleterKeywords(
                    arrayOf(
                        "annie.commands", "annie.commands.register", "annie.http", "annie.http.request",
                        "annie.storage", "annie.storage.get", "annie.storage.set", "annie.sessions",
                        "annie.sessions.register", "annie.actions", "annie.actions.register", "annie.messages",
                        "annie.player", "annie.downloads", "annie.browser", "annie.files", "annie.image",
                        "annie.crypto", "annie.notifications", "annie.tasks", "annie.log",
                    )
                )
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
                    val offset = text.getCharIndex(cursor.leftLine, cursor.leftColumn).coerceIn(0, text.length)
                    onValueChange(TextFieldValue(text.toString(), selection = TextRange(offset)))
                }
                subscribeAlways<SelectionChangeEvent> {
                    val cursor = text.cursor
                    val offset = text.getCharIndex(cursor.leftLine, cursor.leftColumn).coerceIn(0, text.length)
                    onValueChange(TextFieldValue(text.toString(), selection = TextRange(offset)))
                }
                onEditorReady(this)
            }
        },
        update = { editor ->
            editor.isEnabled = enabled
            if (editor.text.toString() != value.text) editor.setText(value.text)
        },
    )
}

@Composable
private fun StudioInput(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Surface(color = StudioSurface, shape = RoundedCornerShape(11.dp), border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder), modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = StudioText, fontSize = 12.sp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) Text(hint, color = StudioMuted, fontSize = 12.sp)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun StudioAction(
    label: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        color = if (!enabled) Color(0xFF15202D) else if (danger) Color(0xFF3A1D29) else StudioSurface2,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (danger) Color(0xFF6A3342) else StudioBorder),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            label,
            color = if (!enabled) Color(0xFF65778B) else if (danger) StudioDanger else StudioText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun StudioTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Color(0xFF183553) else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = if (selected) StudioBlue else StudioMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun StudioProjectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Color(0xFF183553) else StudioSurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) StudioBlue else StudioBorder),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = if (selected) StudioText else StudioMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}
