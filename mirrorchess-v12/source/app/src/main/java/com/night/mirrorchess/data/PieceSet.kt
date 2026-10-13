package com.night.mirrorchess.data

import com.night.mirrorchess.chess.PieceType
import com.night.mirrorchess.chess.Side

/** A stable, complete mapping from every chess side/type pair to one visual. */
data class PieceSet(
    val id: String,
    val name: String,
    val version: Int = 1,
    val pixelArt: Boolean = false,
    val builtIn: Boolean = true,
    val mapping: Map<PieceKey, String> = standardMapping(id),
) {
    fun visualFor(side: Side, type: PieceType): String? = mapping[PieceKey(side, type)]
    fun isComplete(): Boolean = PieceKey.all.all { mapping[it].isNullOrBlank().not() }

    companion object {
        fun standardMapping(prefix: String): Map<PieceKey, String> = buildMap {
            Side.entries.forEach { side ->
                PieceType.entries.forEach { type -> put(PieceKey(side, type), "$prefix/${side.name.lowercase()}-${type.name.lowercase()}") }
            }
        }
    }
}

data class PieceKey(val side: Side, val type: PieceType) {
    companion object { val all = Side.entries.flatMap { side -> PieceType.entries.map { PieceKey(side, it) } } }
}

enum class PieceSetId(val title: String, val subtitle: String, val pixelArt: Boolean = false) {
    CLASSIC("Classic", "Traditional carved silhouettes"),
    MODERN("Modern", "Crisp contemporary forms"),
    MINIMAL("Minimal", "Clean outline-first pieces"),
    PIXEL("Pixel", "Retro block-shaped pieces", true),
    FANTASY("Fantasy", "Decorative crown and crest details"),
    BOLD("Bold", "High-contrast classic"),
    SOFT("Soft", "Warm, softer finish");

    fun asPieceSet() = PieceSet(name.lowercase(), title, pixelArt = pixelArt)

    companion object {
        fun fromId(id: String): PieceSetId = entries.firstOrNull { it.name.equals(id, true) || it.name.lowercase() == id.lowercase() } ?: CLASSIC
    }
}


/** Per-piece presentation adjustment stored with custom sets. Offsets are fractions of a board square. */
data class PieceTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    fun sanitized(): PieceTransform = copy(
        scale = scale.coerceIn(0.65f, 1.35f),
        offsetX = offsetX.coerceIn(-0.20f, 0.20f),
        offsetY = offsetY.coerceIn(-0.20f, 0.20f),
    )
}
