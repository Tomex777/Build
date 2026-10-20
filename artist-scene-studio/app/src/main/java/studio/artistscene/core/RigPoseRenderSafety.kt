package studio.artistscene.core

/**
 * Renderer-boundary validation of persisted/authored rig values.
 *
 * Kotlin's Float.coerceIn accepts NaN unchanged, and Android's Matrix.rotateM propagates
 * non-finite angles into skinning matrices. A malformed imported/old pose must not poison
 * the entire model or affect a second independent character's frame.
 */
internal object RigPoseRenderSafety {
    fun jointOffset(rotation: Vec3?): Vec3 =
        rotation?.takeIf { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() } ?: Vec3()

    fun morphWeight(weight: Float?): Float =
        weight?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
}
