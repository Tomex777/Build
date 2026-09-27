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
        assertEquals(scene, json.decodeFromString(json.encodeToString(scene)))
    }
}
