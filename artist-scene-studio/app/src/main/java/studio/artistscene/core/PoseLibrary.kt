package studio.artistscene.core

/**
 * Authored editable pose studies. Rotation values are local offsets from each imported
 * skeleton's bind pose, never absolute bone transforms. Works offline with VRoid,
 * MakeHuman and correctly labelled third-party GLB/VRM skeletons.
 */
enum class PosePreset(val title: String, val category: String) {
    READY("Ready", "Standing"),
    WALK("Walk", "Movement"),
    RUN("Run", "Movement"),
    JUMP("Jump", "Movement"),
    HERO("Hero", "Action"),
    GUARD("Guard", "Action"),
    REACH("Reach", "Action"),
    SIT("Sit", "Rest"),
    WAVE("Wave", "Expression"),
    DANCE("Dance", "Expression"),
}

object PoseLibrary {
    private fun limb(x: Float = 0f, y: Float = 0f, z: Float = 0f) = Vec3(x, y, z)

    /**
     * Human-readable joint IDs come from the existing rig's RigSemantics.label.
     * Keeping the template semantic avoids reusing imported node indices or vendor paths.
     */
    private fun rotations(preset: PosePreset): Map<String, Vec3> = when (preset) {
        PosePreset.READY -> mapOf(
            "left arm" to limb(z = 12f), "right arm" to limb(z = -12f),
            "left forearm" to limb(x = -13f), "right forearm" to limb(x = -13f),
        )
        PosePreset.WALK -> mapOf(
            "left arm" to limb(x = 23f, z = 8f), "right arm" to limb(x = -23f, z = -8f),
            "left thigh" to limb(x = -27f), "right thigh" to limb(x = 27f),
            "left lower leg" to limb(x = 33f), "right lower leg" to limb(x = 5f),
        )
        PosePreset.RUN -> mapOf(
            "left arm" to limb(x = 50f, z = 12f), "right arm" to limb(x = -42f, z = -12f),
            "left forearm" to limb(x = -72f), "right forearm" to limb(x = -75f),
            "left thigh" to limb(x = -51f), "right thigh" to limb(x = 39f),
            "left lower leg" to limb(x = 77f), "right lower leg" to limb(x = 60f),
        )
        PosePreset.JUMP -> mapOf(
            "left arm" to limb(x = -28f, z = 87f),
            "right arm" to limb(x = -28f, z = -87f),
            "left thigh" to limb(x = -32f, z = 12f),
            "right thigh" to limb(x = 18f, z = -12f),
            "left lower leg" to limb(x = 68f), "right lower leg" to limb(x = 35f),
        )
        PosePreset.HERO -> mapOf(
            "left arm" to limb(x = -15f, z = 50f), "right arm" to limb(x = -35f, z = -110f),
            "left forearm" to limb(x = -22f), "right forearm" to limb(x = -15f),
            "left thigh" to limb(x = -20f, z = 12f), "right thigh" to limb(x = 12f, z = -12f),
            "left lower leg" to limb(x = 28f), "head" to limb(y = -10f),
        )
        PosePreset.GUARD -> mapOf(
            "left arm" to limb(x = -36f, z = 30f), "right arm" to limb(x = -36f, z = -30f),
            "left forearm" to limb(x = -100f), "right forearm" to limb(x = -100f),
            "left thigh" to limb(x = -18f, z = 14f), "right thigh" to limb(x = -18f, z = -14f),
            "left lower leg" to limb(x = 27f), "right lower leg" to limb(x = 27f),
        )
        PosePreset.REACH -> mapOf(
            "left arm" to limb(x = -31f, z = 40f),
            "right arm" to limb(x = -65f, z = -98f),
            "right forearm" to limb(x = -20f),
            "left thigh" to limb(x = -22f),
            "head" to limb(x = -13f, y = 16f),
        )
        PosePreset.SIT -> mapOf(
            "left thigh" to limb(x = -84f, z = 5f), "right thigh" to limb(x = -84f, z = -5f),
            "left lower leg" to limb(x = 92f), "right lower leg" to limb(x = 92f),
            "left arm" to limb(x = -25f, z = 19f), "right arm" to limb(x = -25f, z = -19f),
            "left forearm" to limb(x = -36f), "right forearm" to limb(x = -36f),
        )
        PosePreset.WAVE -> mapOf(
            "right arm" to limb(x = -20f, z = -110f), "right forearm" to limb(x = -100f),
            "left arm" to limb(z = 17f), "head" to limb(y = -8f, z = 10f),
        )
        PosePreset.DANCE -> mapOf(
            "left arm" to limb(x = -27f, z = 115f), "right arm" to limb(x = -45f, z = -65f),
            "left forearm" to limb(x = -55f), "right forearm" to limb(x = -40f),
            "left thigh" to limb(x = -28f, z = 14f), "right thigh" to limb(x = 16f, z = -18f),
            "left lower leg" to limb(x = 44f), "head" to limb(y = 10f, z = 10f),
        )
    }

    private fun boneLabel(bone: RigBone, definition: RigDefinition): String =
        RigSemantics.label(bone, definition.bones).lowercase()

