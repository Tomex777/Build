package com.tomex.whatsappclient

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.time.Instant
import java.util.Optional
import java.util.OptionalInt
import java.util.concurrent.Executors

enum class ClientPhase {
    LOADING, NEEDS_LINK, PAIRING, CONNECTING, CONNECTED, DISCONNECTED, ERROR
}

data class ChatUi(
    val jid: String,
    val title: String,
    val preview: String,
    val timestamp: Long,
    val unread: Int,
    val isGroup: Boolean
)

data class MessageUi(
    val id: String,
    val text: String,
    val fromMe: Boolean,
    val timestamp: Long,
    val sender: String
)

data class ClientUiState(
    val phase: ClientPhase = ClientPhase.LOADING,
    val status: String = "Starting…",
    val pairingCode: String? = null,
    val hasSavedSession: Boolean = false,
    val chats: List<ChatUi> = emptyList(),
    val selectedJid: String? = null,
    val selectedTitle: String = "",
    val messages: List<MessageUi> = emptyList(),
    val sending: Boolean = false
)

class CobaltController(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val tag = "WhatsAppClient"

    @Volatile private var currentClient: Any? = null
    private val rawChats = LinkedHashMap<String, Any>()

    var state by mutableStateOf(ClientUiState())
        private set

    fun start() {
        setState { it.copy(phase = ClientPhase.LOADING, status = "Checking saved session…") }
        worker.execute {
            try {
                verifyMessageSurface()
                val options = loadLatestOptions()
                if (options == null) {
                    setState {
                        it.copy(
                            phase = ClientPhase.NEEDS_LINK,
                            status = "Link this phone as a WhatsApp companion device.",
                            hasSavedSession = false
                        )
                    }
                    return@execute
                }
                val registered = options.javaClass.getMethod("registered").invoke(options) as Optional<*>
                val client = registered.orElse(null)
                if (client == null) {
                    setState {
                        it.copy(
                            phase = ClientPhase.NEEDS_LINK,
                            status = "Saved setup is incomplete. Link this phone again.",
                            hasSavedSession = false
                        )
                    }
                    return@execute
                }
                attachListeners(client)
                currentClient = client
                setState {
                    it.copy(
                        phase = ClientPhase.CONNECTING,
                        status = "Reconnecting WhatsApp…",
                        hasSavedSession = true
                    )
                }
                invokeLinkedClient(client, "connect")
            } catch (error: Throwable) {
                fail("Startup", error)
            }
        }
    }

    fun pair(phone: String) {
        val digits = phone.filter(Char::isDigit)
        if (digits.length !in 8..15) {
            setState { it.copy(status = "Enter the full international number with country code.") }
            return
        }
        setState {
            it.copy(
                phase = ClientPhase.PAIRING,
                status = "Requesting a pairing code…",
                pairingCode = null
            )
        }
        worker.execute {
            try {
                disconnectInternal()
                val options = createFreshOptions()
                val pairingClass = Class.forName(
                    "com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientVerificationHandler\$Web\$PairingCode",
                    true,
                    javaClass.classLoader
                )
                val authenticatorClass = Class.forName(
                    "com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientPasskeyAuthenticator",
                    true,
                    javaClass.classLoader
                )
                val authenticator = Proxy.newProxyInstance(
                    authenticatorClass.classLoader,
                    arrayOf(authenticatorClass)
                ) { proxy, method, args ->
                    when (method.name) {
                        "assertCredential" -> throw UnsupportedOperationException(
                            "WhatsApp requested a passkey integrity challenge that is not wired on Android yet."
                        )
                        "toString" -> "AndroidPasskeyAuthenticator"
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.firstOrNull()
                        else -> null
                    }
                }
                val pairingHandler = Proxy.newProxyInstance(
                    pairingClass.classLoader,
                    arrayOf(pairingClass)
                ) { proxy, method, args ->
                    when (method.name) {
                        "handle" -> {
                            val code = args?.firstOrNull()?.toString().orEmpty()
                            setState {
                                it.copy(
                                    phase = ClientPhase.PAIRING,
                                    pairingCode = code,
                                    status = "Enter this code in WhatsApp → Linked devices → Link with phone number."
                                )
                            }
                            null
                        }
                        "passkeyAuthenticator" -> authenticator
                        "toString" -> "AndroidPairingHandler"
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.firstOrNull()
                        else -> null
                    }
                }
                val client = options.javaClass
                    .getMethod("unregistered", java.lang.Long.TYPE, pairingClass)
                    .invoke(options, digits.toLong(), pairingHandler)
                attachListeners(client)
                currentClient = client
                invokeLinkedClient(client, "connect")
            } catch (error: Throwable) {
                fail("Pairing", error)
            }
        }
    }

    fun reconnect() {
        worker.execute {
            try {
                val options = loadLatestOptions() ?: error("No saved session found")
                val registered = options.javaClass.getMethod("registered").invoke(options) as Optional<*>
                val client = registered.orElse(null) ?: error("Saved session has not finished pairing")
                disconnectInternal()
                attachListeners(client)
                currentClient = client
                setState {
                    it.copy(
                        phase = ClientPhase.CONNECTING,
                        status = "Reconnecting WhatsApp…",
                        hasSavedSession = true
                    )
                }
                invokeLinkedClient(client, "connect")
            } catch (error: Throwable) {
                fail("Reconnect", error)
            }
        }
    }

    fun disconnect() {
        worker.execute {
            try {
                disconnectInternal()
                setState {
                    it.copy(
                        phase = ClientPhase.DISCONNECTED,
                        status = "Disconnected. Your linked session is still saved.",
                        chats = emptyList(),
                        messages = emptyList()
                    )
                }
            } catch (error: Throwable) {
                fail("Disconnect", error)
            }
        }
    }

    fun openChat(jid: String, title: String) {
        setState { it.copy(selectedJid = jid, selectedTitle = title, messages = emptyList()) }
        refreshMessages(jid)
    }

    fun closeChat() {
        setState { it.copy(selectedJid = null, selectedTitle = "", messages = emptyList()) }
    }

    fun openNumber(number: String) {
        val digits = number.filter(Char::isDigit)
        if (digits.length !in 8..15) {
            setState { it.copy(status = "Enter a full international phone number.") }
            return
        }
        setState {
            it.copy(
                selectedJid = digits,
                selectedTitle = "+$digits",
                messages = emptyList()
            )
        }
    }

    fun sendText(text: String) {
        val body = text.trim()
        val jid = state.selectedJid
        if (body.isBlank() || jid.isNullOrBlank()) return
        val client = currentClient ?: run {
            setState { it.copy(status = "Reconnect WhatsApp before sending.") }
            return
        }
        setState { it.copy(sending = true) }
        worker.execute {
            try {
                val loader = javaClass.classLoader
                val jidClass = Class.forName("com.github.auties00.cobalt.wire.core.jid.Jid", true, loader)
                val jidProviderClass = Class.forName(
                    "com.github.auties00.cobalt.wire.core.jid.JidProvider", true, loader
                )
                val containerClass = Class.forName(
                    "com.github.auties00.cobalt.wire.linked.message.LinkedMessageContainer", true, loader
                )
                val whatsappClientClass = Class.forName(
                    "com.github.auties00.cobalt.client.WhatsAppClient", true, loader
                )
                val recipient = jidClass.getMethod("of", String::class.java).invoke(null, jid)
                val container = containerClass.getMethod("of", String::class.java).invoke(null, body)
                whatsappClientClass
                    .getMethod("sendMessage", jidProviderClass, containerClass)
                    .invoke(client, recipient, container)

                if (jid.contains("@")) {
                    refreshChats()
                    refreshMessages(jid)
                } else {
                    setState {
                        it.copy(
                            messages = it.messages + MessageUi(
                                id = "local-${System.currentTimeMillis()}",
                                text = body,
                                fromMe = true,
                                timestamp = System.currentTimeMillis(),
                                sender = "me"
                            )
                        )
                    }
                }
                setState { it.copy(sending = false, status = "Connected") }
            } catch (error: Throwable) {
                setState { it.copy(sending = false) }
                fail("Send", error)
            }
        }
    }

    private fun attachListeners(client: Any) {
        val loader = javaClass.classLoader
        val whatsappClientClass = Class.forName(
            "com.github.auties00.cobalt.client.WhatsAppClient", true, loader
        )
        val loggedInClass = Class.forName(
            "com.github.auties00.cobalt.listener.LoggedInListener", true, loader
        )
        val loggedIn = Proxy.newProxyInstance(
            loggedInClass.classLoader, arrayOf(loggedInClass)
        ) { proxy, method, args ->
            when (method.name) {
                "onLoggedIn" -> {
                    setState {
                        it.copy(
                            phase = ClientPhase.CONNECTED,
                            status = "Connected",
                            pairingCode = null,
                            hasSavedSession = true
                        )
                    }
                    refreshChats()
                    null
                }
                "toString" -> "AndroidLoggedInListener"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
        whatsappClientClass.getMethod("addLoggedInListener", loggedInClass).invoke(client, loggedIn)

        val newMessageClass = Class.forName(
            "com.github.auties00.cobalt.listener.NewMessageListener", true, loader
        )
        val newMessage = Proxy.newProxyInstance(
            newMessageClass.classLoader, arrayOf(newMessageClass)
        ) { proxy, method, args ->
            when (method.name) {
                "onNewMessage" -> {
                    val info = args?.getOrNull(1)
                    val parentJid = info?.let(::messageParentJid)
                    refreshChats()
                    if (parentJid != null && parentJid == state.selectedJid) {
                        refreshMessages(parentJid)
                    }
                    null
                }
                "toString" -> "AndroidNewMessageListener"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
        whatsappClientClass.getMethod("addNewMessageListener", newMessageClass).invoke(client, newMessage)

        runCatching {
            val chatsClass = Class.forName(
                "com.github.auties00.cobalt.listener.linked.LinkedChatsListener", true, loader
            )
            val chatsListener = Proxy.newProxyInstance(
                chatsClass.classLoader, arrayOf(chatsClass)
            ) { proxy, method, args ->
                when (method.name) {
                    "onChats" -> {
                        val chats = args?.getOrNull(1) as? Collection<*>
                        if (chats != null) refreshChatsFrom(chats)
                        null
                    }
                    "toString" -> "AndroidChatsListener"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> null
                }
            }
            val linkedClientClass = Class.forName(
                "com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient", true, loader
            )
            linkedClientClass.getMethod("addChatsListener", chatsClass).invoke(client, chatsListener)
        }.onFailure { Log.w(tag, "Chats listener unavailable", it) }

        runCatching {
            val disconnectedClass = Class.forName(
                "com.github.auties00.cobalt.listener.DisconnectedListener", true, loader
            )
            val disconnected = Proxy.newProxyInstance(
                disconnectedClass.classLoader, arrayOf(disconnectedClass)
            ) { proxy, method, args ->
                when (method.name) {
                    "onDisconnected" -> {
                        setState {
                            it.copy(
                                phase = ClientPhase.DISCONNECTED,
                                status = "Disconnected",
                                hasSavedSession = true
                            )
                        }
                        null
                    }
                    "toString" -> "AndroidDisconnectedListener"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> null
                }
            }
            whatsappClientClass.getMethod("addDisconnectedListener", disconnectedClass)
                .invoke(client, disconnected)
        }
    }

    private fun refreshChats() {
        val client = currentClient ?: return
        worker.execute {
            try {
                val store = client.javaClass.getMethod("store").invoke(client)
                val chatStore = store.javaClass.getMethod("chatStore").invoke(store)
                val chats = chatStore.javaClass.getMethod("chats").invoke(chatStore) as Collection<*>
                refreshChatsFrom(chats)
            } catch (error: Throwable) {
                Log.w(tag, "Could not refresh chats", error)
            }
        }
    }

    private fun refreshChatsFrom(chats: Collection<*>) {
        val snapshots = ArrayList<ChatUi>()
        synchronized(rawChats) {
            rawChats.clear()
            for (raw in chats) {
                if (raw == null) continue
                val jid = invoke(raw, "jid")?.toString().orEmpty()
                if (jid.isBlank()) continue
                val archived = invoke(raw, "archived") as? Boolean ?: false
                if (archived) continue
                rawChats[jid] = raw

                val title = optionalString(invoke(raw, "displayName"))
                    ?: optionalString(invoke(raw, "name"))
                    ?: jid.substringBefore("@").ifBlank { jid }
                val newest = unwrapOptional(invoke(raw, "newestMessage"))
                val preview = newest?.let(::renderMessageText).orEmpty()
                val timestamp = instantMillis(unwrapOptional(invoke(raw, "lastMsgTimestamp")))
                val unread = optionalInt(invoke(raw, "unreadCount"))

                snapshots += ChatUi(
                    jid = jid,
                    title = title,
                    preview = preview.ifBlank { "No messages yet" },
                    timestamp = timestamp,
                    unread = unread,
                    isGroup = jid.contains("@g.us")
                )
            }
        }
        snapshots.sortWith(compareByDescending<ChatUi> { it.timestamp }.thenBy { it.title.lowercase() })
        setState {
            it.copy(
                chats = snapshots,
                phase = if (it.phase == ClientPhase.CONNECTING) ClientPhase.CONNECTED else it.phase,
                status = if (it.phase == ClientPhase.CONNECTING) "Connected" else it.status
            )
        }
    }

    private fun refreshMessages(jid: String) {
        worker.execute {
            try {
                val raw = synchronized(rawChats) { rawChats[jid] } ?: run {
                    setState { it.copy(messages = emptyList()) }
                    return@execute
                }
                val stream = invoke(raw, "messages") ?: return@execute
                val iterator = stream.javaClass.getMethod("iterator").invoke(stream) as Iterator<*>
                val result = ArrayList<MessageUi>()
                while (iterator.hasNext()) {
                    val info = iterator.next() ?: continue
                    result += toMessageUi(info)
                    if (result.size > 500) result.removeAt(0)
                }
                runCatching { stream.javaClass.getMethod("close").invoke(stream) }
                result.sortBy { it.timestamp }
                setState { it.copy(messages = result) }
            } catch (error: Throwable) {
                Log.w(tag, "Could not load messages for $jid", error)
            }
        }
    }

    private fun toMessageUi(info: Any): MessageUi {
        val key = invoke(info, "key")
        val fromMe = key?.let { invoke(it, "fromMe") as? Boolean } ?: false
        val id = key?.let { optionalString(invoke(it, "id")) }
            ?: "msg-${System.identityHashCode(info)}"
        val sender = key?.let { unwrapOptional(invoke(it, "senderJid"))?.toString() }
            ?: if (fromMe) "me" else ""
        return MessageUi(
            id = id,
            text = renderMessageText(info),
            fromMe = fromMe,
            timestamp = instantMillis(unwrapOptional(invoke(info, "timestamp"))),
            sender = sender
        )
    }

    private fun renderMessageText(info: Any): String {
        return try {
            val container = invoke(info, "message") ?: return "[Message]"
            val content = invoke(container, "content") ?: return "[Message]"
            val textMethod = content.javaClass.methods.firstOrNull {
                it.name == "text" && it.parameterCount == 0
            }
            val text = optionalString(textMethod?.invoke(content))
            if (!text.isNullOrBlank()) return text
            when {
                content.javaClass.simpleName.contains("Image", true) -> "📷 Photo"
                content.javaClass.simpleName.contains("Video", true) -> "🎥 Video"
                content.javaClass.simpleName.contains("Audio", true) -> "🎙️ Audio"
                content.javaClass.simpleName.contains("Sticker", true) -> "Sticker"
                content.javaClass.simpleName.contains("Document", true) -> "📄 Document"
                content.javaClass.simpleName.contains("Contact", true) -> "👤 Contact"
                content.javaClass.simpleName.contains("Location", true) -> "📍 Location"
                content.javaClass.simpleName.contains("Poll", true) -> "Poll"
                else -> "[${content.javaClass.simpleName.removeSuffix("Message")}]"
            }
        } catch (_: Throwable) {
            "[Message]"
        }
    }

    private fun messageParentJid(info: Any): String? {
        return runCatching {
            val key = invoke(info, "key") ?: return@runCatching null
            unwrapOptional(invoke(key, "parentJid"))?.toString()
        }.getOrNull()
    }

    private fun verifyMessageSurface() {
        val loader = javaClass.classLoader
        val jidClass = Class.forName("com.github.auties00.cobalt.wire.core.jid.Jid", true, loader)
        val jidProviderClass = Class.forName(
            "com.github.auties00.cobalt.wire.core.jid.JidProvider", true, loader
        )
        val containerClass = Class.forName(
            "com.github.auties00.cobalt.wire.linked.message.LinkedMessageContainer", true, loader
        )
        val newMessageClass = Class.forName(
            "com.github.auties00.cobalt.listener.NewMessageListener", true, loader
        )
        val clientClass = Class.forName(
            "com.github.auties00.cobalt.client.WhatsAppClient", true, loader
        )
        jidClass.getMethod("of", String::class.java)
        containerClass.getMethod("of", String::class.java)
        clientClass.getMethod("sendMessage", jidProviderClass, containerClass)
        clientClass.getMethod("addNewMessageListener", newMessageClass)
    }

    private fun createFreshOptions(): Any {
        val web = createPersistentWebBuilder()
        return web.javaClass.getMethod("createConnection").invoke(web)
    }

    private fun loadLatestOptions(): Any? {
        val web = createPersistentWebBuilder()
        val result = web.javaClass.getMethod("loadLatestConnection").invoke(web) as Optional<*>
        return result.orElse(null)
    }

    private fun createPersistentWebBuilder(): Any {
        val loader = javaClass.classLoader
        val clientClass = Class.forName(
            "com.github.auties00.cobalt.client.WhatsAppClient", true, loader
        )
        val builder = clientClass.getMethod("builder").invoke(null)
        val linked = builder.javaClass.getMethod("linkedApi").invoke(builder)
        val factoryClass = Class.forName(
            "com.github.auties00.cobalt.store.linked.LinkedWhatsAppStoreFactory", true, loader
        )
        val storeDir: Path = appContext.filesDir.toPath().resolve("whatsapp-client")
        val persistentFactory = factoryClass
            .getMethod("persistent", Path::class.java)
            .invoke(null, storeDir)
        return linked.javaClass
            .getMethod("webClient", factoryClass)
            .invoke(linked, persistentFactory)
    }

    private fun invokeLinkedClient(client: Any, method: String): Any? {
        val linkedClientClass = Class.forName(
            "com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient",
            true,
            javaClass.classLoader
        )
        return linkedClientClass.getMethod(method).invoke(client)
    }

    private fun disconnectInternal() {
        val client = currentClient ?: return
        try {
            invokeLinkedClient(client, "disconnect")
        } finally {
            currentClient = null
        }
    }

    private fun invoke(target: Any, name: String): Any? {
        val method = target.javaClass.methods.firstOrNull {
            it.name == name && it.parameterCount == 0
        } ?: return null
        return method.invoke(target)
    }

    private fun unwrapOptional(value: Any?): Any? =
        if (value is Optional<*>) value.orElse(null) else value

    private fun optionalString(value: Any?): String? {
        val unwrapped = unwrapOptional(value) ?: return null
        return unwrapped.toString().takeIf { it.isNotBlank() }
    }

    private fun optionalInt(value: Any?): Int = when (value) {
        is OptionalInt -> if (value.isPresent) value.asInt else 0
        is Number -> value.toInt()
        else -> 0
    }

    private fun instantMillis(value: Any?): Long = when (value) {
        is Instant -> value.toEpochMilli()
        is Number -> value.toLong()
        else -> 0L
    }

    private fun fail(stage: String, error: Throwable) {
        val root = generateSequence(error) { it.cause }.last()
        Log.e(tag, "$stage failed", error)
        setState {
            it.copy(
                phase = ClientPhase.ERROR,
                status = "$stage failed: ${root::class.java.simpleName}: ${root.message ?: "no message"}"
            )
        }
    }

    private fun setState(update: (ClientUiState) -> ClientUiState) {
        main.post { state = update(state) }
    }

    fun close() {
        worker.execute { runCatching { disconnectInternal() } }
        worker.shutdown()
    }
}
