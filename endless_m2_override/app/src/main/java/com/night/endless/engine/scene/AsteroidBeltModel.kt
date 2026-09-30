package com.night.endless.engine.scene

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class AsteroidOrbit(
    val radius: Double,
    val phaseRad: Double,
    val inclinationRad: Double,
    val verticalPhaseRad: Double,
    val orbitPeriodDays: Double,
    val size: Float
)

/**
 * Deterministic main-belt samples. The belt shares UniverseClock time with the planets;
 * these are orbital samples rather than a static decorative ring.
 */
object AsteroidBeltModel {
    const val DEFAULT_COUNT = 420
    const val INNER_RADIUS = 13.25
    const val OUTER_RADIUS = 16.15

    fun build(count: Int = DEFAULT_COUNT): List<AsteroidOrbit> {
        require(count > 0)
        var seed = 0x5EEDBEEFL

        fun rnd(): Double {
            seed = (seed * 1664525L + 1013904223L) and 0xffffffffL
            return seed.toDouble() / 0xffffffffL.toDouble()
        }

        return List(count) {
            val radius = INNER_RADIUS + (OUTER_RADIUS - INNER_RADIUS) * rnd()
            val phase = rnd() * PI * 2.0
            val inclination = (rnd() - 0.5) * Math.toRadians(9.0)
            val verticalPhase = rnd() * PI * 2.0
            val semiMajorAu = 2.05 + ((radius - INNER_RADIUS) / (OUTER_RADIUS - INNER_RADIUS)) * 1.25
            val periodDays = 365.256 * sqrt(semiMajorAu * semiMajorAu * semiMajorAu)
            val size = (0.75 + rnd() * 1.35).toFloat()
            AsteroidOrbit(radius, phase, inclination, verticalPhase, periodDays, size)
        }
    }

    fun positions(orbits: List<AsteroidOrbit>, simulationSeconds: Double): FloatArray {
        val days = simulationSeconds.coerceAtLeast(0.0) / 86400.0
        val values = FloatArray(orbits.size * 3)
        orbits.forEachIndexed { index, orbit ->
            val angle = orbit.phaseRad + 2.0 * PI * days / orbit.orbitPeriodDays
            val x = cos(angle) * orbit.radius
            val z = sin(angle) * orbit.radius
            val y = sin(angle + orbit.verticalPhaseRad) * orbit.radius * sin(orbit.inclinationRad)
            values[index * 3] = x.toFloat()
            values[index * 3 + 1] = y.toFloat()
            values[index * 3 + 2] = z.toFloat()
        }
        return values
    }
}
