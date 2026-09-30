package studio.artistscene.app

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import studio.artistscene.core.Actor
import studio.artistscene.core.ActorKind
import studio.artistscene.core.SceneEditorState
import studio.artistscene.core.SceneProject
import studio.artistscene.core.SceneCamera
import studio.artistscene.core.CameraProjection
import studio.artistscene.core.LightSettings
import studio.artistscene.core.ReferenceImage
import studio.artistscene.core.LightType
import studio.artistscene.core.TransformAxis
import studio.artistscene.core.Transform
import studio.artistscene.core.TransformTool
import studio.artistscene.core.Vec3
import studio.artistscene.core.RigSemantics
import studio.artistscene.core.IkPoint
import studio.artistscene.core.TwoBoneIk
import studio.artistscene.core.evaluateTimeline
import studio.artistscene.core.poseKeyTimes
import studio.artistscene.core.transformKeyTimes
import java.util.UUID
import kotlin.math.sqrt
import kotlin.math.tan

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
    var selectedJointId by remember(editor.selectedActorId) { mutableStateOf<String?>(null) }
    var selectedPoseAxis by remember(editor.selectedActorId) { mutableStateOf(TransformAxis.Z) }
    var poseIkEnabled by remember(editor.selectedActorId) { mutableStateOf(false) }
    var rigJointPositions by remember { mutableStateOf<Map<String, Map<String, Vec3>>>(emptyMap()) }
    var assetStatus by remember { mutableStateOf("Loading scene assets…") }
    var saveStatus by remember {
        mutableStateOf(if (initiallyRestored) "Restored saved scene" else "New scene")
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saveMutex = remember(initialProject.id) { Mutex() }
    var autosaveArmed by remember(initialProject.id) { mutableStateOf(false) }
    val importer = remember(context) { SceneAssetImporter(context) }
    val assetLibrary = remember(context) { ManagedAssetLibrary(context) }
    var libraryAssets by remember(context) { mutableStateOf(assetLibrary.list()) }
    var assetBrowserTab by remember { mutableStateOf(AssetBrowserTab.STARTER) }
    var showAddSheet by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<String?>(null) }
    var selectedReferenceId by remember(initialProject.id) {
        mutableStateOf(initialProject.referenceImages.firstOrNull()?.id)
    }
    var showingMoreTools by remember { mutableStateOf(false) }
    var referenceMode by remember { mutableStateOf(false) }
    var importKind by remember { mutableStateOf(ActorKind.PROP) }
    var importStatus by remember { mutableStateOf("") }
    var saveInProgress by remember { mutableStateOf(false) }
    var exportTargetUri by remember(initialProject.id) { mutableStateOf<Uri?>(null) }
    var exportInProgress by remember(initialProject.id) { mutableStateOf(false) }
    var exportStatus by remember(initialProject.id) { mutableStateOf("") }
    var rigMessages by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var timelineTime by remember(initialProject.id) { mutableStateOf(0f) }
    var timelinePlaying by remember(initialProject.id) { mutableStateOf(false) }

    LaunchedEffect(editor.project.timeline.durationSeconds) {
        timelineTime = timelineTime.coerceIn(0f, editor.project.timeline.durationSeconds.coerceAtLeast(0f))
    }
    LaunchedEffect(
        timelinePlaying,
        editor.project.timeline.durationSeconds,
        editor.project.timeline.loop,
        editor.project.timeline.playbackSpeed,
    ) {
        if (!timelinePlaying) return@LaunchedEffect
        val duration = editor.project.timeline.durationSeconds.coerceAtLeast(0.05f)
        var previousFrame = withFrameNanos { it }
        while (timelinePlaying) {
            val frame = withFrameNanos { it }
            val deltaSeconds = ((frame - previousFrame) / 1_000_000_000f) * editor.project.timeline.playbackSpeed
            previousFrame = frame
            val next = timelineTime + deltaSeconds
            if (next >= duration) {
                if (editor.project.timeline.loop) {
                    timelineTime = next % duration
                } else {
                    timelineTime = duration
                    timelinePlaying = false
                    break
                }
            } else {
                timelineTime = next
            }
        }
    }

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

    LaunchedEffect(editor.project) {
        val snapshot = editor.project
        if (!autosaveArmed) {
            autosaveArmed = true
            return@LaunchedEffect
        }
        delay(900)
        val failure = withContext(Dispatchers.IO) {
            try {
                saveMutex.withLock { onSave(snapshot) }
                null
            } catch (error: Exception) {
                error
            }
        }
        if (editor.project != snapshot) return@LaunchedEffect
        if (failure == null) {
            if (saveStatus == "Unsaved changes") saveStatus = "Saved"
            Log.i(
                RUNTIME_LOG_TAG,
                "scene-autosaved project=${snapshot.id} x=${"%.2f".format(Locale.US, snapshot.propX())}",
            )
        } else {
            if (saveStatus == "Unsaved changes") saveStatus = "Could not autosave"
            Log.e(RUNTIME_LOG_TAG, "scene-autosave-failed project=${snapshot.id}", failure)
        }
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

    fun addAndFrame(actor: Actor, distance: Float, reason: String) {
        var next = editor.addActor(actor)
        val camera = next.project.cameras.firstOrNull { it.id == next.project.activeCameraId }
        if (camera != null) {
            val focus = actor.transform.position.copy(y = actor.transform.position.y + 0.8f)
            next = next.updateActiveCamera(camera.copy(
                position = Vec3(focus.x, focus.y + 0.25f, focus.z + distance),
                target = focus,
            ))
        }
        applyEditor(next, reason)
        showAddSheet = false
        activeSheet = null
        saveStatus = "Unsaved changes"
    }

    val starterAssets = remember {
        PrototypeScene.starterAssets()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            importStatus = "Import cancelled"
        } else {
            val requestedKind = importKind
            importStatus = "Importing model…"
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
                        libraryAssets = assetLibrary.list()
                        importStatus = "Added ${imported.actor.name} to My Assets"
                        saveStatus = "Unsaved changes"
                    },
                    onFailure = { error ->
                        importStatus = "Could not import this model. Choose a GLB, VRM, or self-contained glTF file."
                        Log.w(RUNTIME_LOG_TAG, "asset-import-failed", error)
                    },
                )
            }
        }
    }

    val referenceImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.onFailure {
                Log.w(RUNTIME_LOG_TAG, "reference-persist-permission-failed uri=${uri.scheme}", it)
            }
            val displayName = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: "Reference ${editor.project.referenceImages.size + 1}"
            val id = "reference-" + UUID.randomUUID().toString().replace("-", "").take(12)
            val reference = ReferenceImage(
                id = id,
                name = displayName.take(80),
                persistedUri = uri.toString(),
                opacity = 0.7f,
                transform = Transform(
                    position = Vec3(0f, 1f, -1f),
                    scale = Vec3(2f, 2f, 2f),
                ),
            )
            applyEditor(editor.addReferenceImage(reference), "reference-add")
            selectedReferenceId = id
            activeSheet = "reference"
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        if (uri != null) {
            exportTargetUri = uri
            exportInProgress = true
            exportStatus = "Preparing image…"
            referenceMode = true
        }
    }

    LaunchedEffect(exportInProgress, exportTargetUri) {
        val destination = exportTargetUri
        if (!exportInProgress || destination == null) return@LaunchedEffect
        // Let the file picker disappear and let Compose render one fully clean viewport frame.
        withFrameNanos { }
        withFrameNanos { }
        val activity = context.findActivity()
        val failure = if (activity == null) {
            IllegalStateException("No activity window")
        } else {
            runCatching { captureWindowPng(activity, destination) }.exceptionOrNull()
        }
        exportInProgress = false
        exportTargetUri = null
        exportStatus = if (failure == null) "PNG saved" else "Could not save PNG"
        if (failure == null) {
            Log.i(RUNTIME_LOG_TAG, "export-png-complete project=${editor.project.id}")
        } else {
            Log.e(RUNTIME_LOG_TAG, "export-png-failed project=${editor.project.id}", failure)
        }
    }

    val handleAssetLoaded: (String) -> Unit = { name ->
        assetStatus = "Ready · $name"
        Log.i(RUNTIME_LOG_TAG, "asset-loaded name=$name")
    }
    val handleAssetFailed: (String) -> Unit = { message ->
        val failedActor = editor.project.actors.firstOrNull {
            it.asset?.relativePath?.let(message::contains) == true
        }
        assetStatus = "Couldn't load ${failedActor?.name ?: "this model"}. Try a GLB with embedded textures."
        Log.e(RUNTIME_LOG_TAG, "asset-failed $message")
        failedActor?.asset?.assetId?.let { assetId ->
                scope.launch {
                    withContext(Dispatchers.IO) { assetLibrary.updateRig(assetId, RigCompatibility.UNSUPPORTED, 0) }
                    libraryAssets = withContext(Dispatchers.IO) { assetLibrary.list() }
                }
            }
    }
    val handleRendererFrame: () -> Unit = {
        Log.i(RUNTIME_LOG_TAG, "renderer-first-frame")
    }
    val handleSave: () -> Unit = {
        if (!saveInProgress) {
            val snapshot = editor.project
            saveInProgress = true
            saveStatus = "Saving scene"
            scope.launch {
                val failure = withContext(Dispatchers.IO) {
                    try {
                        saveMutex.withLock { onSave(snapshot) }
                        null
                    } catch (error: Exception) {
                        error
                    }
                }
                saveInProgress = false
                saveStatus = if (failure == null) "Saved scene" else "Could not save scene"
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
                    try {
                        saveMutex.withLock { onSave(snapshot) }
                        null
                    } catch (error: Exception) {
                        error
                    }
                }
                if (failure == null) {
                    saveStatus = "Saved scene"
                    onExitToBrowser()
                } else {
                    saveStatus = "Could not save scene"
                    Log.e(RUNTIME_LOG_TAG, "scene-exit-save-failed project=${snapshot.id}", failure)
                }
            }
        }
    }
    BackHandler(onBack = {
        when {
            timelinePlaying -> {
                timelinePlaying = false
                timelineTime = 0f
            }
            referenceMode -> referenceMode = false
            activeSheet != null -> activeSheet = null
            else -> handleExitToBrowser()
        }
    })

    Surface(
        modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
        color = StudioBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            SceneViewport(
                project = if (timelinePlaying || activeSheet == "motion") {
                    editor.project.evaluateTimeline(timelineTime)
                } else {
                    editor.project
                },
                selectedActorId = editor.selectedActorId,
                modifier = Modifier.fillMaxSize().testTag("scene-viewport"),
                onSelectActor = { applyEditor(editor.selectActor(it), "viewport-select") },
                onCameraGestureCommitted = { position, target ->
                    editor.project.cameras.firstOrNull { it.id == editor.project.activeCameraId }?.let { camera ->
                        applyEditor(
                            editor.updateActiveCamera(camera.copy(position = position, target = target)),
                            "camera-gesture",
                        )
                    }
                },
                onAssetLoaded = handleAssetLoaded,
                onAssetFailed = handleAssetFailed,
                onRigDiscovered = { actorId, definition ->
                    rigMessages = rigMessages - actorId
                    val next = editor.withDiscoveredRig(actorId, definition)
                    applyEditor(next, "rig-discovered")
                    val compatibility = if (definition.bones.any { RigSemantics.label(it.name) != it.name }) {
                        RigCompatibility.POSEABLE
                    } else if (definition.bones.isNotEmpty() || definition.morphTargets.isNotEmpty()) {
                        RigCompatibility.POSEABLE_CUSTOM_RIG
                    } else {
                        RigCompatibility.STATIC
                    }
                    editor.project.actors.firstOrNull { it.id == actorId }?.asset?.assetId?.let { assetId ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                assetLibrary.updateRig(
                                    assetId = assetId,
                                    compatibility = compatibility,
                                    boneCount = definition.bones.size,
                                    fingerJointCount = RigSemantics.fingerBones(definition.bones).size,
                                    morphTargetCount = definition.morphTargets.size,
                                )
                            }
                            libraryAssets = withContext(Dispatchers.IO) { assetLibrary.list() }
                        }
                    }
                },
                onAnimationsDiscovered = { actorId, clips ->
                    val next = editor.withDiscoveredAnimations(actorId, clips)
                    applyEditor(next, "animations-discovered")
                },
                onRigUnavailable = { actorId, message ->
                    rigMessages = rigMessages + (actorId to message)
                    if (message.contains("no skinned joints", ignoreCase = true)) {
                        editor.project.actors.firstOrNull { it.id == actorId }?.asset?.assetId?.let { assetId ->
                            scope.launch {
                                withContext(Dispatchers.IO) { assetLibrary.updateRig(assetId, RigCompatibility.STATIC, 0) }
                                libraryAssets = withContext(Dispatchers.IO) { assetLibrary.list() }
                            }
                        }
                    }
                },
                onRigJointsUpdated = { actorId, positions -> rigJointPositions = rigJointPositions + (actorId to positions) },
                onRendererFrame = handleRendererFrame,
            )
            if (!timelinePlaying) editor.selectedActor?.takeIf { !it.locked }?.let { actor ->
                if (!referenceMode && activeSheet != "pose") {
                    ViewportTransformGizmo(
                        editor = editor,
                        onEditor = { next, reason -> applyEditor(next, reason) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (!timelinePlaying && !referenceMode && activeSheet == "pose") {
                editor.selectedActor?.takeIf { it.kind == ActorKind.CHARACTER && it.rigDefinition != null }?.let { actor ->
                    val camera = editor.project.cameras.firstOrNull { it.id == editor.project.activeCameraId }
                    val defaultBoneId = actor.rigDefinition?.bones?.firstOrNull { it.name.contains("arm", ignoreCase = true) }?.id
                        ?: actor.rigDefinition?.bones?.firstOrNull()?.id
                    val focusedJointId = selectedJointId ?: defaultBoneId
                    if (focusedJointId != null && camera != null) {
                        ViewportJointOverlay(
                            actor = actor,
                            editor = editor,
                            positions = rigJointPositions[actor.id].orEmpty(),
                            camera = camera,
                            selectedJointId = focusedJointId,
                            selectedAxis = selectedPoseAxis,
                            ikEnabled = poseIkEnabled,
                            onSelectJoint = { selectedJointId = it },
                            onEditor = { next, reason -> applyEditor(next, reason) },
                        )
                    }
                }
            }
            if (!referenceMode) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    color = Color(0xEE1D232B),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = handleExitToBrowser, modifier = Modifier.size(40.dp).testTag("back-to-projects")) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Projects", tint = PrimaryText)
                        }
                        IconButton(onClick = { applyEditor(editor.undo(), "undo") }, enabled = editor.canUndo, modifier = Modifier.size(40.dp).testTag("undo")) {
                            Icon(Icons.Default.Undo, contentDescription = "Undo", tint = if (editor.canUndo) PrimaryText else MutedText.copy(alpha = .45f))
                        }
                        IconButton(onClick = { applyEditor(editor.redo(), "redo") }, enabled = editor.canRedo, modifier = Modifier.size(40.dp).testTag("redo")) {
                            Icon(Icons.Default.Redo, contentDescription = "Redo", tint = if (editor.canRedo) PrimaryText else MutedText.copy(alpha = .45f))
                        }
                        IconButton(onClick = { activeSheet = "hierarchy" }, modifier = Modifier.size(40.dp).testTag("scene-hierarchy")) {
                            Icon(Icons.Default.AccountTree, contentDescription = "Scene", tint = PrimaryText)
                        }
                        IconButton(onClick = { activeSheet = "inspector" }, modifier = Modifier.size(40.dp).testTag("inspector")) {
                            Icon(Icons.Default.Tune, contentDescription = "Inspector", tint = PrimaryText)
                        }
                        IconButton(onClick = handleSave, modifier = Modifier.size(40.dp).testTag("save-project")) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = PrimaryText)
                        }
                        IconButton(onClick = { referenceMode = true }, modifier = Modifier.size(40.dp).testTag("reference-mode")) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Clean reference view", tint = PrimaryText)
                        }
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    color = Color(0xEE1D232B),
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 0.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = Modifier.weight(1f).padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                            horizontalArrangement = if (showingMoreTools) {
                                Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)
                            } else Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!showingMoreTools) {
                                EditorTool("Add", Icons.Default.Add, false, "add-object") { showAddSheet = true; Log.i(RUNTIME_LOG_TAG, "add-sheet-open") }
                                EditorTool("Select", Icons.Default.TouchApp, editor.selectedActorId == null, "tool-select") { applyEditor(editor.selectActor(null), "deselect") }
                                listOf(TransformTool.MOVE, TransformTool.ROTATE, TransformTool.SCALE).forEach { tool ->
                                    val label = tool.name.lowercase().replaceFirstChar { it.uppercase() }
                                    val icon = when (tool) {
                                        TransformTool.MOVE -> Icons.Default.OpenWith
                                        TransformTool.ROTATE -> Icons.Default.RotateRight
                                        TransformTool.SCALE -> Icons.Default.AspectRatio
                                    }
                                    EditorTool(label, icon, editor.activeTool == tool, "tool-${tool.name.lowercase()}") {
                                        applyEditor(editor.useTool(tool), "tool")
                                    }
                                }
                            } else {
                                EditorTool("Pose", Icons.Default.AccessibilityNew, false, "pose-tools") {
                                    if (editor.selectedActor?.animation?.playing == true) {
                                        applyEditor(editor.setSelectedAnimationPlaying(false), "pose-stops-animation")
                                    }
                                    activeSheet = "pose"
                                }
                                EditorTool("Animate", Icons.Default.PlayArrow, false, "motion-tools") { activeSheet = "motion" }
                                EditorTool("Reference", Icons.Default.Image, false, "reference-tools") {
                                    if (selectedReferenceId == null) {
                                        selectedReferenceId = editor.project.referenceImages.firstOrNull()?.id
                                    }
                                    activeSheet = "reference"
                                }
                                EditorTool("Camera", Icons.Default.CameraAlt, false, "camera-tools") { activeSheet = "camera" }
                                EditorTool("Light", Icons.Default.LightMode, false, "light-tools") {
                                    if (editor.selectedActor?.kind != ActorKind.LIGHT) {
                                        editor.project.actors.firstOrNull { it.kind == ActorKind.LIGHT }?.let { light ->
                                            applyEditor(editor.selectActor(light.id), "light-select")
                                        }
                                    }
                                    activeSheet = "light"
                                }
                            }
                        }
                        IconButton(
                            onClick = { showingMoreTools = !showingMoreTools },
                            modifier = Modifier.size(48.dp).testTag("tool-rail-page"),
                        ) {
                            Icon(
                                if (!showingMoreTools) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                                contentDescription = if (!showingMoreTools) "More tools" else "Core tools",
                                tint = PrimaryText,
                            )
                        }
                    }
                }
                if (assetStatus.startsWith("Couldn't load") || importStatus.startsWith("Could not import")) {
                    Text(
                        importStatus.ifBlank { assetStatus },
                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 58.dp, start = 12.dp, end = 12.dp).testTag("asset-status"),
                        color = Color(0xFFFFB4AB), fontSize = 11.sp,
                    )
                }
                if (saveStatus != "New scene" && saveStatus != "Restored saved scene") {
                    Text(saveStatus, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 76.dp).testTag("save-status"), color = MutedText, fontSize = 10.sp)
                }
                if (timelinePlaying) {
                    Button(
                        onClick = {
                            timelinePlaying = false
                            timelineTime = 0f
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                            .padding(bottom = 78.dp).testTag("timeline-stop"),
                    ) {
                        Text("${String.format(Locale.US, "%.1f", timelineTime)} s · Stop")
                    }
                }
            } else if (!exportInProgress) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
                    color = PanelBackground,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                exportStatus = ""
                                exportLauncher.launch(scenePngFilename(editor.project.name))
                            },
                            modifier = Modifier.testTag("export-scene-png"),
                        ) { Text("Export PNG") }
                        Button(
                            onClick = {
                                exportStatus = ""
                                referenceMode = false
                            },
                            modifier = Modifier.testTag("exit-reference-mode"),
                        ) { Text("Edit scene") }
                    }
                }
                if (exportStatus.isNotBlank()) {
                    Text(
                        exportStatus,
                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp)
                            .testTag("export-status"),
                        color = if (exportStatus == "PNG saved") PrimaryText else Color(0xFFFFB4AB),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddObjectSheet(
            selectedKind = importKind,
            selectedTab = assetBrowserTab,
            starterAssets = starterAssets,
            libraryAssets = libraryAssets,
            onTabSelected = { assetBrowserTab = it },
            onKindSelected = { importKind = it },
            onAddStarter = { template ->
                val positionX = when (template.kind) {
                    ActorKind.CHARACTER -> (editor.project.actors.filter { it.kind == ActorKind.CHARACTER }.maxOfOrNull { it.transform.position.x } ?: -1.2f) + 1.2f
                    else -> -1.2f + editor.project.actors.count { it.kind == template.kind } * 0.8f
                }
                val id = "starter-" + UUID.randomUUID().toString().replace("-", "").take(12)
                val actor = template.copy(
                    id = id,
                    name = template.name.substringBefore(" ·").take(48),
                    transform = Transform(position = Vec3(positionX, 0f, 0f)),
                    rigDefinition = null,
                    rig = null,
                )
                addAndFrame(actor, if (actor.kind == ActorKind.CHARACTER) 3.8f else 3f, "add-starter")
            },
            onAddLibraryAsset = { record ->
                val kind = runCatching { ActorKind.valueOf(record.category.uppercase()) }.getOrDefault(importKind)
                val x = if (kind == ActorKind.CHARACTER) {
                    (editor.project.actors.filter { it.kind == ActorKind.CHARACTER }.maxOfOrNull { it.transform.position.x } ?: -1.2f) + 1.2f
                } else -1.2f + editor.project.actors.count { it.kind == kind } * 0.8f
                val actor = record.actor(kind, "library-" + UUID.randomUUID().toString().replace("-", "").take(12))
                    .copy(transform = Transform(position = Vec3(x, 0f, 0f)))
                val distance = when (kind) {
                    ActorKind.CHARACTER -> 3.8f
                    ActorKind.VEHICLE -> 4f
                    ActorKind.ENVIRONMENT -> 6f
                    else -> 3f
                }
                addAndFrame(actor, distance, "add-library-asset")
            },
            onDeleteLibraryAsset = { record ->
                if (editor.project.actors.none { it.asset?.assetId == record.assetId }) {
                    assetLibrary.delete(record.assetId)
                    libraryAssets = assetLibrary.list()
                }
            },
            canDeleteLibraryAsset = { record -> editor.project.actors.none { it.asset?.assetId == record.assetId } },
            onAddLight = { type ->
                val id = "light-" + UUID.randomUUID().toString().replace("-", "").take(12)
                val (name, intensity) = when (type) {
                    LightType.POINT -> "Point Light" to 2_200f
                    LightType.SPOT -> "Spot Light" to 3_500f
                    LightType.DIRECTIONAL -> "Sun Light" to 72_000f
                }
                val lightActor = Actor(
                    id = id,
                    name = name,
                    kind = ActorKind.LIGHT,
                    transform = studio.artistscene.core.Transform(position = Vec3(1.5f, 2f, 1f)),
                    light = LightSettings(
                        type = type,
                        intensity = intensity,
                        rangeMeters = if (type == LightType.SPOT) 8f else 5f,
                    ),
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
                // Android 8.0 DocumentsUI predates the registered GLB/VRM MIME types and can
                // hide perfectly valid models when ACTION_OPEN_DOCUMENT is constrained by
                // EXTRA_MIME_TYPES. Let SAF show files and enforce the real format/size policy
                // after selection by inspecting the staged bytes.
                importLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { showAddSheet = false },
        )
    }
    activeSheet?.let { sheet ->
        EditorContextSheet(
            sheet = sheet,
            editor = editor,
            rigMessage = editor.selectedActor?.id?.let(rigMessages::get),
            selectedJointId = selectedJointId,
            selectedAxis = selectedPoseAxis,
            poseIkEnabled = poseIkEnabled,
            selectedReferenceId = selectedReferenceId,
            onReferenceSelected = { selectedReferenceId = it },
            onReferenceImport = { referenceImageLauncher.launch(arrayOf("image/*")) },
            onJointSelected = { selectedJointId = it },
            onAxisSelected = { selectedPoseAxis = it },
            onPoseIkEnabledChange = { poseIkEnabled = it },
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
            timelineTime = timelineTime,
            timelinePlaying = timelinePlaying,
            onTimelineTimeChange = { requested ->
                timelineTime = requested.coerceIn(0f, editor.project.timeline.durationSeconds)
            },
            onTimelinePlayingChange = { playing ->
                if (playing && timelineTime >= editor.project.timeline.durationSeconds) timelineTime = 0f
                timelinePlaying = playing
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
    var draggingAxis by remember(editor.activeTool, editor.selectedActorId) { mutableStateOf<TransformAxis?>(null) }
    val offsets = when (editor.activeTool) {
        TransformTool.MOVE, TransformTool.SCALE -> mapOf(
            TransformAxis.X to androidx.compose.ui.unit.IntOffset(56, 0),
            TransformAxis.Y to androidx.compose.ui.unit.IntOffset(0, -56),
            TransformAxis.Z to androidx.compose.ui.unit.IntOffset(40, 40),
        )
        TransformTool.ROTATE -> mapOf(
            TransformAxis.X to androidx.compose.ui.unit.IntOffset(48, -28),
            TransformAxis.Y to androidx.compose.ui.unit.IntOffset(-48, -28),
            TransformAxis.Z to androidx.compose.ui.unit.IntOffset(0, 54),
        )
    }
    val colors = mapOf(
        TransformAxis.X to Color(0xFFE66A6A),
        TransformAxis.Y to Color(0xFF68C98A),
        TransformAxis.Z to Color(0xFF6A9EFF),
    )
    BoxWithConstraints(modifier) {
        val actor = editor.selectedActor
        val camera = editor.project.cameras.firstOrNull { it.id == editor.project.activeCameraId }
        val pivotOffset = if (actor != null && camera != null) {
            projectActorPivot(actor.transform.position, camera, maxWidth, maxHeight)
        } else Offset.Zero
        Canvas(Modifier.align(Alignment.Center).offset(x = pivotOffset.x.dp, y = pivotOffset.y.dp).size(144.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = 49.dp.toPx()
            if (editor.activeTool == TransformTool.ROTATE) {
                val arcSize = Size(radius * 2, radius * 2)
                val arcTopLeft = Offset(center.x - radius, center.y - radius)
                listOf(
                    TransformAxis.X to -55f,
                    TransformAxis.Y to 65f,
                    TransformAxis.Z to 185f,
                ).forEach { (axis, start) ->
                    drawArc(
                        color = colors.getValue(axis),
                        startAngle = start,
                        sweepAngle = 92f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            } else {
                TransformAxis.entries.forEach { axis ->
                    val offset = offsets.getValue(axis)
                    val end = Offset(center.x + offset.x.dp.toPx(), center.y + offset.y.dp.toPx())
                    drawLine(colors.getValue(axis).copy(alpha = .82f), center, end, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    if (editor.activeTool == TransformTool.MOVE) {
                        val direction = (end - center)
                        val unit = direction / direction.getDistance().coerceAtLeast(1f)
                        val head = end - unit * 14.dp.toPx()
                        val perpendicular = Offset(-unit.y, unit.x) * 4.dp.toPx()
                        drawLine(colors.getValue(axis), head + perpendicular, end, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(colors.getValue(axis), head - perpendicular, end, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
            drawCircle(Color(0xFF11161D), radius = 8.dp.toPx(), center = center)
            drawCircle(Color.White.copy(alpha = .75f), radius = 3.dp.toPx(), center = center)
        }
        TransformAxis.entries.forEach { axis ->
            val axisColor = colors.getValue(axis)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = pivotOffset.x.dp, y = pivotOffset.y.dp)
                    .offset(x = (offsets.getValue(axis).x).dp, y = (offsets.getValue(axis).y).dp)
                    .size(48.dp)
                    .testTag("gizmo-${editor.activeTool.name.lowercase()}-${axis.name.lowercase()}")
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .pointerInput(editor.activeTool, editor.selectedActorId) {
                        var before: SceneEditorState? = null
                        var accumulated = 0f
                        detectDragGestures(
                            onDragStart = {
                                before = latestEditor.value
                                accumulated = 0f
                                draggingAxis = axis
                            },
                            onDragEnd = {
                                before?.let { origin ->
                                    latestOnEditor.value(
                                        latestEditor.value.commitTransformGesture(origin.project),
                                        "gizmo-${latestEditor.value.activeTool.name.lowercase()}-${axis.name.lowercase()}",
                                    )
                                }
                                before = null
                                draggingAxis = null
                            },
                            onDragCancel = {
                                before?.let { origin ->
                                    latestOnEditor.value(
                                        latestEditor.value.cancelTransformGesture(origin.project),
                                        "gizmo-cancel",
                                    )
                                }
                                before = null
                                draggingAxis = null
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
                                    TransformTool.MOVE -> accumulated * 0.00625f
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
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(if (draggingAxis == axis) 30.dp else 24.dp),
                    color = axisColor.copy(alpha = if (draggingAxis == null || draggingAxis == axis) .88f else .7f),
                    shape = if (editor.activeTool == TransformTool.SCALE) RoundedCornerShape(7.dp) else CircleShape,
                    tonalElevation = 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(axis.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewportJointOverlay(
    actor: Actor,
    editor: SceneEditorState,
    positions: Map<String, Vec3>,
    camera: SceneCamera,
    selectedJointId: String,
    selectedAxis: TransformAxis,
    ikEnabled: Boolean,
    onSelectJoint: (String) -> Unit,
    onEditor: (SceneEditorState, String) -> Unit,
) {
    val latestEditor = rememberUpdatedState(editor)
    val latestOnEditor = rememberUpdatedState(onEditor)
    val latestOnSelectJoint = rememberUpdatedState(onSelectJoint)
    val latestJointPositions = rememberUpdatedState(positions)
    val latestCamera = rememberUpdatedState(camera)
    val latestSelectedJointId = rememberUpdatedState(selectedJointId)
    val latestIkEnabled = rememberUpdatedState(ikEnabled)

    BoxWithConstraints(
        Modifier.fillMaxSize().testTag("joint-viewport-overlay"),
    ) {
        val density = LocalDensity.current.density
        val viewportWidthDp = maxWidth
        val viewportHeightDp = maxHeight

        fun projectedPoint(world: Vec3): IkPoint {
            val offset = projectActorPivot(world, camera, viewportWidthDp, viewportHeightDp)
            return IkPoint(
                x = viewportWidthDp.value * density * 0.5f + offset.x * density,
                y = viewportHeightDp.value * density * 0.5f + offset.y * density,
            )
        }

        val rigBones = actor.rigDefinition?.bones.orEmpty()
        val ikEndEffectorIds = RigSemantics.ikEndEffectorIds(rigBones)

        Box(
            Modifier.fillMaxSize()
                .pointerInput(actor.id) {
                    detectTapGestures { touch ->
                        val target = nearestProjectedJointAtTouch(
                            touchPx = touch,
                            positions = latestJointPositions.value,
                            camera = latestCamera.value,
                            viewportWidthDp = viewportWidthDp,
                            viewportHeightDp = viewportHeightDp,
                            density = density,
                            fallbackId = latestSelectedJointId.value,
                        )
                        latestOnSelectJoint.value(target)
                    }
                }
                .pointerInput(actor.id, selectedAxis, ikEnabled) {
                    var before: SceneProject? = null
                    var activeBoneId: String? = null
                    var startRotation = Vec3()
                    var accumulatedDegrees = 0f

                    var ikRootId: String? = null
                    var ikMidId: String? = null
                    var ikRootStartRotation = Vec3()
                    var ikMidStartRotation = Vec3()
                    var ikRootPoint: IkPoint? = null
                    var ikMidPoint: IkPoint? = null
                    var ikEndPoint: IkPoint? = null
                    var ikTargetPoint: IkPoint? = null

                    fun clearGesture() {
                        before = null
                        activeBoneId = null
                        ikRootId = null
                        ikMidId = null
                        ikRootPoint = null
                        ikMidPoint = null
                        ikEndPoint = null
                        ikTargetPoint = null
                    }

                    detectDragGestures(
                        orientationLock = null,
                        onDragStart = { down, _, _ ->
                            // Use the original pointer-down position, not the post-touch-slop
                            // position. Dense skeleton markers can overlap on phone screens;
                            // choosing from the slop-shifted coordinate makes drag direction
                            // change which joint is selected (for example elbow -> wrist).
                            val touch = down.position
                            val availablePositions = latestJointPositions.value
                            val dragPositions = if (latestIkEnabled.value && ikEndEffectorIds.isNotEmpty()) {
                                availablePositions.filterKeys { it in ikEndEffectorIds }
                            } else {
                                availablePositions
                            }
                            val targetBoneId = nearestProjectedJointAtTouch(
                                touchPx = touch,
                                positions = dragPositions,
                                camera = latestCamera.value,
                                viewportWidthDp = maxWidth,
                                viewportHeightDp = maxHeight,
                                density = density,
                                fallbackId = latestSelectedJointId.value,
                            )
                            activeBoneId = targetBoneId
                            latestOnSelectJoint.value(targetBoneId)
                            val state = latestEditor.value
                            before = state.project
                            startRotation = state.selectedActor?.rig?.joints?.get(targetBoneId) ?: Vec3()
                            accumulatedDegrees = 0f

                            if (latestIkEnabled.value) {
                                val bones = state.selectedActor?.rigDefinition?.bones.orEmpty()
                                val endBone = bones.firstOrNull { it.id == targetBoneId }
                                val midId = endBone?.parentId
                                val rootId = midId
                                    ?.let { id -> bones.firstOrNull { it.id == id } }
                                    ?.parentId
                                val rootPosition = rootId?.let(availablePositions::get)
                                val midPosition = midId?.let(availablePositions::get)
                                val endPosition = endBone?.id?.let(availablePositions::get)
                                if (rootId != null && midId != null && rootPosition != null && midPosition != null && endPosition != null) {
                                    val rootPoint = projectedPoint(rootPosition)
                                    val midPoint = projectedPoint(midPosition)
                                    val endPoint = projectedPoint(endPosition)
                                    if (TwoBoneIk.solve(rootPoint, midPoint, endPoint, endPoint) != null) {
                                        ikRootId = rootId
                                        ikMidId = midId
                                        ikRootStartRotation = state.selectedActor?.rig?.joints?.get(rootId) ?: Vec3()
                                        ikMidStartRotation = state.selectedActor?.rig?.joints?.get(midId) ?: Vec3()
                                        ikRootPoint = rootPoint
                                        ikMidPoint = midPoint
                                        ikEndPoint = endPoint
                                        ikTargetPoint = endPoint
                                    }
                                }
                            }
                        },
                        onDragEnd = { _ ->
                            before?.let { snapshot ->
                                latestOnEditor.value(
                                    latestEditor.value.commitRigGesture(snapshot),
                                    if (ikRootId != null) "pose-ik-commit" else "pose-joint-commit",
                                )
                            }
                            clearGesture()
                        },
                        onDragCancel = {
                            before?.let { snapshot ->
                                latestOnEditor.value(
                                    latestEditor.value.cancelRigGesture(snapshot),
                                    if (ikRootId != null) "pose-ik-cancel" else "pose-joint-cancel",
                                )
                            }
                            clearGesture()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val targetBoneId = activeBoneId
                            if (before != null && targetBoneId != null) {
                                val rootId = ikRootId
                                val midId = ikMidId
                                val rootPoint = ikRootPoint
                                val midPoint = ikMidPoint
                                val endPoint = ikEndPoint
                                val currentTarget = ikTargetPoint
                                if (
                                    rootId != null && midId != null &&
                                    rootPoint != null && midPoint != null && endPoint != null && currentTarget != null
                                ) {
                                    val nextTarget = IkPoint(
                                        currentTarget.x + dragAmount.x,
                                        currentTarget.y + dragAmount.y,
                                    )
                                    ikTargetPoint = nextTarget
                                    val solution = TwoBoneIk.solve(
                                        root = rootPoint,
                                        mid = midPoint,
                                        end = endPoint,
                                        target = nextTarget,
                                    )
                                    if (solution != null) {
                                        val rootRotation = ikRootStartRotation.withAxisDegrees(
                                            selectedAxis,
                                            ikRootStartRotation.axisDegrees(selectedAxis) + solution.rootDeltaDegrees,
                                        )
                                        val midRotation = ikMidStartRotation.withAxisDegrees(
                                            selectedAxis,
                                            ikMidStartRotation.axisDegrees(selectedAxis) + solution.midDeltaDegrees,
                                        )
                                        latestOnEditor.value(
                                            latestEditor.value.previewRigJointRotations(
                                                mapOf(rootId to rootRotation, midId to midRotation),
                                            ),
                                            "pose-ik-preview",
                                        )
                                    }
                                } else {
                                    accumulatedDegrees += (dragAmount.x - dragAmount.y) * 0.55f
                                    val nextRotation = startRotation.withAxisDegrees(
                                        selectedAxis,
                                        startRotation.axisDegrees(selectedAxis) + accumulatedDegrees,
                                    )
                                    latestOnEditor.value(
                                        latestEditor.value.previewRigJointRotation(targetBoneId, nextRotation),
                                        "pose-joint-preview",
                                    )
                                }
                            }
                        },
                    )
                },
        ) {
            positions.forEach { (boneId, worldPosition) ->
                val bone = actor.rigDefinition?.bones?.firstOrNull { it.id == boneId } ?: return@forEach
                val screenOffset = projectActorPivot(worldPosition, camera, viewportWidthDp, viewportHeightDp)
                val selected = selectedJointId == boneId
                val ikHandle = ikEnabled && boneId in ikEndEffectorIds
                Box(
                    modifier = Modifier.align(Alignment.Center)
                        .offset(x = screenOffset.x.dp, y = screenOffset.y.dp)
                        .size(46.dp)
                        .testTag("joint-marker-${RigSemantics.tag(bone.name)}")
                        .semantics {
                            onClick(label = "Select ${RigSemantics.label(bone, rigBones)}") {
                                latestOnSelectJoint.value(boneId)
                                true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.size(
                            when {
                                selected -> 16.dp
                                ikHandle -> 14.dp
                                else -> 11.dp
                            },
                        ),
                        color = when {
                            selected -> Color(0xFFFFD166)
                            ikHandle -> Color(0xFF8CC8FF)
                            else -> Color(0xFF18212B)
                        },
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (selected || ikHandle) Color.White else Color(0xFFE8EEF5),
                        ),
                        tonalElevation = 0.dp,
                    ) { }
                }
            }
        }
    }
}

private fun nearestProjectedJointAtTouch(
    touchPx: Offset,
    positions: Map<String, Vec3>,
    camera: SceneCamera,
    viewportWidthDp: androidx.compose.ui.unit.Dp,
    viewportHeightDp: androidx.compose.ui.unit.Dp,
    density: Float,
    fallbackId: String,
): String {
    if (positions.isEmpty()) return fallbackId
    val centerXPx = viewportWidthDp.value * density * 0.5f
    val centerYPx = viewportHeightDp.value * density * 0.5f
    return positions.keys.minByOrNull { candidate ->
        val point = projectActorPivot(
            positions.getValue(candidate),
            camera,
            viewportWidthDp,
            viewportHeightDp,
        )
        val dx = centerXPx + point.x * density - touchPx.x
        val dy = centerYPx + point.y * density - touchPx.y
        dx * dx + dy * dy
    } ?: fallbackId
}

private fun projectActorPivot(position: Vec3, camera: SceneCamera, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp): Offset {
    val fx0 = camera.target.x - camera.position.x
    val fy0 = camera.target.y - camera.position.y
    val fz0 = camera.target.z - camera.position.z
    val fLength = sqrt(fx0 * fx0 + fy0 * fy0 + fz0 * fz0).coerceAtLeast(0.001f)
    val fx = fx0 / fLength
    val fy = fy0 / fLength
    val fz = fz0 / fLength
    val rx0 = -fz
    val rz0 = fx
    val rLength = sqrt(rx0 * rx0 + rz0 * rz0).coerceAtLeast(0.001f)
    val rx = rx0 / rLength
    val rz = rz0 / rLength
    val ux = -rz * fy
    val uy = rz * fx - rx * fz
    val uz = rx * fy
    val dx = position.x - camera.position.x
    val dy = position.y - camera.position.y
    val dz = position.z - camera.position.z
    val depth = (dx * fx + dy * fy + dz * fz).coerceAtLeast(0.15f)
    val focal = when (camera.projection) {
        studio.artistscene.core.CameraProjection.PERSPECTIVE ->
            height.value * 0.5f / tan(Math.toRadians(camera.verticalFovDegrees.toDouble() * 0.5)).toFloat()
        studio.artistscene.core.CameraProjection.ORTHOGRAPHIC -> height.value / camera.orthographicHeightMeters.coerceAtLeast(0.1f)
    }
    val projectedX = ((dx * rx + dz * rz) * focal / if (camera.projection == studio.artistscene.core.CameraProjection.PERSPECTIVE) depth else 1f)
    val projectedY = (-(dx * ux + dy * uy + dz * uz) * focal / if (camera.projection == studio.artistscene.core.CameraProjection.PERSPECTIVE) depth else 1f)
    return Offset(
        x = projectedX.coerceIn(-width.value * 0.38f, width.value * 0.38f),
        y = projectedY.coerceIn(-height.value * 0.34f, height.value * 0.34f),
    )
}

@Composable
private fun EditorTool(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF8DA7FF) else Color(0xFF303844))
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(19.dp), tint = if (selected) Color(0xFF101624) else PrimaryText)
            Text(label, maxLines = 1, fontSize = 9.sp, lineHeight = 10.sp, color = if (selected) Color(0xFF101624) else PrimaryText, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}

@Composable
private fun TimelineScrubber(
    durationSeconds: Float,
    timeSeconds: Float,
    keyTimes: List<Float>,
    enabled: Boolean,
    onTimeChange: (Float) -> Unit,
) {
    val duration = durationSeconds.coerceAtLeast(0.001f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp, max = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF171C23))
            .pointerInput(duration, enabled) {
                if (!enabled) return@pointerInput
                fun updateFromX(x: Float) {
                    val left = 12.dp.toPx()
                    val usable = (size.width.toFloat() - left * 2f).coerceAtLeast(1f)
                    val fraction = ((x - left) / usable).coerceIn(0f, 1f)
                    onTimeChange(fraction * duration)
                }
                detectDragGestures(
                    onDragStart = { updateFromX(it.x) },
                    onDrag = { change, _ ->
                        change.consume()
                        updateFromX(change.position.x)
                    },
                )
            }
            .testTag("timeline-scrubber"),
    ) {
        val inset = 12.dp.toPx()
        val usable = (size.width - inset * 2f).coerceAtLeast(1f)
        val centerY = size.height / 2f
        drawLine(
            color = MutedText.copy(alpha = 0.28f),
            start = Offset(inset, centerY),
            end = Offset(size.width - inset, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        keyTimes.distinct().forEach { keyTime ->
            val x = inset + usable * (keyTime.coerceIn(0f, duration) / duration)
            drawLine(
                color = Color(0xFFB9D8F2),
                start = Offset(x, centerY - 9.dp.toPx()),
                end = Offset(x, centerY + 9.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        val playheadX = inset + usable * (timeSeconds.coerceIn(0f, duration) / duration)
        drawLine(
            color = Color(0xFF73B7FF),
            start = Offset(playheadX, 7.dp.toPx()),
            end = Offset(playheadX, size.height - 7.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = Color(0xFF73B7FF),
            radius = 4.dp.toPx(),
            center = Offset(playheadX, centerY),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorContextSheet(
    sheet: String,
    editor: SceneEditorState,
    rigMessage: String?,
    selectedJointId: String?,
    selectedAxis: TransformAxis,
    poseIkEnabled: Boolean,
    selectedReferenceId: String?,
    onReferenceSelected: (String?) -> Unit,
    onReferenceImport: () -> Unit,
    onJointSelected: (String?) -> Unit,
    onAxisSelected: (TransformAxis) -> Unit,
    onPoseIkEnabledChange: (Boolean) -> Unit,
    onEditor: (SceneEditorState, String) -> Unit,
    onClose: () -> Unit,
    saveStatus: String,
    onFrameSelected: () -> Unit,
    onFrameScene: () -> Unit,
    onResetCamera: () -> Unit,
    timelineTime: Float,
    timelinePlaying: Boolean,
    onTimelineTimeChange: (Float) -> Unit,
    onTimelinePlayingChange: (Boolean) -> Unit,
) {
    if (sheet == "pose") {
        PoseControlsOverlay(
            editor = editor,
            rigMessage = rigMessage,
            selectedJointId = selectedJointId,
            selectedAxis = selectedAxis,
            ikEnabled = poseIkEnabled,
            onJointSelected = onJointSelected,
            onAxisSelected = onAxisSelected,
            onIkEnabledChange = onPoseIkEnabledChange,
            onEditor = onEditor,
            onClose = onClose,
            saveStatus = saveStatus,
        )
        return
    }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = PanelBackground) {
        Box(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when (sheet) {
                        "hierarchy" -> "Scene"
                        "inspector" -> "Inspector"
                        "pose" -> "Pose"
                        "motion" -> "Animation"
                        "reference" -> "Reference"
                        "camera" -> "Camera"
                        else -> "Lighting"
                    },
                    modifier = Modifier.weight(1f),
                    color = PrimaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            when (sheet) {
                "hierarchy" -> {
                    val actors = editor.project.actors
                    actors.sortedWith(compareBy<Actor>({ hierarchyDepth(it, actors) }, { it.name.lowercase() })).forEach { actor ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width((hierarchyDepth(actor, actors) * 12).dp))
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
                        // Parenting is a hierarchy operation, so keep it ahead of rename/edit
                        // fields. Besides being quicker to reach on a phone-sized sheet, this
                        // prevents a vertical hierarchy gesture from accidentally focusing the
                        // object-name field and raising the IME before parent controls are visible.
                        Text("Parent", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = actor.parentId == null,
                                onClick = { onEditor(editor.reparentSelected(null), "reparent") },
                                enabled = editor.canReparentSelected(null),
                                label = { Text("Scene root") },
                                modifier = Modifier.testTag("parent-scene-root"),
                            )
                            actors.filter { it.id != actor.id }.forEach { candidate ->
                                FilterChip(
                                    selected = actor.parentId == candidate.id,
                                    onClick = { onEditor(editor.reparentSelected(candidate.id), "reparent") },
                                    enabled = editor.canReparentSelected(candidate.id),
                                    label = { Text(candidate.name, maxLines = 1) },
                                    modifier = Modifier.testTag("parent-${candidate.id}"),
                                )
                            }
                        }
                        var name by remember(actor.id, actor.name) { mutableStateOf(actor.name) }
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Object name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("actor-name"))
                        Button(onClick = { onEditor(editor.renameSelected(name), "rename"); onClose() }, modifier = Modifier.fillMaxWidth().testTag("rename-actor")) { Text("Rename") }
                        SelectedActorActions(editor, actor, onEditor)
                    }
                }
                "inspector" -> editor.selectedActor?.let { actor ->
                    Text(
                        listOfNotNull(
                            actor.kind.name.lowercase().replaceFirstChar { it.uppercase() },
                            actor.asset?.creator,
                        ).joinToString(" · "),
                        color = MutedText,
                        fontSize = 12.sp,
                    )
                    SelectedActorActions(editor, actor, onEditor)
                    TransformInspector(editor, actor, onEditor)
                } ?: Text("Select an object to inspect it.", color = MutedText)
                "pose" -> {
                    Text("Joint posing is available in the viewport mode.", color = MutedText)
                }
                "motion" -> {
                    val actor = editor.selectedActor
                    if (actor == null) {
                        Text("Select an object to animate.", color = MutedText, fontSize = 12.sp)
                    } else {
                        val duration = editor.project.timeline.durationSeconds
                        val keyTimes = editor.project.transformKeyTimes(actor.id)
                        val rigKeyTimes = editor.project.poseKeyTimes(actor.id)
                        val poseDefinition = actor.rigDefinition
                        val canAuthorPose = actor.kind == ActorKind.CHARACTER &&
                            poseDefinition != null &&
                            (poseDefinition.bones.isNotEmpty() || poseDefinition.morphTargets.isNotEmpty())
                        val sceneHasKeys = editor.project.tracks.any { it.enabled && it.keyframes.isNotEmpty() }
                        Text("Scene timeline", color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${String.format(Locale.US, "%.2f", timelineTime)} s / ${String.format(Locale.US, "%.2f", duration)} s",
                            color = MutedText,
                            fontSize = 12.sp,
                            modifier = Modifier.testTag("timeline-time"),
                        )
                        TimelineScrubber(
                            durationSeconds = duration,
                            timeSeconds = timelineTime,
                            keyTimes = (keyTimes + rigKeyTimes).distinct().sorted(),
                            enabled = !timelinePlaying,
                            onTimeChange = onTimelineTimeChange,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onTimelineTimeChange(timelineTime - 0.25f) },
                                enabled = !timelinePlaying,
                                modifier = Modifier.weight(1f).testTag("timeline-back"),
                            ) { Text("-0.25 s") }
                            Button(
                                onClick = { onTimelineTimeChange(timelineTime + 0.25f) },
                                enabled = !timelinePlaying,
                                modifier = Modifier.weight(1f).testTag("timeline-forward"),
                            ) { Text("+0.25 s") }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onTimelinePlayingChange(!timelinePlaying) },
                                enabled = sceneHasKeys,
                                modifier = Modifier.weight(1f).testTag("timeline-play"),
                            ) { Text(if (timelinePlaying) "Stop" else "Play scene") }
                            Button(
                                onClick = { onEditor(editor.keySelectedTransform(timelineTime), "timeline-key-transform") },
                                enabled = !timelinePlaying && !actor.locked,
                                modifier = Modifier.weight(1f).testTag("timeline-key-transform"),
                            ) { Text("Key transform") }
                        }
                        if (keyTimes.isNotEmpty()) {
                            Text(
                                "Keys · " + keyTimes.joinToString("  ") { "${String.format(Locale.US, "%.2f", it)}s" },
                                color = Color(0xFFB9D8F2),
                                fontSize = 11.sp,
                                modifier = Modifier.testTag("timeline-key-list"),
                            )
                            Button(
                                onClick = { onEditor(editor.removeSelectedTransformKeyframe(timelineTime), "timeline-remove-key") },
                                enabled = !timelinePlaying && keyTimes.any { kotlin.math.abs(it - timelineTime) <= 0.001f },
                                modifier = Modifier.fillMaxWidth().testTag("timeline-remove-key"),
                            ) { Text("Remove key at playhead") }
                        } else {
                            Text(
                                "Set the playhead, move or rotate the object, then key its transform.",
                                color = MutedText,
                                fontSize = 11.sp,
                            )
                        }
                        if (canAuthorPose) {
                            Text("Character pose", color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { onEditor(editor.keySelectedPose(timelineTime), "timeline-key-pose") },
                                    enabled = !timelinePlaying && !actor.locked && !actor.animation.playing,
                                    modifier = Modifier.weight(1f).testTag("timeline-key-pose"),
                                ) { Text("Key pose") }
                                Button(
                                    onClick = { onEditor(editor.removeSelectedPoseKeyframe(timelineTime), "timeline-remove-pose-key") },
                                    enabled = !timelinePlaying &&
                                        rigKeyTimes.any { kotlin.math.abs(it - timelineTime) <= 0.001f },
                                    modifier = Modifier.weight(1f).testTag("timeline-remove-pose-key"),
                                ) { Text("Remove pose key") }
                            }
                            if (rigKeyTimes.isNotEmpty()) {
                                Text(
                                    "Pose keys · " + rigKeyTimes.joinToString("  ") {
                                        "${String.format(Locale.US, "%.2f", it)}s"
                                    },
                                    color = Color(0xFFB9D8F2),
                                    fontSize = 11.sp,
                                    modifier = Modifier.testTag("timeline-pose-key-list"),
                                )
                            } else {
                                Text(
                                    if (actor.animation.playing) {
                                        "Pause the model animation before keying an authored character pose."
                                    } else {
                                        "Pose the character, set the playhead, then key the complete rig."
                                    },
                                    color = MutedText,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = editor.project.timeline.loop,
                                onClick = { onEditor(editor.toggleTimelineLoop(), "timeline-loop") },
                                enabled = !timelinePlaying,
                                label = { Text(if (editor.project.timeline.loop) "Loop" else "Once") },
                                modifier = Modifier.testTag("timeline-loop"),
                            )
                            FilterChip(
                                selected = editor.project.timeline.playbackSpeed == 0.5f,
                                onClick = { onEditor(editor.setTimelinePlaybackSpeed(0.5f), "timeline-speed") },
                                enabled = !timelinePlaying,
                                label = { Text("0.5×") },
                            )
                            FilterChip(
                                selected = editor.project.timeline.playbackSpeed == 1f,
                                onClick = { onEditor(editor.setTimelinePlaybackSpeed(1f), "timeline-speed") },
                                enabled = !timelinePlaying,
                                label = { Text("1×") },
                            )
                            FilterChip(
                                selected = editor.project.timeline.playbackSpeed == 2f,
                                onClick = { onEditor(editor.setTimelinePlaybackSpeed(2f), "timeline-speed") },
                                enabled = !timelinePlaying,
                                label = { Text("2×") },
                            )
                        }
                        Text("Duration", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onEditor(editor.setTimelineDurationSeconds(duration - 1f), "timeline-duration") },
                                enabled = !timelinePlaying,
                                modifier = Modifier.weight(1f).testTag("timeline-duration-down"),
                            ) { Text("Shorter") }
                            Button(
                                onClick = { onEditor(editor.setTimelineDurationSeconds(duration + 1f), "timeline-duration") },
                                enabled = !timelinePlaying,
                                modifier = Modifier.weight(1f).testTag("timeline-duration-up"),
                            ) { Text("Longer") }
                        }

                        if (actor.asset != null) {
                            Text("Model animation", color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            if (actor.animation.clips.isEmpty()) {
                                Text("No embedded animation clips in this model.", color = MutedText, fontSize = 12.sp, modifier = Modifier.testTag("animation-empty"))
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    actor.animation.clips.forEach { clip ->
                                        FilterChip(
                                            selected = actor.animation.selectedClip == clip.name,
                                            onClick = { onEditor(editor.selectAnimationClip(clip.name), "animation-select") },
                                            label = { Text(clip.name, maxLines = 1) },
                                            modifier = Modifier.testTag("animation-clip-${clip.name.hashCode().toUInt().toString(16)}"),
                                        )
                                    }
                                }
                                val selected = actor.animation.clips.firstOrNull { it.name == actor.animation.selectedClip }
                                selected?.let { clip ->
                                    Text(
                                        "${clip.name} · ${String.format(Locale.US, "%.2f", clip.durationSeconds)} s",
                                        color = PrimaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Button(
                                        onClick = {
                                            onEditor(
                                                editor.setSelectedAnimationPlaying(!actor.animation.playing),
                                                if (actor.animation.playing) "animation-pause" else "animation-play",
                                            )
                                        },
                                        enabled = !timelinePlaying,
                                        modifier = Modifier.weight(1f).testTag("animation-toggle"),
                                    ) { Text(if (actor.animation.playing) "Pause clip" else "Play clip") }
                                    FilterChip(
                                        selected = actor.animation.loop,
                                        onClick = { onEditor(editor.toggleSelectedAnimationLoop(), "animation-loop") },
                                        enabled = !timelinePlaying,
                                        label = { Text(if (actor.animation.loop) "Loop" else "Once") },
                                        modifier = Modifier.testTag("animation-loop"),
                                    )
                                }
                                Text("Speed · ${String.format(Locale.US, "%.2f", actor.animation.speed)}×", color = MutedText, fontSize = 12.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = { onEditor(editor.setSelectedAnimationSpeed(actor.animation.speed - 0.25f), "animation-speed") },
                                        enabled = !timelinePlaying,
                                        modifier = Modifier.weight(1f).testTag("animation-speed-down"),
                                    ) { Text("Slower") }
                                    Button(
                                        onClick = { onEditor(editor.setSelectedAnimationSpeed(actor.animation.speed + 0.25f), "animation-speed") },
                                        enabled = !timelinePlaying,
                                        modifier = Modifier.weight(1f).testTag("animation-speed-up"),
                                    ) { Text("Faster") }
                                }
                            }
                        }
                    }
                }
                "reference" -> {
                    val references = editor.project.referenceImages
                    if (references.isEmpty()) {
                        Text(
                            "Import a photo, drawing, anatomy sheet, or other image as a real 3D reference plane.",
                            color = MutedText,
                            fontSize = 12.sp,
                        )
                        Button(
                            onClick = onReferenceImport,
                            modifier = Modifier.fillMaxWidth().testTag("reference-import"),
                        ) { Text("Import reference image") }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            references.forEach { reference ->
                                FilterChip(
                                    selected = selectedReferenceId == reference.id,
                                    onClick = { onReferenceSelected(reference.id) },
                                    label = { Text(reference.name, maxLines = 1) },
                                    modifier = Modifier.testTag("reference-select-${reference.id}"),
                                )
                            }
                        }
                        val reference = references.firstOrNull { it.id == selectedReferenceId }
                            ?: references.first()
                        Text(reference.name, color = PrimaryText, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = reference.visible,
                                onClick = {
                                    onEditor(editor.toggleReferenceVisibility(reference.id), "reference-visibility")
                                },
                                label = { Text(if (reference.visible) "Visible" else "Hidden") },
                                modifier = Modifier.weight(1f).testTag("reference-visibility"),
                            )
                            Button(
                                onClick = onReferenceImport,
                                modifier = Modifier.weight(1f).testTag("reference-import-more"),
                            ) { Text("Add another") }
                        }
                        Text(
                            "Opacity · ${(reference.opacity * 100f).toInt()}%",
                            color = MutedText,
                            fontSize = 12.sp,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onEditor(
                                        editor.setReferenceOpacity(reference.id, reference.opacity - 0.1f),
                                        "reference-opacity",
                                    )
                                },
                                modifier = Modifier.weight(1f).testTag("reference-opacity-down"),
                            ) { Text("Fainter") }
                            Button(
                                onClick = {
                                    onEditor(
                                        editor.setReferenceOpacity(reference.id, reference.opacity + 0.1f),
                                        "reference-opacity",
                                    )
                                },
                                modifier = Modifier.weight(1f).testTag("reference-opacity-up"),
                            ) { Text("Stronger") }
                        }
                        Text(
                            "Position · x ${String.format(Locale.US, "%.1f", reference.transform.position.x)} · y ${String.format(Locale.US, "%.1f", reference.transform.position.y)} · z ${String.format(Locale.US, "%.1f", reference.transform.position.z)}",
                            color = MutedText,
                            fontSize = 11.sp,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { onEditor(editor.translateReference(reference.id, TransformAxis.X, -0.25f), "reference-move") },
                                modifier = Modifier.weight(1f).testTag("reference-left"),
                            ) { Text("Left") }
                            Button(
                                onClick = { onEditor(editor.translateReference(reference.id, TransformAxis.X, 0.25f), "reference-move") },
                                modifier = Modifier.weight(1f).testTag("reference-right"),
                            ) { Text("Right") }
                            Button(
                                onClick = { onEditor(editor.translateReference(reference.id, TransformAxis.Y, 0.25f), "reference-move") },
                                modifier = Modifier.weight(1f).testTag("reference-up"),
                            ) { Text("Up") }
                            Button(
                                onClick = { onEditor(editor.translateReference(reference.id, TransformAxis.Y, -0.25f), "reference-move") },
                                modifier = Modifier.weight(1f).testTag("reference-down"),
                            ) { Text("Down") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { onEditor(editor.translateReference(reference.id, TransformAxis.Z, -0.25f), "reference-depth") },
                                modifier = Modifier.weight(1f).testTag("reference-back"),
                            ) { Text("Back") }
                            Button(
                                onClick = { onEditor(editor.translateReference(reference.id, TransformAxis.Z, 0.25f), "reference-depth") },
                                modifier = Modifier.weight(1f).testTag("reference-forward"),
                            ) { Text("Forward") }
                            Button(
                                onClick = { onEditor(editor.scaleReference(reference.id, -0.25f), "reference-scale") },
                                modifier = Modifier.weight(1f).testTag("reference-smaller"),
                            ) { Text("Smaller") }
                            Button(
                                onClick = { onEditor(editor.scaleReference(reference.id, 0.25f), "reference-scale") },
                                modifier = Modifier.weight(1f).testTag("reference-larger"),
                            ) { Text("Larger") }
                        }
                        Button(
                            onClick = {
                                onEditor(editor.deleteReferenceImage(reference.id), "reference-delete")
                                onReferenceSelected(references.firstOrNull { it.id != reference.id }?.id)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("reference-delete"),
                        ) { Text("Remove reference") }
                        Text(
                            "References live in 3D scene space and are saved with the project. Clean reference view hides only editor chrome.",
                            color = MutedText,
                            fontSize = 11.sp,
                        )
                    }
                }
                "camera" -> {
                    val cameras = editor.project.cameras
                    val camera = cameras.firstOrNull { it.id == editor.project.activeCameraId }
                    Text("Orbit, pan and pinch framing is saved back into the active scene camera.", color = PrimaryText, fontSize = 13.sp)
                    if (cameras.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            cameras.forEach { item ->
                                FilterChip(
                                    selected = item.id == editor.project.activeCameraId,
                                    onClick = { onEditor(editor.activateCamera(item.id), "camera-activate") },
                                    label = { Text(item.name, maxLines = 1) },
                                    modifier = Modifier.testTag("camera-select-${item.id}"),
                                )
                            }
                        }
                    }
                    camera?.let { active ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterChip(
                                selected = active.projection == CameraProjection.PERSPECTIVE,
                                onClick = { onEditor(editor.setActiveCameraProjection(CameraProjection.PERSPECTIVE), "camera-projection") },
                                label = { Text("Perspective") },
                                modifier = Modifier.weight(1f).testTag("camera-perspective"),
                            )
                            FilterChip(
                                selected = active.projection == CameraProjection.ORTHOGRAPHIC,
                                onClick = { onEditor(editor.setActiveCameraProjection(CameraProjection.ORTHOGRAPHIC), "camera-projection") },
                                label = { Text("Orthographic") },
                                modifier = Modifier.weight(1f).testTag("camera-orthographic"),
                            )
                        }
                        if (active.projection == CameraProjection.PERSPECTIVE) {
                            Text("Vertical FOV · ${active.verticalFovDegrees.toInt()}°", color = MutedText, fontSize = 12.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onEditor(editor.setActiveCameraVerticalFov(active.verticalFovDegrees - 5f), "camera-fov") },
                                    modifier = Modifier.weight(1f).testTag("camera-fov-narrower"),
                                ) { Text("Narrower") }
                                Button(
                                    onClick = { onEditor(editor.setActiveCameraVerticalFov(active.verticalFovDegrees + 5f), "camera-fov") },
                                    modifier = Modifier.weight(1f).testTag("camera-fov-wider"),
                                ) { Text("Wider") }
                            }
                        } else {
                            Text("Ortho height · ${String.format(Locale.US, "%.1f", active.orthographicHeightMeters)} m", color = MutedText, fontSize = 12.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onEditor(editor.setActiveCameraOrthographicHeight(active.orthographicHeightMeters - 0.5f), "camera-ortho-height") },
                                    modifier = Modifier.weight(1f).testTag("camera-ortho-smaller"),
                                ) { Text("Closer") }
                                Button(
                                    onClick = { onEditor(editor.setActiveCameraOrthographicHeight(active.orthographicHeightMeters + 0.5f), "camera-ortho-height") },
                                    modifier = Modifier.weight(1f).testTag("camera-ortho-larger"),
                                ) { Text("Wider") }
                            }
                        }
                    }
                    Button(onClick = onFrameSelected, modifier = Modifier.fillMaxWidth().testTag("frame-selected")) { Text("Frame selected") }
                    Button(onClick = onFrameScene, modifier = Modifier.fillMaxWidth().testTag("frame-scene")) { Text("Frame scene") }
                    Button(onClick = onResetCamera, modifier = Modifier.fillMaxWidth().testTag("reset-camera")) { Text("Reset view") }
                }
                "light" -> {
                    val lights = editor.project.actors.filter { it.kind == ActorKind.LIGHT }
                    if (lights.isEmpty()) {
                        Text("No scene lights yet. Add a point, spot, or sun light from Add.", color = MutedText, fontSize = 12.sp)
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            lights.forEach { light ->
                                FilterChip(
                                    selected = editor.selectedActorId == light.id,
                                    onClick = { onEditor(editor.selectActor(light.id), "light-select") },
                                    label = { Text(light.name, maxLines = 1) },
                                    modifier = Modifier.testTag("light-select-${light.id}"),
                                )
                            }
                        }
                        val lightActor = editor.selectedActor?.takeIf { it.kind == ActorKind.LIGHT }
                        val settings = lightActor?.light
                        if (lightActor != null && settings != null) {
                            val step = if (settings.type == LightType.DIRECTIONAL) 5_000f else 250f
                            Text(
                                "${settings.type.name.lowercase().replaceFirstChar { it.uppercase() }} · ${settings.intensity.toInt()} ${if (settings.type == LightType.DIRECTIONAL) "lux" else "lm"}",
                                color = PrimaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = { onEditor(editor.setSelectedLightIntensity(settings.intensity - step), "light-intensity") },
                                    modifier = Modifier.weight(1f).testTag("light-intensity-down"),
                                ) { Text("Dimmer") }
                                Button(
                                    onClick = { onEditor(editor.setSelectedLightIntensity(settings.intensity + step), "light-intensity") },
                                    modifier = Modifier.weight(1f).testTag("light-intensity-up"),
                                ) { Text("Brighter") }
                            }
                            Text("Color", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                listOf(
                                    "White" to "#FFFFFF",
                                    "Warm" to "#FFD8B0",
                                    "Cool" to "#B8D8FF",
                                ).forEach { (label, colorHex) ->
                                    FilterChip(
                                        selected = settings.colorHex.equals(colorHex, ignoreCase = true),
                                        onClick = { onEditor(editor.setSelectedLightColorHex(colorHex), "light-color") },
                                        label = { Text(label) },
                                        modifier = Modifier.testTag("light-color-${label.lowercase()}"),
                                    )
                                }
                            }
                            FilterChip(
                                selected = settings.castsShadow,
                                onClick = { onEditor(editor.toggleSelectedLightShadows(), "light-shadows") },
                                label = { Text(if (settings.castsShadow) "Shadows on" else "Shadows off") },
                                modifier = Modifier.testTag("light-shadows"),
                            )
                            if (settings.type != LightType.DIRECTIONAL) {
                                Text("Range · ${String.format(Locale.US, "%.1f", settings.rangeMeters)} m", color = MutedText, fontSize = 12.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = { onEditor(editor.setSelectedLightRangeMeters(settings.rangeMeters - 0.5f), "light-range") },
                                        modifier = Modifier.weight(1f).testTag("light-range-down"),
                                    ) { Text("Shorter") }
                                    Button(
                                        onClick = { onEditor(editor.setSelectedLightRangeMeters(settings.rangeMeters + 0.5f), "light-range") },
                                        modifier = Modifier.weight(1f).testTag("light-range-up"),
                                    ) { Text("Longer") }
                                }
                            }
                            if (settings.type != LightType.POINT) {
                                Text("Aim", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    listOf(
                                        "Down" to Vec3(0f, -1f, 0f),
                                        "Forward" to Vec3(0f, -0.25f, -1f),
                                        "Back" to Vec3(0f, -0.25f, 1f),
                                        "Left" to Vec3(-1f, -0.25f, 0f),
                                        "Right" to Vec3(1f, -0.25f, 0f),
                                    ).forEach { (label, direction) ->
                                        FilterChip(
                                            selected = kotlin.math.abs(settings.direction.x - direction.x) < 0.01f &&
                                                kotlin.math.abs(settings.direction.y - direction.y) < 0.01f &&
                                                kotlin.math.abs(settings.direction.z - direction.z) < 0.01f,
                                            onClick = { onEditor(editor.setSelectedLightDirection(direction), "light-direction") },
                                            label = { Text(label) },
                                            modifier = Modifier.testTag("light-aim-${label.lowercase()}"),
                                        )
                                    }
                                }
                            }
                            if (settings.type == LightType.SPOT) {
                                Text(
                                    "Beam · ${settings.spotInnerConeDegrees.toInt()}° / ${settings.spotOuterConeDegrees.toInt()}°",
                                    color = MutedText,
                                    fontSize = 12.sp,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            onEditor(
                                                editor.setSelectedSpotConeDegrees(
                                                    settings.spotInnerConeDegrees - 2f,
                                                    settings.spotOuterConeDegrees - 5f,
                                                ),
                                                "spot-cone",
                                            )
                                        },
                                        modifier = Modifier.weight(1f).testTag("spot-narrower"),
                                    ) { Text("Narrower") }
                                    Button(
                                        onClick = {
                                            onEditor(
                                                editor.setSelectedSpotConeDegrees(
                                                    settings.spotInnerConeDegrees + 2f,
                                                    settings.spotOuterConeDegrees + 5f,
                                                ),
                                                "spot-cone",
                                            )
                                        },
                                        modifier = Modifier.weight(1f).testTag("spot-wider"),
                                    ) { Text("Wider") }
                                }
                            }
                            Text("Move the selected light with the normal Move tool; brightness, color, range and shadows are stored in the scene.", color = MutedText, fontSize = 11.sp)
                        } else {
                            Text("Select a light above to edit it.", color = MutedText, fontSize = 12.sp)
                        }
                    }
                }
                else -> Unit
            }
            Text(saveStatus, color = MutedText, fontSize = 10.sp)
            Spacer(Modifier.size(12.dp))
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 12.dp)
                .size(40.dp)
                .background(PanelBackground, CircleShape)
                .testTag("close-context-sheet"),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = PrimaryText)
        }
        }
    }
}

