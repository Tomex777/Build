package com.night.endless.engine.scene

import kotlin.math.max

/** Curated keyframe history. Ages are billions of years before present; negative is future. */
data class HistoryEvent(
    val id: String,
    val title: String,
    val ageGa: Double,
    val summary: String,
    val confidence: String
)

data class EpochVisualState(
    val lava: Float = 0f,
    val ocean: Float = 1f,
    val ice: Float = 0f,
    val atmosphere: Float = 1f,
    val impact: Float = 0f,
    /** Schematic transient surface-water cue; not a geographic reconstruction. */
    val water: Float = 0f,
    /** Schematic transient basalt/mare cue; not a geographic reconstruction. */
    val basalt: Float = 0f
)

object DeepTimeHistory {
    const val OLDEST_AGE_GA = 4.6
    const val PRESENT_AGE_GA = 0.0

    val domains = listOf("System", "Earth", "Sun", "Venus", "Mars", "Moon", "Asteroid Belt")

    private val eventsByDomain = mapOf(
        "System" to listOf(
            HistoryEvent("cloud", "Cloud collapse", 4.60, "A cold molecular cloud begins to contract; rotation helps flatten material into a disk.", "Strong evidence"),
            HistoryEvent("disk", "Protoplanetary disk", 4.56, "A young Sun is surrounded by gas and dust. Inner rocky and outer volatile-rich zones begin to diverge.", "Strong evidence"),
            HistoryEvent("planetesimals", "Planetesimals", 4.54, "Dust and pebbles grow into larger bodies through collisions and accretion.", "Leading model"),
            HistoryEvent("giants", "Giant planets grow", 4.53, "Jupiter and Saturn acquire gas while interacting with the remaining disk.", "Strong evidence; details model-dependent"),
            HistoryEvent("impacts", "Impact-rich era", 4.10, "Planetary surfaces are reshaped by leftover debris. The exact timing and intensity varied by world.", "Strong evidence"),
            HistoryEvent("clearing", "Disk clearing", 3.90, "Radiation and stellar winds disperse most remaining nebular gas and dust.", "Strong evidence"),
            HistoryEvent("system-now", "Present system", 0.0, "The mature Solar System seen today; orbital motion continues on the separate orbital clock.", "Observed")
        ),
        "Earth" to listOf(
            HistoryEvent("earth-forms", "Earth forms", 4.54, "Earth grows by accretion. Radioactive dating of meteorites anchors the age estimate.", "Strong evidence"),
            HistoryEvent("molten", "Molten early Earth", 4.48, "Large impacts and internal heat leave much of the young surface molten.", "Strong evidence"),
            HistoryEvent("moon-impact", "Moon-forming impact", 4.47, "A giant impact followed by debris accretion is the leading explanation for the Earth–Moon system.", "Leading model"),
            HistoryEvent("crust", "Crust and oceans", 4.20, "The surface cools; evidence from ancient minerals supports early liquid water.", "Strong evidence"),
            HistoryEvent("life", "Early life", 3.50, "Microbial life is established by this time; the atmosphere differs greatly from today.", "Strong evidence"),
            HistoryEvent("oxygen", "Great Oxidation", 2.40, "Oxygen begins accumulating in the atmosphere, transforming surface chemistry and life.", "Strong evidence"),
            HistoryEvent("snowball", "Snowball Earth episodes", 0.70, "Geological evidence indicates intervals of extensive, possibly near-global ice cover.", "Strong evidence; extent debated"),
            HistoryEvent("pangaea", "Pangaea", 0.25, "Continents assemble into a supercontinent before later breakup; map is schematic.", "Strong evidence"),
            HistoryEvent("dinosaurs", "Dinosaur era", 0.15, "Mesozoic ecosystems flourish as continents continue to shift.", "Strong evidence"),
            HistoryEvent("chicxulub", "Chicxulub impact", 0.066, "Schematic flash and ejecta cue, not a precise location map. This impact is linked to the end-Cretaceous global disruption.", "Strong evidence"),
            HistoryEvent("ice-age", "Recent ice ages", 0.00002, "Large Northern Hemisphere ice sheets expand and retreat during repeated glacial cycles.", "Observed geological record"),
            HistoryEvent("industrial", "Human influence", 0.00000025, "Instrumental records show rapid greenhouse-gas rise and global warming since industrialization.", "Observed"),
            HistoryEvent("earth-now", "Present Earth", 0.0, "Modern Earth. Any future climate path is a model projection, not an observed epoch.", "Observed")
        ),
        "Sun" to listOf(
            HistoryEvent("sun-cloud", "Molecular cloud", 4.60, "The Sun forms within a collapsing region of a molecular cloud.", "Strong evidence"),
            HistoryEvent("protostar", "Protostar and accretion", 4.57, "Material falls onto a growing protostar while a disk forms around it.", "Strong evidence"),
            HistoryEvent("fusion", "Hydrogen fusion begins", 4.56, "The core reaches conditions for sustained hydrogen fusion; the Sun enters its long-lived main sequence.", "Strong evidence"),
            HistoryEvent("young-sun", "Young active Sun", 4.40, "The young Sun rotates faster and produces stronger magnetic activity than today.", "Strong evidence"),
            HistoryEvent("sun-now", "Present Sun", 0.0, "The Sun is about halfway through its main-sequence lifetime.", "Observed"),
            HistoryEvent("red-giant", "Red giant", -5.0, "In roughly five billion years, the Sun is expected to expand greatly as core hydrogen is exhausted.", "Future model projection"),
            HistoryEvent("white-dwarf", "White dwarf", -7.0, "After envelope loss, the remnant is expected to cool as a white dwarf.", "Future model projection")
        ),
        "Venus" to listOf(
            HistoryEvent("venus-forms", "Venus forms", 4.50, "Venus grows by accretion in the hot inner Solar System.", "Strong evidence"),
            HistoryEvent("venus-magma", "Magma-ocean Venus", 4.40, "Early Venus is expected to have been extremely hot, with large-scale melting while its interior and atmosphere evolved.", "Leading model"),
            HistoryEvent("venus-atmosphere", "Atmosphere evolves", 4.00, "Outgassing, escape and surface chemistry reshape the atmosphere. The duration of any early temperate interval remains uncertain.", "Model-dependent"),
            HistoryEvent("venus-resurfacing", "Widespread resurfacing", 0.70, "A schematic time anchor for Venus's geologically young plains. Crater counts support extensive resurfacing within the last several hundred million years, but the timing and mechanism remain debated.", "Strong evidence; timing debated"),
            HistoryEvent("venus-now", "Present Venus", 0.0, "A hot world beneath a dense carbon-dioxide atmosphere and sulfuric-acid clouds, with evidence for geologically recent volcanism.", "Observed")
        ),
        "Mars" to listOf(
            HistoryEvent("mars-forms", "Mars forms", 4.50, "Mars accretes early and preserves an ancient crust record.", "Strong evidence"),
            HistoryEvent("mars-impact", "Ancient impacts", 4.10, "Large impacts shape the oldest surviving terrains.", "Strong evidence"),
            HistoryEvent("mars-wet", "Early water environments", 3.70, "Valleys, minerals and lake deposits indicate that liquid water once altered parts of Mars.", "Strong evidence; climate details uncertain"),
            HistoryEvent("mars-volcano", "Volcanic evolution", 2.00, "Long-lived volcanism builds major provinces, including the Tharsis region.", "Strong evidence"),
            HistoryEvent("mars-dry", "Atmosphere thins", 1.00, "Mars loses much of its early atmosphere and surface water becomes less stable.", "Strong evidence; rates model-dependent"),
            HistoryEvent("mars-now", "Present Mars", 0.0, "Cold, dry Mars with a thin atmosphere and active seasonal surface processes.", "Observed")
        ),
        "Moon" to listOf(
            HistoryEvent("moon-forms", "Moon forms", 4.47, "A debris disk from a giant impact is the leading formation model.", "Leading model"),
            HistoryEvent("magma-ocean", "Magma ocean", 4.40, "The early Moon cools and differentiates; a crust forms above a molten interior.", "Strong evidence"),
            HistoryEvent("lunar-bombardment", "Basin-forming impacts", 3.90, "Large impacts excavate the basins visible today; the timing distribution remains debated.", "Strong evidence"),
            HistoryEvent("mare", "Mare volcanism", 3.50, "Basaltic lava floods some basins, creating the dark lunar maria.", "Strong evidence"),
            HistoryEvent("moon-now", "Present Moon", 0.0, "A mostly geologically quiet world that still experiences impacts and moonquakes.", "Observed")
        ),
        "Asteroid Belt" to listOf(
            HistoryEvent("belt-solids", "Rocky building blocks", 4.56, "Rock and metal-rich solids condense and collide in the region that becomes the main asteroid belt.", "Strong evidence"),
            HistoryEvent("ceres-growth", "Ceres takes shape", 4.50, "Ceres grows into an embryonic world, but nearby Jupiter helps prevent the region from assembling into a full-sized planet.", "Leading model"),
            HistoryEvent("belt-impacts", "Collisions reshape the belt", 4.10, "Impacts fragment, heat and mix many surviving bodies while larger protoplanets retain distinct histories.", "Strong evidence"),
            HistoryEvent("belt-settles", "Modern belt emerges", 3.90, "After early dynamical reshaping, a sparse population of rocky and icy survivors remains between Mars and Jupiter.", "Strong evidence; details model-dependent"),
            HistoryEvent("belt-now", "Present asteroid belt", 0.0, "A broad family of small worlds orbits between Mars and Jupiter, with dwarf planet Ceres the largest object.", "Observed")
        )
    )

