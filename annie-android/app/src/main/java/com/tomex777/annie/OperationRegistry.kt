package com.tomex777.annie

import org.json.JSONObject
import org.json.JSONTokener

internal enum class AnnieErrorCode {
    NOT_A_PACKAGE, NOT_DECLARED, NOT_GRANTED, UNAVAILABLE, INVALID_ARGUMENT,
    FOREGROUND_REQUIRED, RATE_LIMITED, RESOURCE_LIMIT, NETWORK_ERROR, NOT_FOUND,
    UNSUPPORTED, TIMEOUT, CANCELLED, HOST_NOT_ALLOWED, INTERNAL,
}

internal class AnnieError(
    val code: AnnieErrorCode,
    override val message: String,
    val operation: String,
    val retryable: Boolean = false,
    val retryAfterMs: Long? = null,
    val permission: String? = null,
) : RuntimeException(message)

internal data class OperationProperty(
    val type: String,
    val required: Boolean = false,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: Regex? = null,
    val enumValues: Set<String> = emptySet(),
)

internal data class OperationInputSchema(
    val properties: Map<String, OperationProperty>,
    val additionalProperties: Boolean = false,
) {
    fun validate(operationId: String, input: JSONObject) {
        val unknown = input.keys().asSequence().filterNot { it in properties }.toList()
        if (!additionalProperties && unknown.isNotEmpty()) {
            throw AnnieError(
                AnnieErrorCode.INVALID_ARGUMENT,
                "Operation '$operationId' received unsupported fields: ${unknown.joinToString(", ")}",
                operationId,
            )
        }
        properties.forEach { (name, property) ->
            if (!input.has(name) || input.opt(name) == JSONObject.NULL) {
                if (property.required) throw AnnieError(
                    AnnieErrorCode.INVALID_ARGUMENT,
                    "Operation '$operationId' requires field '$name'",
                    operationId,
                )
                return@forEach
            }
            val value = input.opt(name)
            if (property.type != "string" || value !is String) {
                throw AnnieError(AnnieErrorCode.INVALID_ARGUMENT, "Field '$name' must be a string", operationId)
            }
            if (!property.required && value.isEmpty()) return@forEach
            property.minLength?.let { min ->
                if (value.length < min) throw AnnieError(
                    AnnieErrorCode.INVALID_ARGUMENT,
                    "Field '$name' must be at least ${min} characters",
                    operationId,
                )
            }
            property.maxLength?.let { max ->
                if (value.length > max) throw AnnieError(
                    AnnieErrorCode.INVALID_ARGUMENT,
                    "Field '$name' must be at most ${max} characters",
                    operationId,
                )
            }
            property.pattern?.let { regex ->
                if (!regex.matches(value)) throw AnnieError(
                    AnnieErrorCode.INVALID_ARGUMENT,
                    "Field '$name' has an invalid format",
                    operationId,
                )
            }
            if (property.enumValues.isNotEmpty() && value !in property.enumValues) throw AnnieError(
                AnnieErrorCode.INVALID_ARGUMENT,
                "Field '$name' must be one of ${property.enumValues.joinToString(", ")}",
                operationId,
            )
        }
    }
}

internal data class OperationDefinition(
    val id: String,
    val namespace: String,
    val name: String,
    val capability: String,
    val permissions: List<String>,
    val provider: String,
    val since: Int,
    val input: OperationInputSchema,
    val resultType: String = "object",
    val errors: Set<AnnieErrorCode> = setOf(
        AnnieErrorCode.NOT_A_PACKAGE,
        AnnieErrorCode.NOT_DECLARED,
        AnnieErrorCode.NOT_GRANTED,
        AnnieErrorCode.INVALID_ARGUMENT,
        AnnieErrorCode.RESOURCE_LIMIT,
    ),
    val docs: String = "",
    val maxInputBytes: Int = MAX_ANDROID_BRIDGE_INPUT_BYTES,
    val maxOutputBytes: Int = MAX_ANDROID_BRIDGE_OUTPUT_BYTES,
)

internal data class OperationInvocation(
    val packageId: String,
    val isPackage: Boolean,
    val declaredCapabilities: Set<String>,
    val declaredPermissions: Set<String>,
    val grantedPermissions: Set<String>,
)

internal interface OperationProvider {
    val id: String
    val version: String
    val operations: List<OperationDefinition>
    suspend fun invoke(operation: OperationDefinition, invocation: OperationInvocation, input: JSONObject): JSONObject
}

internal class OperationRegistry {
    private val definitions = linkedMapOf<String, OperationDefinition>()
    private val providers = linkedMapOf<String, OperationProvider>()

