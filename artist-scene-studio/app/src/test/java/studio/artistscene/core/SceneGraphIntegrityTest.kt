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
    @Test
    fun explicitlyClearedSelectionSurvivesEnvironmentChangesAndUndoRedo() {
        val scene = SceneProject(
            id = "deselection",
            name = "Studio focus",
            actors = listOf(character, prop),
        )
        val clear = SceneEditorState(scene).selectActor(null)
        assertEquals(null, clear.selectedActorId)
        val edited = clear.setGroundVisible(false)
        assertFalse(edited.project.world.groundEnabled)
        assertEquals(null, edited.selectedActorId)
        val undone = edited.undo()
        assertEquals(scene, undone.project)
        assertEquals(null, undone.selectedActorId)
        val redone = undone.redo()
        assertEquals(edited.project, redone.project)
        assertEquals(null, redone.selectedActorId)

        val newScene = scene.copy(name = "New save slot")
        assertEquals(null, clear.replaceProject(newScene).selectedActorId)
        // A removed/nonexistent *non-null* focus still safely falls back to an existing actor.
        val invalid = SceneEditorState(scene).selectActor("prop")
            .replaceProject(scene.copy(actors = listOf(character)))
        assertEquals("character", invalid.selectedActorId)
    }

    @Test
    fun immutableSculptedAssetRevisionIsOneUndoStepAndPreservesOtherSceneObjects() {
        val terrain = Actor(
            id = "terrain",
            name = "Mountain",
            kind = ActorKind.ENVIRONMENT,
            asset = AssetReference(
                assetId = "terrain.generated.mountains",
                relativePath = "models/terrain-original.glb",
                storage = AssetStorage.PROJECT_FILE,
                byteSize = 123L,
            ),
            metadata = mapOf("terrainOrigin" to "MOUNTAINS"),
        )
        val project = SceneProject(
            id = "sculpt-history",
            name = "My mountain and two people",
            actors = listOf(terrain, character, prop),
            tracks = listOf(AnimationTrack(
                "actor-motion", "character", SceneTimelinePaths.POSITION,
                listOf(Keyframe(0f, AnimatedValue(vector = Vec3(1f, 0f, 0f)))),
            )),
        )
        val start = SceneEditorState(project).selectActor(terrain.id)
        val revised = terrain.copy(
            asset = terrain.asset!!.copy(
                relativePath = "models/terrain-revision.glb",
                byteSize = 456L,
                checksumSha256 = "a".repeat(64),
            ),
            metadata = terrain.metadata + ("terrainRevision" to "1"),
        )
        val updated = start.commitSelectedAssetRevision(revised)
        assertEquals(1, updated.undoStack.size)
        assertEquals(emptyList<SceneProject>(), updated.redoStack)
        assertEquals(revised, updated.selectedActor)
        assertEquals(project.actors.drop(1), updated.project.actors.drop(1))
        assertEquals(project.tracks, updated.project.tracks)
        assertEquals(updated.project, SceneProjectCodec.decode(SceneProjectCodec.encode(updated.project)))
        assertEquals(updated, updated.commitSelectedAssetRevision(revised)) // No duplicate undo record.

        val undo = updated.undo()
        assertEquals(project, undo.project)
        assertEquals("terrain", undo.selectedActorId)
        assertEquals(terrain.asset, undo.selectedActor?.asset)
        val redo = undo.redo()
        assertEquals(updated.project, redo.project)
        assertEquals("models/terrain-revision.glb", redo.selectedActor?.asset?.relativePath)
        assertEquals("a".repeat(64), redo.selectedActor?.asset?.checksumSha256)
    }

    @Test
    fun terrainRevisionCannotMutateActorIdentityHierarchyOrRigAndRespectsLocking() {
        val original = Actor(
            id = "terrain",
            name = "Mountain",
            kind = ActorKind.ENVIRONMENT,
            asset = AssetReference("terrain.generated.flat", "models/original.glb",
                storage = AssetStorage.PROJECT_FILE),
        )
        val start = SceneEditorState(SceneProject("safe-revisions", "Safe revisions",
            actors = listOf(original, prop))).selectActor(original.id)
        val revision = original.copy(asset = original.asset!!.copy(relativePath = "models/sculpted.glb"))
        assertEquals(start, start.commitSelectedAssetRevision(revision.copy(id = "new-actor")))
        assertEquals(start, start.commitSelectedAssetRevision(revision.copy(kind = ActorKind.VEHICLE)))
        assertEquals(start, start.commitSelectedAssetRevision(revision.copy(transform =
            Transform(position = Vec3(x = 100f)))))
        assertEquals(start, start.commitSelectedAssetRevision(revision.copy(parentId = "prop")))
        assertEquals(start, start.commitSelectedAssetRevision(revision.copy(
            asset = revision.asset!!.copy(assetId = "somebody-elses-model"))))
        assertEquals(start, start.commitSelectedAssetRevision(revision.copy(
            asset = revision.asset!!.copy(storage = AssetStorage.PERSISTED_URI))))
        val locked = start.toggleSelectedLocked()
        assertEquals(locked, locked.commitSelectedAssetRevision(revision))
        assertEquals(original, start.selectedActor)
        assertFalse(start.canUndo)
    }

}
