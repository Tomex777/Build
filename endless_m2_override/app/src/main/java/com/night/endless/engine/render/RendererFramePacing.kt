package com.night.endless.engine.render

/**
 * Vsync request policy for the full-resolution GLES solar renderer.
 * Only apply the low-rate path while an opaque cosmic-scale scene covers it.
 * This changes CPU/GPU work scheduling, never texture size or visual quality.
 */
internal object RendererFramePacing {
    const val OCCLUDED_INTERVAL_NANOS = 200_000_000L // 5Hz hidden render

    fun shouldRequest(
        sceneOccluded: Boolean, lastRequestNanos: Long, frameTimeNanos: Long
    ): Boolean {
        if (!sceneOccluded) return true
        if (lastRequestNanos <= 0L) return true
        if (frameTimeNanos <= lastRequestNanos) return false
        return frameTimeNanos - lastRequestNanos >= OCCLUDED_INTERVAL_NANOS
    }
}
