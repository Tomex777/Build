package com.night.mirrorchess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.night.mirrorchess.chess.PieceType
import com.night.mirrorchess.chess.Side
import com.night.mirrorchess.data.PieceStyle
import kotlin.math.min

@Composable
fun ChessPieceArt(
    type: PieceType,
    side: Side,
    modifier: Modifier = Modifier,
    style: PieceStyle = PieceStyle.CLASSIC,
    shadow: Boolean = false,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val s = min(size.width, size.height)
        val fill = when (style) {
            PieceStyle.CLASSIC -> if (side == Side.WHITE) Color(0xFFF4F1E8) else Color(0xFF171817)
            PieceStyle.BOLD -> if (side == Side.WHITE) Color(0xFFFCFAF4) else Color(0xFF10110F)
            PieceStyle.SOFT -> if (side == Side.WHITE) Color(0xFFEFE9DC) else Color(0xFF292A27)
        }
        val edge = when (style) {
            PieceStyle.CLASSIC -> if (side == Side.WHITE) Color(0xFF3C3D39) else Color(0xFF070807)
            PieceStyle.BOLD -> if (side == Side.WHITE) Color(0xFF242522) else Color(0xFF050605)
            PieceStyle.SOFT -> if (side == Side.WHITE) Color(0xFF6A675F) else Color(0xFF161714)
        }
        val stroke = s * when (style) { PieceStyle.CLASSIC -> 0.025f; PieceStyle.BOLD -> 0.034f; PieceStyle.SOFT -> 0.020f }
        translate((size.width - s) / 2f, (size.height - s) / 2f) {
            if (shadow) {
                translate(s * .025f, s * .035f) {
                    drawPiece(type, s, Color.Black.copy(alpha = .16f), Color.Black.copy(alpha = .10f), stroke)
                }
            }
            drawPiece(type, s, fill, edge, stroke)
        }
    }
}

private fun DrawScope.drawPiece(type: PieceType, s: Float, fill: Color, edge: Color, stroke: Float) {
    when (type) {
        PieceType.PAWN -> pawn(s, fill, edge, stroke)
        PieceType.ROOK -> rook(s, fill, edge, stroke)
        PieceType.KNIGHT -> knight(s, fill, edge, stroke)
        PieceType.BISHOP -> bishop(s, fill, edge, stroke)
        PieceType.QUEEN -> queen(s, fill, edge, stroke)
        PieceType.KING -> king(s, fill, edge, stroke)
    }
}

private fun DrawScope.basePath(s: Float): Path = Path().apply {
    moveTo(s * .25f, s * .79f)
    cubicTo(s * .30f, s * .69f, s * .70f, s * .69f, s * .75f, s * .79f)
    lineTo(s * .80f, s * .86f)
    lineTo(s * .20f, s * .86f)
    close()
}

private fun DrawScope.drawFilled(path: Path, fill: Color, edge: Color, stroke: Float) {
    drawPath(path, fill)
    drawPath(path, edge.copy(alpha = .55f), style = Stroke(stroke))
}

private fun DrawScope.pawn(s: Float, fill: Color, edge: Color, stroke: Float) {
    drawCircle(fill, s * .12f, Offset(s * .5f, s * .31f))
    drawCircle(edge.copy(alpha = .55f), s * .12f, Offset(s * .5f, s * .31f), style = Stroke(stroke))
    val body = Path().apply {
        moveTo(s * .41f, s * .41f)
        cubicTo(s * .38f, s * .53f, s * .34f, s * .63f, s * .29f, s * .72f)
        lineTo(s * .71f, s * .72f)
        cubicTo(s * .66f, s * .63f, s * .62f, s * .53f, s * .59f, s * .41f)
        close()
    }
    drawFilled(body, fill, edge, stroke)
    drawFilled(basePath(s), fill, edge, stroke)
}

