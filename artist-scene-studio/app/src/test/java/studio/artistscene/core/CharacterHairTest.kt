package studio.artistscene.core

import org.junit.Assert.*
import org.junit.Test

class CharacterHairTest {
    private fun editor(locked: Boolean = false) = SceneEditorState(SceneProject(
        id = "hair", name = "Hair", actors = listOf(Actor(
            id = "human", name = "Human", kind = ActorKind.CHARACTER, locked = locked,
            asset = AssetReference("starter.makehuman.humanoid", "models/mise_humanoid.glb"),
            rig = RigPose(joints = mapOf("head" to Vec3(0f, 0f, 30f))),
        )),
    ))

    @Test fun hairEditsPreservePoseAndSupportHistoryAndSerialization() {
        val before = editor()
        val after = before.setCharacterHair(HairStyle.BOB, "#56372a")
        assertEquals(before.selectedActor!!.rig, after.selectedActor!!.rig)
        assertEquals(CharacterAppearanceSettings(HairStyle.BOB, "#56372A"), after.selectedActor!!.appearance)
        assertEquals(before.project, after.undo().project)
        assertEquals(after.project, after.undo().redo().project)
        assertEquals(after.project, SceneProjectCodec.decode(SceneProjectCodec.encode(after.project)))
    }

    @Test fun lockedCharactersAndInvalidColorsRemainUnchanged() {
        val locked = editor(true)
        assertEquals(locked, locked.setCharacterHair(HairStyle.AFRO))
        val before = editor()
        assertEquals(before, before.setCharacterHair(HairStyle.SHORT, "invalid"))
    }

    @Test fun schemaFiveScenesKeepTheirOriginalBaldAppearance() {
        val restored = SceneProjectCodec.decode("""{"schemaVersion":5,"id":"old","name":"Old","actors":[{"id":"a","name":"A","kind":"CHARACTER"}]}""")
        assertEquals(6, restored.schemaVersion)
        assertEquals(CharacterAppearanceSettings(), restored.actors.single().appearance)
    }
}
