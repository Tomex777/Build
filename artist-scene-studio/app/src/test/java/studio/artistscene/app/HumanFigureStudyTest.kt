package studio.artistscene.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.artistscene.core.ActorKind
import studio.artistscene.core.SceneProjectCodec

class HumanFigureStudyTest {
    @Test
    fun humanFigureStarterUsesActualBundledSkinnedHumanoids() {
        val scene = PrototypeScene.createHumanoidStudy()
        val humans = scene.actors.filter { it.kind == ActorKind.CHARACTER }

        assertEquals(PrototypeScene.HUMAN_STUDY_PROJECT_ID, scene.id)
        assertEquals(2, humans.size)
        assertEquals(2, humans.map { it.id }.distinct().size)
        assertTrue(humans.all { it.asset?.assetId == PrototypeScene.HUMANOID_ASSET_ID })
        assertTrue(humans.all { it.asset?.relativePath == "models/mise_humanoid.glb" })
        assertTrue(humans.map { it.appearance.hairStyle }.distinct().size == 2)
        assertTrue(humans[0].transform.position.x < humans[1].transform.position.x)
        assertTrue(scene.actors.any { it.kind == ActorKind.LIGHT })
        assertEquals(scene, SceneProjectCodec.decode(SceneProjectCodec.encode(scene)))
    }
}
