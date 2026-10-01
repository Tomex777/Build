package com.night.cortex.server

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.night.cortex.hosting.HostingFileEntry
import com.night.cortex.hosting.HostingPowerAction
import com.night.cortex.hosting.canSaveHttpsConnection
import com.night.cortex.hosting.isValidHttpsEndpoint
import com.night.cortex.ui.theme.CortexAccent
import com.night.cortex.ui.theme.CortexBackground
import com.night.cortex.ui.theme.CortexDanger
import com.night.cortex.ui.theme.CortexGood
import com.night.cortex.ui.theme.CortexHeader
import com.night.cortex.ui.theme.CortexLine
import com.night.cortex.ui.theme.CortexMuted
import com.night.cortex.ui.theme.CortexSurface
import com.night.cortex.ui.theme.CortexSurface2
import com.night.cortex.ui.theme.CortexText
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import kotlin.math.roundToInt

private enum class ServerTab(val label: String) {
    CONSOLE("Console"),
    PAIRING("Pairing"),
    FILES("Files"),
    ENVIRONMENT("Environment"),
    BACKUPS("Backups"),
    STARTUP("Startup"),
    SETTINGS("Settings"),
    ACTIVITY("Activity"),
}

private enum class SheetMode {
    CONNECTION,
    NEW_FILE,
    NEW_DIRECTORY,
    FILE_ACTIONS,
    RENAME,
    MOVE,
    BACKUP,
    NEW_COMMAND,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CortexServerApp(vm: ServerPanelViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(ServerTab.CONSOLE) }
    var sheet by remember { mutableStateOf<SheetMode?>(null) }
    var selectedEntry by remember { mutableStateOf<HostingFileEntry?>(null) }
    var deleteCandidate by remember { mutableStateOf<HostingFileEntry?>(null) }

