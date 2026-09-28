package studio.artistscene.app

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consume
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.artistscene.core.Actor
import studio.artistscene.core.ActorKind
import studio.artistscene.core.SceneEditorState
import studio.artistscene.core.SceneProject
import studio.artistscene.core.SceneCamera
import studio.artistscene.core.LightSettings
import studio.artistscene.core.LightType
import studio.artistscene.core.TransformAxis
import studio.artistscene.core.Transform
import studio.artistscene.core.TransformTool
import studio.artistscene.core.Vec3
import java.util.UUID

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
    var activeSheet by remember { mutableStateOf<String?>(null) }
    var referenceMode by remember { mutableStateOf(false) }
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

    fun frameAt(target: Vec3, distance: Float, label: String) {
        val camera = editor.project.cameras.firstOrNull { it.id == editor.project.activeCameraId } ?: return
        val focus = target.copy(y = target.y + 0.8f)
        val nextCamera = camera.copy(
            position = Vec3(focus.x, focus.y + 0.25f, focus.z + distance),
            target = focus,
        )
        applyEditor(editor.updateActiveCamera(nextCamera), "frame-$label")
        activeSheet = null
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
                        var next = editor.addActor(imported.actor)
                        val camera = next.project.cameras.firstOrNull { it.id == next.project.activeCameraId }
                        if (camera != null) {
                            val position = imported.actor.transform.position
                            val focus = position.copy(y = position.y + 0.8f)
                            val distance = when (requestedKind) {
                                ActorKind.CHARACTER -> 3.4f
                                ActorKind.VEHICLE -> 4f
                                ActorKind.ENVIRONMENT -> 6f
                                ActorKind.EFFECT -> 3f
                                else -> 2.8f
                            }
                            next = next.updateActiveCamera(
                                camera.copy(
                                    position = Vec3(focus.x, focus.y + 0.25f, focus.z + distance),
                                    target = focus,
                                ),
                            )
                        }
                        applyEditor(next, "import-model")
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
        modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
        color = StudioBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            SceneViewport(
                project = editor.project,
                selectedActorId = editor.selectedActorId,
                modifier = Modifier.fillMaxSize().testTag("scene-viewport"),
                onSelectActor = { applyEditor(editor.selectActor(it), "viewport-select") },
                onAssetLoaded = handleAssetLoaded,
                onAssetFailed = handleAssetFailed,
                onRendererFrame = handleRendererFrame,
            )
            editor.selectedActor?.takeIf { !it.locked }?.let { actor ->
                if (!referenceMode) {
                    ViewportTransformGizmo(
                        editor = editor,
                        onEditor = { next, reason -> applyEditor(next, reason) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (!referenceMode) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    color = PanelBackground,
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 0.dp,
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = handleExitToBrowser, modifier = Modifier.size(42.dp).testTag("back-to-projects")) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Projects", tint = PrimaryText)
                        }
                        IconButton(onClick = { applyEditor(editor.undo(), "undo") }, enabled = editor.canUndo, modifier = Modifier.size(40.dp).testTag("undo")) {
                            Icon(Icons.Default.Undo, contentDescription = "Undo", tint = PrimaryText)
                        }
                        IconButton(onClick = { applyEditor(editor.redo(), "redo") }, enabled = editor.canRedo, modifier = Modifier.size(40.dp).testTag("redo")) {
                            Icon(Icons.Default.Redo, contentDescription = "Redo", tint = PrimaryText)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(editor.selectedActor?.name ?: "Scene", modifier = Modifier.widthIn(max = 72.dp), color = MutedText, fontSize = 11.sp, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { activeSheet = "inspector" }, modifier = Modifier.size(38.dp).testTag("inspector")) {
                            Text("Info", color = PrimaryText, fontSize = 10.sp)
                        }
                        IconButton(onClick = handleSave, modifier = Modifier.size(40.dp).testTag("save-project")) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = PrimaryText)
                        }
                        IconButton(onClick = { referenceMode = true }, modifier = Modifier.size(40.dp).testTag("reference-mode")) {
                            Text("View", color = PrimaryText, fontSize = 11.sp)
                        }
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    color = PanelBackground,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EditorTool("+ Add", true, "add-object") { showAddSheet = true; Log.i(RUNTIME_LOG_TAG, "add-sheet-open") }
                        EditorTool("Scene", false, "scene-hierarchy") { activeSheet = "hierarchy" }
                        listOf(TransformTool.MOVE, TransformTool.ROTATE, TransformTool.SCALE).forEach { tool ->
                            val label = tool.name.lowercase().replaceFirstChar { it.uppercase() }
                            EditorTool(label, editor.activeTool == tool, "tool-${tool.name.lowercase()}") {
                                applyEditor(editor.useTool(tool), "tool")
                            }
                        }
                        EditorTool("Pose", false, "pose-tools") { activeSheet = "pose" }
                        EditorTool("Camera", false, "camera-tools") { activeSheet = "camera" }
                        EditorTool("Light", false, "light-tools") { activeSheet = "light" }
                    }
                }
                if (assetStatus.startsWith("GLB load failed") || importStatus.startsWith("Import failed")) {
                    Text(
                        importStatus.ifBlank { assetStatus },
                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 58.dp, start = 12.dp, end = 12.dp).testTag("asset-status"),
                        color = Color(0xFFFFB4AB), fontSize = 11.sp,
                    )
                }
                if (saveStatus != "New scene" && saveStatus != "Restored saved scene") {
                    Text(saveStatus, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 76.dp).testTag("save-status"), color = MutedText, fontSize = 10.sp)
                }
            } else {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
                    color = PanelBackground,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Button(onClick = { referenceMode = false }, modifier = Modifier.testTag("exit-reference-mode")) { Text("Edit scene") }
                }
            }
        }
    }

    if (showAddSheet) {
        AddObjectSheet(
            selectedKind = importKind,
            onKindSelected = { importKind = it },
            onAddLight = { type ->
                val id = "light-" + UUID.randomUUID().toString().replace("-", "").take(12)
                val lightActor = Actor(
                    id = id,
                    name = if (type == LightType.POINT) "Point Light" else "Directional Light",
                    kind = ActorKind.LIGHT,
                    transform = studio.artistscene.core.Transform(position = Vec3(1.5f, 2f, 1f)),
                    light = LightSettings(type = type, intensity = if (type == LightType.POINT) 1_400f else 110_000f),
                )
                applyEditor(editor.addActor(lightActor), "add-light")
                showAddSheet = false
            },
            onAddCamera = {
                val id = "camera-" + UUID.randomUUID().toString().replace("-", "").take(12)
                applyEditor(editor.addCamera(SceneCamera(id, "Camera ${editor.project.cameras.size}")), "add-camera")
                showAddSheet = false
            },
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
    activeSheet?.let { sheet ->
        EditorContextSheet(
            sheet = sheet,
            editor = editor,
            onEditor = { next, reason -> applyEditor(next, reason) },
            onClose = { activeSheet = null },
            saveStatus = saveStatus,
            onFrameSelected = {
                editor.selectedActor?.let { actor ->
                    val distance = when (actor.kind) {
                        ActorKind.CHARACTER -> 3.4f
                        ActorKind.ENVIRONMENT -> 5.5f
                        ActorKind.VEHICLE -> 4f
                        else -> 2.8f
                    }
                    frameAt(actor.transform.position, distance, "selected")
                }
            },
            onFrameScene = {
                val visible = editor.project.actors.filter { it.visible && it.kind != ActorKind.LIGHT && it.kind != ActorKind.CAMERA }
                if (visible.isNotEmpty()) {
                    val center = Vec3(
                        visible.map { it.transform.position.x }.average().toFloat(),
                        visible.map { it.transform.position.y }.average().toFloat(),
                        visible.map { it.transform.position.z }.average().toFloat(),
                    )
                    frameAt(center, 4.5f, "scene")
                }
            },
            onResetCamera = {
                val camera = editor.project.cameras.firstOrNull { it.id == editor.project.activeCameraId }
                if (camera != null) frameAt(Vec3(), 3.8f, "reset")
            },
        )
    }
}

@Composable
private fun ViewportTransformGizmo(
    editor: SceneEditorState,
    onEditor: (SceneEditorState, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestEditor = rememberUpdatedState(editor)
    val latestOnEditor = rememberUpdatedState(onEditor)
    val offsets = mapOf(
        TransformAxis.X to androidx.compose.ui.unit.IntOffset(72, 0),
        TransformAxis.Y to androidx.compose.ui.unit.IntOffset(0, -72),
        TransformAxis.Z to androidx.compose.ui.unit.IntOffset(54, 54),
    )
    val colors = mapOf(
        TransformAxis.X to Color(0xFFE66A6A),
        TransformAxis.Y to Color(0xFF68C98A),
        TransformAxis.Z to Color(0xFF6A9EFF),
    )
    Box(modifier) {
        Canvas(Modifier.align(Alignment.Center).size(154.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawLine(colors.getValue(TransformAxis.X), center, Offset(size.width - 5f, center.y), strokeWidth = 5.dp.toPx())
            drawLine(colors.getValue(TransformAxis.Y), center, Offset(center.x, 5f), strokeWidth = 5.dp.toPx())
            drawLine(colors.getValue(TransformAxis.Z), center, Offset(size.width - 20.dp.toPx(), size.height - 20.dp.toPx()), strokeWidth = 5.dp.toPx())
        }
        TransformAxis.entries.forEach { axis ->
            val axisColor = colors.getValue(axis)
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (offsets.getValue(axis).x).dp, y = (offsets.getValue(axis).y).dp)
                    .size(54.dp)
                    .testTag("gizmo-${editor.activeTool.name.lowercase()}-${axis.name.lowercase()}")
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .pointerInput(editor.activeTool, editor.selectedActorId) {
                        var before: SceneEditorState? = null
                        var accumulated = 0f
                        detectDragGestures(
                            onDragStart = {
                                before = latestEditor.value
                                accumulated = 0f
                            },
                            onDragEnd = {
                                before?.let { origin ->
                                    latestOnEditor.value(
                                        latestEditor.value.commitTransformGesture(origin.project),
                                        "gizmo-${latestEditor.value.activeTool.name.lowercase()}-${axis.name.lowercase()}",
                                    )
                                }
                                before = null
                            },
                            onDragCancel = {
                                before?.let { origin ->
                                    latestOnEditor.value(
                                        latestEditor.value.cancelTransformGesture(origin.project),
                                        "gizmo-cancel",
                                    )
                                }
                                before = null
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val origin = before ?: return@detectDragGestures
                                val projectedDrag = when (axis) {
                                    TransformAxis.X -> dragAmount.x
                                    TransformAxis.Y -> -dragAmount.y
                                    TransformAxis.Z -> (dragAmount.x + dragAmount.y) * 0.7071f
                                }
                                accumulated += projectedDrag
                                val start = origin.selectedActor?.transform ?: Transform()
                                val amount = when (origin.activeTool) {
                                    TransformTool.MOVE -> accumulated * 0.005f
                                    TransformTool.ROTATE -> accumulated * 0.65f
                                    TransformTool.SCALE -> accumulated * 0.003f
                                }
                                val nextTransform = when (origin.activeTool) {
                                    TransformTool.MOVE -> start.copy(position = when (axis) {
                                        TransformAxis.X -> start.position.copy(x = start.position.x + amount)
                                        TransformAxis.Y -> start.position.copy(y = start.position.y + amount)
                                        TransformAxis.Z -> start.position.copy(z = start.position.z + amount)
                                    })
                                    TransformTool.ROTATE -> start.copy(rotationEulerDegrees = when (axis) {
                                        TransformAxis.X -> start.rotationEulerDegrees.copy(x = start.rotationEulerDegrees.x + amount)
                                        TransformAxis.Y -> start.rotationEulerDegrees.copy(y = start.rotationEulerDegrees.y + amount)
                                        TransformAxis.Z -> start.rotationEulerDegrees.copy(z = start.rotationEulerDegrees.z + amount)
                                    })
                                    TransformTool.SCALE -> start.copy(scale = when (axis) {
                                        TransformAxis.X -> start.scale.copy(x = (start.scale.x + amount).coerceAtLeast(0.01f))
                                        TransformAxis.Y -> start.scale.copy(y = (start.scale.y + amount).coerceAtLeast(0.01f))
                                        TransformAxis.Z -> start.scale.copy(z = (start.scale.z + amount).coerceAtLeast(0.01f))
                                    })
                                }
                                val preview = latestEditor.value.previewSelectedTransform(nextTransform)
                                latestOnEditor.value(preview, "gizmo-preview")
                            },
                        )
                    },
                color = axisColor,
                shape = CircleShape,
                tonalElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(axis.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun EditorTool(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, fontSize = 11.sp) },
        modifier = Modifier.testTag(tag),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorContextSheet(
    sheet: String,
    editor: SceneEditorState,
    onEditor: (SceneEditorState, String) -> Unit,
    onClose: () -> Unit,
    saveStatus: String,
    onFrameSelected: () -> Unit,
    onFrameScene: () -> Unit,
    onResetCamera: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = PanelBackground) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when (sheet) { "hierarchy" -> "Scene"; "inspector" -> "Inspector"; "pose" -> "Pose"; "camera" -> "Camera"; else -> "Lighting" },
                color = PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            )
            when (sheet) {
                "hierarchy" -> {
                    editor.project.actors.forEach { actor ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = editor.selectedActorId == actor.id,
                                onClick = { onEditor(editor.selectActor(actor.id), "hierarchy-select") },
                                label = { Text(actor.name, maxLines = 1) },
                                modifier = Modifier.weight(1f).testTag("actor-${actor.id}"),
                            )
                            IconButton(onClick = {
                                val selected = editor.selectActor(actor.id)
                                onEditor(selected.toggleSelectedVisibility(), "visibility")
                            }, modifier = Modifier.testTag("visibility-${actor.id}")) {
                                Icon(if (actor.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "Toggle visibility", tint = PrimaryText)
                            }
                        }
                    }
                    editor.selectedActor?.let { actor ->
                        var name by remember(actor.id, actor.name) { mutableStateOf(actor.name) }
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Object name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("actor-name"))
                        Button(onClick = { onEditor(editor.renameSelected(name), "rename"); onClose() }, modifier = Modifier.fillMaxWidth().testTag("rename-actor")) { Text("Rename") }
                        SelectedActorActions(editor, actor, onEditor)
                    }
                }
                "inspector" -> editor.selectedActor?.let { actor ->
                    Text("${actor.kind.name.lowercase().replaceFirstChar { it.uppercase() }} · ${actor.asset?.relativePath ?: "Scene object"}", color = MutedText, fontSize = 12.sp)
                    SelectedActorActions(editor, actor, onEditor)
                    TransformInspector(editor, actor, onEditor)
                } ?: Text("Select an object to inspect it.", color = MutedText)
                "pose" -> {
                    val actor = editor.selectedActor
                    Text(actor?.let { "Selected · ${it.name}" } ?: "Select a character to inspect its rig.", color = MutedText)
                    Text(
                        if (actor?.kind == ActorKind.CHARACTER) "Skeleton controls will appear here when the imported asset exposes a supported rig." else "Choose a rigged character to pose it directly in the viewport.",
                        color = PrimaryText, fontSize = 13.sp,
                    )
                }
                "camera" -> {
                    Text("Drag with one finger to orbit. Use two fingers to pan and pinch to zoom.", color = PrimaryText, fontSize = 13.sp)
                    Button(onClick = onFrameSelected, modifier = Modifier.fillMaxWidth().testTag("frame-selected")) { Text("Frame selected") }
                    Button(onClick = onFrameScene, modifier = Modifier.fillMaxWidth().testTag("frame-scene")) { Text("Frame scene") }
                    Button(onClick = onResetCamera, modifier = Modifier.fillMaxWidth().testTag("reset-camera")) { Text("Reset view") }
                }
                else -> {
                    val lights = editor.project.actors.filter { it.kind == ActorKind.LIGHT }
                    Text(if (lights.isEmpty()) "No scene lights yet." else "${lights.size} scene light${if (lights.size == 1) "" else "s"}" , color = PrimaryText)
                    lights.forEach { Text(it.name, color = MutedText) }
                    Text("Light placement is available from Add. Intensity and color controls are next in the lighting workflow.", color = MutedText, fontSize = 12.sp)
                }
            }
            Text(saveStatus, color = MutedText, fontSize = 10.sp)
            Spacer(Modifier.size(12.dp))
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
    onAddLight: (LightType) -> Unit,
    onAddCamera: () -> Unit,
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
                Text(if (selectedKind == ActorKind.CHARACTER) "Import character" else "Import ${selectedKind.name.lowercase()}")
            }
            Text("Add to scene", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAddLight(LightType.POINT) }, modifier = Modifier.weight(1f).testTag("add-point-light")) { Text("Point light") }
                Button(onClick = { onAddLight(LightType.DIRECTIONAL) }, modifier = Modifier.weight(1f).testTag("add-directional-light")) { Text("Sun light") }
            }
            Button(onClick = onAddCamera, modifier = Modifier.fillMaxWidth().testTag("add-camera")) { Text("Add camera") }
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
