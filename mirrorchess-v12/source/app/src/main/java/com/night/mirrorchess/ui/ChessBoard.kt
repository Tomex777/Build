package com.night.mirrorchess.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.night.mirrorchess.chess.GameState
import com.night.mirrorchess.chess.Piece
import com.night.mirrorchess.chess.PieceType
import com.night.mirrorchess.chess.Side
import com.night.mirrorchess.chess.fileOf
import com.night.mirrorchess.chess.index
import com.night.mirrorchess.chess.rankOf
import com.night.mirrorchess.chess.squareName
import com.night.mirrorchess.data.BoardPalette
import kotlin.math.roundToInt

private data class BoardColors(val light: Color, val dark: Color, val selected: Color, val last: Color, val coordLight: Color, val coordDark: Color)
private data class PieceMotion(val piece: Piece, val from: Int, val to: Int)

private fun colorsFor(palette: BoardPalette): BoardColors = when (palette) {
    BoardPalette.CLASSIC -> BoardColors(Color(0xFFE8E9D0), Color(0xFF779455), Color(0xFFF3D35E), Color(0xFFC5B24F), Color(0xFF5F7744), Color(0xFFF1F2DE))
    BoardPalette.WALNUT -> BoardColors(Color(0xFFE0C9A6), Color(0xFF8B5E3C), Color(0xFFD8B34B), Color(0xFFB58B3D), Color(0xFF6E4A30), Color(0xFFF2E2C8))
    BoardPalette.SLATE -> BoardColors(Color(0xFFD3D7D8), Color(0xFF59656A), Color(0xFFB8C77B), Color(0xFF8E9D65), Color(0xFF4B5559), Color(0xFFE8ECEC))
    BoardPalette.OCEAN -> BoardColors(Color(0xFFD7E2E4), Color(0xFF4E7581), Color(0xFFD6BC5A), Color(0xFFA8A04A), Color(0xFF45636B), Color(0xFFEAF3F4))
}

