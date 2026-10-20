package studio.artistscene.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface AssetImportValidation {
    data class Accepted(val format: String) : AssetImportValidation
    data class Rejected(val reason: String) : AssetImportValidation
}

/**
 * Validates untrusted model containers before passing them to the renderer.
 * In particular, the GLB JSON chunk is bounded and read independently: a 128 MiB import must
 * never require a 128 MiB Java heap allocation simply to verify its headers.
 */
object AssetImportPolicy {
    const val MAX_BYTES: Long = 128L * 1024L * 1024L
    const val MAX_GLTF_JSON_BYTES: Long = 16L * 1024L * 1024L

    private const val GLB_JSON_CHUNK = 0x4E4F534A
    private const val GLB_BINARY_CHUNK = 0x004E4942

    /** The file path is the SAF-staged local copy, never an untrusted remote pathname. */
    fun validate(fileName: String, reportedSize: Long?, file: File): AssetImportValidation {
        if (!file.isFile || file.length() == 0L) {
            return AssetImportValidation.Rejected("The selected model is empty.")
        }
        if (file.length() > MAX_BYTES || (reportedSize != null && reportedSize > MAX_BYTES)) {
            return AssetImportValidation.Rejected("Model is larger than the 128 MiB import limit.")
        }
        val name = fileName.lowercase()
        return when {
            name.endsWith(".glb") || name.endsWith(".vrm") || file.hasGlbMagic() ->
                RandomAccessFile(file, "r").use { raf ->
                    validateGlb(file.length()) { offset, count ->
                        ByteArray(count).also { raf.seek(offset); raf.readFully(it) }
                    }
                }
            name.endsWith(".gltf") ->
                if (file.length() > MAX_GLTF_JSON_BYTES) {
                    AssetImportValidation.Rejected("This glTF file is too large to inspect safely. Export it as GLB and try again.")
                } else {
                    validateGltf(file.readBytes(), standalone = true, hasBinaryChunk = false)
                }
            else -> AssetImportValidation.Rejected("Choose a GLB, VRM, or self-contained glTF 2.0 model.")
        }
    }

    fun validate(fileName: String, reportedSize: Long?, bytes: ByteArray): AssetImportValidation {
        if (bytes.size.toLong() > MAX_BYTES || (reportedSize != null && reportedSize > MAX_BYTES)) {
            return AssetImportValidation.Rejected("Model is larger than the 128 MiB import limit.")
        }
        if (bytes.isEmpty()) {
            return AssetImportValidation.Rejected("The selected model is empty.")
        }
        val name = fileName.lowercase()
        return when {
            name.endsWith(".glb") || name.endsWith(".vrm") || bytes.hasGlbMagic() ->
                validateGlb(bytes.size.toLong()) { offset, count ->
                    bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
                }
            name.endsWith(".gltf") ->
                if (bytes.size.toLong() > MAX_GLTF_JSON_BYTES) {
                    AssetImportValidation.Rejected("This glTF file is too large to inspect safely. Export it as GLB and try again.")
                } else {
                    validateGltf(bytes, standalone = true, hasBinaryChunk = false)
                }
            else -> AssetImportValidation.Rejected("Choose a GLB, VRM, or self-contained glTF 2.0 model.")
        }
    }

