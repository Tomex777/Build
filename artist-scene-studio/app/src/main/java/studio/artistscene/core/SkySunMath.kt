package studio.artistscene.core

import kotlin.math.sqrt

/**
 * A scene sun is a directional light whose direction points DOWN toward the
 * terrain; sky pixels must look UP, opposite that direction. Keeping this
 * conversion centralized avoids skies with a static painted sun and shadows
 * moving in a different direction.
 */
object SkySunMath {
    fun toSunPosition(lightDirection: Vec3): Vec3 {
        require(lightDirection.x.isFinite() && lightDirection.y.isFinite() &&
            lightDirection.z.isFinite()) { "Sunlight direction must be finite" }
        val length = sqrt(
            lightDirection.x * lightDirection.x +
                lightDirection.y * lightDirection.y +
                lightDirection.z * lightDirection.z
        )
        require(length > 0.0001f && length.isFinite()) { "A sun needs a nonzero direction" }
        return Vec3(
            -lightDirection.x / length,
            -lightDirection.y / length,
            -lightDirection.z / length,
        )
    }

    /** Dot between a normalized outward sky ray and normalized sun position. */
    fun alignment(sunPosition: Vec3, skyRayX: Float, skyRayY: Float, skyRayZ: Float): Float =
        (skyRayX * sunPosition.x + skyRayY * sunPosition.y +
            skyRayZ * sunPosition.z).coerceIn(-1f, 1f)

    /** Two-degree snapping limits costly cubemap rebuilds during live edits. */
    fun quantizeDirection(lightDirection: Vec3, stepDegrees: Float = 2f): Vec3 {
        require(stepDegrees.isFinite() && stepDegrees in 0.5f..15f)
        val angles = SunOrbit.angles(lightDirection)
        return SunOrbit.direction(
            kotlin.math.round(angles.azimuthDegrees / stepDegrees) * stepDegrees,
            kotlin.math.round(angles.elevationDegrees / stepDegrees) * stepDegrees,
        )
    }
}
