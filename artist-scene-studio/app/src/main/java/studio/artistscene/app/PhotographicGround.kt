package studio.artistscene.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import java.nio.ByteBuffer

/**
 * A genuine photo-textured, PBR-lit ground receiver. The base photo is CC0
 * Leafy Grass by Charlotte Baglioni at Poly Haven (NOT generated geometry).
 *
 * Do not use ImageNode (unlit): the real 3D surface needs to accept shadows.
 * Dense baked UV tiles keep the 36-metre ground at natural photographic scale
 * with correct screen-space perspective and shadowing as the camera moves.
 */
internal fun createPhotographicGround(engine: Engine, context: Context): Texture {
    val source = context.assets.open("world/leafy_grass_diff_1k.png").use { stream ->
        requireNotNull(BitmapFactory.decodeStream(stream)) { "Missing CC0 ground photo" }
    }
    val pixels = 2048
    val tileCount = 12
    val tiled = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(tiled)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (row in 0 until tileCount) {
            for (column in 0 until tileCount) {
                val dest = Rect(
                    pixels * column / tileCount,
                    pixels * row / tileCount,
                    pixels * (column + 1) / tileCount,
                    pixels * (row + 1) / tileCount,
                )
                canvas.drawBitmap(source, null, dest, paint)
            }
        }
        val buffer = ByteBuffer.allocateDirect(pixels * pixels * 4)
        tiled.copyPixelsToBuffer(buffer)
        buffer.flip()
        return Texture.Builder()
            .width(pixels).height(pixels).levels(12)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.SRGB8_A8)
            .build(engine).also { texture ->
                texture.setImage(engine, 0,
                    Texture.PixelBufferDescriptor(buffer, Texture.Format.RGBA, Texture.Type.UBYTE))
                texture.generateMipmaps(engine)
            }
    } finally {
        tiled.recycle()
        source.recycle()
    }
}
