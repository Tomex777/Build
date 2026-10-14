package studio.artistscene.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VrmMetadataInspectorTest {
    @Test
    fun readsVrm10MetadataExpressionsAndHumanoidMapFromGlbJsonChunk() {
        val json = """
            {
              "asset":{"version":"2.0"},
              "extensions":{
                "VRMC_vrm":{
                  "specVersion":"1.0",
                  "meta":{
                    "name":"Studio Avatar",
                    "authors":["Example Artist"],
                    "licenseUrl":"https://vrm.dev/licenses/1.0/",
                    "creditNotation":"required",
                    "allowRedistribution":true
                  },
                  "humanoid":{
                    "humanBones":{
                      "hips":{"node":4},
                      "leftIndexProximal":{"node":31},
                      "rightIndexProximal":{"node":47}
                    }
                  },
                  "expressions":{
                    "preset":{"happy":{},"blink":{}},
                    "custom":{"smirk":{}}
                  }
                }
              }
            }
        """.trimIndent()
        val file = Files.createTempFile("mise-vrm10-", ".vrm").toFile()
        try {
            file.writeBytes(glb(json))
            val metadata = requireNotNull(VrmMetadataInspector.inspect(file))
            assertEquals("VRM 1.0", metadata.specification)
            assertEquals("Studio Avatar", metadata.name)
            assertEquals(listOf("Example Artist"), metadata.authors)
            assertEquals("VRM Public License 1.0", metadata.licenseName)
            assertEquals("https://vrm.dev/licenses/1.0/", metadata.licenseUrl)
            assertTrue(metadata.creditRequired == true)
            assertTrue(metadata.allowRedistribution == true)
            assertEquals(listOf("blink", "happy", "smirk"), metadata.expressionNames)
            assertEquals(31, metadata.humanoidNodeIndices["leftIndexProximal"])
            assertEquals(47, metadata.humanoidNodeIndices["rightIndexProximal"])
        } finally {
            file.delete()
        }
    }

    @Test
    fun readsLegacyVrmMetadataWithoutInventingRedistributionRights() {
        val metadata = requireNotNull(
            VrmMetadataInspector.inspectJsonForTest(
                """
                {
                  "extensions":{
                    "VRM":{
                      "meta":{
                        "title":"Legacy Avatar",
                        "author":"Legacy Artist",
                        "licenseName":"CC_BY",
                        "otherLicenseUrl":"https://example.test/license"
                      },
                      "humanoid":{
                        "humanBones":[
                          {"bone":"hips","node":1},
                          {"bone":"leftThumbProximal","node":18}
                        ]
                      },
                      "blendShapeMaster":{
                        "blendShapeGroups":[
                          {"name":"Smile","presetName":"joy"},
                          {"name":"Custom Face","presetName":"unknown"}
                        ]
                      }
                    }
                  }
                }
                """.trimIndent(),
            ),
        )
        assertEquals("VRM 0.x", metadata.specification)
        assertEquals("Legacy Avatar", metadata.name)
        assertEquals(listOf("Legacy Artist"), metadata.authors)
        assertEquals("CC_BY", metadata.licenseName)
        assertEquals("https://example.test/license", metadata.licenseUrl)
        assertNull(metadata.allowRedistribution)
        assertEquals(listOf("Custom Face", "joy"), metadata.expressionNames)
        assertEquals(18, metadata.humanoidNodeIndices["leftThumbProximal"])
    }

    @Test
    fun plainGltfMetadataIsNotMisrepresentedAsVrm() {
        assertNull(
            VrmMetadataInspector.inspectJsonForTest(
                """{"asset":{"version":"2.0"},"extensions":{}}""",
            ),
        )
    }

    private fun glb(json: String): ByteArray {
        val rawJson = json.toByteArray(Charsets.UTF_8)
        val paddedSize = (rawJson.size + 3) and 3.inv()
        val jsonChunk = ByteArray(paddedSize) { ' '.code.toByte() }
        rawJson.copyInto(jsonChunk)

        val total = 12 + 8 + jsonChunk.size
        val output = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        output.putInt(0x46546C67)
        output.putInt(2)
        output.putInt(total)
        output.putInt(jsonChunk.size)
        output.putInt(0x4E4F534A)
        output.put(jsonChunk)
        return output.array()
    }
}
