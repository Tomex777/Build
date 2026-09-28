package studio.artistscene.app

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.artistscene.core.Actor
import studio.artistscene.core.ActorKind
import studio.artistscene.core.SceneEditorState
import studio.artistscene.core.SceneProject
import studio.artistscene.core.TransformAxis
import studio.artistscene.core.TransformTool

private val StudioBackground = Color(0xFF15191F)
private val PanelBackground = Color(0xFF222832)
private val MutedText = Color(0xFFAAB4C2)
private val PrimaryText = Color(0xFFF2F5F8)

@Composable
internal fun StudioScreen(
    initialProject: SceneProject,
    initiallyRestored: Boolean,
    onSave: (SceneProject) -> Unit,
    onRestore: (String) -> SceneProject?,
    onExitToBrowser: () -> Unit,
) {
    val initialSelection = initialProject.actors.firstOrNull { it.id == PrototypeScene.PROP_ID }?.id
        ?: initialProject.actors.firstOrNull()?.id
    var editor by remember(initialProject.id) {
        mutableStateOf(SceneEditorState(initialProject).selectActor(initialSelection))
    }
    var assetStatus by remember { mutableStateOf("Loading bundled GLB…") }
    var rendererStatus by remember { mutableStateOf("Waiting for renderer surface") }
    var saveStatus by remember {
        mutableStateOf(if (initiallyRestored) "Restored saved scene" else "New scene")
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importer = remember(context) { SceneAssetImporter(context) }
    var showAddSheet by remember { mutableStateOf(false) }
    var importKind by remember { mutableStateOf(ActorKind.PROP) }
    var importStatus by remember { mutableStateOf("") }
    var saveInProgress by remember { mutableStateOf(false) }

    fun applyEditor(next: SceneEditorState, reason: String) {
        if (next == editor) return
        val oldPropX = editor.project.propX()
        val projectChanged = next.project != editor.project
        editor = next
        if (projectChanged) saveStatus = "Unsaved changes"
        val newPropX = next.project.propX()
        if (newPropX != oldPropX) {
            Log.i(
                RUNTIME_LOG_TAG,
                "transform prop=${PrototypeScene.PROP_ID} x=${"%.2f".format(Locale.US, newPropX)}",
            )
        }
        Log.d(RUNTIME_LOG_TAG, "editor-change reason=$reason selected=${next.selectedActorId}")
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            importStatus = "Import cancelled"
        } else {
            val requestedKind = importKind
            importStatus = "Validating model…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    importer.import(uri, requestedKind)
                }
                result.fold(
                    onSuccess = { imported ->
                        applyEditor(editor.addActor(imported.actor), "import-model")
                        importStatus = if (imported.persistedWithSaf) {
                            "Imported ${imported.actor.name} · source access retained"
                        } else {
                            "Imported ${imported.actor.name} · secured local copy"
                        }
                        saveStatus = "Unsaved changes"
                    },
                    onFailure = { error ->
                        importStatus = "Import failed · ${error.message ?: "Unsupported model"}"
                        Log.w(RUNTIME_LOG_TAG, "asset-import-failed", error)
                    },
                )
            }
        }
    }

    val handleAssetLoaded: (String) -> Unit = { name ->
        assetStatus = "Loaded GLB · $name"
        Log.i(RUNTIME_LOG_TAG, "asset-loaded name=$name")
    }
    val handleAssetFailed: (String) -> Unit = { message ->
        assetStatus = "GLB load failed · $message"
        Log.e(RUNTIME_LOG_TAG, "asset-failed $message")
    }
    val handleRendererFrame: () -> Unit = {
        rendererStatus = "Renderer loop active"
        Log.i(RUNTIME_LOG_TAG, "renderer-first-frame")
    }
    val handleSave: () -> Unit = {
        if (!saveInProgress) {
            val snapshot = editor.project
            saveInProgress = true
            saveStatus = "Saving scene"
            scope.launch {
                val failure = withContext(Dispatchers.IO) {
                    runCatching { onSave(snapshot) }.exceptionOrNull()
                }
                saveInProgress = false
                saveStatus = if (failure == null) "Saved scene" else "Save failed · ${failure.message ?: "storage error"}"
                if (failure == null) {
                    Log.i(
                        RUNTIME_LOG_TAG,
                        "scene-saved project=${snapshot.id} x=${"%.2f".format(Locale.US, snapshot.propX())}",
                    )
                } else {
                    Log.e(RUNTIME_LOG_TAG, "scene-save-failed project=${snapshot.id}", failure)
                }
            }
        }
    }
    val handleRestore: () -> Unit = {
        val restored = onRestore(editor.project.id)
        if (restored != null) {
            editor = editor.replaceProject(restored, preserveSelection = false)
            saveStatus = "Restored saved scene"
            Log.i(
                RUNTIME_LOG_TAG,
                "scene-restored-manual project=${restored.id} x=${"%.2f".format(Locale.US, restored.propX())}",
            )
        } else {
            saveStatus = "No saved scene"
            Log.w(RUNTIME_LOG_TAG, "scene-restore-missing project=${editor.project.id}")
        }
    }
    val handleExitToBrowser: () -> Unit = {
        val snapshot = editor.project
        if (snapshot == initialProject) {
            onExitToBrowser()
        } else {
            saveStatus = "Saving before leaving scene"
            scope.launch {
                val failure = withContext(Dispatchers.IO) {
                    runCatching { onSave(snapshot) }.exceptionOrNull()
                }
                if (failure == null) {
                    saveStatus = "Saved scene"
                    onExitToBrowser()
                } else {
                    saveStatus = "Save failed · ${failure.message ?: "storage error"}"
                    Log.e(RUNTIME_LOG_TAG, "scene-exit-save-failed project=${snapshot.id}", failure)
                }
            }
        }
    }
    BackHandler(onBack = handleExitToBrowser)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
        color = StudioBackground,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ViewportPane(
                        editor = editor,
                        assetStatus = assetStatus,
                        rendererStatus = rendererStatus,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        onSelect = { applyEditor(editor.selectActor(it), "viewport-select") },
                        onAssetLoaded = handleAssetLoaded,
                        onAssetFailed = handleAssetFailed,
                        onRendererFrame = handleRendererFrame,
                    )
                    EditorPanel(
                        editor = editor,
                        saveStatus = saveStatus,
                        importStatus = importStatus,
                        modifier = Modifier.width(330.dp).fillMaxSize(),
                        onEditor = { next, reason -> applyEditor(next, reason) },
                        onAdd = { showAddSheet = true; Log.i(RUNTIME_LOG_TAG, "add-sheet-open") },
                        onSave = handleSave,
                        onRestore = handleRestore,
                        onExitToBrowser = handleExitToBrowser,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProjectHeader(editor.project)
                    ViewportPane(
                        editor = editor,
                        assetStatus = assetStatus,
                        rendererStatus = rendererStatus,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onSelect = { applyEditor(editor.selectActor(it), "viewport-select") },
                        onAssetLoaded = handleAssetLoaded,
                        onAssetFailed = handleAssetFailed,
                        onRendererFrame = handleRendererFrame,
                    )
                    EditorPanel(
                        editor = editor,
                        saveStatus = saveStatus,
                        importStatus = importStatus,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 230.dp, max = 285.dp),
                        onEditor = { next, reason -> applyEditor(next, reason) },
                        onAdd = { showAddSheet = true; Log.i(RUNTIME_LOG_TAG, "add-sheet-open") },
                        onSave = handleSave,
                        onRestore = handleRestore,
                        onExitToBrowser = handleExitToBrowser,
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddObjectSheet(
            selectedKind = importKind,
            onKindSelected = { importKind = it },
            onImport = {
                showAddSheet = false
                importLauncher.launch(
                    arrayOf(
                        "model/gltf-binary",
                        "model/gltf+json",
                        "application/octet-stream",
                        "application/json",
                    ),
                )
            },
            onDismiss = { showAddSheet = false },
        )
    }
}

