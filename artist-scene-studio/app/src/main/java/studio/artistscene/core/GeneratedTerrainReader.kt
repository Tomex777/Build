package studio.artistscene.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

data class GeneratedTerrain(val heights: FloatArray, val embeddedPng: ByteArray?)

/**
 * Read exactly the native terrain geometry emitted by TerrainMesh.encodeGlb.
 * In particular, never reinterpret an arbitrary user GLB as terrain.
 * The original embedded licensed surface PNG is retained for a sculpted
 * revision rather than silently replacing dirt/rock with default grass.
 */
object GeneratedTerrainReader {
    fun decode(bytes: ByteArray): GeneratedTerrain {
        require(bytes.size >= 28) { "Truncated terrain GLB" }
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bb.int == 0x46546C67 && bb.int == 2) { "Not a glTF 2.0 binary" }
        require(bb.int == bytes.size) { "Incorrect GLB total length" }
        val jsonLength = bb.int
        require(bb.int == 0x4E4F534A && jsonLength in 2..(bytes.size - 28)) {
            "Invalid GLB JSON chunk"
        }
        val jsonOffset = bb.position()
        val jsonEnd = jsonOffset.toLong() + jsonLength
        require(jsonEnd + 8 <= bytes.size) { "Truncated GLB JSON" }
        val json = Json.parseToJsonElement(
            String(bytes, jsonOffset, jsonLength, Charsets.UTF_8).trimEnd(' ', '\u0000')
        ).jsonObject
        require(json["asset"]!!.jsonObject["generator"]!!.jsonPrimitive.content ==
            "Mise native terrain") { "Only Mise-generated heightfields can be sculpted" }
        bb.position(jsonEnd.toInt())
        val binLength = bb.int
        require(bb.int == 0x004E4942 && binLength >= 0 &&
            bb.position().toLong() + binLength == bytes.size.toLong()) {
            "Invalid GLB binary chunk"
        }
        val binStart = bb.position()
        val views = json["bufferViews"]!!.jsonArray
        val accessors = json["accessors"]!!.jsonArray
        val primitive = json["meshes"]!!.jsonArray[0].jsonObject["primitives"]!!
            .jsonArray[0].jsonObject
        val positionIndex = primitive["attributes"]!!.jsonObject["POSITION"]!!.jsonPrimitive.int
        val positionAccessor = accessors[positionIndex].jsonObject
        require(positionAccessor["count"]!!.jsonPrimitive.int == TerrainMesh.VERTICES &&
            positionAccessor["type"]!!.jsonPrimitive.content == "VEC3" &&
            positionAccessor["componentType"]!!.jsonPrimitive.int == 5126) {
            "Unexpected terrain position accessor"
        }
        val view = views[positionAccessor["bufferView"]!!.jsonPrimitive.int].jsonObject
        require(view["byteStride"] == null) { "Interleaved terrain positions are unsupported" }
        val baseOffset = view["byteOffset"]?.jsonPrimitive?.int ?: 0
        val accessorOffset = positionAccessor["byteOffset"]?.jsonPrimitive?.int ?: 0
        val positionSize = TerrainMesh.VERTICES * 3 * 4
        val viewSize = view["byteLength"]!!.jsonPrimitive.int
        require(baseOffset >= 0 && accessorOffset >= 0 &&
            positionSize.toLong() + accessorOffset <= viewSize.toLong() &&
            baseOffset.toLong() + viewSize <= binLength.toLong()) {
            "Terrain positions exceed the GLB bounds"
        }
        val positions = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        positions.position(binStart + baseOffset + accessorOffset)
        val heights = FloatArray(TerrainMesh.VERTICES)
        val spacing = TerrainMesh.EDGE_METERS / TerrainMesh.CELLS
        for (row in 0..TerrainMesh.CELLS) for (col in 0..TerrainMesh.CELLS) {
            val x = positions.float
            val y = positions.float
            val z = positions.float
            require(x.isFinite() && z.isFinite() && y.isFinite() &&
                y in -100f..200f &&
                kotlin.math.abs(x - (-18f + col * spacing)) < 0.002f &&
                kotlin.math.abs(z - (-18f + row * spacing)) < 0.002f) {
                "The mesh is not a 36m Mise terrain heightfield"
            }
            heights[row * (TerrainMesh.CELLS + 1) + col] = y
        }

        val originalPng = json["images"]?.jsonArray?.firstOrNull()?.jsonObject?.let { image ->
            if (image["mimeType"]?.jsonPrimitive?.content != "image/png") return@let null
            val imageIndex = image["bufferView"]?.jsonPrimitive?.int ?: return@let null
            val imageView = views[imageIndex].jsonObject
            val offset = imageView["byteOffset"]?.jsonPrimitive?.int ?: 0
            val size = imageView["byteLength"]!!.jsonPrimitive.int
            require(offset >= 0 && size >= 8 &&
                offset.toLong() + size <= binLength.toLong()) { "Invalid embedded PNG bounds" }
            val png = bytes.copyOfRange(binStart + offset, binStart + offset + size)
            require(png[0] == 0x89.toByte() && png[1] == 0x50.toByte() &&
                png[2] == 0x4E.toByte() && png[3] == 0x47.toByte()) {
                "Invalid embedded PNG header"
            }
            png
        }
        return GeneratedTerrain(heights, originalPng)
    }
}
