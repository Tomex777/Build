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
    fun hierarchyResolvesMirroredCesiumLimbEndpointsForIk() {
        val torso = RigBone("torso", "Skeleton_torso_joint_3")
        val rightShoulder = RigBone("right-1", "Skeleton_arm_joint_R", torso.id)
        val rightElbow = RigBone("right-2", "Skeleton_arm_joint_R__2_", rightShoulder.id)
        val rightWrist = RigBone("right-3", "Skeleton_arm_joint_R__3_", rightElbow.id)
        val leftShoulder = RigBone("left-4", "Skeleton_arm_joint_L__4_", torso.id)
        val leftElbow = RigBone("left-3", "Skeleton_arm_joint_L__3_", leftShoulder.id)
        val leftWrist = RigBone("left-2", "Skeleton_arm_joint_L__2_", leftElbow.id)
        val rightThigh = RigBone("leg-r-1", "leg_joint_R_1", torso.id)
        val rightKnee = RigBone("leg-r-2", "leg_joint_R_2", rightThigh.id)
        val rightAnkle = RigBone("leg-r-3", "leg_joint_R_3", rightKnee.id)
        val rightToe = RigBone("leg-r-5", "leg_joint_R_5", rightAnkle.id)
        val bones = listOf(
            torso,
            rightShoulder,
            rightElbow,
            rightWrist,
            leftShoulder,
            leftElbow,
            leftWrist,
            rightThigh,
            rightKnee,
            rightAnkle,
            rightToe,
        )

        assertEquals("Right Elbow", RigSemantics.label(rightElbow, bones))
        assertEquals("Right Wrist", RigSemantics.label(rightWrist, bones))
        assertEquals("Left Elbow", RigSemantics.label(leftElbow, bones))
        assertEquals("Left Wrist", RigSemantics.label(leftWrist, bones))
        assertEquals("Right Ankle", RigSemantics.label(rightAnkle, bones))
        assertEquals("Right Toe", RigSemantics.label(rightToe, bones))
        assertEquals(
            setOf(rightWrist.id, leftWrist.id, rightAnkle.id),
            RigSemantics.ikEndEffectorIds(bones),
        )
    }

    @Test
    fun labelsAndOrdersCommonImportedFingerJoints() {
        val wrist = RigBone("wrist", "mixamorig:LeftHand")
        val leftThumb = RigBone("thumb", "mixamorig:LeftHandThumb1", wrist.id)
        val leftIndex = RigBone("index", "finger_index.02.L", wrist.id)
        val rightMiddle = RigBone("middle", "J_Bip_R_Middle3")
        val rightLittle = RigBone("little", "RightHandPinky1")
        val unrelated = RigBone("head", "Head")
        val bones = listOf(rightLittle, unrelated, rightMiddle, leftIndex, leftThumb, wrist)

        assertEquals("Left Thumb 1", RigSemantics.label(leftThumb, bones))
        assertEquals("Left Index 2", RigSemantics.label(leftIndex, bones))
        assertEquals("Right Middle 3", RigSemantics.label(rightMiddle, bones))
        assertEquals("Right Little 1", RigSemantics.label(rightLittle, bones))
        assertEquals(
            listOf(leftThumb.id, leftIndex.id, rightMiddle.id, rightLittle.id),
            RigSemantics.fingerBones(bones).map { it.id },
        )
    }

    @Test
    fun tagsAreStableAndSafeForUiAutomationIds() {
        assertEquals("skeleton-arm-joint-r-2", RigSemantics.tag("Skeleton_arm_joint_R__2_"))
    }
}
