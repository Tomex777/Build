package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneEditorTest {
    private fun scene() = SceneProject(
        id = "editor-test",
        name = "Editor Test",
        actors = listOf(
            Actor("prop-a", "Prop A", ActorKind.PROP),
            Actor("prop-b", "Prop B", ActorKind.PROP, transform = Transform(position = Vec3(1f, 0f, 0f))),
        ),
    )

    @Test
    fun fullTransformEditingIsUndoableAndRedoable() {
        var state = SceneEditorState(scene()).selectActor("prop-a")
        state = state
            .translate(TransformAxis.X, 1f)
            .translate(TransformAxis.Y, 2f)
            .translate(TransformAxis.Z, -3f)
            .rotate(TransformAxis.Y, 45f)
            .setScale(TransformAxis.X, 1.5f)
            .setScale(TransformAxis.Y, 2f)
            .setScale(TransformAxis.Z, 0.5f)

        val actor = requireNotNull(state.selectedActor)
        assertEquals(Vec3(1f, 2f, -3f), actor.transform.position)
        assertEquals(45f, actor.transform.rotationEulerDegrees.y)
        assertEquals(Vec3(1.5f, 2f, 0.5f), actor.transform.scale)
        assertTrue(state.canUndo)

        val undone = state.undo()
        assertEquals(1f, requireNotNull(undone.selectedActor).transform.scale.z)
        assertTrue(undone.canRedo)

        val redone = undone.redo()
        assertEquals(0.5f, requireNotNull(redone.selectedActor).transform.scale.z)
    }

    @Test
    fun viewportDragPreviewsLiveAndCreatesOneUndoStep() {
        val initial = scene()
        val start = SceneEditorState(initial).selectActor("prop-a")
        val preview1 = start.previewSelectedTransform(
            requireNotNull(start.selectedActor).transform.copy(position = Vec3(0.5f, 0f, 0f)),
        )
        val preview2 = preview1.previewSelectedTransform(
            requireNotNull(preview1.selectedActor).transform.copy(position = Vec3(0.75f, 0f, 0f)),
        )

        assertEquals(0f, requireNotNull(start.selectedActor).transform.position.x)
        assertEquals(0.75f, requireNotNull(preview2.selectedActor).transform.position.x)
        assertFalse(preview2.canUndo)

        val committed = preview2.commitTransformGesture(initial)
        assertTrue(committed.canUndo)
        assertEquals(0.75f, requireNotNull(committed.selectedActor).transform.position.x)
        assertEquals(initial, committed.undo().project)
        assertEquals(0f, requireNotNull(committed.undo().selectedActor).transform.position.x)
    }

    @Test
    fun cameraFramingAndAddedCameraAreProjectOwnedAndUndoable() {
        val start = SceneEditorState(scene())
        val camera = SceneCamera("camera-close", "Close-up")
        val added = start.addCamera(camera)
        assertEquals("camera-close", added.project.activeCameraId)
        assertEquals(2, added.project.cameras.size)

        val framed = added.updateActiveCamera(camera.copy(target = Vec3(1f, 2f, 3f)))
        assertEquals(Vec3(1f, 2f, 3f), framed.project.cameras.last().target)
        assertEquals(camera.target, added.project.cameras.last().target)
        assertEquals(added.project, framed.undo().project)
        assertEquals(start.project, added.undo().project)
    }

    @Test
    fun duplicateDeleteVisibilityRenameAndResetAreSceneOwned() {
        var state = SceneEditorState(scene()).selectActor("prop-a")
        state = state.translate(TransformAxis.X, 3f).duplicateSelected()

        val duplicate = requireNotNull(state.selectedActor)
        assertNotEquals("prop-a", duplicate.id)
        assertEquals(3, state.project.actors.size)
        assertEquals(3.25f, duplicate.transform.position.x)

        state = state.toggleSelectedVisibility()
        assertFalse(requireNotNull(state.selectedActor).visible)

        state = state.renameSelected("Reference Box")
        assertEquals("Reference Box", requireNotNull(state.selectedActor).name)

        state = state.resetTransform()
        assertEquals(Transform(), requireNotNull(state.selectedActor).transform)

        state = state.deleteSelected()
        assertEquals(2, state.project.actors.size)
        assertTrue(state.project.actors.none { it.name == "Reference Box" })
    }

    @Test
    fun lockedActorsRejectDestructiveTransformAndDelete() {
        val locked = scene().copy(
            actors = scene().actors.map { if (it.id == "prop-a") it.copy(locked = true) else it },
        )
        val state = SceneEditorState(locked).selectActor("prop-a")
        assertEquals(state, state.translate(TransformAxis.X, 2f))
        assertEquals(state, state.deleteSelected())
    }

    @Test
    fun reparentRejectsCyclesAndDeleteDetachesChildren() {
        val nested = scene().copy(
            actors = listOf(
                Actor("parent", "Parent", ActorKind.PROP),
                Actor("child", "Child", ActorKind.PROP, parentId = "parent"),
            ),
        )
        var state = SceneEditorState(nested).selectActor("parent")
        assertEquals(state, state.reparentSelected("child"))

        state = state.deleteSelected()
        assertNull(state.project.actors.single().parentId)
    }

    @Test
    fun jointEditsAreUndoableAndResetToImportedRestPose() {
        val bone = RigBone(id = "root/right-arm/elbow", name = "Right Elbow", parentId = "root/right-arm")
        val character = Actor(
            id = "character-a",
            name = "Character A",
            kind = ActorKind.CHARACTER,
            rigDefinition = RigDefinition(bones = listOf(bone)),
        )
        val neutral = SceneEditorState(SceneProject(id = "rig", name = "Rig", actors = listOf(character)))
        val posed = neutral.setRigJointRotation(bone.id, Vec3(z = 25f))

        assertEquals(Vec3(z = 25f), posed.selectedActor?.rig?.joints?.get(bone.id))
        assertTrue(posed.canUndo)
        assertNull(posed.undo().selectedActor?.rig)
        assertEquals(Vec3(z = 25f), posed.undo().redo().selectedActor?.rig?.joints?.get(bone.id))
        assertNull(posed.resetRigJoint(bone.id).selectedActor?.rig)
    }

    @Test
    fun jointDragPreviewsWithoutHistoryAndCommitsOnce() {
        val bone = RigBone("root/arm/elbow", "Elbow")
        val character = Actor("pose-character", "Pose Character", ActorKind.CHARACTER, rigDefinition = RigDefinition(bones = listOf(bone)))
        val start = SceneEditorState(SceneProject(id = "drag", name = "Drag", actors = listOf(character)))
        val preview = start
            .previewRigJointRotation(bone.id, Vec3(z = 5f))
            .previewRigJointRotation(bone.id, Vec3(z = 12f))
            .previewRigJointRotation(bone.id, Vec3(z = 24f))

        assertFalse(preview.canUndo)
        val committed = preview.commitRigGesture(start.project)
        assertTrue(committed.canUndo)
        assertEquals(start.project, committed.undo().project)
        assertEquals(Vec3(z = 24f), committed.undo().redo().selectedActor?.rig?.joints?.get(bone.id))
    }

    @Test
    fun duplicateCharactersKeepIndependentBonePoses() {
        val bone = RigBone(id = "skeleton/arm", name = "Arm")
        val original = Actor(
            id = "character-a",
            name = "Character A",
            kind = ActorKind.CHARACTER,
            rigDefinition = RigDefinition(bones = listOf(bone)),
        )
        val state = SceneEditorState(SceneProject(id = "multi-rig", name = "Multi rig", actors = listOf(original)))
            .setRigJointRotation(bone.id, Vec3(y = 30f))
        val duplicated = state.duplicateSelected()
        val duplicateId = duplicated.selectedActorId
        val independentlyPosed = duplicated.setRigJointRotation(bone.id, Vec3(y = -40f))

        assertEquals(Vec3(y = 30f), independentlyPosed.project.actors.first { it.id == original.id }.rig?.joints?.get(bone.id))
        assertEquals(Vec3(y = -40f), independentlyPosed.project.actors.first { it.id == duplicateId }.rig?.joints?.get(bone.id))
    }
}