@Composable
private fun ProjectHeader(project: SceneProject) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Mise", color = PrimaryText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Text(project.name, color = MutedText, fontSize = 12.sp)
        }
        Text("${project.actors.size} objects", color = MutedText, fontSize = 11.sp)
    }
}

@Composable
private fun ViewportPane(
    editor: SceneEditorState,
    assetStatus: String,
    rendererStatus: String,
    modifier: Modifier,
    onSelect: (String?) -> Unit,
    onAssetLoaded: (String) -> Unit,
    onAssetFailed: (String) -> Unit,
    onRendererFrame: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(Color(0xFF202630), RoundedCornerShape(16.dp)),
    ) {
        SceneViewport(
            project = editor.project,
            selectedActorId = editor.selectedActorId,
            modifier = Modifier.fillMaxSize(),
            onSelectActor = onSelect,
            onAssetLoaded = onAssetLoaded,
            onAssetFailed = onAssetFailed,
            onRendererFrame = onRendererFrame,
        )
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                "LIVE FILAMENT VIEWPORT",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            editor.selectedActor?.let {
                Text("Selected · ${it.name}", color = Color(0xFFDDE7F1), fontSize = 10.sp)
            }
        }
        val viewportStatus = if (rendererStatus == "Renderer loop active") {
            "$assetStatus · $rendererStatus"
        } else {
            "$assetStatus · $rendererStatus"
        }
        Text(
            viewportStatus,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .testTag("asset-status"),
            color = Color(0xFFD0D7E1),
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun EditorPanel(
    editor: SceneEditorState,
    saveStatus: String,
    importStatus: String,
    modifier: Modifier,
    onEditor: (SceneEditorState, String) -> Unit,
    onAdd: () -> Unit,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    onExitToBrowser: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .background(PanelBackground, RoundedCornerShape(16.dp))
            .padding(10.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        EditorQuickBar(editor, saveStatus, importStatus, onEditor, onAdd, onSave, onRestore, onExitToBrowser)
        QuickXNudge(editor, onEditor)
        SceneHierarchy(editor, onEditor)
        editor.selectedActor?.let { actor ->
            SelectedActorActions(editor, actor, onEditor)
            TransformInspector(editor, actor, onEditor)
        } ?: Text("Select an object to edit it.", color = MutedText, fontSize = 12.sp)
    }
}

@Composable
private fun EditorQuickBar(
    editor: SceneEditorState,
    saveStatus: String,
    importStatus: String,
    onEditor: (SceneEditorState, String) -> Unit,
    onAdd: () -> Unit,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    onExitToBrowser: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onExitToBrowser,
            modifier = Modifier.size(36.dp).testTag("back-to-projects"),
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back to projects", tint = PrimaryText)
        }
        Text(
            editor.selectedActor?.name ?: "Scene",
            modifier = Modifier.weight(1f),
            color = PrimaryText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
        )
        IconButton(
            onClick = onAdd,
            modifier = Modifier.size(36.dp).testTag("add-object"),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add to scene", tint = PrimaryText)
        }
        IconButton(
            onClick = { onEditor(editor.undo(), "undo") },
            enabled = editor.canUndo,
            modifier = Modifier.size(36.dp).testTag("undo"),
        ) {
            Icon(Icons.Default.Undo, contentDescription = "Undo", tint = PrimaryText)
        }
        IconButton(
            onClick = { onEditor(editor.redo(), "redo") },
            enabled = editor.canRedo,
            modifier = Modifier.size(36.dp).testTag("redo"),
        ) {
            Icon(Icons.Default.Redo, contentDescription = "Redo", tint = PrimaryText)
        }
        IconButton(
            onClick = onRestore,
            modifier = Modifier.size(36.dp).testTag("restore-project"),
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = "Restore saved scene", tint = PrimaryText)
        }
        IconButton(
            onClick = onSave,
            modifier = Modifier.size(36.dp).testTag("save-project"),
        ) {
            Icon(Icons.Default.Save, contentDescription = "Save scene", tint = PrimaryText)
        }
    }
    Text(saveStatus, color = MutedText, fontSize = 10.sp, modifier = Modifier.testTag("save-status"))
    if (importStatus.isNotBlank()) {
        Text(importStatus, color = MutedText, fontSize = 10.sp, modifier = Modifier.testTag("import-status"))
    }
}

