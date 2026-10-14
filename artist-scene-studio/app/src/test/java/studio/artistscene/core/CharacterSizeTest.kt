package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterSizeTest {
    private fun state(locked: Boolean = false) = SceneEditorState(SceneProject(
        id = "character-size", name = "Size",
        actors = listOf(Actor("human", "Human", ActorKind.CHARACTER, locked = locked,
            transform = Transform(scale = Vec3(1f, 2f, .5f)),
            rig = RigPose(joints = mapOf("head" to Vec3(0f, 10f, 0f))),
        )),
    ))

    @Test fun resizingPreservesProportionsPoseAndHistory() {
        val start = state()
        val changed = start.resizeCharacter(1.5f)
        assertEquals(Vec3(1.5f, 3f, .75f), changed.selectedActor!!.transform.scale)
        assertEquals(start.selectedActor!!.rig, changed.selectedActor!!.rig)
        assertEquals(start.project, changed.undo().project)
        assertEquals(changed.project, changed.undo().redo().project)
        assertEquals(changed.project, SceneProjectCodec.decode(SceneProjectCodec.encode(changed.project)))
    }

    @Test fun invalidSizesAndLockedCharactersRemainUnchanged() {
        val start = state()
        listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f, 100f).forEach {
            assertEquals(start, start.resizeCharacter(it))
        }
        assertEquals(state(true), state(true).resizeCharacter(1.5f))
    }
}
