package com.night.mirrorchess.ui

import android.content.Intent
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.night.mirrorchess.data.AppSettings
import com.night.mirrorchess.data.BoardPalette
import com.night.mirrorchess.data.PieceSetId
import com.night.mirrorchess.data.PieceKey
import com.night.mirrorchess.data.PieceSetRepository
import com.night.mirrorchess.data.PieceTransform
import com.night.mirrorchess.data.StoredGame
import com.night.mirrorchess.chess.PieceType
import com.night.mirrorchess.chess.Side
import com.night.mirrorchess.viewmodel.GameViewModel
import com.night.mirrorchess.viewmodel.OpponentProfile
import com.night.mirrorchess.viewmodel.PlayerColorChoice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RootRoute { PLAY, SETTINGS, GAME }
private enum class SettingsPage { MAIN, MODEL, MIRROR, BOARD, GAMEPLAY, COACH, DATA, ABOUT }

@Composable
fun MirrorChessApp(viewModel: GameViewModel) {
    val context = LocalContext.current
    val ui = viewModel.uiState
    var routeName by rememberSaveable { mutableStateOf(RootRoute.PLAY.name) }
    var settingsPageName by rememberSaveable { mutableStateOf(SettingsPage.MAIN.name) }
    var pgnUsername by rememberSaveable { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf("") }
    var pendingExportName by remember { mutableStateOf("MirrorChess.pgn") }
    val route = runCatching { RootRoute.valueOf(routeName) }.getOrDefault(RootRoute.PLAY)
    val settingsPage = runCatching { SettingsPage.valueOf(settingsPageName) }.getOrDefault(SettingsPage.MAIN)

    val pgnImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importMirrorPgn(uri, pgnUsername)
    }
    val modelImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importModel(uri)
    }
    val pgnExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-chess-pgn")) { uri ->
        if (uri != null && pendingExport.isNotBlank()) {
            val saved = runCatching {
                requireNotNull(context.contentResolver.openOutputStream(uri))
                    .bufferedWriter()
                    .use { it.write(pendingExport) }
            }.isSuccess
            Toast.makeText(
                context,
                if (saved) "PGN exported" else "PGN could not be exported",
                Toast.LENGTH_SHORT,
            ).show()
        }
        pendingExport = ""
    }

    fun exportCurrent() {
        pendingExport = viewModel.exportCurrentPgn()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        pendingExportName = "MirrorChess-$stamp.pgn"
        pgnExport.launch(pendingExportName)
    }

    fun exportSaved() {
        pendingExport = viewModel.exportSavedGamesPgn()
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        pendingExportName = "MirrorChess-games-$stamp.pgn"
        pgnExport.launch(pendingExportName)
    }

    if (route == RootRoute.GAME) {
        BackHandler {
            if (ui.reviewing) viewModel.exitReview()
            routeName = RootRoute.PLAY.name
        }
        GameScreen(
            viewModel = viewModel,
            onExit = {
                if (viewModel.uiState.reviewing) viewModel.exitReview()
                routeName = RootRoute.PLAY.name
            },
            onExport = ::exportCurrent,
        )
        return
    }

    if (route == RootRoute.SETTINGS) {
        BackHandler {
            if (settingsPage != SettingsPage.MAIN) settingsPageName = SettingsPage.MAIN.name
            else routeName = RootRoute.PLAY.name
        }
        SettingsScreen(
            viewModel = viewModel,
            page = settingsPage,
            onPage = { settingsPageName = it.name },
            onBack = {
                if (settingsPage != SettingsPage.MAIN) settingsPageName = SettingsPage.MAIN.name
                else routeName = RootRoute.PLAY.name
            },
            username = pgnUsername,
            onUsername = { pgnUsername = it },
            onImportPgn = { pgnImport.launch(arrayOf("application/x-chess-pgn", "text/plain", "application/octet-stream")) },
            onExportSaved = ::exportSaved,
            onImportModel = { modelImport.launch(arrayOf("application/octet-stream", "*/*")) },
        )
        return
    }

    PlayHome(
        viewModel = viewModel,
        onSettings = {
            settingsPageName = SettingsPage.MAIN.name
            routeName = RootRoute.SETTINGS.name
        },
        onGameStarted = { routeName = RootRoute.GAME.name },
        onResume = { viewModel.resumeActiveGame(); routeName = RootRoute.GAME.name },
        onReview = { game -> viewModel.reviewGame(game); routeName = RootRoute.GAME.name },
    )
}

