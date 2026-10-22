package com.night.endless.engine.render

/**
 * Bounds CPU/native-memory allocations for visual orbit guide updates.
 *
 * Lunar nodal regression makes a forever-static 5.145-degree inclined Moon
 * orbit guide misleading after months of accelerated time. Rebuild guides
 * only every 14 simulated days (or following a restored backwards clock),
 * rather than on every 60/90/120Hz GLES frame. At 100000x speed a refresh
 * happens roughly every 12 seconds, not 60x per second.
 *
 * A year-scale or even one-day guide discrepancy remains an approximation:
 * this is a visual explorer, not a precision mission-navigation ephemeris.
 */
internal object OrbitGuideRefresh {
    const val INTERVAL_MILLIS = 14L * 86_400_000L

    fun shouldRefresh(nowMillis: Long, builtAtMillis: Long): Boolean {
        if (builtAtMillis == Long.MIN_VALUE) return true
        // Subtract only in a direction that cannot change sign under normal
        // dated simulation times. Handle extreme clock restores defensively.
        val delta = if (nowMillis >= builtAtMillis) nowMillis - builtAtMillis
                    else builtAtMillis - nowMillis
        return delta < 0 || delta >= INTERVAL_MILLIS
    }
}
