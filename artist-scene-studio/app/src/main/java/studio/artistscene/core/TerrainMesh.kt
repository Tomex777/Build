package studio.artistscene.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

enum class TerrainShape(val title: String) {
    HILL("Hill"),
    MOUNTAINS("Mountains"),
    VALLEY("Valley"),
    FLAT("Flat"),
}

/**
 * Real editable scene geometry. Samples are world-space terrain heights in meters
 * (not a normal-map trick); every hill can cast a physical Filament shadow.
 *
 * This generator is pure Kotlin, works fully offline, and emits standards-
 * compliant glTF 2.0 binary with vertex positions, smooth vertex normals,
 * repeatable UV texture coordinates and optional embedded CC0 surface photo.
 */
object TerrainMesh {
    const val CELLS = 64
    const val EDGE_METERS = 36f
    const val VERTICES = (CELLS + 1) * (CELLS + 1)

    fun preset(shape: TerrainShape): FloatArray {
        val n = CELLS + 1
        val data = FloatArray(VERTICES)
        for (row in 0 until n) for (col in 0 until n) {
            val x = (col.toFloat() / CELLS * 2f - 1f)
            val z = (row.toFloat() / CELLS * 2f - 1f)
            fun mound(cx: Float, cz: Float, width: Float, height: Float): Double {
                val dx = (x - cx) / width
                val dz = (z - cz) / width
                return height.toDouble() * exp(-3.0 * (dx * dx + dz * dz).toDouble())
            }
            val h = when (shape) {
                TerrainShape.FLAT -> 0.0
                TerrainShape.HILL -> mound(0.25f, -0.1f, 1.1f, 7f) +
                    mound(-0.5f, 0.45f, 0.7f, 2.5f)
                TerrainShape.MOUNTAINS -> mound(-0.60f, -0.22f, 0.65f, 18f) +
                    mound(0.25f, -0.5f, 0.48f, 13f) +
                    mound(0.74f, 0.13f, 0.57f, 10f) +
                    sin((x * 11 + z * 4).toDouble()) *
                    cos((z * 7 - x * 3).toDouble()) * 0.3
                TerrainShape.VALLEY -> mound(-0.65f, 0.2f, 0.7f, 11f) +
                    mound(0.65f, 0.15f, 0.7f, 11f) +
                    mound(0.0f, -0.85f, 0.5f, 7f)
            }
            data[row * n + col] = h.coerceAtLeast(0.0).toFloat()
        }
        return data
    }

