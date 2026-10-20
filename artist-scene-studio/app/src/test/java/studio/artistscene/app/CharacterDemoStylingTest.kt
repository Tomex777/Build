package studio.artistscene.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.artistscene.core.ActorKind
import studio.artistscene.core.RigBone
import studio.artistscene.core.RigDefinition
import studio.artistscene.core.RigMorphTarget
import studio.artistscene.core.RigPose
import studio.artistscene.core.SceneProjectCodec

class CharacterDemoStylingTest {
    private val actualFace = RigMorphTarget("morph/face/joy-3", "Joy", "Face")

    private fun rigForStudent(
        actor: studio.artistscene.core.Actor,
        face: Boolean = true,
    ) = RigDefinition(
        bones = actor.rig!!.joints.keys.map { RigBone(it, it.substringAfterLast("/")) },
        morphTargets = if (face) listOf(actualFace) else emptyList(),
    )

    @Test fun genuineOneTimeSmileAppearsOnlyAfterTheSourceFaceRigIsDiscovered() {
        val original = UnderTreeScene.create()
        val first = original.actors.single { it.id == UnderTreeScene.FIRST_STUDENT_ID }
        val second = original.actors.single { it.id == UnderTreeScene.SECOND_STUDENT_ID }
        assertEquals("SMILE", first.metadata[CharacterDemoStyling.INITIAL_EXPRESSION_KEY])
        assertFalse(second.metadata.containsKey(CharacterDemoStyling.INITIAL_EXPRESSION_KEY))

        val sourceRig = rigForStudent(first)
        val styled = PrototypeScene.resolveAnimePose(first, sourceRig)
        assertEquals(.55f, styled.rig!!.morphWeights[actualFace.id]!!, .00001f)
        assertEquals(first.rig!!.joints, styled.rig!!.joints)
        assertFalse(styled.metadata.containsKey(CharacterDemoStyling.INITIAL_EXPRESSION_KEY))
        assertEquals(sourceRig, styled.rigDefinition)
        assertEquals(second, PrototypeScene.resolveAnimePose(second, rigForStudent(second)))
    }

    @Test fun savedAndReopenedStudentNeverHasTheirExpressionReapplied() {
        val original = UnderTreeScene.create()
        val student = original.actors.single { it.id == UnderTreeScene.FIRST_STUDENT_ID }
        val rig = rigForStudent(student)
        val styled = PrototypeScene.resolveAnimePose(student, rig)
        val reopened = SceneProjectCodec.decode(SceneProjectCodec.encode(original.copy(
            actors = original.actors.map { if (it.id == student.id) styled else it },
        )))
        val saved = reopened.actors.single { it.id == student.id }
        val artistClosedEyes = saved.copy(rig = saved.rig!!.copy(
            morphWeights = saved.rig.morphWeights + (actualFace.id to .12f),
        ))
        assertEquals(artistClosedEyes, PrototypeScene.resolveAnimePose(artistClosedEyes, rig))
    }

    @Test fun existingArtistShapesAndLocksAreNeverClobberedDuringLoad() {
        val first = UnderTreeScene.create().actors.single { it.id == UnderTreeScene.FIRST_STUDENT_ID }
        val changed = first.copy(rig = first.rig!!.copy(
            morphWeights = mapOf(actualFace.id to .23f),
        ))
        val result = PrototypeScene.resolveAnimePose(changed, rigForStudent(changed))
        assertEquals(.23f, result.rig!!.morphWeights[actualFace.id]!!, .00001f)
        assertFalse(result.metadata.containsKey(CharacterDemoStyling.INITIAL_EXPRESSION_KEY))
        val locked = first.copy(locked = true)
        val lockResult = PrototypeScene.resolveAnimePose(locked, rigForStudent(locked))
        assertTrue(lockResult.rig!!.morphWeights.isEmpty())
        assertFalse(lockResult.metadata.containsKey(CharacterDemoStyling.INITIAL_EXPRESSION_KEY))
    }

    @Test fun invalidSourceDoesNotInventAStickerOrFabricatedFace() {
        val first = UnderTreeScene.create().actors.single { it.id == UnderTreeScene.FIRST_STUDENT_ID }
        val unrelatedRig = rigForStudent(first, face = false)
        val unsupported = PrototypeScene.resolveAnimePose(first, unrelatedRig)
        assertTrue(unsupported.rig!!.morphWeights.isEmpty())
        // Leave the intent for a later genuine source rig discovery rather than
        // manufacturing a face expression or prematurely disabling the feature.
        assertEquals("SMILE", unsupported.metadata[CharacterDemoStyling.INITIAL_EXPRESSION_KEY])
        val prop = first.copy(kind = ActorKind.PROP)
        assertEquals(prop, CharacterDemoStyling.resolve(prop, rigForStudent(first)))
    }
}
