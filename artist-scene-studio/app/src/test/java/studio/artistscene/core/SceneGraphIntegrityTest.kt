package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Historical and imported projects are not modified by decoding or browsing. Editing cannot
 * spread an existing broken parent chain; the artist can repair it with an undoable reparent.
 */
class SceneGraphIntegrityTest {
    private val character = Actor(
        id = "character",
        name = "Character",
        kind = ActorKind.CHARACTER,
        rigDefinition = RigDefinition(bones = listOf(RigBone("hand", "Right hand"))),
    )
    private val prop = Actor("prop", "Book", ActorKind.PROP)

    @Test
    fun cannotAttachIntoPreExistingParentCycleThatDoesNotIncludeSelectedProp() {
        val a = character.copy(parentId = "b")
        val b = Actor("b", "Cyclic vehicle", ActorKind.VEHICLE, parentId = "character")
        val original = SceneProject(id = "scene-cycle", name = "Imported old scene",
            actors = listOf(a, b, prop))
        val reopened = SceneProjectCodec.decode(SceneProjectCodec.encode(original))
        assertEquals(original, reopened) // No automatic data loss during migration or reopen.
        val editor = SceneEditorState(reopened).selectActor("prop")
        assertFalse(editor.canReparentSelected("character"))
        assertEquals(editor, editor.reparentSelected("character"))
        assertEquals(editor, editor.attachSelectedToBone("character", "hand"))
        assertFalse(editor.canUndo)
        // Repairing a damaged ancestor remains possible and is itself reversible.
        val repaired = editor.selectActor("character").reparentSelected(null)
        assertEquals(null, repaired.selectedActor?.parentId)
        assertEquals(original, repaired.undo().project)
        assertEquals(repaired.project, repaired.undo().redo().project)
        assertTrue(repaired.selectActor("prop").canReparentSelected("character"))
        val attached = repaired.selectActor("prop").attachSelectedToBone("character", "hand")
        assertEquals("character", attached.selectedActor?.parentId)
        assertEquals("hand", attached.selectedActor?.parentBoneId)
    }

    @Test
    fun cannotParentBelowMissingAncestorUntilItIsExplicitlyRepaired() {
        val orphan = Actor("orphan", "Legacy prop", ActorKind.PROP, parentId = "deleted-actor")
        val scene = SceneProject(id = "dangling", name = "Dangling legacy parent",
            actors = listOf(orphan, prop))
        val editor = SceneEditorState(SceneProjectCodec.decode(SceneProjectCodec.encode(scene)))
            .selectActor("prop")

        assertFalse(editor.canReparentSelected("orphan"))
        assertEquals(scene, editor.reparentSelected("orphan").project)
        assertEquals(scene, editor.project)
        assertEquals(scene, SceneProjectCodec.decode(SceneProjectCodec.encode(scene)))

        val repair = editor.selectActor("orphan").reparentSelected(null)
        assertTrue(repair.canUndo)
        assertEquals(scene, repair.undo().project)
        assertTrue(repair.selectActor("prop").canReparentSelected("orphan"))
        val nested = repair.selectActor("prop").reparentSelected("orphan")
        assertEquals("orphan", nested.selectedActor?.parentId)
        assertEquals(nested.project, SceneProjectCodec.decode(SceneProjectCodec.encode(nested.project)))
    }

    @Test
    fun ordinaryValidMultiActorParentingAndUndoStillWork() {
        val scene = SceneProject(id = "normal", name = "Normal scene",
            actors = listOf(character, prop, Actor("tree", "Oak", ActorKind.ENVIRONMENT)))
        val state = SceneEditorState(scene).selectActor("prop")
        assertTrue(state.canReparentSelected("character"))
        val parented = state.reparentSelected("character")
        assertEquals("character", parented.selectedActor?.parentId)
        assertEquals(scene, parented.undo().project)
        assertEquals(parented.project, parented.undo().redo().project)
        assertFalse(parented.selectActor("character").canReparentSelected("prop"))
        // Non-cyclic ancestry leaves unrelated scene nodes intact.
        assertEquals(scene.actors.last(), parented.project.actors.last())
    }
}
