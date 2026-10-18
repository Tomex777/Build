package studio.artistscene.app

import studio.artistscene.core.Actor
import studio.artistscene.core.ActorKind
import studio.artistscene.core.CharacterAppearanceSettings
import studio.artistscene.core.EnvironmentPreset
import studio.artistscene.core.HairStyle
import studio.artistscene.core.LightSettings
import studio.artistscene.core.LightType
import studio.artistscene.core.ProjectMetadata
import studio.artistscene.core.RigPose
import studio.artistscene.core.SceneCamera
import studio.artistscene.core.SceneProject
import studio.artistscene.core.Transform
import studio.artistscene.core.Vec3
import studio.artistscene.core.WorldSettings

/**
 * An editable 3D Mise scene, not a rendered illustration or external Blender file.
 *
 * Uses two different CC0 VRoid skinned anime avatars and Mise's bundled CC0 tree.
 * Every actor retains its source asset, independent pose and transform. No fake
 * 2D sprite cards, AI imagery, online asset requests or pre-rendered backgrounds.
 */
object UnderTreeScene {
    const val PROJECT_ID = "mise-classmates-under-tree-v1"
    const val FIRST_STUDENT_ID = "classmate-a"
    const val SECOND_STUDENT_ID = "classmate-b"
    const val TREE_ID = "schoolyard-tree"
    const val POSE_TAG = "under-tree-student"

    fun create(): SceneProject {
        val catalog = PrototypeScene.starterAssets()
        fun bundled(assetId: String): Actor =
            requireNotNull(catalog.firstOrNull { it.asset?.assetId == assetId }) {
                "Missing a bundled Mise actor: $assetId"
            }

        // Female and male are physically distinct, independently rigged GLBs.
        // Deliberately keep their publisher's actual clothing and textured hair.
        val classmateA = bundled(PrototypeScene.ANIME_FEMALE_ASSET_ID).copy(
            id = FIRST_STUDENT_ID,
            name = "Classmate A",
            transform = Transform(
                position = Vec3(-0.73f, 0f, -0.13f),
                rotationEulerDegrees = Vec3(y = 164f),
            ),
            appearance = CharacterAppearanceSettings(
                hairStyle = HairStyle.NONE,
                hairPhysicsEnabled = true,
                fabricPhysicsEnabled = true,
                hairGravityStrength = 1f,
                hairWindStrength = .10f,
            ),
            // Shoulders lowered from the imported T-pose; one elbow loosely bent while chatting.
            rig = RigPose(joints = mapOf(
                "figure/spine" to Vec3(z = -3f),
                "figure/head" to Vec3(y = -18f, z = 4f),
                "figure/upperarm-l" to Vec3(x = -7f, z = 79f),
                "figure/lowerarm-l" to Vec3(x = -8f, z = 10f),
                "figure/upperarm-r" to Vec3(x = 10f, z = -58f),
                "figure/lowerarm-r" to Vec3(x = 12f, z = -49f),
                "figure/thigh-l" to Vec3(x = -3f),
                "figure/thigh-r" to Vec3(x = 5f),
            )),
            metadata = mapOf("misePose" to POSE_TAG, "sceneRole" to "student"),
        )
        val classmateB = bundled(PrototypeScene.ANIME_ASSET_ID).copy(
            id = SECOND_STUDENT_ID,
            name = "Classmate B",
            transform = Transform(
                position = Vec3(0.70f, 0f, -0.09f),
                rotationEulerDegrees = Vec3(y = 198f),
            ),
            appearance = CharacterAppearanceSettings(
                hairStyle = HairStyle.NONE,
                hairPhysicsEnabled = true,
                fabricPhysicsEnabled = true,
                hairGravityStrength = 1f,
                hairWindStrength = .05f,
            ),
            // Natural listening posture: head turned, lowered arms and mild hip counterbalance.
            rig = RigPose(joints = mapOf(
                "figure/spine" to Vec3(x = 3f, z = 2f),
                "figure/head" to Vec3(y = 18f, z = -3f),
                "figure/upperarm-l" to Vec3(x = 12f, z = 73f),
                "figure/lowerarm-l" to Vec3(x = 6f, z = 14f),
                "figure/upperarm-r" to Vec3(x = 10f, z = -79f),
                "figure/lowerarm-r" to Vec3(x = -12f, z = -17f),
                "figure/thigh-l" to Vec3(x = 4f),
                "figure/thigh-r" to Vec3(x = -4f),
            )),
            metadata = mapOf("misePose" to POSE_TAG, "sceneRole" to "student"),
        )
        val tree = bundled("starter.mise.tree").copy(
            id = TREE_ID,
            name = "Shade tree",
            transform = Transform(
                position = Vec3(-0.34f, 0f, -1.23f),
                scale = Vec3(1.16f, 1.16f, 1.16f),
            ),
        )

        return SceneProject(
            id = PROJECT_ID,
            name = "Two classmates · Under the tree",
            actors = listOf(tree, classmateA, classmateB,
                // A softer key/fill ratio lets white clothes retain their shape and
                // provides natural tree shade without harsh charcoal floor shadows.
                Actor(
                    id = "schoolyard-sun",
                    name = "Soft afternoon sun",
                    kind = ActorKind.LIGHT,
                    light = LightSettings(
                        type = LightType.DIRECTIONAL,
                        intensity = 68_000f,
                        colorHex = "#FFFAF4",
                        direction = Vec3(0f, -1f, 0f),
                        castsShadow = true,
                    ),
                ),
                Actor(
                    id = "schoolyard-sky-fill",
                    name = "Sky fill",
                    kind = ActorKind.LIGHT,
                    transform = Transform(position = Vec3(1.5f, 2.8f, 2.4f)),
                    light = LightSettings(
                        type = LightType.POINT,
                        intensity = 1_900f,
                        colorHex = "#E8F4FF",
                        rangeMeters = 8f,
                        castsShadow = false,
                    ),
                ),
            ),
            cameras = listOf(
                SceneCamera(
                    id = "camera-main",
                    name = "Under the tree",
                    position = Vec3(0.14f, 1.82f, 6.0f),
                    target = Vec3(0f, 1.60f, -0.35f),
                    verticalFovDegrees = 55f,
                ),
            ),
            world = WorldSettings(
                environmentPreset = EnvironmentPreset.PARK_SHADE,
                groundEnabled = true,
                gridEnabled = false,
            ),
            metadata = ProjectMetadata(
                description = "Two anime-style high-school classmates talking in the shade of a tree. Posed and editable in Mise.",
                tags = listOf("anime", "school", "two characters", "outdoors"),
            ),
        )
    }

