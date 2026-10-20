package com.night.soundatlas

import org.junit.Assert.*
import org.junit.Test

class NavigationTest {
    @Test fun directPathRemainsOneWaypoint() {
        val w=World("Field",80f,80f,V2(10f,10f))
        val route=AtlasNavigator.route(w,V2(10f,10f),V2(20f,30f))
        assertEquals(1,route.size)
        assertEquals(V2(20f,30f),route.last())
    }
    @Test fun pathFindsDoorRatherThanWalkingThroughConcrete() {
        val w=World("Door",70f,70f,V2(35f,15f))
        w.walls+=Wall(V2(4f,35f),V2(30f,35f),Material.STONE)
        w.walls+=Wall(V2(40f,35f),V2(65f,35f),Material.STONE)
        val route=AtlasNavigator.route(w,V2(15f,15f),V2(55f,55f))
        assertTrue("Must find path through the 10m door",route.isNotEmpty())
        val points=listOf(V2(15f,15f))+route
        for(i in 0 until points.size-1)
            assertTrue("route cannot cross any solid segment",
                AtlasNavigator.traversable(w,points[i],points[i+1]))
    }
    @Test fun wallOccludesDirectPath() {
        val w=World("Blocked",60f,60f,V2(10f,30f))
        w.walls+=Wall(V2(30f,2f),V2(30f,58f),Material.METAL)
        assertFalse(AtlasNavigator.traversable(w,V2(10f,30f),V2(50f,30f)))
    }
}
