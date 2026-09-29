package studio.artistscene.core

/** Small alias layer for readable pose controls while preserving each rig's real bone IDs/names. */
object RigSemantics {
    fun tag(name: String): String = name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    fun label(name: String): String {
        val value = name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        val compact = value.replace(" ", "")
        val side = when {
            compact.contains("left") || Regex("(^| )(l)( |$)").containsMatchIn(value) -> "Left "
            compact.contains("right") || Regex("(^| )(r)( |$)").containsMatchIn(value) -> "Right "
            else -> ""
        }
        val numberedArmJoint = value.substringAfterLast(' ').toIntOrNull()
        val part = when {
            listOf("hips", "pelvis", "hip").any(compact::contains) -> "Hips"
            listOf("neck").any(compact::contains) -> "Neck"
            listOf("head").any(compact::contains) -> "Head"
            compact.contains("armjoint") && numberedArmJoint == 1 -> "Shoulder"
            compact.contains("armjoint") && numberedArmJoint == 2 -> "Elbow"
            compact.contains("armjoint") && numberedArmJoint == 3 -> "Wrist"
            listOf("shoulder", "clavicle").any(compact::contains) -> "Shoulder"
            listOf("forearm", "lowerarm", "elbow").any(compact::contains) -> "Forearm"
            listOf("upperarm", "arm").any(compact::contains) -> "Arm"
            listOf("hand", "wrist").any(compact::contains) -> "Hand"
            listOf("thigh", "upperleg", "legupper").any(compact::contains) -> "Thigh"
            listOf("calf", "lowerleg", "knee").any(compact::contains) -> "Lower leg"
            listOf("foot", "ankle").any(compact::contains) -> "Foot"
            listOf("spine", "chest", "torso").any(compact::contains) -> "Torso"
            else -> name
        }
        return side + part
    }
}
