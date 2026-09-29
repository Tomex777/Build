package studio.artistscene.app

import android.content.Context
import android.os.Build
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.filament.LightManager
import com.google.android.filament.View
import io.github.sceneview.Scene
import io.github.sceneview.SceneScope
import io.github.sceneview.SurfaceType
import io.github.sceneview.environment.Environment
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberView
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.PlaneNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.artistscene.core.Actor
import studio.artistscene.core.ActorKind
import studio.artistscene.core.AssetReference
import studio.artistscene.core.AssetStorage
import studio.artistscene.core.RigDefinition
import studio.artistscene.core.SceneProject

private const val VIEWPORT_LOG_TAG = "MiseRuntime"
private const val MAX_RENDER_ASSET_BYTES = 128L * 1024L * 1024L

/**
 * Renderer adapter. SceneProject is the input contract; Filament/SceneView nodes stay ephemeral.
 * All model actors are instantiated independently so duplicated scene actors remain independent.
 */
@Composable
fun SceneViewport(
    project: SceneProject,
    selectedActorId: String?,
    modifier: Modifier = Modifier,
    onSelectActor: (String?) -> Unit,
    onAssetLoaded: (String) -> Unit,
    onAssetFailed: (String) -> Unit,
    onRigDiscovered: (String, RigDefinition) -> Unit,
    onRigUnavailable: (String, String) -> Unit,
    onRigJointsUpdated: (String, Map<String, studio.artistscene.core.Vec3>) -> Unit,
    onRendererFrame: () -> Unit,
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val loadedEnvironment = rememberEnvironment(environmentLoader, isOpaque = false)
    val studioEnvironment = remember(loadedEnvironment) {
        Environment(indirectLight = loadedEnvironment.indirectLight, skybox = null)
    }
    val view = rememberView(engine).apply {
        blendMode = View.BlendMode.TRANSLUCENT
        // SceneView 3.6 enables Filament FXAA by default. Its FXAA fragment shader aborts on
        // Android 8.0's SwiftShader/GLES driver, so keep the older API 26 path on native edges.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
            antiAliasing = View.AntiAliasing.NONE
        }
    }
    val renderableActors = project.actors.filter { it.asset != null }
    val modelReadyForFrame = remember(engine) { AtomicBoolean(false) }

    val sun = project.actors.firstOrNull {
        it.kind == ActorKind.LIGHT && it.visible &&
            it.light?.type == studio.artistscene.core.LightType.DIRECTIONAL
    }
    val fill = project.actors.firstOrNull {
        it.kind == ActorKind.LIGHT && it.visible &&
            it.light?.type == studio.artistscene.core.LightType.POINT
    }
    val activeCamera = project.cameras.firstOrNull { it.id == project.activeCameraId }
        ?: project.cameras.firstOrNull()
        ?: studio.artistscene.core.SceneCamera("camera-main", "Camera")

    val floor = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFF39434F), metallic = 0f, roughness = 0.95f)
    }
    val studioBackdrop = Modifier.drawWithCache {
        val gradient = Brush.radialGradient(
            colors = listOf(
                Color(0xFFC3C6CA),
                Color(0xFFA5AAB0),
                Color(0xFF858C95),
                Color(0xFF68727E),
            ),
            center = Offset(size.width * 0.5f, size.height * 0.47f),
            radius = size.maxDimension * 0.9f,
        )
        onDrawBehind {
            drawRect(gradient)
        }
    }
    val camera = rememberCameraNode(engine) {
        position = Position(activeCamera.position.x, activeCamera.position.y, activeCamera.position.z)
        lookAt(Position(activeCamera.target.x, activeCamera.target.y, activeCamera.target.z))
    }
    LaunchedEffect(activeCamera) {
        camera.position = Position(activeCamera.position.x, activeCamera.position.y, activeCamera.position.z)
        camera.lookAt(Position(activeCamera.target.x, activeCamera.target.y, activeCamera.target.z))
    }

    val hasReportedSurfaceFrame = remember(engine) { AtomicBoolean(false) }
    val hasReportedFrame = remember(engine) { AtomicBoolean(false) }

    Scene(
        // TextureSurface preserves the real Filament renderer while letting the studio field show
        // through transparent pixels. The radial field is Compose-drawn behind the 3D surface.
        modifier = modifier.then(studioBackdrop),
        surfaceType = SurfaceType.TextureSurface,
        isOpaque = false,
        engine = engine,
        view = view,
        environment = studioEnvironment,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        cameraNode = camera,
        cameraManipulator = rememberCameraManipulator(
            orbitHomePosition = Position(
                activeCamera.position.x,
                activeCamera.position.y,
                activeCamera.position.z,
            ),
            targetPosition = Position(
                activeCamera.target.x,
                activeCamera.target.y,
                activeCamera.target.z,
            ),
        ),
        mainLightNode = rememberMainLightNode(engine) {
            intensity = sun?.light?.intensity ?: 72_000f
        },
        onTouchEvent = { event, hitResult ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN && hitResult == null) {
                onSelectActor(null)
            }
            false
        },
        onFrame = {
            val modelReady = modelReadyForFrame.get()
            if (hasReportedSurfaceFrame.compareAndSet(false, true)) {
                Log.i(VIEWPORT_LOG_TAG, "renderer-surface-frame modelReady=$modelReady")
            }
            if (modelReady && hasReportedFrame.compareAndSet(false, true)) {
                onRendererFrame()
            }
        },
    ) {
        if (project.world.groundEnabled) {
            PlaneNode(
                size = Size(8f, 8f),
                normal = Direction(0f, 1f, 0f),
                position = Position(0f, -0.3f, 0f),
                materialInstance = floor,
            )
        }

        fill?.let { light ->
            val p = light.transform.position
            LightNode(
                type = LightManager.Type.POINT,
                intensity = light.light?.intensity ?: 1_400f,
                position = Position(p.x, p.y, p.z),
                apply = { falloff(light.light?.rangeMeters ?: 5f) },
            )
        }

        for (actor in renderableActors) {
            ActorModelNode(
                actor = actor,
                context = context,
                isSelected = actor.id == selectedActorId,
                modelReadyForFrame = modelReadyForFrame,
                onSelectActor = onSelectActor,
                onAssetLoaded = onAssetLoaded,
                onAssetFailed = onAssetFailed,
                onRigDiscovered = onRigDiscovered,
                onRigUnavailable = onRigUnavailable,
                onRigJointsUpdated = onRigJointsUpdated,
            )
        }
    }
}

