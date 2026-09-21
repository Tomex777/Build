package com.example.whatsapp.extensions.tools

import android.util.Base64
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * MCP Streamable-HTTP client for Night.
 *
 * Night auto-negotiates the MCP protocol era:
 * - 2026-07-28: stateless server/discover + per-request metadata/headers.
 * - 2025-11-25 and earlier: initialize/initialized + optional session id.
 *
 * Discovered tools are registered in [NightMcpToolRegistry] and therefore use
 * the exact same model-facing execution loop as Night's built-in and extension
 * tools.
 */
class NightMcpHttpBridge(
    private val serverId: String,
    private val endpoint: String,
    private val bearerToken: String? = null,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private enum class Era {
        MODERN,
        LEGACY,
    }

    private data class HttpResult(
        val statusCode: Int,
        val raw: String,
        val contentType: String,
        val sessionId: String?,
    )

    private val nextRequestId = AtomicLong(1L)

    @Volatile
    private var era: Era? = null

    @Volatile
    private var sessionId: String? = null

    @Volatile
    private var legacyProtocolVersion: String = LEGACY_PROTOCOL_VERSION

    suspend fun connect(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            require(serverId.isNotBlank()) {
                "MCP server id is required."
            }
            require(
                endpoint.startsWith("https://") ||
                    endpoint.startsWith("http://")
            ) {
                "MCP endpoint must use HTTP or HTTPS."
            }

            val modern = probeModernEra()
            if (modern) {
                era = Era.MODERN
                sessionId = null
            } else {
                connectLegacy()
                era = Era.LEGACY
            }

            val listed = rpcBlocking(
                method = "tools/list",
                params = JSONObject(),
            )
            val tools =
                listed.optJSONArray("tools") ?: JSONArray()

            NightMcpToolRegistry.unregisterServer(serverId)
            var registered = 0

            for (index in 0 until tools.length()) {
                val item =
                    tools.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                val description =
                    item.optString("description")
                        .trim()
                        .ifBlank {
                            "MCP tool " + name
                        }
                if (name.isBlank()) continue

                val schema =
                    item.optJSONObject("inputSchema")
                        ?: JSONObject()
                            .put("type", "object")
                            .put(
                                "properties",
                                JSONObject(),
                            )

                val readOnly =
                    item.optJSONObject("annotations")
                        ?.optBoolean(
                            "readOnlyHint",
                            false,
                        )
                        ?: false

                val definition =
                    NightMcpToolDefinition(
                        serverId = serverId,
                        name = name,
                        description = description,
                        parameters = schema,
                        readOnly = readOnly,
                    )

                NightMcpToolRegistry.register(
                    definition
                ) { _, arguments ->
                    callTool(
                        name = name,
                        arguments = arguments,
                        inputSchema = schema,
                    )
                }
                registered += 1
            }

            registered
        }.onFailure {
            NightMcpToolRegistry.unregisterServer(serverId)
            era = null
            sessionId = null
        }
    }

    fun disconnect() {
        NightMcpToolRegistry.unregisterServer(serverId)
        era = null
        sessionId = null
    }

    private fun probeModernEra(): Boolean {
        val params =
            JSONObject().put(
                "_meta",
                modernEnvelope(),
            )
        val payload =
            jsonRpcRequest(
                method = "server/discover",
                params = params,
            )
        val result =
            executeHttp(
                payload = payload,
                protocolEra = Era.MODERN,
                method = "server/discover",
                principalName = null,
                extraHeaders = emptyMap(),
            )

        if (
            result.statusCode == 401 ||
            result.statusCode == 403
        ) {
            error(
                "MCP authorization failed (" +
                    result.statusCode + ")."
            )
        }
        if (result.statusCode >= 500) {
            error(
                "MCP discovery failed (" +
                    result.statusCode + "): " +
                    result.raw.take(500)
            )
        }

        val response =
            runCatching {
                decodeResponse(
                    raw = result.raw,
                    contentType = result.contentType,
                )
            }.getOrNull()

        if (response == null) {
            if (result.statusCode in 400..499) {
                return false
            }
            error(
                "MCP discovery returned an unreadable response (" +
                    result.statusCode + ")."
            )
        }

        val error = response.optJSONObject("error")
        if (error != null) {
            val code = error.optInt("code", 0)
            if (
                code == METHOD_NOT_FOUND ||
                code == UNSUPPORTED_PROTOCOL_VERSION
            ) {
                return false
            }
            if (result.statusCode in 400..499) {
                return false
            }
            error(
                "MCP server/discover failed: " +
                    error.optString("message")
                        .ifBlank {
                            error.toString()
                        }
            )
        }

        if (!result.statusCode.toString().startsWith("2")) {
            return false
        }

        val discover =
            response.optJSONObject("result")
                ?: return false
        val supported =
            discover.optJSONArray("supportedVersions")
        if (supported == null) {
            // A successful server/discover response is definitive modern-era
            // evidence even when a permissive server omits the optional list.
            return true
        }

        for (index in 0 until supported.length()) {
            if (
                supported.optString(index) ==
                    MODERN_PROTOCOL_VERSION
            ) {
                return true
            }
        }
        return false
    }

    private fun connectLegacy() {
        sessionId = null
        legacyProtocolVersion =
            LEGACY_PROTOCOL_VERSION

        val payload =
            jsonRpcRequest(
                method = "initialize",
                params = JSONObject()
                    .put(
                        "protocolVersion",
                        LEGACY_PROTOCOL_VERSION,
                    )
                    .put(
                        "capabilities",
                        JSONObject(),
                    )
                    .put(
                        "clientInfo",
                        clientInfo(),
                    ),
            )

        val response =
            executeHttp(
                payload = payload,
                protocolEra = Era.LEGACY,
                method = "initialize",
                principalName = null,
                extraHeaders = emptyMap(),
                includeLegacyProtocolHeader = false,
            )

        if (!response.statusCode.toString().startsWith("2")) {
            error(
                "MCP initialize failed (" +
                    response.statusCode + "): " +
                    response.raw.take(500)
            )
        }

        captureSession(response.sessionId)
        val root =
            decodeResponse(
                raw = response.raw,
                contentType = response.contentType,
            )
        root.optJSONObject("error")?.let { error ->
            error(
                "MCP initialize failed: " +
                    error.optString("message")
                        .ifBlank {
                            error.toString()
                        }
            )
        }

        val initialized =
            root.optJSONObject("result")
                ?: error(
                    "MCP initialize returned no result."
                )
        legacyProtocolVersion =
            initialized.optString(
                "protocolVersion",
                LEGACY_PROTOCOL_VERSION,
            ).ifBlank {
                LEGACY_PROTOCOL_VERSION
            }

        notifyInitializedLegacy()
    }

    private fun callTool(
        name: String,
        arguments: JSONObject,
        inputSchema: JSONObject,
    ): JSONObject {
        val result =
            rpcBlocking(
                method = "tools/call",
                params = JSONObject()
                    .put("name", name)
                    .put(
                        "arguments",
                        arguments,
                    ),
                modernHeaders =
                    if (era == Era.MODERN) {
                        mirroredToolHeaders(
                            arguments = arguments,
                            inputSchema = inputSchema,
                        )
                    } else {
                        emptyMap()
                    },
            )

        if (
            era == Era.MODERN &&
            result.optString("resultType") ==
                "input_required"
        ) {
            return JSONObject()
                .put("ok", false)
                .put(
                    "input_required",
                    true,
                )
                .put(
                    "message",
                    "This MCP tool needs another user-input round before it can finish.",
                )
                .put(
                    "input_requests",
                    result.opt("inputRequests")
                        ?: JSONObject.NULL,
                )
                .put(
                    "request_state",
                    result.opt("requestState")
                        ?: JSONObject.NULL,
                )
        }

        val output =
            JSONObject()
                .put(
                    "ok",
                    !result.optBoolean(
                        "isError",
                        false,
                    ),
                )
                .put(
                    "server_id",
                    serverId,
                )
                .put(
                    "tool",
                    name,
                )

        result.optJSONObject("structuredContent")
            ?.let {
                output.put(
                    "structured_content",
                    it,
                )
            }

        val content = result.optJSONArray("content")
        if (content != null) {
            output.put("content", content)

            val text =
                buildString {
                    for (
                        index in 0 until content.length()
                    ) {
                        val part =
                            content.optJSONObject(index)
                                ?: continue
                        if (
                            part.optString("type") ==
                                "text"
                        ) {
                            val value =
                                part.optString("text")
                            if (value.isNotBlank()) {
                                if (isNotEmpty()) {
                                    append("\n")
                                }
                                append(value)
                            }
                        }
                    }
                }
            if (text.isNotBlank()) {
                output.put("text", text)
            }
        }

        return output
    }

    private fun rpcBlocking(
        method: String,
        params: JSONObject,
        modernHeaders: Map<String, String> =
            emptyMap(),
    ): JSONObject {
        val activeEra =
            era ?: error(
                "MCP server is not connected."
            )
        val wireParams =
            if (activeEra == Era.MODERN) {
                JSONObject(params.toString())
                    .put(
                        "_meta",
                        mergeModernMeta(
                            params.optJSONObject("_meta"),
                        ),
                    )
            } else {
                params
            }

        val payload =
            jsonRpcRequest(
                method = method,
                params = wireParams,
            )

        val principalName =
            when (method) {
                "tools/call",
                "prompts/get" ->
                    wireParams.optString("name")
                        .takeIf {
                            it.isNotBlank()
                        }
                "resources/read" ->
                    wireParams.optString("uri")
                        .takeIf {
                            it.isNotBlank()
                        }
                else -> null
            }

        val response =
            executeHttp(
                payload = payload,
                protocolEra = activeEra,
                method = method,
                principalName = principalName,
                extraHeaders = modernHeaders,
            )

        val root =
            runCatching {
                decodeResponse(
                    raw = response.raw,
                    contentType =
                        response.contentType,
                )
            }.getOrElse {
                if (
                    !response.statusCode
                        .toString()
                        .startsWith("2")
                ) {
                    error(
                        "MCP request failed (" +
                            response.statusCode +
                            "): " +
                            response.raw.take(500)
                    )
                }
                throw it
            }

        val error = root.optJSONObject("error")
        if (error != null) {
            error(
                "MCP " + method + " failed: " +
                    error.optString("message")
                        .ifBlank {
                            error.toString()
                        }
            )
        }

        if (
            !response.statusCode
                .toString()
                .startsWith("2")
        ) {
            error(
                "MCP request failed (" +
                    response.statusCode + ")."
            )
        }

        val result = root.opt("result")
        return when (result) {
            is JSONObject -> result
            is JSONArray ->
                JSONObject().put(
                    "items",
                    result,
                )
            null,
            JSONObject.NULL ->
                JSONObject()
            else ->
                JSONObject().put(
                    "value",
                    result,
                )
        }
    }

    private fun notifyInitializedLegacy() {
        val payload =
            JSONObject()
                .put("jsonrpc", "2.0")
                .put(
                    "method",
                    "notifications/initialized",
                )
                .put(
                    "params",
                    JSONObject(),
                )

        val response =
            executeHttp(
                payload = payload,
                protocolEra = Era.LEGACY,
                method =
                    "notifications/initialized",
                principalName = null,
                extraHeaders = emptyMap(),
            )

        if (
            response.statusCode !in
                setOf(200, 202, 204)
        ) {
            error(
                "MCP initialization notification failed (" +
                    response.statusCode + ")."
            )
        }
        captureSession(response.sessionId)
    }

    private fun jsonRpcRequest(
        method: String,
        params: JSONObject,
    ): JSONObject =
        JSONObject()
            .put("jsonrpc", "2.0")
            .put(
                "id",
                nextRequestId.getAndIncrement(),
            )
            .put("method", method)
            .put("params", params)

    private fun executeHttp(
        payload: JSONObject,
        protocolEra: Era,
        method: String,
        principalName: String?,
        extraHeaders: Map<String, String>,
        includeLegacyProtocolHeader: Boolean =
            true,
    ): HttpResult {
        val builder =
            requestBuilder()
                .post(
                    payload.toString()
                        .toRequestBody(
                            JSON_MEDIA_TYPE,
                        )
                )

        when (protocolEra) {
            Era.MODERN -> {
                builder.header(
                    HEADER_PROTOCOL_VERSION,
                    MODERN_PROTOCOL_VERSION,
                )
                builder.header(
                    HEADER_METHOD,
                    encodeHeaderValue(method),
                )
                principalName?.let {
                    builder.header(
                        HEADER_NAME,
                        encodeHeaderValue(it),
                    )
                }
                extraHeaders.forEach {
                        (name, value) ->
                    builder.header(name, value)
                }
            }

            Era.LEGACY -> {
                if (includeLegacyProtocolHeader) {
                    builder.header(
                        HEADER_PROTOCOL_VERSION,
                        legacyProtocolVersion,
                    )
                }
                sessionId?.let {
                    builder.header(
                        HEADER_SESSION_ID,
                        it,
                    )
                }
            }
        }

        http.newCall(builder.build())
            .execute()
            .use { response ->
                val raw =
                    response.body?.string().orEmpty()
                return HttpResult(
                    statusCode = response.code,
                    raw = raw,
                    contentType =
                        response.header(
                            "Content-Type",
                        ).orEmpty(),
                    sessionId =
                        response.header(
                            HEADER_SESSION_ID,
                        ),
                )
            }
    }

    private fun requestBuilder(): Request.Builder =
        Request.Builder()
            .url(endpoint)
            .header(
                "Content-Type",
                "application/json",
            )
            .header(
                "Accept",
                "application/json, text/event-stream",
            )
            .apply {
                bearerToken
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        header(
                            "Authorization",
                            "Bearer " + it,
                        )
                    }
            }

    private fun modernEnvelope(): JSONObject =
        JSONObject()
            .put(
                META_PROTOCOL_VERSION,
                MODERN_PROTOCOL_VERSION,
            )
            .put(
                META_CLIENT_CAPABILITIES,
                JSONObject(),
            )
            .put(
                META_CLIENT_INFO,
                clientInfo(),
            )

    private fun mergeModernMeta(
        existing: JSONObject?,
    ): JSONObject {
        val meta =
            existing?.let {
                JSONObject(it.toString())
            } ?: JSONObject()
        if (!meta.has(META_PROTOCOL_VERSION)) {
            meta.put(
                META_PROTOCOL_VERSION,
                MODERN_PROTOCOL_VERSION,
            )
        }
        if (!meta.has(META_CLIENT_CAPABILITIES)) {
            meta.put(
                META_CLIENT_CAPABILITIES,
                JSONObject(),
            )
        }
        if (!meta.has(META_CLIENT_INFO)) {
            meta.put(
                META_CLIENT_INFO,
                clientInfo(),
            )
        }
        return meta
    }

    private fun clientInfo(): JSONObject =
        JSONObject()
            .put("name", "Night")
            .put("version", "1")

    private fun mirroredToolHeaders(
        arguments: JSONObject,
        inputSchema: JSONObject,
    ): Map<String, String> {
        val properties =
            inputSchema.optJSONObject("properties")
                ?: return emptyMap()
        val result =
            linkedMapOf<String, String>()

        val keys = properties.keys()
        while (keys.hasNext()) {
            val argumentName = keys.next()
            val property =
                properties.optJSONObject(argumentName)
                    ?: continue
            val headerName =
                property.optString("x-mcp-header")
                    .trim()
            if (
                headerName.isBlank() ||
                !arguments.has(argumentName) ||
                arguments.isNull(argumentName)
            ) {
                continue
            }

            val type =
                property.optString("type")
            val raw =
                when (type) {
                    "string" ->
                        arguments.optString(
                            argumentName,
                        )
                    "integer" ->
                        arguments.optLong(
                            argumentName,
                        ).toString()
                    "boolean" ->
                        arguments.optBoolean(
                            argumentName,
                        ).toString()
                    else -> continue
                }

            result[
                "Mcp-Param-" + headerName
            ] = encodeHeaderValue(raw)
        }
        return result
    }

    private fun encodeHeaderValue(
        value: String,
    ): String {
        val matchesSentinel =
            value.startsWith("=?base64?") &&
                value.endsWith("?=")
        val safeAscii =
            value.isNotEmpty() &&
                value.first() !in charArrayOf(
                    ' ',
                    '\t',
                ) &&
                value.last() !in charArrayOf(
                    ' ',
                    '\t',
                ) &&
                value.all { char ->
                    val code = char.code
                    code in 0x20..0x7E &&
                        code != 0x7F
                }

        if (
            safeAscii &&
            !matchesSentinel
        ) {
            return value
        }

        val encoded =
            Base64.encodeToString(
                value.toByteArray(
                    Charsets.UTF_8,
                ),
                Base64.NO_WRAP,
            )
        return "=?base64?" +
            encoded +
            "?="
    }

    private fun captureSession(
        value: String?,
    ) {
        value
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                sessionId = it
            }
    }

    private fun decodeResponse(
        raw: String,
        contentType: String,
    ): JSONObject {
        if (
            contentType.contains(
                "text/event-stream",
                ignoreCase = true,
            )
        ) {
            val candidates =
                raw.lineSequence()
                    .map(String::trim)
                    .filter {
                        it.startsWith("data:")
                    }
                    .map {
                        it.removePrefix("data:")
                            .trim()
                    }
                    .filter {
                        it.isNotBlank() &&
                            it != "[DONE]"
                    }
                    .toList()

            candidates.asReversed()
                .forEach { data ->
                    runCatching {
                        JSONObject(data)
                    }.getOrNull()
                        ?.let {
                            return it
                        }
                }
            error(
                "MCP server returned no JSON-RPC event."
            )
        }

        require(raw.isNotBlank()) {
            "MCP server returned an empty response."
        }
        return JSONObject(raw)
    }

    companion object {
        const val MODERN_PROTOCOL_VERSION =
            "2026-07-28"
        const val LEGACY_PROTOCOL_VERSION =
            "2025-11-25"

        private const val HEADER_PROTOCOL_VERSION =
            "MCP-Protocol-Version"
        private const val HEADER_SESSION_ID =
            "Mcp-Session-Id"
        private const val HEADER_METHOD =
            "Mcp-Method"
        private const val HEADER_NAME =
            "Mcp-Name"

        private const val META_PROTOCOL_VERSION =
            "io.modelcontextprotocol/protocolVersion"
        private const val META_CLIENT_INFO =
            "io.modelcontextprotocol/clientInfo"
        private const val META_CLIENT_CAPABILITIES =
            "io.modelcontextprotocol/clientCapabilities"

        private const val METHOD_NOT_FOUND = -32601
        private const val UNSUPPORTED_PROTOCOL_VERSION =
            -32022

        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8"
                .toMediaType()
    }
}
