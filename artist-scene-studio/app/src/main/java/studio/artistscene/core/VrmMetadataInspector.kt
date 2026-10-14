package studio.artistscene.core

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class VrmMetadata(
    val specification: String,
    val name: String? = null,
    val authors: List<String> = emptyList(),
    val licenseName: String? = null,
    val licenseUrl: String? = null,
    val creditRequired: Boolean? = null,
    val allowRedistribution: Boolean? = null,
    val expressionNames: List<String> = emptyList(),
    val humanoidNodeIndices: Map<String, Int> = emptyMap(),
)

/**
 * Reads only the bounded JSON chunk from a GLB/VRM file.
 *
 * Rendering remains generic glTF/Filament. This inspector exists so Mise can retain provenance,
 * canonical humanoid mappings and expression names instead of discarding VRM metadata during
 * import. It intentionally does not execute or interpret VRM-specific runtime behavior.
 */
object VrmMetadataInspector {
    private const val GLB_MAGIC = 0x46546C67
    private const val GLB_VERSION = 2
    private const val JSON_CHUNK = 0x4E4F534A
    private const val MAX_JSON_BYTES = 16 * 1024 * 1024

    fun inspect(file: File): VrmMetadata? = runCatching {
        if (!file.isFile || file.length() < 20L) return null
        RandomAccessFile(file, "r").use { input ->
            val headerBytes = ByteArray(12)
            input.readFully(headerBytes)
            val header = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            if (header.int != GLB_MAGIC || header.int != GLB_VERSION) return null
            val declaredLength = header.int.toLong() and 0xffffffffL
            if (declaredLength != file.length()) return null

            var offset = 12L
            while (offset + 8L <= declaredLength) {
                input.seek(offset)
                val chunkHeaderBytes = ByteArray(8)
                input.readFully(chunkHeaderBytes)
                val chunkHeader = ByteBuffer.wrap(chunkHeaderBytes).order(ByteOrder.LITTLE_ENDIAN)
                val chunkLength = chunkHeader.int.toLong() and 0xffffffffL
                val chunkType = chunkHeader.int
                val dataStart = offset + 8L
                val dataEnd = dataStart + chunkLength
                if (chunkLength < 0L || dataEnd > declaredLength) return null

                if (chunkType == JSON_CHUNK) {
                    if (chunkLength > MAX_JSON_BYTES) return null
                    val bytes = ByteArray(chunkLength.toInt())
                    input.readFully(bytes)
                    val text = bytes.toString(Charsets.UTF_8)
                        .trimEnd { it == '\u0000' || it.isWhitespace() }
                    val root = Json.parseToJsonElement(text).jsonObject
                    return inspectRoot(root)
                }
                offset = dataEnd
            }
        }
        null
    }.getOrNull()

    internal fun inspectJsonForTest(text: String): VrmMetadata? = runCatching {
        inspectRoot(Json.parseToJsonElement(text).jsonObject)
    }.getOrNull()

    private fun inspectRoot(root: JsonObject): VrmMetadata? {
        val extensions = root["extensions"]?.jsonObject ?: return null
        extensions["VRMC_vrm"]?.jsonObject?.let { return inspectVrm10(it) }
        extensions["VRM"]?.jsonObject?.let { return inspectVrm0(it) }
        return null
    }

    private fun inspectVrm10(vrm: JsonObject): VrmMetadata {
        val meta = vrm["meta"]?.jsonObject
        val authors = meta?.get("authors")?.jsonArray.orEmpty()
            .mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
        val licenseUrl = meta.string("licenseUrl")
        val licenseName = when {
            licenseUrl?.contains("vrm.dev/licenses/1.0", ignoreCase = true) == true ->
                "VRM Public License 1.0"
            licenseUrl != null -> "VRM 1.0 license"
            else -> null
        }
        val creditRequired = meta.string("creditNotation")?.let {
            when (it.lowercase()) {
                "required" -> true
                "unnecessary" -> false
                else -> null
            }
        }
        val allowRedistribution = meta?.get("allowRedistribution")?.jsonPrimitive?.booleanOrNull

        val expressions = vrm["expressions"]?.jsonObject
        val expressionNames = buildList {
            expressions?.get("preset")?.jsonObject?.keys?.let(::addAll)
            expressions?.get("custom")?.jsonObject?.keys?.let(::addAll)
        }.distinct().sorted()

        val humanBones = vrm["humanoid"]?.jsonObject
            ?.get("humanBones")?.jsonObject
            ?.mapNotNull { (semantic, entry) ->
                entry.jsonObject["node"]?.jsonPrimitive?.intOrNull?.let { semantic to it }
            }
            ?.toMap()
            .orEmpty()

        return VrmMetadata(
            specification = "VRM 1.0",
            name = meta.string("name"),
            authors = authors,
            licenseName = licenseName,
            licenseUrl = licenseUrl,
            creditRequired = creditRequired,
            allowRedistribution = allowRedistribution,
            expressionNames = expressionNames,
            humanoidNodeIndices = humanBones,
        )
    }

    private fun inspectVrm0(vrm: JsonObject): VrmMetadata {
        val meta = vrm["meta"]?.jsonObject
        val author = meta.string("author")?.let(::listOf).orEmpty()
        val expressionNames = vrm["blendShapeMaster"]?.jsonObject
            ?.get("blendShapeGroups")?.jsonArray
            ?.mapNotNull { group ->
                val obj = group.jsonObject
                obj.string("presetName")?.takeUnless { it.equals("unknown", ignoreCase = true) }
                    ?: obj.string("name")
            }
            ?.distinct()
            ?.sorted()
            .orEmpty()
        val humanBones = vrm["humanoid"]?.jsonObject
            ?.get("humanBones")?.jsonArray
            ?.mapNotNull { entry ->
                val obj = entry.jsonObject
                val semantic = obj.string("bone")
                val node = obj["node"]?.jsonPrimitive?.intOrNull
                if (semantic != null && node != null) semantic to node else null
            }
            ?.toMap()
            .orEmpty()

        return VrmMetadata(
            specification = "VRM 0.x",
            name = meta.string("title"),
            authors = author,
            licenseName = meta.string("licenseName"),
            licenseUrl = meta.string("otherLicenseUrl"),
            expressionNames = expressionNames,
            humanoidNodeIndices = humanBones,
        )
    }

    private fun JsonObject?.string(key: String): String? =
        this?.get(key)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
}