    fun events(domain: String): List<HistoryEvent> = eventsByDomain[domain] ?: eventsByDomain.getValue("System")

    fun nearestEvent(domain: String, ageGa: Double): HistoryEvent? =
        events(domain).minByOrNull { kotlin.math.abs(it.ageGa - ageGa) }

    fun adjacentEvent(domain: String, ageGa: Double, direction: Int): HistoryEvent? {
        val events = events(domain)
        val index = events.indexOf(nearestEvent(domain, ageGa))
        return events.getOrNull((index + direction.coerceIn(-1, 1)).coerceIn(0, events.lastIndex))
    }

    fun sliderPosition(ageGa: Double): Float =
        (OLDEST_AGE_GA - ageGa.coerceIn(-7.0, OLDEST_AGE_GA)).toFloat()

    fun ageFromSlider(position: Float): Double =
        (OLDEST_AGE_GA - position.toDouble()).coerceIn(-7.0, OLDEST_AGE_GA)

    fun formatAge(ageGa: Double): String = when {
        ageGa >= 1.0 -> "${trimNumber(ageGa)} Ga ago"
        ageGa >= 0.001 -> "${trimNumber(ageGa * 1000.0)} Ma ago"
        ageGa >= 0.000001 -> "${trimNumber(ageGa * 1_000_000.0)} ka ago"
        ageGa > 0.0 -> "${trimNumber(ageGa * 1_000_000_000.0)} years ago"
        ageGa < 0.0 -> "${trimNumber(-ageGa)} Ga in future · model"
        else -> "Present"
    }

