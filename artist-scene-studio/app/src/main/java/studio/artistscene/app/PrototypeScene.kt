package studio.artistscene.app

import studio.artistscene.core.Actor
import studio.artistscene.core.ActorKind
import studio.artistscene.core.AssetReference
import studio.artistscene.core.LightSettings
import studio.artistscene.core.LightType
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

    fun starterAssets(): List<Actor> {
        val fixtureAssets = create().actors.filter {
            it.asset != null && it.kind in setOf(ActorKind.CHARACTER, ActorKind.PROP)
        }
        return (fixtureAssets + listOf(
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
