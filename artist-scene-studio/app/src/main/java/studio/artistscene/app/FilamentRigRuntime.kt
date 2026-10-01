package studio.artistscene.app

import android.opengl.Matrix
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.engine
import studio.artistscene.core.RigBone
import studio.artistscene.core.RigDefinition
import studio.artistscene.core.RigMorphTarget
import studio.artistscene.core.RigPose
import studio.artistscene.core.uniqueRigIds
import studio.artistscene.core.Vec3

/** Renderer-owned mapping from durable rig IDs to the actual glTF joints and morph targets. */
internal class FilamentRigRuntime private constructor(
    private val model: ModelInstance,
    private val joints: List<Joint>,
    private val morphTargets: List<MorphTarget>,
) {
    data class Joint(
        val bone: RigBone,
        val entity: Int,
        val restLocalTransform: FloatArray,
    )

    data class MorphTarget(
        val definition: RigMorphTarget,
        val entity: Int,
        val targetIndex: Int,
    )

    val definition = RigDefinition(
        name = model.skinNames.firstOrNull()?.takeUnless { it.isNullOrBlank() }
            ?: if (joints.isNotEmpty()) "Imported skeleton" else "Imported shapes",
        bones = joints.map { it.bone },
        morphTargets = morphTargets.map { it.definition },
    )

    /** Rebuilds authored pose state from imported rest data, so edits never accumulate drift. */
    fun apply(pose: RigPose?) {
        val transformManager = model.engine.transformManager
        val rotations = pose?.joints.orEmpty()
        joints.forEach { joint ->
            val transform = joint.restLocalTransform.copyOf()
            val rotation = rotations[joint.bone.id] ?: Vec3()
            Matrix.rotateM(transform, 0, rotation.x, 1f, 0f, 0f)
            Matrix.rotateM(transform, 0, rotation.y, 0f, 1f, 0f)
            Matrix.rotateM(transform, 0, rotation.z, 0f, 0f, 1f)
            transformManager.setTransform(transformManager.getInstance(joint.entity), transform)
        }

        val renderableManager = model.engine.renderableManager
        val authoredMorphs = pose?.morphWeights.orEmpty()
        morphTargets.groupBy { it.entity }.forEach { (entity, bindings) ->
            if (!renderableManager.hasComponent(entity)) return@forEach
            val instance = renderableManager.getInstance(entity)
            val targetCount = renderableManager.getMorphTargetCount(instance)
            if (targetCount <= 0) return@forEach
            val weights = FloatArray(targetCount)
            bindings.forEach { binding ->
                if (binding.targetIndex in weights.indices) {
                    weights[binding.targetIndex] =
                        authoredMorphs[binding.definition.id].orZero().coerceIn(0f, 1f)
                }
            }
            renderableManager.setMorphWeights(instance, weights, 0)
        }

        // Filament Animator updates only skin matrices here. No animation clip is applied, so
        // manually authored local bone transforms and morph weights remain intact.
        if (joints.isNotEmpty()) model.animator.updateBoneMatrices()
    }

    fun worldJointPositions(): Map<String, Vec3> {
        val transformManager = model.engine.transformManager
        return joints.associate { joint ->
            val world = transformManager.getWorldTransform(
                transformManager.getInstance(joint.entity),
                FloatArray(16),
            )
            joint.bone.id to Vec3(world[12], world[13], world[14])
        }
    }

    companion object {
        fun discover(model: ModelInstance): FilamentRigRuntime? {
            val transformManager = model.engine.transformManager
            val jointEntities = if (model.skinCount == 0) {
                emptyList()
            } else {
                (0 until model.skinCount).flatMap { skin ->
                    model.getJointsAt(skin).toList()
                }.distinct()
            }

            fun parentEntity(entity: Int): Int {
                if (!transformManager.hasComponent(entity)) return 0
                return transformManager.getParent(transformManager.getInstance(entity))
            }

            fun pathParts(entity: Int): List<String> {
                val parts = mutableListOf<String>()
                val visited = mutableSetOf<Int>()
                var cursor = entity
                while (cursor != 0 && visited.add(cursor)) {
                    val name = model.asset.getName(cursor).takeUnless { it.isNullOrBlank() }
                        ?: "joint-${jointEntities.indexOf(cursor).coerceAtLeast(0)}"
                    parts += name
                    cursor = parentEntity(cursor)
                }
                return parts.asReversed()
            }

            fun slug(value: String): String =
                value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "item" }

            fun toBoneId(parts: List<String>) = parts.joinToString("/") { slug(it) }

            // Allocate every ID before resolving parents. Renaming a duplicate only after
            // constructing bones leaves descendants pointing at the first namesake.
            val stableIds = uniqueRigIds(jointEntities.map { toBoneId(pathParts(it)) })
            val ids = jointEntities.zip(stableIds).toMap()
            val bones = jointEntities.mapIndexed { index, entity ->
                val names = pathParts(entity)
                var parent = parentEntity(entity)
                while (parent != 0 && parent !in ids) parent = parentEntity(parent)
                RigBone(
                    id = ids.getValue(entity),
                    name = names.lastOrNull() ?: "Joint ${index + 1}",
                    parentId = ids[parent],
                )
            }
            val boneByEntity = jointEntities.zip(bones).toMap()
            val joints = jointEntities.map { entity ->
                val matrix = transformManager.getTransform(transformManager.getInstance(entity), FloatArray(16))
                Joint(boneByEntity.getValue(entity), entity, matrix)
            }

            val renderableManager = model.engine.renderableManager
            val morphTargets = buildList {
                model.asset.renderableEntities.forEachIndexed { entityIndex, entity ->
                    if (!renderableManager.hasComponent(entity)) return@forEachIndexed
                    val instance = renderableManager.getInstance(entity)
                    val targetCount = renderableManager.getMorphTargetCount(instance)
                    if (targetCount <= 0) return@forEachIndexed
                    val declaredNames = model.asset.getMorphTargetNames(entity)
                    val meshName = model.asset.getName(entity).takeUnless { it.isNullOrBlank() }
                        ?: "Mesh ${entityIndex + 1}"
                    repeat(targetCount) { targetIndex ->
                        val targetName = declaredNames.getOrNull(targetIndex)
                            ?.takeUnless { it.isBlank() }
                            ?: "Shape ${targetIndex + 1}"
                        add(
                            MorphTarget(
                                definition = RigMorphTarget(
                                    id = "morph/${slug(meshName)}-$entityIndex/${slug(targetName)}-$targetIndex",
                                    name = targetName,
                                    meshName = meshName,
                                ),
                                entity = entity,
                                targetIndex = targetIndex,
                            ),
                        )
                    }
                }
            }

            if (joints.isEmpty() && morphTargets.isEmpty()) return null
            return FilamentRigRuntime(model, joints, morphTargets)
        }
    }
}

private fun Float?.orZero(): Float = this ?: 0f
