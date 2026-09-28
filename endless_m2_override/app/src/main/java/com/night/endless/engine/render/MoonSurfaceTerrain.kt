package com.night.endless.engine.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

class MoonSurfaceTerrain {
    private val program: Int
    private val vertices: FloatBuffer
    private val indices: ShortBuffer
    private val indexCount: Int

    init {
        val side = 161
        val step = 0.17f
        val data = FloatArray(side * side * 9)
        var p = 0
        for (zi in 0 until side) {
            val z = (zi - (side - 1) / 2) * step
            for (xi in 0 until side) {
                val x = (xi - (side - 1) / 2) * step
                val h = heightAt(x.toDouble(), z.toDouble()).toFloat()
                val nx = -(heightAt(x + 0.015, z.toDouble()) - heightAt(x - 0.015, z.toDouble())).toFloat()
                val nz = -(heightAt(x.toDouble(), z + 0.015) - heightAt(x.toDouble(), z - 0.015)).toFloat()
                val ny = 0.027f
                val length = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
                val dust = ((h + 0.36f) / 0.68f).coerceIn(0f, 1f)
                val mottling =
                    0.028f * sin(x * 6.7f + z * 1.9f) +
                        0.018f * cos(z * 8.1f - x * 2.6f)
                val base = (0.34f + dust * 0.18f + mottling).coerceIn(0.24f, 0.62f)
                data[p++] = x; data[p++] = h; data[p++] = z
                data[p++] = nx / length; data[p++] = ny / length; data[p++] = nz / length
                data[p++] = base * 0.96f; data[p++] = base * 0.97f; data[p++] = base
            }
        }

        val indexData = ShortArray((side - 1) * (side - 1) * 6)
        var i = 0
        for (z in 0 until side - 1) for (x in 0 until side - 1) {
            val a = z * side + x; val b = a + 1; val c = a + side; val d = c + 1
            indexData[i++] = a.toShort(); indexData[i++] = c.toShort(); indexData[i++] = b.toShort()
            indexData[i++] = b.toShort(); indexData[i++] = c.toShort(); indexData[i++] = d.toShort()
        }

        vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(data); position(0) }
        indices = ByteBuffer.allocateDirect(indexData.size * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().apply { put(indexData); position(0) }
        indexCount = indexData.size
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
        fun heightAt(x: Double, z: Double): Double {
            var h =
                0.042 * sin(x * 0.24) * cos(z * 0.19) +
                    0.020 * sin(x * 1.42 + z * 0.36) * cos(z * 1.08) +
                    0.010 * sin(x * 4.7 + z * 1.3) * cos(z * 4.1 - x * 0.8) +
                    0.005 * sin(x * 9.2 - z * 6.4)
            h += crater(x, z, -2.8, 1.7, 1.52, 0.17, 0.086)
            h += crater(x, z, 2.3, -2.1, 0.92, 0.125, 0.068)
            h += crater(x, z, 1.95, -1.28, 0.54, 0.078, 0.046)
            h += crater(x, z, 0.72, -1.52, 0.29, 0.050, 0.028)
            h += crater(x, z, 3.9, 2.8, 0.58, 0.080, 0.044)
            h += crater(x, z, -0.4, -4.0, 0.44, 0.056, 0.033)
            h += crater(x, z, 0.7, 0.6, 0.33, 0.044, 0.026)
            return h
        }

        private fun crater(x: Double, z: Double, cx: Double, cz: Double, radius: Double, depth: Double, rim: Double): Double {
            val dx = x - cx
            val dz = z - cz
            val d = sqrt(dx * dx + dz * dz)
            val bowl = -depth * exp(-(d * d) / (radius * radius * 0.58))
            val rimWidth = radius * 0.18
            val ring = rim * exp(-((d - radius) * (d - radius)) / (rimWidth * rimWidth))
            return bowl + ring
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
    float direct = max(dot(normalize(vNormal), normalize(uLight)), 0.0);
    float lighting = 0.10 + 0.90 * direct;
    lighting = max(lighting, 0.10 + 0.08 * max(normalize(vNormal).y, 0.0));
    float horizon = 1.0 - 0.14 * smoothstep(8.0, 18.0, vDistance);
    fragColor = vec4(vColor * lighting * horizon, 1.0);
}
"""
    }
}
