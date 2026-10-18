package com.nexa.echospace.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** On the sound-field map: negative Y is in front of the listener. */
enum class SoundKind(val title: String, val emoji: String, val subtitle: String) {
    RAIN("Rain", "🌧", "Rainfall"),
    THUNDER("Thunder", "⛈", "Distant rumble"),
    OCEAN("Ocean", "🌊", "Rolling surf"),
    WIND("Wind", "🍃", "Gentle breeze"),
    FIRE("Fire", "🔥", "Crackling embers"),
    CHIMES("Chimes", "✦", "Soft wind chimes"),
    BIRDS("Birdsong", "🐦", "Forest birds"),
    NIGHT("Night life", "🦗", "Crickets at dusk"),
    STREAM("Stream", "💧", "Flowing water"),
    WATERFALL("Waterfall", "🏞", "Water rushing"),
    LEAVES("Leaves", "🍂", "Rustling canopy"),
    OWL("Owls", "🦉", "Night calls"),
    FROGS("Frogs", "🐸", "Wetland chorus"),
    TRAFFIC("City", "🚗", "Distant streets"),
    DRIZZLE("Drizzle", "🌦", "Light rain"),
    SLEET("Sleet", "❄", "Winter storm"),
    CAFE("Café", "☕", "Distant café ambience"),
    TRAIN("Train", "🚆", "Gentle railway motion"),
    FAN("Fan", "◉", "Steady air circulation"),
    WHITE_NOISE("White noise", "◉", "Wideband hiss"),
    PINK_NOISE("Pink noise", "◉", "Warm balanced noise"),
    BROWN_NOISE("Brown noise", "◉", "Deep low-frequency noise")
}

data class SoundSource(
    val id: Int,
    val kind: SoundKind,
    val x: Float,
    val y: Float,
    val gain: Float = 0.8f,
    val muted: Boolean = false
)

data class SoundScene(
    val title: String,
    val emoji: String,
    val caption: String,
    val sources: List<SoundSource>
)

/** Suggest an uncluttered map position when adding a layer. */
object SoundPlacement {
    fun next(sounds: List<SoundSource>): Pair<Float, Float> {
        // Sixteen evenly spaced positions allow new layers to avoid covering
        // pre-existing sound markers. Keep all candidates within the map rim.
        return (0 until 16).map { index ->
            val angle = (index.toDouble() / 16.0 * 2.0 * PI) - PI / 4.0
            (cos(angle) * 0.76).toFloat() to (sin(angle) * 0.76).toFloat()
        }.maxByOrNull { (x, y) ->
            sounds.minOfOrNull { hypot(it.x - x, it.y - y) } ?: 1f
        } ?: (0.54f to -0.54f)
    }
}

object Scenes {
    val rainChamber = SoundScene("Rain Chamber", "🌧", "A gentle storm", listOf(
        SoundSource(1, SoundKind.RAIN, -0.55f, -0.38f, 0.90f),
        SoundSource(2, SoundKind.THUNDER, 0.83f, -0.38f, 0.45f),
        SoundSource(3, SoundKind.WIND, -0.45f, 0.67f, 0.44f)
    ))
    val forest = SoundScene("Deep Forest", "🌲", "Into the green", listOf(
        SoundSource(1, SoundKind.BIRDS, -0.55f, -0.53f, 0.88f),
        SoundSource(2, SoundKind.STREAM, 0.69f, -0.30f, 0.71f),
        SoundSource(3, SoundKind.LEAVES, -0.37f, 0.55f, 0.49f)
    ))
    val shore = SoundScene("Night Shore", "🌊", "Tide after dark", listOf(
        SoundSource(1, SoundKind.OCEAN, -0.68f, -0.28f, 0.90f),
        SoundSource(2, SoundKind.WIND, 0.63f, -0.47f, 0.43f),
        SoundSource(3, SoundKind.NIGHT, 0.53f, 0.58f, 0.40f)
    ))
    val fireside = SoundScene("Fireside", "🔥", "Stay a little longer", listOf(
        SoundSource(1, SoundKind.FIRE, 0.32f, -0.21f, 0.85f),
        SoundSource(2, SoundKind.NIGHT, -0.69f, -0.51f, 0.41f),
        SoundSource(3, SoundKind.WIND, -0.58f, 0.61f, 0.30f)
    ))
    val drift = SoundScene("Dream Drift", "✨", "Drift into calm", listOf(
        SoundSource(1, SoundKind.CHIMES, 0.53f, -0.30f, 0.78f),
        SoundSource(2, SoundKind.OCEAN, -0.71f, 0.34f, 0.41f),
        SoundSource(3, SoundKind.WIND, -0.16f, -0.76f, 0.31f)
    ))
    val waterfall = SoundScene("Wild Falls", "🏞", "Where the water runs", listOf(
        SoundSource(1, SoundKind.WATERFALL, 0.45f, -0.24f, 0.80f),
        SoundSource(2, SoundKind.LEAVES, -0.61f, -0.48f, 0.61f),
        SoundSource(3, SoundKind.BIRDS, -0.15f, 0.71f, 0.40f)
    ))
    val marsh = SoundScene("Moonlit Marsh", "🌙", "The wetland at night", listOf(
        SoundSource(1, SoundKind.FROGS, -0.66f, -0.36f, 0.75f),
        SoundSource(2, SoundKind.OWL, 0.78f, -0.43f, 0.58f),
        SoundSource(3, SoundKind.NIGHT, 0.12f, 0.69f, 0.58f)
    ))
    val cityRain = SoundScene("City Rain", "🌃", "Streets after midnight", listOf(
        SoundSource(1, SoundKind.DRIZZLE, -0.52f, -0.33f, 0.83f),
        SoundSource(2, SoundKind.TRAFFIC, 0.71f, -0.25f, 0.59f),
        SoundSource(3, SoundKind.WIND, -0.20f, 0.67f, 0.33f)
    ))
    val winter = SoundScene("Winter Hush", "❄", "Quiet winter air", listOf(
        SoundSource(1, SoundKind.SLEET, -0.47f, -0.39f, 0.75f),
        SoundSource(2, SoundKind.WIND, 0.74f, 0.21f, 0.43f)
    ))
    val presets = listOf(rainChamber, forest, shore, fireside, drift, waterfall, marsh, cityRain, winter)
}
