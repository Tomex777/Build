package studio.artistscene.app

import android.opengl.Matrix
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.engine
import studio.artistscene.core.RigBone
import studio.artistscene.core.RigDefinition
import studio.artistscene.core.RigPose
import studio.artistscene.core.Vec3

/** Renderer-owned mapping from durable rig IDs to the actual glTF joint entities. */
internal class FilamentRigRuntime private constructor(
    private val model: ModelInstance,
    private val joints: List<Joint>,
) {
    data class Joint(
        val bone: RigBone,
        val entity: Int,
        val restLocalTransform: FloatArray,
    )

    val definition = RigDefinition(
        name = model.skinNames.firstOrNull()?.takeUnless { it.isNullOrBlank() } ?: "Imported skeleton",
        bones = joints.map { it.bone },
    )

    /** Rebuilds each local transform from the captured rest matrix, so edits never accumulate drift. */
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
        // Filament Animator updates only skin matrices here. No animation clip is applied, so
        // manually authored local bone transforms remain intact.
        model.animator.updateBoneMatrices()
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
            if (model.skinCount == 0) return null
            val transformManager = model.engine.transformManager
            val entities = (0 until model.skinCount).flatMap { skin ->
                model.getJointsAt(skin).toList()
            }.distinct()
            if (entities.isEmpty()) return null

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
                        ?: "joint-${entities.indexOf(cursor).coerceAtLeast(0)}"
                    parts += name
                    cursor = parentEntity(cursor)
                }
                return parts.asReversed()
            }

            fun toId(parts: List<String>) = parts.joinToString("/") { part ->
                part.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "joint" }
            }

            val ids = entities.associateWith { toId(pathParts(it)) }
            val bones = entities.mapIndexed { index, entity ->
                val names = pathParts(entity)
                var parent = parentEntity(entity)
                while (parent != 0 && parent !in ids) parent = parentEntity(parent)
                RigBone(
                    id = ids.getValue(entity),
                    name = names.lastOrNull() ?: "Joint ${index + 1}",
                    parentId = ids[parent],
                )
            }
            val boneByEntity = entities.zip(bones).toMap()
            val joints = entities.mapIndexed { index, entity ->
                val matrix = transformManager.getTransform(transformManager.getInstance(entity), FloatArray(16))
                val baseBone = boneByEntity.getValue(entity)
                // Duplicate names under identical paths are uncommon, but still receive a stable
                // source-order suffix rather than silently sharing pose state.
                val sameIdBefore = bones.take(index).count { it.id == baseBone.id }
                val bone = if (sameIdBefore == 0) baseBone else baseBone.copy(id = "${baseBone.id}~${sameIdBefore + 1}")
                Joint(bone, entity, matrix)
            }
            return FilamentRigRuntime(model, joints)
        }
    }
}
