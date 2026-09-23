package com.night.cortex.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.night.cortex.data.CoreConnection
import com.night.cortex.data.CoreHealth
import com.night.cortex.data.CoreSnapshot
import com.night.cortex.data.CortexRepository
import com.night.cortex.data.WorkspaceFile
import com.night.cortex.data.InboxChat
import com.night.cortex.data.InboxMessage
import com.night.cortex.hosting.HostingPowerAction
import com.night.cortex.hosting.HostingProviderId
import com.night.cortex.hosting.HostingSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class CortexUiState(
    val connection: CoreConnection = CoreConnection(),
    val health: CoreHealth? = null,
    val snapshot: CoreSnapshot? = null,
    val chats: List<InboxChat> = emptyList(),
    val messages: List<InboxMessage> = emptyList(),
    val activeChat: InboxChat? = null,
    val provider: HostingProviderId = HostingProviderId.AZURE,
    val hostingIdentifier: String = "",
    val hostingConfigured: Boolean = false,
    val hostingHasSecret: Boolean = false,
    val hostingSnapshot: HostingSnapshot? = null,
    val hostingLogs: List<String> = emptyList(),
    val hostingLoading: Boolean = false,
    val hostingError: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val localFileCount: Int = 0,
    val localFiles: List<WorkspaceFile> = emptyList(),
    val selectedLocalFile: String? = null,
    val editorContent: String = "",
    val workspaceBusy: Boolean = false,
    val workspaceMessage: String? = null,
    val blobConfigured: Boolean = false,
    val blobSasUrl: String = "",
    val blobBusy: Boolean = false,
    val blobMessage: String? = null,
    val blobRestoreBusy: Boolean = false,
    val blobRestoreMessage: String? = null,
    val deployBusy: Boolean = false,
    val deployMessage: String? = null,
)

class CortexViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = CortexRepository(application)
    private val initialProvider = repo.provider()
    private val _state = MutableStateFlow(
        CortexUiState(
            connection = CoreConnection(baseUrl = repo.rememberedBaseUrl(), token = repo.rememberedCoreToken()),
            provider = initialProvider,
            hostingIdentifier = repo.hostingIdentifier(initialProvider),
            hostingConfigured = repo.hostingIdentifier(initialProvider).isNotBlank() && repo.hostingSecret(initialProvider).isNotBlank(),
            hostingHasSecret = repo.hostingSecret(initialProvider).isNotBlank(),
            blobConfigured = repo.blobConfigured(),
            blobSasUrl = repo.rememberedSecret("azure_blob_sas"),
        )
    )
    val state: StateFlow<CortexUiState> = _state.asStateFlow()

    private var eventsJob: Job? = null
    private var eventsRunning: AtomicBoolean? = null

    init {
        refreshLocalFiles()
        if (_state.value.connection.configured) connect()
        if (_state.value.hostingConfigured) refreshHosting()
    }

    fun saveConnection(baseUrl: String, token: String) {
        repo.rememberBaseUrl(baseUrl)
        if (token.isNotBlank()) repo.rememberCoreToken(token.trim())
        val selectedToken = token.trim().ifBlank { _state.value.connection.token }
        _state.value = _state.value.copy(
            connection = CoreConnection(baseUrl.trim().removeSuffix("/"), selectedToken),
            error = null,
        )
        connect()
    }

    fun setProvider(provider: HostingProviderId) {
        repo.setProvider(provider)
        val identifier = repo.hostingIdentifier(provider)
        val hasSecret = repo.hostingSecret(provider).isNotBlank()
        val configured = identifier.isNotBlank() && hasSecret
        _state.value = _state.value.copy(
            provider = provider,
            hostingIdentifier = identifier,
            hostingConfigured = configured,
            hostingHasSecret = hasSecret,
            hostingSnapshot = null,
            hostingLogs = emptyList(),
            hostingError = null,
        )
        if (configured) refreshHosting()
    }

    fun saveHostingConnection(identifier: String, secret: String) {
        val provider = _state.value.provider
        val cleanIdentifier = identifier.trim().removeSuffix("/")
        repo.rememberHostingIdentifier(provider, cleanIdentifier)
        if (secret.isNotBlank()) repo.rememberHostingSecret(provider, secret.trim())
        val hasSecret = repo.hostingSecret(provider).isNotBlank()
        _state.value = _state.value.copy(
            hostingIdentifier = cleanIdentifier,
            hostingConfigured = cleanIdentifier.isNotBlank() && hasSecret,
            hostingHasSecret = hasSecret,
            hostingError = null,
        )
        refreshHosting()
    }

    fun connect() {
        val config = _state.value.connection
        if (!config.configured) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    Triple(repo.health(config), repo.bootstrap(config), repo.chats(config))
                }
            }
            result.onSuccess { (health, snapshot, chats) ->
                _state.value = _state.value.copy(
                    health = health,
                    snapshot = snapshot,
                    chats = chats,
                    loading = false,
                    error = null,
                )
                startEvents()
            }.onFailure {
                _state.value = _state.value.copy(
                    health = CoreHealth(false, detail = it.message),
                    loading = false,
                    error = it.message ?: "Connection failed",
                )
            }
        }
    }

    fun refresh() {
        val config = _state.value.connection
        if (!config.configured) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { repo.bootstrap(config) to repo.chats(config) }
            }.onSuccess { (snapshot, chats) ->
                _state.value = _state.value.copy(snapshot = snapshot, chats = chats, error = null)
            }
        }
    }

    fun refreshHosting() {
        val provider = _state.value.provider
        val secret = repo.hostingSecret(provider)
        val identifier = repo.hostingIdentifier(provider)
        if (identifier.isBlank() || secret.isBlank()) {
            _state.value = _state.value.copy(hostingConfigured = false)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(hostingLoading = true, hostingError = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    repo.hostingSnapshot(provider, secret) to repo.hostingLogs(provider, secret, 200)
                }
            }.onSuccess { (snapshot, logs) ->
                _state.value = _state.value.copy(
                    hostingConfigured = true,
                    hostingSnapshot = snapshot,
                    hostingLogs = logs,
                    hostingLoading = false,
                    hostingError = null,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    hostingLoading = false,
                    hostingError = it.message ?: "Hosting connection failed",
                )
            }
        }
    }

    fun hostingPower(action: HostingPowerAction) {
        val provider = _state.value.provider
        val secret = repo.hostingSecret(provider)
        if (!_state.value.hostingConfigured || secret.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(hostingLoading = true, hostingError = null)
            runCatching {
                withContext(Dispatchers.IO) { repo.hostingPower(provider, secret, action) }
            }.onSuccess { snapshot ->
                if (snapshot != null) _state.value = _state.value.copy(hostingSnapshot = snapshot)
                _state.value = _state.value.copy(hostingLoading = false)
                refreshHosting()
            }.onFailure {
                _state.value = _state.value.copy(
                    hostingLoading = false,
                    hostingError = it.message ?: "Power action failed",
                )
            }
        }
    }

    fun openChat(chat: InboxChat) {
        val config = _state.value.connection
        _state.value = _state.value.copy(activeChat = chat, messages = emptyList())
        if (!config.configured) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repo.messages(config, chat.jid) } }
                .onSuccess { _state.value = _state.value.copy(messages = it) }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun closeChat() {
        _state.value = _state.value.copy(activeChat = null, messages = emptyList())
    }

    fun send(text: String) {
        val config = _state.value.connection
        val chat = _state.value.activeChat ?: return
        if (text.isBlank() || !config.configured) return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repo.sendText(config, chat.jid, text) } }
                .onSuccess {
                    delay(250)
                    openChat(chat)
                    refresh()
                }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun refreshLocalFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = repo.localFiles()
            withContext(Dispatchers.Main) { _state.value = _state.value.copy(localFiles = files, localFileCount = files.size) }
        }
    }

    fun openLocalFile(path: String) {
        if (path.substringAfterLast('.', "").lowercase() !in setOf("js", "mjs", "cjs", "json", "txt", "md", "yml", "yaml", "xml", "html", "css", "sh", "py", "properties", "gradle", "kt")) {
            _state.value = _state.value.copy(workspaceMessage = "This file stays local but is not a text file for the editor.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(workspaceBusy = true, workspaceMessage = null)
            runCatching { withContext(Dispatchers.IO) { repo.localText(path) } }
                .onSuccess { content ->
                    if (content == null) _state.value = _state.value.copy(workspaceBusy = false, workspaceMessage = "The file is too large to edit here.")
                    else _state.value = _state.value.copy(selectedLocalFile = path, editorContent = content, workspaceBusy = false)
                }
                .onFailure { _state.value = _state.value.copy(workspaceBusy = false, workspaceMessage = it.message) }
        }
    }

    fun updateEditor(content: String) {
        _state.value = _state.value.copy(editorContent = content)
    }

    fun closeEditor() {
        _state.value = _state.value.copy(selectedLocalFile = null, editorContent = "")
    }

    fun saveLocalFile() {
        val path = _state.value.selectedLocalFile ?: return
        val content = _state.value.editorContent
        viewModelScope.launch {
            _state.value = _state.value.copy(workspaceBusy = true, workspaceMessage = null)
            runCatching { withContext(Dispatchers.IO) { repo.saveLocalText(path, content); repo.localFiles() } }
                .onSuccess { files ->
                    _state.value = _state.value.copy(localFiles = files, localFileCount = files.size, workspaceBusy = false, workspaceMessage = "Saved on this phone.")
                }
                .onFailure { _state.value = _state.value.copy(workspaceBusy = false, workspaceMessage = it.message ?: "Could not save file") }
        }
    }

    fun createLocalFile(path: String) {
        val clean = path.trim().replace('\\', '/').removePrefix("/")
        if (clean.isBlank() || clean.split('/').any { it.isBlank() || it == "." || it == ".." }) {
            _state.value = _state.value.copy(workspaceMessage = "Enter a valid project file path.")
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    require(repo.localText(clean) == null && !repo.localFiles().any { it.path == clean }) { "That file already exists." }
                    repo.saveLocalText(clean, "")
                    repo.localFiles()
                }
            }
                .onSuccess { files ->
                    _state.value = _state.value.copy(localFiles = files, localFileCount = files.size, workspaceMessage = "Created on this phone.")
                    openLocalFile(clean)
                }
                .onFailure { _state.value = _state.value.copy(workspaceMessage = it.message ?: "Could not create file") }
        }
    }

    fun importDocument(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "imported-file"
            val name = displayName.replace('\\', '/').substringAfterLast('/').trim()
            runCatching {
                val stream = resolver.openInputStream(uri) ?: error("Could not read selected file")
                val count = if (name.endsWith(".zip", true)) repo.importLocalZip(stream)
                    else { repo.saveLocalFile(name, stream); 1 }
                count to repo.localFiles()
            }.onSuccess { (count, files) ->
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(localFiles = files, localFileCount = files.size, workspaceMessage = "Imported $count file(s) to this phone.")
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(workspaceMessage = error.message ?: "Import failed")
                }
            }
        }
    }

    fun deleteLocalFile(path: String) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { repo.deleteLocalFile(path); repo.localFiles() } }
                .onSuccess { files ->
                    _state.value = _state.value.copy(localFiles = files, localFileCount = files.size, workspaceMessage = "Deleted from this phone.")
                }
                .onFailure { _state.value = _state.value.copy(workspaceMessage = it.message ?: "Delete failed") }
        }
    }

    fun saveBlobSas(value: String) {
        repo.rememberBlobSas(value.trim())
        _state.value = _state.value.copy(blobConfigured = value.isNotBlank(), blobSasUrl = value.trim(), blobMessage = "Storage setting saved securely on this phone.")
    }

    fun syncBlobBackup() {
        if (!_state.value.blobConfigured) {
            _state.value = _state.value.copy(blobMessage = "Add your Azure Blob container SAS URL in Settings first.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(blobBusy = true, blobMessage = null)
            runCatching { withContext(Dispatchers.IO) { repo.syncBlob() } }
                .onSuccess { result ->
                    val size = if (result.bytesUploaded >= 1024 * 1024) "%.1f MB".format(result.bytesUploaded / 1024.0 / 1024.0) else "${result.bytesUploaded / 1024} KB"
                    _state.value = _state.value.copy(blobBusy = false, blobMessage = "Backed up ${result.uploadedFiles} changed files ($size); skipped ${result.unchangedFiles} unchanged.")
                }
                .onFailure { _state.value = _state.value.copy(blobBusy = false, blobMessage = it.message ?: "Backup failed") }
        }
    }

    fun restoreBlobBackup() {
        if (!_state.value.blobConfigured) {
            _state.value = _state.value.copy(blobRestoreMessage = "Configure Azure Blob backup in Settings first.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(blobRestoreBusy = true, blobRestoreMessage = null)
            runCatching { withContext(Dispatchers.IO) { repo.restoreBlob() } }
                .onSuccess { result ->
                    if (result.conflicts.isNotEmpty()) {
                        _state.value = _state.value.copy(
                            blobRestoreBusy = false,
                            blobRestoreMessage = "Restore stopped to protect local edits in: ${result.conflicts.take(4).joinToString()}",
                        )
                    } else {
                        val size = if (result.bytesDownloaded >= 1024 * 1024) "%.1f MB".format(result.bytesDownloaded / 1024.0 / 1024.0) else "${result.bytesDownloaded / 1024} KB"
                        _state.value = _state.value.copy(
                            blobRestoreBusy = false,
                            blobRestoreMessage = "Downloaded ${result.restoredFiles} changed files ($size); skipped ${result.unchangedFiles} unchanged.",
                        )
                        refreshLocalFiles()
                    }
                }
                .onFailure { _state.value = _state.value.copy(blobRestoreBusy = false, blobRestoreMessage = it.message ?: "Restore failed") }
        }
    }

    fun deployWorkspace() {
        val current = _state.value
        val secret = repo.hostingSecret(current.provider)
        if (!current.hostingConfigured || secret.isBlank()) {
            _state.value = _state.value.copy(deployMessage = "Connect the Azure Cortex Agent in Settings first.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(deployBusy = true, deployMessage = null)
            runCatching { withContext(Dispatchers.IO) { repo.deployLocalFiles(current.provider, secret) } }
                .onSuccess { result ->
                    val size = if (result.bytesUploaded >= 1024 * 1024) "%.1f MB".format(result.bytesUploaded / 1024.0 / 1024.0) else "${result.bytesUploaded / 1024} KB"
                    val install = if (result.dependenciesInstalled) " Dependencies installed on Azure." else ""
                    _state.value = _state.value.copy(
                        deployBusy = false,
                        deployMessage = "Sent ${result.uploadedFiles} changed files ($size); skipped ${result.unchangedFiles} unchanged.$install ${if (result.uploadedFiles > 0) "Night restarted." else "No changes to deploy."}",
                    )
                    refreshHosting()
                }
                .onFailure { _state.value = _state.value.copy(deployBusy = false, deployMessage = it.message ?: "Deploy failed") }
        }
    }

    private fun startEvents() {
        eventsRunning?.set(false)
        eventsJob?.cancel()
        val config = _state.value.connection
        if (!config.configured) return
        val running = AtomicBoolean(true)
        eventsRunning = running
        eventsJob = viewModelScope.launch(Dispatchers.IO) {
            repo.streamEvents(config, running) { viewModelScope.launch { refresh() } }
        }
    }

    override fun onCleared() {
        eventsRunning?.set(false)
        super.onCleared()
    }
}
