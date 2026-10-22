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
                rotationEulerDegrees = Vec3(y = 197f),
            ),
            appearance = CharacterAppearanceSettings(
                hairStyle = HairStyle.NONE,
                hairPhysicsEnabled = true,
                fabricPhysicsEnabled = true,
                hairGravityStrength = 1f,
                hairWindStrength = .10f,
            ),
            // Imported VRoid faces -Z. Turning the body just past 180° brings the face
            // toward the +Z camera AND slightly toward the other student (+X).
            // Keep head yaw modest: both eyes remain legible in three-quarter view.
            // Arms lower naturally from the original T-pose.
            rig = RigPose(joints = mapOf(
                "figure/spine" to Vec3(z = -3f),
                "figure/head" to Vec3(y = 9f, z = 4f),
                "figure/upperarm-l" to Vec3(x = -7f, z = 79f),
                "figure/lowerarm-l" to Vec3(x = -8f, z = 10f),
                "figure/upperarm-r" to Vec3(x = 10f, z = -58f),
                "figure/lowerarm-r" to Vec3(x = 12f, z = -49f),
                "figure/thigh-l" to Vec3(x = -3f),
                "figure/thigh-r" to Vec3(x = 5f),
            )),
            metadata = mapOf(
                "misePose" to POSE_TAG,
                "sceneRole" to "student",
                // Applied once to a real discovered VRM Face morph, then removed.
                // Never replay against an artist-edited or saved character.
                CharacterDemoStyling.INITIAL_EXPRESSION_KEY to "SMILE",
            ),
        )
        val classmateB = bundled(PrototypeScene.ANIME_ASSET_ID).copy(
            id = SECOND_STUDENT_ID,
            name = "Classmate B",
            transform = Transform(
                position = Vec3(0.70f, 0f, -0.09f),
                rotationEulerDegrees = Vec3(y = 163f),
            ),
            appearance = CharacterAppearanceSettings(
                hairStyle = HairStyle.NONE,
                hairPhysicsEnabled = true,
                fabricPhysicsEnabled = true,
                hairGravityStrength = 1f,
                hairWindStrength = .05f,
            ),
            // Mirrored inward-facing posture: the original male VRoid also faces -Z;
            // turning to 163° exposes his face to +Z while his gaze points -X.
            // Keep his independently editable arm/hip pose and bone rig.
            rig = RigPose(joints = mapOf(
                "figure/spine" to Vec3(x = 3f, z = 2f),
                "figure/head" to Vec3(y = -9f, z = -3f),
                "figure/upperarm-l" to Vec3(x = 12f, z = 73f),
                "figure/lowerarm-l" to Vec3(x = 6f, z = 14f),
                "figure/upperarm-r" to Vec3(x = 10f, z = -79f),
                "figure/lowerarm-r" to Vec3(x = -12f, z = -17f),
                "figure/thigh-l" to Vec3(x = 4f),
                "figure/thigh-r" to Vec3(x = -4f),
            )),
            metadata = mapOf("misePose" to POSE_TAG, "sceneRole" to "student"),
        )
        val tree = bundled("starter.cc0.oak-street-tree").copy(
            id = TREE_ID,
            name = "Oak shade tree",
            transform = Transform(
                position = Vec3(-0.34f, 0f, -1.55f),
                // Landscape oak has a broad crown. A youthful four-metre
                // canopy allows natural sky to remain visible behind actors.
                scale = Vec3(0.70f, 0.70f, 0.70f),
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
                    name = "Afternoon sunlight",
                    kind = ActorKind.LIGHT,
                    light = LightSettings(
                        type = LightType.DIRECTIONAL,
                        intensity = 68_000f,
                        colorHex = "#FFFAF4",
                        direction = Vec3(-0.55f, -1f, -0.45f),
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
                    // Move 0.9 m closer and lower the aim: enlarged student faces and less
                    // empty upper sky while still showing both figures on narrow phones.
                    position = Vec3(0.14f, 1.66f, 5.10f),
                    target = Vec3(0f, 1.22f, -0.28f),
                    verticalFovDegrees = 52f,
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
     * Older installs have this scene already saved; changing [create] alone
     * cannot update it. Migrate ONLY the exact untouched sample composition,
     * including its stock camera and original two stock skinned poses.
     *
     * Runtime glTF binding replaces semantic joint IDs with the real model's
     * rig IDs. Compare the *original authored rotations*, not the IDs, to
     * avoid silently replacing a user's saved pose after the first render.
     * This method is deliberately separate so startup can gate it once with
     * a new migration preference after Agent 1 owns MainActivity wiring.
     */
    fun upgradeUntouchedDemoComposition(scene: SceneProject): SceneProject {
        if (scene.id != PROJECT_ID || scene.tracks.isNotEmpty() ||
            scene.savedPoses.isNotEmpty() || scene.referenceImages.isNotEmpty()
        ) return scene

        val expectedIds = setOf(TREE_ID, FIRST_STUDENT_ID, SECOND_STUDENT_ID,
            "schoolyard-sun", "schoolyard-sky-fill")
        if (scene.actors.size != expectedIds.size ||
            scene.actors.map { it.id }.toSet() != expectedIds
        ) return scene
        val legacyCamera = SceneCamera(
            id = "camera-main", name = "Under the tree",
            position = Vec3(0.14f, 1.82f, 6.0f),
            target = Vec3(0f, 1.60f, -0.35f),
            verticalFovDegrees = 55f,
        )
        if (scene.cameras != listOf(legacyCamera) ||
            scene.activeCameraId != legacyCamera.id
        ) return scene

        val fresh = create()
        val replacement = mutableMapOf<String, Actor>()
        data class LegacyHead(val yaw: Float, val z: Float)
        val originals = mapOf(
            FIRST_STUDENT_ID to LegacyHead(-18f, 4f),
            SECOND_STUDENT_ID to LegacyHead(18f, -3f),
        )
        for ((actorId, originalHead) in originals) {
            val actor = scene.actors.firstOrNull { it.id == actorId } ?: return scene
            val target = fresh.actors.first { it.id == actorId }
            val oldBodyYaw = if (actorId == FIRST_STUDENT_ID) 164f else 198f
            val oldTransform = target.transform.copy(
                rotationEulerDegrees = Vec3(y = oldBodyYaw),
            )
            // A source-backed model name is required. Imported models and
            // renamed, hidden, locked, attached or adjusted student actors
            // must remain exactly as the artist left them.
            if (actor.asset?.assetId != target.asset?.assetId ||
                actor.name != target.name || !actor.visible || actor.locked ||
                actor.parentId != null || actor.parentBoneId != null ||
                actor.transform != oldTransform ||
                actor.appearance != target.appearance ||
                actor.animation != target.animation
            ) return scene

            val pose = actor.rig ?: return scene
            val baseline = target.rig!!.joints.mapValues { (id, rotation) ->
                if (id == "figure/head") Vec3(y = originalHead.yaw, z = originalHead.z)
                else rotation
            }
            if (pose.joints.size != baseline.size ||
                pose.joints.values.toSet() != baseline.values.toSet()
            ) return scene
            val headId = when {
                "figure/head" in pose.joints -> "figure/head"
                else -> actor.rigDefinition?.bones?.firstOrNull {
                    it.name == "J_Bip_C_Head"
                }?.id ?: return scene
            }
            if (headId !in pose.joints) return scene
            replacement[actorId] = actor.copy(
                transform = target.transform,
                rig = pose.copy(joints = pose.joints +
                    (headId to target.rig.joints.getValue("figure/head"))),
            )
        }
        return scene.copy(
            actors = scene.actors.map { replacement[it.id] ?: it },
            cameras = fresh.cameras,
        )
    }

    /**
     * Upgrade only the unmodified old daylight preset in already-saved sample
     * projects. Runtime rig discovery may have changed student metadata/poses;
     * those edits must never be replaced. Artist-customized world settings
     * and lights are left exactly as they were.
     */
    /**
     * Upgrade the specific earlier five-sphere sample tree only if the artist
     * has not moved/renamed/rescaled it. Artists' edited and imported trees
     * are sacred: never silently replace one of their scene assets.
     */
    fun upgradeUntouchedDemoTree(scene: SceneProject): SceneProject {
        if (scene.id != PROJECT_ID) return scene
        val detailedTree = PrototypeScene.starterAssets().firstOrNull {
            it.asset?.assetId == "starter.cc0.oak-street-tree"
        } ?: return scene
        val oldPosition = Vec3(-0.34f, 0f, -1.23f)
        val oldScale = Vec3(1.16f, 1.16f, 1.16f)
        val original = scene.actors.firstOrNull { it.id == TREE_ID }
        if (original?.name != "Shade tree" ||
            original.asset?.assetId != "starter.mise.tree" ||
            original.transform.position != oldPosition ||
            original.transform.scale != oldScale
        ) return scene
        return scene.copy(actors = scene.actors.map { actor ->
            if (actor.id == TREE_ID) actor.copy(
                name = "Oak shade tree",
                asset = detailedTree.asset,
                transform = actor.transform.copy(
                    position = Vec3(-0.34f, 0f, -1.55f),
                    scale = Vec3(0.70f, 0.70f, 0.70f),
                ),
            ) else actor
        })
    }

    /** Keep the demo's earlier perpendicular stock sun from hiding its shadows.
     * Customized lights stay untouched; pose/hair/scene edits are preserved.
     */
    fun refineStockLightAngle(scene: SceneProject): SceneProject {
        if (scene.id != PROJECT_ID || scene.world.environmentPreset != EnvironmentPreset.PARK_SHADE)
            return scene
        val stockOld = LightSettings(
            type = LightType.DIRECTIONAL,
            intensity = 68_000f,
            colorHex = "#FFFAF4",
            direction = Vec3(0f, -1f, 0f),
            castsShadow = true,
        )
        return scene.copy(actors = scene.actors.map { actor ->
            if (actor.id in setOf("schoolyard-sun", "key-sun") && actor.light == stockOld)
                actor.copy(light = stockOld.copy(direction = Vec3(-0.55f, -1f, -0.45f)))
            else actor
        })
    }

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
            direction = Vec3(-0.55f, -1f, -0.45f), castsShadow = true,
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
