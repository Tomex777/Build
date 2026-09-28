package com.night.endless.engine.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class MoonSurfaceTerrain {
    private val program: Int
    private val vertices: FloatBuffer
    private val indices: ShortBuffer
    private val indexCount: Int

    init {
        val mesh = MoonSurfaceMeshGenerator.build()
        vertices = ByteBuffer.allocateDirect(mesh.vertices.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(mesh.vertices); position(0) }
        indices = ByteBuffer.allocateDirect(mesh.indices.size * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().apply { put(mesh.indices); position(0) }
        indexCount = mesh.indices.size
        program = createProgram(VS, FS)
    }

    fun draw(vp: FloatArray, offsetX: Float, offsetZ: Float) {
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uVp"), 1, false, vp, 0)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "uOffset"), offsetX, offsetZ)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uLight"), -0.74f, 0.38f, 0.31f)
        GLES30.glDisable(GLES30.GL_CULL_FACE); GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        vertices.position(0); GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 36, vertices)
        vertices.position(3); GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 36, vertices)
        vertices.position(6); GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, 36, vertices)
        vertices.position(0); indices.position(0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, indices)
        GLES30.glDisableVertexAttribArray(0); GLES30.glDisableVertexAttribArray(1); GLES30.glDisableVertexAttribArray(2)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
    }

    companion object {
        fun heightAt(x: Double, z: Double): Double = MoonSurfaceMeshGenerator.heightAt(x, z)

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