    /** 8-bit source luminance from SAF image pixels, resampled to 65² cells. */
    fun fromGrayscale(pixels: IntArray, maxHeightMeters: Float = 15f): FloatArray {
        require(pixels.size == VERTICES) { "Expected 65 by 65 grayscale pixels" }
        require(maxHeightMeters.isFinite() && maxHeightMeters in 0.1f..100f)
        return FloatArray(VERTICES) { i ->
            val rgba = pixels[i]
            val r = (rgba ushr 16) and 255
            val g = (rgba ushr 8) and 255
            val b = rgba and 255
            (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f * maxHeightMeters
        }
    }

    fun encodeGlb(heights: FloatArray, photoPng: ByteArray? = null): ByteArray {
        require(heights.size == VERTICES)
        require(heights.all { it.isFinite() && it in -100f..200f })
        require(photoPng == null || (photoPng.size > 8 &&
            photoPng[0] == 0x89.toByte() && photoPng[1] == 0x50.toByte())) {
            "Only PNG images can be embedded"
        }

        val n = CELLS + 1
        val buffer = ByteArrayOutputStream()
        fun aligned() { while (buffer.size() % 4 != 0) buffer.write(0) }
        fun append(view: ByteBuffer): Pair<Int, Int> {
            aligned()
            val off = buffer.size()
            buffer.write(view.array())
            return off to view.array().size
        }
        fun floats(count: Int, block: (ByteBuffer) -> Unit): Pair<Int, Int> =
            append(ByteBuffer.allocate(count * 4).order(ByteOrder.LITTLE_ENDIAN).also(block))

        val pos = floats(VERTICES * 3) { dst ->
            for (row in 0..CELLS) for (col in 0..CELLS) {
                val h = heights[row*n+col]
                dst.putFloat(-EDGE_METERS/2f + col*EDGE_METERS/CELLS)
                dst.putFloat(h)
                dst.putFloat(-EDGE_METERS/2f + row*EDGE_METERS/CELLS)
            }
        }
        val normals = floats(VERTICES * 3) { dst ->
            val step = EDGE_METERS/CELLS
            for (row in 0..CELLS) for (col in 0..CELLS) {
                val west = heights[row*n + (col-1).coerceAtLeast(0)]
                val east = heights[row*n + (col+1).coerceAtMost(CELLS)]
                val north = heights[(row-1).coerceAtLeast(0)*n + col]
                val south = heights[(row+1).coerceAtMost(CELLS)*n + col]
                val sx = (east-west)/((if (col==0 || col==CELLS) 1f else 2f)*step)
                val sz = (south-north)/((if (row==0 || row==CELLS) 1f else 2f)*step)
                val norm = sqrt(sx*sx + 1f + sz*sz)
                dst.putFloat(-sx/norm)
                dst.putFloat(1f/norm)
                dst.putFloat(-sz/norm)
            }
        }
        val tex = floats(VERTICES * 2) { dst ->
            for (row in 0..CELLS) for (col in 0..CELLS) {
                dst.putFloat(col.toFloat() / CELLS * 10f)
                dst.putFloat(row.toFloat() / CELLS * 10f)
            }
        }
        val count = CELLS * CELLS * 6
        val indices = append(ByteBuffer.allocate(count*2).order(ByteOrder.LITTLE_ENDIAN).apply {
            for (row in 0 until CELLS) for (col in 0 until CELLS) {
                val a = row*n + col
                val b = (row+1)*n + col
                val c = a + 1
                val d = b + 1
                putShort(a.toShort()); putShort(b.toShort()); putShort(c.toShort())
                putShort(c.toShort()); putShort(b.toShort()); putShort(d.toShort())
            }
        })
        val photo = photoPng?.let {
            aligned()
            val off = buffer.size()
            buffer.write(it)
            off to it.size
        }
        aligned()
        val bin = buffer.toByteArray()
        val views = mutableListOf(
            pos to 34962, normals to 34962, tex to 34962, indices to 34963
        )
        val photoView = if (photo != null) {
            views += photo to 0
            views.lastIndex
        } else null
        val viewJson = views.joinToString(",") { (v, target) ->
            val extra = if (target == 0) "" else ",\"target\":$target"
            "{\"buffer\":0,\"byteOffset\":${v.first},\"byteLength\":${v.second}$extra}"
        }
        val minH = heights.minOrNull() ?: 0f
        val maxH = heights.maxOrNull() ?: 0f
        val material = if (photoView != null) {
            "{\"pbrMetallicRoughness\":{\"baseColorTexture\":{\"index\":0},\"metallicFactor\":0,\"roughnessFactor\":1}}"
        } else "{\"pbrMetallicRoughness\":{\"baseColorFactor\":[0.42,0.48,0.35,1],\"metallicFactor\":0,\"roughnessFactor\":1}}"
        val textureFields = if (photoView != null) {
            """, "images":[{"bufferView":$photoView,"mimeType":"image/png"}],
                "samplers":[{"wrapS":10497,"wrapT":10497}],
                "textures":[{"sampler":0,"source":0}]"""
        } else ""
        val json = """{"asset":{"version":"2.0","generator":"Mise native terrain"},
            "scene":0,"scenes":[{"nodes":[0]}],
            "nodes":[{"mesh":0,"name":"Mise terrain"}],
            "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1,"TEXCOORD_0":2},
                "indices":3,"material":0}]}],
            "materials":[$material],
            "buffers":[{"byteLength":${bin.size}}],
            "bufferViews":[$viewJson],
            "accessors":[
                {"bufferView":0,"componentType":5126,"count":$VERTICES,"type":"VEC3",
                "min":[-18,$minH,-18],"max":[18,$maxH,18]},
                {"bufferView":1,"componentType":5126,"count":$VERTICES,"type":"VEC3"},
                {"bufferView":2,"componentType":5126,"count":$VERTICES,"type":"VEC2"},
                {"bufferView":3,"componentType":5123,"count":$count,"type":"SCALAR"}
            ]$textureFields}""".trimIndent()
        val utf8 = json.toByteArray(Charsets.UTF_8)
        val jsonLength = (utf8.size+3) and -4
        val total = 12+8+jsonLength+8+bin.size
        return ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x46546C67)
            putInt(2)
            putInt(total)
            putInt(jsonLength); putInt(0x4E4F534A); put(utf8)
            repeat(jsonLength-utf8.size) { put(' '.code.toByte()) }
            putInt(bin.size); putInt(0x004E4942); put(bin)
        }.array()
    }
}
