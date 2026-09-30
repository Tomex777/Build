package studio.artistscene.core

import kotlin.math.abs

internal object SceneTimelinePaths {
    const val POSITION = "transform.position"
    const val ROTATION = "transform.rotation"
    const val SCALE = "transform.scale"
    const val RIG_JOINT_PREFIX = "rig.joint."
    const val RIG_MORPH_PREFIX = "rig.morph."

    fun rigJoint(boneId: String): String = RIG_JOINT_PREFIX + boneId
    fun rigMorph(targetId: String): String = RIG_MORPH_PREFIX + targetId
    fun rigJointId(propertyPath: String): String? =
        propertyPath.takeIf { it.startsWith(RIG_JOINT_PREFIX) }?.removePrefix(RIG_JOINT_PREFIX)?.takeIf { it.isNotBlank() }
    fun rigMorphId(propertyPath: String): String? =
        propertyPath.takeIf { it.startsWith(RIG_MORPH_PREFIX) }?.removePrefix(RIG_MORPH_PREFIX)?.takeIf { it.isNotBlank() }
}

fun SceneProject.evaluateTimeline(timeSeconds: Float): SceneProject {
    if (tracks.none { it.enabled && it.keyframes.isNotEmpty() }) return this
    val duration = timeline.durationSeconds.coerceAtLeast(0.001f)
    val time = when {
        timeSeconds <= 0f -> 0f
        timeline.loop -> timeSeconds % duration
        else -> timeSeconds.coerceAtMost(duration)
    }
    val activeTracks = tracks.filter { it.enabled && it.keyframes.isNotEmpty() }
    return copy(
        actors = actors.map { actor ->
            activeTracks.asSequence()
                .filter { it.targetActorId == actor.id }
                .fold(actor) { current, track ->
                    val value = track.sampleAt(time) ?: return@fold current
                    when {
                        track.propertyPath == SceneTimelinePaths.POSITION -> value.vector?.let {
                            current.copy(transform = current.transform.copy(position = it))
                        } ?: current
                        track.propertyPath == SceneTimelinePaths.ROTATION -> value.rotationEulerDegrees?.let {
                            current.copy(transform = current.transform.copy(rotationEulerDegrees = it))
                        } ?: current
                        track.propertyPath == SceneTimelinePaths.SCALE -> value.vector?.let {
                            current.copy(transform = current.transform.copy(scale = it))
                        } ?: current
                        SceneTimelinePaths.rigJointId(track.propertyPath) != null -> {
                            val boneId = requireNotNull(SceneTimelinePaths.rigJointId(track.propertyPath))
                            value.rotationEulerDegrees?.let { current.withTimelineJoint(boneId, it) } ?: current
                        }
                        SceneTimelinePaths.rigMorphId(track.propertyPath) != null -> {
                            val targetId = requireNotNull(SceneTimelinePaths.rigMorphId(track.propertyPath))
                            value.scalar?.let { current.withTimelineMorph(targetId, it) } ?: current
                        }
                        else -> current
                    }
                }
        },
    )
}

fun SceneProject.transformKeyTimes(actorId: String): List<Float> =
    tracks.asSequence()
        .filter {
            it.targetActorId == actorId &&
                it.propertyPath in setOf(
                    SceneTimelinePaths.POSITION,
                    SceneTimelinePaths.ROTATION,
                    SceneTimelinePaths.SCALE,
                )
        }
        .flatMap { it.keyframes.asSequence() }
        .map { it.timeSeconds }
        .distinct()
        .sorted()
        .toList()

fun SceneProject.poseKeyTimes(actorId: String): List<Float> =
    tracks.asSequence()
        .filter {
            it.targetActorId == actorId &&
                (
                    it.propertyPath.startsWith(SceneTimelinePaths.RIG_JOINT_PREFIX) ||
                        it.propertyPath.startsWith(SceneTimelinePaths.RIG_MORPH_PREFIX)
                    )
        }
        .flatMap { it.keyframes.asSequence() }
        .map { it.timeSeconds }
        .distinct()
        .sorted()
        .toList()

