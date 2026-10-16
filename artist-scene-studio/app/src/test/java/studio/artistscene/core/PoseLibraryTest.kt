package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseLibraryTest {
    private val boneNames = listOf(
        "J_Bip_C_Head", "J_Bip_L_UpperArm", "J_Bip_L_LowerArm",
        "J_Bip_R_UpperArm", "J_Bip_R_LowerArm", "J_Bip_L_UpperLeg",
        "J_Bip_L_LowerLeg", "J_Bip_R_UpperLeg", "J_Bip_R_LowerLeg",
        "J_Bip_L_Index1", "J_Bip_R_Index1",
    )
    private fun character(id: String = "anime", x: Float = 0f): Actor = Actor(
        id = id,
        name = "Rigged anime $id",
        kind = ActorKind.CHARACTER,
        transform = Transform(position = Vec3(x, 0f, 0f)),
        rigDefinition = RigDefinition(
            bones = boneNames.mapIndexed { index, name ->
                RigBone("joint-$index", name)
            },
        ),
    )
    private fun state(vararg actors: Actor) =
        SceneEditorState(SceneProject(id = "poses", name = "Pose test", actors = actors.toList()))
            .selectActor(actors.firstOrNull()?.id)

    @Test fun templatesUseRealRigBoneIdsNotPlaceholders() {
        val model = character()
        for (preset in PosePreset.entries) {
            val pose = PoseLibrary.apply(model, preset)
            assertTrue("$preset must bind at least three actual VRoid bones", (pose?.joints?.size ?: 0) >= 3)
            assertTrue(pose!!.joints.keys.all { it.startsWith("joint-") })
            assertTrue(pose.joints.values.any { it != Vec3() })
        }
    }

    @Test fun presetsAndMirrorAreUndoableAndPreserveExpressions() {
        val hero = character().copy(rig = RigPose(morphWeights = mapOf("smile" to .68f)))
        val start = state(hero)
        val wave = start.applyPosePreset(PosePreset.WAVE)
        assertTrue(wave.canUndo)
        assertEquals(.68f, wave.selectedActor!!.rig!!.morphWeights["smile"])
        assertNotEquals(start.project, wave.project)
        assertEquals(start.project, wave.undo().project)
        val mirrored = wave.mirrorSelectedPose()
        assertTrue(mirrored.canUndo)
        assertNotEquals(wave.selectedActor!!.rig!!.joints, mirrored.selectedActor!!.rig!!.joints)
        assertEquals(wave.project, mirrored.undo().project)
        assertEquals(wave.selectedActor!!.rig, mirrored.mirrorSelectedPose().selectedActor!!.rig)
    }

    @Test fun actorLockAndMissingRigCannotApplyPresets() {
        val actor = character()
        assertEquals(null, PoseLibrary.apply(actor.copy(locked = true), PosePreset.HERO))
        assertEquals(null, PoseLibrary.apply(actor.copy(rigDefinition = null), PosePreset.HERO))
        assertEquals(state(actor.copy(locked = true)), state(actor.copy(locked = true)).applyPosePreset(PosePreset.HERO))
    }

    @Test fun stagingTwoCharactersIsSingleUndoStepAndDoesNotMoveOtherActors() {
        val a = character("a", -4f)
        val b = character("b", 5f)
        val light = Actor("sun", "Light", ActorKind.LIGHT)
        val original = state(a, b, light)
        val staged = original.stageDuo()
        assertTrue(staged.canUndo)
        assertEquals(3, staged.project.actors.size)
        assertTrue(staged.project.actors.take(2).all { it.rig?.joints?.isNotEmpty() == true })
        assertEquals(1.6f, staged.project.actors[1].transform.position.x -
            staged.project.actors[0].transform.position.x, 0.001f)
        assertEquals(light, staged.project.actors[2])
        assertEquals(original.project, staged.undo().project)
    }

    @Test fun artistsCanBookmarkApplyDeleteAndUndoNamedPoses() {
        val hero = character()
        val posed = state(hero).applyPosePreset(PosePreset.HERO)
        val saved = posed.saveSelectedPose("My Hero")
        assertEquals(1, saved.project.savedPoses.size)
        assertEquals("My Hero", saved.project.savedPoses.single().name)
        val reset = saved.resetRigPose()
        val restored = reset.applySavedPose(saved.project.savedPoses.single().id)
        assertEquals(posed.selectedActor!!.rig!!.joints, restored.selectedActor!!.rig!!.joints)
        assertEquals(reset.project, restored.undo().project)
        assertEquals(0, saved.deleteSavedPose(saved.project.savedPoses.single().id).project.savedPoses.size)
        val serialized = SceneProjectCodec.decode(SceneProjectCodec.encode(saved.project))
        assertEquals(saved.project.savedPoses, serialized.savedPoses)
    }

    @Test fun lightingChangesSkyAndSunInOneUndoStepWithoutEditingOtherActors() {
        val sun = Actor("sun", "Sun", ActorKind.LIGHT, light = LightSettings(LightType.DIRECTIONAL))
        val untouched = Actor("prop", "Fixture", ActorKind.PROP)
        val initial = state(character(), sun, untouched)
        val lit = initial.applyLightingLook(LightingLook.GOLDEN)
        assertEquals(EnvironmentPreset.SUNSET, lit.project.world.environmentPreset)
        assertEquals("#FFCC9B", lit.project.actors[1].light!!.colorHex)
        assertEquals(38_000f, lit.project.actors[1].light!!.intensity)
        assertEquals(untouched, lit.project.actors[2])
        assertEquals(initial.project, lit.undo().project)
        val locked = initial.copy(project = initial.project.copy(actors = initial.project.actors.map {
            if (it.id == "sun") it.copy(locked = true) else it
        }))
        assertEquals(locked, locked.applyLightingLook(LightingLook.GOLDEN))
    }

    @Test fun importedAnimeFaceMorphsCreateRealUndoableExpressionsWithoutChangingMuscles() {
        val hero = character().copy(
            rigDefinition = character().rigDefinition!!.copy(morphTargets = listOf(
                RigMorphTarget("morph-smile", "Joy"),
                RigMorphTarget("morph-sad", "Sorrow"),
                RigMorphTarget("morph-blink", "Blink"),
                RigMorphTarget("morph-body", "Muscle"),
            )),
            rig = RigPose(morphWeights = mapOf("morph-body" to .4f)),
        )
        val initial = state(hero)
        val smiled = initial.applyFaceExpression(FaceExpression.SMILE)
        assertEquals(1f, smiled.selectedActor!!.rig!!.morphWeights["morph-smile"])
        assertEquals(.4f, smiled.selectedActor!!.rig!!.morphWeights["morph-body"])
        assertEquals(initial.project, smiled.undo().project)
        val saddened = smiled.applyFaceExpression(FaceExpression.SAD)
        assertFalse("Previous smile must turn off", "morph-smile" in saddened.selectedActor!!.rig!!.morphWeights)
        assertEquals(1f, saddened.selectedActor!!.rig!!.morphWeights["morph-sad"])
        assertTrue(AnimeExpressions.canApply(hero, FaceExpression.BLINK))
        assertEquals(null, AnimeExpressions.apply(hero, FaceExpression.SURPRISED))
        val neutral = saddened.applyFaceExpression(FaceExpression.NEUTRAL)
        assertEquals(mapOf("morph-body" to .4f), neutral.selectedActor!!.rig!!.morphWeights)
    }

    @Test fun cameraShotsPreserveSourceAndFrameActualActor() {
        val hero = character(x = 2f)
        val camera = SceneCamera("camera-main", "Camera")
        val front = StudioCameraShots.frame(camera, hero, CameraShot.FRONT)
        val portrait = StudioCameraShots.frame(camera, hero, CameraShot.CLOSE_UP)
        val side = StudioCameraShots.frame(camera, hero, CameraShot.SIDE)
        assertEquals(2f, front.target.x)
        assertTrue(front.position.z < hero.transform.position.z)
        assertTrue(side.position.x < hero.transform.position.x)
        assertTrue(kotlin.math.abs(portrait.position.z - portrait.target.z) <
            kotlin.math.abs(front.position.z - front.target.z))
        assertEquals(CameraProjection.PERSPECTIVE, portrait.projection)
        assertEquals(Vec3(0f, 1.6f, 4f), camera.position)
    }
}
