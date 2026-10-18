package studio.artistscene.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID
import studio.artistscene.core.Actor
import studio.artistscene.core.ActorKind
import studio.artistscene.core.AssetReference
import studio.artistscene.core.AssetStorage
import studio.artistscene.core.TerrainMesh
import studio.artistscene.core.TerrainShape

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

    private fun write(name: String, heights: FloatArray, provenance: String): Actor {
        val id = UUID.randomUUID().toString().replace("-", "")
        val folder = File(context.filesDir, "models")
        check(folder.exists() || folder.mkdirs()) { "Cannot create terrain model directory" }
        val relative = "models/terrain-$id.glb"
        val file = File(context.filesDir, relative)
        val photo = context.assets.open("world/leafy_grass_diff_1k.png").use { it.readBytes() }
        val bytes = TerrainMesh.encodeGlb(heights, photo)
        try {
            file.outputStream().use { output ->
                output.write(bytes)
                output.flush()
                (output as java.io.FileOutputStream).fd.sync()
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
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
            ),
            metadata = mapOf("terrainOrigin" to provenance),
        )
    }
}
