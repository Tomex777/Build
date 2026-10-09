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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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
private enum class FileAction { RENAME, SHARE, EXPORT, DELETE, ENABLE, DISABLE, PERMISSIONS }
private enum class StudioGlyph { SAVE, CLOSE, ASSIST, RUN, FIND, REPLACE, UNDO, REDO, REFRESH, EXPAND, COLLAPSE, FOLDER, MORE, ADD, CHECK }

/** Full-screen, mobile-first local script workspace. The script runtime remains in ScriptWorkspace. */
@Composable
internal fun ScriptStudioSheet(
    workspace: ScriptWorkspace,
    onCommandsReloaded: (List<ScriptCommand>) -> Unit,
    onClose: () -> Unit = {},
    initialProjectId: String? = null,
    openEnvironment: Boolean = false,
    openPackageImport: Boolean = false,
    onPackageImportOpened: () -> Unit = {},
    importFile: File? = null,
    onFileImportOpened: () -> Unit = {},
) {
    // Use the host window insets: the full-screen dialog can report no gesture
    // navigation inset on Android 16 while still drawing behind its handle.
    val systemBars = WindowInsets.systemBars.asPaddingValues()
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(StudioPanel).padding(systemBars).consumeWindowInsets(systemBars)) {
        ScriptStudioContent(
            workspace = workspace,
            onCommandsReloaded = onCommandsReloaded,
            onClose = onClose,
            initialProjectId = initialProjectId,
            openEnvironment = openEnvironment,
            openPackageImport = openPackageImport,
            onPackageImportOpened = onPackageImportOpened,
            importFile = importFile,
            onFileImportOpened = onFileImportOpened,
        )
        }
    }
}

