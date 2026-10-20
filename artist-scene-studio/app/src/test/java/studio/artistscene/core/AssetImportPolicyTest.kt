package studio.artistscene.core

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetImportPolicyTest {
    private fun glb(json: String, binary: ByteArray? = null): ByteArray {
        val jsonBytes = json.encodeToByteArray()
        val jsonPadding = (4 - jsonBytes.size % 4) % 4
        val binaryPadding = binary?.let { (4 - it.size % 4) % 4 } ?: 0
        val length = 12 + 8 + jsonBytes.size + jsonPadding +
            (if (binary == null) 0 else 8 + binary.size + binaryPadding)
        return ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x46546C67) // glTF
            putInt(2)
            putInt(length)
            putInt(jsonBytes.size + jsonPadding)
            putInt(0x4E4F534A) // JSON
            put(jsonBytes)
            repeat(jsonPadding) { put(' '.code.toByte()) }
            if (binary != null) {
                putInt(binary.size + binaryPadding)
                putInt(0x004E4942) // BIN
                put(binary)
                repeat(binaryPadding) { put(0) }
            }
        }.array()
    }

    @Test
    fun validGlbVersionTwoIsAccepted() {
        val bytes = glb("""{"asset":{"version":"2.0"}}""")
        assertEquals(
            AssetImportValidation.Accepted("glb"),
            AssetImportPolicy.validate("model.glb", bytes.size.toLong(), bytes),
        )
    }

    @Test
    fun vrmExtensionIsAcceptedWhenPayloadIsAValidGlbContainer() {
        val bytes = glb(
            """{"asset":{"version":"2.0"},"buffers":[{"byteLength":4}]}""",
            byteArrayOf(1, 2, 3, 4),
        )
        assertEquals(
            AssetImportValidation.Accepted("glb"),
            AssetImportPolicy.validate("character.vrm", bytes.size.toLong(), bytes),
        )
    }

    @Test
    fun truncatedGlbIsRejectedBeforeRenderer() {
        val bytes = glb("""{"asset":{"version":"2.0"}}""").copyOf()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8, 200)
        assertTrue(AssetImportPolicy.validate("broken.glb", bytes.size.toLong(), bytes) is AssetImportValidation.Rejected)
    }

    @Test
    fun headerOnlyGlbIsRejectedRatherThanSentToFilament() {
        val bytes = glb("""{"asset":{"version":"2.0"}}""").copyOfRange(0, 12)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8, 12)
        assertTrue(AssetImportPolicy.validate("empty.glb", null, bytes) is AssetImportValidation.Rejected)
    }

    @Test
    fun glbWithChunkOverrunningContainerIsRejected() {
        val bytes = glb("""{"asset":{"version":"2.0"}}""")
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(12, 2048)
        assertTrue(AssetImportPolicy.validate("overrun.glb", null, bytes) is AssetImportValidation.Rejected)
    }

    @Test
    fun glbWithoutRequiredBinaryChunkIsRejected() {
        val bytes = glb("""{"asset":{"version":"2.0"},"buffers":[{"byteLength":20}]}""")
        val result = AssetImportPolicy.validate("missing.glb", null, bytes)
        assertTrue(result is AssetImportValidation.Rejected)
        assertTrue((result as AssetImportValidation.Rejected).reason.contains("missing binary buffer"))
    }

    @Test
    fun invalidUtf8InGlbJsonChunkIsRejected() {
        val bytes = glb("""{"asset":{"version":"2.0"}}""")
        bytes[22] = 0xFF.toByte()
        assertTrue(AssetImportPolicy.validate("badjson.glb", null, bytes) is AssetImportValidation.Rejected)
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
    fun standaloneGltfWithImplicitExternalBufferIsRejected() {
        val json = """{"asset":{"version":"2.0"},"buffers":[{"byteLength":5}]}""".encodeToByteArray()
        assertTrue(AssetImportPolicy.validate("implicit.gltf", null, json) is AssetImportValidation.Rejected)
    }

    @Test
    fun invalidGltfVersionIsRejected() {
        val json = """{"asset":{"version":"20.0"}}""".encodeToByteArray()
        assertTrue(AssetImportPolicy.validate("wrong.gltf", null, json) is AssetImportValidation.Rejected)
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
    fun stagedGlbChecksChunkBoundariesWithoutLoading64MiBBinaryIntoHeap() {
        val file = Files.createTempFile("mise-large-glb", ".glb").toFile()
        try {
            val length = 64L * 1024 * 1024
            val json = """{"asset":{"version":"2.0"},"buffers":[{"byteLength":1}]}"""
            val jsonBytes = json.encodeToByteArray()
            val jsonPadding = (4 - jsonBytes.size % 4) % 4
            val paddedJson = jsonBytes.size + jsonPadding
            val binaryChunkLength = (length - 12 - 8 - paddedJson - 8).toInt()
            file.outputStream().use { output ->
                val header = ByteBuffer.allocate(20 + paddedJson + 8).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(0x46546C67)
                    putInt(2)
                    putInt(length.toInt())
                    putInt(paddedJson)
                    putInt(0x4E4F534A)
                    put(jsonBytes)
                    repeat(jsonPadding) { put(' '.code.toByte()) }
                    putInt(binaryChunkLength)
                    putInt(0x004E4942)
                }
                output.write(header.array())
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
