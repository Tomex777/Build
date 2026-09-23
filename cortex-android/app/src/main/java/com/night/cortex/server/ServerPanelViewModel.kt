package com.night.cortex.server

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.night.cortex.data.CortexRepository
import com.night.cortex.hosting.HostingFileEntry
import com.night.cortex.hosting.HostingPowerAction
import com.night.cortex.hosting.HostingProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerPanelViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = CortexRepository(application)
    private val _state = MutableStateFlow(
        ServerPanelState(
            baseUrl = repo.hostingIdentifier(HostingProviderId.AZURE),
            hasToken = repo.hostingSecret(HostingProviderId.AZURE).isNotBlank(),
        )
    )
    val state: StateFlow<ServerPanelState> = _state.asStateFlow()

    init {
        syncConfigured()
        if (_state.value.configured) refreshAll()
    }

    fun saveConnection(baseUrl: String, token: String) {
        val clean = baseUrl.trim().removeSuffix("/")
        repo.rememberHostingIdentifier(HostingProviderId.AZURE, clean)
        if (token.isNotBlank()) repo.rememberHostingSecret(HostingProviderId.AZURE, token.trim())
        _state.value = _state.value.copy(
            baseUrl = clean,
            hasToken = repo.hostingSecret(HostingProviderId.AZURE).isNotBlank(),
            error = null,
            message = "Connection saved.",
        )
        syncConfigured()
        if (_state.value.configured) refreshAll()
    }

    private fun syncConfigured() {
        val url = repo.hostingIdentifier(HostingProviderId.AZURE)
        val hasToken = repo.hostingSecret(HostingProviderId.AZURE).isNotBlank()
        _state.value = _state.value.copy(
            baseUrl = url,
            hasToken = hasToken,
            configured = url.startsWith("https://") && hasToken,
        )
    }

    private fun api(): CortexServerApi {
        val url = repo.hostingIdentifier(HostingProviderId.AZURE)
        val token = repo.hostingSecret(HostingProviderId.AZURE)
        return CortexServerApi(url, token)
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
                )
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
                val pair = withContext(Dispatchers.IO) {
                    val name = displayName(uri) ?: "upload.bin"
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Unable to read selected file")
                    name to bytes
                }
                withContext(Dispatchers.IO) { api().upload(join(_state.value.currentPath, pair.first), pair.second) }
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

    fun refreshBackups() {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy {
                val rows = withContext(Dispatchers.IO) { api().backups() }
                _state.value = _state.value.copy(backups = rows)
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

    fun prepareFileDownload(entry: HostingFileEntry) {
        if (entry.type != "file") return
        val path = join(_state.value.currentPath, entry.name)
        viewModelScope.launch {
            busy("File ready to save.") {
                val bytes = withContext(Dispatchers.IO) { api().downloadFile(path) }
                _state.value = _state.value.copy(pendingDownload = PendingDownload(entry.name, bytes))
            }
        }
    }

    fun prepareBackupDownload(entry: BackupEntry) {
        viewModelScope.launch {
            busy("Backup ready to save.") {
                val bytes = withContext(Dispatchers.IO) { api().downloadBackup(entry.name) }
                _state.value = _state.value.copy(pendingDownload = PendingDownload(entry.name, bytes))
            }
        }
    }

    fun consumePendingDownload() {
        _state.value = _state.value.copy(pendingDownload = null)
    }

    fun refreshSettings() {
        if (!_state.value.configured) return
        viewModelScope.launch {
            busy {
                val rows = withContext(Dispatchers.IO) { api().commandSettings() }
                _state.value = _state.value.copy(commandSettings = rows)
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
                _state.value = _state.value.copy(
                    loading = false,
                    error = null,
                    message = _state.value.message ?: success,
                )
            }
            .onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    error = it.message ?: "Request failed",
                    message = null,
                )
            }
    }

    private fun displayName(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun join(parent: String, child: String): String {
        val p = normalize(parent)
        return if (p == "/") "/${child.trimStart('/')}" else "$p/${child.trimStart('/')}"
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
    )
}
