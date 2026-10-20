package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for looping playback, manual scrubbing and imperfect saved tracks. */
class SceneTimelineRobustnessTest {
    private fun baseProject() = SceneProject(
        id = "timeline-boundaries",
        name = "Timeline Boundaries",
        actors = listOf(
            Actor("one", "Character One", ActorKind.CHARACTER),
            Actor("two", "Character Two", ActorKind.CHARACTER),
        ),
        timeline = TimelineSettings(durationSeconds = 4f, loop = true),
    )

    private fun positionTrack(
        id: String,
        actor: String,
        vararg frames: Pair<Float, Float>,
        enabled: Boolean = true,
    ) = AnimationTrack(
        id = id,
        targetActorId = actor,
        propertyPath = SceneTimelinePaths.POSITION,
        keyframes = frames.map { (time, x) ->
            Keyframe(time, AnimatedValue(vector = Vec3(x = x)))
        },
        interpolation = Interpolation.LINEAR,
        enabled = enabled,
    )

    @Test
    fun loopPlaybackWrapsButManualScrubHoldsEndKeyAndSurvivesPersistence() {
        val original = baseProject().copy(
            tracks = listOf(positionTrack("one-pos", "one", 0f to 1f, 4f to 9f)),
        )
        val restored = SceneProjectCodec.decode(SceneProjectCodec.encode(original))

        assertEquals(1f, restored.evaluateTimeline(4f).actors[0].transform.position.x, 0.001f)
        assertEquals(9f, restored.evaluateTimelineAtPlayhead(4f).actors[0].transform.position.x, 0.001f)
        assertEquals(9f, restored.evaluateTimelineAtPlayhead(40f).actors[0].transform.position.x, 0.001f)
        assertEquals(5f, restored.evaluateTimelineAtPlayhead(2f).actors[0].transform.position.x, 0.001f)
        assertEquals(1f, restored.evaluateTimeline(8f).actors[0].transform.position.x, 0.001f)
        assertEquals(original, restored)
        assertEquals(0f, restored.actors[0].transform.position.x, 0.001f)
    }

    @Test
    fun twoActorsAndDisabledTracksDoNotInterfereWithEachOtherOrKeyNavigation() {
        val scene = baseProject().copy(
            tracks = listOf(
                positionTrack("one-pos", "one", 0f to 2f, 4f to 6f),
                positionTrack("two-pos", "two", 0f to 20f, 4f to 60f),
                positionTrack("disabled", "one", 1f to 100f, enabled = false),
            ),
        )
        val middle = scene.evaluateTimelineAtPlayhead(2f)
        assertEquals(4f, middle.actors[0].transform.position.x, 0.001f)
        assertEquals(40f, middle.actors[1].transform.position.x, 0.001f)
        assertEquals(listOf(0f, 4f), scene.actorKeyTimes("one"))
        assertEquals(listOf(0f, 4f), scene.actorKeyTimes("two"))
        assertEquals(4f, scene.nextActorKeyTime("one", 2f))
        assertEquals(0f, scene.previousActorKeyTime("one", 2f))
        assertEquals(scene, scene.evaluateTimeline(Float.NaN))
        assertSame(scene, scene.evaluateTimelineAtPlayhead(Float.POSITIVE_INFINITY))
    }

    @Test
    fun malformedAndDuplicateFramesAreDeterministicAndCannotPoisonRendererMatrices() {
        val malformed = AnimationTrack(
            id = "messy",
            targetActorId = "one",
            propertyPath = SceneTimelinePaths.POSITION,
            interpolation = Interpolation.LINEAR,
            keyframes = listOf(
                Keyframe(2f, AnimatedValue(vector = Vec3(x = 4f))),
                Keyframe(Float.NaN, AnimatedValue(vector = Vec3(x = 100f))),
                Keyframe(0f, AnimatedValue(vector = Vec3(x = 0f))),
                Keyframe(1f, AnimatedValue(vector = Vec3(x = Float.POSITIVE_INFINITY))),
                Keyframe(2f, AnimatedValue(vector = Vec3(x = 8f))),
                Keyframe(-1f, AnimatedValue(vector = Vec3(x = 200f))),
            ),
        )
        val scene = baseProject().copy(tracks = listOf(malformed))
        assertEquals(listOf(0f, 2f), scene.actorKeyTimes("one"))
        assertEquals(4f, scene.evaluateTimelineAtPlayhead(1f).actors[0].transform.position.x, 0.001f)
        assertEquals(8f, scene.evaluateTimelineAtPlayhead(2f).actors[0].transform.position.x, 0.001f)
        assertEquals(8f, scene.evaluateTimelineAtPlayhead(4f).actors[0].transform.position.x, 0.001f)
        assertEquals(0f, scene.actors[0].transform.position.x, 0.001f)
    }

    @Test
    fun invalidPoseKeysAreSkippedWithoutStoppingImportedAnimation() {
        val bone = RigBone("hand", "Hand")
        val actor = Actor(
            "one", "Character One", ActorKind.CHARACTER,
            rigDefinition = RigDefinition(bones = listOf(bone)),
            animation = ActorAnimationState(
                clips = listOf(AnimationClipDefinition("Walk", 2f)),
                selectedClip = "Walk",
                playing = true,
            ),
        )
        val malformedPose = AnimationTrack(
            id = "bad-hand",
            targetActorId = "one",
            propertyPath = SceneTimelinePaths.rigJoint(bone.id),
            keyframes = listOf(
                Keyframe(0f, AnimatedValue(rotationEulerDegrees = Vec3(z = Float.NaN))),
            ),
        )
        val scene = baseProject().copy(actors = listOf(actor), tracks = listOf(malformedPose))
        val result = scene.evaluateTimelineAtPlayhead(1f).actors.single()
        assertTrue(result.animation.playing)
        assertEquals(null, result.rig)
        assertFalse(result == actor.copy(animation = actor.animation.copy(playing = false)))
    }
}
