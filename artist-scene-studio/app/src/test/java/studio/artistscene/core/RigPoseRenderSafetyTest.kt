package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RigPoseRenderSafetyTest {
    @Test
    fun validAuthoredLocalJointRotationIsPreservedExactly() {
        val original = Vec3(-180f, 89.5f, 270f)
        assertEquals(original, RigPoseRenderSafety.jointOffset(original))
        assertEquals(Vec3(), RigPoseRenderSafety.jointOffset(null))
    }

    @Test
    fun nonFiniteBoneRotationResetsOnlyThatJointToRestOffset() {
        val invalid = listOf(
            Vec3(Float.NaN, 0f, 0f),
            Vec3(0f, Float.POSITIVE_INFINITY, 0f),
            Vec3(0f, 0f, Float.NEGATIVE_INFINITY),
        )
        invalid.forEach { assertEquals(Vec3(), RigPoseRenderSafety.jointOffset(it)) }

        // Validation is stateless and cannot mutate adjacent rig/actor pose maps.
        val other = Vec3(x = 12f, y = 15f, z = -8f)
        assertEquals(other, RigPoseRenderSafety.jointOffset(other))
    }

    @Test
    fun morphWeightsAreFiniteClampedAndInvalidValuesCannotReachFilament() {
        assertEquals(0f, RigPoseRenderSafety.morphWeight(null), 0.001f)
        assertEquals(0f, RigPoseRenderSafety.morphWeight(Float.NaN), 0.001f)
        assertEquals(0f, RigPoseRenderSafety.morphWeight(Float.POSITIVE_INFINITY), 0.001f)
        assertEquals(0f, RigPoseRenderSafety.morphWeight(Float.NEGATIVE_INFINITY), 0.001f)
        assertEquals(0f, RigPoseRenderSafety.morphWeight(-1f), 0.001f)
        assertEquals(0.65f, RigPoseRenderSafety.morphWeight(0.65f), 0.001f)
        assertEquals(1f, RigPoseRenderSafety.morphWeight(9f), 0.001f)
    }
}
