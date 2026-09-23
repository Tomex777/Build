package com.tomex.cobaltandroid

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_PICK_IMAGE = 4101
        private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
    }

    private val tag = "Cobalt"
    private val worker = Executors.newFixedThreadPool(3)
    private val connectionWorker = Executors.newSingleThreadExecutor()

    @Volatile private var currentClient: Any? = null
    @Volatile private var hasSavedSession = false
    @Volatile private var selectedChatJid: String? = null
    @Volatile private var selectedChatLabel = ""

    private lateinit var statusText: TextView
    private lateinit var phoneInput: EditText
    private lateinit var pairingCodeText: TextView
    private lateinit var copyButton: Button
    private lateinit var linkButton: Button
    private lateinit var connectionPanel: LinearLayout
    private lateinit var chatListScreen: LinearLayout
    private lateinit var conversationScreen: LinearLayout
    private lateinit var chatListContainer: LinearLayout
    private lateinit var conversationTitle: TextView
    private lateinit var messageFeed: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var attachButton: Button
    private lateinit var sendButton: Button
    private val openedViewOnceIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        probeSavedSession()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(242, 247, 245))
            setPadding(dp(20), dp(28), dp(20), dp(18))
        }

        page.addView(TextView(this).apply {
            text = "Cobalt"
            textSize = 32f
            letterSpacing = -0.025f
            setTextColor(Color.rgb(18, 55, 48))
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD), Typeface.BOLD)
        })
        page.addView(TextView(this).apply {
            text = "Your WhatsApp, in your pocket"
            textSize = 15f
            setTextColor(Color.rgb(92, 111, 105))
            setPadding(0, dp(3), 0, dp(22))
        })

        statusText = TextView(this).apply {
            text = "Checking for a saved WhatsApp link…"
            textSize = 13f
            setTextColor(Color.rgb(47, 78, 68))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(229, 240, 234), 14)
        }
        page.addView(statusText)

        connectionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = rounded(Color.WHITE, 20)
            elevation = dp(2).toFloat()
        }
        connectionPanel.addView(TextView(this).apply {
            text = "Link your WhatsApp"
            textSize = 22f
            setTypeface(Typeface.create("sans-serif", Typeface.BOLD), Typeface.BOLD)
            setTextColor(Color.rgb(24, 43, 38))
        })
        connectionPanel.addView(TextView(this).apply {
            text = "Enter your number, then approve the pairing code in WhatsApp → Linked devices. Your linked session is saved on this phone."
            textSize = 14f
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextColor(Color.rgb(99, 113, 107))
            setPadding(0, dp(7), 0, dp(14))
        })
        phoneInput = EditText(this).apply {
            hint = "Country code and number"
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            textSize = 16f
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(Color.rgb(246, 249, 247), 12)
        }
        connectionPanel.addView(phoneInput, matchWrap())
        linkButton = Button(this).apply {
            text = "Continue to WhatsApp"
            setTextColor(Color.WHITE)
            textSize = 15f
            isAllCaps = false
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = rounded(Color.rgb(22, 105, 82), 14)
            elevation = dp(2).toFloat()
            minHeight = dp(52)
            setOnClickListener { startPairing() }
        }
        connectionPanel.addView(linkButton, matchWrap())

        pairingCodeText = TextView(this).apply {
            text = "Pairing code will appear here"
            textSize = 23f
            gravity = Gravity.CENTER
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.rgb(23, 69, 56))
            setPadding(dp(12), dp(20), dp(12), dp(20))
            setTypeface(Typeface.create("sans-serif-monospace", Typeface.BOLD), Typeface.BOLD)
            background = rounded(Color.rgb(239, 247, 242), 14)
            visibility = View.GONE
        }
        connectionPanel.addView(pairingCodeText, matchWrap())
        copyButton = Button(this).apply {
            text = "Copy pairing code"
            isEnabled = false
            visibility = View.GONE
            setOnClickListener {
                val code = pairingCodeText.text.toString().replace(" ", "")
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("WhatsApp pairing code", code))
                setStatus("Code copied. Open WhatsApp → Linked devices → Link with phone number.")
            }
        }
        connectionPanel.addView(copyButton, matchWrap())
        page.addView(connectionPanel, matchWrap())

        chatListScreen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(16), 0, 0)
        }
        val chatHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        chatHeader.addView(TextView(this).apply {
            text = "Chats"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(20, 30, 38))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        chatHeader.addView(Button(this).apply {
            text = "New chat"
            setOnClickListener { promptNewChat() }
        })
        chatHeader.addView(Button(this).apply {
            text = "Disconnect"
            setOnClickListener { disconnectCurrent() }
        })
        chatListScreen.addView(chatHeader, matchWrap())

        val chatScroll = ScrollView(this)
        chatListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(12))
        }
        chatScroll.addView(chatListContainer)
        chatListScreen.addView(
            chatScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        page.addView(
            chatListScreen,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        conversationScreen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(12), 0, 0)
        }
        val conversationHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        conversationHeader.addView(Button(this).apply {
            text = "‹ Chats"
            setOnClickListener { showChatList() }
        })
        conversationTitle = TextView(this).apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(20, 30, 38))
            setPadding(dp(8), 0, 0, 0)
        }
        conversationHeader.addView(
            conversationTitle,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        conversationScreen.addView(conversationHeader, matchWrap())

        val messageScroll = ScrollView(this)
        messageFeed = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(8), dp(2), dp(10))
        }
        messageScroll.addView(messageFeed)
        conversationScreen.addView(
            messageScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        attachButton = Button(this).apply {
            text = "+"
            contentDescription = "Send photo"
            isEnabled = false
            setOnClickListener { pickImage() }
        }
        composer.addView(attachButton)
        messageInput = EditText(this).apply {
            hint = "Message"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 1
            maxLines = 4
            isEnabled = false
        }
        composer.addView(
            messageInput,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        sendButton = Button(this).apply {
            text = "Send"
            isEnabled = false
            setOnClickListener { sendMessage() }
        }
        composer.addView(sendButton)
        conversationScreen.addView(composer, matchWrap())
        page.addView(
            conversationScreen,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(page)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun probeSavedSession() {
        worker.execute {
            try {
                verifyMessageSurface()
                val options = loadLatestOptions()
                val registered = options?.let { registeredClient(it) }
                if (registered != null) {
                    currentClient = registered
                    attachSessionListeners(registered)
                    runOnUiThread { setStatus("Saved WhatsApp link found. Reconnecting…") }
                    connectionWorker.execute {
                        try {
                            invokeLinkedClient(registered, "connect")
                        } catch (error: Throwable) {
                            reportError("Reconnecting WhatsApp", error)
                        }
                    }
                } else {
                    runOnUiThread {
                        hasSavedSession = options != null
                        showPairing()
                        setStatus(if (options == null) "Ready to link your WhatsApp account." else "A saved link needs pairing to finish. Enter your number to continue.")
                    }
                }
            } catch (error: Throwable) {
                reportError("Opening Cobalt", error)
            }
        }
    }

    private fun registeredClient(options: Any): Any? {
        val result = options.javaClass.getMethod("registered").invoke(options) as Optional<*>
        return result.orElse(null)
    }

    private fun startPairing() {
        val digits = phoneInput.text.toString().filter(Char::isDigit)
        if (digits.length !in 8..15) {
            setStatus("Enter the full international phone number, including its country code.")
            return
        }

        setBusy(true)
        pairingCodeText.visibility = View.VISIBLE
        pairingCodeText.text = "Requesting pairing code…"
        copyButton.isEnabled = false
        copyButton.visibility = View.VISIBLE
        setStatus("Connecting to WhatsApp…")

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
                val authenticator = java.lang.reflect.Proxy.newProxyInstance(
                    authenticatorClass.classLoader,
                    arrayOf(authenticatorClass)
                ) { proxy, method, args ->
                    when (method.name) {
                        "assertCredential" -> throw UnsupportedOperationException(
                            "WhatsApp requested a passkey challenge that this build does not yet support."
                        )
                        "toString" -> "CobaltPasskeyAuthenticator"
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.firstOrNull()
                        else -> null
                    }
                }
                val pairingHandler = java.lang.reflect.Proxy.newProxyInstance(
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
                        "toString" -> "CobaltPairingCodeHandler"
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
                runOnUiThread { setStatus("Requesting your WhatsApp linked-device code…") }
                connectionWorker.execute {
                    try {
                        invokeLinkedClient(client, "connect")
                    } catch (error: Throwable) {
                        reportError("Linking WhatsApp", error)
                    }
                }
            } catch (error: Throwable) {
                reportError("Linking WhatsApp", error)
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
        val loggedInListener = java.lang.reflect.Proxy.newProxyInstance(
            loggedInClass.classLoader,
            arrayOf(loggedInClass)
        ) { proxy, method, args ->
            when (method.name) {
                "onLoggedIn" -> {
                    runOnUiThread {
                        hasSavedSession = true
                        setBusy(false)
                        showWorkspace()
                        setStatus("WhatsApp connected. Your chats are ready.")
                        refreshChats()
                    }
                    null
                }
                "toString" -> "CobaltLoggedInListener"
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
        val newMessageListener = java.lang.reflect.Proxy.newProxyInstance(
            newMessageClass.classLoader,
            arrayOf(newMessageClass)
        ) { proxy, method, args ->
            when (method.name) {
                "onNewMessage" -> {
                    val info = args?.getOrNull(1)
                    val jid = info?.let { messageChatJid(it) }
                    runOnUiThread {
                        if (jid != null && jid == selectedChatJid) refreshConversation()
                        refreshChats()
                    }
                    null
                }
                "toString" -> "CobaltNewMessageListener"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }

        val statusListenerClass = Class.forName(
            "com.github.auties00.cobalt.listener.MessageStatusListener",
            true,
            classLoader
        )
        val statusListener = java.lang.reflect.Proxy.newProxyInstance(
            statusListenerClass.classLoader,
            arrayOf(statusListenerClass)
        ) { proxy, method, args ->
            when (method.name) {
                "onMessageStatus" -> {
                    runOnUiThread {
                        refreshChats()
                        refreshConversation()
                    }
                    null
                }
                "toString" -> "CobaltMessageStatusListener"
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
        whatsappClientClass.getMethod("addLoggedInListener", loggedInClass)
            .invoke(client, loggedInListener)
        whatsappClientClass.getMethod("addNewMessageListener", newMessageClass)
            .invoke(client, newMessageListener)
        whatsappClientClass.getMethod("addMessageStatusListener", statusListenerClass)
            .invoke(client, statusListener)
    }

    private fun verifyMessageSurface() {
        val jidClass = Class.forName("com.github.auties00.cobalt.wire.core.jid.Jid", true, classLoader)
        val jidProviderClass = Class.forName("com.github.auties00.cobalt.wire.core.jid.JidProvider", true, classLoader)
        val containerClass = Class.forName("com.github.auties00.cobalt.wire.linked.message.LinkedMessageContainer", true, classLoader)
        val newMessageClass = Class.forName("com.github.auties00.cobalt.listener.NewMessageListener", true, classLoader)
        val whatsappClientClass = Class.forName("com.github.auties00.cobalt.client.WhatsAppClient", true, classLoader)
        jidClass.getMethod("of", String::class.java)
        containerClass.getMethod("of", String::class.java)
        whatsappClientClass.getMethod("sendMessage", jidProviderClass, containerClass)
        whatsappClientClass.getMethod("addNewMessageListener", newMessageClass)
        Log.i(tag, "Cobalt linked-device messaging surface is ready.")
    }

    private fun refreshChats() {
        val client = currentClient ?: return
        worker.execute {
            try {
                val store = client.javaClass.getMethod("store").invoke(client)
                val chatStore = store.javaClass.getMethod("chatStore").invoke(store)
                val chats = chatStore.javaClass.getMethod("chats").invoke(chatStore) as? Collection<*>
                    ?: emptyList<Any>()
                val models = chats.filterNotNull().mapNotNull { chat ->
                    val jid = chatJid(chat) ?: return@mapNotNull null
                    Triple(chat, jid, chatLabel(chat, jid))
                }.sortedByDescending { chatTimestamp(it.first) }

                runOnUiThread {
                    chatListContainer.removeAllViews()
                    if (models.isEmpty()) {
                        chatListContainer.addView(TextView(this).apply {
                            text = "Your WhatsApp chats will appear here after history sync or when a message arrives."
                            textSize = 15f
                            setTextColor(Color.rgb(84, 96, 105))
                            setPadding(dp(12), dp(18), dp(12), dp(18))
                        })
                    } else {
                        models.forEach { (chat, jid, label) ->
                            val preview = chatPreview(chat)
                            val item = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(dp(14), dp(12), dp(14), dp(12))
                                setBackgroundColor(Color.WHITE)
                                setOnClickListener { openConversation(jid, label) }
                            }
                            item.addView(TextView(this).apply {
                                text = label
                                textSize = 17f
                                setTypeface(typeface, Typeface.BOLD)
                                setTextColor(Color.rgb(26, 39, 47))
                            })
                            item.addView(TextView(this).apply {
                                text = preview
                                textSize = 14f
                                setTextColor(Color.rgb(93, 104, 112))
                                maxLines = 2
                            })
                            val lp = matchWrap()
                            lp.bottomMargin = dp(6)
                            chatListContainer.addView(item, lp)
                        }
                    }
                }
            } catch (error: Throwable) {
                Log.w(tag, "Could not load saved chats", error)
            }
        }
    }

    private fun chatJid(chat: Any): String? {
        val value = firstValue(chat, "jid", "toJid")
        return value?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun chatLabel(chat: Any, jid: String): String {
        return firstValue(chat, "displayName", "name", "subject")?.toString()
            ?.takeIf { it.isNotBlank() } ?: jid
    }

    private fun chatTimestamp(chat: Any): Long {
        val value = firstValue(chat, "lastMsgTimestamp", "conversationTimestamp", "timestamp")
        return when (value) {
            is java.time.Instant -> value.toEpochMilli()
            is Number -> value.toLong()
            else -> 0L
        }
    }

    private fun chatPreview(chat: Any): String {
        val newest = firstValue(chat, "newestMessage")
        return if (newest != null) renderMessage(newest) else "Open conversation"
    }

    private fun openConversation(jid: String, label: String) {
        selectedChatJid = jid
        selectedChatLabel = label
        conversationTitle.text = label
        chatListScreen.visibility = View.GONE
        conversationScreen.visibility = View.VISIBLE
        messageInput.isEnabled = true
        attachButton.isEnabled = true
        sendButton.isEnabled = true
        markChatRead(jid)
        refreshConversation()
    }

    private fun markChatRead(jid: String) {
        val client = currentClient ?: return
        worker.execute {
            try {
                val jidClass = Class.forName("com.github.auties00.cobalt.wire.core.jid.Jid", true, classLoader)
                val jidProviderClass = Class.forName("com.github.auties00.cobalt.wire.core.jid.JidProvider", true, classLoader)
                val clientClass = Class.forName("com.github.auties00.cobalt.client.WhatsAppClient", true, classLoader)
                val chat = jidClass.getMethod("of", String::class.java).invoke(null, jid)
                clientClass.getMethod("markChatAsRead", jidProviderClass).invoke(client, chat)
            } catch (error: Throwable) {
                Log.w(tag, "Could not mark chat as read", error)
            }
        }
    }

    private fun showReactionMenu(info: Any) {
        val key = firstValue(info, "key") ?: return
        val reactions = arrayOf("👍", "❤️", "😂", "😮", "😢", "🙏", "Remove reaction")
        AlertDialog.Builder(this)
            .setTitle("React to message")
            .setItems(reactions) { _, index ->
                worker.execute {
                    try {
                        val keyClass = Class.forName("com.github.auties00.cobalt.wire.core.message.MessageKey", true, classLoader)
                        val clientClass = Class.forName("com.github.auties00.cobalt.client.WhatsAppClient", true, classLoader)
                        if (index == reactions.lastIndex) {
                            clientClass.getMethod("removeReaction", keyClass).invoke(currentClient, key)
                        } else {
                            clientClass.getMethod("addReaction", keyClass, String::class.java)
                                .invoke(currentClient, key, reactions[index])
                        }
                        runOnUiThread { setStatus(if (index == reactions.lastIndex) "Reaction removed." else "Reaction sent.") }
                    } catch (error: Throwable) {
                        Log.w(tag, "Could not react to message", error)
                        runOnUiThread { setStatus("Could not send reaction: ${error.cause?.message ?: error.message ?: "unknown error"}") }
                    }
                }
            }
            .show()
    }

    private fun showChatList() {
        selectedChatJid = null
        conversationScreen.visibility = View.GONE
        chatListScreen.visibility = View.VISIBLE
        refreshChats()
    }

    private fun refreshConversation() {
        val jid = selectedChatJid ?: return
        val client = currentClient ?: return
        worker.execute {
            try {
                val store = client.javaClass.getMethod("store").invoke(client)
                val chatStore = store.javaClass.getMethod("chatStore").invoke(store)
                val jidClass = Class.forName("com.github.auties00.cobalt.wire.core.jid.Jid", true, classLoader)
                val provider = jidClass.getMethod("of", String::class.java).invoke(null, jid)
                val chat = chatStore.javaClass.getMethod(
                    "findChatByJid",
                    Class.forName("com.github.auties00.cobalt.wire.core.jid.JidProvider", true, classLoader)
                ).invoke(chatStore, provider) as Optional<*>
                val model = chat.orElse(null)
                val messages = model?.let { firstValue(it, "messages") as? Collection<*> }.orEmpty()
                val sorted = messages.filterNotNull().sortedBy { messageTimestamp(it) }
                runOnUiThread {
                    if (selectedChatJid != jid) return@runOnUiThread
                    messageFeed.removeAllViews()
                    if (sorted.isEmpty()) {
                        messageFeed.addView(TextView(this).apply {
                            text = "No messages loaded for this chat yet. Send a message to start here."
                            textSize = 14f
                            setTextColor(Color.rgb(88, 100, 108))
                            setPadding(dp(10), dp(12), dp(10), dp(12))
                        })
                    } else {
                        sorted.forEach { info ->
                            val row = LinearLayout(this).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = if (isFromMe(info)) Gravity.END else Gravity.START
                                setPadding(dp(4), dp(3), dp(4), dp(3))
                            }
                            val bubble = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(dp(8), dp(7), dp(8), dp(7))
                                setBackgroundColor(if (isFromMe(info)) Color.rgb(214, 242, 223) else Color.WHITE)
                                setOnLongClickListener {
                                    showReactionMenu(info)
                                    true
                                }
                            }
                            val container = firstValue(info, "message")
                            val content = container?.let { firstValue(it, "content") }
                            val thumbnail = content?.let { firstValue(it, "jpegThumbnail") as? ByteArray }
                            val bitmap = thumbnail?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                            if (bitmap != null) {
                                bubble.addView(ImageView(this).apply {
                                    setImageBitmap(bitmap)
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    adjustViewBounds = true
                                    maxWidth = dp(260)
                                    maxHeight = dp(260)
                                })
                            }
                            val delivery = if (isFromMe(info)) firstValue(info, "status")?.toString()?.lowercase() else null
                            bubble.addView(TextView(this).apply {
                                text = if (delivery.isNullOrBlank()) renderMessage(info) else renderMessage(info) + "\n" + delivery
                                textSize = 15f
                                setTextColor(Color.rgb(26, 39, 47))
                            })
                            row.addView(bubble, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.88f))
                            messageFeed.addView(row, matchWrap())
                        }
                    }
                }
            } catch (error: Throwable) {
                Log.w(tag, "Could not load conversation", error)
                runOnUiThread { setStatus("Could not load this conversation: ${error.message ?: "unknown error"}") }
            }
        }
    }

    private fun promptNewChat() {
        AlertDialog.Builder(this)
            .setTitle("New conversation")
            .setItems(arrayOf("Direct chat", "New group")) { _, index ->
                if (index == 0) promptDirectChat() else promptNewGroup()
            }
            .show()
    }

    private fun promptDirectChat() {
        val input = EditText(this).apply {
            hint = "International phone number"
            inputType = InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("New chat")
            .setMessage("Enter a WhatsApp number with country code.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open") { _, _ ->
                val digits = input.text.toString().filter(Char::isDigit)
                if (digits.length !in 8..15) {
                    setStatus("Enter a valid international phone number.")
                    return@setPositiveButton
                }
                val jid = try {
                    val jidClass = Class.forName("com.github.auties00.cobalt.wire.core.jid.Jid", true, classLoader)
                    jidClass.getMethod("of", String::class.java).invoke(null, digits).toString()
                } catch (error: Throwable) {
                    reportError("Opening chat", error)
                    return@setPositiveButton
                }
                openConversation(jid, digits)
            }
            .show()
    }

    private fun promptNewGroup() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val subjectInput = EditText(this).apply {
            hint = "Group name"
            setSingleLine(true)
        }
        val participantsInput = EditText(this).apply {
            hint = "Participant numbers with country codes, separated by commas"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 4
        }
        form.addView(subjectInput, matchWrap())
        form.addView(participantsInput, matchWrap())

        AlertDialog.Builder(this)
            .setTitle("Create group")
            .setMessage("Add participants using their international numbers.")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val subject = subjectInput.text.toString().trim()
                val participants = participantsInput.text.toString()
                    .split(Regex("[,;\\s]+"))
                    .map { it.filter(Char::isDigit) }
                    .filter { it.length in 8..15 }
                    .distinct()
                if (subject.isBlank() || participants.isEmpty()) {
                    setStatus("Enter a group name and at least one valid international number.")
                    return@setPositiveButton
                }
                createGroup(subject, participants)
            }
            .show()
    }

    private fun createGroup(subject: String, participantNumbers: List<String>) {
        val client = currentClient
        if (client == null) {
            setStatus("Connect WhatsApp before creating a group.")
            return
        }
        setStatus("Creating group…")
        worker.execute {
            try {
                val jidClass = Class.forName("com.github.auties00.cobalt.wire.core.jid.Jid", true, classLoader)
                val linkedClientClass = Class.forName("com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient", true, classLoader)
                val participants = participantNumbers.map { number ->
                    jidClass.getMethod("of", String::class.java).invoke(null, number)
                }
                val group = linkedClientClass
                    .getMethod("createGroup", String::class.java, java.util.Collection::class.java)
                    .invoke(client, subject, participants)
                val groupJid = group?.let { firstValue(it, "jid") }?.toString()
                runOnUiThread {
                    refreshChats()
                    if (groupJid.isNullOrBlank()) {
                        setStatus("Group created. Refreshing your chat list…")
                    } else {
                        openConversation(groupJid, subject)
                        setStatus("Group created.")
                    }
                }
            } catch (error: Throwable) {
                Log.e(tag, "Could not create group", error)
                runOnUiThread { setStatus("Could not create group: ${error.cause?.message ?: error.message ?: "unknown error"}") }
            }
        }
    }

    private fun pickImage() {
        if (selectedChatJid.isNullOrBlank()) {
            setStatus("Open a chat before attaching a photo.")
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK) {
            data?.data?.let(::sendImage)
        }
    }

    private fun sendImage(uri: Uri) {
        val client = currentClient
        val jidValue = selectedChatJid
        if (client == null || jidValue.isNullOrBlank()) {
            setStatus("Open a chat before sending a photo.")
            return
        }
        val caption = messageInput.text.toString().trim()
        sendButton.isEnabled = false
        attachButton.isEnabled = false
        setStatus("Preparing photo…")
        worker.execute {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_IMAGE_BYTES) throw IllegalArgumentException("Choose an image smaller than 20 MB.")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: throw IllegalArgumentException("Could not read the selected photo.")

                val builderClass = Class.forName(
                    "com.github.auties00.cobalt.wire.linked.message.media.ImageMessageBuilder",
                    true,
                    classLoader
                )
                val builder = builderClass.getConstructor().newInstance()
                val withImage = builderClass.getMethod("imageData", ByteArray::class.java).invoke(builder, bytes) ?: builder
                val withCaption = if (caption.isBlank()) withImage else
                    builderClass.getMethod("caption", String::class.java).invoke(withImage, caption) ?: withImage
                val image = builderClass.getMethod("build").invoke(withCaption)

                val jidClass = Class.forName("com.github.auties00.cobalt.wire.core.jid.Jid", true, classLoader)
                val jidProviderClass = Class.forName("com.github.auties00.cobalt.wire.core.jid.JidProvider", true, classLoader)
                val containerClass = Class.forName("com.github.auties00.cobalt.wire.linked.message.LinkedMessageContainer", true, classLoader)
                val containerFactory = containerClass.methods.firstOrNull {
                    it.name == "of" && it.parameterCount == 1 && it.parameterTypes[0].isAssignableFrom(image.javaClass)
                } ?: throw NoSuchMethodException("Cobalt image message factory is unavailable.")
                val messageContainer = containerFactory.invoke(null, image)
                val recipient = jidClass.getMethod("of", String::class.java).invoke(null, jidValue)
                val clientClass = Class.forName("com.github.auties00.cobalt.client.WhatsAppClient", true, classLoader)
                clientClass.getMethod("sendMessage", jidProviderClass, containerClass)
                    .invoke(client, recipient, messageContainer)
                runOnUiThread {
                    messageInput.text.clear()
                    sendButton.isEnabled = true
                    attachButton.isEnabled = true
                    refreshConversation()
                    refreshChats()
                    setStatus("Photo sent.")
                }
            } catch (error: Throwable) {
                Log.e(tag, "Could not send photo", error)
                runOnUiThread {
                    sendButton.isEnabled = true
                    attachButton.isEnabled = true
                    setStatus("Could not send photo: " + (error.cause?.message ?: error.message ?: "unknown error"))
                }
            }
        }
    }

    private fun sendMessage() {
        val client = currentClient
        val jidValue = selectedChatJid
        val body = messageInput.text.toString().trim()
        if (client == null || jidValue.isNullOrBlank()) {
            setStatus("Open a chat before sending.")
            return
        }
        if (body.isBlank()) return

        sendButton.isEnabled = false
        worker.execute {
            try {
                val jidClass = Class.forName("com.github.auties00.cobalt.wire.core.jid.Jid", true, classLoader)
                val jidProviderClass = Class.forName("com.github.auties00.cobalt.wire.core.jid.JidProvider", true, classLoader)
                val containerClass = Class.forName("com.github.auties00.cobalt.wire.linked.message.LinkedMessageContainer", true, classLoader)
                val whatsappClientClass = Class.forName("com.github.auties00.cobalt.client.WhatsAppClient", true, classLoader)
                val recipient = jidClass.getMethod("of", String::class.java).invoke(null, jidValue)
                val container = containerClass.getMethod("of", String::class.java).invoke(null, body)
                whatsappClientClass.getMethod("sendMessage", jidProviderClass, containerClass)
                    .invoke(client, recipient, container)
                runOnUiThread {
                    messageInput.text.clear()
                    sendButton.isEnabled = true
                    refreshConversation()
                    refreshChats()
                    setStatus("Message sent.")
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    sendButton.isEnabled = true
                    setStatus("Message could not be sent: ${error.message ?: "unknown error"}")
                }
            }
        }
    }

    private fun messageChatJid(info: Any): String? {
        val key = firstValue(info, "key") ?: return null
        return firstValue(key, "parentJid", "senderJid")?.toString()
    }

    private fun messageTimestamp(info: Any): Long {
        val value = firstValue(info, "timestamp")
        return when (value) {
            is Optional<*> -> (value.orElse(null) as? java.time.Instant)?.toEpochMilli() ?: 0L
            is java.time.Instant -> value.toEpochMilli()
            is Number -> value.toLong()
            else -> 0L
        }
    }

    private fun isFromMe(info: Any): Boolean {
        val key = firstValue(info, "key") ?: return false
        return firstValue(key, "fromMe") as? Boolean ?: false
    }

    private fun renderMessage(info: Any): String {
        return try {
            val container = firstValue(info, "message") ?: return "[message unavailable]"
            val content = firstValue(container, "content") ?: return "[message]"
            val textValue = firstValue(content, "text")
            if (content.javaClass.simpleName.contains("ImageMessage")) {
                val caption = firstValue(content, "caption")?.toString()?.takeIf { it.isNotBlank() }
                return if (caption == null) "[Photo]" else "[Photo] $caption"
            }
            val body = when (textValue) {
                is Optional<*> -> textValue.orElse(null)?.toString()
                null -> null
                else -> textValue.toString()
            }?.takeIf { it.isNotBlank() } ?: "[${content.javaClass.simpleName}]"
            body
        } catch (_: Throwable) {
            "[message]"
        }
    }

    private fun firstValue(target: Any, vararg methods: String): Any? {
        methods.forEach { name ->
            try {
                val result = target.javaClass.methods.firstOrNull {
                    it.name == name && it.parameterCount == 0
                }?.invoke(target) ?: return@forEach
                return if (result is Optional<*>) result.orElse(null) else result
            } catch (_: Throwable) {
                // Try the next public accessor name.
            }
        }
        return null
    }

    private fun showPairing() {
        connectionPanel.visibility = View.VISIBLE
        chatListScreen.visibility = View.GONE
        conversationScreen.visibility = View.GONE
    }

    private fun showWorkspace() {
        connectionPanel.visibility = View.GONE
        conversationScreen.visibility = View.GONE
        chatListScreen.visibility = View.VISIBLE
        refreshChats()
    }

    private fun showPairingCode(raw: String) {
        val code = raw.trim()
        pairingCodeText.text = code.chunked(4).joinToString(" ")
        pairingCodeText.visibility = View.VISIBLE
        copyButton.visibility = View.VISIBLE
        copyButton.isEnabled = code.isNotBlank()
        setStatus("Pairing code ready. Approve it in WhatsApp → Linked devices → Link with phone number.")
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
        val clientClass = Class.forName("com.github.auties00.cobalt.client.WhatsAppClient", true, classLoader)
        val builder = clientClass.getMethod("builder").invoke(null)
        val linked = builder.javaClass.getMethod("linkedApi").invoke(builder)
        val factoryClass = Class.forName("com.github.auties00.cobalt.store.linked.LinkedWhatsAppStoreFactory", true, classLoader)
        val storeDir: Path = filesDir.toPath().resolve("cobalt-client")
        val factory = factoryClass.getMethod("persistent", Path::class.java).invoke(null, storeDir)
        return linked.javaClass.getMethod("webClient", factoryClass).invoke(linked, factory)
    }

    private fun invokeLinkedClient(client: Any, method: String): Any? {
        val linkedClientClass = Class.forName("com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient", true, classLoader)
        return linkedClientClass.getMethod(method).invoke(client)
    }

    private fun disconnectCurrent() {
        worker.execute {
            try {
                disconnectCurrentInternal()
                runOnUiThread {
                    currentClient = null
                    setBusy(false)
                    showPairing()
                    setStatus("Disconnected. Your linked session remains saved on this phone.")
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

    private fun setBusy(value: Boolean) {
        linkButton.isEnabled = !value
        phoneInput.isEnabled = !value
    }

    private fun setStatus(value: String) {
        statusText.text = value
    }

    private fun reportError(stage: String, error: Throwable) {
        val root = generateSequence(error) { it.cause }.last()
        Log.e(tag, "$stage failed", error)
        runOnUiThread {
            setBusy(false)
            pairingCodeText.visibility = View.GONE
            copyButton.visibility = View.GONE
            showPairing()
            setStatus(when {
                root is java.net.UnknownHostException -> "Couldn’t connect to WhatsApp. Check your internet connection and try again."
                root is java.net.SocketTimeoutException -> "WhatsApp took too long to respond. Try again."
                else -> "Couldn’t link WhatsApp. Please check your number and internet connection, then try again."
            })
        }
    }

    private fun onConnected() {
        hasSavedSession = true
        runOnUiThread {
            pairingCodeText.visibility = View.GONE
            copyButton.visibility = View.GONE
            showWorkspace()
            setStatus("WhatsApp connected. Your chats are ready.")
        }
    }

    override fun onDestroy() {
        try {
            disconnectCurrentInternal()
        } catch (error: Throwable) {
            Log.w(tag, "Disconnect during close failed", error)
        }
        connectionWorker.shutdownNow()
        worker.shutdownNow()
        super.onDestroy()
    }
}