@Composable
private fun PlayHome(
    viewModel: GameViewModel,
    onSettings: () -> Unit,
    onGameStarted: () -> Unit,
    onResume: () -> Unit,
    onReview: (StoredGame) -> Unit,
) {
    val ui = viewModel.uiState
    var opponentName by rememberSaveable { mutableStateOf(OpponentProfile.CLUB.name) }
    var colorName by rememberSaveable { mutableStateOf(PlayerColorChoice.WHITE.name) }
    var showAllRecent by rememberSaveable { mutableStateOf(false) }
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    val selectedOpponent = runCatching { OpponentProfile.valueOf(opponentName) }.getOrDefault(OpponentProfile.CLUB)
    val selectedColor = runCatching { PlayerColorChoice.valueOf(colorName) }.getOrDefault(PlayerColorChoice.WHITE)
    val mirror = ui.mirrorProfile
    val mirrorLocked = selectedOpponent.mirror && mirror?.isReady != true

    fun beginGame() {
        viewModel.startGame(selectedColor, selectedOpponent)
        onGameStarted()
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("MirrorChess", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Play human chess. Learn your own style.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Settings"; role = Role.Button }
                    .clickable(onClick = onSettings),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SettingsGlyph(MaterialTheme.colorScheme.onSurface, Modifier.size(22.dp))
                }
            }
        }

        ui.resumableGame?.takeIf { it.moves.isNotEmpty() }?.let {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp)
                    .clickable(onClick = onResume),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .28f)),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (it.result == "*") "Resume game" else "Recover saved result", style = MaterialTheme.typography.titleMedium)
                        val moveCount = (it.moves.size + 1) / 2
                        Text("$moveCount ${if (moveCount == 1) "move" else "moves"} · ${it.playerSide.name.lowercase().replaceFirstChar { character -> character.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f))
                    }
                    Text("Continue", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        SectionTitle("OPPONENT")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OpponentProfile.entries.forEach { profile ->
                val locked = profile.mirror && mirror?.isReady != true
                OpponentChip(
                    profile = profile,
                    selected = profile == selectedOpponent,
                    locked = locked,
                    mirrorProgress = mirror?.readinessPercent ?: 0,
                    onClick = { opponentName = profile.name },
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedOpponent.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    EngineBadge(
                        label = when {
                            mirrorLocked -> "LEARNING"
                            ui.modelInstalled -> "ON-DEVICE"
                            else -> "PREVIEW"
                        },
                        highlighted = ui.modelInstalled && !mirrorLocked,
                    )
                }
                Text(
                    when {
                        selectedOpponent.mirror && mirrorLocked -> "Mirror is learning your style · ${mirror?.readinessPercent ?: 0}%"
                        selectedOpponent.mirror -> "Plays from the profile built from your own decisions"
                        ui.modelInstalled -> selectedOpponent.description
                        else -> "${selectedOpponent.description}. Preview engine is active until Maia is installed."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selectedOpponent.mirror && mirrorLocked) {
                    Text("Keep playing Maia opponents or import your PGNs to unlock Mirror Me.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        SectionTitle("PLAY AS")
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerColorChoice.entries.forEach { choice ->
                val selected = choice == selectedColor
                OutlinedButton(
                    onClick = { colorName = choice.name },
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(choice.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }

        Button(
            onClick = {
                if (ui.resumableGame?.moves?.isNotEmpty() == true) confirmReplace = true else beginGame()
            },
            enabled = !mirrorLocked,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp).height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text(if (mirrorLocked) "Mirror is still learning" else "Start game") }

        if (ui.recentGames.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("RECENT GAMES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (ui.recentGames.size > 3) {
                    TextButton(onClick = { showAllRecent = !showAllRecent }) {
                        Text(if (showAllRecent) "Show less" else "See all ${ui.recentGames.size}")
                    }
                }
            }
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (if (showAllRecent) ui.recentGames else ui.recentGames.take(3)).forEach { game -> RecentGameRow(game, onReview) }
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Start a new game?") },
            text = { Text("Your unfinished game is still available. Starting a new one will replace it.") },
            confirmButton = {
                Button(onClick = { confirmReplace = false; beginGame() }) { Text("Start new game") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) { Text("Keep current game") }
            },
        )
    }
}

@Composable
private fun OpponentChip(profile: OpponentProfile, selected: Boolean, locked: Boolean, mirrorProgress: Int, onClick: () -> Unit) {
    val label = if (profile.mirror && locked) "Mirror · $mirrorProgress%" else if (profile.mirror) "Mirror" else profile.elo.toString()
    Surface(
        modifier = Modifier.clickable(onClick = onClick).alpha(if (locked && !selected) .72f else 1f),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .45f) else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EngineBadge(label: String, highlighted: Boolean) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentGameRow(game: StoredGame, onReview: (StoredGame) -> Unit) {
    val opponent = runCatching { OpponentProfile.valueOf(game.opponentId).label }.getOrDefault("Maia")
    val date = SimpleDateFormat("MMM d", Locale.US).format(Date(game.startedAt))
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).clickable { onReview(game) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(Modifier.size(34.dp), shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.surface) {
            Box(contentAlignment = Alignment.Center) { PlayGlyph(MaterialTheme.colorScheme.primary, Modifier.size(16.dp)) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(opponent, style = MaterialTheme.typography.titleMedium)
            Text("$date · ${game.moves.size} ${if (game.moves.size == 1) "move" else "moves"} played", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(game.result, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsScreen(
    viewModel: GameViewModel,
    page: SettingsPage,
    onPage: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    username: String,
    onUsername: (String) -> Unit,
    onImportPgn: () -> Unit,
    onExportSaved: () -> Unit,
    onImportModel: () -> Unit,
) {
    val title = when (page) {
        SettingsPage.MAIN -> "Settings"
        SettingsPage.MODEL -> "AI Model"
        SettingsPage.MIRROR -> "Mirror Me"
        SettingsPage.BOARD -> "Board & Pieces"
        SettingsPage.GAMEPLAY -> "Gameplay"
        SettingsPage.COACH -> "Coach"
        SettingsPage.DATA -> "Game Data"
        SettingsPage.ABOUT -> "About & Privacy"
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        SettingsTopBar(title, onBack)
        when (page) {
            SettingsPage.MAIN -> SettingsMain(viewModel, onPage)
            SettingsPage.MODEL -> ModelSettings(viewModel, onImportModel)
            SettingsPage.MIRROR -> MirrorSettings(viewModel)
            SettingsPage.BOARD -> BoardSettings(viewModel)
            SettingsPage.GAMEPLAY -> GameplaySettings(viewModel)
            SettingsPage.COACH -> CoachSettings(viewModel)
            SettingsPage.DATA -> DataSettings(viewModel, username, onUsername, onImportPgn, onExportSaved)
            SettingsPage.ABOUT -> AboutSettings()
        }
    }
}

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.semantics { contentDescription = "Back" },
        ) {
            ChevronGlyph(MaterialTheme.colorScheme.onSurface, Modifier.size(20.dp).rotate(90f))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SettingsMain(viewModel: GameViewModel, onPage: (SettingsPage) -> Unit) {
    val ui = viewModel.uiState
    val mirror = ui.mirrorProfile
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SettingsGroup(
            label = "PLAY & LEARN",
            rows = listOf(
                SettingsDestination("AI Model", if (ui.modelInstalled) "Maia-3 5M · installed and ready" else "Install Maia-3 for human-style play", SettingsPage.MODEL),
                SettingsDestination("Mirror Me", "Learning progress · ${mirror?.readinessPercent ?: 0}%", SettingsPage.MIRROR),
                SettingsDestination("Coach", "Move comparison and your rating", SettingsPage.COACH),
            ),
            onPage = onPage,
        )
        SettingsGroup(
            label = "LOOK & FEEL",
            rows = listOf(
                SettingsDestination("Board & Pieces", "Colors, pieces, shadows and coordinates", SettingsPage.BOARD),
                SettingsDestination("Gameplay", "Move markers and haptics", SettingsPage.GAMEPLAY),
            ),
            onPage = onPage,
        )
        SettingsGroup(
            label = "DATA",
            rows = listOf(
                SettingsDestination("Game Data", "Import and export PGN", SettingsPage.DATA),
                SettingsDestination("About & Privacy", "Storage, privacy and licensing", SettingsPage.ABOUT),
            ),
            onPage = onPage,
        )
        Spacer(Modifier.height(8.dp))
    }
}

private data class SettingsDestination(val title: String, val subtitle: String, val page: SettingsPage)

@Composable
private fun SettingsGroup(label: String, rows: List<SettingsDestination>, onPage: (SettingsPage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    SettingsNavRow(row.title, row.subtitle) { onPage(row.page) }
                    if (index != rows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(76.dp).clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ChevronGlyph(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(16.dp).rotate(-90f))
    }
}

@Composable
private fun ModelSettings(viewModel: GameViewModel, onImportModel: () -> Unit) {
    val ui = viewModel.uiState
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Maia-3 5M", style = MaterialTheme.typography.titleLarge)
                Text(if (ui.modelInstalled) "INSTALLED & READY" else if (ui.modelBusy) "WORKING…" else "NOT INSTALLED", style = MaterialTheme.typography.labelSmall, color = if (ui.modelInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("One Maia model powers the 1200, 1800 and 2200 opponent strengths by receiving rating inputs at inference time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ui.modelDownloadProgress?.let { StyleMeter("Download", it) }
                ui.modelMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (!ui.modelInstalled) {
            Button(
                onClick = viewModel::downloadModel,
                enabled = !ui.modelBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (ui.modelBusy) "Installing Maia…" else "Download Maia model") }
        }
        OutlinedButton(onClick = onImportModel, enabled = !ui.modelBusy, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Import ONNX manually") }
        if (ui.modelInstalled) TextButton(onClick = { confirmRemove = true }, enabled = !ui.modelBusy, modifier = Modifier.align(Alignment.Start)) { Text("Remove model") }
        Text("The model is checksum-verified before installation. Games and inference stay on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove Maia model?") },
            text = { Text("MirrorChess will switch to its simpler preview engine until Maia is installed again.") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; viewModel.deleteModel() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MirrorSettings(viewModel: GameViewModel) {
    val profile = viewModel.uiState.mirrorProfile
    val progress = profile?.readinessPercent ?: 0
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (profile?.isReady == true) "Mirror is ready" else "Mirror is learning", style = MaterialTheme.typography.titleLarge)
                StyleMeter("Learning progress", progress / 100f)
                Text("$progress%", style = MaterialTheme.typography.displaySmall)
                Text("Mirror watches only the moves you choose. It keeps learning during Maia games and Mirror games; it never learns from the opponent's generated moves.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (profile != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("GAMES", profile.totalGames.toString(), Modifier.weight(1f))
                MetricCard("MOVES", profile.movesLearned.toString(), Modifier.weight(1f))
                MetricCard("POSITIONS", profile.positionMemory.size.toString(), Modifier.weight(1f))
            }
            StyleMeter("Captures", profile.captureRate)
            StyleMeter("Checks", profile.checkRate)
            StyleMeter("Castling", profile.castleRate)
            StyleMeter("Center moves", profile.centerRate)
            TextButton(onClick = { confirmReset = true }) { Text("Reset Mirror learning") }
        } else {
            Text("Play normally and Mirror will start building your profile automatically.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset Mirror learning?") },
            text = { Text("This permanently removes the playing-style profile learned from your games and PGN imports.") },
            confirmButton = { TextButton(onClick = { confirmReset = false; viewModel.deleteMirrorProfile() }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BoardSettings(viewModel: GameViewModel) {
    val settings = viewModel.uiState.settings
    val context = LocalContext.current
    val repository = remember(context) { PieceSetRepository.shared(context) }
    val scope = rememberCoroutineScope()
    var customSets by remember { mutableStateOf(repository.listCustomSets()) }
    var newSetDialog by rememberSaveable { mutableStateOf(false) }
    var newSetName by rememberSaveable { mutableStateOf("") }
    var pixelSet by rememberSaveable { mutableStateOf(false) }
    var selectedPiece by remember { mutableStateOf(PieceKey(Side.WHITE, PieceType.KNIGHT)) }
    var importSetId by remember { mutableStateOf("") }
    var exportSetId by remember { mutableStateOf("") }
    var pendingSheetUri by remember { mutableStateOf<Uri?>(null) }
    var sheetPreview by remember { mutableStateOf<List<android.graphics.Bitmap>?>(null) }
    var renameDialog by remember { mutableStateOf(false) }
    var renameName by remember { mutableStateOf("") }
    var deleteDialog by remember { mutableStateOf(false) }

    fun retainReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun clearSheetPreview() {
        sheetPreview?.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        sheetPreview = null
        pendingSheetUri = null
    }

    val sheetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            retainReadPermission(uri)
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { repository.previewSheet(uri) } }
                result.onSuccess { preview ->
                    clearSheetPreview()
                    pendingSheetUri = uri
                    sheetPreview = preview
                }.onFailure { Toast.makeText(context, it.message ?: "Sprite sheet could not be previewed", Toast.LENGTH_LONG).show() }
            }
        }
    }
    val piecePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            retainReadPermission(uri)
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { repository.importOne(importSetId, selectedPiece, uri) } }
                result.onSuccess {
                    customSets = repository.listCustomSets()
                    Toast.makeText(context, "Piece imported", Toast.LENGTH_SHORT).show()
                }.onFailure { Toast.makeText(context, it.message ?: "Piece could not be imported", Toast.LENGTH_LONG).show() }
            }
        }
    }
    val bundlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            retainReadPermission(uri)
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use(repository::importBundle) ?: error("Bundle could not be opened.") }
                }
                result.onSuccess { set ->
                    customSets = repository.listCustomSets()
                    viewModel.updateSettings(viewModel.uiState.settings.copy(pieceSetId = set.id))
                    Toast.makeText(context, "${set.name} imported", Toast.LENGTH_SHORT).show()
                }.onFailure { Toast.makeText(context, it.message ?: "Piece bundle could not be imported", Toast.LENGTH_LONG).show() }
            }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runCatching {
            requireNotNull(context.contentResolver.openOutputStream(uri)).use { repository.exportSet(exportSetId, it) }
        }.onSuccess { Toast.makeText(context, "Piece set exported", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, it.message ?: "Piece set could not be exported", Toast.LENGTH_LONG).show() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        SectionTitle("BOARD THEME")
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BoardPalette.entries.forEach { palette ->
                ChoiceRow(
                    title = palette.name.lowercase().replaceFirstChar { it.uppercase() },
                    subtitle = "Board color preset",
                    trailing = if (settings.boardPalette == palette) "ACTIVE" else "",
                    selected = settings.boardPalette == palette,
                    leading = { BoardPalettePreview(palette) },
                    onClick = { viewModel.updateSettings(settings.copy(boardPalette = palette)) },
                )
            }
        }

        SectionTitle("PIECE SETS")
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PieceSetId.entries.forEach { style ->
                ChoiceRow(
                    title = style.title,
                    subtitle = style.subtitle,
                    trailing = if (settings.pieceSetId == style.name.lowercase()) "ACTIVE" else "",
                    selected = settings.pieceSetId == style.name.lowercase(),
                    leading = { PieceSetIdPreview(style, settings.pieceShadows) },
                    onClick = { viewModel.updateSettings(settings.copy(pieceSetId = style.name.lowercase())) },
                )
            }
            customSets.forEach { set ->
                ChoiceRow(
                    title = set.name,
                    subtitle = if (set.isComplete()) "Custom sprite set" else "${set.mapping.size}/12 sprites · missing pieces use Classic",
                    trailing = if (settings.pieceSetId == set.id) "ACTIVE" else "",
                    selected = settings.pieceSetId == set.id,
                    leading = { PieceCustomPreview(set.id, settings.pieceShadows) },
                    onClick = { viewModel.updateSettings(settings.copy(pieceSetId = set.id)) },
                )
            }
        }

        OutlinedButton(
            onClick = { newSetDialog = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        ) { Text("Create custom set") }
        OutlinedButton(
            onClick = { bundlePicker.launch(arrayOf("application/zip", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        ) { Text("Import .mcset bundle") }

        val activeCustom = customSets.firstOrNull { it.id == settings.pieceSetId }
        if (settings.pieceSetId.startsWith("custom-") && activeCustom == null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Custom set unavailable", style = MaterialTheme.typography.titleSmall)
                    Text("Its files are missing or damaged. Classic pieces are being used so the board remains playable.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        viewModel.updateSettings(viewModel.uiState.settings.copy(pieceSetId = PieceSetId.CLASSIC.name.lowercase()))
                    }) { Text("Use Classic default") }
                }
            }
        }

        if (activeCustom != null) {
            SectionTitle("CUSTOM SPRITES · ${activeCustom.name.uppercase()}")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PieceKey.all.forEach { key ->
                    val selected = selectedPiece == key
                    Surface(
                        modifier = Modifier.clickable { selectedPiece = key },
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            "${key.side.name.first()} ${key.type.name.lowercase().replaceFirstChar { it.uppercase() }}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    importSetId = activeCustom.id
                    piecePicker.launch(arrayOf("image/png", "image/webp", "image/*"))
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            ) { Text("Import selected piece") }
            OutlinedButton(
                onClick = {
                    importSetId = activeCustom.id
                    sheetPicker.launch(arrayOf("image/png", "image/webp", "image/*"))
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            ) { Text("Import 6 × 2 sprite sheet") }
            OutlinedButton(
                onClick = {
                    exportSetId = activeCustom.id
                    exportPicker.launch("MirrorChess-${activeCustom.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}.mcset")
                },
                enabled = activeCustom.isComplete(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
            ) { Text("Export portable .mcset bundle") }

            Text(
                "Sheet order: white King, Queen, Rook, Bishop, Knight, Pawn; then black King, Queen, Rook, Bishop, Knight, Pawn. Use transparent PNG or WebP tiles.",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("MANAGE CUSTOM SET")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { renameName = activeCustom.name; renameDialog = true },
                    modifier = Modifier.weight(1f),
                ) { Text("Rename") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { repository.duplicateSet(activeCustom.id) } }
                            result.onSuccess { copy ->
                                customSets = repository.listCustomSets()
                                viewModel.updateSettings(viewModel.uiState.settings.copy(pieceSetId = copy.id))
                                Toast.makeText(context, "Set duplicated", Toast.LENGTH_SHORT).show()
                            }.onFailure { Toast.makeText(context, it.message ?: "Set could not be duplicated", Toast.LENGTH_LONG).show() }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Duplicate") }
            }
            OutlinedButton(
                onClick = { deleteDialog = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete custom set") }

            SectionTitle("PIECE CREATOR")
            PixelPieceEditor(activeCustom.id, selectedPiece, repository) {
                customSets = repository.listCustomSets()
                Toast.makeText(
                    context,
                    "${selectedPiece.side.name.lowercase().replaceFirstChar { it.uppercase() }} ${selectedPiece.type.name.lowercase()} saved",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        SectionTitle("LIVE BOARD PREVIEW")
        ChessBoard(
            state = com.night.mirrorchess.chess.GameState.initial(),
            selectedSquare = null,
            legalTargets = emptySet(),
            flipped = false,
            onSquareTap = {},
            onMoveAttempt = { _, _ -> },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            palette = settings.boardPalette,
            pieceStyle = settings.pieceSetId,
            pieceShadows = settings.pieceShadows,
            showLegalMoves = false,
            showCoordinates = false,
            interactionsEnabled = false,
        )
        SettingsToggle("Piece shadows", settings.pieceShadows) { viewModel.updateSettings(settings.copy(pieceShadows = it)) }
        SettingsToggle("Board coordinates", settings.showCoordinates) { viewModel.updateSettings(settings.copy(showCoordinates = it)) }
    }

    if (newSetDialog) AlertDialog(
        onDismissRequest = { newSetDialog = false },
        title = { Text("Create a piece set") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newSetName, onValueChange = { newSetName = it.take(40) }, label = { Text("Set name") }, singleLine = true)
                SettingsToggle("Pixel-art nearest-neighbor scaling", pixelSet) { pixelSet = it }
            }
        },
        confirmButton = { TextButton(onClick = {
            val set = repository.createSet(newSetName, pixelSet)
            customSets = repository.listCustomSets()
            viewModel.updateSettings(viewModel.uiState.settings.copy(pieceSetId = set.id))
            newSetName = ""
            pixelSet = false
            newSetDialog = false
        }) { Text("Create") } },
        dismissButton = { TextButton(onClick = { newSetDialog = false }) { Text("Cancel") } },
    )

    if (renameDialog) AlertDialog(
        onDismissRequest = { renameDialog = false },
        title = { Text("Rename custom set") },
        text = {
            OutlinedTextField(
                value = renameName,
                onValueChange = { renameName = it.take(40) },
                label = { Text("Set name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val id = settings.pieceSetId
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { repository.renameSet(id, renameName) } }
                    result.onSuccess {
                        customSets = repository.listCustomSets()
                        renameDialog = false
                        Toast.makeText(context, "Set renamed", Toast.LENGTH_SHORT).show()
                    }.onFailure { Toast.makeText(context, it.message ?: "Set could not be renamed", Toast.LENGTH_LONG).show() }
                }
            }) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = { renameDialog = false }) { Text("Cancel") } },
    )

    if (deleteDialog) AlertDialog(
        onDismissRequest = { deleteDialog = false },
        title = { Text("Delete this custom set?") },
        text = { Text("Its imported and edited piece images will be removed. MirrorChess will switch back to Classic first.") },
        confirmButton = {
            TextButton(onClick = {
                val id = settings.pieceSetId
                viewModel.updateSettings(viewModel.uiState.settings.copy(pieceSetId = PieceSetId.CLASSIC.name.lowercase()))
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { repository.deleteSet(id) } }
                    result.onSuccess {
                        customSets = repository.listCustomSets()
                        deleteDialog = false
                        Toast.makeText(context, "Custom set deleted", Toast.LENGTH_SHORT).show()
                    }.onFailure { Toast.makeText(context, it.message ?: "Set could not be deleted", Toast.LENGTH_LONG).show() }
                }
            }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancel") } },
    )

    sheetPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { clearSheetPreview() },
            title = { Text("Check the 6 × 2 slicing") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("White pieces · King, Queen, Rook, Bishop, Knight, Pawn", style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        preview.take(6).forEach { Image(it.asImageBitmap(), null, Modifier.size(48.dp).padding(2.dp)) }
                    }
                    Text("Black pieces · King, Queen, Rook, Bishop, Knight, Pawn", style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        preview.drop(6).forEach { Image(it.asImageBitmap(), null, Modifier.size(48.dp).padding(2.dp)) }
                    }
                    Text("Confirm that every sprite is in the shown position before importing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = {
                val uri = pendingSheetUri
                val target = importSetId
                clearSheetPreview()
                if (uri != null) scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { repository.importSheet(target, uri) } }
                    result.onSuccess {
                        customSets = repository.listCustomSets()
                        Toast.makeText(context, "12-piece sheet imported", Toast.LENGTH_SHORT).show()
                    }.onFailure { Toast.makeText(context, it.message ?: "Sprite sheet could not be imported", Toast.LENGTH_LONG).show() }
                }
            }) { Text("Import these 12 pieces") } },
            dismissButton = { TextButton(onClick = { clearSheetPreview() }) { Text("Cancel") } },
        )
    }
}

