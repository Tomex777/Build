package com.night.endless.engine.render

import android.opengl.GLES30
import java.nio.ByteBuffer

/** Mipmapped GPU micro-material: ~341 KiB total on device, zero APK image bytes. */
internal object SurfaceMicroTexture {
    fun create(): Int {
        val pixels = ByteBuffer.allocateDirect(
            SurfaceMicroMaterial.SIDE * SurfaceMicroMaterial.SIDE * SurfaceMicroMaterial.CHANNELS
        ).apply { put(SurfaceMicroMaterial.pixels()); position(0) }
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
