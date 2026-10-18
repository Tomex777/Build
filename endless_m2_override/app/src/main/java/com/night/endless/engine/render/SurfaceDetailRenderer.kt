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
    private val lightX: Double,
    private val lightY: Double,
    private val lightZ: Double,
    // Optional coarse-field normal calculator must precede elevation.
    // Existing Mars/Moon constructors intentionally use trailing lambdas.
    private val coarseNormalHeight: ((Double, Double) -> Double)? = null,
    private val elevation: (Double, Double) -> Double
) {
    private var midAnchorX = Double.NaN
    private var midAnchorZ = Double.NaN
    private var midVertices: FloatBuffer? = null
    private var midIndices: ShortBuffer? = null
    private var midCount = 0

    val midfieldAnchorX: Float get() = midAnchorX.toFloat()
    val midfieldAnchorZ: Float get() = midAnchorZ.toFloat()
    val foregroundAnchorX: Float get() = anchorX.toFloat()
    val foregroundAnchorZ: Float get() = anchorZ.toFloat()

    private var anchorX = Double.NaN
    private var anchorZ = Double.NaN
    private var patchVertices: FloatBuffer? = null
    private var patchIndices: ShortBuffer? = null
    private var patchCount = 0
    private var rockVertices: FloatBuffer? = null
    private var rockCount = 0
    private var shadowVertices: FloatBuffer? = null
    private var shadowCount = 0

    // Reuse native buffers on the GL thread instead of waiting for direct
    // buffer cleaners after every ~3m walking-patch rebuild.
    private fun floats(data: FloatArray, previous: FloatBuffer?): FloatBuffer {
        val buffer = previous?.takeIf { it.capacity() >= data.size }
            ?: ByteBuffer.allocateDirect(data.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.clear()
        buffer.put(data)
        buffer.limit(data.size)
        buffer.position(0)
        return buffer
    }

    private fun shorts(data: ShortArray, previous: ShortBuffer?): ShortBuffer {
        val buffer = previous?.takeIf { it.capacity() >= data.size }
            ?: ByteBuffer.allocateDirect(data.size * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer()
        buffer.clear()
        buffer.put(data)
        buffer.limit(data.size)
        buffer.position(0)
        return buffer
    }

    fun prepare(centerX: Double, centerZ: Double) {
        if (!midAnchorX.isFinite() ||
            hypot(centerX - midAnchorX, centerZ - midAnchorZ) >= 0.028) {
            val mid = SurfaceMidfieldMesh.build(centerX, centerZ, elevation, coarseNormalHeight)
            midVertices = floats(mid.vertices, midVertices)
            midIndices = shorts(mid.indices, midIndices)
            midCount = mid.indices.size
            midAnchorX = centerX
            midAnchorZ = centerZ
        }
        update(centerX, centerZ)
    }

    private fun update(centerX: Double, centerZ: Double) {
        if (anchorX.isFinite() && hypot(centerX - anchorX, centerZ - anchorZ) < 0.0011) return
        val patch = SurfaceDetailMesh.buildPatch(centerX, centerZ, elevation)
        val actualSurface: (Double, Double) -> Double = { x, z ->
            SurfaceDetailMesh.surfaceHeightAt(x, z, centerX, centerZ, elevation)
        }
        // The rock bases and their shadows sample the identical ground
        // height field as the displayed mesh, not the unrefined DEM.
        val rocks = SurfaceDetailMesh.buildRocks(centerX, centerZ, actualSurface)
        patchVertices = floats(patch.vertices, patchVertices)
        patchIndices = shorts(patch.indices, patchIndices)
        patchCount = patch.indices.size
        rockVertices = floats(rocks.vertices, rockVertices)
        rockCount = rocks.vertices.size / SurfaceDetailMesh.STRIDE
        val contact = SurfaceRockShadows.build(
            rocks, actualSurface, lightX, lightY, lightZ
        )
        shadowVertices = floats(contact, shadowVertices)
        shadowCount = contact.size / SurfaceDetailMesh.STRIDE
        anchorX = centerX
        anchorZ = centerZ
    }

    fun draw(centerX: Double, centerZ: Double, program: Int) {
        prepare(centerX, centerZ)

        val vertices = patchVertices ?: return
        val indices = patchIndices ?: return
        val patchLocation = GLES30.glGetUniformLocation(program, "uPatchType")
        // Replacement meshes are clipped to disjoint regions in the shader.
        // Keep their true depth: pulling terrain forward with polygon offset
        // can hide the 1.2cm-raised contact shadows on grazing slopes.
        // The middle ring is grounded in the same measured DEM as the far
        // landscape and fades its synthetic subpixel geology at both joins.
        midVertices?.let { middle ->
            midIndices?.let { midIndex ->
                GLES30.glUniform1i(patchLocation, 4)
                bindAttributes(middle)
                midIndex.position(0)
                GLES30.glDrawElements(
                    GLES30.GL_TRIANGLES, midCount,
                    GLES30.GL_UNSIGNED_SHORT, midIndex
                )
                unbindAttributes()
            }
        }

        GLES30.glUniform1i(patchLocation, 1)
        bindAttributes(vertices)
        indices.position(0)
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES, patchCount, GLES30.GL_UNSIGNED_SHORT, indices
        )
        unbindAttributes()

        shadowVertices?.takeIf { shadowCount > 0 }?.let {
            GLES30.glUniform1i(patchLocation, 3)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glDepthMask(false)
            GLES30.glDisable(GLES30.GL_CULL_FACE)
            bindAttributes(it)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, shadowCount)
            unbindAttributes()
            GLES30.glDepthMask(true)
            GLES30.glEnable(GLES30.GL_CULL_FACE)
            GLES30.glDisable(GLES30.GL_BLEND)
        }

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
