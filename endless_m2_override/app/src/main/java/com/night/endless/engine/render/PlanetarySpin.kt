package com.night.endless.engine.render

import kotlin.math.abs

/**
 * Convert sidereal rotation periods to the body's own right-handed polar frame.
 *
 * IAU/planetary fact sheets often report Venus/Uranus rotation periods as
 * NEGATIVE to indicate apparent retrograde spin about ecliptic north. The
 * renderer already tilts each body's spin axis by its physical obliquity:
 * Venus 177.36 degrees, Uranus 97.77 degrees. A second negative rotation
 * around that *already-flipped* local axis makes them prograde in world
 * coordinates. Interpret period signs as metadata; the rotation magnitude
 * advances around the correctly tilted local north axis.
 *
 * Return a bounded angle to avoid Float precision loss when simulating
 * years of rotation at accelerated clock speeds. Epoch phase is illustrative:
 * our map texture zero-longitudes are not physically solved IAU prime
 * meridians (W0), and this is not a Horizons/SPICE orientation model.
 */
internal object PlanetarySpin {
    fun angleDegrees(elapsedHours: Double, signedSiderealPeriodHours: Double): Float {
        if (!elapsedHours.isFinite() || !signedSiderealPeriodHours.isFinite()) return 0f
        val period = abs(signedSiderealPeriodHours)
        if (period < 1e-9) return 0f
        return ((elapsedHours % period) / period * 360.0).toFloat()
    }
}