    fun register(provider: OperationProvider) {
        require(providers.putIfAbsent(provider.id, provider) == null) {
            "Operation provider already registered: ${provider.id}"
        }
        provider.operations.forEach { operation ->
            require(operation.id == operation.namespace + "." + operation.name) {
                "Operation id must derive from namespace and name: ${operation.id}"
            }
            require(operation.errors.isNotEmpty()) { "Operation must declare at least one error: ${operation.id}" }
            require(definitions.putIfAbsent(operation.id, operation) == null) {
                "Operation id is already registered: ${operation.id}"
            }
            require(providers.containsKey(operation.provider)) {
                "Operation references unregistered provider: ${operation.provider}"
            }
        }
    }

    fun all(): List<OperationDefinition> = definitions.values.toList()
    fun get(id: String): OperationDefinition? = definitions[id]

    suspend fun invoke(id: String, invocation: OperationInvocation, inputJson: String): String {
        val operation = definitions[id] ?: throw AnnieError(
            AnnieErrorCode.UNSUPPORTED, "Operation is not available: $id", id
        )

        if (!invocation.isPackage) throw AnnieError(
            AnnieErrorCode.NOT_A_PACKAGE,
            "Only imported packages can use Android bridge APIs",
            id,
        )
        val missingCapability = operation.capability.takeIf { it !in invocation.declaredCapabilities }
        if (missingCapability != null) throw AnnieError(
            AnnieErrorCode.NOT_DECLARED,
            "Package does not declare capability ${missingCapability}",
            id,
        )
        val missingPermission = operation.permissions.firstOrNull { it !in invocation.declaredPermissions }
        if (missingPermission != null) throw AnnieError(
            AnnieErrorCode.NOT_DECLARED,
            "Package does not declare permission ${missingPermission}",
            id,
            permission = missingPermission,
        )
        val ungrantedPermission = operation.permissions.firstOrNull { it !in invocation.grantedPermissions }
        if (ungrantedPermission != null) throw AnnieError(
            AnnieErrorCode.NOT_GRANTED,
            "Permission ${ungrantedPermission} has not been granted",
            id,
            permission = ungrantedPermission,
        )

        val parser = JSONTokener(inputJson)
        val input = parser.nextValue() as? JSONObject ?: throw AnnieError(
            AnnieErrorCode.INVALID_ARGUMENT,
            "Android bridge input must be a JSON object",
            id,
        )
        if (parser.nextClean() != '\u0000') throw AnnieError(
            AnnieErrorCode.INVALID_ARGUMENT,
            "Android bridge input must contain one JSON object",
            id,
        )
        operation.input.validate(id, input)

        if (inputJson.toByteArray(Charsets.UTF_8).size > operation.maxInputBytes) throw AnnieError(
            AnnieErrorCode.RESOURCE_LIMIT,
            "Android bridge input is too large",
            id,
        )

        val provider = providers[operation.provider] ?: throw AnnieError(
            AnnieErrorCode.UNAVAILABLE,
            "Operation provider is unavailable: ${operation.provider}",
            id,
        )
        return try {
            val result = provider.invoke(operation, invocation, input).toString()
            if (result.toByteArray(Charsets.UTF_8).size > operation.maxOutputBytes) throw AnnieError(
                AnnieErrorCode.RESOURCE_LIMIT,
                "Android bridge output is too large",
                id,
            )
            result
        } catch (failure: AnnieError) {
            throw failure
        } catch (failure: Throwable) {
            throw failure.toAnnieError(id)
        }
    }
}

internal fun Throwable.toAnnieError(operation: String): AnnieError = when (this) {
    is AnnieError -> this
    else -> {
        val message = message ?: "Operation failed"
        val lower = message.lowercase()
        val code = when {
            "foreground" in lower || "must be open" in lower -> AnnieErrorCode.FOREGROUND_REQUIRED
            "rate limit" in lower || "too many" in lower -> AnnieErrorCode.RATE_LIMITED
            "does not exist" in lower || "not found" in lower || "missing" in lower -> AnnieErrorCode.NOT_FOUND
            "not supported" in lower || "unsupported" in lower -> AnnieErrorCode.UNSUPPORTED
            "timed out" in lower || "timeout" in lower -> AnnieErrorCode.TIMEOUT
            "cancelled" in lower || "canceled" in lower -> AnnieErrorCode.CANCELLED
            this is IllegalArgumentException -> AnnieErrorCode.INVALID_ARGUMENT
            else -> AnnieErrorCode.INTERNAL
        }
        AnnieError(
            code, message, operation,
            retryable = code == AnnieErrorCode.RATE_LIMITED || code == AnnieErrorCode.TIMEOUT,
        )
    }
}

internal fun AnnieError.toPublicJson(): JSONObject = JSONObject()
    .put("code", code.name)
    .put("message", message)
    .put("operation", operation)
    .put("retryable", retryable)
    .put("retryAfterMs", retryAfterMs ?: JSONObject.NULL)
    .put("permission", permission ?: JSONObject.NULL)
