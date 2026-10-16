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
                position = Vec3(2.15f, 1.65f, 4.45f),
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
        if (actor.asset?.assetId != ANIME_ASSET_ID || actor.id != "anime-hero") return actor
        val pose = actor.rig ?: return actor
        val discoveredIds = definition.bones.map { it.id }.toSet()
        if (pose.joints.keys.all { it in discoveredIds }) return actor
        fun slug(value: String) =
            value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val mapped = pose.joints.mapNotNull { (id, rotation) ->
            val name = id.substringAfterLast('/')
            definition.bones.firstOrNull { slug(it.name) == name }?.let { it.id to rotation }
        }.toMap()
        // An incomplete mapping must never silently discard authored pose keys.
        if (mapped.size != pose.joints.size) return actor
        return actor.copy(rig = pose.copy(joints = mapped))
    }

    /** A real original CC0 65-bone Quaternius stylized character, not a generated screenshot. */
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
            source = "https://quaternius.com/packs/universalbasecharacters.html",
            creator = "Quaternius — Universal Base Characters",
            license = "CC0-1.0",
            licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/legalcode",
            attribution = "Stylized Superhero Male from Quaternius CC0 Universal Base Characters, repackaged by programasweights/avatar; pose authored in Mise.",
            version = "b3fd79533fdb9fcedd077744f7e120920eb6cc97",
        ),
        appearance = CharacterAppearanceSettings(hairStyle = HairStyle.NONE),
        rig = if (pose) dynamicAnimePose() else null,
    )

    /** Genuine local-joint rotations; the project loads posed and remains fully editable. */
    private fun dynamicAnimePose(): RigPose = RigPose(
        joints = mapOf(
            // Names are semantic placeholders until the GLB is loaded; resolveAnimePose
            // maps each joint to Filament's discovered stable path before posing.
            // Deliberately leave spine-02 at rest: changing its bind-frame in this GLB
            // makes the entire skin disappear on Android emulator renderers.
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
        return (listOf(animeActor("starter-anime", "Anime Figure", pose = false)) + fixtureAssets + listOf(
            originalActor("Bicycle", ActorKind.VEHICLE),
            originalActor("Car", ActorKind.VEHICLE),
            originalActor("Tree", ActorKind.ENVIRONMENT),
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
