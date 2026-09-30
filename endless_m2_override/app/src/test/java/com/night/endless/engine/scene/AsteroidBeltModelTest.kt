package com.night.endless.engine.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsteroidBeltModelTest {
    @Test
    fun deterministicSamplesStayInsideTheMainBeltBand() {
        val first = AsteroidBeltModel.build()
        val second = AsteroidBeltModel.build()

        assertEquals(AsteroidBeltModel.DEFAULT_COUNT, first.size)
        assertEquals(first, second)
        assertTrue(first.all { it.radius in AsteroidBeltModel.INNER_RADIUS..AsteroidBeltModel.OUTER_RADIUS })
        assertTrue(first.all { it.orbitPeriodDays > 1000.0 })
        assertTrue(first.all { kotlin.math.abs(Math.toDegrees(it.inclinationRad)) <= 4.5 })
    }

    @Test
    fun particlesAdvanceOnTheSharedUniverseClock() {
        val samples = AsteroidBeltModel.build(16)
        val atStart = AsteroidBeltModel.positions(samples, 0.0)
        val later = AsteroidBeltModel.positions(samples, 200.0 * 86400.0)

        assertEquals(atStart.size, later.size)
        assertNotEquals(atStart[0], later[0])
        assertNotEquals(atStart[2], later[2])
    }
}
