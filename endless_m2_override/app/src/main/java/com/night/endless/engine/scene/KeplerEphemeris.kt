package com.night.endless.engine.scene

import com.night.endless.engine.math.Vec3d
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * JPL Solar System Dynamics approximate J2000 mean-ecliptic elements (Table 1,
 * 1800-2050). These are low-precision positions, NOT Horizons/SPICE ephemerides.
 *
 * At display scale the semi-major axes are compressed independently to keep
 * planets tappable. This changes visual distances, but not Keplerian phase,
 * eccentricity, or inclination.
 * https://ssd.jpl.nasa.gov/planets/approx_pos.html
 */
object KeplerEphemeris {
    private data class Elements(val base: DoubleArray, val rate: DoubleArray)
    private val elements = mapOf(
        "mercury" to Elements(
            doubleArrayOf(.38709927, .20563593, 7.00497902, 252.25032350, 77.45779628, 48.33076593),
            doubleArrayOf(.00000037, .00001906, -.00594749, 149472.67411175, .16047689, -.12534081)),
        "venus" to Elements(
            doubleArrayOf(.72333566, .00677672, 3.39467605, 181.97909950, 131.60246718, 76.67984255),
            doubleArrayOf(.00000390, -.00004107, -.00078890, 58517.81538729, .00268329, -.27769418)),
        // JPL tabulates Earth-Moon barycentre, rather than geocentric Earth.
        "earth" to Elements(
            doubleArrayOf(1.00000261, .01671123, -.00001531, 100.46457166, 102.93768193, 0.0),
            doubleArrayOf(.00000562, -.00004392, -.01294668, 35999.37244981, .32327364, 0.0)),
        "mars" to Elements(
            doubleArrayOf(1.52371034, .09339410, 1.84969142, -4.55343205, -23.94362959, 49.55953891),
            doubleArrayOf(.00001847, .00007882, -.00813131, 19140.30268499, .44441088, -.29257343)),
        "jupiter" to Elements(
            doubleArrayOf(5.20288700, .04838624, 1.30439695, 34.39644051, 14.72847983, 100.47390909),
            doubleArrayOf(-.00011607, -.00013253, -.00183714, 3034.74612775, .21252668, .20469106)),
        "saturn" to Elements(
            doubleArrayOf(9.53667594, .05386179, 2.48599187, 49.95424423, 92.59887831, 113.66242448),
            doubleArrayOf(-.00125060, -.00050991, .00193609, 1222.49362201, -.41897216, -.28867794)),
        "uranus" to Elements(
            doubleArrayOf(19.18916464, .04725744, .77263783, 313.23810451, 170.95427630, 74.01692503),
            doubleArrayOf(-.00196176, -.00004397, -.00242939, 428.48202785, .40805281, .04240589)),
        "neptune" to Elements(
            doubleArrayOf(30.06992276, .00859048, 1.77004347, -55.12002969, 44.96476227, 131.78422574),
            doubleArrayOf(.00026291, .00005105, .00035372, 218.45945325, -.32241464, -.00508664))
    )

    /** Returns a position with ecliptic north mapped to the renderer's +Y axis. */
    fun position(id: String, utcMillis: Long, displaySemiMajor: Double): Vec3d? {
        val raw = elements[id] ?: return null
        val centuries = ((utcMillis / 86_400_000.0 + 2440587.5) - 2451545.0) / 36525.0
        val p = DoubleArray(6) { raw.base[it] + raw.rate[it] * centuries }
        val a = p[0]
        val e = p[1]
        val i = Math.toRadians(p[2])
        val peri = Math.toRadians(p[4] - p[5])
        val node = Math.toRadians(p[5])
        val mean = Math.toRadians(((p[3] - p[4]) % 360.0 + 540.0) % 360.0 - 180.0)
        val anomaly = solveEccentricAnomaly(mean, e)
        val orbitX = a * (cos(anomaly) - e)
        val orbitY = a * sqrt(1.0 - e * e) * sin(anomaly)
        val co = cos(node); val so = sin(node)
        val cw = cos(peri); val sw = sin(peri)
        val ci = cos(i); val si = sin(i)
        val eclipticX = (cw * co - sw * so * ci) * orbitX + (-sw * co - cw * so * ci) * orbitY
        val eclipticY = (cw * so + sw * co * ci) * orbitX + (-sw * so + cw * co * ci) * orbitY
        val eclipticZ = (sw * si) * orbitX + (cw * si) * orbitY
        val sceneUnitsPerAu = displaySemiMajor / a
        return Vec3d(
            eclipticX * sceneUnitsPerAu,
            eclipticZ * sceneUnitsPerAu,
            eclipticY * sceneUnitsPerAu
        )
    }

    fun solveEccentricAnomaly(meanRad: Double, eccentricity: Double): Double {
        require(meanRad.isFinite() && eccentricity in 0.0..<1.0)
        var anomaly = meanRad
        repeat(12) {
            val delta = (anomaly - eccentricity * sin(anomaly) - meanRad) /
                (1.0 - eccentricity * cos(anomaly))
            anomaly -= delta
            if (kotlin.math.abs(delta) < 1e-13) return anomaly
        }
        return anomaly
    }

    /** Evaluate a sampled orbit using the same calendar time as the live planets. */
    fun orbitPoints(id: String, utcMillis: Long, displaySemiMajor: Double, segments: Int): FloatArray? {
        val raw = elements[id] ?: return null
        require(segments > 2)
        val periodDays = 36525.0 * 360.0 / kotlin.math.abs(raw.rate[3])
        val samples = FloatArray(segments * 3)
        for (point in 0 until segments) {
            val t = utcMillis + (point * periodDays * 86_400_000.0 / segments).toLong()
            val pos = position(id, t, displaySemiMajor) ?: return null
            samples[point * 3] = pos.x.toFloat()
            samples[point * 3 + 1] = pos.y.toFloat()
            samples[point * 3 + 2] = pos.z.toFloat()
        }
        return samples
    }
}
