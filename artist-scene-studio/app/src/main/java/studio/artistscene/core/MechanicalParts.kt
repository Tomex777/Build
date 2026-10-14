package studio.artistscene.core

/** Named controls for original rigid-part starter rigs. Axes are local glTF joint axes. */
data class MechanicalPart(val name: String, val axis: TransformAxis, val minimum: Float, val maximum: Float) {
    fun constrain(rotation: Vec3): Vec3 {
        val value = when (axis) { TransformAxis.X -> rotation.x; TransformAxis.Y -> rotation.y; TransformAxis.Z -> rotation.z }
        val safe = if (value.isFinite()) value.coerceIn(minimum, maximum) else 0f
        return when (axis) { TransformAxis.X -> Vec3(x = safe); TransformAxis.Y -> Vec3(y = safe); TransformAxis.Z -> Vec3(z = safe) }
    }
}

fun Actor.mechanicalParts(): List<MechanicalPart> = when (asset?.assetId) {
    "starter.mise.bicycle" -> listOf(
        MechanicalPart("Front steering", TransformAxis.Y, -60f, 60f),
        MechanicalPart("Front wheel", TransformAxis.X, -180f, 180f),
        MechanicalPart("Rear wheel", TransformAxis.X, -180f, 180f),
        MechanicalPart("Pedals", TransformAxis.X, -180f, 180f),
    )
    "starter.mise.car" -> listOf(
        MechanicalPart("Front left steering", TransformAxis.Y, -40f, 40f),
        MechanicalPart("Front right steering", TransformAxis.Y, -40f, 40f),
        MechanicalPart("Front left wheel", TransformAxis.X, -180f, 180f),
        MechanicalPart("Front right wheel", TransformAxis.X, -180f, 180f),
        MechanicalPart("Rear left wheel", TransformAxis.X, -180f, 180f),
        MechanicalPart("Rear right wheel", TransformAxis.X, -180f, 180f),
        MechanicalPart("Left door", TransformAxis.Y, 0f, 75f),
        MechanicalPart("Right door", TransformAxis.Y, -75f, 0f),
    )
    else -> emptyList()
}
