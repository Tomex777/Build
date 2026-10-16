package studio.artistscene.core

/** Fully offline, undoable artistic lighting studies. No fake image overlays or HDRI claims. */
enum class LightingLook(
    val title: String,
    val environment: EnvironmentPreset,
    val sunColor: String,
    val lux: Float,
    val direction: Vec3,
) {
    DAYLIGHT("Daylight", EnvironmentPreset.DAYLIGHT, "#FFF5E9", 72_000f, Vec3(-0.5f, -1f, -0.45f)),
    SOFTBOX("Softbox", EnvironmentPreset.STUDIO_GREY, "#FFFFFF", 45_000f, Vec3(-0.35f, -1f, -0.75f)),
    GOLDEN("Golden hour", EnvironmentPreset.SUNSET, "#FFCC9B", 38_000f, Vec3(0.7f, -0.45f, -0.4f)),
    OVERCAST("Overcast", EnvironmentPreset.OVERCAST, "#D8E7FF", 24_000f, Vec3(-0.1f, -1f, -0.3f)),
    LOW_KEY("Low key", EnvironmentPreset.REFERENCE, "#BFCBFF", 12_000f, Vec3(0.6f, -0.6f, 0.2f)),
}

/** Pure scene transformation; SceneEditorState commits it as ONE undo step. */
fun lightingLookProject(project: SceneProject, look: LightingLook): SceneProject {
    val sunlight = project.actors.firstOrNull {
        it.kind == ActorKind.LIGHT && it.light?.type == LightType.DIRECTIONAL
    } ?: return project
    if (sunlight.locked) return project
    val lighting = sunlight.light ?: return project
    return project.copy(
        world = project.world.copy(environmentPreset = look.environment),
        actors = project.actors.map { actor ->
            if (actor.id == sunlight.id) actor.copy(
                visible = true,
                light = lighting.copy(
                    intensity = look.lux,
                    colorHex = look.sunColor,
                    direction = look.direction,
                    castsShadow = true,
                ),
            ) else actor
        },
    )
}