@Composable
fun ChessBoard(
    state: GameState,
    selectedSquare: Int?,
    legalTargets: Set<Int>,
    flipped: Boolean,
    onSquareTap: (Int) -> Unit,
    onMoveAttempt: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    palette: BoardPalette = BoardPalette.CLASSIC,
    pieceStyle: String = "classic",
    pieceShadows: Boolean = true,
    showLegalMoves: Boolean = true,
    showCoordinates: Boolean = true,
    interactionsEnabled: Boolean = true,
    hapticsEnabled: Boolean = true,
) {
    val boardColors = colorsFor(palette)
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val motionScale = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    var boardWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    var draggingSquare by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var displayedState by remember { mutableStateOf(state) }
    var motions by remember { mutableStateOf<List<PieceMotion>>(emptyList()) }
    var hiddenSquares by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val moveProgress = remember { Animatable(1f) }
    val animationRunning = motions.isNotEmpty() && moveProgress.value < 1f

    LaunchedEffect(state) {
        if (state == displayedState) return@LaunchedEffect
        val previous = displayedState
        val nextMotions = derivePieceMotions(previous, state)

        if (motionScale <= 0f || nextMotions.isEmpty()) {
            displayedState = state
            motions = emptyList()
            hiddenSquares = emptySet()
            moveProgress.snapTo(1f)
            return@LaunchedEffect
        }

        hiddenSquares = previous.board.indices
            .filter { square -> previous.board[square] != null && previous.board[square] != state.board[square] }
            .toSet()
        motions = nextMotions
        moveProgress.snapTo(0f)
        moveProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = (220f * motionScale).roundToInt().coerceIn(1, 650),
                easing = FastOutSlowInEasing,
            ),
        )
        displayedState = state
        motions = emptyList()
        hiddenSquares = emptySet()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onSizeChanged { boardWidthPx = it.width },
    ) {
        Column(Modifier.fillMaxSize()) {
            repeat(8) { screenRow ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    repeat(8) { screenCol ->
                        val square = screenToBoardSquare(screenCol, screenRow, flipped)
                        val piece = displayedState.board[square]
                        val light = (fileOf(square) + rankOf(square)) % 2 == 1
                        val isSelected = selectedSquare == square
                        val isLastMove = displayedState.lastMove?.let { it.from == square || it.to == square } == true
                        val background = if (light) boardColors.light else boardColors.dark
                        val coordinateColor = if (light) boardColors.coordLight else boardColors.coordDark

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(background)
                                .then(if (isSelected) Modifier.border(1.5.dp, boardColors.selected.copy(alpha = .62f)) else Modifier)
                                .semantics {
                                    contentDescription = buildString {
                                        append(squareName(square))
                                        if (piece == null) append(", empty")
                                        else append(", ${piece.side.name.lowercase()} ${piece.type.name.lowercase()}")
                                        if (isSelected) append(", selected")
                                        if (showLegalMoves && legalTargets.contains(square)) append(", legal move")
                                    }
                                }
                                .clickable(enabled = interactionsEnabled && !animationRunning) {
                                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSquareTap(square)
                                },
                        ) {
                            if (isLastMove) {
                                Box(Modifier.fillMaxSize().background(boardColors.last.copy(alpha = .18f)))
                            }
                            if (isSelected) {
                                Box(Modifier.fillMaxSize().background(boardColors.selected.copy(alpha = .12f)))
                            }
                            if (showLegalMoves && legalTargets.contains(square)) {
                                val capture = piece != null
                                Box(
                                    Modifier
                                        .align(Alignment.Center)
                                        .size(if (capture) 34.dp else 13.dp)
                                        .background(
                                            color = Color.Black.copy(alpha = if (capture) 0.16f else 0.22f),
                                            shape = CircleShape,
                                        )
                                )
                                if (capture) {
                                    Box(
                                        Modifier
                                            .align(Alignment.Center)
                                            .size(25.dp)
                                            .background(background, CircleShape)
                                    )
                                }
                            }

                            if (piece != null && draggingSquare != square && !(animationRunning && square in hiddenSquares)) {
                                PieceGlyph(
                                    type = piece.type,
                                    side = piece.side,
                                    style = pieceStyle,
                                    shadow = pieceShadows,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .pointerInput(square, boardWidthPx, flipped, displayedState.turn) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    if (interactionsEnabled && !animationRunning && piece.side == displayedState.turn) {
                                                        draggingSquare = square
                                                        dragOffset = Offset.Zero
                                                    }
                                                },
                                                onDragCancel = {
                                                    draggingSquare = null
                                                    dragOffset = Offset.Zero
                                                },
                                                onDragEnd = {
                                                    val from = draggingSquare
                                                    if (from != null && boardWidthPx > 0) {
                                                        val squareSize = boardWidthPx / 8f
                                                        val (startCol, startRow) = boardToScreen(from, flipped)
                                                        val endCol = (startCol + (dragOffset.x / squareSize).roundToInt()).coerceIn(0, 7)
                                                        val endRow = (startRow + (dragOffset.y / squareSize).roundToInt()).coerceIn(0, 7)
                                                        val to = screenToBoardSquare(endCol, endRow, flipped)
                                                        if (to != from) {
                                                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            onMoveAttempt(from, to)
                                                        } else onSquareTap(from)
                                                    }
                                                    draggingSquare = null
                                                    dragOffset = Offset.Zero
                                                },
                                            ) { change, amount ->
                                                if (draggingSquare == square) {
                                                    change.consume()
                                                    dragOffset += amount
                                                }
                                            }
                                        },
                                )
                            }

                            if (showCoordinates && screenCol == 0) {
                                Text(
                                    text = "${rankOf(square) + 1}",
                                    color = coordinateColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 3.dp, top = 1.dp),
                                )
                            }
                            if (showCoordinates && screenRow == 7) {
                                Text(
                                    text = "${('a'.code + fileOf(square)).toChar()}",
                                    color = coordinateColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 3.dp, bottom = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        val dragged = draggingSquare
        if (dragged != null) {
            val piece = displayedState.board[dragged]
            if (piece != null && boardWidthPx > 0) {
                val squareSize = boardWidthPx / 8f
                val (screenCol, screenRow) = boardToScreen(dragged, flipped)
                val squareDp = with(density) { squareSize.toDp() }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (screenCol * squareSize + dragOffset.x).roundToInt(),
                                (screenRow * squareSize + dragOffset.y).roundToInt(),
                            )
                        }
                        .size(squareDp)
                        .zIndex(20f),
                    contentAlignment = Alignment.Center,
                ) {
                    PieceGlyph(type = piece.type, side = piece.side, style = pieceStyle, shadow = pieceShadows)
                }
            }
        }

        if (animationRunning && boardWidthPx > 0) {
            val squareSize = boardWidthPx / 8f
            val squareDp = with(density) { squareSize.toDp() }
            val progress = moveProgress.value
            motions.forEach { motion ->
                val (fromCol, fromRow) = boardToScreen(motion.from, flipped)
                val (toCol, toRow) = boardToScreen(motion.to, flipped)
                val x = (fromCol + (toCol - fromCol) * progress) * squareSize
                val y = (fromRow + (toRow - fromRow) * progress) * squareSize
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .size(squareDp)
                        .zIndex(30f),
                    contentAlignment = Alignment.Center,
                ) {
                    PieceGlyph(
                        type = motion.piece.type,
                        side = motion.piece.side,
                        style = pieceStyle,
                        shadow = pieceShadows,
                    )
                }
            }
        }
    }
}

