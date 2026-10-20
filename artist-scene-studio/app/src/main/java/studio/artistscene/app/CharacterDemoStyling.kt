package studio.artistscene.app

import studio.artistscene.core.Actor
import studio.artistscene.core.ActorKind
import studio.artistscene.core.AnimeExpressions
import studio.artistscene.core.FaceExpression
import studio.artistscene.core.RigDefinition

/**
 * One-time authoring of an actual model expression for the untouched CC0
 * two-classmate sample. This runs only AFTER the skinned model's real morph
 * targets have been discovered; no guessed morph index or sprite is used.
 *
 * The initializer is consumed and removed after success. Loading an artist's
 * saved scene, changing a pose, or restoring a project cannot reapply it.
 */
internal object CharacterDemoStyling {
    const val INITIAL_EXPRESSION_KEY = "miseInitialExpression"
    private const val STARTER_SMILE_WEIGHT = .55f

    fun resolve(actor: Actor, discoveredRig: RigDefinition): Actor {
        if (actor.kind != ActorKind.CHARACTER ||
            actor.metadata["misePose"] != UnderTreeScene.POSE_TAG ||
            actor.id != UnderTreeScene.FIRST_STUDENT_ID
        ) return actor

        val preset = actor.metadata[INITIAL_EXPRESSION_KEY] ?: return actor
        val after = actor.metadata - INITIAL_EXPRESSION_KEY

        // Respect artist-authored changes even if they happen while the GLB loads.
        // Also consume the sample marker so a later reload never overwrites them.
        if (actor.locked || actor.rig?.morphWeights?.isNotEmpty() == true)
            return actor.copy(metadata = after)

        // Currently only the explicitly authored gentle Smile is supported.
        // Fail closed if the original licensed VRM does not expose its real
        // Face blendshape; do not simulate a fake "expression".
        if (preset != FaceExpression.SMILE.name) return actor
        val fitted = actor.copy(rigDefinition = discoveredRig)
        val expression = AnimeExpressions.apply(fitted, FaceExpression.SMILE) ?: return actor
        val softened = expression.copy(morphWeights =
            expression.morphWeights.mapValues { (_, value) -> value * STARTER_SMILE_WEIGHT },
        )
        return actor.copy(rigDefinition = discoveredRig, rig = softened, metadata = after)
    }
}
