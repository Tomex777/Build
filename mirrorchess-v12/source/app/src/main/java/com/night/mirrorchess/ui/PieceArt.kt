package com.night.mirrorchess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import com.night.mirrorchess.chess.PieceType
import com.night.mirrorchess.chess.Side
import com.night.mirrorchess.data.PieceKey
import com.night.mirrorchess.data.PieceSetRepository
import com.night.mirrorchess.data.PieceSetId
import kotlin.math.min

@Composable
fun ChessPieceArt(
    type: PieceType,
    side: Side,
    modifier: Modifier = Modifier,
    style: String = "classic",
    shadow: Boolean = false,
) {
    val context = LocalContext.current
    val repository = remember(context) { PieceSetRepository.shared(context) }
    val spriteGeneration = repository.generation
    val customBitmap = remember(style, type, side, spriteGeneration) {
        if (style.startsWith("custom-")) repository.bitmapFor(style, PieceKey(side, type)) else null
    }
    val isCustom = style.startsWith("custom-")
    val customPixelArt = remember(style, spriteGeneration) { isCustom && repository.isPixelArt(style) }
    val customTransform = remember(style, type, side, spriteGeneration) {
        if (isCustom) repository.transformFor(style, PieceKey(side, type)) else com.night.mirrorchess.data.PieceTransform()
    }
    val styleId = if (isCustom) PieceSetId.CLASSIC else PieceSetId.fromId(style)
    Canvas(modifier = modifier.fillMaxSize()) {
        val s = min(size.width, size.height)
        if (customBitmap != null && !customBitmap.isRecycled) {
            val target = (s * customTransform.scale).coerceAtLeast(1f)
            val left = (size.width - target) / 2f + customTransform.offsetX * s
            val top = (size.height - target) / 2f + customTransform.offsetY * s
            drawImage(
                image = customBitmap.asImageBitmap(),
                dstOffset = IntOffset(left.toInt(), top.toInt()),
                dstSize = IntSize(target.toInt().coerceAtLeast(1), target.toInt().coerceAtLeast(1)),
                filterQuality = if (customPixelArt) FilterQuality.None else FilterQuality.Medium,
            )
            return@Canvas
        }
        val fill = when (styleId) {
            PieceSetId.CLASSIC -> if (side == Side.WHITE) Color(0xFFF4F1E8) else Color(0xFF171817)
            PieceSetId.BOLD -> if (side == Side.WHITE) Color(0xFFFCFAF4) else Color(0xFF10110F)
            PieceSetId.SOFT -> if (side == Side.WHITE) Color(0xFFEFE9DC) else Color(0xFF292A27)
            PieceSetId.MODERN -> if (side == Side.WHITE) Color(0xFFF8FAFC) else Color(0xFF182433)
            PieceSetId.MINIMAL -> if (side == Side.WHITE) Color(0xFFFFFFFF).copy(alpha = .10f) else Color(0xFF000000).copy(alpha = .10f)
            PieceSetId.PIXEL -> if (side == Side.WHITE) Color(0xFFF3E5B5) else Color(0xFF312A51)
            PieceSetId.FANTASY -> if (side == Side.WHITE) Color(0xFFF3E7CE) else Color(0xFF30213F)
        }
        val edge = when (styleId) {
            PieceSetId.CLASSIC -> if (side == Side.WHITE) Color(0xFF3C3D39) else Color(0xFF070807)
            PieceSetId.BOLD -> if (side == Side.WHITE) Color(0xFF242522) else Color(0xFF050605)
            PieceSetId.SOFT -> if (side == Side.WHITE) Color(0xFF6A675F) else Color(0xFF161714)
            PieceSetId.MODERN -> if (side == Side.WHITE) Color(0xFF275A72) else Color(0xFF050B12)
            PieceSetId.MINIMAL -> if (side == Side.WHITE) Color(0xFFF9F5EA) else Color(0xFF171B20)
            PieceSetId.PIXEL -> if (side == Side.WHITE) Color(0xFF8A522B) else Color(0xFF161126)
            PieceSetId.FANTASY -> if (side == Side.WHITE) Color(0xFF80602F) else Color(0xFF120E19)
        }
        val stroke = s * when (styleId) {
            PieceSetId.CLASSIC -> .025f; PieceSetId.BOLD -> .034f; PieceSetId.SOFT -> .020f
            PieceSetId.MODERN -> .030f; PieceSetId.MINIMAL -> .045f; PieceSetId.PIXEL -> .050f; PieceSetId.FANTASY -> .025f
        }
        translate((size.width - s) / 2f, (size.height - s) / 2f) {
            if (shadow) {
                translate(s * .025f, s * .035f) {
                    drawStyledPiece(type, s, Color.Black.copy(alpha = .16f), Color.Black.copy(alpha = .10f), stroke, styleId == PieceSetId.PIXEL)
                }
            }
            drawStyledPiece(type, s, fill, edge, stroke, styleId == PieceSetId.PIXEL)
            if (styleId == PieceSetId.FANTASY) {
                val gold = Color(0xFFD5AE5E)
                drawLine(gold, Offset(s * .35f, s * .66f), Offset(s * .65f, s * .66f), s * .018f)
                if (type == PieceType.KING || type == PieceType.QUEEN) {
                    drawCircle(gold, s * .025f, Offset(s * .50f, s * .16f))
                }
            }
        }
    }
}

private fun DrawScope.drawStyledPiece(type: PieceType, s: Float, fill: Color, edge: Color, stroke: Float, pixel: Boolean) {
    if (pixel) drawPixelPiece(type, s, fill, edge) else drawPiece(type, s, fill, edge, stroke)
}

private fun DrawScope.drawPixelPiece(type: PieceType, s: Float, fill: Color, edge: Color) {
    val rows = when (type) {
        PieceType.PAWN -> listOf("...##...", "...##...", "....#...", "...###..", "..#####.", "..#####.", "...###..", ".#######")
        PieceType.ROOK -> listOf(".##.##..", ".##.##..", "########", "..####..", "..####..", "..####..", "..####..", ".######.")
        PieceType.KNIGHT -> listOf("....##..", "...###..", ".######.", "..#####.", "..####..", "..####..", "..####..", ".######.")
        PieceType.BISHOP -> listOf("...##...", "...##...", "..####..", "...##...", "..####..", "..####..", "...##...", ".######.")
        PieceType.QUEEN -> listOf(".#..#.#.", ".#..#.#.", "########", "..####..", "..####..", "..####..", "..####..", ".######.")
        PieceType.KING -> listOf("...##...", "...##...", ".######.", "...##...", "..####..", "..####..", "..####..", ".######.")
    }
    val cell = s / 9f
    val left = s / 18f
    val top = s * .08f
    rows.forEachIndexed { y, row -> row.forEachIndexed { x, char ->
        if (char == '#') {
            val origin = Offset(left + x * cell, top + y * cell)
            drawRect(edge, origin, androidx.compose.ui.geometry.Size(cell, cell))
            drawRect(fill, Offset(origin.x + cell * .12f, origin.y + cell * .12f), androidx.compose.ui.geometry.Size(cell * .76f, cell * .76f))
        }
    } }
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