    /** Interpolated Earth appearance parameters shared by focused and system views. */
    fun earthVisualState(ageGa: Double): EpochVisualState {
        val age = ageGa.coerceIn(0.0, OLDEST_AGE_GA)
        val molten = ((age - 4.20) / (4.54 - 4.20)).toFloat().coerceIn(0f, 1f)
        val ocean = ramp(age, 4.24, 3.90) * (1f - smoothBand(age, 0.76, 0.62) * .68f)
        val snowballStarts = smoothBand(age, 0.78, 0.74)
        val snowballEnds = smoothBand(age, 0.64, 0.60)
        val snowball = snowballStarts * (1f - snowballEnds)
        val iceAge = if (age <= 0.0001) .06f else smoothBand(age, 0.0026, 0.00001) * .48f
        val chicxulub = smoothBand(age, 0.068, 0.066) * (1f - smoothBand(age, 0.066, 0.064))
        return EpochVisualState(
            lava = molten,
            ocean = ocean.coerceIn(0f, 1f),
            ice = max(snowball, iceAge).coerceIn(0f, 1f),
            atmosphere = if (age > 2.4) .45f else 1f,
            impact = chicxulub.coerceIn(0f, 1f)
        )
    }

    /**
     * Curated Mars appearance cues on the shared master clock.
     *
     * These are deliberately schematic: a hot early surface, a brief impact cue,
     * and a cool-toned early-water interval. They communicate epoch changes
     * without claiming a reconstructed shoreline or climate map.
     */
    fun marsVisualState(ageGa: Double): EpochVisualState {
        val age = ageGa.coerceIn(0.0, OLDEST_AGE_GA)
        val lava = (1f - ramp(age, 4.50, 4.05)).coerceIn(0f, 1f)
        val impact = (
            smoothBand(age, 4.18, 4.10) *
                (1f - smoothBand(age, 4.10, 3.98))
            ).coerceIn(0f, 1f)
        val wetStarts = smoothBand(age, 4.05, 3.72)
        val wetFades = smoothBand(age, 2.20, 1.00)
        val water = (wetStarts * (1f - wetFades)).coerceIn(0f, 1f)
        return EpochVisualState(
            lava = lava,
            impact = impact,
            water = water
        )
    }