private fun Actor.withTimelineJoint(boneId: String, rotation: Vec3): Actor {
    if (rigDefinition?.bones?.none { it.id == boneId } != false) return this
    val joints = rig?.joints.orEmpty().toMutableMap()
    if (rotation == Vec3()) joints.remove(boneId) else joints[boneId] = rotation
    val pose = (rig ?: RigPose()).copy(joints = joints)
        .takeUnless { it.joints.isEmpty() && it.morphWeights.isEmpty() }
    return copy(rig = pose, animation = animation.copy(playing = false))
}

private fun Actor.withTimelineMorph(targetId: String, weight: Float): Actor {
    if (rigDefinition?.morphTargets?.none { it.id == targetId } != false) return this
    val weights = rig?.morphWeights.orEmpty().toMutableMap()
    val normalized = weight.coerceIn(0f, 1f)
    if (normalized <= 0.00001f) weights.remove(targetId) else weights[targetId] = normalized
    val pose = (rig ?: RigPose()).copy(morphWeights = weights)
        .takeUnless { it.joints.isEmpty() && it.morphWeights.isEmpty() }
    return copy(rig = pose, animation = animation.copy(playing = false))
}

private fun AnimationTrack.sampleAt(timeSeconds: Float): AnimatedValue? {
    val keys = keyframes.sortedBy { it.timeSeconds }
    if (keys.isEmpty()) return null
    if (keys.size == 1 || timeSeconds <= keys.first().timeSeconds) return keys.first().value
    if (timeSeconds >= keys.last().timeSeconds) return keys.last().value

    val rightIndex = keys.indexOfFirst { it.timeSeconds >= timeSeconds }.coerceAtLeast(1)
    val left = keys[rightIndex - 1]
    val right = keys[rightIndex]
    val span = (right.timeSeconds - left.timeSeconds).coerceAtLeast(0.0001f)
    val raw = ((timeSeconds - left.timeSeconds) / span).coerceIn(0f, 1f)
    val amount = when (interpolation) {
        Interpolation.STEP -> 0f
        Interpolation.LINEAR -> raw
        Interpolation.SMOOTH -> raw * raw * (3f - 2f * raw)
    }
    return interpolate(left.value, right.value, amount)
}

private fun interpolate(a: AnimatedValue, b: AnimatedValue, amount: Float): AnimatedValue =
    AnimatedValue(
        scalar = if (a.scalar != null && b.scalar != null) lerp(a.scalar, b.scalar, amount) else a.scalar ?: b.scalar,
        vector = if (a.vector != null && b.vector != null) lerp(a.vector, b.vector, amount) else a.vector ?: b.vector,
        rotationEulerDegrees = if (a.rotationEulerDegrees != null && b.rotationEulerDegrees != null) {
            Vec3(
                lerpDegrees(a.rotationEulerDegrees.x, b.rotationEulerDegrees.x, amount),
                lerpDegrees(a.rotationEulerDegrees.y, b.rotationEulerDegrees.y, amount),
                lerpDegrees(a.rotationEulerDegrees.z, b.rotationEulerDegrees.z, amount),
            )
        } else {
            a.rotationEulerDegrees ?: b.rotationEulerDegrees
        },
    )

private fun lerp(a: Float, b: Float, amount: Float): Float = a + (b - a) * amount

private fun lerp(a: Vec3, b: Vec3, amount: Float): Vec3 = Vec3(
    lerp(a.x, b.x, amount),
    lerp(a.y, b.y, amount),
    lerp(a.z, b.z, amount),
)

private fun lerpDegrees(a: Float, b: Float, amount: Float): Float {
    var delta = (b - a) % 360f
    if (delta > 180f) delta -= 360f
    if (delta <= -180f) delta += 360f
    var result = a + delta * amount
    result %= 360f
    if (result > 180f) result -= 360f
    if (result <= -180f) result += 360f
    return if (abs(result) < 0.00001f) 0f else result
}
