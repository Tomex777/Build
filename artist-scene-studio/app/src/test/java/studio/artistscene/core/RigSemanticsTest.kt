package studio.artistscene.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RigSemanticsTest {
    @Test
    fun labelsCommonHumanoidAliasesWithoutChangingStoredNames() {
        assertEquals("Left Shoulder", RigSemantics.label("mixamorig:LeftShoulder"))
        assertEquals("Right Elbow", RigSemantics.label("Skeleton_arm_joint_R__2_"))
        assertEquals("Hips", RigSemantics.label("pelvis"))
        assertEquals("Head", RigSemantics.label("head_end"))
    }

    @Test
    fun tagsAreStableAndSafeForUiAutomationIds() {
        assertEquals("skeleton-arm-joint-r-2", RigSemantics.tag("Skeleton_arm_joint_R__2_"))
    }
}
