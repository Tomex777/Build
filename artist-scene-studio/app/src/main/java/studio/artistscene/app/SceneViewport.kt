package studio.artistscene.app

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import studio.artistscene.core.ActorKind
import studio.artistscene.core.SceneProject
import studio.artistscene.core.Vec3
import io.github.sceneview.Scene
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.PlaneNode
import com.google.android.filament.LightManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val VIEWPORT_LOG_TAG = "MiseRuntime"

/**
 * Renderer adapter. SceneProject is the input contract; Filament/SceneView nodes stay ephemeral.
 */
@Composable
fun SceneViewport(
    project: SceneProject,
    modifier: Modifier = Modifier,
    onAssetLoaded: (String) -> Unit,
    onAssetFailed: (String) -> Unit,
    onRendererFrame: () -> Unit,
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val prop = project.actors.firstOrNull { it.kind == ActorKind.PROP && it.visible }
    val assetPath = prop?.asset?.relativePath
    val model by produceState<ModelInstance?>(
        initialValue = null,
        key1 = modelLoader,
        key2 = assetPath,
    ) {
        if (assetPath != null) {
            Log.i(VIEWPORT_LOG_TAG, "asset-read-start path=$assetPath")
            val buffer = try {
                withContext(Dispatchers.IO) { context.assets.open(assetPath).use { it.readBytes() } }
            } catch (error: Exception) {
                Log.e(VIEWPORT_LOG_TAG, "asset-read-failed path=$assetPath", error)
                onAssetFailed("$assetPath · asset read: ${error.message ?: error.javaClass.simpleName}")
                return@produceState
            }
            Log.i(VIEWPORT_LOG_TAG, "asset-read-complete path=$assetPath bytes=${buffer.size}")
            Log.i(VIEWPORT_LOG_TAG, "model-parse-start path=$assetPath bytes=${buffer.size}")
            value = try {
                modelLoader.createModelInstance(java.nio.ByteBuffer.wrap(buffer))
            } catch (error: Exception) {
                Log.e(VIEWPORT_LOG_TAG, "model-parse-failed path=$assetPath", error)
                onAssetFailed("$assetPath · GLTF parse: ${error.message ?: error.javaClass.simpleName}")
                return@produceState
            }
            Log.i(VIEWPORT_LOG_TAG, "model-parse-complete path=$assetPath")
            onAssetLoaded(prop?.name ?: assetPath)
        }
    }
    val sun = project.actors.firstOrNull { it.kind == ActorKind.LIGHT && it.light?.type == studio.artistscene.core.LightType.DIRECTIONAL }
    val fill = project.actors.firstOrNull { it.kind == ActorKind.LIGHT && it.light?.type == studio.artistscene.core.LightType.POINT }
    val activeCamera = project.cameras.firstOrNull { it.id == project.activeCameraId } ?: project.cameras.first()
    val floor = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFF39434F), metallic = 0f, roughness = 0.9f)
    }
    val camera = rememberCameraNode(engine) {
        position = Position(activeCamera.position.x, activeCamera.position.y, activeCamera.position.z)
        lookAt(Position(activeCamera.target.x, activeCamera.target.y, activeCamera.target.z))
    }

    val hasReportedFrame = remember(engine) { AtomicBoolean(false) }
    val loadedModel = model

    Scene(
        modifier = modifier,
        surfaceType = SurfaceType.Surface,
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        cameraNode = camera,
        cameraManipulator = rememberCameraManipulator(
            orbitHomePosition = Position(activeCamera.position.x, activeCamera.position.y, activeCamera.position.z),
            targetPosition = Position(activeCamera.target.x, activeCamera.target.y, activeCamera.target.z),
        ),
        mainLightNode = rememberMainLightNode(engine) {
            intensity = sun?.light?.intensity ?: 110_000f
        },
        onFrame = {
            // Prove a model-backed render cycle, not merely an empty surface loop.
            if (loadedModel != null && hasReportedFrame.compareAndSet(false, true)) {
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
                apply = { falloff(5f) },
            )
        }

        if (prop != null && loadedModel != null) {
            val p: Vec3 = prop.transform.position
            ModelNode(
                modelInstance = loadedModel,
                scaleToUnits = 0.8f,
                position = Position(p.x, p.y, p.z),
                isVisible = prop.visible,
                isEditable = false,
            )
        }
    }
}
