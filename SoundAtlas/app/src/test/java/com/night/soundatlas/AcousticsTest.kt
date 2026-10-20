package com.night.soundatlas

import org.junit.Assert.*
import org.junit.Test

class AcousticsTest {
    @Test fun lineIntersectionDoesNotMistakeParallelWalls() {
        assertTrue(AcousticSolver.intersects(V2(0f,0f),V2(10f,10f),
            V2(0f,10f),V2(10f,0f)))
        assertFalse(AcousticSolver.intersects(V2(0f,0f),V2(10f,0f),
            V2(0f,3f),V2(10f,3f)))
    }
    @Test fun solidWallMufflesAndReducesDirectGain() {
        val world=World("Test",100f,100f,V2(20f,50f))
        val source=Emitter(1,SoundKind.BELL,V2(80f,50f))
        val open=AcousticSolver.evaluate(world,source)
        world.walls+=Wall(V2(50f,20f),V2(50f,80f),Material.STONE)
        val closed=AcousticSolver.evaluate(world,source)
        assertTrue(closed.directGain<open.directGain*.2f)
        assertTrue(closed.lowPassHz<open.lowPassHz)
        assertEquals(1,closed.occlusions)
    }
    @Test fun openingDoesNotMuffleSound() {
        val world=World("Test",100f,100f,V2(20f,50f))
        val source=Emitter(1,SoundKind.BELL,V2(80f,50f))
        val original=AcousticSolver.evaluate(world,source)
        world.walls+=Wall(V2(50f,20f),V2(50f,80f),Material.OPEN)
        val open=AcousticSolver.evaluate(world,source)
        assertEquals(original.directGain,open.directGain,0.00001f)
        assertEquals(original.lowPassHz,open.lowPassHz,0.00001f)
    }
    @Test fun movementAltersDistanceAndDirection() {
        val world=World("Test",100f,100f,V2(50f,50f))
        val source=Emitter(1,SoundKind.BIRDS,V2(20f,30f))
        val before=AcousticSolver.evaluate(world,source)
        world.listener=V2(70f,35f)
        val after=AcousticSolver.evaluate(world,source)
        assertNotEquals(before.directGain,after.directGain)
        assertNotEquals(before.pan,after.pan)
    }
    @Test fun presetsHavePlayableSoundSourcesAndValidBounds() {
        repeat(AtlasPresets.titles.size) {
            val world=AtlasPresets.create(it)
            assertTrue(world.name.isNotBlank())
            assertTrue(world.sounds.size>=4)
            assertTrue(world.width>0 && world.height>0)
            assertTrue(world.sounds.all { emitter->
                emitter.at.x in 0f..world.width && emitter.at.y in 0f..world.height
            })
        }
    }
    @Test fun audioSnapshotIsolatedFromWorldEditorMutations() {
        val world=AtlasPresets.create(0)
        val snapshot=world.copyForAudio()
        world.sounds.first().at.x=91f
        assertNotEquals(world.sounds.first().at.x,snapshot.sounds.first().at.x)
        world.walls.first().a.x=222f
        assertNotEquals(world.walls.first().a.x,snapshot.walls.first().a.x)
    }
}
