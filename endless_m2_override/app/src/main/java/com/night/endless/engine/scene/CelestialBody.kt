package com.night.endless.engine.scene

import com.night.endless.engine.math.Vec3d

data class CelestialBody(
    val id: String,
    val name: String,
    val radius: Double,
    val orbitRadius: Double,
    val orbitPeriodDays: Double,
    val rotationHours: Double,
    val color: FloatArray,
    val parentId: String? = null,
    val phaseRad: Double = 0.0,
    val radiusKm: Double = 0.0,
    val semiMajorAxisAu: Double? = null,
    val axialTiltDeg: Double = 0.0,
    val description: String = "",
    var position: Vec3d = Vec3d.ZERO
)
