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
    }
}
