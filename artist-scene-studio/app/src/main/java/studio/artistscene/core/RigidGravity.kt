package studio.artistscene.core

import kotlin.math.abs
import kotlin.math.exp

/** Ground-plane collision and per-actor rigid-body gravity. It is intentionally
 * separate from the VRM per-strand spring solver. Scale is meters / seconds.
 * Bodies only collide with the studio floor, NOT other characters/props.
 */
class RigidGravity(initial: SceneProject) {
    private data class DynamicBody(var verticalSpeed: Float = 0f)
    private val velocities = initial.actors
        .filter { it.physics.dynamic && !it.locked && it.visible && it.kind !in setOf(ActorKind.LIGHT, ActorKind.CAMERA) }
        .associate { it.id to DynamicBody() }

    fun step(project: SceneProject, seconds: Float): Map<String, Float> {
        if (seconds <= 0f || !seconds.isFinite() || !project.world.gravityEnabled) return emptyMap()
        // Integrate at max 60Hz so frames delayed by background/thermal pressure
        // cannot tunnel an avatar or a prop below the floor plane.
        val frame = seconds.coerceAtMost(.07f)
        val steps = kotlin.math.ceil(frame / (1f / 60f)).toInt().coerceAtLeast(1)
        val dt = frame / steps
        val result = linkedMapOf<String, Float>()
        project.actors.forEach { actor ->
            val state = velocities[actor.id] ?: return@forEach
            if (!actor.physics.dynamic || actor.locked || !actor.visible) return@forEach
            var y = actor.transform.position.y
            repeat(steps) {
                state.verticalSpeed -= project.world.gravityMetersPerSecondSquared.coerceIn(0f, 25f) * dt
                state.verticalSpeed *= exp(-actor.physics.airDrag.coerceIn(0f, 5f) * dt)
                y += state.verticalSpeed * dt
                if (y <= 0f && project.world.groundEnabled) {
                    y = 0f
                    state.verticalSpeed = if (abs(state.verticalSpeed) > .16f)
                        -state.verticalSpeed * actor.physics.bounciness.coerceIn(0f, .8f)
                    else 0f
                }
            }
            // Skip unchanged transforms to avoid needless Compose recomposition.
            if (abs(actor.transform.position.y - y) >= .00001f) result[actor.id] = y
        }
        return result
    }
}

@kotlinx.serialization.Serializable
data class RigidPhysicsSettings(
    val dynamic: Boolean = false,
    val bounciness: Float = .14f,
    val airDrag: Float = .09f,
)