private fun DrawScope.rook(s: Float, fill: Color, edge: Color, stroke: Float) {
    val p = Path().apply {
        moveTo(s * .27f, s * .22f)
        lineTo(s * .38f, s * .22f); lineTo(s * .38f, s * .31f)
        lineTo(s * .47f, s * .31f); lineTo(s * .47f, s * .22f)
        lineTo(s * .56f, s * .22f); lineTo(s * .56f, s * .31f)
        lineTo(s * .66f, s * .31f); lineTo(s * .66f, s * .22f)
        lineTo(s * .76f, s * .22f); lineTo(s * .72f, s * .42f)
        lineTo(s * .66f, s * .48f); lineTo(s * .68f, s * .72f)
        lineTo(s * .32f, s * .72f); lineTo(s * .34f, s * .48f)
        lineTo(s * .28f, s * .42f); close()
    }
    drawFilled(p, fill, edge, stroke)
    drawFilled(basePath(s), fill, edge, stroke)
}

private fun DrawScope.bishop(s: Float, fill: Color, edge: Color, stroke: Float) {
    val p = Path().apply {
        moveTo(s * .50f, s * .19f)
        cubicTo(s * .38f, s * .28f, s * .34f, s * .39f, s * .42f, s * .49f)
        cubicTo(s * .36f, s * .57f, s * .34f, s * .64f, s * .33f, s * .72f)
        lineTo(s * .67f, s * .72f)
        cubicTo(s * .66f, s * .64f, s * .64f, s * .57f, s * .58f, s * .49f)
        cubicTo(s * .66f, s * .39f, s * .62f, s * .28f, s * .50f, s * .19f)
        close()
    }
    drawFilled(p, fill, edge, stroke)
    drawLine(edge.copy(alpha = .75f), Offset(s * .44f, s * .29f), Offset(s * .56f, s * .43f), stroke)
    drawFilled(basePath(s), fill, edge, stroke)
}

private fun DrawScope.knight(s: Float, fill: Color, edge: Color, stroke: Float) {
    val p = Path().apply {
        moveTo(s * .30f, s * .72f)
        cubicTo(s * .31f, s * .58f, s * .38f, s * .47f, s * .48f, s * .40f)
        lineTo(s * .38f, s * .31f)
        lineTo(s * .49f, s * .22f)
        cubicTo(s * .61f, s * .26f, s * .70f, s * .34f, s * .74f, s * .46f)
        lineTo(s * .66f, s * .48f)
        lineTo(s * .60f, s * .42f)
        cubicTo(s * .58f, s * .54f, s * .63f, s * .63f, s * .69f, s * .72f)
        close()
    }
    drawFilled(p, fill, edge, stroke)
    drawCircle(edge.copy(alpha = .8f), s * .022f, Offset(s * .58f, s * .34f))
    drawFilled(basePath(s), fill, edge, stroke)
}

private fun DrawScope.queen(s: Float, fill: Color, edge: Color, stroke: Float) {
    val p = Path().apply {
        moveTo(s * .28f, s * .34f)
        lineTo(s * .38f, s * .49f)
        lineTo(s * .47f, s * .32f)
        lineTo(s * .56f, s * .49f)
        lineTo(s * .69f, s * .33f)
        lineTo(s * .65f, s * .70f)
        lineTo(s * .34f, s * .70f)
        close()
    }
    drawFilled(p, fill, edge, stroke)
    listOf(.27f, .47f, .69f).forEach { x ->
        drawCircle(fill, s * .055f, Offset(s * x, s * .27f))
        drawCircle(edge.copy(alpha = .55f), s * .055f, Offset(s * x, s * .27f), style = Stroke(stroke))
    }
    drawFilled(basePath(s), fill, edge, stroke)
}

private fun DrawScope.king(s: Float, fill: Color, edge: Color, stroke: Float) {
    drawLine(edge, Offset(s * .50f, s * .15f), Offset(s * .50f, s * .31f), stroke * 1.4f)
    drawLine(edge, Offset(s * .43f, s * .22f), Offset(s * .57f, s * .22f), stroke * 1.4f)
    val p = Path().apply {
        moveTo(s * .40f, s * .31f)
        cubicTo(s * .31f, s * .39f, s * .34f, s * .52f, s * .42f, s * .58f)
        lineTo(s * .34f, s * .71f)
        lineTo(s * .66f, s * .71f)
        lineTo(s * .58f, s * .58f)
        cubicTo(s * .66f, s * .52f, s * .69f, s * .39f, s * .60f, s * .31f)
        close()
    }
    drawFilled(p, fill, edge, stroke)
    drawFilled(basePath(s), fill, edge, stroke)
}
