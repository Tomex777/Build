package com.night.echospace.audio

import android.content.res.AssetManager
import com.night.echospace.model.SoundKind

/** CC0 field recordings prepared at build time as signed 16-bit 22.05kHz mono samples. */
object RecordedSounds {
    fun load(assets: AssetManager): Map<SoundKind, ShortArray> {
        return SoundKind.entries.mapNotNull { kind ->
            val name = "audio/${kind.name.lowercase()}.pcm"
            val bytes = try { assets.open(name).use { it.readBytes() } } catch (_: Exception) { null }
                ?: return@mapNotNull null
            if (bytes.size < 8000 || bytes.size % 2 != 0) return@mapNotNull null
            val samples=ShortArray(bytes.size/2)
            for (i in samples.indices) {
                val low=bytes[2*i].toInt() and 255
                val high=bytes[2*i+1].toInt()
                samples[i]=((high shl 8) or low).toShort()
            }
            // This runs once, off the UI thread, not during real-time mixing.
            kind to PcmLeveler.normalize(samples)
        }.toMap()
    }
    fun hrtf(assets: AssetManager): MeasuredHrtf? = try {
        assets.open("kemar_44100.hrir").use { MeasuredHrtf.read(it) }
    } catch (_: Exception) { null }
}
