package com.night.cortex.hosting

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import android.util.Base64

/**
 * Talks to the small Cortex Agent running on the Azure VM.
 * The agent is intentionally separate from Night so Cortex can still manage files/logs
 * while Night itself is stopped or restarting.
 */
class AzureAgentClient(
    baseUrl: String,
    private val token: String,
) : HostingControlClient {
    private val base = baseUrl.trim().removeSuffix("/")

    override fun snapshot(): HostingSnapshot {
        val json = get("/api/cortex/host/status")
        val runtime = json.optJSONObject("runtime")
        return HostingSnapshot(
            state = json.optString("state", "unknown"),
            cpuPercent = json.optDoubleOrNull("cpuPercent"),
            memoryUsedBytes = json.optLongOrNull("memoryUsedBytes"),
            memoryLimitBytes = json.optLongOrNull("memoryLimitBytes"),
            diskUsedBytes = json.optLongOrNull("diskUsedBytes"),
            diskLimitBytes = json.optLongOrNull("diskLimitBytes"),
            uptimeMs = json.optLongOrNull("uptimeMs"),
            runtime = runtime?.let {
                HostingRuntime(
                    runtime = it.optString("runtime").takeIf(String::isNotBlank),
                    version = it.optString("version").takeIf(String::isNotBlank),
                    entryFile = it.optString("entryFile").takeIf(String::isNotBlank),
                    startCommand = it.optString("startCommand").takeIf(String::isNotBlank),
                )
            },
        )
    }

    override fun logs(limit: Int): List<String> {
        val json = get("/api/cortex/host/logs?limit=${limit.coerceIn(20, 1000)}")
        val lines = json.optJSONArray("lines") ?: JSONArray()
        return buildList { for (i in 0 until lines.length()) add(lines.optString(i)) }
    }

    override fun power(action: HostingPowerAction): HostingSnapshot? {
        val value = when (action) {
            HostingPowerAction.START -> "start"
            HostingPowerAction.STOP -> "stop"
            HostingPowerAction.RESTART -> "restart"
        }
        post("/api/cortex/host/power", JSONObject().put("action", value))
        return runCatching { snapshot() }.getOrNull()
    }

    override fun listFiles(path: String): List<HostingFileEntry> {
        val json = get("/api/cortex/host/files?path=${encode(path)}")
        val entries = json.optJSONArray("entries") ?: JSONArray()
        return buildList {
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                add(
                    HostingFileEntry(
                        name = item.optString("name"),
                        type = item.optString("type", "file"),
                        sizeBytes = item.optLong("sizeBytes", 0),
                        modifiedAt = item.optString("modifiedAt").takeIf { it.isNotBlank() && it != "null" },
                    )
                )
            }
        }
    }

    override fun readText(path: String): String = get("/api/cortex/host/files/content?path=${encode(path)}").optString("content")

    override fun writeText(path: String, content: String) {
        post("/api/cortex/host/files/content", JSONObject().put("path", path).put("content", content))
    }

    override fun writeBytes(path: String, content: ByteArray) {
        post(
            "/api/cortex/host/files/binary",
            JSONObject()
                .put("path", path)
                .put("contentBase64", Base64.encodeToString(content, Base64.NO_WRAP)),
        )
    }

    override fun installDependencies(): String =
        post("/api/cortex/host/dependencies/install", JSONObject()).optString("message", "Dependencies installed")

    private fun get(path: String): JSONObject = request("GET", path)
    private fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        require(base.startsWith("https://")) { "Azure agent URL must use HTTPS" }
        val conn = URI(base + path).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        check(conn.responseCode in 200..299) { "Azure agent HTTP ${conn.responseCode}: $text" }
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")
}

private fun JSONObject.optLongOrNull(name: String): Long? = if (has(name) && !isNull(name)) optLong(name) else null
private fun JSONObject.optDoubleOrNull(name: String): Double? = if (has(name) && !isNull(name)) optDouble(name).takeUnless { it.isNaN() } else null
