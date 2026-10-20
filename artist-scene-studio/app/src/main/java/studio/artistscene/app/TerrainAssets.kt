package studio.artistscene.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import studio.artistscene.core.Actor
import studio.artistscene.core.ActorKind
import studio.artistscene.core.AssetReference
import studio.artistscene.core.AssetStorage
import studio.artistscene.core.TerrainMesh
import studio.artistscene.core.TerrainShape
import studio.artistscene.core.TerrainStroke
import studio.artistscene.core.TerrainSculpt
import studio.artistscene.core.GeneratedTerrainReader

/**
 * Real, fully offline 3D geometry, not a flat decal. Stored in app-scoped
 * files under models/, so a saved scene's mountain survives application
 * relaunch and its 3D mesh can cast genuine shadows from the movable sun.
 */
internal class TerrainAssets(private val context: Context) {
    fun preset(shape: TerrainShape): Actor = write(
        shape.title, TerrainMesh.preset(shape), "Generated with Mise terrain controls",
    )

    fun fromHeightmap(uri: Uri, maxHeightMeters: Float = 15f): Actor {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: error("Unable to open the heightmap.")
        val width = bounds.outWidth
        val height = bounds.outHeight
        require(width in 16..8192 && height in 16..8192) {
            "Heightmap dimensions must be between 16 and 8192 pixels."
        }
        val sample = BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, maxOf(width, height) / 512)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, sample)
        } ?: error("Selected image is not a readable grayscale heightmap.")
        val pixels = IntArray(TerrainMesh.VERTICES)
        try {
            val resized = Bitmap.createScaledBitmap(
                decoded, TerrainMesh.CELLS + 1, TerrainMesh.CELLS + 1, true,
            )
            try {
                resized.getPixels(pixels, 0, TerrainMesh.CELLS + 1, 0, 0,
                    TerrainMesh.CELLS + 1, TerrainMesh.CELLS + 1)
            } finally {
                if (resized !== decoded) resized.recycle()
            }
        } finally {
            decoded.recycle()
        }
        return write("My heightmap terrain",
            TerrainMesh.fromGrayscale(pixels, maxHeightMeters),
            "Imported grayscale heightmap",
        )
    }

    /**
     * Creates a NEW terrain asset revision while retaining the Actor's identity,
     * placement, selection, and the previous immutable GLB for undo/redo.
     * Generated terrain created in older versions is supported by decoding its
     * actual positions, so no transient heightfield cache is needed to reopen.
     *
     * Caller applies the returned Actor through SceneEditorState's history.
     * This method deliberately does not delete prior files: they may still be
     * referenced by undo steps, a previously saved project, or duplicates.
     */
    fun sculpt(existing: Actor, stroke: TerrainStroke): Actor {
        val asset = requireNotNull(existing.asset) { "Choose a generated terrain actor" }
        require(existing.kind == ActorKind.ENVIRONMENT &&
            asset.assetId.startsWith("terrain.generated.") &&
            asset.storage == AssetStorage.PROJECT_FILE) {
            "Brushes can edit Mise-generated 3D terrain only"
        }
        val folder = File(context.filesDir, "models").canonicalFile
        val original = File(context.filesDir, asset.relativePath).canonicalFile
        require(original.parentFile == folder && original.isFile) {
            "Terrain asset is missing or outside the model library"
        }
        val terrain = GeneratedTerrainReader.decode(original.readBytes())
        val sculpted = TerrainSculpt.apply(terrain.heights, stroke)
        if (terrain.heights.contentEquals(sculpted)) return existing
        val bytes = TerrainMesh.encodeGlb(sculpted, terrain.embeddedPng)
        val revisionPath = saveUniqueTerrain(bytes)
        val revision = (existing.metadata["terrainRevision"]?.toIntOrNull() ?: 0)
            .coerceIn(0, Int.MAX_VALUE - 1) + 1
        return existing.copy(
            asset = asset.copy(
                relativePath = revisionPath,
                byteSize = bytes.size.toLong(),
                checksumSha256 = sha256(bytes),
            ),
            metadata = existing.metadata +
                ("terrainRevision" to revision.toString()) +
                ("terrainLastBrush" to stroke.brush.name),
        )
    }

    private fun saveUniqueTerrain(bytes: ByteArray): String {
        val folder = File(context.filesDir, "models")
        check(folder.exists() || folder.mkdirs()) { "Cannot create terrain model directory" }
        val relative = "models/terrain-${UUID.randomUUID().toString().replace("-", "")}.glb"
        val file = File(context.filesDir, relative)
        try {
            FileOutputStream(file).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
        } catch (error: Exception) {
            file.delete()
            throw error
        }
        return relative
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest
        .getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun write(name: String, heights: FloatArray, provenance: String): Actor {
        val id = UUID.randomUUID().toString().replace("-", "")
        val photo = context.assets.open("world/leafy_grass_diff_1k.png").use { it.readBytes() }
        val bytes = TerrainMesh.encodeGlb(heights, photo)
        val relative = saveUniqueTerrain(bytes)
        return Actor(
            id = "terrain-$id",
            name = name,
            kind = ActorKind.ENVIRONMENT,
            asset = AssetReference(
                assetId = "terrain.generated.$id",
                relativePath = relative,
                format = "glb",
                source = provenance,
                creator = "Mise / Scene artist",
                storage = AssetStorage.PROJECT_FILE,
                byteSize = bytes.size.toLong(),
                checksumSha256 = sha256(bytes),
            ),
            metadata = mapOf("terrainOrigin" to provenance),
        )
    }
}