private enum class EditorTool { PENCIL, ERASER, FILL }

@Composable
private fun PixelPieceEditor(setId: String, piece: PieceKey, repository: PieceSetRepository, onSave: () -> Unit) {
    val generation = repository.generation
    val blank = List(32 * 32) { 0 }
    fun loadSavedPixels(size: Int): List<Int> = repository.bitmapFor(setId, piece)?.let { bitmap ->
        val reduced = android.graphics.Bitmap.createScaledBitmap(bitmap, size, size, false)
        val result = IntArray(size * size).also { reduced.getPixels(it, 0, size, 0, 0, size, size) }.toList()
        if (reduced !== bitmap) reduced.recycle()
        result
    } ?: List(size * size) { 0 }

    var gridSize by remember(setId, piece, generation) { mutableIntStateOf(32) }
    var canvasDp by remember(setId, piece) { mutableIntStateOf(320) }
    val drafts = remember(setId) { mutableStateMapOf<PieceKey, List<Int>>() }
    val histories = remember(setId) { mutableStateMapOf<PieceKey, List<List<Int>>>() }
    val redoHistories = remember(setId) { mutableStateMapOf<PieceKey, List<List<Int>>>() }
    val initialPixels = remember(setId, piece, generation) {
        drafts[piece]?.let { saved ->
            resizeEditorPixels(saved, kotlin.math.sqrt(saved.size.toDouble()).toInt(), 32)
        } ?: loadSavedPixels(32).ifEmpty { blank }
    }
    var pixels by remember(setId, piece, generation) { mutableStateOf(initialPixels) }
    var pixelDirty by remember(setId, piece, generation) { mutableStateOf(false) }
    var color by remember { mutableStateOf(Color(0xFF27251F)) }
    var tool by remember { mutableStateOf(EditorTool.PENCIL) }
    var canvasPx by remember { mutableStateOf(IntSize.Zero) }
    val savedTransform = remember(setId, piece, generation) { repository.transformFor(setId, piece) }
    var pieceScale by remember(setId, piece, generation) { mutableStateOf(savedTransform.scale) }
    var offsetX by remember(setId, piece, generation) { mutableStateOf(savedTransform.offsetX) }
    var offsetY by remember(setId, piece, generation) { mutableStateOf(savedTransform.offsetY) }

    fun store(next: List<Int>, record: Boolean = true) {
        if (record) {
            histories[piece] = (histories[piece].orEmpty() + listOf(pixels)).takeLast(30)
            redoHistories[piece] = emptyList()
        }
        pixels = next
        drafts[piece] = next
        pixelDirty = true
    }

    fun resetChanges() {
        pixels = loadSavedPixels(gridSize)
        drafts[piece] = pixels
        histories[piece] = emptyList()
        redoHistories[piece] = emptyList()
        val transform = repository.transformFor(setId, piece)
        pieceScale = transform.scale
        offsetX = transform.offsetX
        offsetY = transform.offsetY
        canvasDp = 320
        pixelDirty = false
    }

    fun paint(position: Offset) {
        if (canvasPx.width == 0) return
        val col = (position.x / canvasPx.width * gridSize).toInt().coerceIn(0, gridSize - 1)
        val row = (position.y / canvasPx.height * gridSize).toInt().coerceIn(0, gridSize - 1)
        val index = row * gridSize + col
        when (tool) {
            EditorTool.PENCIL, EditorTool.ERASER -> {
                val replacement = if (tool == EditorTool.ERASER) 0 else color.toArgb()
                if (pixels[index] != replacement) {
                    val updated = pixels.toMutableList().also { it[index] = replacement }
                    store(updated, record = false)
                }
            }
            EditorTool.FILL -> {
                val target = pixels[index]
                if (target == color.toArgb()) return
                val updated = pixels.toMutableList()
                val queue = java.util.ArrayDeque<Int>().apply { add(index) }
                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    if (updated[current] != target) continue
                    updated[current] = color.toArgb()
                    val x = current % gridSize
                    val y = current / gridSize
                    if (x > 0) queue.add(current - 1)
                    if (x < gridSize - 1) queue.add(current + 1)
                    if (y > 0) queue.add(current - gridSize)
                    if (y < gridSize - 1) queue.add(current + gridSize)
                }
                store(updated)
                tool = EditorTool.PENCIL
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            "${piece.side.name.lowercase().replaceFirstChar { it.uppercase() }} ${piece.type.name.lowercase().replaceFirstChar { it.uppercase() }} · $gridSize × $gridSize",
            style = MaterialTheme.typography.titleSmall,
        )

        Text(
            "Scale ${(pieceScale * 100).toInt()}% · position ${(offsetX * 100).toInt()}, ${(offsetY * 100).toInt()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(128.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
        ) {
            val target = size.minDimension * pieceScale
            val left = (size.width - target) / 2f + offsetX * size.minDimension
            val top = (size.height - target) / 2f + offsetY * size.minDimension
            val cell = target / gridSize
            for (y in 0 until gridSize) for (x in 0 until gridSize) {
                val pixel = pixels[y * gridSize + x]
                if (pixel != 0) {
                    drawRect(editorColor(pixel), Offset(left + x * cell, top + y * cell), Size(cell, cell))
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = { pieceScale = (pieceScale - .05f).coerceAtLeast(.65f) },
                modifier = Modifier.weight(1f),
            ) { Text("Scale −") }
            OutlinedButton(
                onClick = { pieceScale = 1f; offsetX = 0f; offsetY = 0f },
                modifier = Modifier.weight(1f),
            ) { Text("Reset fit") }
            OutlinedButton(
                onClick = { pieceScale = (pieceScale + .05f).coerceAtMost(1.35f) },
                modifier = Modifier.weight(1f),
            ) { Text("Scale +") }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "←" to { offsetX = (offsetX - .025f).coerceAtLeast(-.20f) },
                "↑" to { offsetY = (offsetY - .025f).coerceAtLeast(-.20f) },
                "↓" to { offsetY = (offsetY + .025f).coerceAtMost(.20f) },
                "→" to { offsetX = (offsetX + .025f).coerceAtMost(.20f) },
            ).forEach { (label, action) ->
                OutlinedButton(onClick = action, modifier = Modifier.weight(1f)) { Text(label) }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = { canvasDp = (canvasDp - 40).coerceAtLeast(240) },
                modifier = Modifier.weight(1f),
            ) { Text("Zoom −") }
            OutlinedButton(
                onClick = { canvasDp = 320 },
                modifier = Modifier.weight(1f),
            ) { Text("Reset zoom") }
            OutlinedButton(
                onClick = { canvasDp = (canvasDp + 40).coerceAtMost(480) },
                modifier = Modifier.weight(1f),
            ) { Text("Zoom +") }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(16, 24, 32, 48, 64).forEach { targetSize ->
                OutlinedButton(onClick = {
                    if (targetSize != gridSize) {
                        histories[piece] = emptyList()
                        redoHistories[piece] = emptyList()
                        val resized = resizeEditorPixels(pixels, gridSize, targetSize)
                        gridSize = targetSize
                        pixels = resized
                        drafts[piece] = resized
                        pixelDirty = true
                    }
                }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("${targetSize}px")
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(EditorTool.PENCIL to "Pencil", EditorTool.ERASER to "Erase", EditorTool.FILL to "Fill").forEach { (entry, label) ->
                OutlinedButton(
                    onClick = { tool = entry },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (tool == entry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.weight(1f),
                ) { Text(label) }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                Color(0xFF27251F),
                Color(0xFFF4F1E8),
                Color(0xFFCA5A4A),
                Color(0xFF3978A5),
                Color(0xFFD5AE5E),
                Color.Transparent,
            ).forEach { swatch ->
                Surface(
                    Modifier.size(36.dp).clickable { color = swatch },
                    shape = CircleShape,
                    color = if (swatch == Color.Transparent) MaterialTheme.colorScheme.surfaceVariant else swatch,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) { }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = {
                val stack = histories[piece].orEmpty()
                if (stack.isNotEmpty()) {
                    redoHistories[piece] = (redoHistories[piece].orEmpty() + listOf(pixels)).takeLast(30)
                    val prev = stack.last()
                    histories[piece] = stack.dropLast(1)
                    store(prev, false)
                }
            }, modifier = Modifier.weight(1f)) { Text("Undo") }
            TextButton(onClick = {
                val stack = redoHistories[piece].orEmpty()
                if (stack.isNotEmpty()) {
                    histories[piece] = (histories[piece].orEmpty() + listOf(pixels)).takeLast(30)
                    val next = stack.last()
                    redoHistories[piece] = stack.dropLast(1)
                    store(next, false)
                }
            }, modifier = Modifier.weight(1f)) { Text("Redo") }
            TextButton(
                onClick = { store(pixels.chunked(gridSize).flatMap { it.reversed() }) },
                modifier = Modifier.weight(1f),
            ) { Text("Mirror") }
            TextButton(
                onClick = { store(List(gridSize * gridSize) { 0 }) },
                modifier = Modifier.weight(1f),
            ) { Text("Clear") }
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Canvas(
                Modifier
                    .size(canvasDp.dp)
                    .semantics { contentDescription = "Pixel art canvas" }
                    .onSizeChanged { canvasPx = it }
                    .pointerInput(tool, color, piece, canvasPx) {
                        detectDragGestures(onDragStart = { position ->
                            histories[piece] = (histories[piece].orEmpty() + listOf(pixels)).takeLast(30)
                            redoHistories[piece] = emptyList()
                            paint(position)
                        }) { change, _ ->
                            change.consume()
                            paint(change.position)
                        }
                    }
                    .pointerInput(tool, color, piece, canvasPx) {
                        detectTapGestures(onTap = { position ->
                            histories[piece] = (histories[piece].orEmpty() + listOf(pixels)).takeLast(30)
                            redoHistories[piece] = emptyList()
                            paint(position)
                        })
                    },
            ) {
                val cellW = size.width / gridSize
                val cellH = size.height / gridSize
                for (y in 0 until gridSize) for (x in 0 until gridSize) {
                    val pixel = pixels[y * gridSize + x]
                    val checker = if ((x + y) % 2 == 0) Color(0xFFDFE1E5) else Color(0xFFBFC3CA)
                    drawRect(checker, Offset(x * cellW, y * cellH), Size(cellW, cellH))
                    if (pixel != 0) drawRect(editorColor(pixel), Offset(x * cellW, y * cellH), Size(cellW, cellH))
                    drawRect(Color.Black.copy(alpha = .12f), Offset(x * cellW, y * cellH), Size(cellW, cellH), style = Stroke(width = .5f))
                }
            }
        }

        OutlinedButton(
            onClick = {
                val opposite = PieceKey(if (piece.side == Side.WHITE) Side.BLACK else Side.WHITE, piece.type)
                drafts[opposite] = pixels
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Copy draft to opposite side") }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { resetChanges() }, modifier = Modifier.weight(1f)) { Text("Cancel changes") }
            Button(
                onClick = {
                    if (pixelDirty) repository.savePixelSprite(setId, piece, pixels.toIntArray(), gridSize, gridSize)
                    repository.updateTransform(setId, piece, PieceTransform(pieceScale, offsetX, offsetY))
                    onSave()
                },
                modifier = Modifier.weight(1f),
            ) { Text("Save piece") }
        }
    }
}

private fun resizeEditorPixels(source: List<Int>, sourceSize: Int, targetSize: Int): List<Int> {
    if (sourceSize <= 0 || source.size != sourceSize * sourceSize) return List(targetSize * targetSize) { 0 }
    return List(targetSize * targetSize) { index ->
        val x = index % targetSize * sourceSize / targetSize
        val y = index / targetSize * sourceSize / targetSize
        source[y * sourceSize + x]
    }
}

private fun editorColor(argb: Int) = Color(
    red = android.graphics.Color.red(argb) / 255f,
    green = android.graphics.Color.green(argb) / 255f,
    blue = android.graphics.Color.blue(argb) / 255f,
    alpha = android.graphics.Color.alpha(argb) / 255f,
)

@Composable
private fun GameplaySettings(viewModel: GameViewModel) {
    val settings = viewModel.uiState.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        SettingsToggle("Legal move markers", settings.showLegalMoves) { viewModel.updateSettings(settings.copy(showLegalMoves = it)) }
        SettingsToggle("Haptics", settings.haptics) { viewModel.updateSettings(settings.copy(haptics = it)) }
        Text("Selected squares and previous moves use restrained translucent highlights so the board and pieces remain readable.", modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CoachSettings(viewModel: GameViewModel) {
    val settings = viewModel.uiState.settings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        SettingsToggle("Move coach", settings.coachEnabled) { viewModel.updateSettings(settings.copy(coachEnabled = it)) }
        SectionTitle("YOUR RATING")
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1200, 1800, 2200).forEach { elo ->
                OutlinedButton(
                    onClick = { viewModel.updateSettings(settings.copy(playerElo = elo)) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (settings.playerElo == elo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface),
                ) { Text(elo.toString()) }
            }
        }
        Text("This rating tells the coach which human-strength Maia decisions to compare your moves against. You can still choose a different opponent strength on Play.", modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DataSettings(
    viewModel: GameViewModel,
    username: String,
    onUsername: (String) -> Unit,
    onImportPgn: () -> Unit,
    onExportSaved: () -> Unit,
) {
    val ui = viewModel.uiState
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Import PGN", style = MaterialTheme.typography.titleLarge)
        Text("Optional: give Mirror an instant head start with old Chess.com, Lichess, or other PGN games.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = username,
            onValueChange = onUsername,
            label = { Text("Your exact PGN username") },
            supportingText = { Text(if (username.isBlank()) "Required so Mirror never learns the opponent's moves." else "Mirror will learn only games played under this name.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onImportPgn, enabled = username.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Import PGN") }
        ui.mirrorImportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 6.dp))
        Text("Export games", style = MaterialTheme.typography.titleLarge)
        Text("Export your saved MirrorChess games as standard PGN so they can be opened in other chess tools.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onExportSaved, enabled = ui.recentGames.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Export saved games") }
    }
}

@Composable
private fun AboutSettings() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("MirrorChess", style = MaterialTheme.typography.titleLarge)
                Text("by Night", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Version 1.2.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Maia inference and Mirror personalization run on your device after the model is installed. Imported PGNs, your Mirror profile, active games, and saved games are kept in app-private storage. Android cloud backup is disabled.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Model licensing", style = MaterialTheme.typography.titleMedium)
        Text("The optional Maia-3 model has separate AGPL-3.0 licensing obligations documented in THIRD_PARTY_NOTICES.md in the project.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp))
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String,
    trailing: String,
    selected: Boolean,
    enabled: Boolean = true,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(66.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.alpha(if (enabled) 1f else .35f)) { leading() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).alpha(if (enabled) 1f else .45f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(trailing, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BoardPalettePreview(palette: BoardPalette) {
    val colors = when (palette) {
        BoardPalette.CLASSIC -> Color(0xFFE8E9D0) to Color(0xFF779455)
        BoardPalette.WALNUT -> Color(0xFFE0C9A6) to Color(0xFF8B5E3C)
        BoardPalette.SLATE -> Color(0xFFD3D7D8) to Color(0xFF59656A)
        BoardPalette.OCEAN -> Color(0xFFD7E2E4) to Color(0xFF4E7581)
    }
    Surface(Modifier.size(38.dp), shape = RoundedCornerShape(10.dp), color = colors.first) {
        Box {
            Box(Modifier.size(22.dp).align(Alignment.BottomEnd).background(colors.second))
        }
    }
}

@Composable
private fun PieceSetIdPreview(style: PieceSetId, shadow: Boolean) {
    Surface(Modifier.size(38.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        ChessPieceArt(
            type = PieceType.KNIGHT,
            side = Side.WHITE,
            style = style.name.lowercase(),
            shadow = shadow,
            modifier = Modifier.padding(3.dp),
        )
    }
}

@Composable
private fun PieceCustomPreview(setId: String, shadow: Boolean) {
    Surface(Modifier.size(38.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        ChessPieceArt(PieceType.KNIGHT, Side.WHITE, Modifier.padding(3.dp), style = setId, shadow = shadow)
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun StyleMeter(label: String, value: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text("${(value.coerceIn(0f, 1f) * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(5.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChecked).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
