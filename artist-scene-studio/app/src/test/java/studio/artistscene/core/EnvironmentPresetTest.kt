package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentPresetTest {
    private fun project() = SceneProject(
        id = "environment-check",
        name = "Environment",
        actors = listOf(Actor("human", "Human", ActorKind.CHARACTER)),
    )

    @Test
    fun defaultEnvironmentHasSeparatingGroundAndDaylightSky() {
        assertEquals(EnvironmentPreset.DAYLIGHT, project().world.environmentPreset)
        val ground = EnvironmentPreset.DAYLIGHT.groundHex
        assertTrue(ground.matches(Regex("^#[0-9A-F]{6}$")))
        // Deliberate luminance separation from the lightly colored MakeHuman body.
        val channels = (1..5 step 2).map { ground.substring(it, it + 2).toInt(16) }
        assertTrue(channels.maxOrNull()!! < 145)
        assertTrue(EnvironmentPreset.DAYLIGHT.horizonRgb != EnvironmentPreset.DAYLIGHT.zenithRgb)
        assertTrue(EnvironmentPreset.entries.all { it.roughness in 0f..1f })
    }

    @Test
    fun environmentEditsSurviveSaveAndSupportUndoRedo() {
        val state = SceneEditorState(project())
        val sunset = state.setEnvironmentPreset(EnvironmentPreset.SUNSET)
        val hidden = sunset.setGroundVisible(false)
        assertTrue(sunset.canUndo)
        assertEquals(EnvironmentPreset.SUNSET, sunset.project.world.environmentPreset)
        assertFalse(hidden.project.world.groundEnabled)

        val restored = SceneProjectCodec.decode(SceneProjectCodec.encode(hidden.project))
        assertEquals(EnvironmentPreset.SUNSET, restored.world.environmentPreset)
        assertFalse(restored.world.groundEnabled)

        val undoGround = hidden.undo()
        assertTrue(undoGround.project.world.groundEnabled)
        val undoPreset = undoGround.undo()
        assertEquals(EnvironmentPreset.DAYLIGHT, undoPreset.project.world.environmentPreset)
        val redoBoth = undoPreset.redo().redo()
        assertEquals(hidden.project.world, redoBoth.project.world)
        assertEquals(state, state.setEnvironmentPreset(EnvironmentPreset.DAYLIGHT))
    }

    @Test
    fun olderProjectsWithoutEnvironmentPresetStillDecode() {
        val oldScene = """{"schemaVersion":6,"id":"prior-scene","name":"Older file",
            "world":{"backgroundHex":"#20242B","groundEnabled":true,"gridEnabled":true}}"""
        val loaded = SceneProjectCodec.decode(oldScene)
        assertEquals(EnvironmentPreset.DAYLIGHT, loaded.world.environmentPreset)
        assertTrue(loaded.world.groundEnabled)
    }
}
