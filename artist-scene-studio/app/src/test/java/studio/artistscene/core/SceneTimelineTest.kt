package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneTimelineTest {
    private fun project() = SceneProject(
        id = "timeline-test",
        name = "Timeline Test",
        actors = listOf(Actor("actor", "Actor", ActorKind.PROP)),
        timeline = TimelineSettings(durationSeconds = 4f, loop = false),
    )

    @Test
    fun keyTransformCreatesOneUndoableSetOfThreeTracks() {
        val moved = SceneEditorState(project())
            .selectActor("actor")
            .setPosition(TransformAxis.X, 2f)
        val keyed = moved.keySelectedTransform(1.5f)

        assertEquals(3, keyed.project.tracks.size)
        assertEquals(listOf(1.5f), keyed.project.transformKeyTimes("actor"))
        assertTrue(keyed.canUndo)

        val evaluated = keyed.project.evaluateTimeline(1.5f)
        assertEquals(2f, requireNotNull(evaluated.actors.first()).transform.position.x, 0.0001f)

        val removed = keyed.removeSelectedTransformKeyframe(1.5f)
        assertTrue(removed.project.tracks.isEmpty())
    }

    @Test
    fun timelineInterpolatesTransformAndUsesShortestRotationPath() {
        var state = SceneEditorState(project()).selectActor("actor")
        state = state
            .setPosition(TransformAxis.X, 0f)
            .setRotation(TransformAxis.Y, 170f)
            .keySelectedTransform(0f)
        state = state
            .setPosition(TransformAxis.X, 4f)
            .setRotation(TransformAxis.Y, -170f)
            .keySelectedTransform(4f)

        val middle = state.project.evaluateTimeline(2f).actors.first().transform
        assertEquals(2f, middle.position.x, 0.0001f)
        assertTrue(kotlin.math.abs(kotlin.math.abs(middle.rotationEulerDegrees.y) - 180f) < 0.001f)
    }

    @Test
    fun keyPoseInterpolatesJointAndMorphTracksAsOneAuthoredPose() {
        val shoulder = RigBone("shoulder", "Right Shoulder")
        val smile = RigMorphTarget("smile", "Smile", "Face")
        val character = Actor(
            id = "character",
            name = "Character",
            kind = ActorKind.CHARACTER,
            rigDefinition = RigDefinition(bones = listOf(shoulder), morphTargets = listOf(smile)),
        )
        var state = SceneEditorState(
            project().copy(actors = listOf(character)),
        ).selectActor(character.id)

        state = state.keySelectedPose(0f)
        state = state.setRigJointRotation(shoulder.id, Vec3(z = 90f))
        state = state.setRigMorphWeight(smile.id, 1f)
        state = state.keySelectedPose(4f)

        assertEquals(listOf(0f, 4f), state.project.poseKeyTimes(character.id))
        assertEquals(2, state.project.tracks.size)

        val middle = state.project.evaluateTimeline(2f).actors.first()
        assertEquals(45f, requireNotNull(middle.rig).joints.getValue(shoulder.id).z, 0.001f)
        assertEquals(0.5f, requireNotNull(middle.rig).morphWeights.getValue(smile.id), 0.001f)

        val removed = state.removeSelectedPoseKeyframe(0f)
        assertEquals(listOf(4f), removed.project.poseKeyTimes(character.id))
        assertTrue(removed.canUndo)
    }

    @Test
    fun authoredPoseTimelineOverridesImportedClipOnlyInEvaluatedFrame() {
        val bone = RigBone("bone", "Hand")
        val character = Actor(
            id = "character",
            name = "Character",
            kind = ActorKind.CHARACTER,
            rigDefinition = RigDefinition(bones = listOf(bone)),
            animation = ActorAnimationState(
                clips = listOf(AnimationClipDefinition("Walk", 2f)),
                selectedClip = "Walk",
                playing = true,
            ),
        )
        val project = project().copy(
            actors = listOf(character),
            tracks = listOf(
                AnimationTrack(
                    id = "pose",
                    targetActorId = character.id,
                    propertyPath = SceneTimelinePaths.rigJoint(bone.id),
                    keyframes = listOf(
                        Keyframe(0f, AnimatedValue(rotationEulerDegrees = Vec3(z = 20f))),
                    ),
                ),
            ),
        )

        val evaluated = project.evaluateTimeline(0f).actors.first()
        assertFalse(evaluated.animation.playing)
        assertEquals(20f, requireNotNull(evaluated.rig).joints.getValue(bone.id).z, 0.001f)
        assertTrue(project.actors.first().animation.playing)
    }

    @Test
    fun loopAndStepInterpolationAreDeterministic() {
        val track = AnimationTrack(
            id = "position",
            targetActorId = "actor",
            propertyPath = SceneTimelinePaths.POSITION,
            keyframes = listOf(
                Keyframe(0f, AnimatedValue(vector = Vec3(1f, 0f, 0f))),
                Keyframe(2f, AnimatedValue(vector = Vec3(3f, 0f, 0f))),
            ),
            interpolation = Interpolation.STEP,
        )
        val looping = project().copy(
            timeline = TimelineSettings(durationSeconds = 2f, loop = true),
            tracks = listOf(track),
        )
        assertEquals(1f, looping.evaluateTimeline(1f).actors.first().transform.position.x, 0.0001f)
        assertEquals(1f, looping.evaluateTimeline(2f).actors.first().transform.position.x, 0.0001f)

        val nonLooping = looping.copy(timeline = looping.timeline.copy(loop = false))
        assertEquals(3f, nonLooping.evaluateTimeline(10f).actors.first().transform.position.x, 0.0001f)
        assertFalse(nonLooping.timeline.loop)
    }
}
