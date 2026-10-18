package studio.artistscene.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.artistscene.core.ActorKind
import studio.artistscene.core.AssetStorage
import studio.artistscene.core.RigBone
import studio.artistscene.core.RigDefinition
import studio.artistscene.core.SceneProjectCodec

class UnderTreeSceneTest {
    @Test fun classroomPairIsAnEditableReal3dScene() {
        val scene = UnderTreeScene.create()
        assertEquals(UnderTreeScene.PROJECT_ID, scene.id)
        assertEquals(2, scene.actors.count { it.kind == ActorKind.CHARACTER })
        assertEquals(1, scene.actors.count { it.kind == ActorKind.ENVIRONMENT })
        assertEquals(UnderTreeScene.TREE_ID,
            scene.actors.single { it.kind == ActorKind.ENVIRONMENT }.id)
        assertTrue(scene.actors.any { it.kind == ActorKind.LIGHT })
        assertFalse(scene.world.gridEnabled)
        assertTrue(scene.world.groundEnabled)

        val a = scene.actors.single { it.id == UnderTreeScene.FIRST_STUDENT_ID }
        val b = scene.actors.single { it.id == UnderTreeScene.SECOND_STUDENT_ID }
        assertEquals(PrototypeScene.ANIME_FEMALE_ASSET_ID, a.asset!!.assetId)
        assertEquals(PrototypeScene.ANIME_ASSET_ID, b.asset!!.assetId)
        assertNotEquals(a.asset!!.relativePath, b.asset!!.relativePath)
        assertEquals(AssetStorage.BUNDLED, a.asset!!.storage)
        assertEquals(AssetStorage.BUNDLED, b.asset!!.storage)
        assertEquals("models/mise_tree.glb",
            scene.actors.single { it.id == UnderTreeScene.TREE_ID }.asset!!.relativePath)
        assertNotEquals(a.transform.position, b.transform.position)
        assertTrue(a.rig!!.joints.isNotEmpty())
        assertTrue(b.rig!!.joints.isNotEmpty())
        assertTrue(a.appearance.hairPhysicsEnabled)
        assertTrue(b.appearance.hairPhysicsEnabled)
        assertTrue(scene.cameras.single().position.z > a.transform.position.z)
        assertEquals(scene, SceneProjectCodec.decode(SceneProjectCodec.encode(scene)))
    }

    @Test fun studentsBindToTheirOwnDiscoveredSkinnedRigWithoutAffectingOtherScenes() {
        val scene = UnderTreeScene.create()
        val skeletonNames = mapOf(
            "spine" to "J_Bip_C_Spine",
            "head" to "J_Bip_C_Head",
            "upperarm-l" to "J_Bip_L_UpperArm",
            "lowerarm-l" to "J_Bip_L_LowerArm",
            "upperarm-r" to "J_Bip_R_UpperArm",
            "lowerarm-r" to "J_Bip_R_LowerArm",
            "thigh-l" to "J_Bip_L_UpperLeg",
            "thigh-r" to "J_Bip_R_UpperLeg",
        )
        val rig = RigDefinition(
            bones = skeletonNames.values.mapIndexed { i, name ->
                RigBone(id = "real-rig-bone/$i", name = name)
            },
        )
        for (student in scene.actors.filter { it.kind == ActorKind.CHARACTER }) {
            val expectedRotations = student.rig!!.joints.values.toSet()
            val mapped = PrototypeScene.resolveAnimePose(student, rig)
            assertEquals(expectedRotations, mapped.rig!!.joints.values.toSet())
            assertEquals(student.rig!!.joints.size, mapped.rig!!.joints.size)
            assertTrue(mapped.rig!!.joints.keys.all { id -> id.startsWith("real-rig-bone/") })
            assertEquals(student.id, mapped.id)
        }
        val unrelated = PrototypeScene.starterAssets().first {
            it.asset?.assetId == PrototypeScene.ANIME_FEMALE_ASSET_ID
        }
        assertEquals(unrelated, PrototypeScene.resolveAnimePose(unrelated, rig))
    }
}
