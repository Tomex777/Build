package com.night.endless.engine.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class SurfaceJoystickMathTest {
    @Test fun directionsAndNeutralDeadZone() {
        assertEquals(0f to 0f, SurfaceJoystickMath.fromDisplacement(0f, 0f, 50f))
        assertEquals(0f to 0f, SurfaceJoystickMath.fromDisplacement(1f, 1f, 50f))
        val forward = SurfaceJoystickMath.fromDisplacement(0f, -50f, 50f)
        assertEquals(1f, forward.first, 0.0001f)
        assertEquals(0f, forward.second, 0.0001f)
        val right = SurfaceJoystickMath.fromDisplacement(50f, 0f, 50f)
        assertEquals(0f, right.first, 0.0001f)
        assertEquals(1f, right.second, 0.0001f)
    }

    @Test fun diagonalSpeedIsNeverFasterAndStrongerPushMovesMore() {
        val diagonal = SurfaceJoystickMath.fromDisplacement(100f, -100f, 50f)
        assertEquals(1f, hypot(diagonal.first, diagonal.second), 0.0001f)
        val mid = SurfaceJoystickMath.fromDisplacement(0f, -22f, 50f)
        assertTrue(mid.first > 0.1f && mid.first < 0.6f)
    }

    @Test fun thumbFollowsRealFingerPositionInsteadOfFakeDeadZonePosition() {
        assertEquals(0f to 0f, SurfaceJoystickMath.knobOffset(0f, 0f, 50f))
        assertEquals(.5f to 0f, SurfaceJoystickMath.knobOffset(25f, 0f, 50f))
        val diagonal = SurfaceJoystickMath.knobOffset(100f, 100f, 50f)
        assertEquals(1f, hypot(diagonal.first, diagonal.second), 0.0001f)
        assertEquals(0f to 0f, SurfaceJoystickMath.knobOffset(Float.NaN, 0f, 50f))
        assertEquals(0f to 0f, SurfaceJoystickMath.knobOffset(10f, 5f, 0f))
    }

    @Test fun stickAndRightSideLookHaveDisjointStartZones() {
        val d = 2f
        val width = 1080
        val height = 2400
        val centerX = SurfaceJoystickMath.centerX(d)
        val centerY = SurfaceJoystickMath.centerY(height, d)
        assertEquals(184f, centerX, 0f)
        assertEquals(2196f, centerY, 0f)
        assertTrue(SurfaceJoystickMath.isInMoveZone(centerX, centerY, height, d))
        assertTrue(SurfaceJoystickMath.isInMoveZone(centerX, centerY - 50f * d, height, d))
        assertTrue(!SurfaceJoystickMath.isInMoveZone(width * .65f, height * .5f, height, d))
        assertTrue(!SurfaceJoystickMath.isInLookZone(centerX, width))
        assertTrue(SurfaceJoystickMath.isInLookZone(width * .63f, width))
        assertTrue(!SurfaceJoystickMath.isInLookZone(width * .38f, width))
    }

    @Test fun frameTimeControlsRealDistanceAndClampsAfterResume() {
        val oneSecondAt60Hz = (1..60).sumOf {
            SurfaceJoystickMath.travelWorldUnits(1.0 / 60)
        }
        assertEquals(3.0 / 3000, oneSecondAt60Hz, 0.00000001)
        assertEquals(.05 * 3.0 / 3000, SurfaceJoystickMath.travelWorldUnits(5.0), 1e-12)
        assertEquals(0.0, SurfaceJoystickMath.travelWorldUnits(-2.0), 1e-12)
    }
}
