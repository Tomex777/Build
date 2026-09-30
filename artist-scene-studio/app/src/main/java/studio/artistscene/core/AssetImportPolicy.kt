package studio.artistscene.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import java.io.RandomAccessFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface AssetImportValidation {
    data class Accepted(val format: String) : AssetImportValidation
    data class Rejected(val reason: String) : AssetImportValidation
}

/**
 * Defensive intake policy for user supplied 3D files before they are ever handed to Filament.
 * GLB is preferred. JSON glTF is accepted only when every URI dependency is embedded as a data URI.
 */
object AssetImportPolicy {
    const val MAX_BYTES: Long = 128L * 1024L * 1024L
    const val MAX_GLTF_JSON_BYTES: Long = 16L * 1024L * 1024L

    /** Validates a staged import without reading a binary GLB into the Java heap. */
    fun validate(fileName: String, reportedSize: Long?, file: File): AssetImportValidation {
        val length = file.length()
        val size = reportedSize ?: length
        if (size > MAX_BYTES || length > MAX_BYTES) {
            return AssetImportValidation.Rejected("Model is larger than the 128 MiB import limit.")
        }
        if (!file.isFile || length == 0L) {
            return AssetImportValidation.Rejected("The selected model is empty.")
        }
        val lowerName = fileName.lowercase()
        return when {
            lowerName.endsWith(".glb") || file.hasGlbMagic() -> validateGlb(file)
            lowerName.endsWith(".gltf") -> {
                if (length > MAX_GLTF_JSON_BYTES) {
                    AssetImportValidation.Rejected("This glTF file is too large to inspect safely. Export it as GLB and try again.")
                } else {
                    validateGltf(file.readBytes())
                }
            }
            else -> AssetImportValidation.Rejected("Choose a GLB, VRM, or self-contained glTF 2.0 model.")
        }
    }

    fun validate(fileName: String, reportedSize: Long?, bytes: ByteArray): AssetImportValidation {
        if (reportedSize != null && reportedSize > MAX_BYTES) {
            return AssetImportValidation.Rejected("Model is larger than the 128 MiB mobile import limit.")
        }
        if (bytes.size.toLong() > MAX_BYTES) {
            return AssetImportValidation.Rejected("Model is larger than the 128 MiB mobile import limit.")
        }
        if (bytes.isEmpty()) {
            return AssetImportValidation.Rejected("The selected model is empty.")
        }

        val lowerName = fileName.lowercase()
        return when {
            lowerName.endsWith(".glb") || bytes.hasGlbMagic() -> validateGlb(bytes)
            lowerName.endsWith(".gltf") -> validateGltf(bytes)
            else -> AssetImportValidation.Rejected("Choose a GLB, VRM, or self-contained glTF 2.0 model.")
        }
    }

    private fun validateGlb(bytes: ByteArray): AssetImportValidation {
        if (bytes.size < 12 || !bytes.hasGlbMagic()) {
            return AssetImportValidation.Rejected("The file does not contain a valid GLB header.")
        }
        val header = ByteBuffer.wrap(bytes, 4, 8).order(ByteOrder.LITTLE_ENDIAN)
        val version = header.int
        val declaredLength = header.int.toLong() and 0xffffffffL
        if (version != 2) {
            return AssetImportValidation.Rejected("Only glTF 2.0 / GLB version 2 is supported.")
        }
        if (declaredLength != bytes.size.toLong()) {
            return AssetImportValidation.Rejected("The GLB is truncated or has an invalid declared length.")
        }
        return AssetImportValidation.Accepted("glb")
    }

    private fun validateGlb(file: File): AssetImportValidation {
        if (file.length() < 12 || !file.hasGlbMagic()) {
            return AssetImportValidation.Rejected("The file does not contain a valid GLB header.")
        }
        val header = ByteArray(8)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(4)
            raf.readFully(header)
        }
        val values = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val version = values.int
        val declaredLength = values.int.toLong() and 0xffffffffL
        if (version != 2) return AssetImportValidation.Rejected("Only glTF 2.0 / GLB version 2 is supported.")
        if (declaredLength != file.length()) {
            return AssetImportValidation.Rejected("The GLB is truncated or has an invalid declared length.")
        }
        return AssetImportValidation.Accepted("glb")
    }

    private fun validateGltf(bytes: ByteArray): AssetImportValidation {
        val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrElse {
            return AssetImportValidation.Rejected("The glTF JSON could not be decoded as UTF-8.")
        }
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrElse {
            return AssetImportValidation.Rejected("The glTF JSON is malformed.")
        }
        val version = runCatching {
            root["asset"]?.jsonObject?.get("version")?.jsonPrimitive?.content
        }.getOrNull()
        if (version == null || !version.startsWith("2")) {
            return AssetImportValidation.Rejected("Only glTF 2.0 models are supported.")
        }

        val externalUris = runCatching {
            buildList {
                root["buffers"]?.jsonArray?.forEach { entry ->
                    entry.jsonObject["uri"]?.jsonPrimitive?.content?.let { uri ->
                        if (!uri.startsWith("data:")) add(uri)
                    }
                }
                root["images"]?.jsonArray?.forEach { entry ->
                    entry.jsonObject["uri"]?.jsonPrimitive?.content?.let { uri ->
                        if (!uri.startsWith("data:")) add(uri)
                    }
                }
            }
        }.getOrElse {
            return AssetImportValidation.Rejected("The glTF JSON has an invalid buffers or images structure.")
        }
        if (externalUris.isNotEmpty()) {
            return AssetImportValidation.Rejected(
                "This glTF depends on external files. Export it as GLB or embed its buffers and textures first.",
            )
        }
        return AssetImportValidation.Accepted("gltf")
    }

    private fun ByteArray.hasGlbMagic(): Boolean =
        size >= 4 &&
            this[0] == 'g'.code.toByte() &&
            this[1] == 'l'.code.toByte() &&
            this[2] == 'T'.code.toByte() &&
            this[3] == 'F'.code.toByte()

    private fun File.hasGlbMagic(): Boolean = inputStream().use { stream ->
        stream.read() == 'g'.code && stream.read() == 'l'.code &&
            stream.read() == 'T'.code && stream.read() == 'F'.code
    }
}
