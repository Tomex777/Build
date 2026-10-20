package com.night.endless.engine.render

/** Independent renderer wall-clock advance, not GPU frame-count time. */
internal object RendererFrameTiming {
    fun elapsedSeconds(previousNanos: Long, nowNanos: Long): Double {
        if (nowNanos <= previousNanos) return 0.0
        // Cap a single long app background/resume gap to two seconds.
        // During active slow GLES, 0.5-1.5s frames should count in full.
        return ((nowNanos - previousNanos) / 1_000_000_000.0)
            .coerceIn(0.0, 2.0)
    }
}
