package studio.artistscene.core

import kotlinx.serialization.Serializable

/** App-owned scene graph. Renderer-specific objects must never enter this model. */
@Serializable
data class SceneProject(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val actors: List<Actor> = emptyList(),
    val cameras: List<SceneCamera> = listOf(SceneCamera("camera-main", "Camera")),
    val activeCameraId: String = "camera-main",
    val world: WorldSettings = WorldSettings(),
    val tracks: List<AnimationTrack> = emptyList(),
    val metadata: ProjectMetadata = ProjectMetadata(),
    val referenceImages: List<ReferenceImage> = emptyList(),
    val timeline: TimelineSettings = TimelineSettings(),
) {
    companion object { const val CURRENT_SCHEMA_VERSION = 4 }
}

@Serializable
data class ProjectMetadata(
    val description: String = "",
    val createdAtEpochMs: Long = 0L,
    val modifiedAtEpochMs: Long = 0L,
    val tags: List<String> = emptyList(),
)

@Serializable
data class Actor(
    val id: String,
    val name: String,
    val kind: ActorKind,
    val transform: Transform = Transform(),
    val visible: Boolean = true,
    val locked: Boolean = false,
    val parentId: String? = null,
    val asset: AssetReference? = null,
    val light: LightSettings? = null,
    val material: MaterialSettings? = null,
    val rigDefinition: RigDefinition? = null,
    val rig: RigPose? = null,
    val animation: ActorAnimationState = ActorAnimationState(),
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
enum class ActorKind { CHARACTER, PROP, VEHICLE, ENVIRONMENT, LIGHT, CAMERA, EFFECT }

@Serializable
data class Transform(
    val position: Vec3 = Vec3(),
    val rotationEulerDegrees: Vec3 = Vec3(),
    val scale: Vec3 = Vec3(1f, 1f, 1f),
)

@Serializable
data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f)

@Serializable
enum class AssetStorage { BUNDLED, PROJECT_FILE, PERSISTED_URI }

@Serializable
data class AssetReference(
    val assetId: String,
    val relativePath: String,
    val format: String = "glb",
    val source: String? = null,
    val creator: String? = null,
    val license: String? = null,
    val licenseUrl: String? = null,
    val attribution: String? = null,
    val version: String? = null,
    val storage: AssetStorage = AssetStorage.BUNDLED,
    val persistedUri: String? = null,
    val byteSize: Long? = null,
    val checksumSha256: String? = null,
)

@Serializable
data class MaterialSettings(
    val baseColorHex: String = "#FFFFFF",
    val metallic: Float = 0f,
    val roughness: Float = 1f,
    val opacity: Float = 1f,
    val emissiveHex: String = "#000000",
)

@Serializable
enum class CameraProjection { PERSPECTIVE, ORTHOGRAPHIC }

@Serializable
data class SceneCamera(
    val id: String,
    val name: String,
    val projection: CameraProjection = CameraProjection.PERSPECTIVE,
    val position: Vec3 = Vec3(0f, 1.6f, 4f),
    val target: Vec3 = Vec3(0f, 1f, 0f),
    val verticalFovDegrees: Float = 50f,
    val orthographicHeightMeters: Float = 5f,
    val rollDegrees: Float = 0f,
    val nearMeters: Float = 0.05f,
    val farMeters: Float = 500f,
)

@Serializable
enum class LightType { DIRECTIONAL, POINT, SPOT }

@Serializable
data class LightSettings(
    val type: LightType,
    val colorHex: String = "#FFFFFF",
    val intensity: Float = 10_000f,
    val castsShadow: Boolean = true,
    val direction: Vec3 = Vec3(0f, -1f, 0f),
    val rangeMeters: Float = 5f,
    val spotInnerConeDegrees: Float = 20f,
    val spotOuterConeDegrees: Float = 35f,
)

@Serializable
data class WorldSettings(
    val backgroundHex: String = "#20242B",
    val groundEnabled: Boolean = true,
    val gridEnabled: Boolean = true,
    val environmentAssetId: String? = null,
)

@Serializable
data class ReferenceImage(
    val id: String,
    val name: String,
    val persistedUri: String,
    val visible: Boolean = true,
    val opacity: Float = 0.75f,
    val transform: Transform = Transform(),
)

@Serializable
data class TimelineSettings(
    val durationSeconds: Float = 5f,
    val loop: Boolean = true,
    val playbackSpeed: Float = 1f,
)

@Serializable
enum class Interpolation { STEP, LINEAR, SMOOTH }

@Serializable
data class AnimatedValue(
    val scalar: Float? = null,
    val vector: Vec3? = null,
    val rotationEulerDegrees: Vec3? = null,
)

@Serializable
data class Keyframe(val timeSeconds: Float, val value: AnimatedValue)

@Serializable
data class AnimationClipDefinition(
    val name: String,
    val durationSeconds: Float = 0f,
)

@Serializable
data class ActorAnimationState(
    val clips: List<AnimationClipDefinition> = emptyList(),
    val selectedClip: String? = null,
    val playing: Boolean = false,
    val loop: Boolean = true,
    val speed: Float = 1f,
)

@Serializable
data class AnimationTrack(
    val id: String,
    val targetActorId: String,
    val propertyPath: String,
    val keyframes: List<Keyframe>,
    val interpolation: Interpolation = Interpolation.SMOOTH,
    val enabled: Boolean = true,
)

@Serializable
data class RigDefinition(
    val name: String = "Skeleton",
    val bones: List<RigBone> = emptyList(),
    val morphTargets: List<RigMorphTarget> = emptyList(),
)

@Serializable
data class RigMorphTarget(
    val id: String,
    val name: String,
    val meshName: String? = null,
)

@Serializable
data class RigBone(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val restRotationEulerDegrees: Vec3 = Vec3(),
)

@Serializable
data class RigPose(
    val joints: Map<String, Vec3> = emptyMap(),
    val morphWeights: Map<String, Float> = emptyMap(),
)
