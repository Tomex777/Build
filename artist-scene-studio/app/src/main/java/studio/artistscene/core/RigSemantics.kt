package studio.artistscene.core

/** Small alias layer for readable pose controls while preserving each rig's real bone IDs/names. */
object RigSemantics {
    fun tag(name: String): String = name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    fun label(name: String): String {
        val value = normalizedWords(name)
        val compact = value.replace(" ", "")
        val side = sidePrefix(value, compact)
        val numberedArmJoint = value.substringAfterLast(' ').toIntOrNull()
        val numberedLegJoint = numberedArmJoint
        fingerPart(value, compact)?.let { return side + it }
        val part = when {
            listOf("hips", "pelvis", "hip").any(compact::contains) -> "Hips"
            listOf("neck").any(compact::contains) -> "Neck"
            listOf("head").any(compact::contains) -> "Head"
            compact.contains("armjoint") && numberedArmJoint == 1 -> "Shoulder"
            compact.contains("armjoint") && numberedArmJoint == 2 -> "Elbow"
            compact.contains("armjoint") && numberedArmJoint == 3 -> "Wrist"
            compact.contains("legjoint") && numberedLegJoint == 1 -> "Thigh"
            compact.contains("legjoint") && numberedLegJoint == 2 -> "Knee"
            compact.contains("legjoint") && numberedLegJoint == 3 -> "Ankle"
            compact.contains("legjoint") && numberedLegJoint == 5 -> "Toe"
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

    /**
     * Uses hierarchy as well as names when a rig's numbering is inconsistent between left/right.
     * Cesium Man is a real example: its right arm ends in R_3 while the mirrored left arm ends in
     * L_2. The imported parent/child chain is therefore more reliable than the suffix alone.
     */
    fun label(bone: RigBone, bones: List<RigBone>): String {
        val value = normalizedWords(bone.name)
        val compact = value.replace(" ", "")
        val side = sidePrefix(value, compact)

        fingerPart(value, compact)?.let { return side + it }

        if (compact.contains("armjoint")) {
            val parentIsArm = bones.firstOrNull { it.id == bone.parentId }
                ?.name?.let(::compactName)?.contains("armjoint") == true
            val childIsArm = bones.any { child ->
                child.parentId == bone.id && compactName(child.name).contains("armjoint")
            }
            val part = when {
                !parentIsArm && childIsArm -> "Shoulder"
                parentIsArm && childIsArm -> "Elbow"
                parentIsArm && !childIsArm -> "Wrist"
                else -> "Arm"
            }
            return side + part
        }

        if (compact.contains("legjoint")) {
            val number = value.substringAfterLast(' ').toIntOrNull()
            val part = when (number) {
                1 -> "Thigh"
                2 -> "Knee"
                3 -> "Ankle"
                5 -> "Toe"
                else -> null
            }
            if (part != null) return side + part
        }

        return label(bone.name)
    }

    /** Finger joints are kept separately selectable because they become too dense for viewport-only picking. */
    fun fingerBones(bones: List<RigBone>): List<RigBone> = bones
        .filter { bone -> fingerPart(normalizedWords(bone.name), compactName(bone.name)) != null }
        .sortedWith(
            compareBy<RigBone>(
                { fingerSideRank(normalizedWords(it.name), compactName(it.name)) },
                { fingerKindRank(compactName(it.name)) },
                { fingerSegment(normalizedWords(it.name), compactName(it.name)) ?: Int.MAX_VALUE },
                { it.name.lowercase() },
            ),
        )

    /** Returns wrist/hand and ankle/foot bones suitable for two-bone viewport IK dragging. */
    fun ikEndEffectorIds(bones: List<RigBone>): Set<String> = bones
        .filter { bone ->
            val readable = label(bone, bones).lowercase()
            readable.endsWith("wrist") ||
                readable.endsWith("hand") ||
                readable.endsWith("ankle") ||
                readable.endsWith("foot")
        }
        .mapTo(linkedSetOf()) { it.id }

    private fun fingerPart(value: String, compact: String): String? {
        Regex("^finger([1-5]) ([1-3])(?: [lr])?$").matchEntire(value)?.let { match ->
            val name = listOf("Thumb", "Index", "Middle", "Ring", "Little")[match.groupValues[1].toInt() - 1]
            return "$name ${match.groupValues[2]}"
        }
        val name = when {
            compact.contains("thumb") -> "Thumb"
            compact.contains("index") -> "Index"
            compact.contains("middle") -> "Middle"
            compact.contains("ring") -> "Ring"
            compact.contains("pinky") || compact.contains("little") -> "Little"
            else -> return null
        }
        val segment = fingerSegment(value, compact)
        return if (segment == null) name else "$name $segment"
    }

    private fun fingerSegment(value: String, compact: String): Int? {
        if ("proximal" in compact) return 1
        if ("intermediate" in compact || "intermed" in compact) return 2
        if ("distal" in compact) return 3

        val fingerToken = when {
            "thumb" in compact -> "thumb"
            "index" in compact -> "index"
            "middle" in compact -> "middle"
            "ring" in compact -> "ring"
            "pinky" in compact -> "pinky"
            "little" in compact -> "little"
            else -> return null
        }
        Regex("$fingerToken(?:finger)?0*([1-4])").find(compact)?.groupValues?.getOrNull(1)
            ?.toIntOrNull()?.let { return it }

        // Handles names such as "finger_index.01.L" after punctuation normalization.
        val words = value.split(' ')
        val index = words.indexOfFirst { it.contains(fingerToken) }
        return words.drop((index + 1).coerceAtLeast(0)).firstNotNullOfOrNull { word ->
            word.toIntOrNull()?.takeIf { it in 1..4 }
        }
    }

    private fun fingerSideRank(value: String, compact: String): Int = when {
        sidePrefix(value, compact).startsWith("Left") -> 0
        sidePrefix(value, compact).startsWith("Right") -> 1
        else -> 2
    }

    private fun fingerKindRank(compact: String): Int = when {
        "thumb" in compact -> 0
        "index" in compact -> 1
        "middle" in compact -> 2
        "ring" in compact -> 3
        "pinky" in compact || "little" in compact -> 4
        else -> 5
    }

    private fun normalizedWords(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun compactName(name: String): String = normalizedWords(name).replace(" ", "")

    private fun sidePrefix(value: String, compact: String): String = when {
        compact.contains("left") || Regex("(^| )(l)( |$)").containsMatchIn(value) -> "Left "
        compact.contains("right") || Regex("(^| )(r)( |$)").containsMatchIn(value) -> "Right "
        else -> ""
    }
}
