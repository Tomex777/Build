package com.night.endless.engine.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Observer-local apparent direction of Earth from a lunar landing site.
 *
 * Site latitude/longitude are selenographic degrees with east-positive
 * longitude. Ground +X = east, +Z = north, +Y = up, matching the Tycho
 * spacecraft DEM. This is geometry, NOT a time-dependent JPL ephemeris:
 * sub-Earth latitude/longitude are the mean (0°, 0°), ignoring libration,
 * lunar nutation and the varying Earth-Moon separation.
 */
internal object MoonEarthSky {
    private const val RADIANS = PI / 180.0
    private const val LUNAR_RADIUS_KM = 1737.4
    private const val MEAN_EARTH_DISTANCE_KM = 384400.0

    val tychoDirection: FloatArray by lazy {
        earthDirectionAt(
            observerLatitudeDegrees = -43.31,
            observerLongitudeEastDegrees = 348.64
        )
    }

    fun earthDirectionAt(
        observerLatitudeDegrees: Double,
        observerLongitudeEastDegrees: Double,
        subEarthLatitudeDegrees: Double = 0.0,
        subEarthLongitudeEastDegrees: Double = 0.0,
        lunarRadiusKm: Double = LUNAR_RADIUS_KM,
        earthDistanceKm: Double = MEAN_EARTH_DISTANCE_KM
    ): FloatArray {
        require(observerLatitudeDegrees.isFinite() && observerLatitudeDegrees in -90.0..90.0)
        require(subEarthLatitudeDegrees.isFinite() && subEarthLatitudeDegrees in -90.0..90.0)
        require(observerLongitudeEastDegrees.isFinite() && subEarthLongitudeEastDegrees.isFinite())
        require(lunarRadiusKm > 0.0 && earthDistanceKm > lunarRadiusKm)

        val phi = observerLatitudeDegrees * RADIANS
        val delta = subEarthLatitudeDegrees * RADIANS
        val dLon = (subEarthLongitudeEastDegrees - observerLongitudeEastDegrees) * RADIANS
        val east = cos(delta) * sin(dLon)
        val north = cos(phi) * sin(delta) - sin(phi) * cos(delta) * cos(dLon)
        // The finite observer offset matters for an actual horizon:
        // at the lunar limb it shifts Earth's apparent elevation ~0.26°.
        val up = sin(phi) * sin(delta) + cos(phi) * cos(delta) * cos(dLon) -
            lunarRadiusKm / earthDistanceKm
        val length = sqrt(east * east + up * up + north * north)
        require(length.isFinite() && length > 0.0)
        return floatArrayOf(
            (east / length).toFloat(),
            (up / length).toFloat(),
            (north / length).toFloat()
        )
    }
}
