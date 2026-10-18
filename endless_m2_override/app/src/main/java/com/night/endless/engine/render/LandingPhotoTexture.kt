package com.night.endless.engine.render

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils

/**
 * Optional, baked-in georeferenced imagery from NASA Trek.
 * If the offline NASA WMTS retrieval fails, terrain is still fully usable:
 * the surface shader falls back to calibrated procedural regolith.
 */
internal object LandingPhotoTexture {
    fun load(context: Context, body: String): Int {
        require(body == "mars" || body == "moon")
        val id = context.resources.getIdentifier(
            "${body}_trek_landing", "drawable", context.packageName
        )
        if (id == 0) return 0
        val bitmap = BitmapFactory.decodeResource(
            context.resources, id, BitmapFactory.Options().apply { inScaled = false }
        ) ?: return 0
        val handles = IntArray(1)
        GLES30.glGenTextures(1, handles, 0)
        val texture = handles[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE
        )
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        bitmap.recycle()
        return texture
    }

    fun bind(program: Int, texture: Int) {
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(program, "uHasLandingAlbedo"),
            if (texture != 0) 1 else 0
        )
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uLandingAlbedo"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
    }
}
