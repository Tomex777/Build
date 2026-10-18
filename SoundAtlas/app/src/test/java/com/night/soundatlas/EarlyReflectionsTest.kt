package com.night.soundatlas

import org.junit.Assert.*
import org.junit.Test

class EarlyReflectionsTest {
    @Test fun longStoneWallProducesValidDelayedSingleBounce() {
        val w=World("reflective",80f,80f,V2(20f,20f))
        w.walls+=Wall(V2(0f,10f),V2(55f,10f),Material.STONE)
        val rays=EarlyReflections.solve(w,V2(30f,20f))
        assertEquals(1,rays.size)
        val ray=rays.single()
        assertEquals(0,ray.wallIndex)
        assertTrue(ray.delayFrames>1000)
        assertTrue(ray.distanceMeters>10f)
        assertTrue(ray.gain in .001f..1f)
    }
    @Test fun openDoorIsNotReflective() {
        val w=World("open",80f,80f,V2(20f,20f))
        w.walls+=Wall(V2(0f,10f),V2(55f,10f),Material.OPEN)
        assertTrue(EarlyReflections.solve(w,V2(30f,20f)).isEmpty())
    }
    @Test fun curtainHasWeakerReflectionThanStone() {
        val w=World("material",80f,80f,V2(20f,20f))
        val wall=Wall(V2(0f,10f),V2(55f,10f),Material.STONE)
        w.walls+=wall
        val stone=EarlyReflections.solve(w,V2(30f,20f)).first().gain
        wall.material=Material.CURTAIN
        val curtain=EarlyReflections.solve(w,V2(30f,20f)).first().gain
        assertTrue(stone>curtain)
    }
    @Test fun otherWallBlocksSpecularRay() {
        val w=World("occlusion",80f,80f,V2(20f,20f))
        w.walls+=Wall(V2(0f,10f),V2(55f,10f),Material.STONE)
        w.walls+=Wall(V2(28f,0f),V2(28f,60f),Material.STONE)
        val reflections=EarlyReflections.solve(w,V2(30f,20f))
        assertFalse(reflections.any{it.wallIndex==0})
    }
    @Test fun materialSpecificDirectOcclusionChangesSpectrum() {
        val w=World("materials",80f,80f,V2(15f,20f))
        val source=Emitter(1,SoundKind.BELL,V2(55f,20f))
        w.walls+=Wall(V2(35f,5f),V2(35f,35f),Material.STONE)
        val stone=AcousticSolver.evaluate(w,source)
        w.walls[0].material=Material.GLASS
        val glass=AcousticSolver.evaluate(w,source)
        assertTrue(glass.lowPassHz>stone.lowPassHz)
        assertTrue(glass.directGain>stone.directGain)
    }
}
