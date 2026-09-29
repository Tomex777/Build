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
