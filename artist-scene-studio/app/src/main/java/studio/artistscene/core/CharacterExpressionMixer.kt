package studio.artistscene.core

/**
 * Blends only real, discovered glTF/VRM facial morph targets.
 *
 * Emotional expressions and eyelid closure are independent controls: choosing
 * a smile must not reopen an artist-authored blink, and closing the eyes must
 * not silently remove the smile. Shape, phoneme, and body morph weights remain
 * untouched. No synthetic face textures or renderer-only state is introduced.
 */
object CharacterExpressionMixer {
    private val emotions = listOf(
        FaceExpression.SMILE, FaceExpression.ANGRY,
        FaceExpression.SAD, FaceExpression.SURPRISED,
    )

    private fun normalized(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), "")

    /** Exclude body/hair morphs that happen to contain an emotion-like name. */
    private fun isFaceMorph(target: RigMorphTarget): Boolean {
        val mesh = target.meshName?.let(::normalized) ?: return true
        return mesh.contains("face") || mesh.contains("head") || mesh.contains("facial") ||
            mesh.contains("eye") || mesh.contains("mouth")
    }

    private fun matching(targets: List<RigMorphTarget>, expression: FaceExpression): Set<String> {
        if (expression == FaceExpression.NEUTRAL) return emptySet()
        return targets.asSequence()
            .filter(::isFaceMorph)
            .filter { target ->
                val name = normalized(target.name)
                expression.aliases.any { alias -> name.contains(normalized(alias)) }
            }.map { it.id }.toSet()
    }

    private data class Groups(
        val emotions: Set<String>,
        val eyes: Set<String>,
        val selected: Set<String>,
    ) {
        val all: Set<String> get() = emotions + eyes
    }

    private fun groups(targets: List<RigMorphTarget>, expression: FaceExpression): Groups =
        Groups(
            emotions = emotions.flatMapTo(linkedSetOf()) { matching(targets, it) },
            eyes = matching(targets, FaceExpression.BLINK),
            selected = matching(targets, expression),
        )

    fun canApply(actor: Actor, expression: FaceExpression): Boolean {
        if (actor.kind != ActorKind.CHARACTER || actor.locked) return false
        val morphs = actor.rigDefinition?.morphTargets.orEmpty()
        val group = groups(morphs, expression)
        return when (expression) {
            FaceExpression.NEUTRAL ->
                group.all.any { id -> (actor.rig?.morphWeights?.get(id) ?: 0f) > 0f }
            else -> group.selected.isNotEmpty()
        }
    }

    /** Returns null for a no-op or unsupported choice so history stays clean. */
    fun apply(actor: Actor, expression: FaceExpression): RigPose? {
        if (actor.kind != ActorKind.CHARACTER || actor.locked) return null
        val morphs = actor.rigDefinition?.morphTargets ?: return null
        val group = groups(morphs, expression)
        val old = actor.rig ?: RigPose()
        if (expression != FaceExpression.NEUTRAL && group.selected.isEmpty()) return null

        val weights = old.morphWeights.toMutableMap()
        when (expression) {
            FaceExpression.NEUTRAL -> group.all.forEach(weights::remove)
            FaceExpression.BLINK -> {
                val isFullyClosed = group.selected.all { (weights[it] ?: 0f) >= 0.999f }
                if (isFullyClosed) group.selected.forEach(weights::remove)
                else group.selected.forEach { weights[it] = 1f }
            }
            else -> {
                // A single emotional look replaces only other emotional morphs.
                group.emotions.forEach(weights::remove)
                group.selected.forEach { weights[it] = 1f }
            }
        }
        return old.copy(morphWeights = weights).takeIf { it != old }
    }
}
