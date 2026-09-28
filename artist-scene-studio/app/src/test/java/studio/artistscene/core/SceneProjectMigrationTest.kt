package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneProjectMigrationTest {
    @Test
    fun schemaOneSceneMigratesWithoutLosingExistingState() {
        val legacy = """
            {
              "schemaVersion": 1,
              "id": "legacy",
              "name": "Legacy Scene",
              "actors": [
                {
                  "id": "box",
                  "name": "Box",
                  "kind": "PROP",
                  "transform": {
                    "position": {"x": 2.0, "y": 1.0, "z": -4.0},
                    "rotationEulerDegrees": {"x": 0.0, "y": 30.0, "z": 0.0},
                    "scale": {"x": 1.0, "y": 1.0, "z": 1.0}
                  }
                }
              ]
            }
        """.trimIndent()

        val migrated = SceneProjectCodec.decode(legacy)
        assertEquals(SceneProject.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals("Legacy Scene", migrated.name)
        assertEquals(Vec3(2f, 1f, -4f), migrated.actors.single().transform.position)
        assertTrue(migrated.referenceImages.isEmpty())
        assertEquals(5f, migrated.timeline.durationSeconds)
    }

    @Test
    fun currentSchemaRoundTripPreservesEditorFacingMetadata() {
        val scene = SceneProject(
            id = "current",
            name = "Current",
            actors = listOf(
                Actor(
                    id = "character",
                    name = "Character",
                    kind = ActorKind.CHARACTER,
                    material = MaterialSettings(roughness = 0.4f),
                    rigDefinition = RigDefinition(
                        bones = listOf(RigBone("hips", "Hips")),
                    ),
                    rig = RigPose(joints = mapOf("hips" to Vec3(0f, 10f, 0f))),
                ),
            ),
            referenceImages = listOf(
                ReferenceImage("ref-1", "Front", "content://reference/front"),
            ),
            timeline = TimelineSettings(durationSeconds = 12f, loop = false, playbackSpeed = 0.75f),
        )

        assertEquals(scene, SceneProjectCodec.decode(SceneProjectCodec.encode(scene)))
    }
}
