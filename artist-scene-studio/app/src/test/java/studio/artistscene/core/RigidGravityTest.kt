package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RigidGravityTest {
    private fun project(
        y: Float = 2f, enabled: Boolean = true,
        bounce: Float = .1f,
    ) = SceneProject(
        id = "physics", name = "Gravity",
        world = WorldSettings(gravityEnabled = enabled),
        actors = listOf(
            Actor("ball", "Prop", ActorKind.PROP,
                transform = Transform(position = Vec3(0f, y, 0f)),
                physics = RigidPhysicsSettings(dynamic = true, bounciness = bounce)),
            Actor("sun", "Light", ActorKind.LIGHT, physics = RigidPhysicsSettings(dynamic = true)),
        ),
    )

    @Test fun gravitationalAccelerationIncreasesFallSpeedAndStopsAtFloor() {
        var scene = project(y = 3f, bounce = 0f)
        val sim = RigidGravity(scene)
        val after = mutableListOf<Float>()
        repeat(100) {
            val updates = sim.step(scene, 1f / 60f)
            updates["ball"]?.let { y ->
                scene = scene.copy(actors = scene.actors.map {
                    if (it.id == "ball") it.copy(transform = it.transform.copy(
                        position = it.transform.position.copy(y = y),
                    )) else it
                })
            }
            after.add(scene.actors.first().transform.position.y)
        }
        assertTrue(after[20] < after[10])
        assertEquals(0f, after.last(), .001f)
        assertTrue("Static lights cannot move", scene.actors.last().transform.position == Vec3())
    }

    @Test fun earthGravityCanBeTurnedOffWithoutDroppingActors() {
        val scene = project(enabled = false)
        val sim = RigidGravity(scene)
        assertTrue(sim.step(scene, 1f / 60f).isEmpty())
    }

    @Test fun bouncingBodyRemainsFiniteAndNeverFallsThroughFloor() {
        var scene = project(y = 1f, bounce = .6f)
        val sim = RigidGravity(scene)
        repeat(300) {
            val heights = sim.step(scene, 1f / 30f)
            heights["ball"]?.let { height ->
                assertTrue(height.isFinite() && height >= 0f)
                scene = scene.copy(actors = scene.actors.map {
                    if (it.id == "ball") it.copy(transform = it.transform.copy(
                        position = it.transform.position.copy(y = height),
                    )) else it
                })
            }
        }
        assertTrue(scene.actors.first().transform.position.y < .05f)
    }

    @Test fun physicsPreviewDoesNotPolluteUndoAndPauseCommitsOnce() {
        val initial = SceneEditorState(project(y = 3f))
        val changed = initial.previewGravityPositions(mapOf("ball" to 1.2f))
        assertFalse(changed.canUndo)
        assertEquals(1.2f, changed.selectedActor!!.transform.position.y)
        val saved = changed.commitTransformGesture(initial.project)
        assertTrue(saved.canUndo)
        assertEquals(initial.project, saved.undo().project)
    }

    @Test fun physicsSettingsAreUndoableAndSurviveProjectCodec() {
        val initial = SceneEditorState(project(y = 5f, enabled = false))
        val updated = initial.setGravityWorld(enabled = true, metersPerSecondSquared = 1.62f)
            .setSelectedRigidPhysics(bounciness = .55f)
        assertEquals(1.62f, updated.project.world.gravityMetersPerSecondSquared)
        assertEquals(.55f, updated.selectedActor!!.physics.bounciness)
        assertEquals(updated.project, SceneProjectCodec.decode(SceneProjectCodec.encode(updated.project)))
        assertEquals(initial.project, updated.undo().undo().project)
        assertEquals(initial, initial.setGravityWorld(metersPerSecondSquared = Float.NaN))
    }
}
