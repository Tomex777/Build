package studio.artistscene.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneProjectTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun emptySceneIsValidAndRoundTripsWithoutHumanoids() {
        val scene = SceneProject(id = "landscape-01", name = "Character-free landscape")
        val restored = json.decodeFromString<SceneProject>(json.encodeToString(scene))
        assertEquals(scene, restored)
        assertTrue(restored.actors.none { it.kind == ActorKind.CHARACTER })
    }

    @Test
    fun mixedActorsAndAnimationSurviveRoundTrip() {
        val scene = SceneProject(
            id = "street",
            name = "Night street",
            actors = listOf(
                Actor("tree-1", "Tree", ActorKind.ENVIRONMENT, Transform(position = Vec3(2f, 0f, -1f))),
                Actor("sun", "Key light", ActorKind.LIGHT, light = LightSettings(LightType.DIRECTIONAL))
            ),
            tracks = listOf(AnimationTrack(
                "camera-move", "camera-main", "camera.position",
                listOf(Keyframe(0f, AnimatedValue(vector = Vec3(0f, 1f, 5f))))
            ))
        )
        assertEquals(scene, json.decodeFromString<SceneProject>(json.encodeToString(scene)))
    }

    @Test
    fun discoveredRigAndIndependentPoseSurviveSceneRoundTrip() {
        val bone = RigBone("root/arm/elbow", "Elbow", "root/arm", restRotationEulerDegrees = Vec3(4f, 0f, 0f))
        val scene = SceneProject(
            id = "pose-save",
            name = "Pose save",
            actors = listOf(
                Actor(
                    "actor-a", "A", ActorKind.CHARACTER,
                    rigDefinition = RigDefinition("Humanoid", listOf(bone)),
                    rig = RigPose(joints = mapOf(bone.id to Vec3(z = 38f))),
                ),
                Actor("actor-b", "B", ActorKind.CHARACTER),
            ),
        )

        val restored = json.decodeFromString<SceneProject>(json.encodeToString(scene))
        assertEquals(scene, restored)
        assertEquals(Vec3(z = 38f), restored.actors.first().rig?.joints?.get(bone.id))
        assertEquals(null, restored.actors.last().rig)
    }
}
