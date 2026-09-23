package com.night.cortex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.night.cortex.data.InboxChat
import com.night.cortex.data.InboxMessage
import com.night.cortex.hosting.HostingPowerAction
import com.night.cortex.hosting.HostingProviderId
import com.night.cortex.hosting.providerFor
import com.night.cortex.ui.theme.*
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

enum class CortexTab { HOME, SERVER, INBOX, LIBRARY, SETTINGS }

@Composable
fun CortexApp(vm: CortexViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(CortexTab.HOME) }
    var connect by rememberSaveable { mutableStateOf(false) }
    var hostingConnect by rememberSaveable { mutableStateOf(false) }
    var blobConnect by rememberSaveable { mutableStateOf(false) }
    val provider = providerFor(state.provider)

    BackHandler(enabled = connect || hostingConnect || blobConnect || state.selectedLocalFile != null || state.activeChat != null) {
        when {
            state.selectedLocalFile != null -> vm.closeEditor()
            blobConnect -> blobConnect = false
            hostingConnect -> hostingConnect = false
            connect -> connect = false
            else -> vm.closeChat()
        }
    }

    LaunchedEffect(state.provider) {
        if (tab == CortexTab.SERVER && !provider.capabilities.serverManagement) tab = CortexTab.HOME
    }

    Scaffold(
        containerColor = CortexBackground,
        bottomBar = {
            if (!connect && !hostingConnect && !blobConnect && state.activeChat == null && state.selectedLocalFile == null) {
                NavigationBar(containerColor = Color(0xFF0F1114)) {
                    val tabs = buildList {
                        add(CortexTab.HOME)
                        if (provider.capabilities.serverManagement) add(CortexTab.SERVER)
                        add(CortexTab.INBOX)
                        add(CortexTab.LIBRARY)
                        add(CortexTab.SETTINGS)
                    }
                    tabs.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(tabIcon(item), item.name) },
                            label = { Text(item.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 9.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CortexAccent,
                                selectedTextColor = CortexAccent,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = Color(0xFF737A84),
                                unselectedTextColor = Color(0xFF737A84),
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                connect -> ConnectionScreen(state.connection.baseUrl, state.connection.token.isNotBlank()) { url, token ->
                    vm.saveConnection(url, token)
                    connect = false
                }
                hostingConnect -> HostingConnectionScreen(
                    provider = state.provider,
                    initialIdentifier = state.hostingIdentifier,
                    hasStoredSecret = state.hostingHasSecret,
                    onBack = { hostingConnect = false },
                    onSave = { identifier, secret ->
                        vm.saveHostingConnection(identifier, secret)
                        hostingConnect = false
                    },
                )
                blobConnect -> BlobStorageScreen(
                    initialUrl = state.blobSasUrl,
                    onBack = { blobConnect = false },
                    onSave = { url ->
                        vm.saveBlobSas(url)
                        blobConnect = false
                    },
                )
                state.activeChat != null -> ThreadScreen(state.activeChat!!, state.messages, vm::closeChat, vm::send)
                tab == CortexTab.HOME -> HomeScreen(state, vm::refresh) { connect = true }
                tab == CortexTab.SERVER -> ServerScreen(
                    state = state,
                    refresh = vm::refreshHosting,
                    power = vm::hostingPower,
                    configure = { hostingConnect = true },
                )
                tab == CortexTab.INBOX -> InboxScreen(state.chats, vm::openChat, vm::refresh)
                tab == CortexTab.LIBRARY -> CortexLibraryScreen(
                    state = state,
                    provider = state.provider,
                    refresh = vm::refreshLocalFiles,
                    openFile = vm::openLocalFile,
                    saveFile = vm::saveLocalFile,
                    updateEditor = vm::updateEditor,
                    closeEditor = vm::closeEditor,
                    createFile = vm::createLocalFile,
                    importFile = vm::importDocument,
                    deleteFile = vm::deleteLocalFile,
                    syncBlob = vm::syncBlobBackup,
                    restoreBlob = vm::restoreBlobBackup,
                    deploy = vm::deployWorkspace,
                    configureBlob = { blobConnect = true },
                    configureHosting = { hostingConnect = true },
                )
                else -> SettingsScreen(
                    state = state,
                    provider = vm::setProvider,
                    connection = { connect = true },
                    hosting = { hostingConnect = true },
                    storage = { blobConnect = true },
                )
            }
        }
    }
}

private fun tabIcon(tab: CortexTab) = when (tab) {
    CortexTab.HOME -> Icons.Rounded.Home
    CortexTab.SERVER -> Icons.Rounded.Dns
    CortexTab.INBOX -> Icons.Rounded.ChatBubbleOutline
    CortexTab.LIBRARY -> Icons.Rounded.Folder
    CortexTab.SETTINGS -> Icons.Rounded.Settings
}

@Composable
private fun ConnectionScreen(initialUrl: String, hasSavedToken: Boolean, onConnect: (String, String) -> Unit) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("Connect Cortex", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Cortex reads the live Night Core API. It does not replace unavailable data with demo values.", color = CortexMuted, fontSize = 12.sp)
        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Night Core URL") }, placeholder = { Text("https://…") }, singleLine = true)
        OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("Cortex API token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Button({ onConnect(url, token) }, enabled = url.startsWith("https://") && (token.isNotBlank() || hasSavedToken), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Connect") }
        Text("The token is encrypted with Android Keystore on this phone.", color = CortexMuted, fontSize = 10.sp)
    }
}

@Composable
private fun HostingConnectionScreen(
    provider: HostingProviderId,
    initialIdentifier: String,
    hasStoredSecret: Boolean,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var identifier by rememberSaveable(provider.name) { mutableStateOf(initialIdentifier) }
    var secret by remember(provider) { mutableStateOf("") }
    val azure = provider == HostingProviderId.AZURE
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.width(4.dp))
            Text(if (azure) "Connect Azure" else "Connect Bot-Hosting", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            if (azure) "Connect Cortex to the Cortex Agent running on your Azure VM. Server data remains blank until the agent responds."
            else "Connect Cortex directly to your Bot-Hosting.net deployment using its public API.",
            color = CortexMuted,
            fontSize = 11.sp,
        )
        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (azure) "Cortex Agent URL" else "Deployment ID") },
            placeholder = { Text(if (azure) "https://cortex-agent.example.com" else "dep_…") },
            singleLine = true,
        )
        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (azure) "Agent token" else "Bot-Hosting API key") },
            placeholder = { Text(if (hasStoredSecret) "Saved securely — leave blank to keep it" else if (azure) "Agent bearer token" else "bhk_…") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Button(
            onClick = { onSave(identifier, secret) },
            enabled = identifier.isNotBlank() && (secret.isNotBlank() || hasStoredSecret) && (!azure || identifier.startsWith("https://")),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Connect") }
        Text("The endpoint is remembered locally. Credentials are encrypted with Android Keystore.", color = CortexMuted, fontSize = 9.sp)
    }
}

