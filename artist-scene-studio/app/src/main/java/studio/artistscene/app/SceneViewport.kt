package studio.artistscene.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import studio.artistscene.core.ActorKind
import studio.artistscene.core.SceneProject
import studio.artistscene.core.Vec3
import io.github.sceneview.Scene
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.PlaneNode
import com.google.android.filament.LightManager

/**
 * Renderer adapter. SceneProject is the input contract; Filament/SceneView nodes stay ephemeral.
 */
@Composable
fun SceneViewport(
    project: SceneProject,
    modifier: Modifier = Modifier,
    onAssetLoaded: (String) -> Unit,
    onAssetFailed: (String) -> Unit,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val prop = project.actors.firstOrNull { it.kind == ActorKind.PROP && it.visible }
    val assetPath = prop?.asset?.relativePath
    val model = assetPath?.let { rememberModelInstance(modelLoader, it) }
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

    LaunchedEffect(assetPath, model) {
        if (assetPath != null) {
            if (model != null) onAssetLoaded(prop?.name ?: assetPath)
        }
    }
    LaunchedEffect(assetPath) {
        if (assetPath != null) {
            kotlinx.coroutines.delay(30_000)
            if (model == null) onAssetFailed(assetPath)
        }
    }

    Scene(
        modifier = modifier,
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

        if (prop != null && model != null) {
            val p: Vec3 = prop.transform.position
            ModelNode(
                modelInstance = model,
                scaleToUnits = 0.8f,
                position = Position(p.x, p.y, p.z),
                isVisible = prop.visible,
                isEditable = false,
            )
        }
    }
}
