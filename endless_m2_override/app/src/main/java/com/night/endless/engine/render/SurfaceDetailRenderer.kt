package com.night.endless.engine.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.hypot

/**
 * Draws a moving near-field meter-resolution patch, then 3D stones.
 * Source terrain below this LOD is still the original MOLA/LOLA mesh.
 * Caller owns the active SurfaceMaterialShaders program and its uniforms.
 */
internal class SurfaceDetailRenderer(
    private val elevation: (Double, Double) -> Double
) {
    private var anchorX = Double.NaN
    private var anchorZ = Double.NaN
    private var patchVertices: FloatBuffer? = null
    private var patchIndices: ShortBuffer? = null
    private var patchCount = 0
    private var rockVertices: FloatBuffer? = null
    private var rockCount = 0

    private fun floats(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(data); position(0) }

    private fun shorts(data: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(data.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
            .apply { put(data); position(0) }

    private fun update(centerX: Double, centerZ: Double) {
        if (anchorX.isFinite() && hypot(centerX - anchorX, centerZ - anchorZ) < 0.0011) return
        val patch = SurfaceDetailMesh.buildPatch(centerX, centerZ, elevation)
        val rocks = SurfaceDetailMesh.buildRocks(centerX, centerZ, elevation)
        patchVertices = floats(patch.vertices)
        patchIndices = shorts(patch.indices)
        patchCount = patch.indices.size
        rockVertices = floats(rocks.vertices)
        rockCount = rocks.vertices.size / SurfaceDetailMesh.STRIDE
        anchorX = centerX
        anchorZ = centerZ
    }

    fun draw(centerX: Double, centerZ: Double, program: Int) {
        update(centerX, centerZ)

        val vertices = patchVertices ?: return
        val indices = patchIndices ?: return
        val patchLocation = GLES30.glGetUniformLocation(program, "uPatchType")
        GLES30.glUniform1i(patchLocation, 1)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(-1.0f, -1.0f)
        bindAttributes(vertices)
        indices.position(0)
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES, patchCount, GLES30.GL_UNSIGNED_SHORT, indices
        )
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        unbindAttributes()

        rockVertices?.takeIf { rockCount > 0 }?.let {
            GLES30.glUniform1i(patchLocation, 2)
            bindAttributes(it)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, rockCount)
            unbindAttributes()
        }
        GLES30.glUniform1i(patchLocation, 0)
    }

    private fun bindAttributes(buffer: FloatBuffer) {
        buffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 36, buffer)
        buffer.position(3)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 36, buffer)
        buffer.position(6)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, 36, buffer)
    }

    private fun unbindAttributes() {
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
    }
}