@Composable
private fun SceneScope.ActorModelNode(
    actor: Actor,
    context: Context,
    isSelected: Boolean,
    modelReadyForFrame: AtomicBoolean,
    onSelectActor: (String?) -> Unit,
    onAssetLoaded: (String) -> Unit,
    onAssetFailed: (String) -> Unit,
    onRigDiscovered: (String, RigDefinition) -> Unit,
    onRigUnavailable: (String, String) -> Unit,
    onRigJointsUpdated: (String, Map<String, studio.artistscene.core.Vec3>) -> Unit,
) {
    val asset = actor.asset ?: return
    val model by produceState<ModelInstance?>(
        initialValue = null,
        key1 = actor.id,
        key2 = asset,
    ) {
        Log.i(VIEWPORT_LOG_TAG, "asset-read-start actor=${actor.id} path=${asset.relativePath}")
        val bytes = try {
            withContext(Dispatchers.IO) { readAssetBytes(context, asset) }
        } catch (error: Exception) {
            Log.e(VIEWPORT_LOG_TAG, "asset-read-failed actor=${actor.id}", error)
            onAssetFailed("${asset.relativePath} · asset read: ${error.message ?: error.javaClass.simpleName}")
            return@produceState
        }
        Log.i(
            VIEWPORT_LOG_TAG,
            "asset-read-complete actor=${actor.id} path=${asset.relativePath} bytes=${bytes.size}",
        )
        value = try {
            modelLoader.createModelInstance(java.nio.ByteBuffer.wrap(bytes))
        } catch (error: Exception) {
            Log.e(VIEWPORT_LOG_TAG, "model-parse-failed actor=${actor.id}", error)
            onAssetFailed("${asset.relativePath} · GLTF parse: ${error.message ?: error.javaClass.simpleName}")
            return@produceState
        }
        Log.i(VIEWPORT_LOG_TAG, "model-parse-complete actor=${actor.id} path=${asset.relativePath}")
        modelReadyForFrame.set(true)
        onAssetLoaded(actor.name)
    }

    val loaded = model
    val rigRuntime = remember(loaded) { loaded?.let(FilamentRigRuntime::discover) }
    LaunchedEffect(rigRuntime, actor.id, actor.rig?.joints, actor.transform) {
        if (rigRuntime != null) {
            onRigDiscovered(actor.id, rigRuntime.definition)
            rigRuntime.apply(actor.rig)
            withFrameNanos { }
            onRigJointsUpdated(actor.id, rigRuntime.worldJointPositions())
            Log.i(
                VIEWPORT_LOG_TAG,
                "rig-ready actor=${actor.id} bones=${rigRuntime.definition.bones.size} posed=${actor.rig?.joints?.size ?: 0}",
            )
        } else if (loaded != null && actor.kind == ActorKind.CHARACTER) {
            onRigUnavailable(actor.id, "This model loaded, but it has no skinned joints to pose.")
        }
    }
    LaunchedEffect(loaded, actor.id) {
        if (loaded != null) Log.i(VIEWPORT_LOG_TAG, "model-ready-for-scene prop=${actor.id}")
    }
    if (loaded == null) return

    val transform = actor.transform
    Node(
        position = Position(
            transform.position.x,
            transform.position.y,
            transform.position.z,
        ),
        rotation = Rotation(
            transform.rotationEulerDegrees.x,
            transform.rotationEulerDegrees.y,
            transform.rotationEulerDegrees.z,
        ),
        scale = Scale(
            transform.scale.x,
            transform.scale.y,
            transform.scale.z,
        ),
        isVisible = actor.visible,
    ) {
        ModelNode(
            modelInstance = loaded,
            scaleToUnits = actor.initialDisplayDimensionMeters(),
            isVisible = actor.visible,
            isEditable = false,
            apply = {
                onSingleTapConfirmed = {
                    onSelectActor(actor.id)
                    true
                }
                if (isSelected) {
                    setPriority(1)
                }
            },
        )
    }
}