@Composable
private fun ScriptStudioContent(
    workspace: ScriptWorkspace,
    onCommandsReloaded: (List<ScriptCommand>) -> Unit,
    onClose: () -> Unit,
    initialProjectId: String?,
    openEnvironment: Boolean,
    openPackageImport: Boolean,
    onPackageImportOpened: () -> Unit,
    importFile: File?,
    onFileImportOpened: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scriptAssistant = remember { ScriptAssistant() }
    var assistOpen by remember { mutableStateOf(false) }
    val initialProjects = remember(workspace, initialProjectId) { workspace.files.listProjects() }
    val initialProject = remember(initialProjects, initialProjectId) {
        initialProjects.firstOrNull { it.id == initialProjectId } ?: initialProjects.firstOrNull()
    }
    var projects by remember(initialProjectId) { mutableStateOf(initialProjects) }
    var selectedProjectId by remember(initialProjectId) { mutableStateOf(initialProject?.id) }
    var selectedPath by remember(initialProjectId) { mutableStateOf(initialProject?.entryPath) }
    var currentDirectory by remember(initialProjectId) { mutableStateOf("") }
    var editorValue by remember(initialProjectId) {
        val initial = initialProject?.entryPath?.let { initialProject.files[it] }.orEmpty()
        mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
    }
    var savedSource by remember(initialProjectId) { mutableStateOf(editorValue.text) }
    var page by remember(initialProjectId, openEnvironment) {
        mutableStateOf(if (openEnvironment) StudioPage.ENV else StudioPage.FILES)
    }
    var status by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var previewResult by remember { mutableStateOf<String?>(null) }
    var previewOpen by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogValue by remember { mutableStateOf("") }
    var dialogAction by remember { mutableStateOf<(String) -> Unit>({}) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var replaceOpen by remember { mutableStateOf(false) }
    var codeEditor by remember { mutableStateOf<CodeEditor?>(null) }
    var logVersion by remember { mutableStateOf(0) }
    var consoleHeight by remember { mutableStateOf(166.dp) }
    var consoleCollapsed by remember { mutableStateOf(true) }
    var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingSpecExport by remember { mutableStateOf<String?>(null) }
    var apiSearch by remember { mutableStateOf("") }
    var pendingPackageArchive by remember { mutableStateOf<File?>(null) }
    var pendingPackageName by remember { mutableStateOf("") }
    var packageArchivePreview by remember { mutableStateOf<AnniePackageArchivePreview?>(null) }
    var permissionsProject by remember { mutableStateOf<ScriptProject?>(null) }
    var selectedPackageEntry by remember { mutableStateOf("") }

    fun flushPendingEdits() {
        val id = selectedProjectId ?: return
        val path = selectedPath ?: return
        if (editorValue.text == savedSource) return
        workspace.files.writeFile(id, path, editorValue.text)
        savedSource = editorValue.text
    }

    // Persist source quietly, never reloading the JS engine just because someone types.
    LaunchedEffect(selectedProjectId, selectedPath, editorValue.text, saving) {
        val id = selectedProjectId ?: return@LaunchedEffect
        val path = selectedPath ?: return@LaunchedEffect
        val source = editorValue.text
        if (saving || source == savedSource) return@LaunchedEffect
        delay(650)
        runCatching {
            withContext(Dispatchers.IO) { workspace.files.writeFile(id, path, source) }
        }.onSuccess {
            if (selectedProjectId == id && selectedPath == path && editorValue.text == source) {
                savedSource = source
                status = "Saved automatically"
            }
        }.onFailure { error -> status = "Autosave failed: " + (error.message ?: "unknown error") }
    }

    // A user may exit before the debounce interval. Keep the final edit durable.
    val latestDraft = rememberUpdatedState(
        Triple(selectedProjectId to selectedPath, editorValue.text, savedSource)
    )
    DisposableEffect(workspace) {
        onDispose {
            val (selected, text, stored) = latestDraft.value
            val (id, path) = selected
            if (id != null && path != null && text != stored) {
                runCatching { workspace.files.writeFile(id, path, text) }
            }
        }
    }

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
        runCatching { flushPendingEdits() }.onFailure { status = "Could not save previous file" }
        selectedProjectId = project.id
        selectedPath = path
        currentDirectory = path.substringBeforeLast('/', "")
        val source = project.files[path].orEmpty()
        editorValue = TextFieldValue(source, selection = TextRange(source.length))
        savedSource = source
        status = ""
        if (openEditor) page = StudioPage.EDITOR
    }

    fun saveScript(runAfterSave: Boolean = false, openPreview: Boolean = false) {
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
                if (openPreview && result != null) {
                    previewResult = result
                    previewOpen = true
                }
                status = if (runAfterSave) {
                    val response = runCatching { org.json.JSONObject(result.orEmpty()) }.getOrNull()
                    response?.optString("text")?.takeIf(String::isNotBlank)
                        ?: response?.optString("type")?.let { "Test returned $it" }
                        ?: "Test completed"
                } else ""
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
    val importPackageArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            var cached: File? = null
            runCatching {
                cached = File(context.cacheDir, "annie-package-import-${UUID.randomUUID()}.zip")
                val suggestedName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?.removeSuffix(".zip")
                    .orEmpty()
                withContext(Dispatchers.IO) {
                    val target = requireNotNull(cached)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var copied = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                copied += read
                                require(copied <= 32L * 1024 * 1024) { "ZIP archive is too large" }
                                output.write(buffer, 0, read)
                            }
                        }
                    } ?: error("Could not read the selected ZIP package")
                }
                val preview = withContext(Dispatchers.IO) {
                    AnniePackageArchive.inspect(requireNotNull(cached), suggestedName)
                }
                pendingPackageArchive = cached
                pendingPackageName = suggestedName
                packageArchivePreview = preview
                selectedPackageEntry = preview.manifest.entryPoint.ifBlank { preview.entryCandidates.firstOrNull().orEmpty() }
                status = "Package inspected · nothing has been run"
            }.onFailure {
                cached?.delete()
                status = it.message ?: "Package inspection failed"
            }
        }
    }
    LaunchedEffect(importFile) {
        val file = importFile ?: return@LaunchedEffect
        runCatching {
            if (file.extension.equals("js", true)) {
                val staged = withContext(Dispatchers.IO) {
                    require(file.length() <= 2L * 1024 * 1024) { "Script is too large" }
                    workspace.files.importJavaScript(file.name, file.readText())
                }
                refreshProjects(staged.nameWithoutExtension, staged.name)
                status = "Imported · review and enable to run"
            } else {
                val cached = withContext(Dispatchers.IO) {
                    require(file.length() <= 32L * 1024 * 1024) { "Package is too large" }
                    file.copyTo(File(context.cacheDir, "annie-package-import-${UUID.randomUUID()}.zip"))
                }
                try {
                    val preview = withContext(Dispatchers.IO) { AnniePackageArchive.inspect(cached, file.nameWithoutExtension) }
                    pendingPackageArchive = cached
                    pendingPackageName = file.nameWithoutExtension
                    packageArchivePreview = preview
                    selectedPackageEntry = preview.manifest.entryPoint.ifBlank { preview.entryCandidates.firstOrNull().orEmpty() }
                    status = "Review package"
                } catch (failure: Throwable) { cached.delete(); throw failure }
            }
        }.onFailure { status = "Unable to import this file. Check its package structure." }
        onFileImportOpened()
    }
    LaunchedEffect(openPackageImport) {
        if (openPackageImport) {
            onPackageImportOpened()
            importPackageArchive.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
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
        Modifier.fillMaxSize().background(StudioPanel).imePadding().testTag("script_studio"),
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
                label = when { saving -> "Preparing…"; dirty -> "Preview •"; else -> "Preview" },
                emphasized = !saving,
                icon = StudioGlyph.RUN,
                onClick = {
                    if (selectedPath?.endsWith(".js", ignoreCase = true) == true) {
                        saveScript(runAfterSave = true, openPreview = true)
                    } else {
                        status = "Preview isn't supported for this file type yet. Changes are saved automatically."
                    }
                },
                enabled = !saving && selectedProject != null && selectedPath != null,
            )
            Spacer(Modifier.width(8.dp))
            StudioIconAction(StudioGlyph.CLOSE, "Close Script Studio", onClick = onClose)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(StudioSurface, RoundedCornerShape(13.dp)).padding(4.dp)) {
            StudioPage.entries.forEach { destination ->
                StudioTab(destination.title, page == destination, Modifier.weight(1f)) {
                    if (page != destination) {
                        codeEditor?.clearFocus()
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        page = destination
                    }
                }
            }
        }

        when (page) {
            StudioPage.FILES -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioAction("New File", emphasized = true, icon = StudioGlyph.ADD, onClick = {
                        val owner = selectedProject?.takeIf { File(workspace.files.root, it.id).isDirectory }
                        askForText(if (owner == null) "New script" else "New file", "") { rawName ->
                            runCatching {
                                val name = rawName.trim().let { raw ->
                                    if (raw.substringAfterLast('/').contains('.')) raw else "$raw.js"
                                }
                                if (owner != null) {
                                    workspace.files.createFile(owner.id, listOf(currentDirectory.trim('/'), name).filter(String::isNotBlank).joinToString("/"))
                                } else if (name.endsWith(".js", ignoreCase = true)) {
                                    workspace.files.createScript(name)
                                } else {
                                    // Non-JS documents always belong to a project directory.
                                    val projectName = name.substringBeforeLast('.')
                                    val folder = workspace.files.createFolder(projectName)
                                    workspace.files.createFile(folder.name, name)
                                }
                            }.onSuccess { file ->
                                val id = if (owner == null) file.nameWithoutExtension else owner!!.id
                                val path = if (owner == null) file.name else file.relativeTo(File(workspace.files.root, id)).invariantSeparatorsPath
                                refreshProjects(id, path)
                                currentDirectory = path.substringBeforeLast('/', "")
                                status = "Created ${file.name}"
                                page = StudioPage.EDITOR
                            }.onFailure { status = it.message ?: "Could not create file" }
                        }
                    })
                    StudioAction("Folder", icon = StudioGlyph.FOLDER, onClick = {
                        val owner = selectedProject?.takeIf { File(workspace.files.root, it.id).isDirectory }
                        askForText("New folder") { name ->
                            runCatching {
                                if (owner == null) workspace.files.createFolder(name)
                                else workspace.files.createFolder(owner.id, listOf(currentDirectory.trim('/'), name.trim('/')).filter(String::isNotBlank).joinToString("/"))
                            }.onSuccess { folder ->
                                val id = if (owner == null) folder.name else owner!!.id
                                val path = if (owner == null) "main.js" else folder.relativeTo(File(workspace.files.root, id)).invariantSeparatorsPath + "/main.js"
                                refreshProjects(id, path)
                                currentDirectory = path.substringBeforeLast('/', "")
                                status = "Created \${folder.name}"
                            }.onFailure { status = it.message ?: "Could not create folder" }
                        }
                    })
                    StudioAction("Import", onClick = {
                        importScript.launch(arrayOf("application/javascript", "text/javascript", "application/x-javascript", "text/plain"))
                    })
                    StudioAction("ZIP", onClick = {
                        importPackageArchive.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
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
                                        onClick = { if (isFolder) { runCatching { flushPendingEdits() }; selectedProjectId = project.id; selectedPath = project.entryPath; currentDirectory = "" } else selectFile(project, project.entryPath) },
                                        actions = listOf(
                                            FileAction.RENAME,
                                            FileAction.SHARE,
                                            FileAction.EXPORT,
                                            if (project.enabled) FileAction.DISABLE else FileAction.ENABLE,
                                            FileAction.DELETE,
                                        ) + if (project.hasPackageManifest) listOf(FileAction.PERMISSIONS) else emptyList(),
                                        onAction = { action ->
                                            when (action) {
                                                FileAction.RENAME -> askForText("Rename ${if (isFolder) "project" else "script"}", project.name) { next ->
                                                    runCatching { workspace.files.renameProject(project.id, next) }
                                                        .onSuccess { id -> refreshProjects(id, if (isFolder) "main.js" else "$id.js"); status = "Renamed to $id" }
                                                        .onFailure { status = it.message ?: "Rename failed" }
                                                }
                                                FileAction.SHARE -> shareFile(project.entryPath, project.files[project.entryPath].orEmpty())
                                                FileAction.EXPORT -> exportFile(project.entryPath, project.files[project.entryPath].orEmpty())
                                                FileAction.PERMISSIONS -> permissionsProject = project
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
                            if (isFolder && selectedProjectId == project.id) {
                                val normalizedDirectory = currentDirectory.trim('/').replace('\\', '/')
                                val prefix = if (normalizedDirectory.isBlank()) "" else "$normalizedDirectory/"
                                if (normalizedDirectory.isNotBlank()) {
                                    item(key = "dir-back:" + project.id + ":" + normalizedDirectory) {
                                        ScriptFileRow(
                                            name = "..",
                                            subtitle = "Back to " + normalizedDirectory.substringBeforeLast('/', ""),
                                            isFolder = true,
                                            isEntry = false,
                                            selected = false,
                                            indent = normalizedDirectory.contains('/'),
                                            onClick = { currentDirectory = normalizedDirectory.substringBeforeLast('/', "") },
                                            actions = emptyList(),
                                            onAction = {},
                                        )
                                    }
                                }
                                val childDirectories = linkedSetOf<String>()
                                val childFiles = mutableListOf<String>()
                                project.files.keys.sorted().forEach { rawPath ->
                                    val path = rawPath.replace('\\', '/')
                                    if (!path.startsWith(prefix) || path == prefix) return@forEach
                                    val remainder = path.removePrefix(prefix)
                                    val slash = remainder.indexOf('/')
                                    if (slash >= 0) {
                                        childDirectories += remainder.substring(0, slash)
                                    } else {
                                        childFiles += path
                                    }
                                }
                                childDirectories.filter { it.contains(query, true) }.forEach { child ->
                                    val childPath = if (normalizedDirectory.isBlank()) child else "$normalizedDirectory/$child"
                                    item(key = "dir:" + project.id + ":" + childPath) {
                                        ScriptFileRow(
                                            name = child,
                                            subtitle = project.name + " / " + childPath,
                                            isFolder = true,
                                            isEntry = false,
                                            selected = false,
                                            indent = normalizedDirectory.isNotBlank(),
                                            onClick = { currentDirectory = childPath },
                                            actions = emptyList(),
                                            onAction = {},
                                        )
                                    }
                                }
                                childFiles.filter { it.contains(query, true) }.forEach { path ->
                                    item(key = "file:" + project.id + ":" + path) {
                                        ScriptFileRow(
                                            name = path.substringAfterLast('/'),
                                            subtitle = project.name + " / " + path,
                                            isFolder = false,
                                            isEntry = path == project.entryPath,
                                            selected = selectedProjectId == project.id && selectedPath == path,
                                            indent = normalizedDirectory.isNotBlank(),
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
                                                            .onSuccess { moved ->
                                                                refreshProjects(project.id, moved)
                                                                currentDirectory = moved.substringBeforeLast('/', "")
                                                                status = "Moved to $moved"
                                                            }
                                                            .onFailure { status = it.message ?: "Rename failed" }
                                                    }
                                                    FileAction.SHARE -> shareFile(path.substringAfterLast('/'), project.files[path].orEmpty())
                                                    FileAction.EXPORT -> exportFile(path.substringAfterLast('/'), project.files[path].orEmpty())
                                                    FileAction.DELETE -> runCatching { workspace.files.deleteFile(project.id, path) }
                                                        .onSuccess {
                                                            refreshProjects(project.id, project.entryPath)
                                                            currentDirectory = normalizedDirectory
                                                            status = "Deleted $path"
                                                        }
                                                        .onFailure { status = it.message ?: "Delete failed" }
                                                    else -> Unit
                                                }
                                            },
                                        )
                                    }
                                }
                            }                        }
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
                        }
                        StudioAction("AI spec", icon = StudioGlyph.ASSIST, onClick = { exportAiSpec() }, enabled = !saving)
                        Spacer(Modifier.width(7.dp))
                        StudioAction("Run", emphasized = true, icon = StudioGlyph.RUN, onClick = { saveScript(runAfterSave = true) }, enabled = !saving && path.endsWith(".js", ignoreCase = true))
                    }
                    val matches = remember(query, editorValue.text) {
                        if (query.isBlank()) 0 else Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(editorValue.text).count()
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StudioInput(query, { query = it }, "Find in file", Modifier.weight(1f), tag = "script_find_query")
                        StudioAction("Find", icon = StudioGlyph.FIND, enabled = query.isNotBlank(), onClick = {
                            codeEditor?.searcher?.search(query, EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true))
                        })
                        Text(if (query.isBlank()) "" else "$matches", color = StudioMuted, fontSize = 11.sp)
                        StudioIconAction(StudioGlyph.REPLACE, "Find and replace", onClick = { replaceOpen = !replaceOpen })
                        StudioIconAction(StudioGlyph.UNDO, "Undo", enabled = codeEditor?.text?.canUndo() == true, onClick = { codeEditor?.undo() })
                        StudioIconAction(StudioGlyph.REDO, "Redo", enabled = codeEditor?.text?.canRedo() == true, onClick = { codeEditor?.redo() })
                    }
                    if (replaceOpen) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            StudioInput(replacement, { replacement = it }, "Replace with", Modifier.weight(1f), tag = "script_replace_text")
                            StudioAction("Replace", enabled = matches > 0, contentDescription = "Replace next match", onClick = {
                                codeEditor?.let { editor ->
                                    val source = editor.text.toString()
                                    val pattern = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
                                    val next = pattern.find(source, editor.text.cursor.left.coerceIn(0, source.length)) ?: pattern.find(source)
                                    next?.let { editor.text.replace(it.range.first, it.range.last + 1, replacement) }
                                }
                            })
                            StudioAction("All", enabled = matches > 0, contentDescription = "Replace all matches", onClick = {
                                codeEditor?.let { editor ->
                                    val pattern = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
                                    val source = editor.text.toString()
                                    // Content.replace retains the editor undo history. Treat
                                    // replacement literally, including dollar signs/backslashes.
                                    val replaced = pattern.replace(source) { replacement }
                                    editor.text.replace(0, source.length, replaced)
                                }
                            })
                        }
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
                        // Keep the 12dp handle, 48dp actions and 8dp header padding visible.
                        height = if (consoleCollapsed) 68.dp else consoleHeight,
                        collapsed = consoleCollapsed,
                        onRefresh = { logVersion++ },
                        onToggle = { consoleCollapsed = !consoleCollapsed },
                        onDrag = { delta ->
                            val nextHeight = (consoleHeight - delta.dp).coerceIn(100.dp, 340.dp)
                            consoleHeight = nextHeight
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
        permissionsProject?.let { project ->
            var grants by remember(project.id) { mutableStateOf(workspace.files.grantedPermissions(project.id)) }
            AlertDialog(
                onDismissRequest = { permissionsProject = null },
                containerColor = StudioSurface,
                titleContentColor = StudioText,
                textContentColor = StudioMuted,
                title = { Text("Package permissions") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${project.manifest.displayName} can use only permissions you grant here.", color = StudioMuted)
                        if (project.manifest.permissions.isEmpty()) {
                            Text("This package declares no permissions.", color = StudioMuted)
                        } else {
                            project.manifest.permissions.sorted().forEach { permission ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(extensionPermissionLabel(permission), color = StudioText, modifier = Modifier.weight(1f))
                                    Switch(checked = permission in grants, onCheckedChange = { allowed ->
                                        grants = if (allowed) grants + permission else grants - permission
                                    })
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        permissionsProject = null
                        scope.launch {
                            runCatching { workspace.setGrantedPermissions(project.id, grants) }
                                .onSuccess { status = "Package permissions saved" }
                                .onFailure { status = it.message ?: "Could not save package permissions" }
                        }
                    }) { Text("Save", color = StudioBlue) }
                },
                dismissButton = { TextButton(onClick = { permissionsProject = null }) { Text("Cancel", color = StudioMuted) } },
            )
        }
        packageArchivePreview?.let { preview ->
            var entryMenuExpanded by remember(preview) { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = {
                    pendingPackageArchive?.delete()
                    pendingPackageArchive = null
                    pendingPackageName = ""
                    packageArchivePreview = null
                },
                containerColor = StudioSurface,
                titleContentColor = StudioText,
                textContentColor = StudioMuted,
                title = { Text("Import project") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(preview.manifest.displayName, color = StudioText, fontWeight = FontWeight.SemiBold)
                        Text("ID  ${preview.manifest.packageId}", color = StudioMuted, fontSize = 11.sp)
                        Text("JavaScript  ${preview.javaScriptFiles.size}", color = StudioMuted)
                        Text("Images  ${preview.imageFiles.size}", color = StudioMuted)
                        Text("Audio  ${preview.audioFiles.size}", color = StudioMuted)
                        Text("Other files  ${preview.otherFiles.size}", color = StudioMuted)
                        if (preview.manifest.permissions.isEmpty()) {
                            Text("Permissions  None declared", color = StudioMuted)
                        } else {
                            Text("Permissions", color = StudioText, fontWeight = FontWeight.SemiBold)
                            Text(preview.manifest.permissions.sorted().joinToString(", ") { extensionPermissionLabel(it) }, color = StudioMuted, fontSize = 12.sp)
                        }
                        if (preview.entryCandidates.size > 1) {
                            Text("Choose the entry point", color = StudioText, fontWeight = FontWeight.SemiBold)
                            Box {
                                TextButton(onClick = { entryMenuExpanded = true }) {
                                    Text(selectedPackageEntry.ifBlank { "Select JavaScript file" }, color = StudioBlue)
                                }
                                DropdownMenu(expanded = entryMenuExpanded, onDismissRequest = { entryMenuExpanded = false }) {
                                    preview.entryCandidates.forEach { candidate ->
                                        DropdownMenuItem(
                                            text = { Text(candidate) },
                                            onClick = { selectedPackageEntry = candidate; entryMenuExpanded = false },
                                        )
                                    }
                                }
                            }
                        } else {
                            Text("Entry point  ${preview.manifest.entryPoint.ifBlank { selectedPackageEntry }}", color = StudioMuted)
                        }
                        Text("Imported packages stay disabled until you enable them.", color = StudioMuted, fontSize = 11.sp)
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = selectedPackageEntry in preview.javaScriptFiles,
                        onClick = {
                            val archive = pendingPackageArchive ?: return@TextButton
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        AnniePackageArchive.install(context, archive, selectedPackageEntry, pendingPackageName)
                                    }
                                }.onSuccess { imported ->
                                    archive.delete()
                                    pendingPackageArchive = null
                                    pendingPackageName = ""
                                    packageArchivePreview = null
                                    refreshProjects(imported.id, imported.entryPath)
                                    status = "Imported ${imported.name} · disabled until you review and enable it"
                                }.onFailure { status = it.message ?: "Package import failed" }
                            }
                        },
                    ) { Text("Import", color = StudioBlue) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingPackageArchive?.delete()
                        pendingPackageArchive = null
                        pendingPackageName = ""
                        packageArchivePreview = null
                    }) { Text("Cancel", color = StudioMuted) }
                },
            )
        }
        if (previewOpen) {
            Dialog(
                onDismissRequest = { previewOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                Column(
                    Modifier.fillMaxSize().background(StudioPanel).statusBarsPadding().navigationBarsPadding()
                        .testTag("script_message_preview")
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Message Preview", color = StudioText, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { previewOpen = false }) { Text("Back to editor") }
                    }
                    Column(Modifier.fillMaxWidth().weight(1f).padding(16.dp)) {
                        val result = previewResult
                        if (result.isNullOrBlank()) {
                            Text("The command returned no message.", color = StudioMuted)
                        } else {
                            ChatBubble(
                                entry = ChatEntry(
                                    id = 0L,
                                    fromUser = false,
                                    text = "",
                                    scriptMessageJson = result,
                                    scriptId = selectedProjectId
                                ),
                                onCatalogClick = {},
                                onActionClick = { _, _ -> },
                                onOpenSource = {},
                                onSeriesAction = { _, _, _ -> },
                                onScriptAction = { _, _, done -> done(null) },
                                onScriptInlineAction = { _, _, done -> done(null) },
                            )
                        }
                    }
                    Text(
                        "Rendered with Annie's chat components. Script actions are disabled in preview.",
                        color = StudioMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
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
            if (isFolder) {
                Icon(
                    studioGlyphVector(StudioGlyph.FOLDER),
                    contentDescription = null,
                    tint = StudioBlue,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text("JS", color = StudioGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(name, color = StudioText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (isEntry) EntryBadge()
                }
                Text(subtitle, color = StudioMuted, fontSize = 11.sp, maxLines = 1)
            }
            Box {
                Icon(
                    studioGlyphVector(StudioGlyph.MORE),
                    contentDescription = "File actions",
                    tint = StudioMuted,
                    modifier = Modifier.size(36.dp).clickable { expanded = true }.padding(8.dp),
                )
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
    FileAction.PERMISSIONS -> "Permissions"
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
    Column(Modifier.fillMaxWidth().height(height).background(Color(0xFF0D1B2A)).testTag("script_console_panel")) {
        Box(
            Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> onDrag(amount / density) }
                )
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
                Text("No output yet", color = StudioMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp))
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
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    if (active) {
                                        Icon(
                                            studioGlyphVector(StudioGlyph.CHECK),
                                            contentDescription = null,
                                            tint = StudioGreen,
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                    Text(option, color = StudioText, fontSize = 11.sp)
                                }
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
            ApiEntry("Match results", "annie.messages.matches(value)", "Render source-labeled native results with optional relevance scores; selection routes back to the owning script action.", "return annie.messages.matches({\n  title: \"Choose a source\",\n  items: [{ id: \"train\", title: \"Evening Train\", sourceName: \"North catalog\", relevance: 0.92, action: \"details\" }]\n});"),
            ApiEntry("Package sources", "manifest.sources[]", "Register the media categories and slash command provided by this catalog source. The command must also be declared in commands and registered in JavaScript.", "{\n  \"commands\": [{ \"name\": \"search\", \"description\": \"Search catalog\" }],\n  \"sources\": [{ \"id\": \"catalog\", \"name\": \"North catalog\", \"mediaTypes\": [\"anime\", \"movie\"], \"command\": \"search\" }]\n}"),
            ApiEntry("HTTP", "annie.http.request(request)", "Imported packages need the network capability, network.access permission, and a user grant. Direct requests do not inherit WebView cookies.", "const result = await annie.http.request({\n  url: \"https://example.com\",\n  method: \"GET\"\n});"),
            ApiEntry("Browser", "annie.browser.open/fetch(spec)", "Open a native browser message, then run fetch in that live browser page context with its cookies and browser network stack. Read or clear its session with session(id) and clear(id).", "const browser = annie.browser.open({\n  url: \"https://example.com\",\n  sessionId: \"catalog\"\n});\nconst response = await annie.browser.fetch({\n  sessionId: browser.sessionId,\n  url: \"https://example.com/api\"\n});"),
            ApiEntry("Storage", "annie.storage.get/set(key, value)", "Persistent storage isolated to this script project.", "await annie.storage.set(\"lastSearch\", query);\nconst saved = await annie.storage.get(\"lastSearch\");"),
            ApiEntry("Assets", "annie.assets.image/audio/text/json(id)", "Read declared package-local assets by logical ID. Image/audio APIs return Annie-owned package URIs; they do not expose Android filesystem paths.", "const board = annie.assets.image(\"board\");\nconst rules = annie.assets.json(\"rules\");\nreturn annie.messages.image({ uri: board, caption: rules.title });"),
            ApiEntry("Files", "annie.files.readText/writeText/list(path)", "Read and write files inside this script’s private data directory.", "const files = await annie.files.list(\"\");"),
            ApiEntry("ENV", "annie.env.define/get/set/secret/values", "Declare persistent per-script user configuration. Secret fields use Keystore-backed encrypted storage and are excluded from values().", "annie.env.define({ fields: [{ key: \"enabled\", type: \"switch\", label: \"Enabled\", default: true }] });"),
            ApiEntry("Android notifications", "annie.android.notifications.post/update/cancel", "Requires the android.notifications capability and declared permission. Annie requests runtime consent when required; keys are package-owned and rate-limited. Update and cancel require android.notifications.manage.", "await annie.android.notifications.post({ key: \"status\", title: \"Annie\", text: \"Task complete\" });\nawait annie.android.notifications.update({ key: \"status\", title: \"Annie\", text: \"Updated\" });\nawait annie.android.notifications.cancel(\"status\");"),
            ApiEntry("Messages", "Return { type, ... }", "Return structured data. Annie renders registered first-party native message types.", "return { type: \"image\", uri, caption: \"Result\" };"),
            ApiEntry("Media messages", "annie.messages.seasonList/episodeList/continueWatching", "Render native media navigation without extension-owned UI. Episode qualities become selectors only when multiple choices are supplied.", "return annie.messages.episodeList({ title: \"Season 1\", episodes: [{ id: \"e1\", title: \"Episode 1\", qualities: [\"720p\", \"1080p\"], playAction: \"play\", downloadAction: \"download\" }] });"),
            ApiEntry("Music + lyrics", "annie.messages.music({...})", "Render the compact in-chat player. Plain lyrics remain static; standard LRC timestamps enable line highlighting and seek-on-tap.", "return annie.messages.music({ title: \"Track\", artist: \"Artist\", streamUrl, lyrics: \"[00:12.50]First line\\n[00:18.00]Second line\" });"),
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
        StudioInput(search, onSearch, "Search APIs", Modifier.fillMaxWidth().padding(top = 11.dp), tag = "script_api_search")
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
    if (status.isBlank()) return
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
                onTextSync = {
                    val cursor = text.cursor
                    val start = text.getCharIndex(cursor.leftLine, cursor.leftColumn).coerceIn(0, text.length)
                    val end = text.getCharIndex(cursor.rightLine, cursor.rightColumn).coerceIn(start, text.length)
                    onValueChange(TextFieldValue(text.toString(), selection = TextRange(start, end)))
                }
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
    internal var onTextSync: (() -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(connection, false) {
            private fun deleteSelectedText(): Boolean {
                if (!isTextSelected()) return false
                deleteText()
                notifyIMEExternalCursorChange()
                onTextSync?.invoke()
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength < 0 || afterLength < 0) return false
                if (beforeLength == 0 && afterLength == 0) return true
                if (deleteSelectedText()) return true

                val before = text.toString()
                val handled = super.deleteSurroundingText(beforeLength, afterLength)
                if (handled && before != text.toString()) onTextSync?.invoke()
                return handled
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength < 0 || afterLength < 0) return false
                if (beforeLength == 0 && afterLength == 0) return true
                if (deleteSelectedText()) return true

                val content = text.toString()
                val cursor = text.getCharIndex(cursorLeftLine, cursorLeftColumn).coerceIn(0, content.length)
                val beforeCount = minOf(beforeLength, Character.codePointCount(content, 0, cursor))
                val afterCount = minOf(afterLength, Character.codePointCount(content, cursor, content.length))
                val start = Character.offsetByCodePoints(content, cursor, -beforeCount)
                val end = Character.offsetByCodePoints(content, cursor, afterCount)
                val handled = super.deleteSurroundingText(cursor - start, end - cursor)
                if (handled && content != text.toString()) onTextSync?.invoke()
                return handled
            }
        }
    }

    private val cursorLeftLine: Int get() = text.cursor.leftLine
    private val cursorLeftColumn: Int get() = text.cursor.leftColumn
}

@Composable
private fun StudioInput(value: String, onValueChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, tag: String? = null) {
    Surface(color = StudioSurface, shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, StudioBorder), modifier = modifier.heightIn(min = 40.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = StudioText, fontSize = 13.sp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp).then(if (tag == null) Modifier else Modifier.testTag(tag)),
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
                Icon(studioGlyphVector(icon), contentDescription = null, tint = if (!enabled) Color(0xFF65778B) else if (emphasized) StudioBlue else StudioText, modifier = Modifier.size(18.dp))
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
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription }
            .testTag(contentDescription)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(studioGlyphVector(icon), contentDescription = contentDescription, tint = if (enabled) StudioText else Color(0xFF65778B), modifier = Modifier.size(20.dp))
        }
    }
}

private fun studioGlyphVector(icon: StudioGlyph): ImageVector = ImageVector.Builder(
    name = "Studio${icon.name}", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.White)) {
        when (icon) {
            StudioGlyph.UNDO -> {
                moveTo(12.5f, 8f); curveTo(9.85f, 8f, 7.45f, 8.99f, 5.6f, 10.6f)
                lineTo(2f, 7f); verticalLineTo(16f); horizontalLineTo(11f); lineTo(7.38f, 12.38f)
                curveTo(8.72f, 11.2f, 10.5f, 10.5f, 12.5f, 10.5f)
                curveTo(15.54f, 10.5f, 18.14f, 12.22f, 19.43f, 14.74f)
                lineTo(21.56f, 14.04f); curveTo(20.18f, 10.3f, 16.55f, 8f, 12.5f, 8f); close()
            }
            StudioGlyph.REDO -> {
                moveTo(11.5f, 8f); curveTo(14.15f, 8f, 16.55f, 8.99f, 18.4f, 10.6f)
                lineTo(22f, 7f); verticalLineTo(16f); horizontalLineTo(13f); lineTo(16.62f, 12.38f)
                curveTo(15.28f, 11.2f, 13.5f, 10.5f, 11.5f, 10.5f)
                curveTo(8.46f, 10.5f, 5.86f, 12.22f, 4.57f, 14.74f)
                lineTo(2.44f, 14.04f); curveTo(3.82f, 10.3f, 7.45f, 8f, 11.5f, 8f); close()
            }
            StudioGlyph.REFRESH -> {
                moveTo(17.65f, 6.35f); curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
                curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f); horizontalLineTo(1f)
                lineTo(5f, 16f); lineTo(9f, 12f); horizontalLineTo(6.01f)
                curveTo(6.01f, 8.69f, 8.69f, 6f, 12f, 6f); curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
                lineTo(17.65f, 6.35f); close(); moveTo(19.99f, 12f)
                curveTo(19.99f, 15.31f, 17.31f, 18f, 14f, 18f); curveTo(12.34f, 18f, 10.86f, 17.31f, 9.78f, 16.22f)
                lineTo(8.35f, 17.65f); curveTo(9.8f, 19.1f, 11.79f, 20f, 14f, 20f)
                curveTo(18.42f, 20f, 21.99f, 16.42f, 21.99f, 12f); horizontalLineTo(19.99f); close()
            }
            StudioGlyph.SAVE -> {
                moveTo(17f, 3f); horizontalLineTo(5f); curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
                verticalLineTo(19f); curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f); horizontalLineTo(19f)
                curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f); verticalLineTo(7f); lineTo(17f, 3f); close()
                moveTo(12f, 19f); curveTo(10.34f, 19f, 9f, 17.66f, 9f, 16f); curveTo(9f, 14.34f, 10.34f, 13f, 12f, 13f)
                curveTo(13.66f, 13f, 15f, 14.34f, 15f, 16f); curveTo(15f, 17.66f, 13.66f, 19f, 12f, 19f); close()
                moveTo(15f, 9f); horizontalLineTo(5f); verticalLineTo(5f); horizontalLineTo(15f); verticalLineTo(9f); close()
            }
            StudioGlyph.CLOSE -> { moveTo(19f, 6.41f); lineTo(17.59f, 5f); lineTo(12f, 10.59f); lineTo(6.41f, 5f); lineTo(5f, 6.41f); lineTo(10.59f, 12f); lineTo(5f, 17.59f); lineTo(6.41f, 19f); lineTo(12f, 13.41f); lineTo(17.59f, 19f); lineTo(19f, 17.59f); lineTo(13.41f, 12f); close() }
            StudioGlyph.RUN -> { moveTo(8f, 5f); verticalLineTo(19f); lineTo(19f, 12f); close() }
            StudioGlyph.FIND -> { moveTo(15.5f, 14f); horizontalLineTo(14.71f); lineTo(14.43f, 13.73f); curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f); curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f); curveTo(5.91f, 3f, 3f, 5.91f, 3f, 9.5f); curveTo(3f, 13.09f, 5.91f, 16f, 9.5f, 16f); curveTo(11.11f, 16f, 12.59f, 15.41f, 13.73f, 14.43f); lineTo(14f, 14.71f); verticalLineTo(15.5f); lineTo(19f, 20.49f); lineTo(20.49f, 19f); close(); moveTo(9.5f, 14f); curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f); curveTo(5f, 7.01f, 7.01f, 5f, 9.5f, 5f); curveTo(11.99f, 5f, 14f, 7.01f, 14f, 9.5f); curveTo(14f, 11.99f, 11.99f, 14f, 9.5f, 14f); close() }
            StudioGlyph.REPLACE -> {
                moveTo(3f, 5f); horizontalLineTo(16f); verticalLineTo(2f); lineTo(22f, 8f)
                lineTo(16f, 14f); verticalLineTo(11f); horizontalLineTo(3f); close()
                moveTo(21f, 19f); horizontalLineTo(8f); verticalLineTo(22f); lineTo(2f, 16f)
                lineTo(8f, 10f); verticalLineTo(13f); horizontalLineTo(21f); close()
            }
            StudioGlyph.ASSIST -> { moveTo(12f, 2f); lineTo(14f, 9f); lineTo(21f, 12f); lineTo(14f, 14f); lineTo(12f, 22f); lineTo(10f, 14f); lineTo(3f, 12f); lineTo(10f, 9f); close() }
            StudioGlyph.EXPAND -> { moveTo(7.41f, 8.59f); lineTo(12f, 13.17f); lineTo(16.59f, 8.59f); lineTo(18f, 10f); lineTo(12f, 16f); lineTo(6f, 10f); close() }
            StudioGlyph.COLLAPSE -> { moveTo(16.59f, 15.41f); lineTo(12f, 10.83f); lineTo(7.41f, 15.41f); lineTo(6f, 14f); lineTo(12f, 8f); lineTo(18f, 14f); close() }
            StudioGlyph.FOLDER -> {
                moveTo(3f, 5f); horizontalLineTo(9f); lineTo(11f, 7f); horizontalLineTo(21f)
                verticalLineTo(19f); horizontalLineTo(3f); close()
            }
            StudioGlyph.MORE -> {
                moveTo(4f, 11f); horizontalLineTo(7f); verticalLineTo(14f); horizontalLineTo(4f); close()
                moveTo(10.5f, 11f); horizontalLineTo(13.5f); verticalLineTo(14f); horizontalLineTo(10.5f); close()
                moveTo(17f, 11f); horizontalLineTo(20f); verticalLineTo(14f); horizontalLineTo(17f); close()
            }
            StudioGlyph.ADD -> {
                moveTo(11f, 4f); horizontalLineTo(13f); verticalLineTo(11f); horizontalLineTo(20f)
                verticalLineTo(13f); horizontalLineTo(13f); verticalLineTo(20f); horizontalLineTo(11f)
                verticalLineTo(13f); horizontalLineTo(4f); verticalLineTo(11f); horizontalLineTo(11f); close()
            }
            StudioGlyph.CHECK -> {
                moveTo(9.2f, 16.6f); lineTo(4.8f, 12.2f); lineTo(6.2f, 10.8f)
                lineTo(9.2f, 13.8f); lineTo(17.8f, 5.2f); lineTo(19.2f, 6.6f); close()
            }
        }
    }
}.build()

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


