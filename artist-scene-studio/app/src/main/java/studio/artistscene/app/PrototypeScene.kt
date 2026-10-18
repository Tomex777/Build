package studio.artistscene.app

import studio.artistscene.core.Actor
import studio.artistscene.core.ActorKind
import studio.artistscene.core.AssetReference
import studio.artistscene.core.LightSettings
import studio.artistscene.core.LightType
import studio.artistscene.core.HairStyle
import studio.artistscene.core.CharacterAppearanceSettings
import studio.artistscene.core.RigPose
import studio.artistscene.core.RigDefinition
import studio.artistscene.core.WorldSettings
import studio.artistscene.core.EnvironmentPreset
import studio.artistscene.core.SceneProject
import studio.artistscene.core.SceneCamera
import studio.artistscene.core.Transform
import studio.artistscene.core.Vec3

object PrototypeScene {
    const val PROJECT_ID = "feasibility-stage"
    const val PROP_ID = "fixture-boombox"
    const val CHARACTER_ID = "fixture-cesium-man"
    const val SECOND_CHARACTER_ID = "fixture-cesium-man-b"
    const val RIGGED_FIGURE_ASSET_ID = "starter.khronos.rigged-figure"
    const val COLOR_CUBE_ASSET_ID = "starter.khronos.color-cube"
    const val HUMANOID_ASSET_ID = "starter.makehuman.humanoid"
    const val ANIME_ASSET_ID = "starter.mise.anime-figure"
    const val ANIME_FEMALE_ASSET_ID = "starter.mise.anime-figure-female"
    const val ANIME_POSE_PROJECT_ID = "anime-pose-study-v1"

    /** Keep the tested Cesium starter intact. This independent scene leads with a posed character. */
    fun createAnimePoseStudy(): SceneProject = SceneProject(
        id = ANIME_POSE_PROJECT_ID,
        name = "Anime Pose Study",
        actors = listOf(
            animeActor("anime-hero", "Anime · Dynamic pose", Vec3(), pose = true),
        ) + create().actors.filter { it.kind == ActorKind.LIGHT },
        cameras = listOf(
            SceneCamera(
                id = "camera-main", name = "Anime pose camera",
                // Stand in front of the VRoid character (-Z), avoiding changes to the
                // skinned root transform that broke emulator skin deformation.
                // Larger portrait-to-full-body framing keeps the face and feet legible.
                position = Vec3(-1.3f, 1.52f, -3.35f),
                target = Vec3(0f, .93f, 0f),
            ),
        ),
        world = WorldSettings(environmentPreset = EnvironmentPreset.DAYLIGHT),
    )

    /**
     * Filament can prefix imported bone paths differently across SceneView releases.
     * Bind the authored real-joint hero pose by *discovered* bone name when needed.
     * Other characters, bones and user-authored poses remain untouched.
     */
    fun resolveAnimePose(actor: Actor, definition: RigDefinition): Actor {
        val isHero = actor.asset?.assetId == ANIME_ASSET_ID && actor.id == "anime-hero"
        val isUnderTreeStudent = actor.metadata["misePose"] == UnderTreeScene.POSE_TAG &&
            actor.asset?.assetId in setOf(ANIME_ASSET_ID, ANIME_FEMALE_ASSET_ID)
        if (!isHero && !isUnderTreeStudent) return actor
        val pose = actor.rig ?: return actor
        val discoveredIds = definition.bones.map { it.id }.toSet()
        if (pose.joints.keys.all { it in discoveredIds }) return actor
        // VRoid names joints J_Bip_L_UpperArm, J_Bip_R_LowerLeg, etc., while
        // other rigged imports use upperarm_l or LeftUpperArm. Match semantics
        // and left/right limbs, never depend on one vendor's skeleton prefix.
        fun slug(value: String) =
            value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        fun matches(name: String, target: String): Boolean {
            val requested = target.substringAfterLast('/')
            val side = requested.substringAfterLast('-', "")
            val rawPart = requested.substringBeforeLast('-', requested)
            val part = when (rawPart) {
                "thigh" -> "upperleg"
                "calf" -> "lowerleg"
                else -> rawPart
            }
            val clean = slug(name)
            val flat = clean.replace("-", "")
            val needle = part.replace("-", "")
            val isCorrectPart = flat.contains(needle) ||
                (part == "upperleg" && flat.contains("thigh")) ||
                (part == "lowerleg" && flat.contains("calf"))
            if (!isCorrectPart) return false
            if (side.isEmpty()) return true
            val tokens = clean.split('-')
            return if (side == "l") {
                "l" in tokens || "left" in tokens || flat.startsWith("left") ||
                    flat.endsWith("left") || flat.endsWith("l")
            } else {
                "r" in tokens || "right" in tokens || flat.startsWith("right") ||
                    flat.endsWith("right") || flat.endsWith("r")
            }
        }
        val mapped = pose.joints.mapNotNull { (id, rotation) ->
            val requested = id.substringAfterLast('/')
            definition.bones.firstOrNull { bone ->
                slug(bone.name) == requested || matches(bone.name, requested)
            }?.let { it.id to rotation }
        }.toMap()
        // An incomplete mapping must never silently discard authored pose keys.
        if (mapped.size != pose.joints.size) return actor
        return actor.copy(rig = pose.copy(joints = mapped))
    }

