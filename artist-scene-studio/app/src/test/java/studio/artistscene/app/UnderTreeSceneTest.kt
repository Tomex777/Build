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
        assertEquals("models/mise_oak_tree.glb",
            scene.actors.single { it.id == UnderTreeScene.TREE_ID }.asset!!.relativePath)
        assertNotEquals(a.transform.position, b.transform.position)
        assertTrue(a.rig!!.joints.isNotEmpty())
        assertTrue(b.rig!!.joints.isNotEmpty())
        assertTrue(a.appearance.hairPhysicsEnabled)
        assertTrue(b.appearance.hairPhysicsEnabled)
        assertTrue(scene.cameras.single().position.z > a.transform.position.z)
        assertEquals(scene, SceneProjectCodec.decode(SceneProjectCodec.encode(scene)))
    }

    @Test fun actualVroidFacingDirectionsShowBothFacesAndInviteConversation() {
        // The bundled VRoid faces local -Z, verified by the front-camera
        // anime-pose study. Use real authored yaw and head joint angles:
        // don't replace these skinned figures with cosmetic face decals.
        val scene = UnderTreeScene.create()
        val first = scene.actors.single { it.id == UnderTreeScene.FIRST_STUDENT_ID }
        val second = scene.actors.single { it.id == UnderTreeScene.SECOND_STUDENT_ID }
        val firstGaze = first.transform.rotationEulerDegrees.y +
            first.rig!!.joints.getValue("figure/head").y
        val secondGaze = second.transform.rotationEulerDegrees.y +
            second.rig!!.joints.getValue("figure/head").y

        fun gazeX(yawDegrees: Float): Double =
            -kotlin.math.sin(Math.toRadians(yawDegrees.toDouble()))
        fun gazeZ(yawDegrees: Float): Double =
            -kotlin.math.cos(Math.toRadians(yawDegrees.toDouble()))

        assertTrue("Classmate A should turn inward toward B on her right",
            first.transform.position.x < second.transform.position.x && gazeX(firstGaze) > .25)
        assertTrue("Classmate B should turn inward toward A on his left",
            gazeX(secondGaze) < -.25)
        assertTrue("Both modeled faces must stay visible from the front +Z camera",
            gazeZ(firstGaze) > .8 && gazeZ(secondGaze) > .8)

        val camera = scene.cameras.single()
        assertTrue("Keep both posed VRoid arms in the portrait frame rather than zooming too close",
            camera.position.z in 5.8f..6.2f)
        assertTrue("Keep real oak crown, both classmates and ground framed",
            camera.target.y in 1.45f..1.75f)
        // At the original 320x640 portrait ratio, this is the view width at
        // student depth. It is a guardrail, not a substitute for device PNG QA.
        val depth = camera.position.z - minOf(
            first.transform.position.z, second.transform.position.z)
        val portraitWidthAtStudents = 2.0 * depth *
            kotlin.math.tan(Math.toRadians(camera.verticalFovDegrees.toDouble() / 2.0)) * .5
        assertTrue("Clipping both characters defeats a tighter portrait shot",
            portraitWidthAtStudents >= 3.0)
        assertTrue("Real-model portrait staging must not turn into an overly distant panorama",
            portraitWidthAtStudents < 3.65)
    }

    private fun earlierClassmatesScene(): studio.artistscene.core.SceneProject {
        val fresh = UnderTreeScene.create()
        return fresh.copy(
            actors = fresh.actors.map { actor ->
                when (actor.id) {
                    UnderTreeScene.FIRST_STUDENT_ID -> actor.copy(
                        transform = actor.transform.copy(
                            position = studio.artistscene.core.Vec3(-0.73f, 0f, -0.13f),
                            rotationEulerDegrees = studio.artistscene.core.Vec3(y = 164f),
                        ),
                        rig = actor.rig!!.copy(joints = actor.rig.joints +
                            ("figure/head" to studio.artistscene.core.Vec3(y = -18f, z = 4f))),
                    )
                    UnderTreeScene.SECOND_STUDENT_ID -> actor.copy(
                        transform = actor.transform.copy(
                            position = studio.artistscene.core.Vec3(0.70f, 0f, -0.09f),
                            rotationEulerDegrees = studio.artistscene.core.Vec3(y = 198f),
                        ),
                        rig = actor.rig!!.copy(joints = actor.rig.joints +
                            ("figure/head" to studio.artistscene.core.Vec3(y = 18f, z = -3f))),
                    )
                    else -> actor
                }
            },
            cameras = listOf(studio.artistscene.core.SceneCamera(
                id = "camera-main", name = "Under the tree",
                position = studio.artistscene.core.Vec3(0.14f, 1.82f, 6f),
                target = studio.artistscene.core.Vec3(0f, 1.60f, -0.35f),
                verticalFovDegrees = 55f,
            )),
        )
    }

    @Test fun legacyDemoCompositionMigratesOnlyIfEntireStockPairAndCameraAreUntouched() {
        val legacy = earlierClassmatesScene()
        assertEquals(UnderTreeScene.create(),
            UnderTreeScene.upgradeUntouchedDemoComposition(legacy))
        assertEquals(legacy.copy(id = "my-other-project"),
            UnderTreeScene.upgradeUntouchedDemoComposition(legacy.copy(id = "my-other-project")))

        // Artists must not lose any of their authored poses, camera work,
        // changed tree, custom appearance, or saved animation and poses.
        val changedArm = legacy.copy(actors = legacy.actors.map {
            if (it.id == UnderTreeScene.FIRST_STUDENT_ID)
                it.copy(rig = it.rig!!.copy(joints = it.rig.joints +
                    ("figure/upperarm-l" to studio.artistscene.core.Vec3(z = 43f))))
            else it
        })
        assertEquals(changedArm,
            UnderTreeScene.upgradeUntouchedDemoComposition(changedArm))
        val changedCamera = legacy.copy(cameras = legacy.cameras.map {
            it.copy(position = it.position.copy(z = 5.75f))
        })
        assertEquals(changedCamera,
            UnderTreeScene.upgradeUntouchedDemoComposition(changedCamera))
        val movedTree = legacy.copy(actors = legacy.actors.map {
            if (it.id == UnderTreeScene.TREE_ID)
                it.copy(transform = it.transform.copy(
                    position = studio.artistscene.core.Vec3(-4f, 0f, -2f)))
            else it
        })
        assertEquals(movedTree,
            UnderTreeScene.upgradeUntouchedDemoComposition(movedTree))
        val withCustomObject = legacy.copy(actors = legacy.actors + legacy.actors[0].copy(id = "my-prop"))
        assertEquals(withCustomObject,
            UnderTreeScene.upgradeUntouchedDemoComposition(withCustomObject))
        val customHair = legacy.copy(actors = legacy.actors.map {
            if (it.id == UnderTreeScene.SECOND_STUDENT_ID)
                it.copy(appearance = it.appearance.copy(animeHairTintHex = "#B97744"))
            else it
        })
        assertEquals(customHair,
            UnderTreeScene.upgradeUntouchedDemoComposition(customHair))
    }

    @Test fun poseUpgradeKeepsDiscoveredRealSkinIdsAndSavedFaceMorphs() {
        val legacy = earlierClassmatesScene()
        val modelNames = mapOf(
            "spine" to "J_Bip_C_Spine",
            "head" to "J_Bip_C_Head",
            "upperarm-l" to "J_Bip_L_UpperArm",
            "lowerarm-l" to "J_Bip_L_LowerArm",
            "upperarm-r" to "J_Bip_R_UpperArm",
            "lowerarm-r" to "J_Bip_R_LowerArm",
            "thigh-l" to "J_Bip_L_UpperLeg",
            "thigh-r" to "J_Bip_R_UpperLeg",
        )
        val retargeted = legacy.copy(actors = legacy.actors.map { actor ->
            if (actor.kind != ActorKind.CHARACTER) actor
            else {
                val discovered = RigDefinition(
                    bones = modelNames.entries.mapIndexed { idx, (_, name) ->
                        RigBone(id = "real-skin/$idx", name = name)
                    },
                    morphTargets = listOf(studio.artistscene.core.RigMorphTarget(
                        "morph/face/joy-3", "Joy", "Face")),
                )
                PrototypeScene.resolveAnimePose(actor, discovered)
            }
        })
        val migrated = UnderTreeScene.upgradeUntouchedDemoComposition(retargeted)
        for (actorId in listOf(UnderTreeScene.FIRST_STUDENT_ID, UnderTreeScene.SECOND_STUDENT_ID)) {
            val before = retargeted.actors.single { it.id == actorId }
            val after = migrated.actors.single { it.id == actorId }
            assertTrue(after.rig!!.joints.keys.all { it.startsWith("real-skin/") })
            assertEquals(before.rig!!.morphWeights, after.rig!!.morphWeights)
            assertNotEquals(before.rig.joints, after.rig.joints)
            assertEquals(before.rig.joints.keys, after.rig.joints.keys)
        }
        val encoded = SceneProjectCodec.encode(migrated)
        assertEquals(migrated, SceneProjectCodec.decode(encoded))
    }

    @Test fun detailedOakReplacesOnlyUntouchedOldDemoTree() {
        val latest = UnderTreeScene.create()
        val oldTree = PrototypeScene.starterAssets().first { it.asset?.assetId == "starter.mise.tree" }
        val old = latest.copy(actors = latest.actors.map {
            if (it.id == UnderTreeScene.TREE_ID) it.copy(
                name = "Shade tree",
                asset = oldTree.asset,
                transform = it.transform.copy(
                    position = studio.artistscene.core.Vec3(-0.34f, 0f, -1.23f),
                    scale = studio.artistscene.core.Vec3(1.16f, 1.16f, 1.16f),
                ),
            ) else it
        })
        val upgraded = UnderTreeScene.upgradeUntouchedDemoTree(old)
        assertEquals("starter.cc0.oak-street-tree",
            upgraded.actors.single { it.id == UnderTreeScene.TREE_ID }.asset!!.assetId)
        assertEquals(old.actors.filterNot { it.id == UnderTreeScene.TREE_ID },
            upgraded.actors.filterNot { it.id == UnderTreeScene.TREE_ID })
        val artistMoved = old.copy(actors = old.actors.map {
            if (it.id == UnderTreeScene.TREE_ID)
                it.copy(transform = it.transform.copy(position = studio.artistscene.core.Vec3(-2f, 0f, -1f)))
            else it
        })
        assertEquals(artistMoved, UnderTreeScene.upgradeUntouchedDemoTree(artistMoved))
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
