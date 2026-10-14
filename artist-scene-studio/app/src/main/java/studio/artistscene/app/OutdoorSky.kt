package studio.artistscene.app

import com.google.android.filament.Engine
import com.google.android.filament.Texture
import java.nio.ByteBuffer
import kotlin.math.sqrt

/** Direction-based cubemap: the horizon stays in the world while the camera orbits. */
internal fun createOutdoorSky(engine: Engine): Texture {
    val size = 64
    val faceBytes = size * size * 4
    val pixels = ByteBuffer.allocateDirect(faceBytes * 6)
    for (face in 0 until 6) {
        for (y in 0 until size) for (x in 0 until size) {
            val u = 2f * (x + 0.5f) / size - 1f
            val v = 2f * (y + 0.5f) / size - 1f
            val elevation = when (face) {
                2 -> 1f
                3 -> -1f
                else -> -v
            } / sqrt(1f + u * u + v * v)
            val t = sqrt(elevation.coerceIn(0f, 1f))
            val horizon = floatArrayOf(0.72f, 0.83f, 0.90f)
            val zenith = floatArrayOf(0.16f, 0.40f, 0.70f)
            for (channel in 0..2) {
                val value = if (elevation >= 0f) {
                    horizon[channel] + (zenith[channel] - horizon[channel]) * t
                } else {
                    horizon[channel] + (0.36f - horizon[channel]) * (-elevation).coerceIn(0f, 1f)
                }
                pixels.put((value * 255f).toInt().coerceIn(0, 255).toByte())
            }
            pixels.put(255.toByte())
        }
    }
    pixels.flip()
    return Texture.Builder().width(size).height(size).levels(1)
        .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
        .format(Texture.InternalFormat.SRGB8_A8).build(engine).also { texture ->
            texture.setImage(engine, 0,
                Texture.PixelBufferDescriptor(pixels, Texture.Format.RGBA, Texture.Type.UBYTE),
                IntArray(6) { it * faceBytes })
        }
}