@Composable
private fun QuickXNudge(
    editor: SceneEditorState,
    onEditor: (SceneEditorState, String) -> Unit,
) {
    val actor = editor.selectedActor ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Position X", color = MutedText, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = { onEditor(editor.translate(TransformAxis.X, -0.25f), "move-x") },
            enabled = !actor.locked,
            modifier = Modifier.size(36.dp).testTag("move-left"),
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Move X left", tint = PrimaryText)
        }
        Text(
            "X ${"%.2f".format(Locale.US, actor.transform.position.x)}",
            color = PrimaryText,
            fontSize = 11.sp,
            modifier = Modifier.testTag("actor-x"),
        )
        IconButton(
            onClick = { onEditor(editor.translate(TransformAxis.X, 0.25f), "move-x") },
            enabled = !actor.locked,
            modifier = Modifier.size(36.dp).testTag("move-right"),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Move X right", tint = PrimaryText)
        }
    }
}

@Composable
private fun SceneHierarchy(
    editor: SceneEditorState,
    onEditor: (SceneEditorState, String) -> Unit,
) {
    Text("Scene hierarchy", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        editor.project.actors.forEach { actor ->
            FilterChip(
                selected = editor.selectedActorId == actor.id,
                onClick = { onEditor(editor.selectActor(actor.id), "hierarchy-select") },
                label = { Text(actor.name, maxLines = 1) },
                leadingIcon = if (!actor.visible) {
                    { Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(15.dp)) }
                } else null,
                modifier = Modifier.testTag("actor-${actor.id}"),
            )
        }
    }
}

