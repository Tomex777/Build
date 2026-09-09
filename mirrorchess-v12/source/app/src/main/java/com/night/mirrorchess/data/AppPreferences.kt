package com.night.mirrorchess.data

import android.content.Context

enum class BoardPalette { CLASSIC, WALNUT, SLATE, OCEAN }
enum class PieceStyle { CLASSIC, BOLD, SOFT }

data class AppSettings(
    val playerElo: Int = 1800,
    val showLegalMoves: Boolean = true,
    val showCoordinates: Boolean = true,
    val coachEnabled: Boolean = true,
    val haptics: Boolean = true,
    val boardPalette: BoardPalette = BoardPalette.CLASSIC,
    val pieceStyle: PieceStyle = PieceStyle.CLASSIC,
    val pieceShadows: Boolean = true,
)

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("mirror_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        playerElo = prefs.getInt("playerElo", 1800).coerceIn(600, 2800),
        showLegalMoves = prefs.getBoolean("showLegalMoves", true),
        showCoordinates = prefs.getBoolean("showCoordinates", true),
        coachEnabled = prefs.getBoolean("coachEnabled", true),
        haptics = prefs.getBoolean("haptics", true),
        boardPalette = runCatching { BoardPalette.valueOf(prefs.getString("boardPalette", BoardPalette.CLASSIC.name).orEmpty()) }.getOrDefault(BoardPalette.CLASSIC),
        pieceStyle = runCatching { PieceStyle.valueOf(prefs.getString("pieceStyle", PieceStyle.CLASSIC.name).orEmpty()) }.getOrDefault(PieceStyle.CLASSIC),
        pieceShadows = prefs.getBoolean("pieceShadows", true),
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putInt("playerElo", settings.playerElo.coerceIn(600, 2800))
            .putBoolean("showLegalMoves", settings.showLegalMoves)
            .putBoolean("showCoordinates", settings.showCoordinates)
            .putBoolean("coachEnabled", settings.coachEnabled)
            .putBoolean("haptics", settings.haptics)
            .putString("boardPalette", settings.boardPalette.name)
            .putString("pieceStyle", settings.pieceStyle.name)
            .putBoolean("pieceShadows", settings.pieceShadows)
            .apply()
    }
}
