package studio.artistscene.app

import android.content.Context
import android.opengl.Matrix
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.LightManager
import com.google.android.filament.View
import com.google.android.filament.Skybox
import io.github.sceneview.safeDestroySkybox
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
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberView
import io.github.sceneview.gesture.CameraGestureDetector
import io.github.sceneview.texture.ImageTexture
import io.github.sceneview.texture.setBitmap
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.PlaneNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.artistscene.core.Actor
import studio.artistscene.core.ActorKind
import studio.artistscene.core.AssetReference
import studio.artistscene.core.AssetStorage
import studio.artistscene.core.CameraProjection
import studio.artistscene.core.EnvironmentPreset
import studio.artistscene.core.GroundSurface
import studio.artistscene.core.ReferenceImage
import studio.artistscene.core.RigDefinition
import studio.artistscene.core.SceneCamera
import studio.artistscene.core.SceneProject
import studio.artistscene.core.SkySunMath
import studio.artistscene.core.Vec3

private const val VIEWPORT_LOG_TAG = "MiseRuntime"
private const val MAX_RENDER_ASSET_BYTES = 128L * 1024L * 1024L

/** Only legacy scenes without *any* authored light get the default studio sun. */
internal fun sceneUsesFallbackSun(actors: List<Actor>): Boolean =
    actors.none { it.kind == ActorKind.LIGHT && it.light != null }

