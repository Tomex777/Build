package com.night.endless.engine.scene

import com.night.endless.engine.math.Vec3d
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Low-precision Europa and Titan orbits about their giant-planet parents.
 *
 * The previous trajectory used y = sin(angle * 0.47) * r * 0.013, which
 * never closed after one orbital period. The rendered orbit line was also
 * a flat ecliptic circle even while the satellite wandered above/below it.
 *
 * These moons orbit close to the parent planet's equatorial plane:
 * Europa ~Jupiter's 3.13 degree obliquity; Titan ~Saturn's 26.73 degree
 * obliquity. Rotate a true circular local XZ orbit by the exact same +Z
 * obliquity used by the rendered giant and Saturn ring mesh. The geometry
 * is coherent, periodic and has exactly constant radius. Real minor
 * inclinations, perturbations/precession and instantaneous ephemerides
 * remain illustrative rather than claiming SPICE/Horizons precision.
 */
internal object GiantSatelliteOrbit {
    fun position(radius: Double, angleRad: Double, parentTiltDeg: Double): Vec3d {
        require(radius.isFinite() && radius > 0.0 &&
            angleRad.isFinite() && parentTiltDeg.isFinite())
        val tilt = Math.toRadians(parentTiltDeg)
        val x = radius * cos(angleRad)
        return Vec3d(x * cos(tilt), x * sin(tilt), radius * sin(angleRad))
    }

    fun polyline(radius: Double, parentTiltDeg: Double, segments: Int): FloatArray {
        require(segments >= 8)
        return FloatArray(segments * 3).also { data ->
            for (index in 0 until segments) {
                val p = position(radius, 2.0 * PI * index / segments, parentTiltDeg)
                data[3 * index] = p.x.toFloat()
                data[3 * index + 1] = p.y.toFloat()
                data[3 * index + 2] = p.z.toFloat()
            }
        }
    }
}