    /** Replaces the named body pose, keeps previously authored finger poses and facial morphs. */
    fun apply(actor: Actor, preset: PosePreset): RigPose? {
        if (actor.kind != ActorKind.CHARACTER || actor.locked) return null
        val definition = actor.rigDefinition ?: return null
        val template = rotations(preset)
        val available = definition.bones.associateBy { boneLabel(it, definition) }
        val mapped = template.mapNotNull { (name, value) ->
            available[name]?.let { it.id to value }
        }.toMap()
        // Refuse bogus poses on nonhumanoid models or models without a resolved rig.
        if (mapped.size < 3) return null
        val standard = PosePreset.entries.flatMap { rotations(it).keys }.toSet()
        val managedIds = definition.bones.filter {
            boneLabel(it, definition) in standard
        }.mapTo(mutableSetOf()) { it.id }
        val old = actor.rig ?: RigPose()
        val next = old.copy(joints = old.joints.filterKeys { it !in managedIds } + mapped)
        return next.takeIf { it != actor.rig }
    }

    /** Reflection swaps anatomical limbs AND mirrors their Y/Z angles, retaining local X. */
    fun mirror(actor: Actor): RigPose? {
        if (actor.kind != ActorKind.CHARACTER || actor.locked) return null
        val definition = actor.rigDefinition ?: return null
        val pose = actor.rig ?: return null
        if (pose.joints.isEmpty()) return null
        val byId = definition.bones.associateBy { it.id }
        val byLabel = definition.bones.associateBy { boneLabel(it, definition) }
        val reflected = mutableMapOf<String, Vec3>()
        pose.joints.forEach { (id, angles) ->
            val bone = byId[id] ?: return@forEach
            val label = boneLabel(bone, definition)
            val opposite = when {
                label.startsWith("left ") -> "right " + label.removePrefix("left ")
                label.startsWith("right ") -> "left " + label.removePrefix("right ")
                else -> label
            }
            val destination = byLabel[opposite] ?: if (opposite == label) bone else null
            if (destination != null) reflected[destination.id] =
                Vec3(angles.x, -angles.y, -angles.z)
        }
        if (reflected.isEmpty()) return null
        return pose.copy(joints = reflected).takeIf { it != pose }
    }
}

/** Only real imported glTF morph targets are used; these are not 2D face stickers. */
enum class FaceExpression(val title: String, val aliases: Set<String>) {
    NEUTRAL("Neutral", emptySet()),
    SMILE("Smile", setOf("joy", "happy", "smile", "fun")),
    ANGRY("Angry", setOf("angry", "anger")),
    SAD("Sad", setOf("sorrow", "sad", "cry")),
    SURPRISED("Surprised", setOf("surprised", "surprise", "wow")),
    BLINK("Blink", setOf("blink", "blinkl", "blinkr")),
}

object AnimeExpressions {
    private fun key(name: String) = name.lowercase().replace(Regex("[^a-z0-9]+"), "")

    fun canApply(actor: Actor, expression: FaceExpression): Boolean {
        val targets = actor.rigDefinition?.morphTargets.orEmpty()
        return expression == FaceExpression.NEUTRAL && (actor.rig?.morphWeights?.isNotEmpty() == true) ||
            targets.any { target -> expression.aliases.any { alias -> key(target.name).contains(alias) } }
    }

    fun apply(actor: Actor, expression: FaceExpression): RigPose? {
        if (actor.kind != ActorKind.CHARACTER || actor.locked) return null
        val definition = actor.rigDefinition ?: return null
        val old = actor.rig ?: RigPose()
        val matched = definition.morphTargets.filter { morph ->
            expression.aliases.any { alias -> key(morph.name).contains(alias) }
        }
        if (expression != FaceExpression.NEUTRAL && matched.isEmpty()) return null
        // Replace all expression weights, but retain body/shape targets such as Fat/Muscle.
        val expressions = FaceExpression.entries.flatMap { it.aliases }.toSet()
        val expressiveIds = definition.morphTargets.filter { morph ->
            expressions.any { alias -> key(morph.name).contains(alias) }
        }.mapTo(mutableSetOf()) { it.id }
        val next = old.copy(morphWeights =
            old.morphWeights.filterKeys { it !in expressiveIds } +
                matched.associate { it.id to 1f },
        )
        return next.takeIf { it != old }
    }
}

/** Camera framings are project-independent; they do not create redundant cameras. */
enum class CameraShot(val title: String) {
    FRONT("Front"), THREE_QUARTER("3/4"), SIDE("Side"), BACK("Back"),
    CLOSE_UP("Portrait"), TOP("Top"),
}

object StudioCameraShots {
    fun frame(camera: SceneCamera, actor: Actor, shot: CameraShot): SceneCamera {
        val focus = actor.transform.position
        val y = focus.y + (if (shot == CameraShot.CLOSE_UP) 1.48f else 0.93f)
        val distance = if (shot == CameraShot.CLOSE_UP) 1.25f else 3.7f
        val dx: Float
        val dy: Float
        val dz: Float
        when (shot) {
            CameraShot.FRONT -> { dx = 0f; dy = 0.15f; dz = -distance }
            CameraShot.THREE_QUARTER -> { dx = -distance * .55f; dy = .27f; dz = -distance * .83f }
            CameraShot.SIDE -> { dx = -distance; dy = .15f; dz = 0f }
            CameraShot.BACK -> { dx = 0f; dy = .15f; dz = distance }
            CameraShot.CLOSE_UP -> { dx = -.12f; dy = .04f; dz = -distance }
            CameraShot.TOP -> { dx = 0f; dy = distance; dz = -.25f }
        }
        return camera.copy(
            projection = CameraProjection.PERSPECTIVE,
            position = Vec3(focus.x + dx, y + dy, focus.z + dz),
            target = Vec3(focus.x, y, focus.z),
            verticalFovDegrees = if (shot == CameraShot.CLOSE_UP) 42f else 50f,
        )
    }
}
