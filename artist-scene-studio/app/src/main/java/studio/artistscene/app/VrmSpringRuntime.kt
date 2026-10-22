package studio.artistscene.app

import android.content.Context
import android.opengl.Matrix
import android.util.Log
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.engine
import org.json.JSONObject
import studio.artistscene.core.Actor
import studio.artistscene.core.AssetStorage
import studio.artistscene.core.SpringMotion
import studio.artistscene.core.RigPoseRenderSafety
import studio.artistscene.core.Vec3
import kotlin.math.sqrt

/**
 * Animates real VRoid skinned HairJoint/J_Sec nodes with spring/gravity each Filament
 * frame. Never replaces character geometry or actor transforms, and never persists
 * transient spring offsets in the project. The GLB spring manifest is extracted
 * from the model's own VRM.secondaryAnimation authored by the original creator.
 */
internal class VrmSpringRuntime private constructor(
    private val model: ModelInstance,
    private val roots: List<SpringRoot>,
) {
    private data class SpringRoot(
        val entity: Int,
        val instance: Int,
        val parentEntity: Int,
        val restMatrix: FloatArray,
        val scratch: FloatArray,
        val boundPoseId: String?,
        val restTip: Vec3,
        val motion: SpringMotion,
        val category: String,
        // A disabled spring must restore its actual bind transform once.
        // Otherwise the last simulated angle remains frozen on the skinned mesh.
        var wasEnabled: Boolean = false,
    )

    private var lastFrameNs = 0L
    private var suspended = false
    // Emit diagnostic evidence only when the user's actual live settings change.
    // This catches stale Compose-to-native frame callbacks without log spam.
    private var lastObservedGravity = Float.NaN
    private var lastObservedWind = Float.NaN
    private var lastObservedHairEnabled: Boolean? = null
    private val manager get() = model.engine.transformManager
    private val reusableParent = FloatArray(16)
    private val reusableRoot = FloatArray(16)

    private fun restoreSpring(root: SpringRoot, actor: Actor): Boolean {
        if (!manager.hasComponent(root.entity)) return false
        val matrix = root.scratch
        System.arraycopy(root.restMatrix, 0, matrix, 0, 16)
        val authored = RigPoseRenderSafety.jointOffset(root.boundPoseId?.let { actor.rig?.joints?.get(it) })
        Matrix.rotateM(matrix, 0, authored.x, 1f, 0f, 0f)
        Matrix.rotateM(matrix, 0, authored.y, 0f, 1f, 0f)
        Matrix.rotateM(matrix, 0, authored.z, 0f, 0f, 1f)
        manager.setTransform(root.instance, matrix)
        root.motion.reset()
        root.wasEnabled = false
        return true
    }

    val hairCount: Int = roots.count { it.category == "hair" }
    val fabricCount: Int = roots.count { it.category == "fabric" }

    fun advance(actor: Actor, nanoTime: Long) {
        val appearance = actor.appearance
        if (lastObservedGravity != appearance.hairGravityStrength ||
            lastObservedWind != appearance.hairWindStrength ||
            lastObservedHairEnabled != appearance.hairPhysicsEnabled
        ) {
            lastObservedGravity = appearance.hairGravityStrength
            lastObservedWind = appearance.hairWindStrength
            lastObservedHairEnabled = appearance.hairPhysicsEnabled
            Log.i("MiseHairPhysics", "Live renderer controls actor=${actor.id} hair=${appearance.hairPhysicsEnabled} gravity=${appearance.hairGravityStrength} wind=${appearance.hairWindStrength} roots=$hairCount")
        }
        val noPhysics = !appearance.hairPhysicsEnabled && !appearance.fabricPhysicsEnabled
        if (!actor.visible || actor.animation.playing || noPhysics) {
            if (!suspended) {
                // Unlike invisible actors or authored clip playback, switching
                // BOTH physics categories off must restore real joint transforms
                // immediately. Resetting numerical spring state alone would
                // leave the rendered mesh in its last simulated bend.
                var restored = false
                roots.forEach { root ->
                    if (noPhysics && actor.visible && !actor.animation.playing &&
                        root.wasEnabled && restoreSpring(root, actor)) {
                        restored = true
                    } else {
                        root.motion.reset()
                        root.wasEnabled = false
                    }
                }
                if (restored) model.animator.updateBoneMatrices()
                suspended = true
            }
            lastFrameNs = nanoTime
            return
        }
        if (suspended) {
            roots.forEach { root -> root.motion.reset() }
            suspended = false
        }
        val dt = if (lastFrameNs == 0L) 1f / 60f else
            ((nanoTime - lastFrameNs).toDouble() / 1_000_000_000.0).toFloat()
        lastFrameNs = nanoTime
        if (dt <= 0f || !dt.isFinite()) return
        // Android 8 emulators and budget phones can miss a 150ms frame while
        // loading textures or handling a sheet gesture. Resetting the springs on
        // EVERY slow frame makes wind/gravity look permanently disabled. The
        // integrator already bounds each step to 1/30s, so advance on a bounded
        // timestep; only reset after a genuine background-sized pause.
        if (dt > 2f) {
            roots.forEach { it.motion.reset() }
            return
        }
        val safeDt = dt.coerceAtMost(1f / 30f)
        var changed = false
        roots.forEach { root ->
            val isHair = root.category == "hair"
            val enabled = if (isHair) appearance.hairPhysicsEnabled
                else appearance.fabricPhysicsEnabled
            if (!enabled) {
                // The other category may still be playing, so the global
                // suspend branch is not entered. Restore this spring's joint
                // exactly once rather than leaving a bent strand/skirt frozen.
                if (root.wasEnabled && restoreSpring(root, actor)) {
                    changed = true
                } else {
                    root.motion.reset()
                    root.wasEnabled = false
                }
                return@forEach
            }
            root.wasEnabled = true
            if (!manager.hasComponent(root.parentEntity) ||
                !manager.hasComponent(root.entity)) return@forEach
            // The parent includes current head/chest/body rotation. Converting
            // world-down to local frame means a character leaning changes how
            // gravity pulls its hair, even when it is standing still.
            val parent = manager.getWorldTransform(
                manager.getInstance(root.parentEntity), reusableParent,
            )
            val gravity = localGravity(parent)
            val world = manager.getWorldTransform(root.instance, reusableRoot)
            val pivot = Vec3(world[12], world[13], world[14])
            val wobble = root.motion.advance(
                dtSeconds = safeDt,
                gravityLocal = gravity,
                pivotWorld = pivot,
                // Hair-strength and breeze controls are scoped to actual hair.
                // Fabric dynamics has its own toggle: changing a hairstyle
                // must not move the skirt, shirt, or the lower-body proof frame.
                gravityPower = if (isHair) appearance.hairGravityStrength else 1f,
                wind = if (isHair) appearance.hairWindStrength else 0f,
            )
            // Rebuild from bind pose every frame; NEVER compound the previous
            // frame's offset, which causes permanent distortion and skin drift.
            val matrix = root.scratch
            System.arraycopy(root.restMatrix, 0, matrix, 0, 16)
            val authored = RigPoseRenderSafety.jointOffset(root.boundPoseId?.let { actor.rig?.joints?.get(it) })
            Matrix.rotateM(matrix, 0, authored.x + wobble.x, 1f, 0f, 0f)
            Matrix.rotateM(matrix, 0, authored.y, 0f, 1f, 0f)
            Matrix.rotateM(matrix, 0, authored.z + wobble.z, 0f, 0f, 1f)
            manager.setTransform(root.instance, matrix)
            changed = true
        }
        if (changed) model.animator.updateBoneMatrices()
    }

    /** Parent basis may contain avatar scaling; normalize columns before projection. */
    private fun localGravity(matrix: FloatArray): Vec3 {
        fun projection(column: Int): Float {
            val x = matrix[column * 4]
            val y = matrix[column * 4 + 1]
            val z = matrix[column * 4 + 2]
            val length = sqrt(x * x + y * y + z * z).coerceAtLeast(.000001f)
            return -y / length
        }
        return Vec3(projection(0), projection(1), projection(2))
    }

    companion object {
        fun fromAsset(
            context: Context,
            asset: studio.artistscene.core.AssetReference,
            model: ModelInstance,
            definition: studio.artistscene.core.RigDefinition?,
        ): VrmSpringRuntime? {
            if (asset.storage != AssetStorage.BUNDLED) return null
            val path = when (asset.assetId) {
                PrototypeScene.ANIME_ASSET_ID -> "models/mise_anime.spring.json"
                PrototypeScene.ANIME_FEMALE_ASSET_ID -> "models/mise_anime_female.spring.json"
                else -> return null
            }
            val data = try {
                JSONObject(context.assets.open(path).bufferedReader().use { it.readText() })
            } catch (error: Exception) {
                Log.e("MiseHairPhysics", "Spring metadata unavailable for $path", error)
                return null
            }
            val manager = model.engine.transformManager
            // VRM springs are authored glTF NODES, not necessarily vertices
            // weighted in the CURRENT skinned mesh. Android's compact GPU
            // palettes may legitimately remove spring-root nodes from
            // getJointsAt(), even though the complete GLB hierarchy remains.
            // Resolve against all FilamentAsset nodes first; the skin-only
            // lookup caused live hair controls to produce no rendered motion.
            val allNodes = model.asset.entities
                .filter { manager.hasComponent(it) }
            val joints = allNodes
                .associateBy { model.asset.getName(it).orEmpty() }
            val roots = buildList {
                val chains = data.getJSONArray("chains")
                for (i in 0 until chains.length()) {
                    val entry = chains.getJSONObject(i)
                    val entity = joints[entry.getString("name")] ?: continue
                    if (!manager.hasComponent(entity)) continue
                    val instance = manager.getInstance(entity)
                    val parent = manager.getParent(instance)
                    if (parent == 0 || !manager.hasComponent(parent)) continue
                    val tip = entry.getJSONArray("tip")
                    val lengthSquared = (0..2).sumOf { tip.getDouble(it) * tip.getDouble(it) }
                    if (lengthSquared < .000001) continue
                    val rest = manager.getTransform(instance, FloatArray(16))
                    val vec = Vec3(tip.getDouble(0).toFloat(),
                        tip.getDouble(1).toFloat(), tip.getDouble(2).toFloat())
                    add(SpringRoot(
                        entity = entity, instance = instance, parentEntity = parent,
                        restMatrix = rest, scratch = FloatArray(16),
                        boundPoseId = definition?.bones?.firstOrNull { it.name == entry.getString("name") }?.id,
                        restTip = vec,
                        motion = SpringMotion(
                            vec, entry.getDouble("stiffness").toFloat(),
                            entry.getDouble("drag").toFloat(),
                        ),
                        category = entry.getString("category"),
                    ))
                }
            }
            if (roots.isEmpty()) {
                Log.e("MiseHairPhysics", "No authored spring roots bound: asset=${asset.assetId} nodes=${allNodes.size}")
                return null
            }
            Log.i("MiseHairPhysics", "Real VRM spring rig: ${roots.count { it.category == "hair" }} hair and ${roots.count { it.category == "fabric" }} fabric bones (from ${allNodes.size} glTF nodes)")
            return VrmSpringRuntime(model, roots)
        }
    }
}
