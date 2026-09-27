Warning: truncated output (original token count: 19030)
Total output lines: 1328

package com.tomex777.annie

import android.content.Intent
import android.provider.OpenableColumns
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
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
                val file = workspace.files.importJavaScript(displayName, source)
                refreshProjects(file.nameWithoutExtension, file.name)
                status = "Imported ${file.name} · disabled until you review and enable it"
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
                            val isFolder = File(workspace.file…9030 tokens truncated… browser page context with its cookies and browser network stack. Read or clear its session with session(id) and clear(id).", "const browser = annie.browser.open({\n  url: \"https://example.com\",\n  sessionId: \"catalog\"\n});\nconst response = await annie.browser.fetch({\n  sessionId: browser.sessionId,\n  url: \"https://example.com/api\"\n});"),
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
            AnnieScriptCodeEditor(context).apply {
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

/**
 * Android keyboards may send Backspace through deleteSurroundingTextInCodePoints. Sora 0.24.6
 * leaves that method unsupported, which makes a selected document appear immune to Backspace on
 * some keyboards. Translate that operation to Sora's working UTF-16 input path while preserving
 * Unicode code point boundaries.
 */
private class AnnieScriptCodeEditor(context: android.content.Context) : CodeEditor(context) {
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(connection, false) {
            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength < 0 || afterLength < 0) return false
                if (beforeLength == 0 && afterLength == 0) return true

                if (isTextSelected()) {
                    deleteText()
                    notifyIMEExternalCursorChange()
                    return true
                }

                val content = text.toString()
                val cursor = text.getCharIndex(cursorLeftLine, cursorLeftColumn).coerceIn(0, content.length)
                val beforeCount = minOf(beforeLength, Character.codePointCount(content, 0, cursor))
                val afterCount = minOf(afterLength, Character.codePointCount(content, cursor, content.length))
                val start = Character.offsetByCodePoints(content, cursor, -beforeCount)
                val end = Character.offsetByCodePoints(content, cursor, afterCount)
                return super.deleteSurroundingText(cursor - start, end - cursor)
            }
        }
    }

    private val cursorLeftLine: Int get() = text.cursor.leftLine
    private val cursorLeftColumn: Int get() = text.cursor.leftColumn
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