    /** Genuine CC0 VRoid anime avatar with a rendered face, hair, clothes and real skeleton. */
    private fun animeActor(
        id: String,
        name: String,
        position: Vec3 = Vec3(),
        pose: Boolean = false,
    ) = Actor(
        id = id,
        name = name,
        kind = ActorKind.CHARACTER,
        transform = Transform(position = position),
        asset = AssetReference(
            assetId = ANIME_ASSET_ID,
            relativePath = "models/mise_anime.glb",
            format = "glb",
            source = "https://github.com/madjin/vrm-samples/tree/e16eb187100149a315ad92c3c9968f1d5baa6c7d/vroid/beta",
            creator = "pixiv Inc. — VRoid Studio (beta HairSample_Male)",
            license = "CC0-1.0",
            licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/legalcode",
            attribution = "pixiv VRoid Studio beta HairSample_Male, designated CC0; MIME-normalized for Filament while preserving skinned mesh, textures and rig. Authored pose by Mise.",
            version = "46e8c583ce5df3b8ee167141b59ba3ad583a2687",
        ),
        appearance = CharacterAppearanceSettings(hairStyle = HairStyle.NONE),
        rig = if (pose) dynamicAnimePose() else null,
    )

    /** Distinct official VRoid CC0 female model, including sculpted hair, outfit and morphs. */
    private fun femaleAnimeActor(id: String) = Actor(
        id = id,
        name = "Anime Figure · Female",
        kind = ActorKind.CHARACTER,
        asset = AssetReference(
            assetId = ANIME_FEMALE_ASSET_ID,
            relativePath = "models/mise_anime_female.glb",
            format = "glb",
            source = "https://github.com/madjin/vrm-samples/tree/e16eb187100149a315ad92c3c9968f1d5baa6c7d/vroid/beta",
            creator = "pixiv Inc. — VRoid Studio (beta HairSample_Female)",
            license = "CC0-1.0",
            licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/legalcode",
            attribution = "pixiv VRoid Studio beta HairSample_Female, designated CC0; converted losslessly to Filament-compatible glTF.",
            version = "0e5235f9a7972fbcbceb74960479b208c27a8707",
        ),
    )

    /** Genuine local-joint rotations; the project loads posed and remains fully editable. */
    private fun dynamicAnimePose(): RigPose = RigPose(
        joints = mapOf(
            // Names are semantic placeholders until the GLB is loaded; resolveAnimePose
            // maps each joint to Filament's discovered stable path before posing.
            // Avoid extreme spine rotation to keep an expressive, grounded pose.
            "figure/head" to Vec3(y = -9f, z = 9f),
            "figure/upperarm-l" to Vec3(x = -20f, z = 54f),
            "figure/lowerarm-l" to Vec3(x = -15f, z = 80f),
            "figure/upperarm-r" to Vec3(x = 16f, z = -45f),
            "figure/lowerarm-r" to Vec3(x = 18f, z = -58f),
            "figure/thigh-l" to Vec3(x = -23f, z = 14f),
            "figure/calf-l" to Vec3(x = 24f),
            "figure/thigh-r" to Vec3(x = 16f, z = -16f),
            "figure/calf-r" to Vec3(x = 17f),
        ),
    )

