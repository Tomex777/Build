package com.night.endless.engine.render

/**
 * Polar/equatorial radius ratios of the four giant planets. Their apparent
 * flattening is visible from orbit, particularly on Jupiter and Saturn.
 *
 * The app's body.radius is a presentation-scale mean radius. Preserve its
 * volume-equivalent radius by stretching x/z by ratio^(-1/3) and compressing
 * y by ratio^(2/3); do not change physical orbital distances or collisions.
 * NASA Planetary Fact Sheet equatorial/polar radii, in kilometres.
 */
internal object PlanetShape {
    fun polarRatio(id: String): Float = when (id) {
        "jupiter" -> (66854.0 / 71492.0).toFloat()
        "saturn" -> (54364.0 / 60268.0).toFloat()
        "uranus" -> (24973.0 / 25559.0).toFloat()
        "neptune" -> (24341.0 / 24764.0).toFloat()
        else -> 1f
    }

    fun equatorialScale(id: String): Float =
        (1.0 / Math.cbrt(polarRatio(id).toDouble())).toFloat()

    fun polarScale(id: String): Float = equatorialScale(id) * polarRatio(id)
}