/** Shadows follow active visible lights, including local spot/point lights. */
internal fun sceneNeedsShadowMaps(actors: List<Actor>): Boolean =
    sceneUsesFallbackSun(actors) ||
        actors.any { it.kind == ActorKind.LIGHT && it.visible && it.light?.castsShadow == true }

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
    onCameraGestureCommitted: (Vec3, Vec3) -> Unit,
    onAssetLoaded: (String) -> Unit,
    onAssetFailed: (String) -> Unit,
    onRigDiscovered: (String, RigDefinition) -> Unit,
    onAnimationsDiscovered: (String, List<studio.artistscene.core.AnimationClipDefinition>) -> Unit,
    onRigUnavailable: (String, String) -> Unit,
    onRigJointsUpdated: (String, Map<String, studio.artistscene.core.Vec3>) -> Unit,
    onRendererFrame: () -> Unit,
    onActorPivotUpdated: (String, Vec3) -> Unit = { _, _ -> },
) {
    val currentSelection = rememberUpdatedState(onSelectActor)
    val selectActor = remember { { id: String? -> currentSelection.value(id) } }
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val loadedEnvironment = rememberEnvironment(environmentLoader, isOpaque = false)
    val preset = project.world.environmentPreset
    val sun = project.actors.firstOrNull {
        it.kind == ActorKind.LIGHT && it.visible &&
            it.light?.type == studio.artistscene.core.LightType.DIRECTIONAL
    }
    // The procedural sun must follow the same authored directional light as Filament's shadows.
    // No visible sun means no painted disk; older scenes without lights keep their studio sun.
    val fallbackSun = sceneUsesFallbackSun(project.actors)
    val skyDirection = sun?.light?.direction?.takeIf { direction ->
        direction.x.isFinite() && direction.y.isFinite() && direction.z.isFinite() &&
            (direction.x * direction.x + direction.y * direction.y + direction.z * direction.z) > 0.000001f
    } ?: if (fallbackSun) Vec3(-0.45f, -1f, -0.35f) else Vec3(0f, 1f, 0f)
    val snappedSkyDirection = remember(skyDirection) {
        runCatching { SkySunMath.quantizeDirection(skyDirection) }
            .getOrElse { Vec3(0f, 1f, 0f) }
    }
    val skyTexture = remember(engine, preset, snappedSkyDirection) {
        if (preset == EnvironmentPreset.PARK_SHADE) {
            createPhotographicOutdoorSky(engine, context)
        } else createOutdoorSky(engine, preset, snappedSkyDirection)
    }
    val studioSkybox = remember(engine, skyTexture) {
        Skybox.Builder().environment(skyTexture).build(engine)
    }
    DisposableEffect(studioSkybox, skyTexture) {
        onDispose {
            engine.safeDestroySkybox(studioSkybox)
            engine.destroyTexture(skyTexture)
        }
    }
    val studioEnvironment = remember(loadedEnvironment, studioSkybox) {
        Environment(indirectLight = loadedEnvironment.indirectLight, skybox = studioSkybox)
    }
    val view = rememberView(engine).apply {
        blendMode = View.BlendMode.OPAQUE
        // Android 8.x emulator images ship an old SwiftShader GLSL compiler that aborts on
        // Filament's post-process blit shaders. Keep the real Filament scene/model pipeline, but
        // use the direct color path on API 26/27: no FXAA, dithering, or post-processing.
        // Physical devices on newer Android versions retain SceneView's full render pipeline.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
            antiAliasing = View.AntiAliasing.NONE
            dithering = View.Dithering.NONE
            isPostProcessingEnabled = false
        }
    }
    val renderableActors = project.actors.filter { it.asset != null }
    val modelReadyForFrame = remember(engine) { AtomicBoolean(false) }

    val secondaryLights = project.actors.filter { actor ->
        actor.kind == ActorKind.LIGHT && actor.visible && actor.id != sun?.id && actor.light != null
    }
    // Artists may intentionally hide the sun or light a scene using ONLY spot/point lights.
    // Never replace an authored (possibly hidden) light with a surprise default sunlight.
    val activeCamera = project.cameras.firstOrNull { it.id == project.activeCameraId }
        ?: project.cameras.firstOrNull()
        ?: studio.artistscene.core.SceneCamera("camera-main", "Camera")

    // A low-chroma matte ground creates value separation from human skin and light clothing.
    // Keep it as Filament PBR geometry so it receives real shadow maps/contact shadows.
    val chosenGround = project.world.groundSurface.let { selected ->
        if (selected == GroundSurface.AUTO && preset == EnvironmentPreset.PARK_SHADE)
            GroundSurface.GRASS else selected
    }
    val groundTiles = project.world.groundTileCount.coerceIn(2, 24)
    val groundRoughness = project.world.groundRoughness.coerceIn(0.3f, 1f)
    val groundPhoto = remember(engine, chosenGround, groundTiles) {
        if (chosenGround != GroundSurface.AUTO)
            PhotographicGroundCache.obtain(engine, context, chosenGround, groundTiles)
        else null
    }
    val floor = remember(materialLoader, preset, groundPhoto, groundRoughness) {
        if (groundPhoto != null) {
            materialLoader.createTextureInstance(groundPhoto, metallic = 0f, roughness = groundRoughness)
        } else {
            val color = Color(android.graphics.Color.parseColor(preset.groundHex))
            materialLoader.createColorInstance(color, metallic = 0f, roughness = preset.roughness)
        }
    }
    // A genuine ground-plane reference grid, not a 2D UI overlay. It follows
    // perspective as the artist orbits and can be hidden for clean story scenes.
    // SceneView 3.6's ImageNode destructor can free its texture while its
    // MaterialLoader still holds a JNI wrapper for the textured MaterialInstance.
    // Filament aborts natively on scene close: "Invalid texture still bound".
    // A PlaneNode with the ORIGINAL loader-owned instance avoids that lifecycle bug.
    // Keep this image texture alive until MaterialLoader/Engine teardown.
    val gridBitmap = remember(engine) { createReferenceGridBitmap() }
    val gridTexture = remember(engine) {
        ImageTexture.Builder().bitmap(gridBitmap).build(engine)
    }
    val gridMaterial = remember(materialLoader, gridTexture) {
        materialLoader.createImageInstance(gridTexture)
    }
    DisposableEffect(gridBitmap) {
        onDispose { gridBitmap.recycle() }
    }
    val mainLightNode = rememberMainLightNode(engine)
    // Shadow maps are part of Filament's scene rendering, independent of the
    // post-processing workaround above. Android 8 devices still need studio shadows.
    val shadowingSupported = true
    SideEffect {
        val settings = sun?.light
        if (settings != null) {
            mainLightNode.intensity = settings.intensity
            mainLightNode.color = sceneLightColor(settings.colorHex)
            mainLightNode.lightDirection = Direction(
                settings.direction.x,
                settings.direction.y,
                settings.direction.z,
            )
            mainLightNode.isShadowCaster = settings.castsShadow && shadowingSupported
        } else if (fallbackSun) {
            // Backward compatibility for legacy scenes that have no lighting actors.
            mainLightNode.intensity = 72_000f
            mainLightNode.color = sceneLightColor("#FFFFFF")
            mainLightNode.lightDirection = Direction(-0.45f, -1f, -0.35f)
            mainLightNode.isShadowCaster = shadowingSupported
        } else {
            // Honor the explicit lights: there may be a hidden sun, or only point/spot lights.
            mainLightNode.intensity = 0f
            mainLightNode.isShadowCaster = false
        }
        view.setShadowingEnabled(shadowingSupported && sceneNeedsShadowMaps(project.actors))
    }

    val camera = rememberCameraNode(engine) {
        position = Position(activeCamera.position.x, activeCamera.position.y, activeCamera.position.z)
        lookAt(Position(activeCamera.target.x, activeCamera.target.y, activeCamera.target.z))
    }
    LaunchedEffect(activeCamera) {
        camera.position = Position(activeCamera.position.x, activeCamera.position.y, activeCamera.position.z)
        camera.lookAt(Position(activeCamera.target.x, activeCamera.target.y, activeCamera.target.z))
    }
    val latestActiveCamera = rememberUpdatedState(activeCamera)
    val latestCameraCommit = rememberUpdatedState(onCameraGestureCommitted)
    val projectManipulator = remember(activeCamera.id, activeCamera.position, activeCamera.target) {
        ProjectCameraManipulator(
            orbitHomePosition = Position(activeCamera.position.x, activeCamera.position.y, activeCamera.position.z),
            targetPosition = Position(activeCamera.target.x, activeCamera.target.y, activeCamera.target.z),
            onCommitted = { position, target -> latestCameraCommit.value(position, target) },
        )
    }
    // SceneView 3.6 captures its manipulator in the long-lived frame coroutine.
    // Keep that reference stable while forwarding to the current project camera.
    val cameraManipulator = remember(engine) { CurrentCameraManipulator(projectManipulator) }
    SideEffect { cameraManipulator.replace(projectManipulator) }
    val backgroundTouch = remember { arrayOfNulls<Offset>(1) }
    val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    val projectionFingerprint = remember(engine) { AtomicReference<String?>(null) }

    val hasReportedSurfaceFrame = remember(engine) { AtomicBoolean(false) }
    val hasReportedFrame = remember(engine) { AtomicBoolean(false) }
    val readyFrames = remember(engine) { AtomicInteger(0) }

    // Recreate the native surface on orientation changes; API 26 EGL cannot reliably
    // resize the existing TextureSurface swapchain. Project/engine state stays outside it.
    key(LocalConfiguration.current.orientation) {
    Scene(
        modifier = modifier,
        surfaceType = SurfaceType.TextureSurface,
        isOpaque = true,
        engine = engine,
        view = view,
        environment = studioEnvironment,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        cameraNode = camera,
        cameraManipulator = cameraManipulator,
        mainLightNode = mainLightNode,
        onTouchEvent = { event, hitResult ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    backgroundTouch[0] = if (hitResult == null) Offset(event.x, event.y) else null
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    backgroundTouch[0]?.let { down ->
                        if ((Offset(event.x, event.y) - down).getDistance() > touchSlop) backgroundTouch[0] = null
                    }
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (backgroundTouch[0] != null && hitResult == null) selectActor(null)
                    backgroundTouch[0] = null
                }
                android.view.MotionEvent.ACTION_POINTER_DOWN,
                android.view.MotionEvent.ACTION_CANCEL -> backgroundTouch[0] = null
            }
            false
        },
        onFrame = {
            val cameraConfig = latestActiveCamera.value
            val viewportSize = view.viewport
            val fingerprint = buildString {
                append(cameraConfig.id).append('|')
                append(cameraConfig.projection).append('|')
                append(cameraConfig.verticalFovDegrees).append('|')
                append(cameraConfig.orthographicHeightMeters).append('|')
                append(cameraConfig.nearMeters).append('|')
                append(cameraConfig.farMeters).append('|')
                append(viewportSize.width).append('x').append(viewportSize.height)
            }
            if (projectionFingerprint.getAndSet(fingerprint) != fingerprint) {
                applyProjectCameraProjection(camera, cameraConfig, viewportSize.width, viewportSize.height)
            }
            val modelReady = modelReadyForFrame.get()
            if (hasReportedSurfaceFrame.compareAndSet(false, true)) {
                Log.i(VIEWPORT_LOG_TAG, "renderer-surface-frame modelReady=$modelReady")
            }
            if ((renderableActors.isEmpty() || modelReady) && readyFrames.incrementAndGet() >= 12 &&
                viewportSize.width > 0 && viewportSize.height > 0 && hasReportedFrame.compareAndSet(false, true)) {
                onRendererFrame()
            }
        },
    ) {
        if (project.world.groundEnabled) {
            PlaneNode(
                // Plane geometry uses all three size components; normals alone do not
                // orient vertices. Keep the XZ receiver flat, with a nonzero culling box.
                // A finite photo-textured ground reveals the actual sky horizon.
                // Previously the 2000m plane filled EVERY camera ray below the tree,
                // so we accidentally hid the sky and made floor/sky indistinguishable.
                size = if (groundPhoto != null)
                    Size(36f, 0f, 36f) else Size(2000f, 0f, 2000f),
                normal = Direction(0f, 1f, 0f),
                position = Position(0f, -0.01f, 0f),
                materialInstance = floor,
                apply = {
                    isTouchable = false
                    isShadowCaster = false
                    isShadowReceiver = true
                    setCulling(false)
                    val groundHalf = if (groundPhoto != null) 18f else 1000f
                    axisAlignedBoundingBox = Box(0f, 0f, 0f, groundHalf, 0.01f, groundHalf)
                },
            )
        }

        if (project.world.groundEnabled && project.world.gridEnabled) {
            PlaneNode(
                materialInstance = gridMaterial,
                size = Size(24f, 0f, 24f),
                normal = Direction(0f, 1f, 0f),
                position = Position(0f, 0.007f, 0f),
                apply = {
                    isTouchable = false
                    isShadowCaster = false
                    isShadowReceiver = false
                },
            )
        }

        project.referenceImages.filter { it.visible }.forEach { reference ->
            val bitmap = rememberReferenceBitmap(context, reference)
            if (bitmap != null) {
                val t = reference.transform
                key(reference.id, reference.persistedUri) {
                    // One texture and one loader-owned MaterialInstance per reference URI,
                    // updated in place when opacity changes. The reference plane no longer
                    // destroys a bound texture before its MaterialInstance on navigation.
                    val texture = remember(engine) {
                        ImageTexture.Builder().bitmap(bitmap).build(engine)
                    }
                    LaunchedEffect(texture, bitmap) {
                        texture.setBitmap(engine, bitmap)
                    }
                    val imageMaterial = remember(materialLoader, texture) {
                        materialLoader.createImageInstance(texture)
                    }
                    val maxSide = maxOf(bitmap.width, bitmap.height).toFloat().coerceAtLeast(1f)
                    PlaneNode(
                        materialInstance = imageMaterial,
                        size = Size(bitmap.width / maxSide, bitmap.height / maxSide, 0f),
                        normal = Direction(0f, 0f, 1f),
                        position = Position(t.position.x, t.position.y, t.position.z),
                        rotation = Rotation(
                            x = t.rotationEulerDegrees.x,
                            y = t.rotationEulerDegrees.y,
                            z = t.rotationEulerDegrees.z,
                        ),
                        scale = Scale(t.scale.x, t.scale.y, t.scale.z),
                        apply = {
                            isTouchable = false
                        },
                    )
                }
            }
        }

        secondaryLights.forEach { light ->
            val settings = light.light ?: return@forEach
            val p = light.transform.position
            key(light.id, settings, light.transform) {
                LightNode(
                    type = when (settings.type) {
                        studio.artistscene.core.LightType.DIRECTIONAL -> LightManager.Type.DIRECTIONAL
                        studio.artistscene.core.LightType.POINT -> LightManager.Type.POINT
                        studio.artistscene.core.LightType.SPOT -> LightManager.Type.SPOT
                    },
                    intensity = settings.intensity,
                    direction = Direction(
                        settings.direction.x,
                        settings.direction.y,
                        settings.direction.z,
                    ),
                    position = Position(p.x, p.y, p.z),
                    apply = {
                        val argb = runCatching { android.graphics.Color.parseColor(settings.colorHex) }
                            .getOrDefault(android.graphics.Color.WHITE)
                        color(
                            android.graphics.Color.red(argb) / 255f,
                            android.graphics.Color.green(argb) / 255f,
                            android.graphics.Color.blue(argb) / 255f,
                        )
                        falloff(settings.rangeMeters)
                        castShadows(settings.castsShadow && shadowingSupported)
                        if (settings.type == studio.artistscene.core.LightType.SPOT) {
                            spotLightCone(
                                Math.toRadians(settings.spotInnerConeDegrees.toDouble()).toFloat(),
                                Math.toRadians(settings.spotOuterConeDegrees.toDouble()).toFloat(),
                            )
                        }
                    },
                )
            }
        }

        val renderableIds = renderableActors.mapTo(mutableSetOf()) { it.id }
        fun hasRenderableParentCycle(actor: Actor): Boolean {
            val seen = mutableSetOf(actor.id)
            var parentId = actor.parentId
            while (parentId != null && parentId in renderableIds) {
                if (!seen.add(parentId)) return true
                parentId = renderableActors.firstOrNull { it.id == parentId }?.parentId
            }
            return false
        }
        val cyclicActorIds = renderableActors.filter(::hasRenderableParentCycle).mapTo(mutableSetOf()) { it.id }
        val childrenByParent = renderableActors
            .filter { actor ->
                val parentId = actor.parentId
                actor.id !in cyclicActorIds && parentId != null && parentId in renderableIds
            }
            .groupBy { requireNotNull(it.parentId) }
        val rootActors = renderableActors.filter { actor ->
            val parentId = actor.parentId
            actor.id in cyclicActorIds || parentId == null || parentId !in renderableIds
        }
        rootActors.forEach { actor ->
            ActorModelNode(
                actor = actor,
                context = context,
                selectedActorId = selectedActorId,
                childrenByParent = childrenByParent,
                modelReadyForFrame = modelReadyForFrame,
                onSelectActor = selectActor,
                onAssetLoaded = onAssetLoaded,
                onAssetFailed = onAssetFailed,
                onRigDiscovered = onRigDiscovered,
                onAnimationsDiscovered = onAnimationsDiscovered,
                onRigUnavailable = onRigUnavailable,
                onRigJointsUpdated = onRigJointsUpdated,
                onActorPivotUpdated = onActorPivotUpdated,
            )
        }
    }
    }
}

