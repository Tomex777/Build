package studio.artistscene.core

enum class TransformTool { MOVE, ROTATE, SCALE }
enum class TransformAxis { X, Y, Z }

/**
 * Runtime editor state. SceneProject remains the durable source of truth while history and selection
 * stay ephemeral. Every scene mutation records a project snapshot so undo/redo never serializes
 * renderer objects or UI state.
 */
data class SceneEditorState(
    val project: SceneProject,
    val selectedActorId: String? = project.actors.firstOrNull()?.id,
    val activeTool: TransformTool = TransformTool.MOVE,
    val undoStack: List<SceneProject> = emptyList(),
    val redoStack: List<SceneProject> = emptyList(),
) {
    val selectedActor: Actor?
        get() = project.actors.firstOrNull { it.id == selectedActorId }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun selectActor(actorId: String?): SceneEditorState =
        copy(selectedActorId = actorId?.takeIf { id -> project.actors.any { it.id == id } })

    fun useTool(tool: TransformTool): SceneEditorState = copy(activeTool = tool)

    fun translate(axis: TransformAxis, delta: Float): SceneEditorState =
        updateSelectedTransform { transform ->
            transform.copy(position = transform.position.withAxis(axis, transform.position.axis(axis) + delta))
        }

    fun setPosition(axis: TransformAxis, value: Float): SceneEditorState =
        updateSelectedTransform { transform ->
            transform.copy(position = transform.position.withAxis(axis, value))
        }

    fun rotate(axis: TransformAxis, deltaDegrees: Float): SceneEditorState =
        updateSelectedTransform { transform ->
            transform.copy(
                rotationEulerDegrees = transform.rotationEulerDegrees.withAxis(
                    axis,
                    normalizeDegrees(transform.rotationEulerDegrees.axis(axis) + deltaDegrees),
                ),
            )
        }

    fun setRotation(axis: TransformAxis, valueDegrees: Float): SceneEditorState =
        updateSelectedTransform { transform ->
            transform.copy(
                rotationEulerDegrees = transform.rotationEulerDegrees.withAxis(
                    axis,
                    normalizeDegrees(valueDegrees),
                ),
            )
        }

    fun scale(axis: TransformAxis, delta: Float): SceneEditorState =
        updateSelectedTransform { transform ->
            transform.copy(
                scale = transform.scale.withAxis(
                    axis,
                    (transform.scale.axis(axis) + delta).coerceAtLeast(MIN_SCALE),
                ),
            )
        }

    fun setScale(axis: TransformAxis, value: Float): SceneEditorState =
        updateSelectedTransform { transform ->
            transform.copy(scale = transform.scale.withAxis(axis, value.coerceAtLeast(MIN_SCALE)))
        }

    /** Commits one joint rotation edit; values are local offsets from the imported rest pose. */
    fun setRigJointRotation(boneId: String, rotation: Vec3): SceneEditorState {
        return previewRigJointRotation(boneId, rotation).commitRigGesture(project)
    }

    /** Live bone rotation preview. A completed pointer gesture commits one history item. */
    fun previewRigJointRotation(boneId: String, rotation: Vec3): SceneEditorState {
        val actor = selectedActor ?: return this
        if (actor.kind != ActorKind.CHARACTER || actor.locked) return this
        if (actor.rigDefinition?.bones?.none { it.id == boneId } != false) return this
        val joints = actor.rig?.joints.orEmpty().toMutableMap()
        val normalized = Vec3(
            normalizeDegrees(rotation.x),
            normalizeDegrees(rotation.y),
            normalizeDegrees(rotation.z),
        )
        if (normalized == Vec3()) joints.remove(boneId) else joints[boneId] = normalized
        val pose = (actor.rig ?: RigPose()).copy(joints = joints)
            .takeUnless { it.joints.isEmpty() && it.morphWeights.isEmpty() }
        return copy(project = project.copy(actors = project.actors.map {
            if (it.id == actor.id) {
                it.copy(rig = pose, animation = it.animation.copy(playing = false))
            } else it
        }))
    }

    fun commitRigGesture(before: SceneProject): SceneEditorState {
        if (before == project) return this
        return copy(undoStack = (undoStack + before).takeLast(HISTORY_LIMIT), redoStack = emptyList())
    }

    fun cancelRigGesture(before: SceneProject): SceneEditorState = copy(project = before)

    fun resetRigJoint(boneId: String): SceneEditorState {
        val actor = selectedActor ?: return this
        val pose = actor.rig ?: return this
        if (boneId !in pose.joints) return this
        val reset = pose.copy(joints = pose.joints - boneId)
        return replaceSelected(actor.copy(
            rig = reset.takeUnless { it.joints.isEmpty() && it.morphWeights.isEmpty() },
            animation = actor.animation.copy(playing = false),
        ))
    }

    fun resetRigPose(): SceneEditorState {
        val actor = selectedActor ?: return this
        if (actor.rig == null) return this
        return replaceSelected(actor.copy(rig = null, animation = actor.animation.copy(playing = false)))
    }

    /** Runtime discovery enriches durable actor data without adding a spurious undo step. */
    fun withDiscoveredRig(actorId: String, definition: RigDefinition): SceneEditorState {
        val actor = project.actors.firstOrNull { it.id == actorId } ?: return this
        if (actor.kind != ActorKind.CHARACTER || actor.rigDefinition == definition) return this
        return copy(project = project.copy(actors = project.actors.map {
            if (it.id == actorId) it.copy(rigDefinition = definition) else it
        }))
    }

    /** Runtime animation discovery enriches durable actor data without adding undo history. */
    fun withDiscoveredAnimations(
        actorId: String,
        clips: List<AnimationClipDefinition>,
    ): SceneEditorState {
        val actor = project.actors.firstOrNull { it.id == actorId } ?: return this
        val normalized = clips
            .map { it.copy(name = it.name.trim().take(MAX_NAME_LENGTH), durationSeconds = it.durationSeconds.coerceAtLeast(0f)) }
            .filter { it.name.isNotBlank() }
            .distinctBy { it.name }
        val current = actor.animation
        val selected = current.selectedClip?.takeIf { name -> normalized.any { it.name == name } }
            ?: normalized.firstOrNull()?.name
        val nextAnimation = current.copy(
            clips = normalized,
            selectedClip = selected,
            playing = current.playing && selected != null,
        )
        if (nextAnimation == current) return this
        return copy(project = project.copy(actors = project.actors.map {
            if (it.id == actorId) it.copy(animation = nextAnimation) else it
        }))
    }

    fun selectAnimationClip(name: String): SceneEditorState {
        val actor = selectedActor ?: return this
        if (actor.animation.clips.none { it.name == name }) return this
        if (actor.animation.selectedClip == name && !actor.animation.playing) return this
        return replaceSelected(actor.copy(
            animation = actor.animation.copy(selectedClip = name, playing = false),
        ))
    }

    fun setSelectedAnimationPlaying(playing: Boolean): SceneEditorState {
        val actor = selectedActor ?: return this
        val animation = actor.animation
        if (playing && animation.selectedClip == null) return this
        if (animation.playing == playing) return this
        return replaceSelected(actor.copy(animation = animation.copy(playing = playing)))
    }

    fun toggleSelectedAnimationLoop(): SceneEditorState {
        val actor = selectedActor ?: return this
        if (actor.animation.clips.isEmpty()) return this
        return replaceSelected(actor.copy(animation = actor.animation.copy(loop = !actor.animation.loop)))
    }

    fun setSelectedAnimationSpeed(speed: Float): SceneEditorState {
        val actor = selectedActor ?: return this
        if (actor.animation.clips.isEmpty()) return this
        val normalized = speed.coerceIn(MIN_ANIMATION_SPEED, MAX_ANIMATION_SPEED)
        if (normalized == actor.animation.speed) return this
        return replaceSelected(actor.copy(animation = actor.animation.copy(speed = normalized)))
    }

    /** Applies a live viewport preview without adding one undo entry per pointer sample. */
    fun previewSelectedTransform(transform: Transform): SceneEditorState {
        val actor = selectedActor ?: return this
        if (actor.locked) return this
        return copy(project = project.copy(actors = project.actors.map {
            if (it.id == actor.id) it.copy(transform = transform) else it
        }))
    }

    /** Commits one completed viewport drag to history. */
    fun commitTransformGesture(before: SceneProject): SceneEditorState {
        if (before == project) return this
        return copy(
            undoStack = (undoStack + before).takeLast(HISTORY_LIMIT),
            redoStack = emptyList(),
        )
    }

    fun cancelTransformGesture(before: SceneProject): SceneEditorState = copy(project = before)

    fun scaleUniform(delta: Float): SceneEditorState {
        val actor = selectedActor ?: return this
        if (actor.locked) return this
        val next = (actor.transform.scale.x + delta).coerceAtLeast(MIN_SCALE)
        return replaceSelected(actor.copy(transform = actor.transform.copy(scale = Vec3(next, next, next))))
    }

    fun resetTransform(): SceneEditorState {
        val actor = selectedActor ?: return this
        if (actor.locked) return this
        return replaceSelected(actor.copy(transform = Transform()))
    }

    fun toggleSelectedVisibility(): SceneEditorState {
        val actor = selectedActor ?: return this
        return replaceSelected(actor.copy(visible = !actor.visible))
    }

    fun toggleSelectedLocked(): SceneEditorState {
        val actor = selectedActor ?: return this
        return replaceSelected(actor.copy(locked = !actor.locked))
    }

    fun renameSelected(name: String): SceneEditorState {
        val actor = selectedActor ?: return this
        val normalized = name.trim().take(MAX_NAME_LENGTH)
        if (normalized.isEmpty() || normalized == actor.name) return this
        return replaceSelected(actor.copy(name = normalized))
    }

    fun duplicateSelected(): SceneEditorState {
        val actor = selectedActor ?: return this
        val id = uniqueActorId(actor.id + "-copy")
        val copy = actor.copy(
            id = id,
            name = uniqueActorName(actor.name + " Copy"),
            parentId = actor.parentId,
            transform = actor.transform.copy(
                position = actor.transform.position.copy(x = actor.transform.position.x + 0.25f),
            ),
        )
        return commit(project.copy(actors = project.actors + copy), selected = id)
    }

    fun deleteSelected(): SceneEditorState {
        val actor = selectedActor ?: return this
        if (actor.locked) return this
        val remaining = project.actors
            .filterNot { it.id == actor.id }
            .map { child -> if (child.parentId == actor.id) child.copy(parentId = null) else child }
        val nextSelection = remaining.firstOrNull()?.id
        return commit(project.copy(actors = remaining), selected = nextSelection)
    }

    fun addActor(actor: Actor): SceneEditorState {
        require(project.actors.none { it.id == actor.id }) { "Actor ID already exists: ${actor.id}" }
        return commit(project.copy(actors = project.actors + actor), selected = actor.id)
    }

    fun addCamera(camera: SceneCamera, activate: Boolean = true): SceneEditorState {
        require(project.cameras.none { it.id == camera.id }) { "Camera ID already exists: ${camera.id}" }
        val next = project.copy(
            cameras = project.cameras + camera,
            activeCameraId = if (activate) camera.id else project.activeCameraId,
        )
        return commit(next, selectedActorId)
    }

    fun updateActiveCamera(camera: SceneCamera): SceneEditorState {
        if (project.cameras.none { it.id == camera.id }) return this
        return commit(
            project.copy(cameras = project.cameras.map { if (it.id == camera.id) camera else it }),
            selectedActorId,
        )
    }

    fun activateCamera(cameraId: String): SceneEditorState {
        if (cameraId == project.activeCameraId || project.cameras.none { it.id == cameraId }) return this
        return commit(project.copy(activeCameraId = cameraId), selectedActorId)
    }

    fun setActiveCameraProjection(projection: CameraProjection): SceneEditorState {
        val camera = project.cameras.firstOrNull { it.id == project.activeCameraId } ?: return this
        if (camera.projection == projection) return this
        return updateActiveCamera(camera.copy(projection = projection))
    }

    fun setActiveCameraVerticalFov(degrees: Float): SceneEditorState {
        val camera = project.cameras.firstOrNull { it.id == project.activeCameraId } ?: return this
        val normalized = degrees.coerceIn(MIN_CAMERA_FOV, MAX_CAMERA_FOV)
        if (camera.verticalFovDegrees == normalized) return this
        return updateActiveCamera(camera.copy(verticalFovDegrees = normalized))
    }

    fun setActiveCameraOrthographicHeight(meters: Float): SceneEditorState {
        val camera = project.cameras.firstOrNull { it.id == project.activeCameraId } ?: return this
        val normalized = meters.coerceIn(MIN_ORTHOGRAPHIC_HEIGHT, MAX_ORTHOGRAPHIC_HEIGHT)
        if (camera.orthographicHeightMeters == normalized) return this
        return updateActiveCamera(camera.copy(orthographicHeightMeters = normalized))
    }

    fun setSelectedLightIntensity(value: Float): SceneEditorState =
        updateSelectedLight { settings ->
            settings.copy(intensity = value.coerceIn(0f, MAX_LIGHT_INTENSITY))
        }

    fun setSelectedLightColorHex(value: String): SceneEditorState {
        val normalized = value.trim().uppercase()
        if (!Regex("^#[0-9A-F]{6}$").matches(normalized)) return this
        return updateSelectedLight { settings -> settings.copy(colorHex = normalized) }
    }

    fun setSelectedLightRangeMeters(value: Float): SceneEditorState =
        updateSelectedLight { settings ->
            settings.copy(rangeMeters = value.coerceIn(MIN_LIGHT_RANGE_METERS, MAX_LIGHT_RANGE_METERS))
        }

    fun toggleSelectedLightShadows(): SceneEditorState =
        updateSelectedLight { settings -> settings.copy(castsShadow = !settings.castsShadow) }

    private fun updateSelectedLight(change: (LightSettings) -> LightSettings): SceneEditorState {
        val actor = selectedActor ?: return this
        val settings = actor.light ?: return this
        if (actor.kind != ActorKind.LIGHT || actor.locked) return this
        return replaceSelected(actor.copy(light = change(settings)))
    }

    fun reparentSelected(parentId: String?): SceneEditorState {
        val actor = selectedActor ?: return this
        if (parentId == actor.id) return this
        if (parentId != null && project.actors.none { it.id == parentId }) return this
        if (wouldCreateCycle(actor.id, parentId)) return this
        return replaceSelected(actor.copy(parentId = parentId))
    }

    fun undo(): SceneEditorState {
        if (undoStack.isEmpty()) return this
        val previous = undoStack.last()
        return copy(
            project = previous,
            selectedActorId = selectedActorId.validFor(previous),
            undoStack = undoStack.dropLast(1),
            redoStack = (redoStack + project).takeLast(HISTORY_LIMIT),
        )
    }

    fun redo(): SceneEditorState {
        if (redoStack.isEmpty()) return this
        val next = redoStack.last()
        return copy(
            project = next,
            selectedActorId = selectedActorId.validFor(next),
            undoStack = (undoStack + project).takeLast(HISTORY_LIMIT),
            redoStack = redoStack.dropLast(1),
        )
    }

    fun replaceProject(next: SceneProject, preserveSelection: Boolean = true): SceneEditorState =
        copy(
            project = next,
            selectedActorId = if (preserveSelection) selectedActorId.validFor(next) else next.actors.firstOrNull()?.id,
            undoStack = emptyList(),
            redoStack = emptyList(),
        )

    private fun updateSelectedTransform(change: (Transform) -> Transform): SceneEditorState {
        val actor = selectedActor ?: return this
        if (actor.locked) return this
        return replaceSelected(actor.copy(transform = change(actor.transform)))
    }

    private fun replaceSelected(actor: Actor): SceneEditorState {
        val old = selectedActor ?: return this
        if (old == actor) return this
        return commit(
            project.copy(actors = project.actors.map { if (it.id == old.id) actor else it }),
            selected = actor.id,
        )
    }

    private fun commit(nextProject: SceneProject, selected: String?): SceneEditorState {
        if (nextProject == project) return copy(selectedActorId = selected)
        return copy(
            project = nextProject,
            selectedActorId = selected.validFor(nextProject),
            undoStack = (undoStack + project).takeLast(HISTORY_LIMIT),
            redoStack = emptyList(),
        )
    }

    private fun uniqueActorId(base: String): String {
        if (project.actors.none { it.id == base }) return base
        var index = 2
        while (project.actors.any { it.id == "$base-$index" }) index++
        return "$base-$index"
    }

    private fun uniqueActorName(base: String): String {
        if (project.actors.none { it.name == base }) return base
        var index = 2
        while (project.actors.any { it.name == "$base $index" }) index++
        return "$base $index"
    }

    private fun wouldCreateCycle(actorId: String, proposedParentId: String?): Boolean {
        var current = proposedParentId
        val seen = mutableSetOf<String>()
        while (current != null && seen.add(current)) {
            if (current == actorId) return true
            current = project.actors.firstOrNull { it.id == current }?.parentId
        }
        return false
    }

    private fun String?.validFor(project: SceneProject): String? =
        this?.takeIf { id -> project.actors.any { it.id == id } } ?: project.actors.firstOrNull()?.id

    private fun Vec3.axis(axis: TransformAxis): Float = when (axis) {
        TransformAxis.X -> x
        TransformAxis.Y -> y
        TransformAxis.Z -> z
    }

    private fun Vec3.withAxis(axis: TransformAxis, value: Float): Vec3 = when (axis) {
        TransformAxis.X -> copy(x = value)
        TransformAxis.Y -> copy(y = value)
        TransformAxis.Z -> copy(z = value)
    }

    private fun normalizeDegrees(value: Float): Float {
        var result = value % 360f
        if (result > 180f) result -= 360f
        if (result <= -180f) result += 360f
        return result
    }

    private companion object {
        const val HISTORY_LIMIT = 50
        const val MIN_SCALE = 0.01f
        const val MAX_NAME_LENGTH = 80
        const val MAX_LIGHT_INTENSITY = 500_000f
        const val MIN_LIGHT_RANGE_METERS = 0.1f
        const val MAX_LIGHT_RANGE_METERS = 100f
        const val MIN_ANIMATION_SPEED = 0.1f
        const val MAX_ANIMATION_SPEED = 3f
        const val MIN_CAMERA_FOV = 15f
        const val MAX_CAMERA_FOV = 120f
        const val MIN_ORTHOGRAPHIC_HEIGHT = 0.2f
        const val MAX_ORTHOGRAPHIC_HEIGHT = 50f
    }
}
