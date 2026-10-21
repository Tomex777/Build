package com.night.endless.engine.render

/** Pure decoding policy for large equirectangular planetary maps. */
internal object PlanetTextureSizing {
    /** Return an Android BitmapFactory power-of-two inSampleSize. */
    fun inSampleSize(width: Int, height: Int, reportedGlMax: Int, lowMemory: Boolean): Int {
        if (width <= 0 || height <= 0) return 1
        val gpuLimit = if (reportedGlMax >= 2048) reportedGlMax else 2048
        val memoryCap = if (lowMemory) 2048 else 4096
        val cap = minOf(gpuLimit, memoryCap)
        var sample = 1
        while (maxOf(width, height) / sample > cap && sample < 1024) sample *= 2
        return sample
    }
}
