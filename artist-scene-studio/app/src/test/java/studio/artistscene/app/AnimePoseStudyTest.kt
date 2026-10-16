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
        assertEquals(9, hero.rig?.joints?.size)
        assertTrue(hero.rig!!.joints.values.any { it.z > 30f })
        assertTrue(hero.rig.joints.values.any { it.z < -30f })
        assertTrue(hero.rig.joints.values.any { it.x != 0f })
        assertTrue(hero.rig.joints.keys.any { it.contains("calf") })
        assertTrue(project.actors.any { it.kind == ActorKind.LIGHT })
        assertNotNull(project.cameras.firstOrNull())
        assertTrue("VRoid camera should see the modeled anime face", project.cameras.first().position.z < 0f)
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

    @Test fun authenticVRoidBipSkeletonResolvesAllAuthoredPoseJoints() {
        val hero = PrototypeScene.createAnimePoseStudy().actors.single { it.id == "anime-hero" }
        val names = mapOf(
            "head" to "J_Bip_C_Head",
            "upperarm-l" to "J_Bip_L_UpperArm",
            "lowerarm-l" to "J_Bip_L_LowerArm",
            "upperarm-r" to "J_Bip_R_UpperArm",
            "lowerarm-r" to "J_Bip_R_LowerArm",
            "thigh-l" to "J_Bip_L_UpperLeg",
            "calf-l" to "J_Bip_L_LowerLeg",
            "thigh-r" to "J_Bip_R_UpperLeg",
            "calf-r" to "J_Bip_R_LowerLeg",
        )
        val rig = RigDefinition(
            bones = hero.rig!!.joints.keys.mapIndexed { index, id ->
                RigBone(id = "vroid-bones/$index", name = names.getValue(id.substringAfterLast('/')))
            },
        )
        val resolved = PrototypeScene.resolveAnimePose(hero, rig).rig!!.joints
        assertEquals(hero.rig.joints.size, resolved.size)
        assertTrue(resolved.keys.all { it.startsWith("vroid-bones/") })
        assertEquals(hero.rig.joints.values.toSet(), resolved.values.toSet())
    }

    @Test fun characterCatalogStillKeepsNeutralAnimeForManualPoseEditing() {
        val anime = PrototypeScene.starterAssets().single {
            it.asset?.assetId == PrototypeScene.ANIME_ASSET_ID
        }
        assertTrue(anime.kind == ActorKind.CHARACTER)
        assertTrue(anime.rig == null || anime.rig!!.joints.isEmpty())
        assertEquals(Vec3(), anime.transform.position)
        assertEquals(Vec3(), anime.transform.rotationEulerDegrees)
        assertTrue(PrototypeScene.starterAssets().any {
            it.asset?.assetId == PrototypeScene.HUMANOID_ASSET_ID
        })
    }
}
