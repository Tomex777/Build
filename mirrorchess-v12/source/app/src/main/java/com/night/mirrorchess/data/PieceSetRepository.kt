package com.night.mirrorchess.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.night.mirrorchess.chess.PieceType
import com.night.mirrorchess.chess.Side
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream

/** Private, bounded storage for imported piece art. The cache prevents disk decoding during redraws. */
class PieceSetRepository(context: Context) {
    private val resolver = context.contentResolver
    private val root = File(context.filesDir, "piece-sets").apply { mkdirs() }
    private val bitmapCache = linkedMapOf<String, Bitmap>()
    @Volatile var generation: Int = 0
        private set

    companion object {
        @Volatile private var shared: PieceSetRepository? = null
        fun shared(context: Context): PieceSetRepository = shared ?: synchronized(this) {
            shared ?: PieceSetRepository(context.applicationContext).also { shared = it }
        }
    }

    fun listCustomSets(): List<PieceSet> = root.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
        val metadata = File(dir, "set.properties")
        if (!metadata.isFile) return@mapNotNull null
        val props = runCatching { java.util.Properties().apply { metadata.inputStream().use { load(it) } } }.getOrNull() ?: return@mapNotNull null
        val id = dir.name
        val mappings = PieceSet.standardMapping(id).filterKeys { key -> isUsableSprite(spriteFile(id, key)) }
        PieceSet(id, props.getProperty("name", "Custom set"), props.getProperty("version", "1").toIntOrNull() ?: 1,
            props.getProperty("pixelArt", "false").toBoolean(), builtIn = false, mapping = mappings)
    }?.sortedBy { it.name.lowercase() } ?: emptyList()

    fun createSet(name: String, pixelArt: Boolean = false): PieceSet {
        val clean = cleanName(name)
        val id = "custom-${UUID.randomUUID()}"
        val dir = File(root, id).apply { mkdirs() }
        java.util.Properties().apply {
            setProperty("name", clean)
            setProperty("version", "1")
            setProperty("pixelArt", pixelArt.toString())
        }.store(File(dir, "set.properties").outputStream(), "MirrorChess piece set")
        return PieceSet(id, clean, pixelArt = pixelArt, builtIn = false, mapping = emptyMap())
    }

    fun renameSet(setId: String, name: String): PieceSet {
        val props = loadSetProperties(setId)
        props.setProperty("name", cleanName(name))
        storeSetProperties(setId, props)
        generation++
        return listCustomSets().first { it.id == setId }
    }

    fun duplicateSet(setId: String): PieceSet {
        val source = listCustomSets().firstOrNull { it.id == setId } ?: error("Custom set not found.")
        val copy = createSet("${source.name} copy", source.pixelArt)
        val sourceSprites = File(customSetDir(setId), "sprites")
        if (sourceSprites.isDirectory) {
            sourceSprites.copyRecursively(File(customSetDir(copy.id), "sprites"), overwrite = true)
        }
        val sourceProps = loadSetProperties(setId)
        val copyProps = loadSetProperties(copy.id)
        sourceProps.stringPropertyNames().filter { it.startsWith("transform.") }.forEach { key ->
            copyProps.setProperty(key, sourceProps.getProperty(key))
        }
        storeSetProperties(copy.id, copyProps)
        generation++
        return listCustomSets().first { it.id == copy.id }
    }

    fun deleteSet(setId: String) {
        val dir = customSetDir(setId)
        require(dir.isDirectory) { "Custom set not found." }
        check(dir.deleteRecursively()) { "Custom set could not be deleted." }
        synchronized(bitmapCache) {
            val prefix = "$setId/"
            bitmapCache.keys.filter { it.startsWith(prefix) }.toList().forEach { key ->
                bitmapCache.remove(key)?.takeUnless { it.isRecycled }?.recycle()
            }
        }
        generation++
    }

    fun transformFor(setId: String, key: PieceKey): PieceTransform {
        if (!setId.startsWith("custom-")) return PieceTransform()
        val props = runCatching { loadSetProperties(setId) }.getOrNull() ?: return PieceTransform()
        val prefix = transformPrefix(key)
        return PieceTransform(
            scale = props.getProperty("$prefix.scale", "1").toFloatOrNull() ?: 1f,
            offsetX = props.getProperty("$prefix.x", "0").toFloatOrNull() ?: 0f,
            offsetY = props.getProperty("$prefix.y", "0").toFloatOrNull() ?: 0f,
        ).sanitized()
    }

    fun updateTransform(setId: String, key: PieceKey, transform: PieceTransform) {
        val clean = transform.sanitized()
        val props = loadSetProperties(setId)
        val prefix = transformPrefix(key)
        props.setProperty("$prefix.scale", clean.scale.toString())
        props.setProperty("$prefix.x", clean.offsetX.toString())
        props.setProperty("$prefix.y", clean.offsetY.toString())
        storeSetProperties(setId, props)
        generation++
    }

    fun isPixelArt(setId: String): Boolean {
        val file = File(File(root, setId), "set.properties")
        if (!file.isFile) return false
        return runCatching { java.util.Properties().apply { file.inputStream().use { load(it) } }.getProperty("pixelArt", "false").toBoolean() }.getOrDefault(false)
    }

    fun exportSet(setId: String, output: OutputStream) {
        val set = listCustomSets().firstOrNull { it.id == setId } ?: error("Custom set not found.")
        require(set.isComplete()) { "Add all 12 piece images before exporting this set." }
        ZipOutputStream(output.buffered()).use { zip ->
            val manifest = java.util.Properties().apply {
                setProperty("format", "mirrorchess-piece-set")
                setProperty("version", "1")
                setProperty("name", set.name)
                setProperty("pixelArt", set.pixelArt.toString())
                setProperty("layout", "white-king,white-queen,white-rook,white-bishop,white-knight,white-pawn,black-king,black-queen,black-rook,black-bishop,black-knight,black-pawn")
                PieceKey.all.forEach { key ->
                    val transform = transformFor(setId, key)
                    val prefix = transformPrefix(key)
                    setProperty("$prefix.scale", transform.scale.toString())
                    setProperty("$prefix.x", transform.offsetX.toString())
                    setProperty("$prefix.y", transform.offsetY.toString())
                }
            }
            zip.putNextEntry(ZipEntry("manifest.properties"))
            manifest.store(zip, "MirrorChess portable piece set")
            zip.closeEntry()
            PieceKey.all.forEach { key ->
                val file = spriteFile(setId, key)
                zip.putNextEntry(ZipEntry("sprites/${key.side.name.lowercase()}-${key.type.name.lowercase()}.png"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.finish()
        }
    }

    fun importBundle(input: InputStream): PieceSet {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                require(!entry.name.startsWith('/') && !entry.name.contains("..")) { "Bundle contains an invalid path." }
                require(entry.name == "manifest.properties" || entry.name in PieceKey.all.map { "sprites/${it.side.name.lowercase()}-${it.type.name.lowercase()}.png" }) { "Bundle contains an unexpected file." }
                val data = zip.readBytesLimited(8 * 1024 * 1024)
                total += data.size
                require(total <= 32 * 1024 * 1024) { "Bundle is larger than 32 MB." }
                require(entries.put(entry.name, data) == null) { "Bundle contains duplicate files." }
            }
        }
        val manifestBytes = entries.remove("manifest.properties") ?: error("Bundle manifest is missing.")
        val props = java.util.Properties().apply { manifestBytes.inputStream().use { load(it) } }
        require(props.getProperty("format") == "mirrorchess-piece-set" && props.getProperty("version") == "1") { "Unsupported piece set bundle." }
        val set = createSet(props.getProperty("name", "Imported pieces"), props.getProperty("pixelArt", "false").toBoolean())
        try {
            PieceKey.all.forEach { key ->
                val path = "sprites/${key.side.name.lowercase()}-${key.type.name.lowercase()}.png"
                val bytes = entries[path] ?: error("Bundle is missing ${key.side.name.lowercase()} ${key.type.name.lowercase()}.")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192 && bounds.outWidth.toLong() * bounds.outHeight <= 4_000_000L) { "Bundle contains an invalid or oversized sprite." }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }) ?: error("Bundle sprite is corrupt.")
                try { writeSprite(set.id, key, decoded) } finally { decoded.recycle() }
            }
            PieceKey.all.forEach { key ->
                val prefix = transformPrefix(key)
                if (props.containsKey("$prefix.scale") || props.containsKey("$prefix.x") || props.containsKey("$prefix.y")) {
                    updateTransform(
                        set.id,
                        key,
                        PieceTransform(
                            scale = props.getProperty("$prefix.scale", "1").toFloatOrNull() ?: 1f,
                            offsetX = props.getProperty("$prefix.x", "0").toFloatOrNull() ?: 0f,
                            offsetY = props.getProperty("$prefix.y", "0").toFloatOrNull() ?: 0f,
                        ),
                    )
                }
            }
        } catch (error: Throwable) {
            File(root, set.id).deleteRecursively()
            throw error
        }
        return listCustomSets().first { it.id == set.id }
    }

    private fun InputStream.readBytesLimited(maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            count += read
            require(count <= maxBytes) { "A bundle entry is larger than 8 MB." }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    fun importOne(setId: String, key: PieceKey, uri: Uri) {
        val bitmap = decodeBounded(uri)
        try { writeSprite(setId, key, bitmap) } finally { bitmap.recycle() }
    }

    fun savePixelSprite(setId: String, key: PieceKey, colors: IntArray, width: Int, height: Int) {
        require(width in 16..64 && height == width && colors.size == width * height) { "Pixel canvas must be square and between 16 and 64 pixels." }
        val bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
        try { writeSprite(setId, key, bitmap) } finally { bitmap.recycle() }
    }

    /** Expected sheet layout: 6 columns x 2 rows; white K,Q,R,B,N,P, then black K,Q,R,B,N,P. */
    fun previewSheet(uri: Uri): List<Bitmap> {
        val bitmap = decodeBounded(uri)
        try {
            require(bitmap.width / 6 >= 8 && bitmap.height / 2 >= 8 && bitmap.width % 6 == 0 && bitmap.height % 2 == 0) {
                "Sprite sheet must divide evenly into 6 columns and 2 rows."
            }
            val tileWidth = bitmap.width / 6
            val tileHeight = bitmap.height / 2
            return buildList(12) { repeat(2) { row -> repeat(6) { col -> add(Bitmap.createBitmap(bitmap, col * tileWidth, row * tileHeight, tileWidth, tileHeight)) } } }
        } finally { bitmap.recycle() }
    }

    fun importSheet(setId: String, uri: Uri) {
        val bitmap = decodeBounded(uri)
        try {
            require(bitmap.width / 6 >= 8 && bitmap.height / 2 >= 8 && bitmap.width % 6 == 0 && bitmap.height % 2 == 0) {
                "Sprite sheet must divide evenly into 6 columns and 2 rows."
            }
            val types = listOf(PieceType.KING, PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN)
            Side.entries.forEachIndexed { row, side ->
                types.forEachIndexed { col, type ->
                    val tile = Bitmap.createBitmap(bitmap, col * (bitmap.width / 6), row * (bitmap.height / 2), bitmap.width / 6, bitmap.height / 2)
                    try { writeSprite(setId, PieceKey(side, type), tile) } finally { tile.recycle() }
                }
            }
        } finally { bitmap.recycle() }
    }

    fun bitmapFor(setId: String, key: PieceKey): Bitmap? {
        val file = spriteFile(setId, key)
        if (!file.isFile) return null
        synchronized(bitmapCache) {
            val cacheKey = "$setId/${key.side}/${key.type}"
            bitmapCache.remove(cacheKey)?.takeUnless { it.isRecycled }?.let { existing ->
                bitmapCache[cacheKey] = existing
                return existing
            }
            val loaded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            bitmapCache[cacheKey] = loaded
            while (bitmapCache.size > 24) {
                val eldest = bitmapCache.entries.first()
                bitmapCache.remove(eldest.key)
                if (!eldest.value.isRecycled) eldest.value.recycle()
            }
            return loaded
        }
    }

    private fun decodeBounded(uri: Uri): Bitmap {
        validatePngOrWebp(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("The selected image could not be opened.")
        require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192) { "Image dimensions must be between 1 and 8192 pixels." }
        require(bounds.outWidth.toLong() * bounds.outHeight <= 32_000_000L) { "Image is too large to import safely." }
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
        } ?: error("The selected image is corrupt or unsupported. Choose a PNG or WebP image.")
        require(decoded.width > 0 && decoded.height > 0) { decoded.recycle(); "The selected image has no drawable pixels." }
        return decoded
    }

    /**
     * DocumentsUI can expose shell-pushed Downloads files as application/octet-stream on Android 16.
     * Trust the bytes, not provider MIME metadata, while still accepting only PNG/WebP.
     */
    private fun validatePngOrWebp(uri: Uri) {
        val header = ByteArray(12)
        val count = resolver.openInputStream(uri)?.use { input ->
            var offset = 0
            while (offset < header.size) {
                val read = input.read(header, offset, header.size - offset)
                if (read <= 0) break
                offset += read
            }
            offset
        } ?: error("The selected image could not be opened.")
        val png = count >= 8 &&
            header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() &&
            header[4] == 0x0D.toByte() && header[5] == 0x0A.toByte() && header[6] == 0x1A.toByte() && header[7] == 0x0A.toByte()
        val webp = count >= 12 &&
            String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
        require(png || webp) { "Choose a valid PNG or WebP image." }
    }

    private fun writeSprite(setId: String, key: PieceKey, source: Bitmap) {
        require(File(root, setId).isDirectory) { "Create a custom piece set before importing sprites." }
        val dest = spriteFile(setId, key)
        dest.parentFile?.mkdirs()
        synchronized(bitmapCache) { bitmapCache.remove("$setId/${key.side}/${key.type}")?.takeUnless { it.isRecycled }?.recycle() }
        generation++
        val bounds = alphaBounds(source)
        require(bounds != null) { "The image is fully transparent." }
        val cropped = Bitmap.createBitmap(source, bounds.left, bounds.top, bounds.width(), bounds.height())
        val normalized = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        try {
            val desiredHeight = when (key.type) {
                PieceType.KING -> 224f; PieceType.QUEEN -> 210f; PieceType.ROOK -> 190f
                PieceType.BISHOP -> 204f; PieceType.KNIGHT -> 204f; PieceType.PAWN -> 158f
            }
            val scale = minOf(desiredHeight / cropped.height, 218f / cropped.width)
            val width = (cropped.width * scale).toInt().coerceAtLeast(1)
            val height = (cropped.height * scale).toInt().coerceAtLeast(1)
            val nearest = isPixelArt(setId)
            val scaled = Bitmap.createScaledBitmap(cropped, width, height, !nearest)
            val canvas = android.graphics.Canvas(normalized)
            val left = (256 - width) / 2f
            val top = 236f - height
            val paint = android.graphics.Paint().apply { isAntiAlias = !nearest; isFilterBitmap = !nearest }
            canvas.drawBitmap(scaled, left, top, paint)
            if (scaled !== cropped) scaled.recycle()
            FileOutputStream(dest).use { out -> check(normalized.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Could not save the sprite." } }
        } finally {
            cropped.recycle()
            normalized.recycle()
        }
    }

    private fun alphaBounds(bitmap: Bitmap): android.graphics.Rect? {
        var left = bitmap.width; var top = bitmap.height; var right = -1; var bottom = -1
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in row.indices) if ((row[x] ushr 24) > 24) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return if (right < left || bottom < top) null else android.graphics.Rect(left, top, right + 1, bottom + 1)
    }

    private fun cleanName(name: String): String = name.trim().take(40).ifBlank { "Custom pieces" }

    private fun customSetDir(setId: String): File {
        require(setId.startsWith("custom-") && '/' !in setId && '\\' !in setId) { "Invalid custom set." }
        return File(root, setId)
    }

    private fun loadSetProperties(setId: String): java.util.Properties {
        val file = File(customSetDir(setId), "set.properties")
        require(file.isFile) { "Custom set not found." }
        return java.util.Properties().apply { file.inputStream().use { load(it) } }
    }

    private fun storeSetProperties(setId: String, props: java.util.Properties) {
        val dir = customSetDir(setId)
        require(dir.isDirectory) { "Custom set not found." }
        props.store(File(dir, "set.properties").outputStream(), "MirrorChess piece set")
    }

    private fun transformPrefix(key: PieceKey): String =
        "transform.${key.side.name.lowercase()}.${key.type.name.lowercase()}"

    private fun isUsableSprite(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192
    }

    private fun spriteFile(setId: String, key: PieceKey) = File(File(File(root, setId), "sprites"), "${key.side.name.lowercase()}-${key.type.name.lowercase()}.png")
}