    /**
     * Upgrade only the unmodified old daylight preset in already-saved sample
     * projects. Runtime rig discovery may have changed student metadata/poses;
     * those edits must never be replaced. Artist-customized world settings
     * and lights are left exactly as they were.
     */
    fun upgradeDefaultEnvironment(scene: SceneProject): SceneProject {
        if (scene.id != PROJECT_ID) return scene
        val legacyWorld = WorldSettings(
            environmentPreset = EnvironmentPreset.DAYLIGHT,
            groundEnabled = true,
            gridEnabled = false,
        )
        if (scene.world != legacyWorld) return scene
        val oldKey = LightSettings(type = LightType.DIRECTIONAL, intensity = 72_000f, castsShadow = true)
        val oldFill = LightSettings(type = LightType.POINT, intensity = 2_200f, castsShadow = false)
        val newKey = LightSettings(
            type = LightType.DIRECTIONAL, intensity = 68_000f, colorHex = "#FFFAF4",
            direction = Vec3(0f, -1f, 0f), castsShadow = true,
        )
        val newFill = LightSettings(
            type = LightType.POINT, intensity = 1_900f, colorHex = "#E8F4FF",
            castsShadow = false, rangeMeters = 8f,
        )
        return scene.copy(
            world = scene.world.copy(environmentPreset = EnvironmentPreset.PARK_SHADE),
            actors = scene.actors.map { actor ->
                when {
                    actor.id == "key-sun" && actor.light == oldKey ->
                        actor.copy(light = newKey)
                    actor.id == "fill-light" && actor.light == oldFill ->
                        actor.copy(light = newFill)
                    else -> actor
                }
            },
        )
    }
}
