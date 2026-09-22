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
import java.security.MessageDigest
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class NightInstalledExtensionSummary(
    val extensionId: String,
    val displayName: String,
    val packageName: String,
    val serviceName: String,
    val toolCount: Int,
    val messageTypeCount: Int,
    val enabled: Boolean,
    val capabilities: Set<NightIntegrationCapability> = emptySet(),
    val error: String? = null,
    val signingDigest: String = "",
)

/**
 * Runtime bridge for independently installed Night extension APKs.
 *
 * Extension APKs expose an exported bound service with [ACTION_EXTENSION_SERVICE].
 * Night discovers those services and reads their JSON descriptors, but external
 * extensions remain disabled until the user explicitly enables them in Night.
 *
 * IPC deliberately carries JSON-only payloads. Night never sends provider keys,
 * full conversation history, or another extension's state to an extension.
 */
class NightExternalExtensionManager private constructor(
    context: Context,
) {
    private data class Discovered(
        val component: ComponentName,
        val descriptor: JSONObject,
        val extensionId: String,
        val displayName: String,
        val toolCount: Int,
        val messageTypeCount: Int,
        val capabilities: Set<NightIntegrationCapability>,
        val signingDigest: String,
    )

    private val app = context.applicationContext
    private val prefs =
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val extensionServices =
        ConcurrentHashMap<String, ComponentName>()

    private val _extensions =
        MutableStateFlow<List<NightInstalledExtensionSummary>>(emptyList())
    val extensions: StateFlow<List<NightInstalledExtensionSummary>> =
        _extensions

    suspend fun refreshInstalledExtensions(): List<String> {
        val components = withContext(Dispatchers.IO) {
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

        val discovered = mutableListOf<Discovered>()
        val failures = mutableListOf<NightInstalledExtensionSummary>()

        components.forEach { component ->
            runCatching {
                val descriptor = request(
                    component = component,
                    what = MSG_DESCRIBE,
                    payload = Bundle(),
                )
                parseDescriptor(component, descriptor)
            }.onSuccess(discovered::add)
                .onFailure { error ->
                    failures += NightInstalledExtensionSummary(
                        extensionId = component.packageName,
                        displayName = component.packageName,
                        packageName = component.packageName,
                        serviceName = component.className,
                        toolCount = 0,
                        messageTypeCount = 0,
                        enabled = false,
                        error = error.message ?: "Could not read extension descriptor.",
                        signingDigest = signingDigest(component.packageName).orEmpty(),
                    )
                }
        }

        val duplicateIds = discovered
            .groupBy { it.extensionId }
            .filterValues { it.size > 1 }
            .keys

        val active = linkedSetOf<String>()
        val summaries = mutableListOf<NightInstalledExtensionSummary>()

        discovered.forEach { item ->
            val duplicate = item.extensionId in duplicateIds
            val enabled =
                !duplicate &&
                    isEnabled(
                        component = item.component,
                        extensionId = item.extensionId,
                        signingDigest = item.signingDigest,
                    )

            if (enabled) {
                runCatching {
                    registerDescriptor(
                        component = item.component,
                        descriptor = item.descriptor,
                    )
                }.onSuccess {
                    active += item.extensionId
                    summaries += item.toSummary(
                        enabled = true,
                    )
                }.onFailure { error ->
                    unregister(item.extensionId)
                    summaries += item.toSummary(
                        enabled = false,
                        error = error.message ?: "Could not register extension.",
                    )
                }
            } else {
                summaries += item.toSummary(
                    enabled = false,
                    error = if (duplicate) {
                        "Duplicate extension id. Both packages are blocked until the conflict is removed."
                    } else {
                        null
                    },
                )
            }
        }

        extensionServices.keys
            .filterNot(active::contains)
            .forEach(::unregister)

        _extensions.value =
            (summaries + failures).sortedWith(
                compareByDescending<NightInstalledExtensionSummary> { it.enabled }
                    .thenBy { it.displayName.lowercase() }
                    .thenBy { it.packageName }
            )

        return active.toList()
    }

    suspend fun setEnabled(
        extension: NightInstalledExtensionSummary,
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                approvalKey(
                    packageName = extension.packageName,
                    extensionId = extension.extensionId,
                    signingDigest = extension.signingDigest,
                ),
                enabled,
            )
            .apply()

        if (!enabled) {
            unregister(extension.extensionId)
        }
        refreshInstalledExtensions()
    }

    fun unregister(extensionId: String) {
        extensionServices.remove(extensionId)
        NightExtensionToolRegistry.unregisterExtension(extensionId)
        NightExtensionMessageTypeRegistry.unregisterExtension(extensionId)
        NightExtensionMessageActionRegistry.unregister(extensionId)
    }

    private fun parseDescriptor(
        component: ComponentName,
        descriptor: JSONObject,
    ): Discovered {
        val schemaVersion = descriptor.optInt("schemaVersion", 1)
        require(schemaVersion in 1..SUPPORTED_SCHEMA_VERSION) {
            "Unsupported Night extension schema version: " + schemaVersion
        }

        val extensionId =
            descriptor.optString("extensionId").trim().lowercase()
        require(EXTENSION_ID.matches(extensionId)) {
            "Invalid Night extension id."
        }

        val tools = descriptor.optJSONArray("tools")
        val messageTypes = descriptor.optJSONArray("messageTypes")

        return Discovered(
            component = component,
            descriptor = descriptor,
            extensionId = extensionId,
            displayName = descriptor.optString("name")
                .trim()
                .ifBlank {
                    descriptor.optString("extensionName")
                        .trim()
                        .ifBlank { extensionId }
                },
            toolCount = minOf(tools?.length() ?: 0, MAX_TOOLS),
            messageTypeCount =
                minOf(messageTypes?.length() ?: 0, MAX_MESSAGE_TYPES),
            capabilities =
                NightIntegrationManifest
                    .fromJson(
                        json = descriptor,
                        legacyExtensionId = extensionId,
                        legacyExtensionName = descriptor.optString("name"),
                    )
                    .capabilities,
            signingDigest = requireNotNull(signingDigest(component.packageName)) {
                "Could not verify the extension APK signing certificate."
            },
        )
    }

    private fun registerDescriptor(
        component: ComponentName,
        descriptor: JSONObject,
    ): String {
        val parsed = parseDescriptor(component, descriptor)
        val extensionId = parsed.extensionId

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
                    readOnly =
                        when {
                            item.has("readOnly") ->
                                item.optBoolean("readOnly", false)
                            item.has("readOnlyHint") ->
                                item.optBoolean("readOnlyHint", false)
                            else -> false
                        },
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

    private fun isEnabled(
        component: ComponentName,
        extensionId: String,
        signingDigest: String,
    ): Boolean =
        prefs.getBoolean(
            approvalKey(
                packageName = component.packageName,
                extensionId = extensionId,
                signingDigest = signingDigest,
            ),
            false,
        )

    private fun approvalKey(
        packageName: String,
        extensionId: String,
        signingDigest: String,
    ): String =
        "enabled::" + packageName + "::" +
            extensionId + "::" + signingDigest

    @Suppress("DEPRECATION")
    private fun signingDigest(packageName: String): String? =
        runCatching {
            val packageInfo =
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    app.packageManager.getPackageInfo(
                        packageName,
                        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES,
                    )
                } else {
                    app.packageManager.getPackageInfo(
                        packageName,
                        android.content.pm.PackageManager.GET_SIGNATURES,
                    )
                }

            val signatures =
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    packageInfo.signingInfo
                        ?.apkContentsSigners
                        ?.toList()
                        .orEmpty()
                } else {
                    packageInfo.signatures
                        ?.toList()
                        .orEmpty()
                }

            require(signatures.isNotEmpty()) {
                "Extension package has no signing certificate."
            }

            signatures
                .map { signature ->
                    MessageDigest
                        .getInstance("SHA-256")
                        .digest(signature.toByteArray())
                        .joinToString("") { byte ->
                            "%02x".format(byte)
                        }
                }
                .sorted()
                .joinToString(":")
        }.getOrNull()

    private fun Discovered.toSummary(
        enabled: Boolean,
        error: String? = null,
    ): NightInstalledExtensionSummary =
        NightInstalledExtensionSummary(
            extensionId = extensionId,
            displayName = displayName,
            packageName = component.packageName,
            serviceName = component.className,
            toolCount = toolCount,
            messageTypeCount = messageTypeCount,
            enabled = enabled,
            capabilities = capabilities,
            error = error,
            signingDigest = signingDigest,
        )

    private suspend fun executeTool(
        extensionId: String,
        chatId: String,
        toolName: String,
        arguments: JSONObject,
    ): JSONObject {
        val component = extensionServices[extensionId]
            ?: error(
                "Night extension is disabled or no longer installed: " +
                    extensionId
            )

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
            ?: error(
                "Night extension is disabled or no longer installed: " +
                    extensionId
            )

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
                runCatching {
                    app.unbindService(bound.connection)
                }
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
                        runCatching {
                            app.unbindService(connection)
                        }
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
                    runCatching {
                        JSONObject(raw.ifBlank { "{}" })
                    }.getOrElse {
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
        private const val PREFS_NAME = "night_external_extensions"

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