@Composable
private fun SceneScope.ActorModelNode(
    actor: Actor,
    context: Context,
    selectedActorId: String?,
    childrenByParent: Map<String, List<Actor>>,
    modelReadyForFrame: AtomicBoolean,
    onSelectActor: (String?) -> Unit,
    onAssetLoaded: (String) -> Unit,
    onAssetFailed: (String) -> Unit,
    onRigDiscovered: (String, RigDefinition) -> Unit,
    onAnimationsDiscovered: (String, List<studio.artistscene.core.AnimationClipDefinition>) -> Unit,
    onRigUnavailable: (String, String) -> Unit,
    onRigJointsUpdated: (String, Map<String, studio.artistscene.core.Vec3>) -> Unit,
    onActorPivotUpdated: (String, Vec3) -> Unit,
) {
    val asset = actor.asset ?: return
    // SceneView retains onFrame on its native Node. Never capture the actor
    // snapshot from when the node was first mounted: Compose Inspector sliders
    // update immutable Actor values but must affect the VERY NEXT GPU frame.
    val latestActor = rememberUpdatedState(actor)
    val model by produceState<ModelInstance?>(
        initialValue = null,
        key1 = actor.id,
        key2 = asset,
    ) {
        Log.i(VIEWPORT_LOG_TAG, "asset-read-start actor=${actor.id} path=${asset.relativePath}")
        val bytes = try {
            withContext(Dispatchers.IO) { readAssetBytes(context, asset) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
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
        onAssetLoaded(asset.relativePath)
    }

    val loaded = model
    val vrmFaceNames = remember(asset) { VrmExpressionMetadata.load(context, asset) }
    val rigRuntime = remember(loaded, vrmFaceNames) {
        loaded?.let { FilamentRigRuntime.discover(it, vrmFaceNames) }
    }
    // Actual VRM spring roots, with a private integrator per independently posed actor.
    val springRuntime = remember(loaded, asset, rigRuntime) {
        loaded?.let { VrmSpringRuntime.fromAsset(context, asset, it, rigRuntime?.definition) }
    }
    // Parent Node.onFrame is installed while produceState(model) is still null.
    // SceneView retains that lambda even after async GLB loading/recomposition:
    // keep the *runtime* live too, not only the current immutable Actor settings.
    val latestSpringRuntime = rememberUpdatedState(springRuntime)
    val latestRigRuntime = rememberUpdatedState(rigRuntime)
    // A model parsed in produceState is not necessarily attached to Filament yet.
    // A distinct actor can mount one or more native frames later than its sibling.
    val nativeModelMounted = remember(actor.id, asset) { AtomicBoolean(false) }
    val hasBoundRestPose = remember(rigRuntime) { AtomicBoolean(false) }
    val animationClips = remember(loaded) {
        loaded?.animator?.let { animator ->
            (0 until animator.animationCount).map { index ->
                studio.artistscene.core.AnimationClipDefinition(
                    name = animator.getAnimationName(index).takeIf { it.isNotBlank() } ?: "Clip ${index + 1}",
                    durationSeconds = animator.getAnimationDuration(index).toFloat(),
                )
            }
        }.orEmpty()
    }
    LaunchedEffect(loaded, actor.id, animationClips) {
        if (loaded != null) {
            onAnimationsDiscovered(actor.id, animationClips)
            Log.i(VIEWPORT_LOG_TAG, "animations-ready actor=${actor.id} clips=${animationClips.size}")
        }
    }
    LaunchedEffect(rigRuntime, actor.id, actor.rig, actor.transform, actor.animation.playing, actor.appearance, actor.visible) {
        if (rigRuntime != null) {
            onRigDiscovered(actor.id, rigRuntime.definition)
            // ModelNode attaches and normalizes the imported root before skin matrices
            // are calculated. Later edits remain immediate for live joint dragging.
            if (!hasBoundRestPose.get()) withFrameNanos { }
            if (!actor.animation.playing) {
                rigRuntime.apply(actor.rig)
                hasBoundRestPose.set(true)
            }
            withFrameNanos { }
            rigRuntime.applyAppearance(actor.appearance, actor.visible)
            onRigJointsUpdated(actor.id, rigRuntime.worldJointPositions())
            Log.i(
                VIEWPORT_LOG_TAG,
                "rig-ready actor=${actor.id} bones=${rigRuntime.definition.bones.size} posed=${actor.rig?.joints?.size ?: 0} morphs=${rigRuntime.definition.morphTargets.size} shaped=${actor.rig?.morphWeights?.size ?: 0}",
            )
        } else if (loaded != null && actor.kind == ActorKind.CHARACTER) {
            onRigUnavailable(actor.id, "This model loaded, but it has no skinned joints to pose.")
        }
    }
    LaunchedEffect(loaded, actor.id) {
        if (loaded != null) Log.i(VIEWPORT_LOG_TAG, "model-ready-for-scene prop=${actor.id}")
    }
    val latestSelectionCallback = rememberUpdatedState(onSelectActor)
    val latestPivotCallback = rememberUpdatedState(onActorPivotUpdated)
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
        apply = {
            var lastPivot: Vec3? = null
            val rootMatrix = FloatArray(16)
            var lastAppliedPose: studio.artistscene.core.RigPose? = null
            var lastAppliedRig: FilamentRigRuntime? = null
            onFrame = {
                val current = latestActor.value
                // The coroutine can upload the pose before the asynchronously
                // constructed child ModelNode is natively mounted. Recover ONLY
                // after this actor's own ModelNode.apply confirms attachment.
                // Do not overwrite spring motion every frame: rebind on actual
                // authored-pose / model changes, or after a clip finishes.
                if (current.animation.playing) {
                    lastAppliedRig = null
                    lastAppliedPose = null
                } else if (nativeModelMounted.get()) {
                    val rig = latestRigRuntime.value
                    if (rig != null && (lastAppliedRig !== rig || lastAppliedPose != current.rig)) {
                        rig.apply(current.rig)
                        lastAppliedRig = rig
                        lastAppliedPose = current.rig
                        val boneIds = rig.definition.bones.mapTo(mutableSetOf()) { it.id }
                        val boundCount = current.rig?.joints?.keys?.count { it in boneIds } ?: 0
                        Log.i(VIEWPORT_LOG_TAG,
                            "rig-native-frame-bind actor=${current.id} bound=$boundCount/" +
                                "${current.rig?.joints?.size ?: 0} bones=${boneIds.size}")
                    }
                }
                // Apply physical hair/fabric only after authored pose and native
                // mesh registration, preserving the actual skinned geometry.
                if (nativeModelMounted.get()) {
                    latestSpringRuntime.value?.advance(current, System.nanoTime())
                }
                val manager = engine.transformManager
                val world = manager.getWorldTransform(manager.getInstance(entity), rootMatrix)
                val pivot = Vec3(world[12], world[13], world[14])
                if (pivot != lastPivot) {
                    lastPivot = pivot
                    latestPivotCallback.value(actor.id, pivot)
                }
            }
        },
    ) {
        if (loaded != null) {
            val displayOrigin = remember(loaded, actor.kind) {
                val bounds = if (asset.assetId in setOf(PrototypeScene.HUMANOID_ASSET_ID, PrototypeScene.ANIME_ASSET_ID, PrototypeScene.ANIME_FEMALE_ASSET_ID)) {
                    loaded.asset.renderableEntities.firstOrNull { loaded.asset.getName(it) == "Body" }?.let {
                        engine.renderableManager.getAxisAlignedBoundingBox(engine.renderableManager.getInstance(it), null)
                    } ?: loaded.asset.boundingBox
                } else loaded.asset.boundingBox
                val center = bounds.center
                val half = bounds.halfExtent
                val dimension = maxOf(half[0], half[1], half[2]) * 2f
                val unitScale = actor.initialDisplayDimensionMeters() / dimension.coerceAtLeast(0.00001f)
                val allHalf = loaded.asset.boundingBox.halfExtent
                val allDimension = maxOf(allHalf[0], allHalf[1], allHalf[2]) * 2f
                if (asset.assetId == PrototypeScene.ANIME_ASSET_ID) {
                    Log.i(
                        VIEWPORT_LOG_TAG,
                        "anime-mesh-bounds bodyCenter=${center.contentToString()} bodyHalf=${half.contentToString()} " +
                            "assetHalf=${allHalf.contentToString()} scale=${unitScale} extent=${allDimension} " +
                            "renderables=${loaded.asset.renderableEntities.size}",
                    )
                }
                Pair(Position(-center[0] * unitScale, -(center[1] - half[1]) * unitScale, -center[2] * unitScale), allDimension * unitScale)
            }
            // SceneView switches clips reactively. Keying on animation settings destroys
            // the native model root and reuses a ModelInstance whose hierarchy is now invalid.
                ModelNode(
                    modelInstance = loaded,
                    autoAnimate = false,
                    animationName = actor.animation.selectedClip.takeIf { actor.animation.playing },
                    animationLoop = actor.animation.loop,
                    animationSpeed = actor.animation.speed,
                    scaleToUnits = displayOrigin.second,
                    position = displayOrigin.first,
                    isVisible = actor.visible,
                    isEditable = false,
                    apply = {
                        nativeModelMounted.set(true)
                        modelReadyForFrame.set(true)
                        setScreenSpaceContactShadows(true)
                        onSingleTapConfirmed = {
                            latestSelectionCallback.value(actor.id)
                            true
                        }
                        if (actor.id == selectedActorId) {
                            setPriority(1)
                        }
                    },
                )
        }
        val attachmentParentEntity = parentNode.entity
        childrenByParent[actor.id].orEmpty().forEach { child ->
            key(child.id, child.parentBoneId, rigRuntime) {
                val attachedBoneId = child.parentBoneId
                if (attachedBoneId != null) {
                    val resolved = rigRuntime?.definition?.bones?.any { it.id == attachedBoneId } == true
                    Node(
                        isVisible = actor.visible && resolved,
                        apply = {
                            var lastGrip: Vec3? = null
                            // Reuse matrices: this closure runs every rendered frame.
                            val gripMatrix = FloatArray(16)
                            val parentMatrix = FloatArray(16)
                            val inverseParent = FloatArray(16)
                            val gripLocal = FloatArray(16)
                            onFrame = {
                                rigRuntime?.worldJointTransform(attachedBoneId, gripMatrix)?.let { gripWorld ->
                                    val manager = engine.transformManager
                                    val parentWorld = manager.getWorldTransform(
                                        manager.getInstance(attachmentParentEntity), parentMatrix,
                                    )
                                    if (Matrix.invertM(inverseParent, 0, parentWorld, 0)) {
                                        Matrix.multiplyMM(gripLocal, 0, inverseParent, 0, gripWorld, 0)
                                        // Strip the imported skeleton's unit conversion; the prop
                                        // keeps its own authored size and inherits actor scale.
                                        for (column in 0..2) {
                                            val offset = column * 4
                                            val length = kotlin.math.sqrt(
                                                gripLocal[offset] * gripLocal[offset] +
                                                    gripLocal[offset + 1] * gripLocal[offset + 1] +
                                                    gripLocal[offset + 2] * gripLocal[offset + 2],
                                            ).coerceAtLeast(0.00001f)
                                            for (row in 0..2) gripLocal[offset + row] /= length
                                        }
                                        manager.setTransform(manager.getInstance(entity), gripLocal)
                                        val grip = Vec3(gripWorld[12], gripWorld[13], gripWorld[14])
                                        if (lastGrip != grip) {
                                            lastGrip = grip
                                            Log.i(VIEWPORT_LOG_TAG, "bone-attachment-followed actor=${child.id} parent=${actor.id} bone=$attachedBoneId x=${grip.x} y=${grip.y} z=${grip.z}")
                                        }
                                    }
                                }
                            }
                        },
                    ) {
                        ActorModelNode(
                            actor = child,
                            context = context,
                            selectedActorId = selectedActorId,
                            childrenByParent = childrenByParent,
                            modelReadyForFrame = modelReadyForFrame,
                            onSelectActor = onSelectActor,
                            onAssetLoaded = onAssetLoaded,
                            onAssetFailed = onAssetFailed,
                            onRigDiscovered = onRigDiscovered,
                            onAnimationsDiscovered = onAnimationsDiscovered,
                            onRigUnavailable = onRigUnavailable,
                            onRigJointsUpdated = onRigJointsUpdated,
                            onActorPivotUpdated = onActorPivotUpdated,
                        )
                    }
                } else {
                    ActorModelNode(
                        actor = child,
                        context = context,
                        selectedActorId = selectedActorId,
                        childrenByParent = childrenByParent,
                        modelReadyForFrame = modelReadyForFrame,
                        onSelectActor = onSelectActor,
                        onAssetLoaded = onAssetLoaded,
                        onAssetFailed = onAssetFailed,
                        onRigDiscovered = onRigDiscovered,
                        onAnimationsDiscovered = onAnimationsDiscovered,
                        onRigUnavailable = onRigUnavailable,
                        onRigJointsUpdated = onRigJointsUpdated,
                        onActorPivotUpdated = onActorPivotUpdated,
                    )
                }
            }
        }
    }
}

/**
 * Keep a newly imported asset readable before the artist edits its transform. SceneView applies
 * this only while instantiating the model; the authored SceneProject transform remains untouched.
 */
private fun Actor.initialDisplayDimensionMeters(): Float =
    if (asset?.assetId?.startsWith("terrain.generated.") == true) 36f
    else when (asset?.assetId) {
    "starter.mise.bicycle" -> 1.81f
    "starter.mise.car" -> 3.88f
    "starter.mise.tree" -> 3.65f
    // Species-scale tree; use genuine detailed leaf canopy at 5.8m height.
    "starter.cc0.oak-street-tree" -> 5.8f
    "starter.cc0.two-storey-house" -> 14.7f
    "starter.cc0.bungalow-house" -> 12.9f
    "starter.cc0.sedan-car" -> 4.7f
    else -> when (kind) {
    ActorKind.CHARACTER -> 1.7f
    ActorKind.VEHICLE -> 2.2f
    ActorKind.ENVIRONMENT -> 3f
    ActorKind.PROP, ActorKind.EFFECT, ActorKind.LIGHT, ActorKind.CAMERA -> 1f
    }
}

/** Small bounded cache for immutable packaged models shared across scene actors.
 * Filament instances are NEVER cached/shared: every actor still receives an independent
 * animated ModelInstance. Cache only raw bytes, so duplicating VRoid avatars avoids
 * repeated 18 MB disk reads without leaking native GPU assets.
 */
internal object MiseModelBytes {
    private val cache = object : LruCache<String, ByteArray>(40 * 1024) {
        override fun sizeOf(key: String, bytes: ByteArray): Int =
            (bytes.size + 1023) / 1024
    }

    fun load(context: Context, asset: AssetReference, fetch: () -> ByteArray): ByteArray {
        if (asset.storage != AssetStorage.BUNDLED) return fetch()
        val fingerprint = listOf(asset.assetId, asset.relativePath, asset.version ?: "").joinToString("|")
        cache.get(fingerprint)?.let { return it }
        val bytes = fetch()
        if (bytes.size <= 32 * 1024 * 1024) cache.put(fingerprint, bytes)
        return bytes
    }

    fun trim() = cache.evictAll()
}

private fun readAssetBytes(context: Context, asset: AssetReference): ByteArray =
    MiseModelBytes.load(context, asset) { readAssetBytesUncached(context, asset) }

private fun readAssetBytesUncached(context: Context, asset: AssetReference): ByteArray {
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

private fun sceneLightColor(hex: String): io.github.sceneview.math.Color {
    val argb = runCatching { android.graphics.Color.parseColor(hex) }
        .getOrDefault(android.graphics.Color.WHITE)
    return io.github.sceneview.math.colorOf(argb)
}

internal class CurrentCameraManipulator(
    private var current: CameraGestureDetector.CameraManipulator,
) : CameraGestureDetector.CameraManipulator {
    private var width = 0
    private var height = 0

    fun replace(next: CameraGestureDetector.CameraManipulator) {
        if (current === next) return
        current = next
        if (width > 0 && height > 0) current.setViewport(width, height)
    }

    override fun setViewport(width: Int, height: Int) {
        this.width = width
        this.height = height
        current.setViewport(width, height)
    }
    override fun getTransform() = current.getTransform()
    override fun grabBegin(x: Int, y: Int, strafe: Boolean) = current.grabBegin(x, y, strafe)
    override fun grabUpdate(x: Int, y: Int) = current.grabUpdate(x, y)
    override fun grabEnd() = current.grabEnd()
    override fun scrollBegin(x: Int, y: Int, separation: Float) = current.scrollBegin(x, y, separation)
    override fun scrollUpdate(x: Int, y: Int, prevSeparation: Float, currSeparation: Float) =
        current.scrollUpdate(x, y, prevSeparation, currSeparation)
    override fun scrollEnd() = current.scrollEnd()
    override fun update(deltaTime: Float) = current.update(deltaTime)
}

private class ProjectCameraManipulator(
    orbitHomePosition: Position,
    targetPosition: Position,
    private val onCommitted: (Vec3, Vec3) -> Unit,
) : CameraGestureDetector.DefaultCameraManipulator(
    orbitHomePosition = orbitHomePosition,
    targetPosition = targetPosition,
) {
    override fun grabEnd() {
        super.grabEnd()
        commitProjectCamera()
    }

    override fun scrollEnd() {
        super.scrollEnd()
        commitProjectCamera()
    }

    private fun commitProjectCamera() {
        val eye = FloatArray(3)
        val target = FloatArray(3)
        val up = FloatArray(3)
        manipulator.getLookAt(eye, target, up)
        onCommitted(
            Vec3(eye[0], eye[1], eye[2]),
            Vec3(target[0], target[1], target[2]),
        )
    }
}

/**
 * Project aspect must be derived from the CURRENT viewport, not a camera's previous projection.
 * CameraNode's last perspective/ortho aspect can lag one orientation behind after a surface
 * recreation, stretching models and producing a mismatched PNG until another camera edit.
 */
internal fun viewportAspectOrFallback(width: Int, height: Int, previousAspect: Double): Double =
    if (width > 0 && height > 0) {
        width.toDouble() / height.toDouble()
    } else {
        previousAspect.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    }

private fun applyProjectCameraProjection(
    camera: io.github.sceneview.node.CameraNode,
    config: SceneCamera,
    viewportWidth: Int,
    viewportHeight: Int,
) {
    val aspect = viewportAspectOrFallback(viewportWidth, viewportHeight, camera.getViewPortAspect())
    val near = config.nearMeters.coerceAtLeast(0.001f)
    val far = config.farMeters.coerceAtLeast(near + 0.01f)
    when (config.projection) {
        CameraProjection.PERSPECTIVE -> camera.setProjection(
            fovInDegrees = config.verticalFovDegrees.coerceIn(15f, 120f).toDouble(),
            near = near,
            far = far,
            direction = Camera.Fov.VERTICAL,
            aspect = aspect,
        )
        CameraProjection.ORTHOGRAPHIC -> {
            val halfHeight = config.orthographicHeightMeters.coerceAtLeast(0.2f).toDouble() * 0.5
            val halfWidth = halfHeight * aspect
            camera.setProjection(
                Camera.Projection.ORTHO,
                -halfWidth,
                halfWidth,
                -halfHeight,
                halfHeight,
                near.toDouble(),
                far.toDouble(),
            )
        }
    }
}

@Composable
private fun rememberReferenceBitmap(
    context: Context,
    reference: ReferenceImage,
): Bitmap? {
    val uri = remember(reference.persistedUri) { Uri.parse(reference.persistedUri) }
    return produceState<Bitmap?>(
        initialValue = null,
        key1 = reference.persistedUri,
        key2 = reference.opacity,
    ) {
        value = withContext(Dispatchers.IO) {
            decodeReferenceBitmap(context, uri, reference.opacity)
        }
    }.value
}

private fun decodeReferenceBitmap(
    context: Context,
    uri: Uri,
    opacity: Float,
): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
        sample *= 2
    }
    val decoded = resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(
            input,
            null,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    } ?: return@runCatching null
    if (opacity >= 0.995f) return@runCatching decoded
    val output = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
    Canvas(output).drawBitmap(
        decoded,
        0f,
        0f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        },
    )
    decoded.recycle()
    output
}.onFailure {
    Log.w(VIEWPORT_LOG_TAG, "reference-image-load-failed uri=${uri.scheme}", it)
}.getOrNull()

/** Thin alpha grid lines; PBR floor beneath still receives the scene's shadows. */
private fun createReferenceGridBitmap(): Bitmap {
    val size = 1024
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(46, 51, 68, 76)
        strokeWidth = 1.2f
    }
    for (index in 0..48) {
        val at = size * index / 48f
        canvas.drawLine(at, 0f, at, size.toFloat(), stroke)
        canvas.drawLine(0f, at, size.toFloat(), at, stroke)
    }
    stroke.color = android.graphics.Color.argb(68, 57, 76, 80)
    stroke.strokeWidth = 1.7f
    canvas.drawLine(size / 2f, 0f, size / 2f, size.toFloat(), stroke)
    canvas.drawLine(0f, size / 2f, size.toFloat(), size / 2f, stroke)
    return bitmap
}
