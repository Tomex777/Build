package com.night.cortex.data

import android.content.Context
import com.night.cortex.hosting.AzureAgentClient
import com.night.cortex.hosting.BotHostingClient
import com.night.cortex.hosting.HostingConnectionStore
import com.night.cortex.hosting.HostingControlClient
import com.night.cortex.hosting.HostingFileEntry
import com.night.cortex.hosting.HostingPowerAction
import com.night.cortex.hosting.HostingProviderId
import com.night.cortex.hosting.HostingSnapshot
import java.io.File
import java.io.InputStream

data class DeployResult(
    val uploadedFiles: Int,
    val unchangedFiles: Int,
    val bytesUploaded: Long,
    val dependenciesInstalled: Boolean,
)

class CortexRepository(context: Context) {
    private val connectionStore = ConnectionStore(context)
    private val hostingStore = HostingConnectionStore(context)
    private val prefs = context.getSharedPreferences("cortex_state", Context.MODE_PRIVATE)
    private val api = NightCoreApi()
    private val workspace = LocalWorkspace(context)
    private val secretVault = SecretVault(context)
    private val blobBackup = AzureBlobBackup(workspace)
    private val syncPrefs = context.getSharedPreferences("cortex_sync_state", Context.MODE_PRIVATE)

    fun rememberedBaseUrl(): String = connectionStore.loadBaseUrl()
    fun rememberBaseUrl(baseUrl: String) = connectionStore.saveBaseUrl(baseUrl)
    fun rememberCoreToken(token: String) = secretVault.put("night_core_token", token)
    fun rememberedCoreToken(): String = secretVault.get("night_core_token").orEmpty()
    fun rememberSecret(key: String, value: String) = secretVault.put(key, value)
    fun rememberedSecret(key: String): String = secretVault.get(key).orEmpty()

    fun provider(): HostingProviderId = runCatching {
        HostingProviderId.valueOf(prefs.getString("provider", HostingProviderId.AZURE.name)!!)
    }.getOrDefault(HostingProviderId.AZURE)

    fun setProvider(id: HostingProviderId) = prefs.edit().putString("provider", id.name).apply()

    fun hostingIdentifier(id: HostingProviderId): String = hostingStore.identifier(id)
    fun rememberHostingIdentifier(id: HostingProviderId, value: String) = hostingStore.saveIdentifier(id, value)
    fun hostingSecret(id: HostingProviderId): String = rememberedSecret("hosting_${id.name}")
    fun rememberHostingSecret(id: HostingProviderId, value: String) = rememberSecret("hosting_${id.name}", value)
    fun blobConfigured(): Boolean = rememberedSecret("azure_blob_sas").isNotBlank()
    fun rememberBlobSas(value: String) = rememberSecret("azure_blob_sas", value)

    fun health(config: CoreConnection) = api.health(config.baseUrl)
    fun bootstrap(config: CoreConnection) = api.bootstrap(config)
    fun chats(config: CoreConnection) = api.chats(config)
    fun messages(config: CoreConnection, jid: String) = api.messages(config, jid)
    fun sendText(config: CoreConnection, jid: String, text: String) = api.sendText(config, jid, text)
    fun streamEvents(config: CoreConnection, running: java.util.concurrent.atomic.AtomicBoolean, onEvent: () -> Unit) = api.streamEvents(config, running, onEvent)

    fun hostingSnapshot(provider: HostingProviderId, secret: String): HostingSnapshot = hostingClient(provider, secret).snapshot()
    fun hostingLogs(provider: HostingProviderId, secret: String, limit: Int = 200): List<String> = hostingClient(provider, secret).logs(limit)
    fun hostingPower(provider: HostingProviderId, secret: String, action: HostingPowerAction): HostingSnapshot? = hostingClient(provider, secret).power(action)
    fun hostingFiles(provider: HostingProviderId, secret: String, path: String = "/"): List<HostingFileEntry> = hostingClient(provider, secret).listFiles(path)
    fun hostingReadText(provider: HostingProviderId, secret: String, path: String): String = hostingClient(provider, secret).readText(path)
    fun hostingWriteText(provider: HostingProviderId, secret: String, path: String, content: String) = hostingClient(provider, secret).writeText(path, content)

