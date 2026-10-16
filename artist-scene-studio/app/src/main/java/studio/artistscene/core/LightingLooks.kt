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

fun SceneEditorState.applyLightingLook(look: LightingLook): SceneEditorState {
    val sunlight = project.actors.firstOrNull {
        it.kind == ActorKind.LIGHT && it.light?.type == LightType.DIRECTIONAL
    } ?: return this
    if (sunlight.locked) return this
    val lighting = sunlight.light ?: return this
    val next = project.copy(
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
    if (next == project) return this
    // Existing editor primitive keeps this transformation atomic in project history.
    return setEnvironmentPreset(look.environment)
        .let { state ->
            val active = state.project.actors.firstOrNull { it.id == sunlight.id } ?: return@let state
            state.selectActor(active.id)
                .setSelectedLightIntensity(look.lux)
                .setSelectedLightColorHex(look.sunColor)
                .let { edited ->
                    // Single combined history operation: undo returns both sky and light at once.
                    edited.copy(
                        project = next,
                        undoStack = (undoStack + project).takeLast(100),
                        redoStack = emptyList(),
                        undoSelections = (undoSelections + selectedActorId).takeLast(100),
                        redoSelections = emptyList(),
                        selectedActorId = selectedActorId,
                    )
                }
        }
}