private fun hierarchyDepth(actor: Actor, actors: List<Actor>): Int {
    var depth = 0
    var parentId = actor.parentId
    val seen = mutableSetOf(actor.id)
    while (parentId != null && seen.add(parentId) && depth < 8) {
        val parent = actors.firstOrNull { it.id == parentId } ?: break
        depth++
        parentId = parent.parentId
    }
    return depth
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

private enum class AssetBrowserTab { STARTER, MY_ASSETS, IMPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddObjectSheet(
    selectedKind: ActorKind,
    selectedTab: AssetBrowserTab,
    starterAssets: List<Actor>,
    libraryAssets: List<LibraryAsset>,
    onTabSelected: (AssetBrowserTab) -> Unit,
    onKindSelected: (ActorKind) -> Unit,
    onAddStarter: (Actor) -> Unit,
    onAddLibraryAsset: (LibraryAsset) -> Unit,
    onDeleteLibraryAsset: (LibraryAsset) -> Unit,
    canDeleteLibraryAsset: (LibraryAsset) -> Boolean,
    onAddLight: (LightType) -> Unit,
    onAddCamera: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val contentScrollState = rememberScrollState()
    LaunchedEffect(selectedTab) {
        contentScrollState.scrollTo(0)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelBackground) {
        Box(Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
                .verticalScroll(contentScrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Add to scene",
                    modifier = Modifier.weight(1f),
                    color = PrimaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssetBrowserTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        label = { Text(when (tab) {
                            AssetBrowserTab.STARTER -> "Starter"
                            AssetBrowserTab.MY_ASSETS -> "My Assets"
                            AssetBrowserTab.IMPORT -> "Import"
                        }) },
                        modifier = Modifier.weight(1f).testTag("asset-tab-${tab.name.lowercase().replace('_', '-')}")
                    )
                }
            }
            when (selectedTab) {
                AssetBrowserTab.STARTER -> {
                    starterAssets.filter { it.kind == selectedKind || selectedKind == ActorKind.CHARACTER && it.kind == ActorKind.CHARACTER }
                        .forEach { actor ->
                            AssetLibraryRow(
                                title = actor.name.substringBefore(" ·"),
                                subtitle = "${actor.kind.name.lowercase().replaceFirstChar { it.uppercase() }} · ${actor.asset?.creator ?: "Mise starter"} · ${actor.asset?.license ?: "License recorded"}",
                                badge = if (actor.kind == ActorKind.CHARACTER) "Rigged starter" else "Prop",
                                onClick = { onAddStarter(actor) },
                                tag = "starter-${actor.id}",
                            )
                        }
                }
                AssetBrowserTab.MY_ASSETS -> {
                    if (libraryAssets.isEmpty()) {
                        Text("Imported models will appear here.", color = MutedText, fontSize = 12.sp)
                        Button(onClick = onImport, modifier = Modifier.fillMaxWidth().testTag("import-model")) { Text("Import a 3D model") }
                    } else {
                        libraryAssets.forEach { asset ->
                            val compatibility = when (asset.rigCompatibility) {
                                RigCompatibility.POSEABLE -> "Poseable"
                                RigCompatibility.POSEABLE_CUSTOM_RIG -> "Poseable · custom rig"
                                RigCompatibility.STATIC -> "Static"
                                RigCompatibility.UNSUPPORTED -> "Unsupported"
                                RigCompatibility.UNKNOWN -> "Checking rig"
                            }
                            AssetLibraryRow(
                                title = asset.name,
                                subtitle = listOfNotNull(asset.creator, asset.license).joinToString(" · ").ifBlank { "Imported model" },
                                badge = if (asset.rigCompatibility == RigCompatibility.UNKNOWN) {
                                    compatibility
                                } else {
                                    buildList {
                                        add(compatibility)
                                        if (asset.boneCount > 0) add("${asset.boneCount} bones")
                                        if (asset.fingerJointCount > 0) add("Hands")
                                        if (asset.morphTargetCount > 0) add("${asset.morphTargetCount} shapes")
                                    }.joinToString(" · ")
                                },
                                onClick = { onAddLibraryAsset(asset) },
                                tag = "library-asset-${asset.assetId.hashCode().toUInt().toString(16)}",
                                onDelete = { onDeleteLibraryAsset(asset) },
                                deleteEnabled = canDeleteLibraryAsset(asset),
                            )
                        }
                    }
                }
                AssetBrowserTab.IMPORT -> {
                    Text("Bring a model into My Assets", color = PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Choose a GLB, VRM, or self-contained glTF model from your device.", color = MutedText, fontSize = 12.sp)
                    Button(onClick = onImport, modifier = Modifier.fillMaxWidth().testTag("import-model")) {
                        Text(if (selectedKind == ActorKind.CHARACTER) "Import character" else "Import ${selectedKind.name.lowercase()}")
                    }
                }
            }
            Text("Model role", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(ActorKind.PROP, ActorKind.CHARACTER, ActorKind.VEHICLE, ActorKind.ENVIRONMENT).forEach { kind ->
                    FilterChip(selected = selectedKind == kind, onClick = { onKindSelected(kind) }, label = { Text(kind.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { onAddLight(LightType.POINT) }, modifier = Modifier.weight(1f).testTag("add-point-light")) { Text("Point") }
                Button(onClick = { onAddLight(LightType.SPOT) }, modifier = Modifier.weight(1f).testTag("add-spot-light")) { Text("Spot") }
                Button(onClick = { onAddLight(LightType.DIRECTIONAL) }, modifier = Modifier.weight(1f).testTag("add-directional-light")) { Text("Sun") }
            }
            Button(onClick = onAddCamera, modifier = Modifier.fillMaxWidth().testTag("add-camera")) { Text("Add camera") }
            Spacer(Modifier.size(12.dp))
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 10.dp)
                .size(40.dp)
                .background(PanelBackground, CircleShape)
                .testTag("close-add-sheet"),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = PrimaryText)
        }
        }
    }
}

@Composable
private fun AssetLibraryRow(
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
    tag: String,
    onDelete: (() -> Unit)? = null,
    deleteEnabled: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag),
        color = Color(0xFF2B323D),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MutedText, fontSize = 11.sp)
                Text(badge, color = Color(0xFFB9D8F2), fontSize = 10.sp)
            }
            Text("Add", color = Color(0xFFB9D8F2), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    enabled = deleteEnabled,
                    modifier = Modifier.size(36.dp).testTag("delete-library-asset"),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete asset", tint = if (deleteEnabled) Color(0xFFFFB4AB) else MutedText)
                }
            }
        }
    }
}

