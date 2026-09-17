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

class CortexRepository(context: Context) {
    private val connectionStore = ConnectionStore(context)
    private val hostingStore = HostingConnectionStore(context)
    private val prefs = context.getSharedPreferences("cortex_state", Context.MODE_PRIVATE)
    private val api = NightCoreApi()
    private val workspace = LocalWorkspace(context)

    fun rememberedBaseUrl(): String = connectionStore.loadBaseUrl()
    fun rememberBaseUrl(baseUrl: String) = connectionStore.saveBaseUrl(baseUrl)

    fun provider(): HostingProviderId = runCatching {
        HostingProviderId.valueOf(prefs.getString("provider", HostingProviderId.AZURE.name)!!)
    }.getOrDefault(HostingProviderId.AZURE)

    fun setProvider(id: HostingProviderId) = prefs.edit().putString("provider", id.name).apply()

    fun hostingIdentifier(id: HostingProviderId): String = hostingStore.identifier(id)
    fun rememberHostingIdentifier(id: HostingProviderId, value: String) = hostingStore.saveIdentifier(id, value)

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

    fun localFiles(): List<File> = workspace.listProjectFiles("Night")

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
