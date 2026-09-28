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
}
