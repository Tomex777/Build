package com.tomex777.annie

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal const val MESSAGES_PROVIDER_ID = "messages"
internal const val MESSAGES_CAPABILITY = "messages"
internal const val MESSAGES_POST_PERMISSION = "messages.post"
internal const val MESSAGE_SEND_RATE_LIMIT = 20
internal const val MESSAGE_SEND_WINDOW_MS = 60_000L
internal const val MAX_MESSAGE_BYTES = 48 * 1024

/**
 * Host-owned message handle (contract v0.2 section 7). The id is an opaque token minted by the host and stored
 * with the chat entry; a package can only update messages it created. Nothing is held in QuickJS.
 */
internal data class ScriptMessageHandle(
    val id: String,
    val packageId: String,
    val conversationId: String,
    val createdAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("packageId", packageId).put("conversationId", conversationId).put("createdAt", createdAt)
}

/** Static definitions, free of Android, so conformance tests can inspect them on the JVM. */
internal fun messageOperationDefinitions(): List<OperationDefinition> {
    fun op(name: String, input: OperationInputSchema, js: JsBinding) = OperationDefinition(
        id = "messages.$name",
        namespace = "messages",
        name = name,
        capability = MESSAGES_CAPABILITY,
        permissions = listOf(MESSAGES_POST_PERMISSION),
        provider = MESSAGES_PROVIDER_ID,
        since = 1,
        input = input,
        js = js,
        maxInputBytes = MAX_MESSAGE_BYTES + 1024,
        errors = setOf(
            AnnieErrorCode.NOT_A_PACKAGE,
            AnnieErrorCode.NOT_DECLARED,
            AnnieErrorCode.NOT_GRANTED,
            AnnieErrorCode.INVALID_ARGUMENT,
            AnnieErrorCode.RESOURCE_LIMIT,
            AnnieErrorCode.RATE_LIMITED,
            AnnieErrorCode.NOT_FOUND,
            AnnieErrorCode.INTERNAL,
        ),
    )

    fun schema(vararg properties: Pair<String, OperationProperty>) =
        OperationInputSchema(linkedMapOf(*properties), additionalProperties = false)

    val message = OperationProperty("object", required = true, freeForm = true, tsType = "AnnieMessage")
    return listOf(
        op(
            "send",
            schema("message" to message),
            JsBinding(positional = listOf("message"), returns = "AnnieMessageHandle"),
        ),
        op(
            "update",
            schema("id" to OperationProperty("string", required = true, maxLength = 64), "message" to message),
            JsBinding(positional = listOf("id", "message"), returns = "AnnieMessageHandle"),
        ),
    )
}

internal class ScriptMessageOperationProvider(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : OperationProvider {
    override val id: String = MESSAGES_PROVIDER_ID
    override val version: String = "1"
    override val operations = messageOperationDefinitions()

    override suspend fun invoke(
        operation: OperationDefinition,
        invocation: OperationInvocation,
        input: JSONObject,
    ): JSONObject {
        val projectId = invocation.projectId ?: throw AnnieError(
            AnnieErrorCode.NOT_A_PACKAGE, "Package operation has no project identity", operation.id,
        )
        val message = input.getJSONObject("message")
        validateMessage(operation.id, message)
        val resultJson = message.toString()
        return when (operation.id) {
            "messages.send" -> {
                enforceRate(projectId, operation.id)
                val chatId = invocation.chatId?.takeIf(String::isNotBlank) ?: throw AnnieError(
                    AnnieErrorCode.INVALID_ARGUMENT,
                    "There is no chat to post into. Call messages.send from a command or action.",
                    operation.id,
                )
                ChatHistoryStore.sendScriptMessage(context, chatId, resultJson, projectId)?.toJson()
                    ?: throw AnnieError(AnnieErrorCode.NOT_FOUND, "The chat no longer exists", operation.id)
            }
            "messages.update" -> {
                val handleId = input.optString("id").trim()
                ChatHistoryStore.updateScriptMessage(context, handleId, projectId, resultJson)?.toJson()
                    ?: throw AnnieError(AnnieErrorCode.NOT_FOUND, "Message handle not found for this package", operation.id)
            }
            else -> throw AnnieError(AnnieErrorCode.UNSUPPORTED, "Operation is not available: ${operation.id}", operation.id)
        }
    }

    private fun validateMessage(operationId: String, message: JSONObject) {
        val type = message.optString("type").trim()
        if (type.isEmpty() || type.length > 32) throw AnnieError(
            AnnieErrorCode.INVALID_ARGUMENT, "Message needs a type of 1-32 characters", operationId,
        )
        if (type == "error") throw AnnieError(
            AnnieErrorCode.INVALID_ARGUMENT, "The error type is reserved; send a text message instead", operationId,
        )
        val known = MessageTypeRegistry.supportedWireNames() + "text"
        if (type !in known) throw AnnieError(
            AnnieErrorCode.INVALID_ARGUMENT,
            "Unknown message type '$type'. Supported: ${known.sorted().joinToString(", ")}",
            operationId,
        )
        if (message.toString().toByteArray(Charsets.UTF_8).size > MAX_MESSAGE_BYTES) throw AnnieError(
            AnnieErrorCode.RESOURCE_LIMIT, "Message is too large", operationId,
        )
        if (type == "canvas" && AnnieCanvasDocument.from(message) == null) throw AnnieError(
            AnnieErrorCode.INVALID_ARGUMENT,
            "Canvas needs nonempty html, css or javascript content", operationId,
        )
    }

    private fun enforceRate(projectId: String, operationId: String) {
        val window = sends.getOrPut(projectId) { ArrayDeque() }
        synchronized(window) {
            val now = clock()
            while (window.isNotEmpty() && now - window.first() >= MESSAGE_SEND_WINDOW_MS) window.removeFirst()
            if (window.size >= MESSAGE_SEND_RATE_LIMIT) throw AnnieError(
                AnnieErrorCode.RATE_LIMITED,
                "Too many messages; wait before sending more",
                operationId,
                retryable = true,
                retryAfterMs = (MESSAGE_SEND_WINDOW_MS - (now - window.first())).coerceAtLeast(1L),
            )
            window.addLast(now)
        }
    }

    private companion object {
        val sends = ConcurrentHashMap<String, ArrayDeque<Long>>()
    }
}
