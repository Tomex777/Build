package com.night.endless.engine.render

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import java.nio.ByteBuffer

/** Mipmapped GPU micro-material: ~341 KiB total on device, zero APK image bytes. */
internal object SurfaceMicroTexture {
    fun create(context: Context, planet: String): Int {
        require(planet == "mars" || planet == "moon")
        val atlas = SurfaceMicroMaterial.pixels()
        val referenceId = context.resources.getIdentifier(
            planet + "_surface_grit", "drawable", context.packageName
        )
        if (referenceId != 0) {
            // Optional 256x256 photographic grain; high-pass normalized and
            // mirrored at build time. NOT a photo taken at this landing site.
            val bitmap = BitmapFactory.decodeResource(
                context.resources, referenceId,
                BitmapFactory.Options().apply { inScaled = false }
            )
            if (bitmap != null) {
                try {
                    if (bitmap.width == SurfaceMicroMaterial.SIDE &&
                        bitmap.height == SurfaceMicroMaterial.SIDE) {
                        val colors = IntArray(SurfaceMicroMaterial.SIDE * SurfaceMicroMaterial.SIDE)
                        bitmap.getPixels(colors, 0, SurfaceMicroMaterial.SIDE, 0, 0,
                            SurfaceMicroMaterial.SIDE, SurfaceMicroMaterial.SIDE)
                        for (i in colors.indices) {
                            val grain = (colors[i] ushr 16) and 255
                            val original = atlas[i * 4].toInt() and 255
                            atlas[i * 4] = (original + (grain - 128) * .70)
                                .toInt().coerceIn(0, 255).toByte()
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }
        val pixels = ByteBuffer.allocateDirect(atlas.size)
            .apply { put(atlas); position(0) }
        val names = IntArray(1)
        GLES30.glGenTextures(1, names, 0)
        val texture = names[0]
        check(texture != 0) { "Could not allocate regolith micro-material" }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8,
            SurfaceMicroMaterial.SIDE, SurfaceMicroMaterial.SIDE, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels
        )
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return texture
    }

    fun bind(program: Int, texture: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uMicroMaterial"), 1)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }
}
