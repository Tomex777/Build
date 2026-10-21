package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceWalkingPhysicsTest {
    @Test fun walksAcrossNormalGentleInclines() {
        val slope: (Double, Double) -> Double = { x, z -> x * .15 + z * .10 }
        val walked = SurfaceWalkingPhysics.move(0.0, 0.0, .00005, .00005, slope)
        assertEquals(.00005, walked.first, 1e-12)
        assertEquals(.00005, walked.second, 1e-12)
    }

    @Test fun cannotClimbOrDropOffKilometreHeightCliffs() {
        val wall: (Double, Double) -> Double = { x, _ -> if (x > 0.0) .007 else 0.0 }
        val walked = SurfaceWalkingPhysics.move(-.00002, 0.0, .00009, 0.0, wall)
        assertEquals(-.00002, walked.first, 0.0)
        assertEquals(0.0, walked.second, 0.0)
        val down = SurfaceWalkingPhysics.move(.00008, 0.0, -.00009, 0.0, wall)
        assertEquals(.00008, down.first, 0.0)
    }

    @Test fun diagonalCollisionSlidesAlongRidge() {
        val wall: (Double, Double) -> Double = { x, _ -> if (x > 0.0) .01 else 0.0 }
        val walked = SurfaceWalkingPhysics.move(-.00001, 0.0, .00006, .00004, wall)
        assertEquals(-.00001, walked.first, 0.0)
        assertEquals(.00004, walked.second, 1e-10)
    }

    @Test fun sweptCollisionCannotTunnelThroughNarrowRidge() {
        // A 72cm-to-1.6m-wide ridge with the same elevation on both sides:
        // endpoint-only physics crossed this obstacle when a gesture skipped frames.
        val ridge: (Double, Double) -> Double = { x, _ ->
            if (x >= .00032 && x <= .00056) .005 else 0.0
        }
        val moved = SurfaceWalkingPhysics.move(0.0, 0.0, .0014, 0.0, ridge)
        assertTrue("walker tunneled through a ridge", moved.first < .00032)
        assertTrue("walker failed to approach ridge", moved.first >= .0002)
    }

    @Test fun diagonalSweepKeepsProgressParallelToWall() {
        val ridge: (Double, Double) -> Double = { x, _ ->
            if (x >= .00032 && x <= .00056) .005 else 0.0
        }
        val moved = SurfaceWalkingPhysics.move(0.0, 0.0, .0014, .0010, ridge)
        assertTrue("crossed narrow wall", moved.first < .00032)
        assertEquals("lost parallel movement", .0010, moved.second, 1e-10)
    }

    @Test fun largeInjectedMotionStillCannotTunnelThroughNarrowCliffs() {
        // The 96-sample bound must limit total travel, not silently turn
        // 30cm steps into 250m leaps on an outlier touch displacement.
        val ridge: (Double, Double) -> Double = { x, _ ->
            if (x in .00032..00056) .005 else 0.0
        }
        val moved = SurfaceWalkingPhysics.move(0.0, 0.0, 2.0, 0.0, ridge)
        assertTrue("large delta skipped the ridge", moved.first < .00032)
        assertTrue("walk never approached ridge", moved.first >= .0002)
    }

    @Test fun flatOutlierMovementIsDistanceBounded() {
        val position = SurfaceWalkingPhysics.move(0.0, 0.0, 2.0, 0.0) { _, _ -> 0.0 }
        assertTrue(position.first > 0.0)
        assertTrue("large input moved more than 28.8 metres", position.first <= .0096 + 1e-12)
    }

    @Test fun rejectsInvalidTerrainAndKeepsBoundaries() {
        val flat: (Double, Double) -> Double = { _, _ -> 0.0 }
        val bounded = SurfaceWalkingPhysics.move(7.999, 7.999, 1.0, 1.0, flat)
        assertEquals(8.0, bounded.first, 0.0)
        assertEquals(8.0, bounded.second, 0.0)
        assertEquals(1.0 to 2.0, SurfaceWalkingPhysics.move(
            1.0, 2.0, Double.NaN, 0.0, flat
        ))
        assertEquals(0.0 to 0.0, SurfaceWalkingPhysics.move(
            0.0, 0.0, .01, .01
        ) { _, _ -> Double.NaN })
    }
}