/**
 * Keep a newly imported asset readable before the artist edits its transform. SceneView applies
 * this only while instantiating the model; the authored SceneProject transform remains untouched.
 */
private fun Actor.initialDisplayDimensionMeters(): Float = when (kind) {
    ActorKind.CHARACTER -> 1.7f
    ActorKind.VEHICLE -> 2.2f
    ActorKind.ENVIRONMENT -> 3f
    ActorKind.PROP, ActorKind.EFFECT, ActorKind.LIGHT, ActorKind.CAMERA -> 1f
}

private fun readAssetBytes(context: Context, asset: AssetReference): ByteArray {
    val stream = when (asset.storage) {
        AssetStorage.BUNDLED -> context.assets.open(asset.relativePath)
        AssetStorage.PROJECT_FILE -> File(context.filesDir, asset.relativePath).inputStream()
        AssetStorage.PERSISTED_URI -> {
            val uri = requireNotNull(asset.persistedUri) { "Missing persisted URI" }
            requireNotNull(context.contentResolver.openInputStream(Uri.parse(uri))) {
                "Unable to open persisted URI"
            }
        }
    }
    return stream.use { it.readBounded(MAX_RENDER_ASSET_BYTES) }
}

private fun InputStream.readBounded(maxBytes: Long): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "Asset exceeds ${maxBytes / (1024L * 1024L)} MiB render limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