@Composable
private fun PieceGlyph(type: PieceType, side: Side, style: String, shadow: Boolean, modifier: Modifier = Modifier) {
    ChessPieceArt(
        type = type,
        side = side,
        style = style,
        shadow = shadow,
        modifier = modifier
            .fillMaxSize()
            .padding(4.dp),
    )
}

private fun derivePieceMotions(previous: GameState, next: GameState): List<PieceMotion> {
    next.lastMove?.let { move ->
        val source = previous.board.getOrNull(move.from)
        val destination = next.board.getOrNull(move.to)
        if (source != null && destination?.side == source.side) {
            val result = mutableListOf(PieceMotion(source, move.from, move.to))
            if (move.isCastle) {
                val rank = rankOf(move.from)
                val kingSide = fileOf(move.to) > fileOf(move.from)
                val rookFrom = index(if (kingSide) 7 else 0, rank)
                val rookTo = index(if (kingSide) 5 else 3, rank)
                previous.board[rookFrom]?.let { rook ->
                    result += PieceMotion(rook, rookFrom, rookTo)
                }
            }
            return result
        }
    }

    previous.lastMove?.let { move ->
        val source = previous.board.getOrNull(move.to)
        val destination = next.board.getOrNull(move.from)
        if (source != null && destination?.side == source.side) {
            val result = mutableListOf(PieceMotion(source, move.to, move.from))
            if (move.isCastle) {
                val rank = rankOf(move.from)
                val kingSide = fileOf(move.to) > fileOf(move.from)
                val rookFrom = index(if (kingSide) 5 else 3, rank)
                val rookTo = index(if (kingSide) 7 else 0, rank)
                previous.board[rookFrom]?.let { rook ->
                    result += PieceMotion(rook, rookFrom, rookTo)
                }
            }
            return result
        }
    }

    return emptyList()
}

private fun screenToBoardSquare(screenCol: Int, screenRow: Int, flipped: Boolean): Int {
    val file = if (flipped) 7 - screenCol else screenCol
    val rank = if (flipped) screenRow else 7 - screenRow
    return index(file, rank)
}

private fun boardToScreen(square: Int, flipped: Boolean): Pair<Int, Int> {
    val file = fileOf(square)
    val rank = rankOf(square)
    val col = if (flipped) 7 - file else file
    val row = if (flipped) rank else 7 - rank
    return col to row
}
