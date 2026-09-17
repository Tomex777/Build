package com.night.cortex.data

import android.content.Context
import com.night.cortex.hosting.HostingProviderId
import java.io.File

class CortexRepository(context: Context) {
    private val connectionStore = ConnectionStore(context)
    private val prefs = context.getSharedPreferences("cortex_state", Context.MODE_PRIVATE)
    private val api = NightCoreApi()
    private val workspace = LocalWorkspace(context)

    fun rememberedBaseUrl(): String = connectionStore.loadBaseUrl()
    fun rememberBaseUrl(baseUrl: String) = connectionStore.saveBaseUrl(baseUrl)

    fun provider(): HostingProviderId = runCatching {
        HostingProviderId.valueOf(prefs.getString("provider", HostingProviderId.AZURE.name)!!)
    }.getOrDefault(HostingProviderId.AZURE)

    fun setProvider(id: HostingProviderId) = prefs.edit().putString("provider", id.name).apply()

    fun health(config: CoreConnection) = api.health(config.baseUrl)
    fun bootstrap(config: CoreConnection) = api.bootstrap(config)
    fun chats(config: CoreConnection) = api.chats(config)
    fun messages(config: CoreConnection, jid: String) = api.messages(config, jid)
    fun sendText(config: CoreConnection, jid: String, text: String) = api.sendText(config, jid, text)
    fun streamEvents(config: CoreConnection, running: java.util.concurrent.atomic.AtomicBoolean, onEvent: () -> Unit) = api.streamEvents(config, running, onEvent)
    fun localFiles(): List<File> = workspace.listProjectFiles("Night")
}
