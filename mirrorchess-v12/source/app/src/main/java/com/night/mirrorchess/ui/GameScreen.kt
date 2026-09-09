package com.night.mirrorchess.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.night.mirrorchess.chess.PieceType
import com.night.mirrorchess.chess.Side
import com.night.mirrorchess.viewmodel.GameUiState
import com.night.mirrorchess.viewmodel.GameViewModel
import kotlin.math.roundToInt

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onExit: () -> Unit,
    onExport: () -> Unit,
) {
    val ui = viewModel.uiState
    var showResign by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val screenAlpha by animateFloatAsState(if (entered) 1f else 0f, tween(180), label = "screenAlpha")
    val screenOffset by animateDpAsState(if (entered) 0.dp else 6.dp, tween(180), label = "screenOffset")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (ui.reviewing) {
                ReviewControls(
                    index = ui.reviewIndex,
                    count = ui.reviewCount,
                    onPrevious = viewModel::reviewPrevious,
                    onNext = viewModel::reviewNext,
                    onExport = onExport,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .offset(y = screenOffset)
                .alpha(screenAlpha),
        ) {
            AppHeader(
                onExit = onExit,
                onUndo = viewModel::undo,
                onFlip = viewModel::flipBoard,
                onExport = onExport,
                onResign = { showResign = true },
                canUndo = ui.moveLog.any { it.side == ui.playerSide } && !ui.thinking && !ui.gameOver && !ui.reviewing,
                canExport = ui.moveLog.isNotEmpty(),
                canResign = !ui.gameOver && !ui.reviewing,
            )
            PlayerBar(
                name = ui.opponentProfile.label,
                subtitle = when {
                    ui.thinking -> "Choosing a human-style move…"
                    ui.opponentProfile.mirror -> "Playing from your learned style"
                    else -> ui.opponentProfile.description
                },
                badge = ui.opponentElo.toString(),
                side = ui.playerSide.opposite(),
                emphasized = true,
                thinking = ui.thinking,
                pieceStyle = ui.settings.pieceStyle,
                pieceShadows = ui.settings.pieceShadows,
            )

            ChessBoard(
                state = ui.game,
                selectedSquare = ui.selectedSquare,
                legalTargets = ui.legalTargets,
                flipped = ui.flipped,
                onSquareTap = viewModel::onSquareTap,
                onMoveAttempt = viewModel::onMoveAttempt,
                palette = ui.settings.boardPalette,
                pieceStyle = ui.settings.pieceStyle,
                pieceShadows = ui.settings.pieceShadows,
                showLegalMoves = ui.settings.showLegalMoves,
                showCoordinates = ui.settings.showCoordinates,
                interactionsEnabled = !ui.reviewing && !ui.gameOver && !ui.thinking && ui.game.turn == ui.playerSide,
                hapticsEnabled = ui.settings.haptics,
            )

            PlayerBar(
                name = "You",
                subtitle = if (ui.reviewing) {
                    if (ui.reviewIndex == 0) "Starting position" else "Position ${ui.reviewIndex} of ${ui.reviewCount - 1}"
                } else ui.message,
                badge = ui.playerSide.name,
                side = ui.playerSide,
                emphasized = false,
                thinking = false,
                pieceStyle = ui.settings.pieceStyle,
                pieceShadows = ui.settings.pieceShadows,
            )

            MoveStrip(ui)
            if (ui.reviewing) {
                ReviewPanel(ui, Modifier.fillMaxWidth().weight(1f, fill = true))
            } else if (ui.settings.coachEnabled) {
                LearningPanel(ui, viewModel::setCoachElo, Modifier.fillMaxWidth().weight(1f, fill = true))
            } else {
                Surface(modifier = Modifier.fillMaxWidth().weight(1f), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("COACH OFF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text("Enable move comparison in Settings.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    ui.pendingPromotion?.let { request ->
        PromotionSheet(
            side = ui.playerSide,
            options = request.options,
            onChoose = viewModel::choosePromotion,
            onDismiss = viewModel::cancelPromotion,
            pieceStyle = ui.settings.pieceStyle,
            pieceShadows = ui.settings.pieceShadows,
        )
    }

    if (showResign) {
        AlertDialog(
            onDismissRequest = { showResign = false },
            title = { Text("Resign this game?") },
            text = { Text("The game will be saved to your recent games as a loss.") },
            confirmButton = { TextButton(onClick = { showResign = false; viewModel.resign() }) { Text("Resign") } },
            dismissButton = { TextButton(onClick = { showResign = false }) { Text("Keep playing") } },
        )
    }

    if (ui.gameOver && !ui.reviewing) {
        val saveMessage = if (ui.gameSaved) {
            "Your game was saved and can be reviewed from Play."
        } else {
            "The result is safe on this screen, but MirrorChess could not add it to recent games. Export the PGN before leaving."
        }
        AlertDialog(
            onDismissRequest = {},
            title = { Text(ui.resultTitle) },
            text = { Text("Result ${ui.result}. $saveMessage") },
            confirmButton = { Button(onClick = viewModel::rematch) { Text("Rematch") } },
            dismissButton = {
                Row {
                    TextButton(onClick = onExport) { Text("Export PGN") }
                    TextButton(onClick = onExit) { Text("Done") }
                }
            },
        )
    }
}

@Composable
private fun AppHeader(
    onExit: () -> Unit,
    onUndo: () -> Unit,
    onFlip: () -> Unit,
    onExport: () -> Unit,
    onResign: () -> Unit,
    canUndo: Boolean,
    canExport: Boolean,
    canResign: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onExit) { Text("Back") }
        Text("MirrorChess", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Box {
            TextButton(
                onClick = { menuOpen = true },
                modifier = Modifier.semantics { contentDescription = "More game actions" },
            ) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Undo move") },
                    enabled = canUndo,
                    onClick = { menuOpen = false; onUndo() },
                )
                DropdownMenuItem(
                    text = { Text("Flip board") },
                    onClick = { menuOpen = false; onFlip() },
                )
                DropdownMenuItem(
                    text = { Text("Export PGN") },
                    enabled = canExport,
                    onClick = { menuOpen = false; onExport() },
                )
                DropdownMenuItem(
                    text = { Text("Resign") },
                    enabled = canResign,
                    onClick = { menuOpen = false; onResign() },
                )
            }
        }
    }
}

@Composable
private fun PlayerBar(
    name: String,
    subtitle: String,
    badge: String,
    side: Side,
    emphasized: Boolean,
    thinking: Boolean,
    pieceStyle: com.night.mirrorchess.data.PieceStyle,
    pieceShadows: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(modifier = Modifier.size(32.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(contentAlignment = Alignment.Center) {
                ChessPieceArt(
                    type = if (emphasized) PieceType.KNIGHT else PieceType.KING,
                    side = side,
                    style = pieceStyle,
                    shadow = pieceShadows,
                    modifier = Modifier.size(27.dp).padding(2.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (thinking) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(badge, style = MaterialTheme.typography.labelLarge, color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoveStrip(ui: GameUiState) {
    val scroll = rememberScrollState()
    LaunchedEffect(ui.moveLog.size) {
        if (ui.moveLog.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue)
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(34.dp).background(MaterialTheme.colorScheme.surface).horizontalScroll(scroll).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (ui.moveLog.isEmpty()) {
            Text("MOVE HISTORY", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        } else {
            ui.moveLog.chunked(2).forEachIndexed { index, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${index + 1}.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(pair[0].notation, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    pair.getOrNull(1)?.let { Text(it.notation, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun ReviewPanel(ui: GameUiState, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.heightIn(min = 140.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("GAME REVIEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(if (ui.reviewIndex == 0) "Starting position" else "Position ${ui.reviewIndex} / ${ui.reviewCount - 1}", style = MaterialTheme.typography.titleMedium)
            Text("Use the controls below to walk through every saved position. Export creates a normal PGN you can open in other chess tools.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LearningPanel(ui: GameUiState, onEloSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.heightIn(min = 150.dp), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val candidateLimit = if (maxHeight >= 235.dp) 5 else 3
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("COACH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text("How would a human play this?", style = MaterialTheme.typography.titleMedium)
                    }
                    EloChooser(ui.coachElo, onEloSelected)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                when {
                    ui.thinking && ui.coachPrediction == null -> CoachEmptyState("Reading the position you just played…")
                    ui.coachPrediction == null -> CoachEmptyState("Make a move. I’ll compare it with human choices around ${ui.coachElo} rating.")
                    else -> PredictionRows(ui, candidateLimit)
                }
            }
        }
    }
}

@Composable
private fun CoachEmptyState(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("YOUR MOVE VS HUMAN PLAY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PredictionRows(ui: GameUiState, candidateLimit: Int) {
    val prediction = ui.coachPrediction ?: return
    val userMove = ui.lastUserMove
    val userNotation = ui.moveLog.lastOrNull { it.side == ui.playerSide }?.notation ?: "—"
    val rank = userMove?.let { move -> prediction.candidates.indexOfFirst { it.move == move }.takeIf { it >= 0 } }
    val top = prediction.candidates.firstOrNull()

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        CoachStat(
            label = "YOU PLAYED",
            value = userNotation,
            detail = when { rank == 0 -> "Top human choice"; rank != null -> "Human choice #${rank + 1}"; else -> "Outside top five" },
            modifier = Modifier.weight(1f),
        )
        CoachStat(
            label = "HUMAN ${ui.coachElo}",
            value = top?.label ?: "—",
            detail = top?.let { "${(it.probability * 100).roundToInt()}% predicted" } ?: "No prediction",
            modifier = Modifier.weight(1f),
        )
    }

    prediction.outcome?.let { outcome ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutcomeChip("WIN", outcome.win)
            OutcomeChip("DRAW", outcome.draw)
            OutcomeChip("LOSS", outcome.loss)
        }
    }

    prediction.candidates.take(candidateLimit).forEachIndexed { index, candidate ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(20.dp))
            Text(candidate.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(58.dp))
            ProbabilityBar(candidate.probability, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("${(candidate.probability * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(34.dp))
        }
    }
    Text(prediction.source, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun OutcomeChip(label: String, value: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CoachStat(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.displaySmall, maxLines = 1)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProbabilityBar(value: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))) {
        Box(modifier = Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(5.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun EloChooser(current: Int, onSelected: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(1200, 1800, 2200).forEach { elo ->
            val selected = current == elo
            TextButton(
                onClick = { onSelected(elo) },
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                colors = ButtonDefaults.textButtonColors(contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant),
            ) { Text(elo.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun ReviewControls(index: Int, count: Int, onPrevious: () -> Unit, onNext: () -> Unit, onExport: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
            BottomAction("Previous", index > 0, onPrevious, Modifier.weight(1f)) { color -> UndoGlyph(color, Modifier.size(20.dp)) }
            BottomAction("Next", index < count - 1, onNext, Modifier.weight(1f)) { color -> FlipGlyph(color, Modifier.size(20.dp)) }
            BottomAction("Export", count > 1, onExport, Modifier.weight(1f)) { color -> ExportGlyph(color, Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun BottomAction(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable (Color) -> Unit) {
    val content = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f)
    Column(
        modifier = modifier.fillMaxSize().clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon(content)
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromotionSheet(
    side: Side,
    options: List<PieceType>,
    onChoose: (PieceType) -> Unit,
    onDismiss: () -> Unit,
    pieceStyle: com.night.mirrorchess.data.PieceStyle,
    pieceShadows: Boolean,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
            Text("PROMOTE PAWN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                options.forEach { type ->
                    Surface(
                        modifier = Modifier.size(64.dp).clickable { onChoose(type) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ChessPieceArt(type, side, Modifier.size(54.dp).padding(4.dp), style = pieceStyle, shadow = pieceShadows)
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}