@Composable
private fun SelectedActorActions(
    editor: SceneEditorState,
    actor: Actor,
    onEditor: (SceneEditorState, String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(
            onClick = { onEditor(editor.toggleSelectedVisibility(), "visibility") },
            modifier = Modifier.size(36.dp).testTag("toggle-visibility"),
        ) {
            Icon(
                if (actor.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (actor.visible) "Hide object" else "Show object",
                tint = PrimaryText,
            )
        }
        IconButton(
            onClick = { onEditor(editor.toggleSelectedLocked(), "lock") },
            modifier = Modifier.size(36.dp).testTag("toggle-lock"),
        ) {
            Icon(
                if (actor.locked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = if (actor.locked) "Unlock object" else "Lock object",
                tint = PrimaryText,
            )
        }
        IconButton(
            onClick = { onEditor(editor.duplicateSelected(), "duplicate") },
            modifier = Modifier.size(36.dp).testTag("duplicate-actor"),
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate object", tint = PrimaryText)
        }
        IconButton(
            onClick = { onEditor(editor.resetTransform(), "reset-transform") },
            enabled = !actor.locked,
            modifier = Modifier.size(36.dp).testTag("reset-transform"),
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = "Reset transform", tint = PrimaryText)
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = { onEditor(editor.deleteSelected(), "delete") },
            enabled = !actor.locked,
            modifier = Modifier.size(36.dp).testTag("delete-actor"),
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete object", tint = Color(0xFFFFB4AB))
        }
    }
}

@Composable
private fun TransformInspector(
    editor: SceneEditorState,
    actor: Actor,
    onEditor: (SceneEditorState, String) -> Unit,
) {
    Text("Transform", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TransformTool.entries.forEach { tool ->
            FilterChip(
                selected = editor.activeTool == tool,
                onClick = { onEditor(editor.useTool(tool), "tool") },
                label = { Text(tool.name.lowercase().replaceFirstChar { it.uppercase() }) },
                modifier = Modifier.testTag("tool-${tool.name.lowercase()}"),
            )
        }
    }

    val values = when (editor.activeTool) {
        TransformTool.MOVE -> actor.transform.position
        TransformTool.ROTATE -> actor.transform.rotationEulerDegrees
        TransformTool.SCALE -> actor.transform.scale
    }
    val step = when (editor.activeTool) {
        TransformTool.MOVE -> 0.25f
        TransformTool.ROTATE -> 15f
        TransformTool.SCALE -> 0.1f
    }

    TransformAxis.entries.forEach { axis ->
        val value = when (axis) {
            TransformAxis.X -> values.x
            TransformAxis.Y -> values.y
            TransformAxis.Z -> values.z
        }
        NumericAxisEditor(
            axis = axis,
            value = value,
            step = step,
            enabled = !actor.locked,
            onDelta = { delta ->
                val next = when (editor.activeTool) {
                    TransformTool.MOVE -> editor.translate(axis, delta)
                    TransformTool.ROTATE -> editor.rotate(axis, delta)
                    TransformTool.SCALE -> editor.scale(axis, delta)
                }
                onEditor(next, "transform-${editor.activeTool.name.lowercase()}")
            },
            onSet = { exact ->
                val next = when (editor.activeTool) {
                    TransformTool.MOVE -> editor.setPosition(axis, exact)
                    TransformTool.ROTATE -> editor.setRotation(axis, exact)
                    TransformTool.SCALE -> editor.setScale(axis, exact)
                }
                onEditor(next, "transform-exact-${editor.activeTool.name.lowercase()}")
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddObjectSheet(
    selectedKind: ActorKind,
    onKindSelected: (ActorKind) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PanelBackground,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add to scene", color = PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Import a GLB or self-contained glTF 2.0 model. External glTF buffers/textures are rejected with a clear error instead of leaving a broken scene.",
                color = MutedText,
                fontSize = 12.sp,
            )
            Text("Model role", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                listOf(
                    ActorKind.PROP,
                    ActorKind.CHARACTER,
                    ActorKind.VEHICLE,
                    ActorKind.ENVIRONMENT,
                    ActorKind.EFFECT,
                ).forEach { kind ->
                    FilterChip(
                        selected = selectedKind == kind,
                        onClick = { onKindSelected(kind) },
                        label = { Text(kind.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth().testTag("import-model"),
            ) {
                Text("Choose model file")
            }
            Text(
                "Imported files are capped at 128 MiB. Mise keeps a persistable Android file grant when possible and falls back to a private validated copy when needed.",
                color = MutedText,
                fontSize = 11.sp,
            )
            Spacer(Modifier.size(14.dp))
        }
    }
}

@Composable
private fun NumericAxisEditor(
    axis: TransformAxis,
    value: Float,
    step: Float,
    enabled: Boolean,
    onDelta: (Float) -> Unit,
    onSet: (Float) -> Unit,
) {
    var text by remember(value) { mutableStateOf("%.2f".format(Locale.US, value)) }
    fun commitText() {
        text.toFloatOrNull()?.let(onSet)
        text = "%.2f".format(Locale.US, value)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(axis.name, color = PrimaryText, fontSize = 12.sp, modifier = Modifier.width(14.dp))
        IconButton(
            onClick = { onDelta(-step) },
            enabled = enabled,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease ${axis.name}", tint = PrimaryText)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.filter { ch -> ch.isDigit() || ch == '-' || ch == '.' }.take(12) },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            keyboardActions = KeyboardActions(onDone = { commitText() }),
            modifier = Modifier
                .width(88.dp)
                .heightIn(min = 48.dp)
                .onFocusChanged { focus -> if (!focus.isFocused) commitText() }
                .testTag("numeric-${axis.name.lowercase()}"),
        )
        IconButton(
            onClick = { onDelta(step) },
            enabled = enabled,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase ${axis.name}", tint = PrimaryText)
        }
        Text(
            when (step) {
                15f -> "±15°"
                else -> "±${"%.2f".format(Locale.US, step)}"
            },
            color = MutedText,
            fontSize = 10.sp,
            modifier = Modifier.alpha(if (enabled) 1f else 0.5f),
        )
    }
}