    /**
     * Curated lunar appearance cues on the shared master clock.
     *
     * The magma, basin-impact and mare cues are visual teaching aids. The
     * procedural mare cue is intentionally non-geographic.
     */
    fun moonVisualState(ageGa: Double): EpochVisualState {
        val age = ageGa.coerceIn(0.0, OLDEST_AGE_GA)
        val lava = (1f - ramp(age, 4.45, 4.08)).coerceIn(0f, 1f)
        val impact = (
            smoothBand(age, 4.06, 3.90) *
                (1f - smoothBand(age, 3.90, 3.72))
            ).coerceIn(0f, 1f)
        val mareStarts = smoothBand(age, 3.78, 3.50)
        val mareFades = smoothBand(age, 3.12, 2.72)
        val basalt = (mareStarts * (1f - mareFades)).coerceIn(0f, 1f)
        return EpochVisualState(
            lava = lava,
            impact = impact,
            basalt = basalt
        )
    }

    /**
     * Schematic Venus appearance cues on the shared master clock.
     *
     * Ancient Venus climate is still uncertain, so these cues only communicate
     * a hot early surface, atmospheric growth and a later resurfacing interval.
     */
    fun venusVisualState(ageGa: Double): EpochVisualState {
        val age = ageGa.coerceIn(0.0, OLDEST_AGE_GA)
        val earlyMagma = (1f - ramp(age, 4.50, 4.05)).coerceIn(0f, 1f)
        val resurfacingStarts = smoothBand(age, 0.90, 0.70)
        val resurfacingEnds = smoothBand(age, 0.70, 0.45)
        val resurfacing = (resurfacingStarts * (1f - resurfacingEnds)).coerceIn(0f, 1f)
        val atmosphere = (
            0.22f + 0.78f * smoothBand(age, 4.50, 4.00)
            ).coerceIn(0f, 1f)
        return EpochVisualState(
            lava = max(earlyMagma, resurfacing * .55f),
            atmosphere = atmosphere
        )
    }

    fun systemFormationProgress(ageGa: Double): Float =
        (((OLDEST_AGE_GA - ageGa.coerceIn(0.0, OLDEST_AGE_GA)) / (OLDEST_AGE_GA - 3.85))).toFloat().coerceIn(0f, 1f)

    private fun ramp(value: Double, start: Double, end: Double): Float =
        ((start - value) / (start - end)).toFloat().coerceIn(0f, 1f)

    private fun smoothBand(value: Double, older: Double, newer: Double): Float {
        val x = ((older - value) / (older - newer)).coerceIn(0.0, 1.0)
        return (x * x * (3.0 - 2.0 * x)).toFloat()
    }

    private fun trimNumber(value: Double): String {
        val rounded = if (value >= 100.0) "%.0f".format(java.util.Locale.US, value)
        else if (value >= 10.0) "%.1f".format(java.util.Locale.US, value)
        else "%.2f".format(java.util.Locale.US, value)
        return if ('.' in rounded) rounded.trimEnd('0').trimEnd('.') else rounded
    }
}
