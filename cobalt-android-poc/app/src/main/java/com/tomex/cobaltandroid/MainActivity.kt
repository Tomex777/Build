package com.tomex.cobaltandroid

import android.app.Activity
import android.content.ClipboardManager
import android.content.ClipData
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val tag = "CobaltPOC"
    private val worker = Executors.newSingleThreadExecutor()

    @Volatile
    private var currentClient: Any? = null

    @Volatile
    private var hasSavedSession = false

    private lateinit var phoneInput: EditText
    private lateinit var statusText: TextView
    private lateinit var pairingCodeText: TextView
    private lateinit var copyButton: Button
    private lateinit var linkButton: Button
    private lateinit var reconnectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var destinationInput: EditText
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var incomingText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        probeSavedSession()
    }

    private fun buildUi() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "WhatsApp client"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "Cobalt Android proof of concept · linked-device mode"
            textSize = 15f
            alpha = 0.70f
            setPadding(0, dp(8), 0, dp(28))
        })

        root.addView(TextView(this).apply {
            text = "Phone number"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        })

        phoneInput = EditText(this).apply {
            hint = "Country code + number, e.g. 234…"
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
        }
        root.addView(
            phoneInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        linkButton = Button(this).apply {
            text = "Link with code"
            setOnClickListener { startPairing() }
        }
        root.addView(linkButton)

        reconnectButton = Button(this).apply {
            text = "Reconnect saved session"
            setOnClickListener { reconnectSavedSession() }
        }
        root.addView(reconnectButton)

        root.addView(TextView(this).apply {
            text = "PAIRING CODE"
            textSize = 12f
            alpha = 0.60f
            setPadding(0, dp(30), 0, dp(8))
        })

        pairingCodeText = TextView(this).apply {
            text = "— — — —"
            textSize = 34f
            gravity = Gravity.CENTER
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(dp(12), dp(18), dp(12), dp(18))
            setTextIsSelectable(true)
        }
        root.addView(
            pairingCodeText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        copyButton = Button(this).apply {
            text = "Copy code"
            isEnabled = false
            setOnClickListener {
                val code = pairingCodeText.text.toString().replace(" ", "")
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("WhatsApp pairing code", code))
                setStatus("Pairing code copied. Open WhatsApp → Linked devices → Link with phone number.")
            }
        }
        root.addView(copyButton)

        statusText = TextView(this).apply {
            text = "Preparing local Cobalt store…"
            textSize = 15f
            setPadding(0, dp(28), 0, dp(20))
        }
        root.addView(statusText)

        disconnectButton = Button(this).apply {
            text = "Disconnect"
            isEnabled = false
            setOnClickListener { disconnectCurrent() }
        }
        root.addView(disconnectButton)

        root.addView(TextView(this).apply {
            text = "END-TO-END MESSAGE TEST"
            textSize = 12f
            alpha = 0.60f
            setPadding(0, dp(30), 0, dp(8))
        })

        destinationInput = EditText(this).apply {
            hint = "Recipient number with country code"
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            isEnabled = false
        }
        root.addView(
            destinationInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        messageInput = EditText(this).apply {
            hint = "Test message"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 5
            isEnabled = false
        }
        root.addView(
            messageInput,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        sendButton = Button(this).apply {
            text = "Send test message"
            isEnabled = false
            setOnClickListener { sendTestMessage() }
        }
        root.addView(sendButton)

        incomingText = TextView(this).apply {
            text = "Incoming messages will appear here after linking."
            textSize = 14f
            setTextIsSelectable(true)
            setPadding(0, dp(16), 0, dp(8))
        }
        root.addView(incomingText)

        root.addView(TextView(this).apply {
            text = "The WhatsApp protocol runs inside this APK. No Termux, VPS, or separate web server is required."
            textSize = 13f
            alpha = 0.65f
            setPadding(0, dp(24), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun probeSavedSession() {
        worker.execute {
            try {
                verifyMessageSurface()
                val options = loadLatestOptions()
                runOnUiThread {
                    hasSavedSession = options != null
                    reconnectButton.isEnabled = hasSavedSession
                    if (!hasSavedSession) {
                        setStatus("Ready. Enter your WhatsApp number to create a linked-device pairing code.")
                    } else {
                        setStatus("A saved Cobalt session exists. Reconnect it after pairing, or start a new link.")
                    }
                }
            } catch (error: Throwable) {
                reportError("Checking saved session", error)
            }
        }
    }

    private fun startPairing() {
        val digits = phoneInput.text.toString().filter(Char::isDigit)
        if (digits.length !in 8..15) {
            setStatus("Enter the full international number with country code, without the + sign.")
            return
        }

        setBusy(true)
        pairingCodeText.text = "…"
        copyButton.isEnabled = false
        setStatus("Creating persistent linked-device session…")

        worker.execute {
            try {
                disconnectCurrentInternal()

                val options = createFreshOptions()
                val pairingClass = Class.forName(
                    "com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientVerificationHandler\$Web\$PairingCode",
                    true,
                    classLoader
                )
                val authenticatorClass = Class.forName(
                    "com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientPasskeyAuthenticator",
                    true,
                    classLoader
                )
                val authenticator = Proxy.newProxyInstance(
                    authenticatorClass.classLoader,
                    arrayOf(authenticatorClass)
                ) { proxy, method, args ->
                    when (method.name) {
                        "assertCredential" -> throw UnsupportedOperationException(
                            "WhatsApp requested a passkey integrity challenge. Android passkey relay is not wired yet."
                        )
                        "toString" -> "AndroidDeferredPasskeyAuthenticator"
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
                            runOnUiThread { showPairingCode(code) }
                            null
                        }
                        "passkeyAuthenticator" -> authenticator
                        "toString" -> "AndroidPairingCodeHandler"
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.firstOrNull()
                        else -> null
                    }
                }

                val client = options.javaClass
                    .getMethod("unregistered", java.lang.Long.TYPE, pairingClass)
                    .invoke(options, digits.toLong(), pairingHandler)

                attachSessionListeners(client)
                currentClient = client

                runOnUiThread {
                    disconnectButton.isEnabled = true
                    setStatus("Connecting to WhatsApp and requesting a pairing code…")
                }

                invokeLinkedClient(client, "connect")
            } catch (error: Throwable) {
                reportError("Starting pairing", error)
            } finally {
                runOnUiThread { setBusy(false) }
            }
        }
    }

    private fun reconnectSavedSession() {
        setBusy(true)
        setStatus("Loading saved linked-device credentials…")

        worker.execute {
            try {
                disconnectCurrentInternal()

                val options = loadLatestOptions()
                    ?: error("No saved Cobalt session was found.")

                val registered = options.javaClass
                    .getMethod("registered")
                    .invoke(options) as Optional<*>

                val client = registered.orElse(null)
                    ?: error("The saved session has not completed WhatsApp pairing yet.")

                attachSessionListeners(client)
                currentClient = client

                runOnUiThread {
                    disconnectButton.isEnabled = true
                    setStatus("Reconnecting saved WhatsApp session…")
                }

                invokeLinkedClient(client, "connect")
            } catch (error: Throwable) {
                reportError("Reconnecting saved session", error)
            } finally {
                runOnUiThread { setBusy(false) }
            }
        }
    }

    private fun attachSessionListeners(client: Any) {
        val loggedInClass = Class.forName(
            "com.github.auties00.cobalt.listener.LoggedInListener",
            true,
            classLoader
        )
        val loggedInListener = Proxy.newProxyInstance(
            loggedInClass.classLoader,
            arrayOf(loggedInClass)
        ) { proxy, method, args ->
            when (method.name) {
                "onLoggedIn" -> {
                    runOnUiThread {
                        pairingCodeText.text = "LINKED"
                        copyButton.isEnabled = false
                        hasSavedSession = true
                        reconnectButton.isEnabled = true
                        disconnectButton.isEnabled = true
                        setChatEnabled(true)
                        setStatus("Linked successfully. Credentials are stored locally on this phone.")
                    }
                    null
                }
                "toString" -> "AndroidLoggedInListener"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }

        val newMessageClass = Class.forName(
            "com.github.auties00.cobalt.listener.NewMessageListener",
            true,
            classLoader
        )
        val newMessageListener = Proxy.newProxyInstance(
            newMessageClass.classLoader,
            arrayOf(newMessageClass)
        ) { proxy, method, args ->
            when (method.name) {
                "onNewMessage" -> {
                    val info = args?.getOrNull(1)
                    val rendered = renderMessage(info)
                    runOnUiThread {
                        appendEvent("RECEIVED  $rendered")
                    }
                    null
                }
                "toString" -> "AndroidNewMessageListener"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }

        val whatsappClientClass = Class.forName(
            "com.github.auties00.cobalt.client.WhatsAppClient",
            true,
            classLoader
        )
        whatsappClientClass
            .getMethod("addLoggedInListener", loggedInClass)
            .invoke(client, loggedInListener)
        whatsappClientClass
            .getMethod("addNewMessageListener", newMessageClass)
            .invoke(client, newMessageListener)
    }

    private fun verifyMessageSurface() {
        val jidClass = Class.forName(
            "com.github.auties00.cobalt.wire.core.jid.Jid",
            true,
            classLoader
        )
        val jidProviderClass = Class.forName(
            "com.github.auties00.cobalt.wire.core.jid.JidProvider",
            true,
            classLoader
        )
        val containerClass = Class.forName(
            "com.github.auties00.cobalt.wire.linked.message.LinkedMessageContainer",
            true,
            classLoader
        )
        val newMessageClass = Class.forName(
            "com.github.auties00.cobalt.listener.NewMessageListener",
            true,
            classLoader
        )
        val whatsappClientClass = Class.forName(
            "com.github.auties00.cobalt.client.WhatsAppClient",
            true,
            classLoader
        )

        jidClass.getMethod("of", String::class.java)
        containerClass.getMethod("of", String::class.java)
        whatsappClientClass.getMethod("sendMessage", jidProviderClass, containerClass)
        whatsappClientClass.getMethod("addNewMessageListener", newMessageClass)
        Log.i(tag, "Cobalt send/receive reflection surface verified.")
    }

    private fun sendTestMessage() {
        val client = currentClient
        if (client == null) {
            setStatus("Link or reconnect WhatsApp before sending.")
            return
        }

        val digits = destinationInput.text.toString().filter(Char::isDigit)
        val body = messageInput.text.toString().trim()
        if (digits.length !in 8..15) {
            setStatus("Enter the recipient's full international number with country code.")
            return
        }
        if (body.isBlank()) {
            setStatus("Enter a message to send.")
            return
        }

        sendButton.isEnabled = false
        setStatus("Sending test message…")

        worker.execute {
            try {
                val jidClass = Class.forName(
                    "com.github.auties00.cobalt.wire.core.jid.Jid",
                    true,
                    classLoader
                )
                val jidProviderClass = Class.forName(
                    "com.github.auties00.cobalt.wire.core.jid.JidProvider",
                    true,
                    classLoader
                )
                val containerClass = Class.forName(
                    "com.github.auties00.cobalt.wire.linked.message.LinkedMessageContainer",
                    true,
                    classLoader
                )
                val whatsappClientClass = Class.forName(
                    "com.github.auties00.cobalt.client.WhatsAppClient",
                    true,
                    classLoader
                )

                val recipient = jidClass
                    .getMethod("of", String::class.java)
                    .invoke(null, digits)
                val container = containerClass
                    .getMethod("of", String::class.java)
                    .invoke(null, body)
                val key = whatsappClientClass
                    .getMethod("sendMessage", jidProviderClass, containerClass)
                    .invoke(client, recipient, container)

                runOnUiThread {
                    appendEvent("SENT  $digits: $body")
                    messageInput.text.clear()
                    sendButton.isEnabled = true
                    setStatus("Message handed to Cobalt successfully. Key: ${key ?: "created"}")
                }
            } catch (error: Throwable) {
                runOnUiThread { sendButton.isEnabled = true }
                reportError("Sending test message", error)
            }
        }
    }

    private fun renderMessage(info: Any?): String {
        if (info == null) {
            return "[message payload unavailable]"
        }

        return try {
            val key = info.javaClass.getMethod("key").invoke(info)
            val senderOptional = key.javaClass.getMethod("senderJid").invoke(key) as Optional<*>
            val sender = senderOptional.orElse(null)?.toString() ?: "unknown"
            val fromMe = key.javaClass.getMethod("fromMe").invoke(key) as? Boolean ?: false

            val container = info.javaClass.getMethod("message").invoke(info)
            val content = container.javaClass.getMethod("content").invoke(container)
            val textMethod = content.javaClass.methods.firstOrNull {
                it.name == "text" && it.parameterCount == 0
            }
            val textValue = textMethod?.invoke(content)
            val text = when (textValue) {
                is Optional<*> -> textValue.orElse(null)?.toString()
                null -> null
                else -> textValue.toString()
            }
            val body = text?.takeIf { it.isNotBlank() } ?: "[${content.javaClass.simpleName}]"
            "${if (fromMe) "me" else sender}: $body"
        } catch (error: Throwable) {
            Log.w(tag, "Could not render incoming message", error)
            "[${info.javaClass.simpleName}]"
        }
    }

    private fun appendEvent(value: String) {
        val previous = incomingText.text?.toString().orEmpty()
        incomingText.text = if (previous == "Incoming messages will appear here after linking." || previous.isBlank()) {
            value
        } else {
            "$value\n$previous"
        }
        Log.i(tag, value)
    }

    private fun setChatEnabled(enabled: Boolean) {
        destinationInput.isEnabled = enabled
        messageInput.isEnabled = enabled
        sendButton.isEnabled = enabled
    }

    private fun createFreshOptions(): Any {
        val web = createPersistentWebBuilder()
        return web.javaClass.getMethod("createConnection").invoke(web)
    }

    private fun loadLatestOptions(): Any? {
        val web = createPersistentWebBuilder()
        val result = web.javaClass
            .getMethod("loadLatestConnection")
            .invoke(web) as Optional<*>
        return result.orElse(null)
    }

    private fun createPersistentWebBuilder(): Any {
        val clientClass = Class.forName(
            "com.github.auties00.cobalt.client.WhatsAppClient",
            true,
            classLoader
        )
        val builder = clientClass.getMethod("builder").invoke(null)
        val linked = builder.javaClass.getMethod("linkedApi").invoke(builder)

        val factoryClass = Class.forName(
            "com.github.auties00.cobalt.store.linked.LinkedWhatsAppStoreFactory",
            true,
            classLoader
        )
        val storeDir: Path = filesDir.toPath().resolve("cobalt-client")
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
            classLoader
        )
        return linkedClientClass.getMethod(method).invoke(client)
    }

    private fun disconnectCurrent() {
        worker.execute {
            try {
                disconnectCurrentInternal()
                runOnUiThread {
                    disconnectButton.isEnabled = false
                    setChatEnabled(false)
                    setStatus("Disconnected. Your linked credentials remain stored locally.")
                }
            } catch (error: Throwable) {
                reportError("Disconnecting", error)
            }
        }
    }

    private fun disconnectCurrentInternal() {
        val client = currentClient ?: return
        try {
            invokeLinkedClient(client, "disconnect")
        } finally {
            currentClient = null
        }
    }

    private fun showPairingCode(raw: String) {
        val code = raw.trim()
        pairingCodeText.text = code.chunked(4).joinToString(" ")
        copyButton.isEnabled = code.isNotBlank()
        setStatus("Pairing code ready. In WhatsApp open Linked devices → Link a device → Link with phone number, then enter this code.")
    }

    private fun setBusy(value: Boolean) {
        linkButton.isEnabled = !value
        reconnectButton.isEnabled = !value && hasSavedSession
        phoneInput.isEnabled = !value
    }

    private fun setStatus(value: String) {
        statusText.text = value
        Log.i(tag, value)
    }

    private fun reportError(stage: String, error: Throwable) {
        val root = generateSequence(error) { it.cause }.last()
        Log.e(tag, "$stage failed", error)
        runOnUiThread {
            setBusy(false)
            setStatus("$stage failed: ${root::class.java.simpleName}: ${root.message ?: "no message"}")
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