    val uploadLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.upload(uri)
        }
    }

    val saveBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            vm.exportPendingDownload(uri)
        } else {
            vm.consumePendingDownload()
        }
    }

    LaunchedEffect(state.pendingDownload?.name) {
        state.pendingDownload?.let { saveBackupLauncher.launch(it.name) }
    }

    if (state.selectedFile != null) {
        EditorScreen(
            path = state.selectedFile.orEmpty(),
            content = state.editorContent,
            dirty = state.editorDirty,
            busy = state.loading,
            onBack = vm::closeEditor,
            onChange = vm::updateEditor,
            onSave = vm::saveEditor,
        )
        return
    }

    Scaffold(containerColor = CortexBackground) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
        ) {
            Header(
                configured = state.configured,
                reachable = state.agentReachable,
                reconnecting = state.reconnecting,
                authFailed = state.authFailed,
                lastSuccessfulSyncAt = state.lastSuccessfulSyncAt,
                state = state.snapshot?.state,
            )
            ServerTabs(tab = tab, onTab = { next ->
                if (next != tab) {
                    tab = next
                    when (next) {
                        ServerTab.CONSOLE -> vm.refreshConsole()
                        ServerTab.PAIRING -> vm.refreshPairing()
                        ServerTab.FILES -> vm.refreshFiles()
                        ServerTab.ENVIRONMENT -> vm.refreshEnvironment()
                        ServerTab.BACKUPS -> vm.refreshBackups()
                        ServerTab.ACTIVITY -> vm.refreshActivity()
                        ServerTab.SETTINGS -> vm.refreshSettings()
                        ServerTab.STARTUP -> vm.refreshStartup()
                    }
                }
            })
            HorizontalDivider(color = CortexLine)

            if (!state.configured) {
                NotConnected(onConnect = { sheet = SheetMode.CONNECTION })
            } else {
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        ServerTab.CONSOLE -> ConsolePage(state, vm::power, vm::refreshConsole, vm::clearConsole)
                        ServerTab.PAIRING -> CortexPairingScreen(
                            state = state.pairing,
                            busy = state.loading,
                            onRefresh = vm::refreshPairing,
                            onAddAccount = vm::addAccount,
                            onDestination = vm::setDestination,
                            onPair = vm::pairAccount,
                            onReconnect = vm::reconnectPairing,
                            onDisconnect = vm::disconnectPairing,
                            onRemove = vm::removePairing,
                            onRepair = vm::repairAccount,
                        )
                        ServerTab.FILES -> FilesPage(
                            state = state,
                            onUp = vm::goUp,
                            onPath = vm::goToPath,
                            onRefresh = vm::refreshFiles,
                            onOpen = { entry ->
                                if (entry.type == "directory") vm.openDirectory(entry.name) else vm.openFile(entry.name)
                            },
                            onMore = {
                                selectedEntry = it
                                sheet = SheetMode.FILE_ACTIONS
                            },
                            onNewFile = { sheet = SheetMode.NEW_FILE },
                            onNewDirectory = { sheet = SheetMode.NEW_DIRECTORY },
                            onUpload = { uploadLauncher.launch(arrayOf("*/*")) },
                        )
                        ServerTab.ENVIRONMENT -> EnvironmentPage(
                            state = state,
                            onReveal = vm::revealEnvironment,
                            onHide = vm::hideEnvironment,
                            onSave = vm::saveEnvironment,
                        )
                        ServerTab.BACKUPS -> BackupsPage(
                            state = state,
                            onRefresh = vm::refreshBackups,
                            onDownloadProject = vm::downloadProjectBackup,
                            onCreate = { sheet = SheetMode.BACKUP },
                            onDownload = vm::prepareBackupDownload,
                            onDelete = vm::deleteBackup,
                            onRestore = vm::restoreBackup,
                        )
                        ServerTab.STARTUP -> StartupPage(
                            state = state,
                            installDependencies = vm::installDependencies,
                            power = vm::power,
                            setStartupEnabled = vm::setStartupEnabled,
                        )
                        ServerTab.SETTINGS -> SettingsPage(
                            state = state,
                            onConnection = { sheet = SheetMode.CONNECTION },
                            onToggle = vm::setCommandSetting,
                            onRefresh = vm::refreshSettings,
                            onNewCommand = { sheet = SheetMode.NEW_COMMAND },
                            onReloadCommands = vm::reloadCommands,
                            onReloadModule = vm::reloadModule,
                        )
                        ServerTab.ACTIVITY -> ActivityPage(state, vm::refreshActivity)
                    }
                }
            }
        }
    }

    state.error?.let { error ->
        StatusBar(text = error, bad = true, onDismiss = vm::clearMessage)
    } ?: state.message?.let { message ->
        StatusBar(text = message, bad = false, onDismiss = vm::clearMessage)
    }

    when (sheet) {
        SheetMode.CONNECTION -> ConnectionSheet(
            initialUrl = state.baseUrl,
            hasToken = state.hasToken,
            onDismiss = { sheet = null },
            onSave = { url, token ->
                vm.saveConnection(url, token)
                sheet = null
            },
            onForget = {
                vm.forgetConnection()
                sheet = null
            },
        )
        SheetMode.NEW_FILE -> NameSheet(
            title = "New File",
            label = "File name",
            action = "Create",
            onDismiss = { sheet = null },
            onSubmit = {
                vm.createFile(it)
                sheet = null
            },
        )
        SheetMode.NEW_DIRECTORY -> NameSheet(
            title = "Create Directory",
            label = "Directory name",
            action = "Create",
            onDismiss = { sheet = null },
            onSubmit = {
                vm.createDirectory(it)
                sheet = null
            },
        )
        SheetMode.FILE_ACTIONS -> selectedEntry?.let { entry ->
            FileActionsSheet(
                entry = entry,
                onDismiss = { sheet = null },
                onEdit = {
                    sheet = null
                    vm.openFile(entry.name)
                },
                onRename = { sheet = SheetMode.RENAME },
                onMove = { sheet = SheetMode.MOVE },
                onDuplicate = {
                    sheet = null
                    vm.duplicate(entry)
                },
                onDownload = {
                    sheet = null
                    vm.prepareFileDownload(entry)
                },
                onCompress = {
                    sheet = null
                    vm.compress(entry)
                },
                onExtract = {
                    sheet = null
                    vm.extract(entry)
                },
                onDelete = {
                    sheet = null
                    deleteCandidate = entry
                },
            )
        }
        SheetMode.RENAME -> selectedEntry?.let { entry ->
            NameSheet(
                title = "Rename",
                label = "New name",
                initial = entry.name,
                action = "Rename",
                onDismiss = { sheet = null },
                onSubmit = {
                    vm.rename(entry, it)
                    sheet = null
                },
            )
        }
        SheetMode.MOVE -> selectedEntry?.let { entry ->
            val current = state.currentPath.trimEnd('/').ifBlank { "" }
            NameSheet(
                title = "Move",
                label = "Destination path",
                initial = "$current/${entry.name}",
                action = "Move",
                onDismiss = { sheet = null },
                onSubmit = {
                    vm.move(entry, it)
                    sheet = null
                },
            )
        }
        SheetMode.BACKUP -> BackupSheet(
            onDismiss = { sheet = null },
            onCreate = { privateBackup ->
                vm.createBackup(privateBackup)
                sheet = null
            },
        )
        SheetMode.NEW_COMMAND -> NameSheet(
            title = "New Command",
            label = "Command name",
            action = "Create & edit",
            onDismiss = { sheet = null },
            onSubmit = {
                vm.createCommand(it)
                sheet = null
            },
        )
        null -> Unit
    }

    deleteCandidate?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete ${entry.name}?") },
            text = {
                Text(
                    if (entry.type == "directory") {
                        "This permanently deletes the directory and everything inside it from the server workspace."
                    } else {
                        "This permanently deletes the file from the server workspace."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCandidate = null
                        vm.delete(entry)
                    }
                ) { Text("Delete", color = CortexDanger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Header(
    configured: Boolean,
    reachable: Boolean,
    reconnecting: Boolean,
    authFailed: Boolean,
    lastSuccessfulSyncAt: Long?,
    state: String?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CortexHeader)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Cortex", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    !configured -> "Server connection is not configured"
                    authFailed -> "Sign-in failed · update access token"
                    reachable && lastSuccessfulSyncAt != null &&
                        System.currentTimeMillis() - lastSuccessfulSyncAt > 120_000L ->
                        "Connected · status may be stale"
                    reachable -> "Connected"
                    reconnecting -> "Server unavailable · retrying"
                    lastSuccessfulSyncAt != null -> "Server unavailable · showing last known state"
                    else -> "Server unavailable · retrying"
                },
                color = CortexMuted,
                fontSize = 11.sp,
            )
        }
        if (configured) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when {
                    authFailed -> CortexDanger.copy(alpha = .28f)
                    !reachable -> CortexDanger.copy(alpha = .28f)
                    state.equals("active", true) || state.equals("running", true) -> CortexGood.copy(alpha = .24f)
                    else -> CortexSurface2
                },
            ) {
                Text(
                    when {
                        authFailed -> "SIGN-IN"
                        !reachable -> if (lastSuccessfulSyncAt != null) "STALE" else "OFFLINE"
                        lastSuccessfulSyncAt != null &&
                            System.currentTimeMillis() - lastSuccessfulSyncAt > 120_000L -> "STALE"
                        state.isNullOrBlank() -> "CONNECTED"
                        else -> state.uppercase()
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ServerTabs(tab: ServerTab, onTab: (ServerTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(CortexHeader)
            .padding(horizontal = 8.dp),
    ) {
        ServerTab.entries.forEach { item ->
            val selected = item == tab
            Column(
                Modifier
                    .selectable(
                        selected = selected,
                        enabled = !selected,
                        onClick = { onTab(item) },
                        role = Role.Tab,
                    )
                    .testTag("server-tab-${item.name.lowercase()}")
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    item.label,
                    color = if (selected) Color.White else CortexMuted,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .height(2.dp)
                        .width(28.dp)
                        .background(if (selected) CortexAccent else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun NotConnected(onConnect: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = 520.dp),
            color = CortexSurface,
            shape = RoundedCornerShape(5.dp),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Connect server", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Connect Cortex to your server. MSCC keeps running even when this app is closed.",
                    color = CortexMuted,
                    fontSize = 12.sp,
                )
                Button(onClick = onConnect) { Text("Connect") }
            }
        }
    }
}

@Composable
internal fun ConsolePage(
    state: ServerPanelState,
    power: (HostingPowerAction) -> Unit,
    refresh: () -> Unit,
    clear: () -> Unit,
) {
    val snapshot = state.snapshot
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf("ALL") }
    val visibleLogs = remember(state.logs, query, level) {
        val needle = query.trim().lowercase()
        state.logs.filter { line ->
            val lower = line.lowercase()
            val matchesText = needle.isBlank() || lower.contains(needle)
            val matchesLevel = when (level) {
                "ERROR" -> lower.contains("error") || lower.contains("exception") || lower.contains("fatal") || lower.contains("fail")
                "WARN" -> lower.contains("warn") || lower.contains("warning")
                else -> true
            }
            matchesText && matchesLevel
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CortexPowerControls(
                busy = state.loading,
                onPower = power,
            )
        }

        item {
            Surface(color = Color(0xFF131A20), shape = RoundedCornerShape(4.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Console", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(
                            "${visibleLogs.size}/${state.logs.size}",
                            color = CortexMuted,
                            fontSize = 8.sp,
                        )
                        TextButton(
                            onClick = {
                                level = when (level) {
                                    "ALL" -> "WARN"
                                    "WARN" -> "ERROR"
                                    else -> "ALL"
                                }
                            }
                        ) {
                            Text(level, fontSize = 8.sp)
                        }
                        TextButton(onClick = clear, enabled = state.logs.isNotEmpty()) {
                            Text("CLEAR", fontSize = 8.sp)
                        }
                        IconButton(onClick = refresh, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Refresh, "Refresh logs", Modifier.size(16.dp))
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        label = { Text("Filter console") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(16.dp)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    )
                    HorizontalDivider(color = CortexLine)
                    SelectionContainer {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            if (visibleLogs.isEmpty()) {
                                Text(
                                    if (state.logs.isEmpty()) "No console output returned." else "No console lines match this filter.",
                                    color = CortexMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            } else {
                                visibleLogs.takeLast(300).forEach { line ->
                                    val lower = line.lowercase()
                                    val color = when {
                                        lower.contains("error") || lower.contains("exception") || lower.contains("fatal") || lower.contains("fail") -> CortexDanger
                                        lower.contains("warn") || lower.contains("warning") -> Color(0xFFFACC15)
                                        else -> Color(0xFFE5E8EB)
                                    }
                                    Text(
                                        line,
                                        color = color,
                                        fontSize = 9.sp,
                                        lineHeight = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard(
                        label = "CPU Load",
                        value = snapshot?.cpuPercent?.let { "${it.roundToInt()}%" } ?: "—",
                        sub = "/ 100%",
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = "Memory",
                        value = bytes(snapshot?.memoryUsedBytes),
                        sub = snapshot?.memoryLimitBytes?.let { "/ ${bytes(it)}" }.orEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard(
                        label = "Disk",
                        value = bytes(snapshot?.diskUsedBytes),
                        sub = snapshot?.diskLimitBytes?.let { "/ ${bytes(it)}" }.orEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                    MetricCard(
                        label = "Uptime",
                        value = uptime(snapshot?.uptimeMs),
                        sub = "",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}


@Composable
fun CortexPowerControls(
    busy: Boolean,
    onPower: (HostingPowerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pending by remember { mutableStateOf<HostingPowerAction?>(null) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Button(
            onClick = { onPower(HostingPowerAction.START) },
            enabled = !busy,
            modifier = Modifier.weight(1f).testTag("power-start"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
            shape = RoundedCornerShape(4.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Start", fontSize = 10.sp)
        }
        Button(
            onClick = { pending = HostingPowerAction.RESTART },
            enabled = !busy,
            modifier = Modifier.weight(1f).testTag("power-restart"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAB308)),
            shape = RoundedCornerShape(4.dp),
        ) {
            Icon(Icons.Rounded.RestartAlt, null, Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Restart", fontSize = 10.sp)
        }
        Button(
            onClick = { pending = HostingPowerAction.STOP },
            enabled = !busy,
            modifier = Modifier.weight(1f).testTag("power-stop"),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
            shape = RoundedCornerShape(4.dp),
        ) {
            Icon(Icons.Rounded.Stop, null, Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Stop", fontSize = 10.sp)
        }
    }

    pending?.let { action ->
        val restart = action == HostingPowerAction.RESTART
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (restart) "Restart Night?" else "Stop Night?") },
            text = {
                Text(
                    if (restart) {
                        "Night will be briefly unavailable while it restarts. Saved sessions and project data are not deleted."
                    } else {
                        "Night will go offline and stay stopped until you start it again. Saved sessions and project data are not deleted."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        onPower(action)
                    },
                    modifier = Modifier.testTag(if (restart) "confirm-power-restart" else "confirm-power-stop"),
                ) {
                    Text(if (restart) "Restart" else "Stop", color = if (restart) Color(0xFFEAB308) else CortexDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = CortexSurface, shape = RoundedCornerShape(4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = CortexMuted, fontSize = 10.sp)
            Spacer(Modifier.height(7.dp))
            Text(value, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (sub.isNotBlank()) Text(sub, color = CortexMuted, fontSize = 9.sp)
        }
    }
}

@Composable
internal fun FilesPage(
    state: ServerPanelState,
    onUp: () -> Unit,
    onPath: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (HostingFileEntry) -> Unit,
    onMore: (HostingFileEntry) -> Unit,
    onNewFile: () -> Unit,
    onNewDirectory: () -> Unit,
    onUpload: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Breadcrumbs(state.currentPath, onPath)
        HorizontalDivider(color = CortexLine)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallAction("Create Directory", Icons.Rounded.CreateNewFolder, onNewDirectory)
            SmallAction("Upload", Icons.Rounded.Upload, onUpload)
            SmallAction("New File", Icons.Rounded.NoteAdd, onNewFile)
            SmallAction("Refresh", Icons.Rounded.Refresh, onRefresh)
        }
        HorizontalDivider(color = CortexLine)
        if (state.currentPath != "/") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onUp)
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ArrowUpward, null, tint = CortexMuted)
                Spacer(Modifier.width(10.dp))
                Text("..", fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(color = CortexLine)
        }
        if (state.files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This directory is empty.", color = CortexMuted)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.files, key = { it.name }) { entry ->
                    FileRow(entry, onOpen = { onOpen(entry) }, onMore = { onMore(entry) })
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(path: String, onPath: (String) -> Unit) {
    val parts = path.trim('/').split('/').filter(String::isNotBlank)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("/", color = CortexAccent, modifier = Modifier.clickable { onPath("/") })
        var current = ""
        parts.forEach { part ->
            current += "/$part"
            val target = current
            Text("  /  ", color = CortexMuted)
            Text(part, color = CortexAccent, modifier = Modifier.clickable { onPath(target) })
        }
    }
}

@Composable
private fun SmallAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) {
    OutlinedButton(
        onClick = click,
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Icon(icon, null, Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 10.sp)
    }
}

@Composable
private fun FileRow(entry: HostingFileEntry, onOpen: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 13.dp, end = 5.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (entry.type == "directory") Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
            null,
            tint = if (entry.type == "directory") Color(0xFF60A5FA) else CortexMuted,
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (entry.type == "directory") (entry.modifiedAt ?: "") else "${bytes(entry.sizeBytes)}  ${entry.modifiedAt.orEmpty()}",
                color = CortexMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, "File actions") }
    }
    HorizontalDivider(color = CortexLine)
}

@Composable
internal fun BackupsPage(
    state: ServerPanelState,
    onRefresh: () -> Unit,
    onDownloadProject: () -> Unit,
    onCreate: () -> Unit,
    onDownload: (BackupEntry) -> Unit,
    onDelete: (BackupEntry) -> Unit,
    onRestore: (BackupEntry) -> Unit,
) {
    var deleting by remember { mutableStateOf<BackupEntry?>(null) }
    var restoring by remember { mutableStateOf<BackupEntry?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Backups", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Refresh backups") }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onDownloadProject,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(4.dp),
            ) {
                Icon(Icons.Rounded.Download, null, Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Download Project ZIP", fontSize = 10.sp)
            }
            OutlinedButton(onClick = onCreate, shape = RoundedCornerShape(4.dp)) {
                Text("More", fontSize = 10.sp)
            }
        }
        HorizontalDivider(color = CortexLine)
        if (state.backups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No Cortex backups yet.", color = CortexMuted)
                }
            }
        } else {
            LazyColumn {
                items(state.backups, key = { it.name }) { backup ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (backup.privateBackup) Icons.Rounded.Warning else Icons.Rounded.Archive, null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(backup.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${bytes(backup.sizeBytes)} · ${backup.createdAt}",
                                color = CortexMuted,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!backup.privateBackup) {
                            IconButton(onClick = { restoring = backup }) {
                                Icon(Icons.Rounded.Unarchive, "Restore project backup")
                            }
                        }
                        IconButton(onClick = { onDownload(backup) }) {
                            Icon(Icons.Rounded.Download, "Download backup")
                        }
                        IconButton(onClick = { deleting = backup }) {
                            Icon(Icons.Rounded.Delete, "Delete backup", tint = CortexDanger)
                        }
                    }
                    HorizontalDivider(color = CortexLine)
                }
            }
        }
    }

    restoring?.let { backup ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text("Restore project backup?") },
            text = {
                Text(
                    "Cortex will create a new safety backup first, then restore source files from this project ZIP. " +
                        "Private backups are not restored through this action."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    restoring = null
                    onRestore(backup)
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { restoring = null }) { Text("Cancel") }
            },
        )
    }

    deleting?.let { backup ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete backup?") },
            text = {
                Text(
                    if (backup.privateBackup) {
                        "This private backup may include saved sign-in and other private app state. The ZIP will be permanently deleted from the server."
                    } else {
                        "The project backup ZIP will be permanently deleted from the server."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    onDelete(backup)
                }) { Text("Delete", color = CortexDanger) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
internal fun StartupPage(
    state: ServerPanelState,
    installDependencies: () -> Unit,
    power: (HostingPowerAction) -> Unit,
    setStartupEnabled: (Boolean) -> Unit,
) {
    val startup = state.startup
    val snapshot = state.snapshot
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(color = CortexSurface, shape = RoundedCornerShape(4.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Night", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${snapshot?.state ?: "unknown"} · uptime ${uptime(snapshot?.uptimeMs)} · RAM ${bytes(snapshot?.memoryUsedBytes)}",
                                color = CortexMuted,
                                fontSize = 9.sp,
                            )
                        }
                        Surface(
                            color = if (snapshot?.state.equals("active", true) || snapshot?.state.equals("running", true)) Color(0xFF166534) else CortexSurface2,
                            shape = RoundedCornerShape(3.dp),
                        ) {
                            Text(
                                (snapshot?.state ?: "unknown").uppercase(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    CortexPowerControls(
                        busy = state.loading,
                        onPower = power,
                    )
                }
            }
        }
        item {
            Surface(color = CortexSurface, shape = RoundedCornerShape(4.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Start Night at boot", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(
                            when (startup?.startupMode) {
                                "enabled" -> "Starts automatically"
                                "disabled" -> "Does not start automatically"
                                else -> "Startup status unavailable"
                            },
                            color = CortexMuted,
                            fontSize = 9.sp,
                        )
                    }
                    Switch(
                        checked = startup?.startupMode == "enabled",
                        onCheckedChange = setStartupEnabled,
                        enabled = startup != null && startup.startupMode != "unknown" && !state.loading,
                    )
                }
            }
        }
        item { SettingBlock("Runtime", startup?.let { "${it.runtime} ${it.version}" } ?: "Not reported") }
        item {
            Surface(color = CortexSurface, shape = RoundedCornerShape(4.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("Dependencies", color = CortexMuted, fontSize = 10.sp)
                    Spacer(Modifier.height(7.dp))
                    if (startup?.additionalNodePackages.isNullOrEmpty()) {
                        Text("No extra dependencies.", fontSize = 11.sp)
                    } else {
                        Text(
                            startup.additionalNodePackages.joinToString("  "),
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = installDependencies, shape = RoundedCornerShape(4.dp)) {
                        Text("Install dependencies")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingBlock(label: String, value: String, mono: Boolean = false) {
    Surface(color = CortexSurface, shape = RoundedCornerShape(4.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, color = CortexMuted, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 12.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default)
        }
    }
}

@Composable
internal fun SettingsPage(
    state: ServerPanelState,
    onConnection: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onRefresh: () -> Unit,
    onNewCommand: () -> Unit,
    onReloadCommands: () -> Unit,
    onReloadModule: (String) -> Unit,
) {
    val registry = state.runtimeRegistry
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Night settings", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Manage the commands, modules, and settings used by Night.",
                        color = CortexMuted,
                        fontSize = 10.sp,
                    )
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Refresh settings") }
            }
        }

        item {
            Text(
                "Modules",
                color = CortexMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (registry == null) {
            item {
                Surface(color = CortexSurface, shape = RoundedCornerShape(4.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Settings unavailable", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Cortex will keep the rest of the app usable and retry when server settings are available.",
                            color = CortexMuted,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        } else if (registry.modules.isEmpty()) {
            item {
                Surface(color = CortexSurface, shape = RoundedCornerShape(4.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("No modules available", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            items(registry.modules, key = { it.id }) { module ->
                Surface(color = CortexSurface, shape = RoundedCornerShape(4.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(module.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${module.id}${module.version.takeIf(String::isNotBlank)?.let { " · v$it" }.orEmpty()}",
                                    color = CortexMuted,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            Text(
                                if (module.enabled) module.status.uppercase() else "DISABLED",
                                color = if (module.loadError.isBlank()) CortexGood else CortexDanger,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (module.moduleDirectory.isNotBlank()) {
                            Spacer(Modifier.height(7.dp))
                            Text(module.moduleDirectory, color = CortexMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                        if (module.commands.isNotEmpty()) {
                            Spacer(Modifier.height(7.dp))
                            Text(
                                "Commands: " + module.commands.joinToString("  ") { ".$it" },
                                color = CortexAccent,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        if (module.configuration.isNotEmpty()) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Configuration: " + module.configuration.joinToString(", ") { it.label.ifBlank { it.key } },
                                color = CortexMuted,
                                fontSize = 8.sp,
                            )
                        }
                        if (module.dependencies.isNotEmpty() || module.permissions.isNotEmpty()) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                listOfNotNull(
                                    module.dependencies.takeIf { it.isNotEmpty() }?.let { "Deps: " + it.joinToString(", ") },
                                    module.permissions.takeIf { it.isNotEmpty() }?.let { "Permissions: " + it.joinToString(", ") },
                                ).joinToString(" · "),
                                color = CortexMuted,
                                fontSize = 8.sp,
                            )
                        }
                        if (module.loadError.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                module.loadError,
                                color = CortexDanger,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                module.lastReload.takeIf(String::isNotBlank)?.let { "Last reload: $it" }.orEmpty(),
                                modifier = Modifier.weight(1f),
                                color = CortexMuted,
                                fontSize = 8.sp,
                            )
                            TextButton(
                                onClick = { onReloadModule(module.id) },
                                enabled = !state.loading,
                            ) {
                                Text("RELOAD", fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Commands",
                    modifier = Modifier.weight(1f),
                    color = CortexMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (registry != null) {
                    Text(
                        "${registry.commands.size} commands",
                        color = CortexMuted,
                        fontSize = 8.sp,
                    )
                }
            }
        }
        if (registry?.commands?.isNotEmpty() == true) {
            items(registry.commands, key = { "${it.moduleId}:${it.name}" }) { command ->
                Surface(color = CortexSurface, shape = RoundedCornerShape(4.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                ".${command.name}",
                                modifier = Modifier.weight(1f),
                                color = CortexAccent,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                command.moduleId.ifBlank { "runtime" },
                                color = CortexMuted,
                                fontSize = 8.sp,
                            )
                        }
                        if (command.description.isNotBlank()) {
                            Text(command.description, color = CortexMuted, fontSize = 9.sp)
                        }
                        val metadata = listOfNotNull(
                            command.aliases.takeIf { it.isNotEmpty() }?.let { "aliases " + it.joinToString(", ") },
                            command.permission.takeIf(String::isNotBlank)?.let { "access $it" },
                            command.usage.takeIf(String::isNotBlank),
                        )
                        if (metadata.isNotEmpty()) {
                            Spacer(Modifier.height(5.dp))
                            Text(metadata.joinToString(" · "), color = CortexMuted, fontSize = 8.sp)
                        }
                        if (command.error.isNotBlank()) {
                            Spacer(Modifier.height(5.dp))
                            Text(command.error, color = CortexDanger, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onNewCommand,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Icon(Icons.Rounded.NoteAdd, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("New command", fontSize = 9.sp)
                }
                OutlinedButton(
                    onClick = onReloadCommands,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Reload commands", fontSize = 9.sp)
                }
            }
        }

        item {
            Text(
                "Configuration",
                color = CortexMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (state.commandSettings.isEmpty()) {
            item {
                Surface(color = CortexSurface, shape = RoundedCornerShape(4.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("No quick settings available", fontWeight = FontWeight.Medium)
                        Text(
                            "Some modules may provide their own settings above.",
                            color = CortexMuted,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        } else {
            items(state.commandSettings, key = { it.key }) { setting ->
                Surface(color = CortexSurface, shape = RoundedCornerShape(4.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(setting.key, !setting.enabled) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(setting.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            if (setting.description.isNotBlank()) {
                                Text(setting.description, color = CortexMuted, fontSize = 9.sp)
                            }
                            if (setting.command.isNotBlank()) {
                                Text("." + setting.command, color = CortexAccent, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Switch(
                            checked = setting.enabled,
                            onCheckedChange = { onToggle(setting.key, it) },
                            enabled = !state.loading,
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("Cortex", color = CortexMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onConnection),
                color = CortexSurface,
                shape = RoundedCornerShape(4.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Settings, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Server connection")
                        Text(state.baseUrl.ifBlank { "Not configured" }, color = CortexMuted, fontSize = 9.sp)
                    }
                    Text("Edit", color = CortexAccent, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable

internal fun ActivityPage(state: ServerPanelState, refresh: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Activity", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = refresh) { Text("Refresh") }
        }
        HorizontalDivider(color = CortexLine)
        if (state.activity.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No activity recorded yet.", color = CortexMuted)
            }
        } else {
            LazyColumn {
                items(state.activity, key = { it.id }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Rounded.History, null, tint = CortexMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(activityTitle(entry.action), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            val detail = activityDetail(entry)
                            if (detail.isNotBlank()) {
                                Text(detail, color = CortexMuted, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text(activityTime(entry.at), color = CortexMuted, fontSize = 8.sp)
                    }
                    HorizontalDivider(color = CortexLine)
                }
            }
        }
    }
}

@Composable
internal fun EditorScreen(
    path: String,
    content: String,
    dirty: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    var editor by remember(path) { mutableStateOf(TextFieldValue(content)) }
    var undoStack by remember(path) { mutableStateOf(emptyList<TextFieldValue>()) }
    var redoStack by remember(path) { mutableStateOf(emptyList<TextFieldValue>()) }
    var searchVisible by remember(path) { mutableStateOf(false) }
    var search by remember(path) { mutableStateOf("") }
    var replacement by remember(path) { mutableStateOf("") }
    var confirmDiscard by remember(path) { mutableStateOf(false) }

    LaunchedEffect(content) {
        if (content != editor.text) {
            val cursor = editor.selection.end.coerceIn(0, content.length)
            editor = editor.copy(text = content, selection = TextRange(cursor))
        }
    }

    fun applyValue(next: TextFieldValue, trackHistory: Boolean = true) {
        if (next.text != editor.text) {
            if (trackHistory) {
                undoStack = (undoStack + editor).takeLast(100)
                redoStack = emptyList()
            }
            onChange(next.text)
        }
        editor = next
    }

    fun undo() {
        val previous = undoStack.lastOrNull() ?: return
        undoStack = undoStack.dropLast(1)
        redoStack = (redoStack + editor).takeLast(100)
        editor = previous
        onChange(previous.text)
    }

    fun redo() {
        val next = redoStack.lastOrNull() ?: return
        redoStack = redoStack.dropLast(1)
        undoStack = (undoStack + editor).takeLast(100)
        editor = next
        onChange(next.text)
    }

    fun findNext() {
        if (search.isEmpty()) return
        val from = editor.selection.end.coerceIn(0, editor.text.length)
        val direct = editor.text.indexOf(search, startIndex = from)
        val found = if (direct >= 0) direct else editor.text.indexOf(search)
        if (found >= 0) editor = editor.copy(selection = TextRange(found, found + search.length))
    }

    fun replaceSelection() {
        if (search.isEmpty()) return
        val start = editor.selection.min
        val end = editor.selection.max
        val selected = if (end > start) editor.text.substring(start, end) else ""
        if (selected != search) {
            findNext()
            return
        }
        val nextText = editor.text.replaceRange(start, end, replacement)
        applyValue(
            editor.copy(
                text = nextText,
                selection = TextRange(start + replacement.length),
            )
        )
    }

    fun replaceAll() {
        if (search.isEmpty()) return
        val nextText = editor.text.replace(search, replacement)
        if (nextText != editor.text) {
            applyValue(editor.copy(text = nextText, selection = TextRange(0)))
        }
    }

    val cursor = editor.selection.end.coerceIn(0, editor.text.length)
    val beforeCursor = editor.text.take(cursor)
    val line = beforeCursor.count { it == '\n' } + 1
    val column = beforeCursor.substringAfterLast('\n').length + 1
    val totalLines = editor.text.count { it == '\n' } + 1
    val requestBack = {
        if (dirty) confirmDiscard = true else onBack()
    }

    BackHandler(onBack = requestBack)

    Column(Modifier.fillMaxSize().background(CortexBackground)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = requestBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        path.substringAfterLast('/'),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (dirty) {
                        Spacer(Modifier.width(6.dp))
                        Text("MODIFIED", color = CortexAccent, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "Ln $line, Col $column · $totalLines lines · $path",
                    color = CortexMuted,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = ::undo, enabled = undoStack.isNotEmpty() && !busy) {
                Icon(Icons.Rounded.Undo, "Undo")
            }
            IconButton(onClick = ::redo, enabled = redoStack.isNotEmpty() && !busy) {
                Icon(Icons.Rounded.Redo, "Redo")
            }
            IconButton(onClick = { searchVisible = !searchVisible }, enabled = !busy) {
                Icon(Icons.Rounded.Search, "Search and replace")
            }
            Button(
                onClick = onSave,
                enabled = dirty && !busy,
                shape = RoundedCornerShape(4.dp),
            ) {
                Icon(Icons.Rounded.Save, null, Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Save", fontSize = 10.sp)
            }
        }

        if (searchVisible) {
            HorizontalDivider(color = CortexLine)
            Column(
                Modifier.fillMaxWidth().background(CortexSurface).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Find") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Replace") },
                        singleLine = true,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = ::findNext, enabled = search.isNotEmpty()) {
                        Text("Next", fontSize = 9.sp)
                    }
                    OutlinedButton(onClick = ::replaceSelection, enabled = search.isNotEmpty() && !busy) {
                        Text("Replace", fontSize = 9.sp)
                    }
                    OutlinedButton(onClick = ::replaceAll, enabled = search.isNotEmpty() && !busy) {
                        Text("Replace all", fontSize = 9.sp)
                    }
                }
            }
        }

        HorizontalDivider(color = CortexLine)
        TextField(
            value = editor,
            onValueChange = { applyValue(it) },
            modifier = Modifier.fillMaxSize(),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            ),
            visualTransformation = remember(path) { CortexCodeVisualTransformation(path) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF131A20),
                unfocusedContainerColor = Color(0xFF131A20),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard unsaved changes?") },
            text = { Text("This file has changes that have not been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) {
                    Text("Discard", color = CortexDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            },
        )
    }
}

private class CortexCodeVisualTransformation(
    path: String,
) : VisualTransformation {
    private val extension = path.substringAfterLast('.', "").lowercase()

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val lineCount = source.count { it == '\n' } + 1
        val lineWidth = lineCount.toString().length.coerceAtLeast(2)
        val rendered = StringBuilder(source.length + lineCount * (lineWidth + 3))
        val originalToTransformed = IntArray(source.length + 1)
        val transformedToOriginal = ArrayList<Int>(source.length + lineCount * (lineWidth + 3) + 1)
        val prefixRanges = ArrayList<Pair<Int, Int>>(lineCount)

        fun appendPrefix(line: Int, originalOffset: Int) {
            val start = rendered.length
            val prefix = line.toString().padStart(lineWidth, ' ') + " │ "
            prefix.forEach { ch ->
                rendered.append(ch)
                transformedToOriginal.add(originalOffset)
            }
            prefixRanges += start to rendered.length
        }

        var line = 1
        appendPrefix(line, 0)
        for (index in source.indices) {
            originalToTransformed[index] = rendered.length
            rendered.append(source[index])
            transformedToOriginal.add(index)
            if (source[index] == '\n') {
                line += 1
                appendPrefix(line, index + 1)
            }
        }
        originalToTransformed[source.length] = rendered.length
        transformedToOriginal.add(source.length)

        val highlighted = buildAnnotatedString {
            append(rendered.toString())

            prefixRanges.forEach { (start, end) ->
                addStyle(
                    SpanStyle(color = CortexMuted),
                    start,
                    end,
                )
            }

            fun style(regex: Regex, spanStyle: SpanStyle) {
                regex.findAll(source).forEach { match ->
                    val start = originalToTransformed[match.range.first]
                    val end = originalToTransformed[match.range.last + 1]
                    if (start < end) addStyle(spanStyle, start, end)
                }
            }

            val keywords = when (extension) {
                "kt", "kts" -> listOf(
                    "package", "import", "class", "object", "data", "fun", "val", "var",
                    "private", "public", "internal", "suspend", "when", "if", "else",
                    "return", "try", "catch", "throw", "true", "false", "null",
                )
                "sh", "bash" -> listOf("if", "then", "else", "fi", "for", "do", "done", "case", "esac", "function")
                else -> listOf(
                    "import", "export", "from", "const", "let", "var", "function", "class",
                    "async", "await", "if", "else", "return", "try", "catch", "throw",
                    "new", "true", "false", "null", "undefined",
                )
            }

            if (keywords.isNotEmpty()) {
                style(
                    Regex("\\b(?:${keywords.joinToString("|") { Regex.escape(it) }})\\b"),
                    SpanStyle(color = CortexAccent, fontWeight = FontWeight.SemiBold),
                )
            }
            style(
                Regex("\\b\\d+(?:\\.\\d+)?\\b"),
                SpanStyle(color = Color(0xFF93C5FD)),
            )
            style(
                Regex("[\"'](?:\\\\.|[^\"'\\\\])*[\"']"),
                SpanStyle(color = Color(0xFFA7F3D0)),
            )
            style(
                Regex("(?m)(//|#).*?$"),
                SpanStyle(color = CortexMuted),
            )
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                originalToTransformed[offset.coerceIn(0, source.length)]

            override fun transformedToOriginal(offset: Int): Int =
                transformedToOriginal[offset.coerceIn(0, transformedToOriginal.lastIndex)]
        }
        return TransformedText(highlighted, mapping)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionSheet(
    initialUrl: String,
    hasToken: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onForget: () -> Unit,
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf("") }
    var confirmForget by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.navigationBarsPadding(),
        containerColor = CortexSurface,
        contentColor = CortexText,
        scrimColor = Color.Black.copy(alpha = .68f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .testTag("connection-sheet-root")
                .padding(start = 20.dp, end = 20.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Server connection", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Enter the secure server address and access token for this Cortex installation.", color = CortexMuted, fontSize = 12.sp)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Server URL") },
                placeholder = { Text("https://cortex.example.com") },
                colors = cortexConnectionTextFieldColors(),
                singleLine = true,
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Access token") },
                placeholder = { Text(if (hasToken) "Saved securely — leave blank to keep" else "Paste token") },
                visualTransformation = PasswordVisualTransformation(),
                colors = cortexConnectionTextFieldColors(),
                singleLine = true,
            )
            Button(
                onClick = { onSave(url, token) },
                enabled = canSaveHttpsConnection(
                    savedEndpoint = initialUrl,
                    candidateEndpoint = url,
                    hasSavedToken = hasToken,
                    enteredToken = token,
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(9.dp),
            ) { Text("Save connection") }

            if (hasToken || initialUrl.isNotBlank()) {
                TextButton(
                    onClick = { confirmForget = true },
                    modifier = Modifier.fillMaxWidth().testTag("forget-cortex-connection"),
                ) {
                    Text("Forget saved connection", color = CortexDanger)
                }
            }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            containerColor = CortexSurface,
            titleContentColor = CortexText,
            textContentColor = CortexMuted,
            title = { Text("Forget saved connection?") },
            text = {
                Text(
                    "This removes the saved server URL and encrypted token from this device only. " +
                        "It does not stop Night, remove accounts, or delete server/session state."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmForget = false
                        onForget()
                    },
                    modifier = Modifier.testTag("confirm-forget-cortex-connection"),
                ) {
                    Text("Forget connection", color = CortexDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun cortexConnectionTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = CortexText,
    unfocusedTextColor = CortexText,
    focusedContainerColor = CortexHeader.copy(alpha = .42f),
    unfocusedContainerColor = CortexHeader.copy(alpha = .42f),
    cursorColor = CortexAccent,
    focusedBorderColor = CortexAccent,
    unfocusedBorderColor = CortexLine,
    focusedLabelColor = CortexAccent,
    unfocusedLabelColor = CortexMuted,
    focusedPlaceholderColor = CortexMuted,
    unfocusedPlaceholderColor = CortexMuted,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameSheet(
    title: String,
    label: String,
    initial: String = "",
    action: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.navigationBarsPadding(),
        containerColor = CortexSurface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) },
                singleLine = true,
            )
            Button(
                onClick = { onSubmit(value) },
                enabled = value.trim().isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
            ) { Text(action) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileActionsSheet(
    entry: HostingFileEntry,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDuplicate: () -> Unit,
    onDownload: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.navigationBarsPadding(),
        containerColor = CortexSurface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 22.dp)
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(entry.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (entry.type == "directory") "Directory" else bytes(entry.sizeBytes), color = CortexMuted, fontSize = 10.sp)
            }
            if (entry.type == "file") SheetAction(Icons.Rounded.Edit, "Edit", onEdit)
            SheetAction(Icons.Rounded.Edit, "Rename", onRename)
            SheetAction(Icons.Rounded.DriveFileMove, "Move", onMove)
            SheetAction(Icons.Rounded.ContentCopy, "Duplicate", onDuplicate)
            if (entry.type == "file") SheetAction(Icons.Rounded.Download, "Download", onDownload)
            SheetAction(Icons.Rounded.Archive, "Compress to ZIP", onCompress)
            if (entry.type == "file" && entry.name.endsWith(".zip", true)) {
                SheetAction(Icons.Rounded.Unarchive, "Extract here", onExtract)
            }
            SheetAction(Icons.Rounded.Delete, "Delete", onDelete, danger = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupSheet(
    onDismiss: () -> Unit,
    onCreate: (Boolean) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.navigationBarsPadding(),
        containerColor = CortexSurface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 22.dp)
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text("Create Backup", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            SheetAction(Icons.Rounded.Archive, "Project ZIP", click = { onCreate(false) })
            Text(
                "Project files only. Saved sign-in and private state are excluded.",
                Modifier.padding(horizontal = 58.dp, vertical = 2.dp),
                color = CortexMuted,
                fontSize = 9.sp,
            )
            SheetAction(Icons.Rounded.Warning, "Full private backup", click = { onCreate(true) })
            Text(
                "Includes saved sign-in and other private app state. Keep it private.",
                Modifier.padding(horizontal = 58.dp, vertical = 2.dp),
                color = CortexMuted,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    click: () -> Unit,
    danger: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = click)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger) CortexDanger else Color.White)
        Spacer(Modifier.width(16.dp))
        Text(title, color = if (danger) CortexDanger else Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun StatusBar(text: String, bad: Boolean, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(14.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = if (bad) Color(0xFF7F1D1D) else Color(0xFF14532D),
            contentColor = Color.White,
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDismiss),
        ) {
            Text(text, Modifier.padding(14.dp), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun activityTitle(action: String): String = when (action) {
    "server:power.start" -> "Started the server"
    "server:power.stop" -> "Stopped the server"
    "server:power.restart" -> "Restarted the server"
    "server:file.mkdir" -> "Created a directory"
    "server:file.write" -> "Saved a file"
    "server:file.uploaded" -> "Uploaded a file"
    "server:file.rename" -> "Moved or renamed a file"
    "server:file.copy" -> "Duplicated a file or directory"
    "server:file.delete" -> "Deleted a file"
    "server:file.compress" -> "Compressed files"
    "server:file.decompress" -> "Decompressed an archive"
    "server:backup.create" -> "Created a backup"
    "server:backup.download" -> "Downloaded a backup"
    "server:backup.delete" -> "Deleted a backup"
    "server:backup.restore" -> "Restored a project backup"
    "server:backup.restore-restart-failed" -> "Restore completed but Night restart failed"
    "server:startup.update" -> "Changed startup behavior"
    "mscc:module.reload" -> "Reloaded a module"
    "mscc:module.reload-failed" -> "Module reload failed"
    "mscc:commands.reload" -> "Reloaded commands"
    "mscc:commands.reload-failed" -> "Command reload failed"
    "mscc:account.disconnect" -> "Disconnected a WhatsApp account"
    "mscc:account.remove" -> "Removed a WhatsApp account"
    "account.created" -> "Added a WhatsApp account"
    "account.connected" -> "WhatsApp account connected"
    "account.disconnected" -> "WhatsApp account disconnected"
    "account.disconnected-manually" -> "WhatsApp account disconnected manually"
    "account.reconnect-requested" -> "Reconnecting WhatsApp account"
    "account.removed" -> "Removed a WhatsApp account"
    "pairing.requested" -> "WhatsApp pairing requested"
    "pairing.repair-requested" -> "WhatsApp re-pair requested"
    "cc.forwarded" -> "Forwarded recovered media"
    "cc.deleted-recovery" -> "Recovered a deleted message"
    "cc.destination-changed" -> "Changed recovery destination"
    "configuration.changed" -> "Changed settings"
    "configuration.reloaded" -> "Reloaded settings"
    "command.registry-changed" -> "Commands changed"
    "module.reloaded" -> "Reloaded a module"
    "server:dependencies.install" -> "Installed dependencies"
    else -> action.removePrefix("server:").removePrefix("mscc:").replace('.', ' ').replaceFirstChar { it.uppercase() }
}

private fun activityDetail(entry: ActivityEntry): String {
    val json = runCatching { JSONObject(entry.detail) }.getOrNull() ?: return ""
    fun text(key: String): String = json.optString(key).takeUnless { it == "null" }.orEmpty()
    fun route(from: String, to: String): String =
        listOf(text(from), text(to)).filter { it.isNotBlank() }.joinToString(" → ")

    return when (entry.action) {
        "server:file.mkdir", "server:file.write", "server:file.uploaded", "server:file.delete" -> text("path")
        "server:file.rename", "server:file.copy" -> route("from", "to")
        "server:file.compress" -> text("destination")
        "server:file.decompress" -> text("destination")
        "server:backup.create", "server:backup.download", "server:backup.delete", "server:backup.restore" -> text("name")
        "server:startup.update" -> if (json.optBoolean("enabled")) "Starts automatically" else "Does not start automatically"
        "mscc:module.reload", "mscc:module.reload-failed", "module.reloaded" -> text("module").ifBlank { text("id") }
        "mscc:commands.reload" -> json.optInt("count", -1).takeIf { it >= 0 }?.let { "$it commands" }.orEmpty()
        "mscc:account.create" -> text("displayName")
        "mscc:pairing.pair", "mscc:pairing.repair", "pairing.requested", "pairing.repair-requested" -> when (text("mode")) {
            "qr" -> "QR pairing"
            "code" -> "Phone-number pairing"
            else -> ""
        }
        else -> ""
    }
}

private fun activityTime(value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US)
        val date = parser.parse(value) ?: return@runCatching value
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(date)
    }.getOrDefault(value)
}

private fun bytes(value: Long?): String {
    if (value == null || value < 0) return "—"
    val kib = value / 1024.0
    val mib = kib / 1024.0
    val gib = mib / 1024.0
    return when {
        gib >= 1 -> String.format(Locale.US, "%.2f GiB", gib)
        mib >= 1 -> String.format(Locale.US, "%.2f MiB", mib)
        kib >= 1 -> String.format(Locale.US, "%.1f KiB", kib)
        else -> "$value Bytes"
    }
}

private fun uptime(value: Long?): String {
    if (value == null || value <= 0) return "—"
    val totalMinutes = value / 60_000
    val days = totalMinutes / 1440
    val hours = (totalMinutes % 1440) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h ${minutes}m"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
