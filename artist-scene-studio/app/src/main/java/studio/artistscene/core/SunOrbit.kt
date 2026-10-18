package studio.artistscene.core

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A directional light points from its sun position towards the ground. */
data class SunAngles(val azimuthDegrees: Float, val elevationDegrees: Float)

/** Azimuth: 0° is +Z, 90° is +X; elevation: 0° horizon, 90° zenith. */
object SunOrbit {
    fun direction(azimuthDegrees: Float, elevationDegrees: Float): Vec3 {
        require(azimuthDegrees.isFinite() && elevationDegrees.isFinite())
        val azimuth = Math.toRadians(azimuthDegrees.toDouble())
        val elevation = Math.toRadians(elevationDegrees.coerceIn(2f, 89f).toDouble())
        val horizontal = cos(elevation)
        return Vec3(
            x = (-sin(azimuth) * horizontal).toFloat(),
            y = -sin(elevation).toFloat(),
            z = (-cos(azimuth) * horizontal).toFloat(),
        )
    }

    fun angles(direction: Vec3): SunAngles {
        val length = sqrt((direction.x * direction.x + direction.y * direction.y +
            direction.z * direction.z).toDouble()).coerceAtLeast(0.00001)
        val elevation = Math.toDegrees(asin((-direction.y / length).coerceIn(-1.0, 1.0)))
        val azimuth = Math.toDegrees(atan2(-direction.x.toDouble(), -direction.z.toDouble()))
        return SunAngles(((azimuth + 360.0) % 360.0).toFloat(), elevation.toFloat())
    }

    fun move(direction: Vec3, azimuthStep: Float = 0f, elevationStep: Float = 0f): Vec3 {
        val at = angles(direction)
        return direction(
            azimuthDegrees = (at.azimuthDegrees + azimuthStep + 360f) % 360f,
            elevationDegrees = (at.elevationDegrees + elevationStep).coerceIn(2f, 89f),
        )
    }
}
