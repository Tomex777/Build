package com.example.whatsapp.extensions.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.example.whatsapp.extensions.messages.ExtensionCardTemplate
import com.example.whatsapp.extensions.messages.NightExtensionMessageActionRegistry
import com.example.whatsapp.extensions.messages.NightExtensionMessageTypeDefinition
import com.example.whatsapp.extensions.messages.NightExtensionMessageTypeRegistry
import com.example.whatsapp.extensions.tools.NightExtensionToolDefinition
import com.example.whatsapp.extensions.tools.NightExtensionToolRegistry
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Runtime bridge for independently installed Night extension APKs.
 *
 * Extension APKs expose an exported bound service with [ACTION_EXTENSION_SERVICE].
 * Night discovers those services, asks each service for a JSON descriptor, then
 * exposes the declared tools/message types through the same registries used by
 * in-process extensions.
 *
 * IPC deliberately carries JSON-only payloads. Night never sends provider keys,
 * full conversation history, or another extension's state to an extension.
 */
class NightExternalExtensionManager private constructor(
    context: Context,
) {
    private val app = context.applicationContext
    private val extensionServices = ConcurrentHashMap<String, ComponentName>()

    suspend fun refreshInstalledExtensions(): List<String> {
        val discovered = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            app.packageManager.queryIntentServices(
                Intent(ACTION_EXTENSION_SERVICE),
                android.content.pm.PackageManager.GET_META_DATA,
            )
                .mapNotNull { resolveInfo ->
                    resolveInfo.serviceInfo?.let {
                        ComponentName(it.packageName, it.name)
                    }
                }
                .distinct()
                .sortedBy { it.flattenToShortString() }
        }

        val active = linkedSetOf<String>()
        discovered.forEach { component ->
            runCatching {
                val descriptor = request(
                    component = component,
                    what = MSG_DESCRIBE,
                    payload = Bundle(),
                )
                registerDescriptor(component, descriptor)
            }.onSuccess { extensionId ->
                active += extensionId
            }
        }

        extensionServices.keys
            .filterNot(active::contains)
            .forEach(::unregister)

        return active.toList()
    }

    fun unregister(extensionId: String) {
        extensionServices.remove(extensionId)
        NightExtensionToolRegistry.unregisterExtension(extensionId)
        NightExtensionMessageTypeRegistry.unregisterExtension(extensionId)
        NightExtensionMessageActionRegistry.unregister(extensionId)
    }

    private fun registerDescriptor(
        component: ComponentName,
        descriptor: JSONObject,
    ): String {
        val schemaVersion = descriptor.optInt("schemaVersion", 1)
        require(schemaVersion in 1..SUPPORTED_SCHEMA_VERSION) {
            "Unsupported Night extension schema version: " + schemaVersion
        }

        val extensionId = descriptor.optString("extensionId").trim().lowercase()
        require(EXTENSION_ID.matches(extensionId)) {
            "Invalid Night extension id."
        }

        unregister(extensionId)
        extensionServices[extensionId] = component

        val seenTools = mutableSetOf<String>()
        descriptor.optJSONArray("tools")?.let { tools ->
            for (index in 0 until minOf(tools.length(), MAX_TOOLS)) {
                val item = tools.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                val description = item.optString("description").trim()
                if (name.isBlank() || description.isBlank()) continue

                val definition = NightExtensionToolDefinition(
                    extensionId = extensionId,
                    name = name,
                    description = description,
                    parameters = normalizeParameters(
                        item.optJSONObject("parameters"),
                    ),
                )
                require(seenTools.add(definition.qualifiedName)) {
                    "Extension declares colliding tool names."
                }

                NightExtensionToolRegistry.register(definition) {
                        chatId,
                        arguments,
                    ->
                    executeTool(
                        extensionId = extensionId,
                        chatId = chatId,
                        toolName = name,
                        arguments = arguments,
                    )
                }
            }
        }

        val messageDefinitions =
            buildList {
                descriptor.optJSONArray("messageTypes")?.let { types ->
                    for (index in 0 until minOf(types.length(), MAX_MESSAGE_TYPES)) {
                        val item = types.optJSONObject(index) ?: continue
                        val messageType = item.optString("messageType").trim()
                        val description = item.optString("description").trim()
                        if (messageType.isBlank() || description.isBlank()) continue

                        add(
                            NightExtensionMessageTypeDefinition(
                                extensionId = extensionId,
                                messageType = messageType,
                                template = ExtensionCardTemplate.fromWireName(
                                    item.optString("template"),
                                ),
                                description = description,
                                whenToUse = item.optString("whenToUse").trim(),
                            )
                        )
                    }
                }
            }
        NightExtensionMessageTypeRegistry.registerAll(messageDefinitions)

        NightExtensionMessageActionRegistry.register(extensionId) {
                chatId,
                messageId,
                messageType,
                actionId,
                payload,
            ->
            executeAction(
                extensionId = extensionId,
                chatId = chatId,
                messageId = messageId,
                messageType = messageType,
                actionId = actionId,
                payload = payload,
            )
        }

        return extensionId
    }

    private suspend fun executeTool(
        extensionId: String,
        chatId: String,
        toolName: String,
        arguments: JSONObject,
    ): JSONObject {
        val component = extensionServices[extensionId]
            ?: error("Night extension is no longer installed: " + extensionId)

        return request(
            component = component,
            what = MSG_EXECUTE_TOOL,
            payload = Bundle().apply {
                putString(KEY_EXTENSION_ID, extensionId)
                putString(KEY_CHAT_ID, chatId)
                putString(KEY_TOOL_NAME, toolName)
                putString(KEY_ARGUMENTS_JSON, arguments.toString())
            },
        )
    }

    private suspend fun executeAction(
        extensionId: String,
        chatId: String,
        messageId: String,
        messageType: String,
        actionId: String,
        payload: JSONObject,
    ): JSONObject {
        val component = extensionServices[extensionId]
            ?: error("Night extension is no longer installed: " + extensionId)

        return request(
            component = component,
            what = MSG_EXECUTE_ACTION,
            payload = Bundle().apply {
                putString(KEY_EXTENSION_ID, extensionId)
                putString(KEY_CHAT_ID, chatId)
                putString(KEY_MESSAGE_ID, messageId)
                putString(KEY_MESSAGE_TYPE, messageType)
                putString(KEY_ACTION_ID, actionId)
                putString(KEY_PAYLOAD_JSON, payload.toString())
            },
        )
    }

    private suspend fun request(
        component: ComponentName,
        what: Int,
        payload: Bundle,
    ): JSONObject = withTimeout(REQUEST_TIMEOUT_MS) {
        val bound = bind(component)
        try {
            awaitReply(
                remote = bound.messenger,
                what = what,
                payload = payload,
            )
        } finally {
            withContext(Dispatchers.Main.immediate) {
                runCatching { app.unbindService(bound.connection) }
            }
        }
    }

    private suspend fun bind(component: ComponentName): BoundService =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(
                        name: ComponentName,
                        service: IBinder,
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(
                                BoundService(
                                    messenger = Messenger(service),
                                    connection = this,
                                )
                            )
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        // A later request will bind again.
                    }
                }

                val intent =
                    Intent(ACTION_EXTENSION_SERVICE)
                        .setComponent(component)
                val bound = runCatching {
                    app.bindService(
                        intent,
                        connection,
                        Context.BIND_AUTO_CREATE,
                    )
                }.getOrDefault(false)

                if (!bound && continuation.isActive) {
                    continuation.resumeWithException(
                        IllegalStateException(
                            "Could not bind Night extension " +
                                component.flattenToShortString(),
                        )
                    )
                }

                continuation.invokeOnCancellation {
                    if (bound) {
                        runCatching { app.unbindService(connection) }
                    }
                }
            }
        }

    private suspend fun awaitReply(
        remote: Messenger,
        what: Int,
        payload: Bundle,
    ): JSONObject =
        suspendCancellableCoroutine { continuation ->
            val requestId = UUID.randomUUID().toString()
            val handler = Handler(Looper.getMainLooper()) { reply ->
                if (
                    reply.what != MSG_RESULT ||
                    reply.data.getString(KEY_REQUEST_ID) != requestId
                ) {
                    return@Handler false
                }

                if (!continuation.isActive) {
                    return@Handler true
                }

                if (!reply.data.getBoolean(KEY_OK, false)) {
                    continuation.resumeWithException(
                        IllegalStateException(
                            reply.data.getString(KEY_ERROR)
                                ?.takeIf { it.isNotBlank() }
                                ?: "Night extension request failed.",
                        )
                    )
                    return@Handler true
                }

                val raw = reply.data.getString(KEY_RESULT_JSON).orEmpty()
                continuation.resume(
                    runCatching { JSONObject(raw.ifBlank { "{}" }) }
                        .getOrElse {
                            JSONObject()
                                .put("ok", true)
                                .put("result", raw)
                        }
                )
                true
            }
            val replyTo = Messenger(handler)

            payload.putString(KEY_REQUEST_ID, requestId)
            val message =
                Message.obtain(null, what).apply {
                    data = payload
                    this.replyTo = replyTo
                }

            runCatching { remote.send(message) }
                .onFailure {
                    if (continuation.isActive) {
                        continuation.resumeWithException(it)
                    }
                }
        }

    private fun normalizeParameters(raw: JSONObject?): JSONObject {
        val parameters = raw ?: JSONObject()
        if (!parameters.has("type")) {
            parameters.put("type", "object")
        }
        if (!parameters.has("properties")) {
            parameters.put("properties", JSONObject())
        }
        if (!parameters.has("additionalProperties")) {
            parameters.put("additionalProperties", false)
        }
        return parameters
    }

    private data class BoundService(
        val messenger: Messenger,
        val connection: ServiceConnection,
    )

    companion object {
        const val ACTION_EXTENSION_SERVICE =
            "com.example.whatsapp.action.NIGHT_EXTENSION_SERVICE"

        const val MSG_DESCRIBE = 1
        const val MSG_EXECUTE_TOOL = 2
        const val MSG_EXECUTE_ACTION = 3
        const val MSG_RESULT = 100

        const val KEY_REQUEST_ID = "requestId"
        const val KEY_OK = "ok"
        const val KEY_ERROR = "error"
        const val KEY_RESULT_JSON = "resultJson"
        const val KEY_EXTENSION_ID = "extensionId"
        const val KEY_CHAT_ID = "chatId"
        const val KEY_TOOL_NAME = "toolName"
        const val KEY_ARGUMENTS_JSON = "argumentsJson"
        const val KEY_MESSAGE_ID = "messageId"
        const val KEY_MESSAGE_TYPE = "messageType"
        const val KEY_ACTION_ID = "actionId"
        const val KEY_PAYLOAD_JSON = "payloadJson"

        const val SUPPORTED_SCHEMA_VERSION = 1
        private const val MAX_TOOLS = 64
        private const val MAX_MESSAGE_TYPES = 64
        private const val REQUEST_TIMEOUT_MS = 15_000L

        private val EXTENSION_ID =
            Regex("[a-z0-9][a-z0-9_.-]{1,63}")

        @Volatile
        private var instance: NightExternalExtensionManager? = null

        fun get(context: Context): NightExternalExtensionManager =
            instance ?: synchronized(this) {
                instance
                    ?: NightExternalExtensionManager(context)
                        .also { instance = it }
            }
    }
}
