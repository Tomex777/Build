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
    /** Preserves a legacy English message for length/pattern/enum failures of this property. */
    val invalidMessage: String? = null,
    /** Schema for an "object" property. Every object property needs either this or [freeForm]. */
    val nested: OperationInputSchema? = null,
    /** Explicitly free-form object/array/any value; the provider is responsible for validating it. */
    val freeForm: Boolean = false,
    /** TypeScript type override for free-form values (annie.d.ts generation). */
    val tsType: String? = null,
)

/**
 * How an operation is exposed to JavaScript. Ergonomics only; the semantic contract is the input schema.
 * [path] overrides the default `annie.<operation id>` (legacy names). Every schema property must be reachable
 * through exactly one of [positional], [optionsKeys] or [spreadArg] (L1 checks this).
 */
internal data class JsBinding(
    val path: String? = null,
    val positional: List<String> = emptyList(),
    val optionsKeys: List<String> = emptyList(),
    val spreadArg: String? = null,
    val returns: String = "Record<string, unknown>",
)

internal data class OperationInputSchema(
    val properties: Map<String, OperationProperty>,
    val additionalProperties: Boolean = false,
) {
    fun validate(operationId: String, input: JSONObject, path: String = "", mode: ValidationMode = ValidationMode.STRICT) {
        // Compatibility dialect affects diagnostic wording, never type/permission/length gates.
        fun errorMessage(property: OperationProperty, fallback: String): String =
            if (mode == ValidationMode.LEGACY) property.invalidMessage ?: fallback else fallback
        val unknown = input.keys().asSequence().filterNot { it in properties }.toList()
        if (mode == ValidationMode.STRICT && !additionalProperties && unknown.isNotEmpty()) {
            throw AnnieError(
                AnnieErrorCode.INVALID_ARGUMENT,
                "Operation '$operationId' received unsupported fields: " +
                    unknown.joinToString(", ") { path + it },
                operationId,
            )
        }
        properties.forEach { (name, property) ->
            if (!input.has(name) || input.opt(name) == JSONObject.NULL) {
                if (property.required) throw AnnieError(
                    AnnieErrorCode.INVALID_ARGUMENT,
                    "Operation '$operationId' requires field '$path$name'",
                    operationId,
                )
                return@forEach
            }
            val value = input.opt(name)
            if (!property.required && value is String && value.isEmpty()) return@forEach
            when (property.type) {
                "string" -> if (value !is String) throw AnnieError(
                    AnnieErrorCode.INVALID_ARGUMENT,
                    "Field '$name' must be a string",
                    operationId,
                )
                "object" -> {
                    if (value !is JSONObject) throw AnnieError(
                        AnnieErrorCode.INVALID_ARGUMENT,
                        "Field '$path$name' must be an object",
                        operationId,
                    )
                    property.nested?.validate(operationId, value, "$path$name.", mode)
                }
                "any" -> Unit
                "array" -> if (value !is org.json.JSONArray) throw AnnieError(
                    AnnieErrorCode.INVALID_ARGUMENT,
                    "Field '$name' must be an array",
                    operationId,
                )
                "number" -> if (value !is Number) throw AnnieError(
                    AnnieErrorCode.INVALID_ARGUMENT,
                    "Field '$name' must be a number",
                    operationId,
                )
                "boolean" -> if (value !is Boolean) throw AnnieError(
                    AnnieErrorCode.INVALID_ARGUMENT,
                    "Field '$name' must be a boolean",
                    operationId,
                )
                else -> throw AnnieError(
                    AnnieErrorCode.INTERNAL,
                    "Unsupported registry schema type '${property.type}'",
                    operationId,
                )
            }
            if (value is String) {
                property.minLength?.let { min ->
                    if (value.length < min) throw AnnieError(
                        AnnieErrorCode.INVALID_ARGUMENT,
                        errorMessage(property, "Field '$name' must be at least ${min} characters"),
                        operationId,
                    )
                }
                property.maxLength?.let { max ->
                    if (value.length > max) throw AnnieError(
                        AnnieErrorCode.INVALID_ARGUMENT,
                        errorMessage(property, "Field '$name' must be at most ${max} characters"),
                        operationId,
                    )
                }
                property.pattern?.let { regex ->
                    if (!regex.matches(value)) throw AnnieError(
                        AnnieErrorCode.INVALID_ARGUMENT,
                        errorMessage(property, "Field '$name' has an invalid format"),
                        operationId,
                    )
                }
                if (property.enumValues.isNotEmpty() && value !in property.enumValues) throw AnnieError(
                    AnnieErrorCode.INVALID_ARGUMENT,
                    errorMessage(property, "Field '$name' must be one of ${property.enumValues.joinToString(", ")}"),
                    operationId,
                )
            }
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
    val capabilities: Set<String> = setOf(capability),
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
    val js: JsBinding = JsBinding(),
)

internal data class OperationInvocation(
    val packageId: String,
    val isPackage: Boolean,
    val declaredCapabilities: Set<String>,
    val declaredPermissions: Set<String>,
    val grantedPermissions: Set<String>,
    val projectId: String? = null,
    val chatId: String? = null,
    val validationMode: ValidationMode = ValidationMode.STRICT,
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
        require(provider.id.isNotBlank()) { "Operation provider id must not be blank" }
        require(provider.operations.map(OperationDefinition::id).distinct().size == provider.operations.size) {
            "Operation provider declares duplicate operation IDs: " + provider.id
        }
        require(providers[provider.id] == null) {
            "Operation provider already registered: " + provider.id
        }

        provider.operations.forEach { operation ->
            require(operation.provider == provider.id) {
                "Operation provider mismatch: " + operation.id + " belongs to " + operation.provider + ", not " + provider.id
            }
            require(operation.id == operation.namespace + "." + operation.name) {
                "Operation id must derive from namespace and name: " + operation.id
            }
            require(operation.errors.isNotEmpty()) { "Operation must declare at least one error: " + operation.id }
            require(definitions[operation.id] == null) {
                "Operation id is already registered: " + operation.id
            }
            require(operation.maxInputBytes > 0 && operation.maxOutputBytes > 0) {
                "Operation limits must be positive: " + operation.id
            }
            require(operation.since > 0) {
                "Operation since must be positive: " + operation.id
            }
        }

        providers[provider.id] = provider
        provider.operations.forEach { definitions[it.id] = it }
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
        val missingCapability = operation.capabilities.firstOrNull { it !in invocation.declaredCapabilities }
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
        operation.input.validate(id, input, mode = invocation.validationMode)

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
        val result = try {
            provider.invoke(operation, invocation, input).toString()
        } catch (failure: Throwable) {
            val error = failure.toAnnieError(id)
            // Typed errors from providers are held to the same declared-error set as mapped exceptions.
            if (error.code != AnnieErrorCode.INTERNAL && error.code !in operation.errors) {
                throw AnnieError(
                    AnnieErrorCode.INTERNAL,
                    "Operation provider emitted undeclared error " + error.code.name,
                    id,
                )
            }
            throw error
        }
        if (result.toByteArray(Charsets.UTF_8).size > operation.maxOutputBytes) throw AnnieError(
            AnnieErrorCode.RESOURCE_LIMIT,
            "Android bridge output is too large",
            id,
        )
        return result
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
            "does not exist" in lower || "not found" in lower || "missing" in lower ||
                "asset is not declared" in lower -> AnnieErrorCode.NOT_FOUND
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

/** Typed failure raised by the services.call machinery itself (message text stays the legacy English). */
internal fun serviceFailure(code: AnnieErrorCode, message: String, permission: String? = null) =
    AnnieError(code, message, "services.call", retryable = false, permission = permission)

/**
 * Rebuilds a provider package's AnnieError on the caller side of services.call.
 * Keeps code/operation/retryable/retryAfterMs/permission; message goes through [redact];
 * unknown codes become INTERNAL; no cause or stack ever crosses the boundary.
 */
internal fun decodeServiceError(info: JSONObject, redact: (String) -> String): AnnieError {
    val code = runCatching { AnnieErrorCode.valueOf(info.optString("code")) }.getOrDefault(AnnieErrorCode.INTERNAL)
    val message = redact(info.optString("message").ifBlank { "Service failed" }).take(MAX_SERVICE_ERROR_MESSAGE_CHARS)
    return AnnieError(
        code = code,
        message = message,
        operation = info.optString("operation").ifBlank { "services.call" },
        retryable = info.optBoolean("retryable", false),
        retryAfterMs = if (info.isNull("retryAfterMs")) null else info.optLong("retryAfterMs").takeIf { it > 0 },
        permission = if (info.isNull("permission")) null else info.optString("permission").ifBlank { null },
    )
}

internal const val MAX_SERVICE_ERROR_MESSAGE_CHARS = 500
