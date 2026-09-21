package com.example.whatsapp.extensions.tools

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
 * Minimal MCP Streamable-HTTP client for Night.
 *
 * The bridge performs the MCP initialize handshake, discovers tools/list and
 * registers those tools in [NightMcpToolRegistry]. Tool execution then uses
 * tools/call and returns JSON that Night's normal agent loop can consume.
 */
class NightMcpHttpBridge(
    private val serverId: String,
    private val endpoint: String,
    private val bearerToken: String? = null,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val nextRequestId = AtomicLong(1L)

    @Volatile
    private var sessionId: String? = null

    suspend fun connect(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            require(serverId.isNotBlank()) { "MCP server id is required." }
            require(
                endpoint.startsWith("https://") ||
                    endpoint.startsWith("http://")
            ) {
                "MCP endpoint must use HTTP or HTTPS."
            }

            val initialized = rpc(
                method = "initialize",
                params = JSONObject()
                    .put("protocolVersion", PROTOCOL_VERSION)
                    .put(
                        "capabilities",
                        JSONObject(),
                    )
                    .put(
                        "clientInfo",
                        JSONObject()
                            .put("name", "Night")
                            .put("version", "1"),
                    ),
            )
            val negotiated =
                initialized.optString("protocolVersion").trim()
            require(negotiated.isNotBlank()) {
                "MCP server did not return a protocol version."
            }

            notifyInitialized()

            val listed = rpc(
                method = "tools/list",
                params = JSONObject(),
            )
            val tools = listed.optJSONArray("tools") ?: JSONArray()

            NightMcpToolRegistry.unregisterServer(serverId)
            var registered = 0

            for (index in 0 until tools.length()) {
                val item = tools.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                val description =
                    item.optString("description").trim()
                        .ifBlank { "MCP tool " + name }
                if (name.isBlank()) continue

                val schema =
                    item.optJSONObject("inputSchema")
                        ?: JSONObject()
                            .put("type", "object")
                            .put("properties", JSONObject())

                val readOnly =
                    item.optJSONObject("annotations")
                        ?.optBoolean("readOnlyHint", false)
                        ?: false

                val definition = NightMcpToolDefinition(
                    serverId = serverId,
                    name = name,
                    description = description,
                    parameters = schema,
                    readOnly = readOnly,
                )

                NightMcpToolRegistry.register(definition) {
                        _,
                        arguments,
                    ->
                    callTool(
                        name = name,
                        arguments = arguments,
                    )
                }
                registered += 1
            }

            registered
        }.onFailure {
            NightMcpToolRegistry.unregisterServer(serverId)
        }
    }

    fun disconnect() {
        NightMcpToolRegistry.unregisterServer(serverId)
        sessionId = null
    }

    private fun callTool(
        name: String,
        arguments: JSONObject,
    ): JSONObject {
        val result = rpcBlocking(
            method = "tools/call",
            params = JSONObject()
                .put("name", name)
                .put("arguments", arguments),
        )

        val output = JSONObject()
            .put(
                "ok",
                !result.optBoolean("isError", false),
            )
            .put("server_id", serverId)
            .put("tool", name)

        result.optJSONObject("structuredContent")
            ?.let { output.put("structured_content", it) }

        val content = result.optJSONArray("content")
        if (content != null) {
            output.put("content", content)

            val text = buildString {
                for (index in 0 until content.length()) {
                    val part = content.optJSONObject(index) ?: continue
                    if (part.optString("type") == "text") {
                        val value = part.optString("text")
                        if (value.isNotBlank()) {
                            if (isNotEmpty()) append("\n")
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

    private suspend fun rpc(
        method: String,
        params: JSONObject,
    ): JSONObject = withContext(Dispatchers.IO) {
        rpcBlocking(method, params)
    }

    private fun rpcBlocking(
        method: String,
        params: JSONObject,
    ): JSONObject {
        val id = nextRequestId.getAndIncrement()
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)

        val response = execute(payload)
        val error = response.optJSONObject("error")
        if (error != null) {
            error(
                "MCP " + method + " failed: " +
                    error.optString("message")
                        .ifBlank { error.toString() }
            )
        }

        val result = response.opt("result")
        return when (result) {
            is JSONObject -> result
            is JSONArray ->
                JSONObject().put("items", result)
            null, JSONObject.NULL -> JSONObject()
            else -> JSONObject().put("value", result)
        }
    }

    private fun notifyInitialized() {
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("method", "notifications/initialized")
            .put("params", JSONObject())

        val request = requestBuilder()
            .post(
                payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            )
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(
                    "MCP initialization notification failed (" +
                        response.code + ")."
                )
            }
            captureSession(response.header(HEADER_SESSION_ID))
        }
    }

    private fun execute(payload: JSONObject): JSONObject {
        val request = requestBuilder()
            .post(
                payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            )
            .build()

        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error(
                    "MCP request failed (" + response.code + "): " +
                        raw.take(500)
                )
            }

            captureSession(response.header(HEADER_SESSION_ID))
            return decodeResponse(
                raw = raw,
                contentType = response.header("Content-Type").orEmpty(),
            )
        }
    }

    private fun requestBuilder(): Request.Builder =
        Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .apply {
                sessionId?.let {
                    header(HEADER_SESSION_ID, it)
                }
                bearerToken
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        header("Authorization", "Bearer " + it)
                    }
            }

    private fun captureSession(value: String?) {
        value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { sessionId = it }
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
                    .filter { it.startsWith("data:") }
                    .map {
                        it.removePrefix("data:").trim()
                    }
                    .filter {
                        it.isNotBlank() && it != "[DONE]"
                    }
                    .toList()

            candidates.asReversed().forEach { data ->
                runCatching { JSONObject(data) }
                    .getOrNull()
                    ?.let { return it }
            }
            error("MCP server returned no JSON-RPC event.")
        }

        require(raw.isNotBlank()) {
            "MCP server returned an empty response."
        }
        return JSONObject(raw)
    }

    companion object {
        const val PROTOCOL_VERSION = "2025-06-18"
        private const val HEADER_SESSION_ID = "Mcp-Session-Id"
        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8".toMediaType()
    }
}