@Composable
private fun PoseControlsOverlay(
    editor: SceneEditorState,
    rigMessage: String?,
    selectedJointId: String?,
    selectedAxis: TransformAxis,
    ikEnabled: Boolean,
    onJointSelected: (String?) -> Unit,
    onAxisSelected: (TransformAxis) -> Unit,
    onIkEnabledChange: (Boolean) -> Unit,
    onEditor: (SceneEditorState, String) -> Unit,
    onClose: () -> Unit,
    saveStatus: String,
) {
    val actor = editor.selectedActor
    val bones = actor?.rigDefinition?.bones.orEmpty()
    val morphTargets = actor?.rigDefinition?.morphTargets.orEmpty()
    val fingerBones = RigSemantics.fingerBones(bones)
    val selectedBone = bones.firstOrNull { it.id == selectedJointId }
        ?: bones.firstOrNull { it.name.contains("arm", ignoreCase = true) }
        ?: bones.firstOrNull()
    val rotation = selectedBone?.let { actor?.rig?.joints?.get(it.id) } ?: Vec3()

    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp).navigationBarsPadding()
                .heightIn(
                    max = when {
                        morphTargets.isNotEmpty() -> 340.dp
                        fingerBones.isNotEmpty() -> 285.dp
                        else -> 230.dp
                    },
                ),
            color = PanelBackground,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pose", color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (actor?.kind == ActorKind.CHARACTER && (bones.isNotEmpty() || morphTargets.isNotEmpty())) {
                        Text(
                            listOfNotNull(
                                bones.takeIf { it.isNotEmpty() }?.let { "${it.size} joints" },
                                morphTargets.takeIf { it.isNotEmpty() }?.let { "${it.size} shapes" },
                            ).joinToString(" · "),
                            color = MutedText,
                            fontSize = 10.sp,
                        )
                    }
                    TextButton(onClick = onClose, modifier = Modifier.testTag("pose-done")) { Text("Done") }
                }
                if (actor?.kind == ActorKind.CHARACTER && bones.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        FilterChip(
                            selected = !ikEnabled,
                            onClick = { onIkEnabledChange(false) },
                            label = { Text("Joint") },
                            modifier = Modifier.testTag("pose-mode-joint"),
                        )
                        FilterChip(
                            selected = ikEnabled,
                            onClick = { onIkEnabledChange(true) },
                            label = { Text("IK") },
                            modifier = Modifier.testTag("pose-mode-ik"),
                        )
                    }
                    if (ikEnabled) {
                        Text(
                            "Drag a wrist or foot marker to move the limb as a chain.",
                            color = MutedText,
                            fontSize = 10.sp,
                        )
                    } else if (fingerBones.isNotEmpty()) {
                        Text("Hands", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            fingerBones.forEach { finger ->
                                FilterChip(
                                    selected = selectedBone?.id == finger.id,
                                    onClick = { onJointSelected(finger.id) },
                                    label = { Text(RigSemantics.label(finger, bones), maxLines = 1) },
                                    modifier = Modifier.testTag("pose-finger-${RigSemantics.tag(finger.name)}"),
                                )
                            }
                        }
                    }
                }
                when {
                    actor?.kind != ActorKind.CHARACTER -> {
                        Text("Select a character in Scene to work with its pose.", color = PrimaryText, fontSize = 12.sp)
                    }
                    bones.isEmpty() && morphTargets.isEmpty() -> {
                        Text(rigMessage ?: "Reading the imported rig…", color = PrimaryText, fontSize = 12.sp, modifier = Modifier.testTag("pose-rig-loading"))
                    }
                    else -> {
                        selectedBone?.let { bone ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${RigSemantics.label(bone, bones)} · ${rotation.axisDegrees(selectedAxis).toInt()}°",
                                    color = PrimaryText,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f).testTag("selected-joint"),
                                )
                                IconButton(
                                    onClick = { onEditor(editor.setRigJointRotation(bone.id, rotation.withAxisDegrees(selectedAxis, rotation.axisDegrees(selectedAxis) - 10f)), "pose-joint") },
                                    modifier = Modifier.size(36.dp).testTag("pose-joint-negative"),
                                ) { Icon(Icons.Default.Remove, contentDescription = "Decrease joint rotation", tint = PrimaryText) }
                                IconButton(
                                    onClick = { onEditor(editor.setRigJointRotation(bone.id, rotation.withAxisDegrees(selectedAxis, rotation.axisDegrees(selectedAxis) + 10f)), "pose-joint") },
                                    modifier = Modifier.size(36.dp).testTag("pose-joint-positive"),
                                ) { Icon(Icons.Default.Add, contentDescription = "Increase joint rotation", tint = PrimaryText) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                TransformAxis.values().forEach { axis ->
                                    FilterChip(
                                        selected = selectedAxis == axis,
                                        onClick = { onAxisSelected(axis) },
                                        label = { Text(axis.name) },
                                        modifier = Modifier.testTag("pose-axis-${axis.name.lowercase()}"),
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onEditor(editor.resetRigJoint(bone.id), "pose-reset-joint") },
                                    modifier = Modifier.weight(1f).testTag("pose-reset-joint"),
                                ) { Text("Reset joint") }
                                Button(
                                    onClick = { onEditor(editor.resetRigPose(), "pose-reset-all") },
                                    modifier = Modifier.weight(1f).testTag("pose-reset-all"),
                                ) { Text("Reset pose") }
                            }
                        }
                        if (morphTargets.isNotEmpty()) {
                            Text("Expressions & shape", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            morphTargets.forEach { target ->
                                val weight = actor.rig?.morphWeights?.get(target.id) ?: 0f
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .testTag("morph-${target.id.hashCode().toUInt().toString(16)}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(target.name, color = PrimaryText, fontSize = 12.sp, maxLines = 1)
                                        target.meshName?.let { mesh ->
                                            Text(mesh, color = MutedText, fontSize = 9.sp, maxLines = 1)
                                        }
                                    }
                                    Text("${(weight * 100f).toInt()}%", color = MutedText, fontSize = 10.sp)
                                    IconButton(
                                        onClick = { onEditor(editor.setRigMorphWeight(target.id, weight - 0.1f), "pose-morph") },
                                        enabled = weight > 0f,
                                        modifier = Modifier.size(34.dp),
                                    ) { Icon(Icons.Default.Remove, contentDescription = "Decrease ${target.name}", tint = PrimaryText) }
                                    IconButton(
                                        onClick = { onEditor(editor.setRigMorphWeight(target.id, weight + 0.1f), "pose-morph") },
                                        enabled = weight < 1f,
                                        modifier = Modifier.size(34.dp),
                                    ) { Icon(Icons.Default.Add, contentDescription = "Increase ${target.name}", tint = PrimaryText) }
                                    TextButton(
                                        onClick = { onEditor(editor.resetRigMorph(target.id), "pose-morph-reset") },
                                        enabled = weight > 0f,
                                    ) { Text("Reset") }
                                }
                            }
                        }
                    }
                }
            }
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

private fun Vec3.axisDegrees(axis: TransformAxis): Float = when (axis) {
    TransformAxis.X -> x
    TransformAxis.Y -> y
    TransformAxis.Z -> z
}

private fun Vec3.withAxisDegrees(axis: TransformAxis, value: Float): Vec3 = when (axis) {
    TransformAxis.X -> copy(x = value)
    TransformAxis.Y -> copy(y = value)
    TransformAxis.Z -> copy(z = value)
}
