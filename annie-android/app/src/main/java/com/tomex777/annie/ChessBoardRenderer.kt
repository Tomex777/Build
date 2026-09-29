package com.tomex777.annie

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/**
 * Native image service for scripts. Chess state remains JavaScript-owned; Kotlin only turns
 * a FEN board position into a durable PNG that Annie can render as a normal image message.
 */
internal object ChessBoardRenderer {
    private const val SIZE = 1024
    private const val SQUARE = SIZE / 8

    private val pieces = mapOf(
        'K' to "♔", 'Q' to "♕", 'R' to "♖", 'B' to "♗", 'N' to "♘", 'P' to "♙",
        'k' to "♚", 'q' to "♛", 'r' to "♜", 'b' to "♝", 'n' to "♞", 'p' to "♟",
    )

    fun render(context: Context, fen: String): String {
        val placement = fen.trim().substringBefore(' ')
        val rows = placement.split('/')
        require(rows.size == 8) { "Chess FEN must contain eight ranks" }
        val expanded = rows.map(::expandRank)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(placement.toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }
        val directory = File(context.filesDir, "script-generated/chess").apply { mkdirs() }
        val output = File(directory, "$digest.png")
        if (!output.exists()) draw(expanded, output)
        return Uri.fromFile(output).toString()
    }

    private fun expandRank(rank: String): List<Char?> {
        val cells = mutableListOf<Char?>()
        for (char in rank) {
            if (char.isDigit()) {
                repeat(char.digitToInt()) { cells += null }
            } else {
                require(char in pieces) { "Unsupported chess piece: $char" }
                cells += char
            }
        }
        require(cells.size == 8) { "Each FEN rank must describe eight files" }
        return cells
    }

    private fun draw(board: List<List<Char?>>, output: File) {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val squarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val piecePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = SQUARE * 0.72f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val coordinatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = SQUARE * 0.13f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        for (rank in 0 until 8) {
            for (file in 0 until 8) {
                val light = (rank + file) % 2 == 0
                squarePaint.color = if (light) Color.rgb(224, 230, 236) else Color.rgb(72, 105, 135)
                val left = (file * SQUARE).toFloat()
                val top = (rank * SQUARE).toFloat()
                canvas.drawRect(left, top, left + SQUARE, top + SQUARE, squarePaint)

                if (file == 0) {
                    coordinatePaint.color = if (light) Color.rgb(72, 105, 135) else Color.rgb(224, 230, 236)
                    canvas.drawText((8 - rank).toString(), left + 6f, top + coordinatePaint.textSize + 4f, coordinatePaint)
                }
                if (rank == 7) {
                    coordinatePaint.color = if (light) Color.rgb(72, 105, 135) else Color.rgb(224, 230, 236)
                    canvas.drawText(('a'.code + file).toChar().toString(), left + SQUARE - 18f, top + SQUARE - 8f, coordinatePaint)
                }

                val code = board[rank][file] ?: continue
                val glyph = pieces.getValue(code)
                val baseline = top + SQUARE / 2f - (piecePaint.ascent() + piecePaint.descent()) / 2f
                // A small contrasting shadow keeps both white and black Unicode pieces legible
                // even on devices whose chess glyphs are rendered as monochrome outlines.
                piecePaint.setShadowLayer(3f, 0f, 2f, if (code.isUpperCase()) Color.BLACK else Color.WHITE)
                piecePaint.color = if (code.isUpperCase()) Color.rgb(250, 250, 250) else Color.rgb(20, 27, 34)
                canvas.drawText(glyph, left + SQUARE / 2f, baseline, piecePaint)
                piecePaint.clearShadowLayer()
            }
        }

        output.outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "Could not encode chess board" }
        }
        bitmap.recycle()
    }
}