    /** Walk GLB chunk headers without loading the binary buffers or textures into memory. */
    private fun validateGlb(
        length: Long,
        read: (offset: Long, count: Int) -> ByteArray,
    ): AssetImportValidation {
        if (length < 20 || length % 4L != 0L) {
            return AssetImportValidation.Rejected("The GLB is missing its required JSON chunk.")
        }
        val header = ByteBuffer.wrap(read(0, 12)).order(ByteOrder.LITTLE_ENDIAN)
        if (header.int != 0x46546C67) {
            return AssetImportValidation.Rejected("The file does not contain a valid GLB header.")
        }
        if (header.int != 2) {
            return AssetImportValidation.Rejected("Only glTF 2.0 / GLB version 2 is supported.")
        }
        val declaredLength = header.int.toLong() and 0xFFFF_FFFFL
        if (declaredLength != length) {
            return AssetImportValidation.Rejected("The GLB is truncated or has an invalid declared length.")
        }

        var offset = 12L
        var jsonLength = 0
        var jsonOffset = -1L
        var hasBinaryChunk = false
        var chunkIndex = 0
        while (offset < length) {
            if (length - offset < 8L) {
                return AssetImportValidation.Rejected("The GLB contains an incomplete chunk header.")
            }
            val chunkHeader = ByteBuffer.wrap(read(offset, 8)).order(ByteOrder.LITTLE_ENDIAN)
            val chunkLength = chunkHeader.int.toLong() and 0xFFFF_FFFFL
            val chunkType = chunkHeader.int
            offset += 8
            if (chunkLength % 4L != 0L || chunkLength > length - offset) {
                return AssetImportValidation.Rejected("The GLB contains a truncated or misaligned chunk.")
            }
            if (chunkIndex == 0) {
                if (chunkType != GLB_JSON_CHUNK || chunkLength == 0L || chunkLength > MAX_GLTF_JSON_BYTES) {
                    return AssetImportValidation.Rejected("The GLB must start with a JSON chunk no larger than 16 MiB.")
                }
                jsonOffset = offset
                jsonLength = chunkLength.toInt()
            } else if (chunkType == GLB_BINARY_CHUNK) {
                hasBinaryChunk = true
            }
            offset += chunkLength
            chunkIndex++
        }
        if (jsonOffset < 0L) {
            return AssetImportValidation.Rejected("The GLB does not contain a JSON chunk.")
        }
        return when (val result = validateGltf(read(jsonOffset, jsonLength), standalone = false, hasBinaryChunk)) {
            is AssetImportValidation.Accepted -> AssetImportValidation.Accepted("glb")
            is AssetImportValidation.Rejected -> result
        }
    }

    private fun validateGltf(
        bytes: ByteArray,
        standalone: Boolean,
        hasBinaryChunk: Boolean,
    ): AssetImportValidation {
        val text = runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .trimEnd('\u0000', ' ')
        }.getOrElse {
            return AssetImportValidation.Rejected("The glTF JSON could not be decoded as UTF-8.")
        }
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrElse {
            return AssetImportValidation.Rejected("The glTF JSON is malformed.")
        }
        val version = runCatching { root["asset"]?.jsonObject?.get("version")?.jsonPrimitive?.content }.getOrNull()
        if (version != "2.0") {
            return AssetImportValidation.Rejected("Only glTF 2.0 models are supported.")
        }
        val invalidDependencies = runCatching {
            var unembeddedBuffer = false
            var externalUri = false
            root["buffers"]?.jsonArray?.forEach { entry ->
                val uri = entry.jsonObject["uri"]?.jsonPrimitive?.content
                if (uri == null) unembeddedBuffer = true
                else if (!uri.isEmbeddedDataUri()) externalUri = true
            }
            root["images"]?.jsonArray?.forEach { entry ->
                val uri = entry.jsonObject["uri"]?.jsonPrimitive?.content
                if (uri != null && !uri.isEmbeddedDataUri()) externalUri = true
            }
            externalUri to unembeddedBuffer
        }.getOrElse {
            return AssetImportValidation.Rejected("The glTF JSON has an invalid buffers or images structure.")
        }
        if (invalidDependencies.first) {
            return AssetImportValidation.Rejected(
                "This glTF depends on external files. Export it as GLB or embed its buffers and textures first.",
            )
        }
        if (invalidDependencies.second && (standalone || !hasBinaryChunk)) {
            return AssetImportValidation.Rejected(
                "The glTF has a missing binary buffer. Export it as GLB or embed its buffers first.",
            )
        }
        return AssetImportValidation.Accepted("gltf")
    }

    private fun String.isEmbeddedDataUri(): Boolean =
        startsWith("data:") && indexOf(',') > "data:".length

    private fun ByteArray.hasGlbMagic(): Boolean =
        size >= 4 && this[0] == 'g'.code.toByte() && this[1] == 'l'.code.toByte() &&
            this[2] == 'T'.code.toByte() && this[3] == 'F'.code.toByte()

    private fun File.hasGlbMagic(): Boolean = inputStream().use { stream ->
        stream.read() == 'g'.code && stream.read() == 'l'.code &&
            stream.read() == 'T'.code && stream.read() == 'F'.code
    }
}
