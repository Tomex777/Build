package studio.artistscene.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.exp

/**
 * Deterministic, bounded spring angular motion for ACTUAL skinned joints.
 *
 * A strand root has an authored rest-axis pointing toward its child. Gravity
 * in the parent-joint frame generates a torque via restAxis cross gravity.
 * Inertia from the parent bone's displacement and a weak optional wind make
 * its tip lag during head turns, then settle. No animation clips, textures,
 * simulated buttons or render-only overlays are involved.
 */
class SpringMotion(
    private val restTip: Vec3,
    stiffness: Float = .5f,
    drag: Float = .4f,
) {
    private val stiffness = stiffness.coerceIn(.05f, 1f)
    private val drag = drag.coerceIn(0f, 1f)
    private var x = 0f
    private var z = 0f
    private var vx = 0f
    private var vz = 0f
    private var previousPosition: Vec3? = null
    private var previousVelocity = Vec3()
    private var seconds = 0f

    /** Returns actual local-joint angle offsets in degrees, constrained to 28°. */
    fun advance(
        dtSeconds: Float,
        gravityLocal: Vec3,
        pivotWorld: Vec3,
        gravityPower: Float = 1f,
        wind: Float = 0f,
    ): Vec3 {
        if (!dtSeconds.isFinite() || dtSeconds <= 0f) return Vec3(x, 0f, z)
        // Never integrate one huge frame after background/resume: avoid an impulse.
        val dt = dtSeconds.coerceIn(.001f, 1f / 30f)
        seconds += dt
        val velocity = previousPosition?.let { prev ->
            val v = difference(pivotWorld, prev, 1f / dt)
            Vec3(v.x.coerceIn(-8f, 8f), v.y.coerceIn(-8f, 8f), v.z.coerceIn(-8f, 8f))
        } ?: Vec3()
        previousPosition = pivotWorld
        // Real physical pivot acceleration, capped to prevent teleport explosions.
        val acceleration = difference(velocity, previousVelocity, 1f / dt)
        previousVelocity = velocity

        val gravity = gravityPower.coerceIn(0f, 2f)
        val breeze = wind.coerceIn(0f, 2f)
        // These are independent artist controls, not a combined direction
        // multiplied by gravity: at Gravity=0 a breeze MUST still bend hair.
        // Parent acceleration is also independent of gravity strength.
        val down = normalize(gravityLocal)
        val effective = Vec3(
            down.x * gravity - acceleration.x.coerceIn(-25f, 25f) * .014f +
                sin(seconds * 1.7f) * breeze * .24f,
            down.y * gravity - acceleration.y.coerceIn(-25f, 25f) * .014f,
            down.z * gravity - acceleration.z.coerceIn(-25f, 25f) * .014f +
                cos(seconds * 1.19f) * breeze * .19f,
        )
        val rest = normalize(restTip)
        val torque = cross(rest, effective)
        val maxTilt = 28f
        val targetX = (torque.x * maxTilt).coerceIn(-maxTilt, maxTilt)
        val targetZ = (torque.z * maxTilt).coerceIn(-maxTilt, maxTilt)
        // Stable second-order damped spring at Android frame rates: responsiveness
        // follows VRM stiffness; nonzero damping avoids continual oscillation.
        val frequency = 2f + stiffness * 5.5f
        val k = frequency * frequency
        val damping = 2f * frequency * (.42f + drag * .95f)
        vx += (k * (targetX - x) - damping * vx) * dt
        vz += (k * (targetZ - z) - damping * vz) * dt
        x = (x + vx * dt).coerceIn(-maxTilt, maxTilt)
        z = (z + vz * dt).coerceIn(-maxTilt, maxTilt)
        return Vec3(x, 0f, z)
    }

    fun reset() {
        x = 0f; z = 0f; vx = 0f; vz = 0f
        previousPosition = null
        previousVelocity = Vec3()
        seconds = 0f
    }

    private fun difference(a: Vec3, b: Vec3, factor: Float) =
        Vec3((a.x - b.x) * factor, (a.y - b.y) * factor, (a.z - b.z) * factor)

    private fun normalize(v: Vec3): Vec3 {
        val length = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
        return if (length > .00001f) Vec3(v.x / length, v.y / length, v.z / length)
        else Vec3(0f, -1f, 0f)
    }

    private fun cross(a: Vec3, b: Vec3): Vec3 =
        Vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
}
