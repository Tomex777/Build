package com.night.endless.engine.scene

enum class ExplorationScale {
    SOLAR_SYSTEM,
    LOCAL_STARS,
    MILKY_WAY,
    OBSERVABLE_UNIVERSE
}

data class CosmicScaleDescriptor(
    val scale: ExplorationScale,
    val title: String,
    val spanLightYears: Double,
    val distanceLabel: String,
    val summary: String,
    val schematic: Boolean
)

object CosmicScaleModel {
    val ordered = listOf(
        ExplorationScale.SOLAR_SYSTEM,
        ExplorationScale.LOCAL_STARS,
        ExplorationScale.MILKY_WAY,
        ExplorationScale.OBSERVABLE_UNIVERSE
    )

    private val descriptors = mapOf(
        ExplorationScale.SOLAR_SYSTEM to CosmicScaleDescriptor(
            ExplorationScale.SOLAR_SYSTEM,
            "Solar System",
            2.0,
            "planetary system → Oort Cloud",
            "The Sun, planets, moons, small bodies and the distant reservoir of long-period comets.",
            false
        ),
        ExplorationScale.LOCAL_STARS to CosmicScaleDescriptor(
            ExplorationScale.LOCAL_STARS,
            "Local Stars",
            20.0,
            "tens of light-years",
            "The Sun's stellar neighborhood. Proxima Centauri, the nearest known star beyond the Sun, is about 4.25 light-years away.",
            true
        ),
        ExplorationScale.MILKY_WAY to CosmicScaleDescriptor(
            ExplorationScale.MILKY_WAY,
            "Milky Way",
            100_000.0,
            "≈100,000 light-years",
            "A barred spiral galaxy containing hundreds of billions of stars. Endless renders this scale as a navigable structural overview.",
            true
        ),
        ExplorationScale.OBSERVABLE_UNIVERSE to CosmicScaleDescriptor(
            ExplorationScale.OBSERVABLE_UNIVERSE,
            "Observable Universe",
            92_000_000_000.0,
            "≈92 billion light-years",
            "The region whose light can in principle reach us today. Its visual structure is a scale model, not a literal map of every galaxy.",
            true
        )
    )

    fun descriptor(scale: ExplorationScale): CosmicScaleDescriptor = descriptors.getValue(scale)

    fun next(scale: ExplorationScale): ExplorationScale {
        val index = ordered.indexOf(scale).coerceAtLeast(0)
        return ordered[(index + 1).coerceAtMost(ordered.lastIndex)]
    }

    fun previous(scale: ExplorationScale): ExplorationScale {
        val index = ordered.indexOf(scale).coerceAtLeast(0)
        return ordered[(index - 1).coerceAtLeast(0)]
    }
}
