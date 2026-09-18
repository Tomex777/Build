package com.night.endless.engine.render

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SphereMesh(stacks: Int = 48, slices: Int = 72) {
    val vertices: FloatBuffer
    val indices: ShortBuffer
    val indexCount: Int

    init {
        val data = ArrayList<Float>((stacks + 1) * (slices + 1) * 8)
        for (stack in 0..stacks) {
            val v = stack.toDouble() / stacks
            val phi = PI * (v - 0.5)
            val cp = cos(phi).toFloat()
            val sp = sin(phi).toFloat()

            for (slice in 0..slices) {
                val u = slice.toDouble() / slices
                val theta = 2.0 * PI * u
                val x = (cp * cos(theta)).toFloat()
                val y = sp
                val z = (cp * sin(theta)).toFloat()

                data += x; data += y; data += z
                data += x; data += y; data += z
                data += (1.0 - u).toFloat()
                data += (1.0 - v).toFloat()
            }
        }

        val idx = ArrayList<Short>(stacks * slices * 6)
        val row = slices + 1
        for (stack in 0 until stacks) {
            for (slice in 0 until slices) {
                val a = (stack * row + slice).toShort()
                val b = ((stack + 1) * row + slice).toShort()
                val c = (stack * row + slice + 1).toShort()
                val d = ((stack + 1) * row + slice + 1).toShort()
                idx += a; idx += b; idx += c
                idx += c; idx += b; idx += d
            }
        }

        vertices = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                data.forEach(::put)
                position(0)
            }

        indices = ByteBuffer.allocateDirect(idx.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                idx.forEach(::put)
                position(0)
            }

        indexCount = idx.size
    }
}
