package studio.artistscene.core

import org.junit.Assert.*
import org.junit.Test

class BoneAttachmentTest {
    private val character = Actor(
        "character", "Character", ActorKind.CHARACTER,
        rigDefinition = RigDefinition(bones = listOf(
            RigBone("arm", "Right Arm"),
            RigBone("hand", "Right Hand", parentId = "arm"),
        )),
    )
    private val prop = Actor("prop", "Prop", ActorKind.PROP,
        transform = Transform(position = Vec3(2f, 0f, 0f), scale = Vec3(.2f, .2f, .2f)))
    private fun editor() = SceneEditorState(SceneProject(id = "scene", name = "Scene",
        actors = listOf(character, prop))).selectActor("prop")

    @Test fun attachmentSnapsToGripWithAuthoredScaleAndOneUndoStep() {
        val original = editor()
        val attached = original.attachSelectedToBone("character", "hand")
        assertEquals("character", attached.selectedActor?.parentId)
        assertEquals("hand", attached.selectedActor?.parentBoneId)
        assertEquals(Transform(scale = prop.transform.scale), attached.selectedActor?.transform)
        val restored = attached.undo()
        assertEquals(original.project, restored.project)
        assertEquals(attached.project, restored.redo().project)
    }

    @Test fun poseAndAttachmentSurviveCodecAndReopenWithoutChangingOtherActor() {
        val attached = editor().attachSelectedToBone("character", "hand")
        val posed = attached.selectActor("character").setRigJointRotation("arm", Vec3(0f, 0f, 45f))
        val reopened = SceneProjectCodec.decode(SceneProjectCodec.encode(posed.project))
        assertEquals(posed.project, reopened)
        assertEquals("hand", reopened.actors.first { it.id == "prop" }.parentBoneId)
        assertEquals(attached.project.actors.first { it.id == "prop" }, reopened.actors.first { it.id == "prop" })
    }

    @Test fun rejectsMissingBonesLockedPropsAndParentCycles() {
        val state = editor()
        assertEquals(state, state.attachSelectedToBone("character", "missing"))
        assertEquals(state, state.attachSelectedToBone("prop", "hand"))
        val locked = state.toggleSelectedLocked()
        assertEquals(locked, locked.attachSelectedToBone("character", "hand"))
        val cyclic = state.copy(project = state.project.copy(actors = listOf(character.copy(parentId = "prop"), prop)))
        assertEquals(cyclic, cyclic.attachSelectedToBone("character", "hand"))
    }

    @Test fun reparentAndCharacterDeletionClearJointRelationshipUndoRestoresIt() {
        val attached = editor().attachSelectedToBone("character", "hand")
        assertNull(attached.reparentSelected(null).selectedActor?.parentBoneId)
        val deleted = attached.selectActor("character").deleteSelected()
        assertNull(deleted.project.actors.single().parentId)
        assertNull(deleted.project.actors.single().parentBoneId)
        assertEquals(attached.project, deleted.undo().project)
    }

    @Test fun versionFourMigrationKeepsExistingParentingAndAddsNoAttachment() {
        val migrated = SceneProjectCodec.decode("""{"schemaVersion":4,"id":"old","name":"Old","actors":[{"id":"prop","name":"Prop","kind":"PROP","parentId":"character"}]}""")
        assertEquals(5, migrated.schemaVersion)
        assertEquals("character", migrated.actors.single().parentId)
        assertNull(migrated.actors.single().parentBoneId)
    }
}