@Composable
private fun HomeScreen(state: CortexUiState, refresh: () -> Unit, reconnect: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(CortexAccent, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text("C", color = Color.Black, fontWeight = FontWeight.Black) }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Cortex", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Night control center", color = CortexMuted, fontSize = 10.sp)
                }
                IconButton(refresh) { Icon(Icons.Rounded.Refresh, "Refresh") }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = CortexSurface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).background(CortexSurface2, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SmartToy, null) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Night", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(if (state.health?.online == true) "Night Core connected" else "Night Core unavailable", color = CortexMuted, fontSize = 11.sp)
                        }
                        Pill(if (state.health?.online == true) "LIVE" else "OFFLINE", state.health?.online == true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Connected", state.snapshot?.let { "${it.connectedSessions}/${it.sessionCount}" } ?: "—", Modifier.weight(1f))
                        Metric("Allowed", state.snapshot?.allowedChats?.toString() ?: "—", Modifier.weight(1f))
                        Metric("Local", state.localFileCount.toString(), Modifier.weight(1f))
                    }
                }
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { error ->
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF1B1516)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Live data unavailable", fontWeight = FontWeight.Bold)
                        Text(error, color = CortexMuted, fontSize = 10.sp, maxLines = 3)
                        TextButton(reconnect) { Text("Connection settings") }
                    }
                }
            }
        }
        item {
            Text("AI", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(18.dp), color = CortexSurface) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(when (state.snapshot?.aiEnabled) { true -> "AI is on"; false -> "AI is off"; null -> "AI state not reported" }, fontWeight = FontWeight.SemiBold)
                        Text("Live Night Core configuration", color = CortexMuted, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = CortexSurface2) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = CortexMuted, fontSize = 8.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Pill(text: String, good: Boolean) {
    Surface(shape = CircleShape, color = (if (good) CortexGood else CortexDanger).copy(alpha = .12f)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = if (good) CortexGood else CortexDanger, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun InboxScreen(chats: List<InboxChat>, open: (InboxChat) -> Unit, refresh: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    val visible = chats.filter {
        (query.isBlank() || it.title.contains(query, true) || it.jid.contains(query, true)) &&
            (filter == "all" || filter == "unread" && it.unreadCount > 0 || filter == "groups" && it.isGroup)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("NIGHT", color = CortexMuted, fontSize = 8.sp); Text("Inbox", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            IconButton(refresh) { Icon(Icons.Rounded.Refresh, "Refresh") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search chats") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf("all" to "All", "unread" to "Unread", "groups" to "Groups").forEach { (k, label) -> FilterChip(filter == k, { filter = k }, label = { Text(label) }) }
        }
        if (visible.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (chats.isEmpty()) "No live chats returned." else "No chats match.", color = CortexMuted) }
        else LazyColumn { items(visible, key = { it.jid }) { chat -> ChatRow(chat) { open(chat) } } }
    }
}

@Composable
private fun ChatRow(chat: InboxChat, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).background(Color(0xFF252930), CircleShape), contentAlignment = Alignment.Center) { Text(chat.title.take(1).uppercase(), fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row { Text(chat.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(time(chat.lastMessageAt), color = CortexMuted, fontSize = 8.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chat.preview ?: chat.previewType ?: "No preview", Modifier.weight(1f), color = CortexMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (chat.unreadCount > 0) Badge { Text(chat.unreadCount.toString()) }
            }
        }
    }
    HorizontalDivider(color = Color(0xFF1E2126))
}

@Composable
private fun ThreadScreen(chat: InboxChat, messages: List<InboxMessage>, back: () -> Unit, send: (String) -> Unit) {
    var text by rememberSaveable(chat.jid) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Color(0xFF0C0E11))) {
        Row(Modifier.fillMaxWidth().height(62.dp).background(Color(0xFF111318)), verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text(chat.title, fontWeight = FontWeight.SemiBold)
        }
        if (messages.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No messages returned.", color = CortexMuted) }
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { items(messages, key = { it.id }) { MessageBubble(it) } }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Message") }, maxLines = 4)
            Spacer(Modifier.width(6.dp))
            FilledIconButton({ val s = text.trim(); if (s.isNotBlank()) { send(s); text = "" } }, enabled = text.isNotBlank()) { Icon(Icons.Rounded.Send, "Send") }
        }
    }
}

@Composable
private fun MessageBubble(m: InboxMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromMe) Arrangement.End else Arrangement.Start) {
        Surface(shape = RoundedCornerShape(10.dp), color = if (m.fromMe) Color(0xFF272A2E) else Color(0xFF1A1D22)) {
            Column(Modifier.padding(9.dp).widthIn(max = 290.dp)) {
                Text(m.text ?: m.fileName ?: if (m.viewOnce) "View once ${m.type}" else m.type, fontSize = 11.sp)
                Text(time(m.timestamp), color = CortexMuted, fontSize = 7.sp, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun ServerScreen(
    state: CortexUiState,
    refresh: () -> Unit,
    power: (HostingPowerAction) -> Unit,
    configure: () -> Unit,
) {
    val provider = providerFor(state.provider)
    val snapshot = state.hostingSnapshot
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Server", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(provider.displayName, color = CortexMuted, fontSize = 10.sp)
                }
                IconButton(refresh, enabled = state.hostingConfigured && !state.hostingLoading) { Icon(Icons.Rounded.Refresh, "Refresh") }
            }
        }
        if (!state.hostingConfigured) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = CortexSurface) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Connect ${provider.displayName}", fontWeight = FontWeight.Bold)
                        Text("Cortex will show only live host data returned by this provider.", color = CortexMuted, fontSize = 10.sp)
                        Button(configure) { Text("Configure hosting") }
                    }
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button({ power(HostingPowerAction.START) }, enabled = !state.hostingLoading, modifier = Modifier.weight(1f)) { Text("Start", fontSize = 10.sp) }
                    OutlinedButton({ power(HostingPowerAction.RESTART) }, enabled = !state.hostingLoading, modifier = Modifier.weight(1f)) { Text("Restart", fontSize = 10.sp) }
                    OutlinedButton({ power(HostingPowerAction.STOP) }, enabled = !state.hostingLoading, modifier = Modifier.weight(1f)) { Text("Stop", fontSize = 10.sp) }
                }
            }
            if (state.hostingLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.hostingError?.let { error ->
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF1B1516)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Hosting data unavailable", fontWeight = FontWeight.Bold)
                            Text(error, color = CortexMuted, fontSize = 9.sp, maxLines = 4)
                            TextButton(configure) { Text("Connection settings") }
                        }
                    }
                }
            }
            if (snapshot != null) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = CortexSurface) {
                        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).background(CortexSurface2, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Dns, null) }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Night host", fontWeight = FontWeight.Bold)
                                Text(snapshot.runtime?.let { runtimeLabel(it.runtime, it.version) } ?: provider.displayName, color = CortexMuted, fontSize = 9.sp)
                            }
                            Pill(snapshot.state.uppercase(), snapshot.state.equals("running", true) || snapshot.state.equals("online", true))
                        }
                    }
                }
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF090B0D), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().height(300.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Live logs", color = CortexMuted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                                Box(Modifier.size(6.dp).background(CortexGood, CircleShape))
                            }
                            HorizontalDivider(color = CortexLine)
                            Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                if (state.hostingLogs.isEmpty()) Text("No log lines returned.", color = CortexMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                else state.hostingLogs.takeLast(200).forEach { Text(it, color = Color(0xFFAAB0B8), fontSize = 8.sp, lineHeight = 12.sp, fontFamily = FontFamily.Monospace) }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ServerMetric("CPU", snapshot.cpuPercent?.let { "${it.roundToInt()}%" } ?: "—", Modifier.weight(1f))
                            ServerMetric("RAM", bytes(snapshot.memoryUsedBytes), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ServerMetric("Disk", bytes(snapshot.diskUsedBytes), Modifier.weight(1f))
                            ServerMetric("Uptime", uptime(snapshot.uptimeMs), Modifier.weight(1f))
                        }
                    }
                }
                snapshot.runtime?.let { runtime ->
                    item {
                        Surface(shape = RoundedCornerShape(18.dp), color = CortexSurface) {
                            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Runtime", color = CortexMuted, fontSize = 9.sp)
                                Text(runtimeLabel(runtime.runtime, runtime.version), fontWeight = FontWeight.SemiBold)
                                runtime.entryFile?.let { Text(it, color = CortexMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
                                runtime.startCommand?.let { Text(it, color = CortexMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            } else if (!state.hostingLoading && state.hostingError == null) {
                item { Text("Waiting for live host data.", color = CortexMuted, fontSize = 10.sp) }
            }
        }
    }
}

@Composable
private fun ServerMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = CortexSurface) {
        Column(Modifier.padding(13.dp)) {
            Text(label, color = CortexMuted, fontSize = 8.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LibraryScreen(count: Int) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Library", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Local Cortex workspace", color = CortexMuted)
        Spacer(Modifier.height(18.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = CortexSurface) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, null)
                Spacer(Modifier.width(12.dp))
                Column { Text("Night", fontWeight = FontWeight.SemiBold); Text("$count local project files", color = CortexMuted, fontSize = 9.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Imports are saved locally first, then synced to the active provider.", color = CortexMuted, fontSize = 10.sp)
    }
}

@Composable
private fun SettingsScreen(
    state: CortexUiState,
    provider: (HostingProviderId) -> Unit,
    connection: () -> Unit,
    hosting: () -> Unit,
    storage: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState())) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Hosting", color = CortexMuted, fontSize = 9.sp)
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = CortexSurface) {
            Column {
                ProviderRow("Azure", state.provider == HostingProviderId.AZURE) { provider(HostingProviderId.AZURE) }
                HorizontalDivider(color = CortexLine)
                ProviderRow("Bot-Hosting.net", state.provider == HostingProviderId.BOT_HOSTING) { provider(HostingProviderId.BOT_HOSTING) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = CortexSurface, modifier = Modifier.fillMaxWidth().clickable(onClick = hosting)) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Cloud, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${providerFor(state.provider).displayName} connection", fontWeight = FontWeight.SemiBold)
                    Text(state.hostingIdentifier.ifBlank { "Not configured" }, color = CortexMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ChevronRight, null)
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = CortexSurface, modifier = Modifier.fillMaxWidth().clickable(onClick = storage)) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudUpload, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Azure Blob backup", fontWeight = FontWeight.SemiBold)
                    Text(if (state.blobConfigured) "Container SAS saved on this phone" else "Not configured", color = CortexMuted, fontSize = 9.sp)
                }
                Icon(Icons.Rounded.ChevronRight, null)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Night Core", color = CortexMuted, fontSize = 9.sp)
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = CortexSurface, modifier = Modifier.fillMaxWidth().clickable(onClick = connection)) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Link, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Connection", fontWeight = FontWeight.SemiBold)
                    Text(state.connection.baseUrl.ifBlank { "Not configured" }, color = CortexMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ChevronRight, null)
            }
        }
    }
}

@Composable
private fun ProviderRow(label: String, active: Boolean, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(if (active) "Active" else "Available", color = if (active) CortexGood else CortexMuted, fontSize = 9.sp)
        }
        if (active) Icon(Icons.Rounded.Check, "Active", tint = CortexGood)
    }
}

private fun time(value: Long): String {
    if (value <= 0) return ""
    val ms = if (value < 10_000_000_000L) value * 1000 else value
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
}

private fun runtimeLabel(runtime: String?, version: String?): String = listOfNotNull(runtime?.takeIf { it.isNotBlank() }, version?.takeIf { it.isNotBlank() }).joinToString(" ").ifBlank { "Runtime not reported" }

private fun bytes(value: Long?): String {
    if (value == null || value < 0) return "—"
    val mb = value / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format("%.1f GB", mb / 1024.0) else String.format("%.0f MB", mb)
}

private fun uptime(value: Long?): String {
    if (value == null || value <= 0) return "—"
    val totalMinutes = value / 60_000
    val days = totalMinutes / 1440
    val hours = (totalMinutes % 1440) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
