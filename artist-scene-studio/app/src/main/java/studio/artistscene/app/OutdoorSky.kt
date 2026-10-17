package studio.artistscene.app

import com.google.android.filament.Engine
import com.google.android.filament.Texture
import java.nio.ByteBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import studio.artistscene.core.EnvironmentPreset

/**
 * Seamless view-independent procedural sky. The direction is sampled in 3D (not as
 * six separate 2D gradients), keeping the same clouds and horizon while orbiting.
 *
 * This is an original lightweight offline preview atmosphere, not a copied HDRI or
 * a claim of physically accurate image-based lighting. Rendered figures still use
 * Filament's standard directional / indirect lighting and contact shadows.
 */
internal fun createOutdoorSky(engine: Engine, preset: EnvironmentPreset = EnvironmentPreset.DAYLIGHT): Texture {
    val size = 128
    val faceBytes = size * size * 4
    val pixels = ByteBuffer.allocateDirect(faceBytes * 6)
    val zenith = colorChannels(preset.zenithRgb)
    val horizon = colorChannels(preset.horizonRgb)
    val lower = colorChannels(preset.lowerSkyRgb)

    for (face in 0 until 6) {
        for (y in 0 until size) for (x in 0 until size) {
            val u = 2f * (x + 0.5f) / size - 1f
            val v = 2f * (y + 0.5f) / size - 1f
            // Filament cubemap convention: +X, -X, +Y, -Y, +Z, -Z.
            val direction = when (face) {
                0 -> floatArrayOf(1f, -v, -u)
                1 -> floatArrayOf(-1f, -v, u)
                2 -> floatArrayOf(u, 1f, v)
                3 -> floatArrayOf(u, -1f, -v)
                4 -> floatArrayOf(u, -v, 1f)
                else -> floatArrayOf(-u, -v, -1f)
            }
            val inverse = 1f / sqrt(direction.sumOf { (it * it).toDouble() }.toFloat())
            val dx = direction[0] * inverse
            val dy = direction[1] * inverse
            val dz = direction[2] * inverse
            val upper = sqrt(dy.coerceIn(0f, 1f))
            val under = (-dy).coerceIn(0f, 1f)
            val haze = exp(-kotlin.math.abs(dy) * 16f) * 0.075f

            // 3D angular cloud structure fades near the horizon. No cubemap seams.
            val cloudSignal = 0.50f +
                0.21f * sin(dx * 14f + dz * 8f + dy * 5f) *
                    sin(dz * 17f - dx * 6f + dy * 4f) +
                0.13f * sin(dx * 27f + dz * 19f - dy * 7f)
            val cloudCover = smoothstep(0.53f, 0.76f, cloudSignal) *
                smoothstep(-0.03f, 0.23f, dy) * preset.cloudAmount

            // Warm directional sun scatter; only the outdoor presets emit it.
            val sunDot = dx * 0.40f + dy * 0.70f + dz * (-0.59f)
            val scatter = preset.sunStrength * exp((sunDot - 1f) * 44f) * 0.42f
            val sunDisk = if (sunDot > 0.9989f) preset.sunStrength * 0.56f else 0f

            for (channel in 0..2) {
                val base = if (dy >= 0f) {
                    lerp(horizon[channel], zenith[channel], upper)
                } else {
                    lerp(horizon[channel], lower[channel], under)
                }
                val cloudWhite = if (preset == EnvironmentPreset.SUNSET) {
                    floatArrayOf(1f, 0.81f, 0.70f)[channel]
                } else 0.94f
                val cloudy = lerp(base, cloudWhite, cloudCover * 0.70f)
                val value = (cloudy + haze + scatter + sunDisk).coerceIn(0f, 1f)
                pixels.put((value * 255f).toInt().toByte())
            }
            pixels.put(255.toByte())
        }
    }
    pixels.flip()
    return Texture.Builder().width(size).height(size).levels(1)
        .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
        .format(Texture.InternalFormat.SRGB8_A8).build(engine).also { texture ->
            texture.setImage(
                engine, 0,
                Texture.PixelBufferDescriptor(pixels, Texture.Format.RGBA, Texture.Type.UBYTE),
                IntArray(6) { it * faceBytes },
            )
        }
}

private fun smoothstep(start: Float, end: Float, value: Float): Float {
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun lerp(start: Float, end: Float, amount: Float) = start + (end - start) * amount

private fun colorChannels(color: Int): FloatArray =
    floatArrayOf(
        ((color shr 16) and 255) / 255f,
        ((color shr 8) and 255) / 255f,
        (color and 255) / 255f,
    )
