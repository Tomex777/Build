package com.night.endless.engine.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Deterministic first landing patch, pending georeferenced elevation tiles. */
class MarsSurfaceTerrain {
    private val program: Int
    private val vertices: FloatBuffer
    private val indices: ShortBuffer
    private val indexCount: Int

    init {
        val side = 121
        val step = 0.22f
        val data = FloatArray(side * side * 9)
        var p = 0
        for (zi in 0 until side) {
            val z = (zi - (side - 1) / 2) * step
            for (xi in 0 until side) {
                val x = (xi - (side - 1) / 2) * step
                val h = heightAt(x.toDouble(), z.toDouble()).toFloat()
                val nx = -(heightAt(x + 0.02, z.toDouble()) - heightAt(x - 0.02, z.toDouble())).toFloat()
                val nz = -(heightAt(x.toDouble(), z + 0.02) - heightAt(x.toDouble(), z - 0.02)).toFloat()
                val ny = 0.04f
                val length = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
                val blend = (((h + 0.40f) / 0.78f).coerceIn(0f, 1f) * 0.72f + 0.14f)
                data[p++] = x; data[p++] = h; data[p++] = z
                data[p++] = nx / length; data[p++] = ny / length; data[p++] = nz / length
                data[p++] = 0.24f + 0.50f * blend; data[p++] = 0.105f + 0.255f * blend
                data[p++] = 0.065f + 0.125f * blend
            }
        }
        val indexData = ShortArray((side - 1) * (side - 1) * 6)
        var i = 0
        for (z in 0 until side - 1) for (x in 0 until side - 1) {
            val a = z * side + x; val b = a + 1; val c = a + side; val d = c + 1
            indexData[i++] = a.toShort(); indexData[i++] = c.toShort(); indexData[i++] = b.toShort()
            indexData[i++] = b.toShort(); indexData[i++] = c.toShort(); indexData[i++] = d.toShort()
        }
        vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }
        indices = ByteBuffer.allocateDirect(indexData.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(indexData); position(0) }
        indexCount = indexData.size
        program = createProgram(VS, FS)
    }

    fun draw(vp: FloatArray, offsetX: Float, offsetZ: Float) {
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uVp"), 1, false, vp, 0)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(program, "uOffset"), offsetX, offsetZ)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uLight"), -0.45f, 0.85f, 0.35f)
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
        fun heightAt(x: Double, z: Double): Double {
            val broad = 0.16 * sin(x * 0.36) * cos(z * 0.29)
            val ridges = 0.09 * sin(x * 1.08 + cos(z * 0.44)) * cos(z * 0.83)
            val ripples = 0.035 * sin(x * 2.6 + z * 1.7) * cos(z * 2.1 - x * 0.8)
            val r = sqrt((x + 2.1) * (x + 2.1) + (z - 1.2) * (z - 1.2))
            val crater = -0.12 * kotlin.math.exp(-((r - 1.15) * (r - 1.15)) / 0.08) +
                0.055 * kotlin.math.exp(-((r - 1.42) * (r - 1.42)) / 0.05)
            return broad + ridges + ripples + crater
        }

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
    float diffuse = 0.48 + 0.52 * max(dot(normalize(vNormal), normalize(uLight)), 0.0);
    float haze = smoothstep(5.0, 15.0, vDistance);
    fragColor = vec4(mix(vColor * diffuse, vec3(0.36, 0.17, 0.10), haze), 1.0);
}
"""
    }
}