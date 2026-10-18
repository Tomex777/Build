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
        assertEquals(studio.artistscene.core.EnvironmentPreset.PARK_SHADE, scene.world.environmentPreset)
        assertTrue(scene.actors.any {
            it.kind == ActorKind.LIGHT && it.light?.type == studio.artistscene.core.LightType.DIRECTIONAL &&
                it.light!!.intensity in 60_000f..80_000f
        })

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

    @Test fun adjustOnlyStockSunWithoutErasingArtistLightOrPose() {
        val origin = UnderTreeScene.create()
        val oldStock = origin.copy(actors = origin.actors.map { actor ->
            if (actor.id == "schoolyard-sun")
                actor.copy(light = actor.light!!.copy(direction = studio.artistscene.core.Vec3(0f, -1f, 0f)))
            else actor
        })
        val updated = UnderTreeScene.refineStockLightAngle(oldStock)
        assertEquals(studio.artistscene.core.Vec3(-0.55f, -1f, -0.45f),
            updated.actors.first { it.id == "schoolyard-sun" }.light!!.direction)
        assertEquals(oldStock.actors.filterNot { it.id == "schoolyard-sun" },
            updated.actors.filterNot { it.id == "schoolyard-sun" })
        val customized = oldStock.copy(actors = oldStock.actors.map { actor ->
            if (actor.id == "schoolyard-sun")
                actor.copy(light = actor.light!!.copy(direction = studio.artistscene.core.Vec3(0.3f, -1f, 0.3f)))
            else actor
        })
        assertEquals(customized, UnderTreeScene.refineStockLightAngle(customized))
    }

    @Test fun starterLibraryContainsRealBicycleCarTreeAndProps() {
        val catalog = PrototypeScene.starterAssets()
        assertTrue(catalog.any { it.kind == ActorKind.VEHICLE && it.asset?.assetId == "starter.mise.bicycle" })
        assertTrue(catalog.any { it.kind == ActorKind.VEHICLE && it.asset?.assetId == "starter.mise.car" })
        assertTrue(catalog.any { it.kind == ActorKind.ENVIRONMENT && it.asset?.assetId == "starter.mise.tree" })
        assertTrue(catalog.any { it.kind == ActorKind.PROP })
    }

    @Test fun stockEnvironmentUpgradePreservesPosesAndCustomWorlds() {
        val latest = UnderTreeScene.create()
        val original = latest.copy(
            world = latest.world.copy(environmentPreset = studio.artistscene.core.EnvironmentPreset.DAYLIGHT),
            actors = latest.actors.filterNot { it.kind == ActorKind.LIGHT } +
                PrototypeScene.create().actors.filter { it.kind == ActorKind.LIGHT },
        )
        val posed = original.copy(actors = original.actors.map { actor ->
            if (actor.id == UnderTreeScene.FIRST_STUDENT_ID)
                actor.copy(transform = actor.transform.copy(position = studio.artistscene.core.Vec3(-2f, 0f, 1f)))
            else actor
        })
        val upgraded = UnderTreeScene.upgradeDefaultEnvironment(posed)
        assertEquals(studio.artistscene.core.EnvironmentPreset.PARK_SHADE, upgraded.world.environmentPreset)
        assertEquals(
            posed.actors.first { it.id == UnderTreeScene.FIRST_STUDENT_ID }.transform,
            upgraded.actors.first { it.id == UnderTreeScene.FIRST_STUDENT_ID }.transform,
        )
        assertEquals(68_000f, upgraded.actors.first { it.id == "key-sun" }.light!!.intensity, 0.01f)
        val custom = posed.copy(world = posed.world.copy(
            environmentPreset = studio.artistscene.core.EnvironmentPreset.OVERCAST,
        ))
        assertEquals(custom, UnderTreeScene.upgradeDefaultEnvironment(custom))
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