    fun localFiles(): List<WorkspaceFile> = workspace.files("Night")
    fun localText(path: String): String? = workspace.readText("Night", path)
    fun saveLocalText(path: String, content: String) = workspace.saveText("Night", path, content)
    fun saveLocalFile(path: String, input: InputStream) = workspace.save("Night", path, input)
    fun importLocalZip(input: InputStream): Int = workspace.importZip("Night", input)
    fun deleteLocalFile(path: String) = workspace.delete("Night", path)
    fun localFileCount(): Int = workspace.files("Night").size

    fun syncBlob(): BlobSyncResult {
        val sas = rememberedSecret("azure_blob_sas")
        require(sas.isNotBlank()) { "Configure the Azure Blob container SAS URL in Settings first" }
        return blobBackup.sync(
            containerSasUrl = sas,
            onBackedUp = { path, hash -> syncPrefs.edit().putString("blob:$path", hash).apply() },
        )
    }

    fun restoreBlob(): BlobRestoreResult {
        val sas = rememberedSecret("azure_blob_sas")
        require(sas.isNotBlank()) { "Configure the Azure Blob container SAS URL in Settings first" }
        return blobBackup.restore(
            containerSasUrl = sas,
            currentHash = { path -> workspace.sha256(relativePath = path) },
            previousHash = { path -> syncPrefs.getString("blob:$path", null) },
            onRestored = { path, hash -> syncPrefs.edit().putString("blob:$path", hash).apply() },
        )
    }

    fun deployLocalFiles(provider: HostingProviderId, secret: String): DeployResult {
        require(provider == HostingProviderId.AZURE) { "Deploy from the local workspace is available for Azure" }
        val client = hostingClient(provider, secret)
        var uploaded = 0
        var unchanged = 0
        var bytes = 0L
        var dependencyFilesChanged = false
        val uploadedHashes = mutableMapOf<String, String>()
        workspace.listProjectFiles("Night").forEach { file ->
            require(workspace.isTransferable("Night", file)) { "A file is too large to deploy: " + workspace.relativePath("Night", file) }
            val path = workspace.relativePath("Night", file)
            val hash = workspace.sha256("Night", path) ?: return@forEach
            if (hash == syncPrefs.getString("deployed:$path", null)) {
                unchanged++
                return@forEach
            }
            val content = workspace.readBytes("Night", path)
            client.writeBytes(path, content)
            uploadedHashes[path] = hash
            uploaded++
            bytes += content.size
            if (path == "package.json" || path == "package-lock.json") dependencyFilesChanged = true
        }
        val installed = dependencyFilesChanged && workspace.exists("Night", "package.json")
        if (installed) client.installDependencies()
        if (uploaded > 0) client.power(HostingPowerAction.RESTART)
        if (uploadedHashes.isNotEmpty()) {
            val editor = syncPrefs.edit()
            uploadedHashes.forEach { (path, hash) -> editor.putString("deployed:$path", hash) }
            editor.apply()
        }
        return DeployResult(uploaded, unchanged, bytes, installed)
    }

    private fun hostingClient(provider: HostingProviderId, secret: String): HostingControlClient {
        require(secret.isNotBlank()) { "Hosting credential is required" }
        val identifier = hostingStore.identifier(provider)
        require(identifier.isNotBlank()) {
            when (provider) {
                HostingProviderId.BOT_HOSTING -> "Bot-Hosting deployment ID is not configured"
                HostingProviderId.AZURE -> "Azure Cortex Agent URL is not configured"
            }
        }
        return when (provider) {
            HostingProviderId.BOT_HOSTING -> BotHostingClient(secret, identifier)
            HostingProviderId.AZURE -> AzureAgentClient(identifier, secret)
        }
    }
}