    fun starterAssets(): List<Actor> {
        val fixtureAssets = create().actors.filter {
            it.asset != null && it.kind in setOf(ActorKind.CHARACTER, ActorKind.PROP)
        }
        return (listOf(
            animeActor("starter-anime", "Anime Figure · Male", pose = false),
            femaleAnimeActor("starter-anime-female"),
        ) + fixtureAssets + listOf(
            originalActor("Bicycle", ActorKind.VEHICLE),
            originalActor("Car", ActorKind.VEHICLE),
            originalActor("Tree", ActorKind.ENVIRONMENT),
            Actor(
                id = "starter-two-storey-house",
                name = "Two-storey House · Detailed",
                kind = ActorKind.ENVIRONMENT,
                asset = AssetReference(
                    assetId = "starter.cc0.two-storey-house",
                    relativePath = "models/mise_house_two_storey.glb",
                    source = "https://www.innerscene.com/tools/library/3d-parts/detached-two-storey-house-66774933",
                    creator = "Innerscene",
                    license = "CC0-1.0",
                    licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/legalcode",
                    attribution = "Original unbranded Innerscene two-storey detailed house; CC0.",
                    version = "2026-10-08",
                ),
                metadata = mapOf("assetCategory" to "Architecture"),
            ),
            Actor(
                id = "starter-bungalow-house",
                name = "Bungalow · Detailed",
                kind = ActorKind.ENVIRONMENT,
                asset = AssetReference(
                    assetId = "starter.cc0.bungalow-house",
                    relativePath = "models/mise_house_bungalow.glb",
                    source = "https://www.innerscene.com/tools/library/3d-parts/bungalow-house-85d40048",
                    creator = "Innerscene",
                    license = "CC0-1.0",
                    licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/legalcode",
                    attribution = "Original real-scale textured Innerscene bungalow house; CC0.",
                    version = "2026-10-08",
                ),
                metadata = mapOf("assetCategory" to "Architecture"),
            ),
            Actor(
                id = "starter-detailed-sedan",
                name = "Sedan · Real Scale",
                kind = ActorKind.VEHICLE,
                asset = AssetReference(
                    assetId = "starter.cc0.sedan-car",
                    relativePath = "models/mise_sedan.glb",
                    source = "https://www.innerscene.com/tools/library/3d-parts/sedan-car-42109739",
                    creator = "Innerscene",
                    license = "CC0-1.0",
                    licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/legalcode",
                    attribution = "Original CC0 unbranded exterior geometry with glazing, doors, wheels and trim.",
                    version = "2026-10-05",
                ),
                metadata = mapOf("assetCategory" to "Vehicles"),
            ),
            Actor(
                id = "starter-oak-tree",
                name = "Oak Tree · Detailed Leaves",
                kind = ActorKind.ENVIRONMENT,
                asset = AssetReference(
                    assetId = "starter.cc0.oak-street-tree",
                    relativePath = "models/mise_oak_tree.glb",
                    format = "glb",
                    creator = "Innerscene",
                    source = "https://www.innerscene.com/tools/library/3d-parts/oak-street-tree-detailed-leaves-a273fef9",
                    license = "CC0-1.0",
                    licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/legalcode",
                    attribution = "Original Innerscene CC0 oak model. 9,450 native geometric leaves.",
                    version = "2026-10-05",
                ),
            ),
            Actor(
                id = "starter-mise-humanoid",
                name = "Humanoid",
                kind = ActorKind.CHARACTER,
                asset = AssetReference(
                    assetId = HUMANOID_ASSET_ID,
                    relativePath = "models/mise_humanoid.glb",
                    format = "glb",
                    source = "https://github.com/makehumancommunity/makehuman/tree/a8bc2d54ff0ac92e78ff71431b1023eda42bf482",
                    creator = "MakeHuman Community",
                    license = "CC0-1.0",
                    licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/legalcode",
                    attribution = "CC0 graphical assets, independently converted for Mise. Source and license retained in the app.",
                    version = "a8bc2d54ff0ac92e78ff71431b1023eda42bf482",
                ),
            ),
            Actor(
                id = "starter-rigged-figure",
                name = "Rigged Figure",
                kind = ActorKind.CHARACTER,
                asset = AssetReference(
                    assetId = RIGGED_FIGURE_ASSET_ID,
                    relativePath = "models/rigged_figure.glb",
                    format = "glb",
                    source = "https://github.com/KhronosGroup/glTF-Sample-Assets/tree/7d4ba189827916452eeadc82d4b712dbc6280a6f/Models/RiggedFigure",
                    creator = "Cesium",
                    license = "CC-BY-4.0",
                    licenseUrl = "https://creativecommons.org/licenses/by/4.0/legalcode",
                    attribution = "Credit Cesium and the Khronos glTF Sample Assets source under CC-BY-4.0.",
                    version = "7d4ba189827916452eeadc82d4b712dbc6280a6f",
                ),
            ),
            Actor(
                id = "starter-color-cube",
                name = "Color Cube",
                kind = ActorKind.PROP,
                asset = AssetReference(
                    assetId = COLOR_CUBE_ASSET_ID,
                    relativePath = "models/color_cube.glb",
                    format = "glb",
                    source = "https://github.com/KhronosGroup/glTF-Sample-Assets/tree/7d4ba189827916452eeadc82d4b712dbc6280a6f/Models/BoxVertexColors",
                    creator = "Marco Hutter",
                    license = "CC0-1.0",
                    licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/legalcode",
                    attribution = "Attribution is not required by CC0; creator and source are retained as provenance.",
                    version = "7d4ba189827916452eeadc82d4b712dbc6280a6f",
                ),
            ),
        )).distinctBy { it.asset?.assetId }
    }

