package com.night.offlinegames

import org.junit.Assert.*
import org.junit.Test

class SharkEngineTest {
    @Test fun generatedDiveContainsPreyAndDangerWithoutNetwork() {
        val initial = SharkEngine.newGame()
        assertEquals(25, initial.creatures.size)
        assertTrue(initial.creatures.any { it.kind == SeaKind.PREY })
        assertTrue(initial.creatures.any { it.kind == SeaKind.PREDATOR })
        assertEquals(100f, initial.health, .001f)
    }

    @Test fun swimmingFollowsTouchAndRespectsWorldBoundaries() {
        var state = SharkEngine.newGame()
        repeat(60) { state = SharkEngine.step(state, 999f, 999f, .05f) }
        assertTrue(state.x <= SharkEngine.worldWidth - 19f)
        assertTrue(state.y <= SharkEngine.worldHeight - 45f)
        assertTrue(state.seconds > 0f)
    }

    @Test fun touchingSmallPreyAddsScoreAndRestoresHealth() {
        val prey = SeaCreature(1, SeaKind.PREY, 180f, 330f, 0f, 9f, 12)
        val state = SharkState(health = 60f, creatures = listOf(prey), nextId = 2)
        val next = SharkEngine.step(state, null, null, .016f)
        assertTrue(next.points >= 12)
        assertEquals(1, next.eaten)
        assertTrue(next.health > state.health)
    }

    @Test fun hazardsCauseDamageWithCooldownAndGameEndsAtZero() {
        val jelly = SeaCreature(1, SeaKind.JELLYFISH, 180f, 330f, 0f, 16f, 0)
        val state = SharkState(health = 11f, creatures = listOf(jelly), nextId = 2)
        val dead = SharkEngine.step(state, null, null, .016f)
        assertTrue(dead.finished)
        assertEquals(0f, dead.health, .001f)
        assertEquals(dead, SharkEngine.step(dead, 180f, 180f, 1f))
    }

    @Test fun localResumeRoundTripsAndRejectsInvalidSnapshots() {
        val state = SharkEngine.step(SharkEngine.newGame(8), 250f, 310f, .05f)
        assertEquals(state, SharkEngine.decode(SharkEngine.encode(state)))
        assertNull(SharkEngine.decode("garbage"))
        assertNull(SharkEngine.decode(null))
    }
}
