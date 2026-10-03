package studio.artistscene.core

import org.junit.Assert.*
import org.junit.Test

class MechanicalPartsTest {
    private fun editor(asset: String) = SceneEditorState(SceneProject(id = "parts", name = "Parts", actors = listOf(
        Actor(id = "vehicle", name = "Vehicle", kind = ActorKind.VEHICLE, asset = AssetReference(asset, "models/test.glb")),
    )))

    @Test fun steeringAndWheelsUseSeparateAxesAndSurviveHistoryAndSaving() {
        var state = editor("starter.mise.bicycle")
        val bones = state.selectedActor!!.mechanicalParts().map { RigBone(it.name, it.name) }
        state = state.withDiscoveredRig("vehicle", RigDefinition(bones = bones))
        val before = state.project
        state = state.setRigJointRotation("Front steering", Vec3(45f, 90f, 30f))
        assertEquals(Vec3(y = 60f), state.selectedActor!!.rig!!.joints["Front steering"])
        assertEquals(before, state.undo().project)
        assertEquals(state.project, state.undo().redo().project)
        state = state.setRigJointRotation("Front wheel", Vec3(80f, 40f, 20f))
        assertEquals(Vec3(x = 80f), state.selectedActor!!.rig!!.joints["Front wheel"])
        assertEquals(Vec3(y = 60f), state.selectedActor!!.rig!!.joints["Front steering"])
        val restored = SceneProjectCodec.decode(SceneProjectCodec.encode(state.project))
        assertEquals(state.project, restored)
        assertEquals(state, state.setRigJointRotation("Front wheel", Vec3(Float.NaN, 0f, 0f)))
        val locked = state.copy(project = state.project.copy(actors = listOf(state.selectedActor!!.copy(locked = true))))
        assertEquals(locked, locked.setRigJointRotation("Front wheel", Vec3(x = 10f)))
    }

    @Test fun doorsOpenOutwardsAndVehiclePosesCanBeKeyed() {
        var state = editor("starter.mise.car")
        state = state.withDiscoveredRig("vehicle", RigDefinition(bones = state.selectedActor!!.mechanicalParts().map { RigBone(it.name, it.name) }))
        state = state.keySelectedPose(0f)
            .setRigJointRotation("Left door", Vec3(y = 100f))
            .setRigJointRotation("Right door", Vec3(y = -100f))
            .keySelectedPose(2f)
        assertEquals(Vec3(y = 75f), state.selectedActor!!.rig!!.joints["Left door"])
        assertEquals(Vec3(y = -75f), state.selectedActor!!.rig!!.joints["Right door"])
        val middle = state.project.evaluateTimeline(1f).actors.single()
        assertEquals(37.5f, middle.rig!!.joints.getValue("Left door").y, .001f)
        assertEquals(-37.5f, middle.rig!!.joints.getValue("Right door").y, .001f)
    }
}