    private fun originalActor(name: String, kind: ActorKind): Actor {
        val slug = name.lowercase()
        return Actor(
            id = "mise-$slug",
            name = name,
            kind = kind,
            asset = AssetReference(
                assetId = "starter.mise.$slug",
                relativePath = "models/mise_$slug.glb",
                source = "https://github.com/Tomex777/Build/blob/artist-scene-studio-foundation/artist-scene-studio/scripts/build-scene-actors.py",
                creator = "Mise",
                license = "CC0-1.0",
                licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/legalcode",
                attribution = "Original procedural Mise starter geometry. Released under CC0-1.0.",
                version = "1",
            ),
        )
    }

    fun create() = SceneProject(
        id = PROJECT_ID,
        name = "Starter Scene",
        actors = listOf(
            Actor(
                id = PROP_ID,
                name = "Boom Box",
                kind = ActorKind.PROP,
                asset = AssetReference(
                    assetId = "fixture.khronos.boom-box",
                    relativePath = "models/boom_box.glb",
                    format = "glb",
                    source = "https://github.com/KhronosGroup/glTF-Sample-Assets/tree/7d4ba189827916452eeadc82d4b712dbc6280a6f/Models/BoomBox",
                    creator = "Microsoft",
                    license = "CC0-1.0",
                    licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/legalcode",
                    attribution = "Attribution is not required by CC0; creator and source are retained as provenance.",
                    version = "7d4ba189827916452eeadc82d4b712dbc6280a6f",
                ),
            ),
            Actor(
                id = CHARACTER_ID,
                name = "Cesium Man",
                kind = ActorKind.CHARACTER,
                transform = Transform(position = Vec3(0.9f, 0f, 0f)),
                asset = AssetReference(
                    assetId = "fixture.khronos.cesium-man",
                    relativePath = "models/cesium_man.glb",
                    format = "glb",
                    source = "https://github.com/KhronosGroup/glTF-Sample-Assets/tree/7d4ba189827916452eeadc82d4b712dbc6280a6f/Models/CesiumMan",
                    creator = "Cesium",
                    license = "CC-BY-4.0",
                    licenseUrl = "https://creativecommons.org/licenses/by/4.0/legalcode",
                    attribution = "Credit Cesium and the Khronos glTF Sample Assets source under CC-BY-4.0.",
                    version = "7d4ba189827916452eeadc82d4b712dbc6280a6f",
                ),
            ),
            Actor(
                id = SECOND_CHARACTER_ID,
                name = "Cesium Man B",
                kind = ActorKind.CHARACTER,
                transform = Transform(position = Vec3(-0.9f, 0f, 0f)),
                asset = AssetReference(
                    assetId = "fixture.khronos.cesium-man",
                    relativePath = "models/cesium_man.glb",
                    format = "glb",
                    source = "https://github.com/KhronosGroup/glTF-Sample-Assets/tree/7d4ba189827916452eeadc82d4b712dbc6280a6f/Models/CesiumMan",
                    creator = "Cesium",
                    license = "CC-BY-4.0",
                    licenseUrl = "https://creativecommons.org/licenses/by/4.0/legalcode",
                    attribution = "Credit Cesium and the Khronos glTF Sample Assets source under CC-BY-4.0.",
                    version = "7d4ba189827916452eeadc82d4b712dbc6280a6f",
                ),
            ),
            Actor(
                id = "key-sun",
                name = "Key Sun",
                kind = ActorKind.LIGHT,
                light = LightSettings(
                    type = LightType.DIRECTIONAL,
                    intensity = 72_000f,
                    castsShadow = true,
                ),
            ),
            Actor(
                id = "fill-light",
                name = "Fill Light",
                kind = ActorKind.LIGHT,
                transform = Transform(position = Vec3(1.2f, 1.5f, 1.5f)),
                light = LightSettings(
                    type = LightType.POINT,
                    intensity = 2_200f,
                    castsShadow = false,
                ),
            ),
        ),
        cameras = listOf(
            SceneCamera(
                id = "camera-main",
                name = "Main Camera",
                position = Vec3(0f, 1.15f, 6.2f),
                target = Vec3(0f, 0.8f, 0f),
            ),
        ),
    )
}
