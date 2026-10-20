package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterExpressionMixerTest {
    private val rig = RigDefinition(
        bones = listOf(RigBone("head", "Head")),
        morphTargets = listOf(
            RigMorphTarget("joy", "Joy", "Face"),
            RigMorphTarget("anger", "Angry", "Face"),
            RigMorphTarget("sorrow", "Sorrow", "Face"),
            RigMorphTarget("blink-l", "Blink_L", "Face"),
            RigMorphTarget("blink-r", "Blink_R", "Face"),
            RigMorphTarget("mouth-aa", "A", "Face"),
            RigMorphTarget("fat", "Fat", "Body"),
            // This is NOT facial, despite looking like an expression name.
            RigMorphTarget("hair-smile", "Smile accessory length", "Hair001"),
        ),
    )

    private fun actor(
        id: String = "a",
        weights: Map<String, Float> = mapOf(
            "fat" to .35f, "mouth-aa" to .4f, "hair-smile" to .1f,
        ),
    ) = Actor(
        id = id,
        name = "Real VRoid skinned character",
        kind = ActorKind.CHARACTER,
        asset = AssetReference("starter.mise.anime-figure", "models/mise_anime.glb"),
        rigDefinition = rig,
        rig = RigPose(joints = mapOf("head" to Vec3(z = 12f)), morphWeights = weights),
    )

    @Test fun blinkCanBeCombinedWithAnEmotionAndToggledWithoutErasingThatEmotion() {
        val initial = actor()
        val smiling = CharacterExpressionMixer.apply(initial, FaceExpression.SMILE)!!
        assertEquals(1f, smiling.morphWeights["joy"])
        val blinking = CharacterExpressionMixer.apply(initial.copy(rig = smiling), FaceExpression.BLINK)!!
        assertEquals(1f, blinking.morphWeights["joy"])
        assertEquals(1f, blinking.morphWeights["blink-l"])
        assertEquals(1f, blinking.morphWeights["blink-r"])
        val eyesOpen = CharacterExpressionMixer.apply(initial.copy(rig = blinking), FaceExpression.BLINK)!!
        assertFalse("Blink is an independent on/off expression", eyesOpen.morphWeights.containsKey("blink-l"))
        assertFalse(eyesOpen.morphWeights.containsKey("blink-r"))
        assertEquals(1f, eyesOpen.morphWeights["joy"])
        assertNull("Repeating the exact same facial choice must be a no-op",
            CharacterExpressionMixer.apply(initial.copy(rig = eyesOpen), FaceExpression.SMILE))
    }

    @Test fun changingEmotionDoesNotAlterEyesMouthBodyOrHair() {
        val closed = actor(weights = mapOf(
            "blink-l" to 1f, "blink-r" to .8f, "joy" to .7f,
            "fat" to .35f, "mouth-aa" to .4f, "hair-smile" to .1f,
        ))
        val angry = CharacterExpressionMixer.apply(closed, FaceExpression.ANGRY)!!
        assertFalse(angry.morphWeights.containsKey("joy"))
        assertEquals(1f, angry.morphWeights["anger"])
        assertEquals(1f, angry.morphWeights["blink-l"])
        assertEquals(.8f, angry.morphWeights["blink-r"])
        assertEquals(.4f, angry.morphWeights["mouth-aa"])
        assertEquals(.35f, angry.morphWeights["fat"])
        assertEquals(.1f, angry.morphWeights["hair-smile"])

        val neutral = CharacterExpressionMixer.apply(closed.copy(rig = angry), FaceExpression.NEUTRAL)!!
        assertEquals(
            mapOf("fat" to .35f, "mouth-aa" to .4f, "hair-smile" to .1f),
            neutral.morphWeights,
        )
        assertEquals(closed.rig!!.joints, neutral.joints)
    }

    @Test fun neutralIsNotOfferedToACharacterWithOnlyBodyShapes() {
        val shapeOnly = actor()
        assertFalse(CharacterExpressionMixer.canApply(shapeOnly, FaceExpression.NEUTRAL))
        assertNull(CharacterExpressionMixer.apply(shapeOnly, FaceExpression.NEUTRAL))
        assertTrue(CharacterExpressionMixer.canApply(shapeOnly, FaceExpression.SMILE))
        assertTrue(CharacterExpressionMixer.canApply(shapeOnly, FaceExpression.BLINK))
        assertFalse(CharacterExpressionMixer.canApply(shapeOnly, FaceExpression.SURPRISED))
        assertNull(CharacterExpressionMixer.apply(shapeOnly, FaceExpression.SURPRISED))
    }

    @Test fun nonCharactersAndLockedActorsCannotAccidentallyModifyAMesh() {
        val normal = actor()
        val locked = normal.copy(locked = true)
        val prop = normal.copy(kind = ActorKind.PROP)
        for (invalid in listOf(locked, prop)) {
            assertFalse(AnimeExpressions.canApply(invalid, FaceExpression.BLINK))
            assertNull(AnimeExpressions.apply(invalid, FaceExpression.BLINK))
            assertNull(AnimeExpressions.apply(invalid, FaceExpression.NEUTRAL))
        }
    }

    @Test fun existingEditorPillsStoreFaceWithUndoAndIndependentActorsAcrossReopen() {
        val first = actor("a")
        val second = actor("b", weights = mapOf("anger" to .5f))
        val initial = SceneEditorState(SceneProject(
            id = "expressions", name = "Expression rig scene", actors = listOf(first, second),
        )).selectActor("a")
        val smiled = initial.applyFaceExpression(FaceExpression.SMILE)
        val blinked = smiled.applyFaceExpression(FaceExpression.BLINK)
        assertTrue(blinked.canUndo)
        assertEquals(first, blinked.undo().undo().selectedActor)
        assertEquals(second, blinked.project.actors.single { it.id == "b" })
        val reopened = SceneProjectCodec.decode(SceneProjectCodec.encode(blinked.project))
        assertEquals(blinked.project, reopened)
        assertEquals(1f, reopened.actors.single { it.id == "a" }.rig!!.morphWeights["joy"])
        assertEquals(1f, reopened.actors.single { it.id == "a" }.rig!!.morphWeights["blink-l"])
    }
}
