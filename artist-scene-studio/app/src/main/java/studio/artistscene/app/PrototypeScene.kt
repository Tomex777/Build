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

    fun create() = SceneProject(
        id = PROJECT_ID,
        name = "Scene Studio Test Stage",
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
                    version = "7d4ba189827916452eeadc82d4b712dbc6280a6f",
                ),
            ),
            Actor(
                id = "key-sun",
                name = "Key Sun",
                kind = ActorKind.LIGHT,
                light = LightSettings(
                    type = LightType.DIRECTIONAL,
                    intensity = 110_000f,
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
                    intensity = 1_400f,
                    castsShadow = false,
                ),
            ),
        ),
        cameras = listOf(
            SceneCamera(
                id = "camera-main",
                name = "Main Camera",
                position = Vec3(0f, 0.8f, 2.6f),
                target = Vec3(0f, 0f, 0f),
            ),
        ),
    )
}
