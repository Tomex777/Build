package studio.artistscene.app

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.filament.LightManager
import io.github.sceneview.Scene
import io.github.sceneview.SceneScope
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
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
    onRendererFrame: () -> Unit,
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
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
        materialLoader.createColorInstance(Color(0xFF39434F), metallic = 0f, roughness = 0.9f)
    }
    val camera = rememberCameraNode(engine) {
        position = Position(activeCamera.position.x, activeCamera.position.y, activeCamera.position.z)
        lookAt(Position(activeCamera.target.x, activeCamera.target.y, activeCamera.target.z))
    }

    val hasReportedSurfaceFrame = remember(engine) { AtomicBoolean(false) }
    val hasReportedFrame = remember(engine) { AtomicBoolean(false) }

    Scene(
        modifier = modifier,
        surfaceType = SurfaceType.Surface,
        engine = engine,
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
            intensity = sun?.light?.intensity ?: 110_000f
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
) {
    val asset = actor.asset ?: return
    val model by produceState<ModelInstance?>(
        initialValue = null,
        key1 = actor.id,
        key2 = asset,
    ) {
        Log.i(VIEWPORT_LOG_TAG, "asset-read-start actor=\${actor.id} path=\${asset.relativePath}")
        val bytes = try {
            withContext(Dispatchers.IO) { readAssetBytes(context, asset) }
        } catch (error: Exception) {
            Log.e(VIEWPORT_LOG_TAG, "asset-read-failed actor=\${actor.id}", error)
            onAssetFailed("\${asset.relativePath} · asset read: \${error.message ?: error.javaClass.simpleName}")
            return@produceState
        }
        Log.i(
            VIEWPORT_LOG_TAG,
            "asset-read-complete actor=\${actor.id} path=\${asset.relativePath} bytes=\${bytes.size}",
        )
        value = try {
            modelLoader.createModelInstance(java.nio.ByteBuffer.wrap(bytes))
        } catch (error: Exception) {
            Log.e(VIEWPORT_LOG_TAG, "model-parse-failed actor=\${actor.id}", error)
            onAssetFailed("\${asset.relativePath} · GLTF parse: \${error.message ?: error.javaClass.simpleName}")
            return@produceState
        }
        Log.i(VIEWPORT_LOG_TAG, "model-parse-complete actor=\${actor.id} path=\${asset.relativePath}")
        modelReadyForFrame.set(true)
        onAssetLoaded(actor.name)
    }

    val loaded = model
    LaunchedEffect(loaded, actor.id) {
        if (loaded != null) Log.i(VIEWPORT_LOG_TAG, "model-ready-for-scene prop=\${actor.id}")
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
            scaleToUnits = 0.8f,
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
        require(total <= maxBytes) { "Asset exceeds \${maxBytes / (1024L * 1024L)} MiB render limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
