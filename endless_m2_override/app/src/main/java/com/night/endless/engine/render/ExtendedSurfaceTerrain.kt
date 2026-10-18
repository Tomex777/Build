package com.night.endless.engine.render

import android.content.Context
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

internal class ExtendedSurfaceTerrain(private val context: Context, private val bodyId: String) {
    private val program: Int
    private val landingPhotoTexture: Int
    private val microMaterialTexture: Int
    private val vertices: FloatBuffer
    private val indices: ShortBuffer
    private val indexCount: Int
    private val detail = SurfaceDetailRenderer(
        if (bodyId == "titan") -.32 else -.74,
        if (bodyId == "titan") .58 else .38,
        .31,
        coarseNormalHeight = { x, z -> ExtendedSurfaceMesh.heightAt(bodyId, x, z) },
        coarseElevation = { x, z -> ExtendedSurfaceMesh.renderedHeightAt(bodyId, x, z) },
        geologyRelief = { x,z -> ExtendedSurfaceMesh.nearReliefAt(bodyId,x,z) },
        drawStones = bodyId == "mercury" || bodyId == "ceres",
        elevation = { x, z -> ExtendedSurfaceMesh.heightAt(bodyId, x, z) }
    )

    init {
        val mesh = ExtendedSurfaceMesh.build(bodyId)
        vertices = ByteBuffer.allocateDirect(mesh.vertices.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(mesh.vertices); position(0) }
        indices = ByteBuffer.allocateDirect(mesh.indices.size * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().apply { put(mesh.indices); position(0) }
        indexCount = mesh.indices.size
        program = createProgram(SurfaceMaterialShaders.VERTEX, SurfaceMaterialShaders.FRAGMENT)
        landingPhotoTexture = LandingPhotoTexture.load(context, bodyId)
        microMaterialTexture = SurfaceMicroTexture.create(context, bodyId)
        // Prepare the measured coarse-to-mid-to-near geometry while the GL
        // engine initializes, not on the user's first landing frame.
        detail.prepare(0.0, 0.0)
    }

    fun draw(vp: FloatArray, offsetX: Float, offsetZ: Float) {
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uVp"), 1, false, vp, 0)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "uOffset"), offsetX, offsetZ)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uPlanetRadiusUnits"), radiusKm / 3.0f)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uWorld"), worldIndex)
        LandingPhotoTexture.bind(program, landingPhotoTexture)
        SurfaceMicroTexture.bind(program, microMaterialTexture)
        // Initialize/reanchor midfield LOD *before* drawing the coarse
        // mesh, so its masked region always matches the actual replacement.
        detail.prepare(-offsetX.toDouble(), -offsetZ.toDouble())
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(program, "uViewAnchor"),
            detail.foregroundAnchorX, detail.foregroundAnchorZ
        )
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(program, "uMidAnchor"),
            detail.midfieldAnchorX, detail.midfieldAnchorZ
        )
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uPatchType"), 0)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uLight"), lightX, lightY, lightZ)
        // Both local and kilometer-scale meshes have counterclockwise upward faces.
// Cull rear/underside triangles so steep terrain never forms a false ceiling.
        GLES30.glEnable(GLES30.GL_CULL_FACE); GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        vertices.position(0); GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 36, vertices)
        vertices.position(3); GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 36, vertices)
        vertices.position(6); GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, 36, vertices)
        vertices.position(0); indices.position(0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, indices)
        GLES30.glDisableVertexAttribArray(0); GLES30.glDisableVertexAttribArray(1); GLES30.glDisableVertexAttribArray(2)
        detail.draw(-offsetX.toDouble(), -offsetZ.toDouble(), program)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
    }

    private val radiusKm: Float get() = when(bodyId) {
        "mercury" -> 2439.7f
        "ceres" -> 473.0f
        "europa" -> 1560.8f
        "titan" -> 2574.73f
        else -> error("Unsupported surface: $bodyId")
    }
    private val worldIndex: Int get() = when(bodyId) {
        "mercury" -> 2
        "ceres" -> 3
        "europa" -> 4
        "titan" -> 5
        else -> error("Unsupported surface: $bodyId")
    }
    private val lightX: Float get() = if (bodyId == "titan") -.32f else -.74f
    private val lightY: Float get() = if (bodyId == "titan") .58f else .38f
    private val lightZ: Float get() = .31f

    companion object {
        private fun createProgram(vs: String, fs: String): Int {
            fun compile(type: Int, source: String): Int {
                val id = GLES30.glCreateShader(type); GLES30.glShaderSource(id, source); GLES30.glCompileShader(id)
                val status = IntArray(1); GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, status, 0)
                check(status[0] == GLES30.GL_TRUE) { GLES30.glGetShaderInfoLog(id) }
                return id
            }
            val v = compile(GLES30.GL_VERTEX_SHADER, vs); val f = compile(GLES30.GL_FRAGMENT_SHADER, fs)
            return GLES30.glCreateProgram().also { id ->
                GLES30.glAttachShader(id, v); GLES30.glAttachShader(id, f); GLES30.glLinkProgram(id)
                val status = IntArray(1); GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
                check(status[0] == GLES30.GL_TRUE) { GLES30.glGetProgramInfoLog(id) }
                GLES30.glDeleteShader(v); GLES30.glDeleteShader(f)
            }
        }

        private const val VS = """#version 300 es
layout(location=0) in vec3 aPosition;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec3 aColor;
uniform mat4 uVp;
uniform vec2 uOffset;
out vec3 vNormal;
out vec3 vColor;
out float vDistance;
void main() {
    vec3 world = vec3(aPosition.x + uOffset.x, aPosition.y, aPosition.z + uOffset.y);
    gl_Position = uVp * vec4(world, 1.0);
    vNormal = aNormal; vColor = aColor; vDistance = length(world.xz);
}
"""
        private const val FS = """#version 300 es
precision highp float;
in vec3 vNormal;
in vec3 vColor;
in float vDistance;
uniform vec3 uLight;
out vec4 fragColor;
void main() {
    float direct = max(dot(normalize(vNormal), normalize(uLight)), 0.0);
    float lighting = 0.10 + 0.90 * direct;
    lighting = max(lighting, 0.10 + 0.08 * max(normalize(vNormal).y, 0.0));
    float horizon = 1.0 - 0.14 * smoothstep(8.0, 18.0, vDistance);
    fragColor = vec4(vColor * lighting * horizon, 1.0);
}
"""
    }
}
