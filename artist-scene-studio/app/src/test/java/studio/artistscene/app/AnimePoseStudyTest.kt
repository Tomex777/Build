package studio.artistscene.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.artistscene.core.ActorKind
import studio.artistscene.core.EnvironmentPreset
import studio.artistscene.core.HairStyle
import studio.artistscene.core.SceneProjectCodec
import studio.artistscene.core.Vec3

class AnimePoseStudyTest {
    @Test fun animeStudyUsesRealLicensedSkinnedAssetAndAuthoredDynamicPose() {
        val project = PrototypeScene.createAnimePoseStudy()
        val hero = project.actors.single { it.kind == ActorKind.CHARACTER }

        assertEquals("Anime Pose Study", project.name)
        assertEquals(PrototypeScene.ANIME_ASSET_ID, hero.asset?.assetId)
        assertEquals("models/mise_anime.glb", hero.asset?.relativePath)
        assertEquals("CC0-1.0", hero.asset?.license)
        assertEquals(HairStyle.BOB, hero.appearance.hairStyle)
        assertEquals(EnvironmentPreset.DAYLIGHT, project.world.environmentPreset)
        assertEquals(10, hero.rig?.joints?.size)
        assertTrue(hero.rig!!.joints.values.any { it.z > 30f })
        assertTrue(hero.rig.joints.values.any { it.z < -30f })
        assertTrue(hero.rig.joints.values.any { it.x != 0f })
        assertTrue(hero.rig.joints.keys.any { it.contains("lowerleg") })
        assertTrue(project.actors.any { it.kind == ActorKind.LIGHT })
        assertNotNull(project.cameras.firstOrNull())
        assertEquals(project, SceneProjectCodec.decode(SceneProjectCodec.encode(project)))
    }

    @Test fun characterCatalogStillKeepsNeutralAnimeForManualPoseEditing() {
        val anime = PrototypeScene.starterAssets().single {
            it.asset?.assetId == PrototypeScene.ANIME_ASSET_ID
        }
        assertTrue(anime.kind == ActorKind.CHARACTER)
        assertTrue(anime.rig == null || anime.rig!!.joints.isEmpty())
        assertEquals(Vec3(), anime.transform.position)
        assertTrue(PrototypeScene.starterAssets().any {
            it.asset?.assetId == PrototypeScene.HUMANOID_ASSET_ID
        })
    }
}
