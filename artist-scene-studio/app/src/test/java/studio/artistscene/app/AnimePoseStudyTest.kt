package studio.artistscene.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.artistscene.core.ActorKind
import studio.artistscene.core.EnvironmentPreset
import studio.artistscene.core.HairStyle
import studio.artistscene.core.SceneProjectCodec
import studio.artistscene.core.RigDefinition
import studio.artistscene.core.RigBone
import studio.artistscene.core.Vec3

class AnimePoseStudyTest {
    @Test fun animeStudyUsesRealLicensedSkinnedAssetAndAuthoredDynamicPose() {
        val project = PrototypeScene.createAnimePoseStudy()
        val hero = project.actors.single { it.kind == ActorKind.CHARACTER }

        assertEquals("Anime Pose Study", project.name)
        assertEquals(PrototypeScene.ANIME_ASSET_ID, hero.asset?.assetId)
        assertEquals("models/mise_anime.glb", hero.asset?.relativePath)
        assertEquals("CC0-1.0", hero.asset?.license)
        assertEquals(HairStyle.NONE, hero.appearance.hairStyle)
        assertEquals(EnvironmentPreset.DAYLIGHT, project.world.environmentPreset)
        assertEquals(10, hero.rig?.joints?.size)
        assertTrue(hero.rig!!.joints.values.any { it.z > 30f })
        assertTrue(hero.rig.joints.values.any { it.z < -30f })
        assertTrue(hero.rig.joints.values.any { it.x != 0f })
        assertTrue(hero.rig.joints.keys.any { it.contains("calf") })
        assertTrue(project.actors.any { it.kind == ActorKind.LIGHT })
        assertNotNull(project.cameras.firstOrNull())
        assertEquals(project, SceneProjectCodec.decode(SceneProjectCodec.encode(project)))
    }

    @Test fun dynamicPoseIsRetargetedByActualFilamentRigBoneNames() {
        val character = PrototypeScene.createAnimePoseStudy().actors
            .single { it.id == "anime-hero" }
        val rotations = character.rig!!.joints
        val imported = RigDefinition(
            name = "Runtime rig with a different root",
            bones = rotations.keys.mapIndexed { index, original ->
                RigBone(id = "runtime-root/$index", name = original.substringAfterLast('/').replace("-", "."))
            },
        )
        // The real rig names use dots before L/R; slug("upperarm01.l") is upperarm01-l.
        val remapped = PrototypeScene.resolveAnimePose(character, imported)
        assertEquals(rotations.size, remapped.rig!!.joints.size)
        assertTrue(remapped.rig.joints.keys.all { it.startsWith("runtime-root/") })
        assertEquals(character, PrototypeScene.resolveAnimePose(character, RigDefinition(
            bones = rotations.keys.map { RigBone(it, it.substringAfterLast('/')) }
        )))
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
