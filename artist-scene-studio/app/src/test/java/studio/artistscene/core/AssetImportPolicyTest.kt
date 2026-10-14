package studio.artistscene.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetImportPolicyTest {
    @Test
    fun validGlbVersionTwoIsAccepted() {
        val bytes = ByteArray(12)
        bytes[0] = 'g'.code.toByte()
        bytes[1] = 'l'.code.toByte()
        bytes[2] = 'T'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(4, 2)
            putInt(8, bytes.size)
        }
        assertEquals(
            AssetImportValidation.Accepted("glb"),
            AssetImportPolicy.validate("model.glb", bytes.size.toLong(), bytes),
        )
    }

    @Test
    fun vrmExtensionIsAcceptedWhenPayloadIsAValidGlbContainer() {
        val bytes = ByteArray(12)
        bytes[0] = 'g'.code.toByte()
        bytes[1] = 'l'.code.toByte()
        bytes[2] = 'T'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(4, 2)
            putInt(8, bytes.size)
        }
        assertEquals(
            AssetImportValidation.Accepted("glb"),
            AssetImportPolicy.validate("character.vrm", bytes.size.toLong(), bytes),
        )
    }

    @Test
    fun truncatedGlbIsRejectedBeforeRenderer() {
        val bytes = ByteArray(12)
        bytes[0] = 'g'.code.toByte()
        bytes[1] = 'l'.code.toByte()
        bytes[2] = 'T'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(4, 2)
            putInt(8, 200)
        }
        val result = AssetImportPolicy.validate("broken.glb", 12, bytes)
        assertTrue(result is AssetImportValidation.Rejected)
    }

    @Test
    fun selfContainedGltfIsAccepted() {
        val json = """
            {
              "asset": {"version": "2.0"},
              "buffers": [{"uri": "data:application/octet-stream;base64,AA==", "byteLength": 1}],
              "images": [{"uri": "data:image/png;base64,AA=="}]
            }
        """.trimIndent().encodeToByteArray()
        assertEquals(
            AssetImportValidation.Accepted("gltf"),
            AssetImportPolicy.validate("embedded.gltf", json.size.toLong(), json),
        )
    }

    @Test
    fun externalGltfDependenciesAreRejectedWithActionableReason() {
        val json = """
            {
              "asset": {"version": "2.0"},
              "buffers": [{"uri": "mesh.bin", "byteLength": 12}],
              "images": [{"uri": "albedo.png"}]
            }
        """.trimIndent().encodeToByteArray()
        val result = AssetImportPolicy.validate("external.gltf", json.size.toLong(), json)
        assertTrue(result is AssetImportValidation.Rejected)
        assertTrue((result as AssetImportValidation.Rejected).reason.contains("Export it as GLB"))
    }

    @Test
    fun oversizedReportedAssetIsRejectedWithoutParsing() {
        val result = AssetImportPolicy.validate(
            "huge.glb",
            AssetImportPolicy.MAX_BYTES + 1,
            byteArrayOf(1, 2, 3),
        )
        assertTrue(result is AssetImportValidation.Rejected)
    }

    @Test
    fun stagedGlbIsValidatedFromHeaderWithoutLoadingTheWholePayload() {
        val file = Files.createTempFile("mise-large-glb", ".glb").toFile()
        try {
            val length = 64L * 1024 * 1024
            file.outputStream().use { output ->
                output.write(byteArrayOf('g'.code.toByte(), 'l'.code.toByte(), 'T'.code.toByte(), 'F'.code.toByte()))
                output.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(2).putInt(length.toInt()).array())
                output.channel.position(length - 1)
                output.write(0)
            }
            assertEquals(AssetImportValidation.Accepted("glb"), AssetImportPolicy.validate(file.name, null, file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun oversizedSelfContainedJsonIsRejectedBeforeParsing() {
        val file = Files.createTempFile("mise-huge-gltf", ".gltf").toFile()
        try {
            file.outputStream().use { output ->
                output.write("{\"asset\":{\"version\":\"2.0\"}}".encodeToByteArray())
                output.channel.position(AssetImportPolicy.MAX_GLTF_JSON_BYTES)
                output.write(' '.code)
            }
            val result = AssetImportPolicy.validate(file.name, null, file)
            assertTrue(result is AssetImportValidation.Rejected)
            assertTrue((result as AssetImportValidation.Rejected).reason.contains("too large"))
        } finally {
            file.delete()
        }
    }
}
