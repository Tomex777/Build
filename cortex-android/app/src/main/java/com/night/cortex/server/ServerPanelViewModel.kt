package com.night.cortex.server

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.night.cortex.data.SecretVault
import com.night.cortex.hosting.HostingFileEntry
import com.night.cortex.hosting.HostingPowerAction
import com.night.cortex.hosting.canSaveHttpsConnection
import com.night.cortex.hosting.isValidHttpsEndpoint
import com.night.cortex.hosting.normalizeHttpsEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

class ServerPanelViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val MAX_UPLOAD_BYTES = 512L * 1024L * 1024L
    }

    private val connectionPrefs = application.getSharedPreferences("cortex_hosting", Context.MODE_PRIVATE)
    private val secretVault = SecretVault(application)
    private val connectivityManager = application.getSystemService(ConnectivityManager::class.java)
    private var reconnectJob: Job? = null
    private var logStreamJob: Job? = null
    private var pairingMonitorJob: Job? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (_state.value.configured && !_state.value.loading) {
                refreshAll()
                startLogStream()
            }
        }

        override fun onLost(network: Network) {
            if (connectivityManager.activeNetwork == null) {
                _state.value = _state.value.copy(agentReachable = false)
            }
        }
    }
    private val _state = MutableStateFlow(
        ServerPanelState(
            baseUrl = serverUrl(),
            hasToken = serverToken().isNotBlank(),
        )
    )
    val state: StateFlow<ServerPanelState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { cleanupDownloadCache() }
        syncConfigured()
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
        if (_state.value.configured) {
            refreshAll()
            startLogStream()
        }
    }

    override fun onCleared() {
        logStreamJob?.cancel()
        reconnectJob?.cancel()
        pairingMonitorJob?.cancel()
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }

    fun saveConnection(baseUrl: String, token: String) {
        val clean = runCatching { normalizeHttpsEndpoint(baseUrl) }.getOrElse { error ->
            // Validation happens before touching persisted connection state.
            // Keep an already-working server/session visible when the user
            // mistypes a replacement URL in the connection sheet.
            _state.value = _state.value.copy(
                loading = false,
                error = error.message ?: "Enter a valid HTTPS server URL.",
                message = null,
            )
            return
        }

        val savedUrl = serverUrl()
        val savedTokenPresent = serverToken().isNotBlank()
        if (!canSaveHttpsConnection(savedUrl, clean, savedTokenPresent, token)) {
            _state.value = _state.value.copy(
                loading = false,
                error = if (savedUrl.isNotBlank() && clean != savedUrl && savedTokenPresent) {
                    "Enter the access token for the new server."
                } else {
                    "Access token is required."
                },
                message = null,
            )
            return
        }

        logStreamJob?.cancel()
        logStreamJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        pairingMonitorJob?.cancel()
        pairingMonitorJob = null
        saveServerUrl(clean)
        if (token.isNotBlank()) saveServerToken(token.trim())
        _state.value = _state.value.copy(
            baseUrl = clean,
            hasToken = serverToken().isNotBlank(),
            error = null,
            message = "Connection saved.",
        )
        syncConfigured()
        if (_state.value.configured) {
            refreshAll()
            startLogStream()
        }
    }

    fun forgetConnection() {
        logStreamJob?.cancel()
        logStreamJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        pairingMonitorJob?.cancel()
        pairingMonitorJob = null
        saveServerUrl("")
        saveServerToken("")
        _state.value = ServerPanelState(
            message = "Saved server connection removed from this device.",
        )
    }

    private fun syncConfigured() {
        val url = serverUrl()
        val hasToken = serverToken().isNotBlank()
        val validConnection = isValidHttpsEndpoint(url) && hasToken
        _state.value = _state.value.copy(
            baseUrl = url,
            hasToken = hasToken,
            configured = validConnection,
            agentReachable = if (validConnection) _state.value.agentReachable else false,
            authFailed = if (validConnection) _state.value.authFailed else false,
            reconnecting = if (validConnection) _state.value.reconnecting else false,
        )
    }

    private fun api(): CortexServerApi {
        return CortexServerApi(serverUrl(), serverToken())
    }

    private fun serverUrl(): String =
        connectionPrefs.getString("azure_agent_url", "").orEmpty()

    private fun saveServerUrl(value: String) {
        val clean = if (value.isBlank()) "" else normalizeHttpsEndpoint(value)
        connectionPrefs.edit().putString("azure_agent_url", clean).apply()
    }

    private fun serverToken(): String =
        secretVault.get("hosting_AZURE").orEmpty()

    private fun saveServerToken(value: String) {
        secretVault.put("hosting_AZURE", value)
    }

    fun refreshAll() {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy {
                val path = _state.value.currentPath
                val result = withContext(Dispatchers.IO) {
                    val api = api()
                    Bundle(
                        snapshot = api.snapshot(),
                        logs = api.logs(),
                        files = api.listFiles(path),
                        startup = runCatching { api.startup() }.getOrNull(),
                        activity = runCatching { api.activity() }.getOrDefault(emptyList()),
                        backups = runCatching { api.backups() }.getOrDefault(emptyList()),
                        commandSettings = runCatching { api.commandSettings() }.getOrDefault(emptyList()),
                        runtimeRegistry = runCatching { api.runtimeRegistry() }.getOrNull(),
                        pairing = runCatching { api.pairingState() }.getOrNull(),
                    )
                }
                _state.value = _state.value.copy(
                    snapshot = result.snapshot,
                    logs = result.logs,
                    files = result.files,
                    startup = result.startup,
                    activity = result.activity,
                    backups = result.backups,
                    commandSettings = result.commandSettings,
                    runtimeRegistry = result.runtimeRegistry ?: _state.value.runtimeRegistry,
                    pairing = result.pairing,
                )
                result.pairing?.let(::resumePairingMonitorIfNeeded)
            }
        }
    }

    fun refreshConsole() {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy {
                val (snapshot, logs) = withContext(Dispatchers.IO) {
                    api().let { it.snapshot() to it.logs() }
                }
                _state.value = _state.value.copy(snapshot = snapshot, logs = logs)
            }
        }
    }

    fun clearConsole() {
        _state.value = _state.value.copy(logs = emptyList())
    }

    fun power(action: HostingPowerAction) {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy("Power command sent.") {
                withContext(Dispatchers.IO) { api().power(action) }
                val (snapshot, logs) = withContext(Dispatchers.IO) {
                    api().let { it.snapshot() to it.logs() }
                }
                _state.value = _state.value.copy(snapshot = snapshot, logs = logs)
            }
        }
    }

    fun openDirectory(name: String) {
        val next = join(_state.value.currentPath, name)
        _state.value = _state.value.copy(currentPath = next)
        refreshFiles()
    }

    fun goToPath(path: String) {
        _state.value = _state.value.copy(currentPath = normalize(path))
        refreshFiles()
    }

    fun goUp() {
        val current = normalize(_state.value.currentPath)
        if (current == "/") return
        val parent = current.substringBeforeLast('/').ifBlank { "/" }
        goToPath(parent)
    }

    fun refreshFiles() {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy {
                val path = _state.value.currentPath
                val files = withContext(Dispatchers.IO) { api().listFiles(path) }
                _state.value = _state.value.copy(files = files)
            }
        }
    }

    fun openFile(name: String) {
        val path = join(_state.value.currentPath, name)
        viewModelScope.launch {
            busy {
                val content = withContext(Dispatchers.IO) { api().readText(path) }
                _state.value = _state.value.copy(
                    selectedFile = path,
                    editorContent = content,
                    editorDirty = false,
                )
            }
        }
    }

    fun updateEditor(value: String) {
        _state.value = _state.value.copy(editorContent = value, editorDirty = true)
    }

    fun saveEditor() {
        val path = _state.value.selectedFile ?: return
        val content = _state.value.editorContent
        viewModelScope.launch {
            busy("Saved.") {
                withContext(Dispatchers.IO) { api().writeText(path, content) }
                _state.value = _state.value.copy(editorDirty = false)
            }
        }
    }

    fun closeEditor() {
        _state.value = _state.value.copy(selectedFile = null, editorContent = "", editorDirty = false)
    }

    fun createFile(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            busy("File created.") {
                withContext(Dispatchers.IO) { api().writeText(join(_state.value.currentPath, trimmed), "") }
                refreshFilesInline()
            }
        }
    }

    fun createDirectory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            busy("Directory created.") {
                withContext(Dispatchers.IO) { api().makeDirectory(join(_state.value.currentPath, trimmed)) }
                refreshFilesInline()
            }
        }
    }

    fun upload(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            busy("Uploaded.") {
                val meta = withContext(Dispatchers.IO) { documentMeta(uri) }
                val name = meta.name.ifBlank { "upload.bin" }
                if (meta.sizeBytes != null && meta.sizeBytes > MAX_UPLOAD_BYTES) {
                    error("This file is larger than the server transfer limit.")
                }
                val destination = join(_state.value.currentPath, name)
                withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri) ?: error("Unable to read selected file")
                    input.use { api().upload(destination, it, meta.sizeBytes) }
                }
                refreshFilesInline()
            }
        }
    }

    fun rename(entry: HostingFileEntry, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank() || clean == entry.name) return
        viewModelScope.launch {
            busy("Renamed.") {
                withContext(Dispatchers.IO) {
                    api().rename(
                        join(_state.value.currentPath, entry.name),
                        join(_state.value.currentPath, clean),
                    )
                }
                refreshFilesInline()
            }
        }
    }

    fun move(entry: HostingFileEntry, destination: String) {
        val clean = destination.trim()
        if (clean.isBlank()) return
        val from = join(_state.value.currentPath, entry.name)
        val to = if (clean.startsWith("/")) normalize(clean) else join(_state.value.currentPath, clean)
        if (from == to) return
        viewModelScope.launch {
            busy("Moved.") {
                withContext(Dispatchers.IO) { api().rename(from, to) }
                refreshFilesInline()
            }
        }
    }

    fun duplicate(entry: HostingFileEntry) {
        val from = join(_state.value.currentPath, entry.name)
        val to = join(_state.value.currentPath, duplicateName(entry.name))
        viewModelScope.launch {
            busy("Duplicated.") {
                withContext(Dispatchers.IO) { api().copy(from, to) }
                refreshFilesInline()
            }
        }
    }

    fun delete(entry: HostingFileEntry) {
        viewModelScope.launch {
            busy("Deleted.") {
                withContext(Dispatchers.IO) { api().delete(join(_state.value.currentPath, entry.name)) }
                refreshFilesInline()
            }
        }
    }

    fun compress(entry: HostingFileEntry) {
        viewModelScope.launch {
            busy("Archive created.") {
                val destination = join(_state.value.currentPath, entry.name.trimEnd('/') + ".zip")
                withContext(Dispatchers.IO) {
                    api().archive(listOf(join(_state.value.currentPath, entry.name)), destination)
                }
                refreshFilesInline()
            }
        }
    }

    fun extract(entry: HostingFileEntry) {
        if (!entry.name.endsWith(".zip", true)) return
        viewModelScope.launch {
            busy("Archive extracted.") {
                withContext(Dispatchers.IO) {
                    api().extract(join(_state.value.currentPath, entry.name), _state.value.currentPath)
                }
                refreshFilesInline()
            }
        }
    }

    fun refreshStartup() {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy {
                val (startup, snapshot) = withContext(Dispatchers.IO) {
                    api().let { it.startup() to it.snapshot() }
                }
                _state.value = _state.value.copy(startup = startup, snapshot = snapshot)
            }
        }
    }

    fun setStartupEnabled(enabled: Boolean) {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy(if (enabled) "Start at boot enabled." else "Start at boot disabled.") {
                val startup = withContext(Dispatchers.IO) { api().setStartupEnabled(enabled) }
                _state.value = _state.value.copy(startup = startup)
            }
        }
    }

    fun refreshBackups() {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy {
                val rows = withContext(Dispatchers.IO) { api().backups() }
                _state.value = _state.value.copy(backups = rows)
            }
        }
    }

    fun deleteBackup(entry: BackupEntry) {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy("Backup deleted.") {
                withContext(Dispatchers.IO) { api().deleteBackup(entry.name) }
                val rows = withContext(Dispatchers.IO) { api().backups() }
                _state.value = _state.value.copy(backups = rows)
            }
        }
    }

    fun restoreBackup(entry: BackupEntry) {
        if (!_state.value.configured || entry.privateBackup) return
        viewModelScope.launch {
            busy("Backup restored. A safety backup was created first.") {
                val result = withContext(Dispatchers.IO) {
                    val api = api()
                    val safetyBackup = api.restoreBackup(entry.name)
                    Triple(
                        safetyBackup,
                        api.backups(),
                        runCatching { api.activity() }.getOrDefault(emptyList()),
                    )
                }
                _state.value = _state.value.copy(
                    backups = result.second,
                    activity = result.third,
                    message = "Restored ${entry.name}. Safety backup: ${result.first}",
                )
            }
        }
    }

    fun createBackup(privateBackup: Boolean) {
        viewModelScope.launch {
            busy(if (privateBackup) "Private backup created." else "Project backup created.") {
                withContext(Dispatchers.IO) { api().createBackup(privateBackup) }
                _state.value = _state.value.copy(backups = withContext(Dispatchers.IO) { api().backups() })
            }
        }
    }

    fun downloadProjectBackup() {
        viewModelScope.launch {
            busy("Project ZIP ready to save.") {
                val api = api()
                val backup = withContext(Dispatchers.IO) { api.createBackup(false) }
                val pending = withContext(Dispatchers.IO) {
                    cacheDownload(backup.name) { output -> api.downloadBackup(backup.name, output) }
                }
                val backups = withContext(Dispatchers.IO) { api.backups() }
                replacePendingDownload(pending)
                _state.value = _state.value.copy(backups = backups)
            }
        }
    }

    fun prepareFileDownload(entry: HostingFileEntry) {
        if (entry.type != "file") return
        val path = join(_state.value.currentPath, entry.name)
        viewModelScope.launch {
            busy("File ready to save.") {
                val pending = withContext(Dispatchers.IO) {
                    val api = api()
                    cacheDownload(entry.name) { output -> api.downloadFile(path, output) }
                }
                replacePendingDownload(pending)
            }
        }
    }

    fun prepareBackupDownload(entry: BackupEntry) {
        viewModelScope.launch {
            busy("Backup ready to save.") {
                val pending = withContext(Dispatchers.IO) {
                    val api = api()
                    cacheDownload(entry.name) { output -> api.downloadBackup(entry.name, output) }
                }
                replacePendingDownload(pending)
            }
        }
    }

    fun exportPendingDownload(uri: Uri) {
        val pending = _state.value.pendingDownload ?: return
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, message = null)
            try {
                withContext(Dispatchers.IO) {
                    val source = File(pending.cachePath)
                    require(source.isFile) { "Prepared download is no longer available" }
                    val target = resolver.openOutputStream(uri, "w")
                        ?: error("Unable to open the selected destination")
                    source.inputStream().buffered().use { input ->
                        target.buffered().use { output -> input.copyTo(output, 64 * 1024) }
                    }
                }
                _state.value = _state.value.copy(
                    loading = false,
                    pendingDownload = null,
                    error = null,
                    message = "${pending.name} saved.",
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(
                    loading = false,
                    pendingDownload = null,
                    error = "Could not save ${pending.name}. Choose a destination and try again.",
                    message = null,
                )
            } finally {
                runCatching { File(pending.cachePath).delete() }
            }
        }
    }

    fun consumePendingDownload() {
        val pending = _state.value.pendingDownload
        _state.value = _state.value.copy(pendingDownload = null)
        pending?.cachePath?.let { path -> runCatching { File(path).delete() } }
    }

    fun createCommand(name: String) {
        val clean = name.trim().lowercase()
        if (!Regex("^[a-z0-9][a-z0-9_-]{0,31}$").matches(clean)) {
            _state.value = _state.value.copy(error = "Use 1-32 lowercase letters, numbers, _ or - for command names.")
            return
        }
        val path = "/commands/$clean.js"
        val source = """
            export default {
              name: '$clean',
              description: 'Custom Night command.',
              ownerOnly: true,

              async run(ctx) {
                await ctx.reply('Hello from .$clean 👋')
              },
            }
        """.trimIndent() + "\n"

        viewModelScope.launch {
            busy("Command file created. Edit it, save it, then reload command files.") {
                withContext(Dispatchers.IO) { api().writeText(path, source) }
                _state.value = _state.value.copy(
                    selectedFile = path,
                    editorContent = source,
                    editorDirty = false,
                )
            }
        }
    }

    fun refreshSettings() {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy {
                val (rows, registry) = withContext(Dispatchers.IO) {
                    val api = api()
                    api.commandSettings() to runCatching { api.runtimeRegistry() }.getOrNull()
                }
                _state.value = _state.value.copy(
                    commandSettings = rows,
                    runtimeRegistry = registry ?: _state.value.runtimeRegistry,
                )
            }
        }
    }

    fun setCommandSetting(key: String, enabled: Boolean) {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy("Setting saved.") {
                val rows = withContext(Dispatchers.IO) { api().setCommandSetting(key, enabled) }
                _state.value = _state.value.copy(commandSettings = rows)
            }
        }
    }

    fun reloadCommands() {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy {
                val result = withContext(Dispatchers.IO) {
                    val api = api()
                    val names = api.reloadCommands()
                    Triple(
                        names,
                        api.commandSettings(),
                        runCatching { api.runtimeRegistry() }.getOrNull(),
                    )
                }
                _state.value = _state.value.copy(
                    commandSettings = result.second,
                    runtimeRegistry = result.third ?: _state.value.runtimeRegistry,
                    message = "Reloaded ${result.first.size} commands.",
                )
            }
        }
    }

    fun reloadModule(id: String) {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy("Module reloaded.") {
                val registry = withContext(Dispatchers.IO) {
                    val api = api()
                    api.reloadModule(id)
                    api.runtimeRegistry()
                }
                val activity = withContext(Dispatchers.IO) { runCatching { api().activity() }.getOrDefault(_state.value.activity) }
                _state.value = _state.value.copy(runtimeRegistry = registry, activity = activity)
            }
        }
    }

    fun refreshPairing() {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy {
                val pairing = withContext(Dispatchers.IO) { api().pairingState() }
                _state.value = _state.value.copy(pairing = pairing)
                resumePairingMonitorIfNeeded(pairing)
            }
        }
    }

    fun addAccount(phoneNumber: String, displayName: String) {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy("Account added.") {
                withContext(Dispatchers.IO) { api().addAccount(phoneNumber, displayName) }
                val pairing = withContext(Dispatchers.IO) { api().pairingState() }
                _state.value = _state.value.copy(pairing = pairing)
            }
        }
    }

    fun setDestination(id: String) {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy("Destination changed to Account $id.") {
                withContext(Dispatchers.IO) { api().setDestination(id) }
                val pairing = withContext(Dispatchers.IO) { api().pairingState() }
                _state.value = _state.value.copy(pairing = pairing)
            }
        }
    }

    fun pairAccount(id: String, mode: String) {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy("Pairing started.") {
                withContext(Dispatchers.IO) { api().pairAccount(id, mode) }
                pollPairing(id)
                startPairingMonitor()
            }
        }
    }

    fun repairAccount(id: String, mode: String) {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy("Re-pair started.") {
                withContext(Dispatchers.IO) { api().repairAccount(id, mode) }
                pollPairing(id)
                startPairingMonitor()
            }
        }
    }

    fun reconnectPairing(id: String) {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy("Reconnect requested.") {
                withContext(Dispatchers.IO) { api().reconnectAccount(id) }
                pollPairing(id)
                startPairingMonitor()
            }
        }
    }

    fun disconnectPairing(id: String) {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy("Account disconnected.") {
                withContext(Dispatchers.IO) { api().disconnectAccount(id) }
                val pairing = withContext(Dispatchers.IO) { api().pairingState() }
                _state.value = _state.value.copy(pairing = pairing)
            }
        }
    }

    fun removePairing(id: String) {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy {
                val authPreserved = withContext(Dispatchers.IO) { api().removeAccount(id) }
                val pairing = withContext(Dispatchers.IO) { api().pairingState() }
                _state.value = _state.value.copy(pairing = pairing)
                if (!authPreserved) {
                    error("Account was removed, but the server did not confirm that its saved sign-in state was preserved.")
                }
                _state.value = _state.value.copy(message = "Account removed. Saved sign-in state preserved.")
            }
        }
    }

    private suspend fun pollPairing(id: String) {
        repeat(10) { attempt ->
            delay(if (attempt == 0) 300 else 650)
            val pairing = withContext(Dispatchers.IO) { api().pairingState() }
            _state.value = _state.value.copy(pairing = pairing)
            val account = pairing.accounts.firstOrNull { it.id == id }
            if (
                account?.connected == true ||
                !account?.pairingCode.isNullOrBlank() ||
                !account?.pairingQr.isNullOrBlank() ||
                !account?.pairingError.isNullOrBlank()
            ) return
        }
    }

    private fun resumePairingMonitorIfNeeded(pairing: PairingState) {
        if (pairing.accounts.any(::isPairingPending)) startPairingMonitor()
    }

    private fun isPairingPending(account: PairingAccount): Boolean {
        val terminal = account.status.lowercase() in setOf(
            "auth-invalid",
            "logged-out",
            "revoked",
            "session-expired",
            "expired",
            "failed",
            "error",
        )
        return !account.connected &&
            !terminal &&
            account.pairingError.isBlank() &&
            (
                account.status.lowercase() in setOf("pairing", "connecting", "reconnecting", "pending") ||
                    account.pairingCode.isNotBlank() ||
                    account.pairingQr.isNotBlank()
            )
    }

    private fun startPairingMonitor() {
        if (!_state.value.configured || pairingMonitorJob?.isActive == true) return
        pairingMonitorJob = viewModelScope.launch {
            try {
                repeat(120) {
                    delay(1_000)
                    if (!_state.value.configured) return@launch

                    val result = withContext(Dispatchers.IO) {
                        runCatching { api().pairingState() }
                    }
                    val pairing = result.getOrNull()
                    if (pairing != null) {
                        _state.value = _state.value.copy(
                            pairing = pairing,
                            agentReachable = true,
                            reconnecting = false,
                            authFailed = false,
                            lastSuccessfulSyncAt = System.currentTimeMillis(),
                        )
                        if (pairing.accounts.none(::isPairingPending)) return@launch
                        return@repeat
                    }

                    when (val error = result.exceptionOrNull()) {
                        is CortexTransportException -> {
                            _state.value = _state.value.copy(agentReachable = false)
                            scheduleReconnect()
                        }
                        is CortexHttpException -> {
                            if (error.statusCode in setOf(401, 403)) {
                                _state.value = _state.value.copy(authFailed = true)
                                return@launch
                            }
                        }
                    }
                }
            } finally {
                pairingMonitorJob = null
            }
        }
    }

    fun refreshActivity() {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy {
                val rows = withContext(Dispatchers.IO) { api().activity() }
                _state.value = _state.value.copy(activity = rows)
            }
        }
    }

    fun installDependencies() {
        viewModelScope.launch {
            busy {
                val result = withContext(Dispatchers.IO) { api().installDependencies() }
                _state.value = _state.value.copy(message = result.takeLast(300))
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    private suspend fun refreshFilesInline() {
        val files = withContext(Dispatchers.IO) { api().listFiles(_state.value.currentPath) }
        _state.value = _state.value.copy(files = files)
    }

    private suspend fun busy(success: String? = null, block: suspend () -> Unit) {
        _state.value = _state.value.copy(loading = true, error = null, message = null)
        runCatching { block() }
            .onSuccess {
                reconnectJob?.cancel()
                reconnectJob = null
                _state.value = _state.value.copy(
                    loading = false,
                    agentReachable = true,
                    reconnecting = false,
                    authFailed = false,
                    lastSuccessfulSyncAt = System.currentTimeMillis(),
                    error = null,
                    message = _state.value.message ?: success,
                )
            }
            .onFailure { error ->
                val transportFailure = error is CortexTransportException
                val authFailure = error is CortexHttpException && error.statusCode in setOf(401, 403)
                _state.value = _state.value.copy(
                    loading = false,
                    agentReachable = if (transportFailure) false else _state.value.agentReachable,
                    authFailed = authFailure,
                    error = error.message ?: "Request failed",
                    message = null,
                )
                if (transportFailure) scheduleReconnect()
            }
    }

    private fun startLogStream() {
        if (!_state.value.configured || logStreamJob?.isActive == true) return
        logStreamJob = viewModelScope.launch {
            var waitMs = 1_500L
            while (isActive && _state.value.configured) {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        api().streamLogs { line ->
                            _state.update { current ->
                                current.copy(
                                    logs = (current.logs + line).takeLast(500),
                                    agentReachable = true,
                                    reconnecting = false,
                                    authFailed = false,
                                    lastSuccessfulSyncAt = System.currentTimeMillis(),
                                )
                            }
                        }
                    }
                }
                if (!isActive || !_state.value.configured) break
                val error = result.exceptionOrNull()
                when (error) {
                    is CortexTransportException -> {
                        _state.value = _state.value.copy(agentReachable = false)
                        scheduleReconnect()
                    }
                    is CortexHttpException -> {
                        if (error.statusCode in setOf(401, 403)) {
                            _state.value = _state.value.copy(
                                agentReachable = true,
                                authFailed = true,
                                reconnecting = false,
                                error = error.message,
                            )
                            break
                        }
                    }
                }
                delay(waitMs)
                waitMs = (waitMs * 2).coerceAtMost(20_000L)
            }
        }
    }

    private fun scheduleReconnect() {
        if (!_state.value.configured || reconnectJob?.isActive == true) return
        _state.value = _state.value.copy(reconnecting = true)
        reconnectJob = viewModelScope.launch {
            var waitMs = 2_000L
            repeat(6) {
                delay(waitMs)
                if (!_state.value.configured) return@launch
                val result = withContext(Dispatchers.IO) { runCatching { api().snapshot() } }
                val snapshot = result.getOrNull()
                if (snapshot != null) {
                    _state.value = _state.value.copy(
                        snapshot = snapshot,
                        agentReachable = true,
                        reconnecting = false,
                        lastSuccessfulSyncAt = System.currentTimeMillis(),
                        error = null,
                    )
                    reconnectJob = null
                    refreshAll()
                    startLogStream()
                    return@launch
                }
                if (result.exceptionOrNull() is CortexHttpException) {
                    val http = result.exceptionOrNull() as CortexHttpException
                    _state.value = _state.value.copy(
                        agentReachable = true,
                        reconnecting = false,
                        authFailed = http.statusCode in setOf(401, 403),
                        error = http.message,
                    )
                    reconnectJob = null
                    return@launch
                }
                waitMs = (waitMs * 2).coerceAtMost(30_000L)
            }
            _state.value = _state.value.copy(
                reconnecting = false,
                error = _state.value.error
                    ?: "Server is still unreachable. Check the network or server, then refresh.",
            )
            reconnectJob = null
        }
    }

    private fun downloadDirectory(): File =
        File(getApplication<Application>().cacheDir, "cortex-downloads").apply { mkdirs() }

    private fun cleanupDownloadCache() {
        downloadDirectory().listFiles()
            ?.filter { it.isFile && it.name.startsWith("cortex-") && it.name.endsWith(".download") }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun cacheDownload(name: String, transfer: (OutputStream) -> Unit): PendingDownload {
        val file = File.createTempFile("cortex-", ".download", downloadDirectory())
        return try {
            file.outputStream().buffered().use { output -> transfer(output) }
            PendingDownload(name = name, cachePath = file.absolutePath)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    private fun replacePendingDownload(pending: PendingDownload) {
        val previous = _state.value.pendingDownload
        if (previous?.cachePath != pending.cachePath) {
            previous?.cachePath?.let { path -> runCatching { File(path).delete() } }
        }
        _state.value = _state.value.copy(pendingDownload = pending)
    }

    private data class DocumentMeta(
        val name: String,
        val sizeBytes: Long?,
    )

    private fun documentMeta(uri: Uri): DocumentMeta {
        val resolver = getApplication<Application>().contentResolver
        return resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else ""
            val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex).takeIf { it >= 0 } else null
            DocumentMeta(name = name, sizeBytes = size)
        } ?: DocumentMeta(name = "", sizeBytes = null)
    }

    private fun join(parent: String, child: String): String {
        val p = normalize(parent)
        return if (p == "/") "/${child.trimStart('/')}" else "$p/${child.trimStart('/')}"
    }

    private fun duplicateName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.length - 1) {
            name.substring(0, dot) + " copy" + name.substring(dot)
        } else {
            "$name copy"
        }
    }

    private fun normalize(path: String): String {
        val clean = "/" + path.trim().replace('\\', '/').trim('/')
        return if (clean == "/") "/" else clean
    }

    private data class Bundle(
        val snapshot: com.night.cortex.hosting.HostingSnapshot,
        val logs: List<String>,
        val files: List<HostingFileEntry>,
        val startup: StartupInfo?,
        val activity: List<ActivityEntry>,
        val backups: List<BackupEntry>,
        val commandSettings: List<CommandSetting>,
        val runtimeRegistry: RuntimeRegistry?,
        val pairing: PairingState?,
    )
}
