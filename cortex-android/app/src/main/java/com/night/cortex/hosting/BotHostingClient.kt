package com.night.cortex.hosting

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class BotHostingClient(
    private val apiKey: String,
    private val deploymentId: String,
) : HostingControlClient {
    private val base = "https://bot-hosting.net/api/v1/deployments/$deploymentId"

    override fun snapshot(): HostingSnapshot {
        val resources = get("/resources")
        val startup = runCatching { get("/startup") }.getOrNull()
        val cpu = resources.optJSONObject("cpu")
        val memory = resources.optJSONObject("memory")
        val disk = resources.optJSONObject("disk")
        return HostingSnapshot(
            state = resources.optString("state", "unknown"),
            cpuPercent = cpu?.optDouble("usedPercent")?.takeUnless { it.isNaN() },
            memoryUsedBytes = memory?.optLong("usedBytes"),
            memoryLimitBytes = memory?.optLong("limitBytes"),
            diskUsedBytes = disk?.optLong("usedBytes"),
            diskLimitBytes = disk?.optLong("limitBytes"),
            uptimeMs = resources.optLong("uptimeMs").takeIf { it > 0 },
            runtime = startup?.let {
                HostingRuntime(
                    runtime = it.optString("runtime").takeIf(String::isNotBlank),
                    version = it.optString("runtimeVersion").takeIf(String::isNotBlank),
                    entryFile = it.optString("entryFile").takeIf(String::isNotBlank),
                    startCommand = it.optString("startCommand").takeIf(String::isNotBlank),
                )
            },
        )
    }

    override fun logs(limit: Int): List<String> {
        val json = get("/logs?size=${(limit.coerceIn(20, 1000) * 512)}&waitSeconds=0")
        val lines = json.optJSONArray("lines") ?: JSONArray()
        return buildList { for (i in 0 until lines.length()) add(lines.optString(i)) }
    }

    override fun power(action: HostingPowerAction): HostingSnapshot? {
        val name = when (action) {
            HostingPowerAction.START -> "start"
            HostingPowerAction.STOP -> "stop"
            HostingPowerAction.RESTART -> "restart"
        }
        post("/power", JSONObject().put("action", name).put("waitSeconds", if (action == HostingPowerAction.STOP) 0 else 20))
        return runCatching { snapshot() }.getOrNull()
    }

    override fun listFiles(path: String): List<HostingFileEntry> {
        val json = get("/files?path=${encode(path)}")
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

    override fun readText(path: String): String = get("/files/content?path=${encode(path)}").optString("content")

    override fun writeText(path: String, content: String) {
        post("/files/content", JSONObject().put("path", path).put("content", content).put("mode", "overwrite"))
    }

    override fun writeBytes(path: String, content: ByteArray) {
        error("Binary workspace uploads are supported only for Azure")
    }

    override fun installDependencies(): String = error("Package installation is available for Azure deployments")

    private fun get(path: String): JSONObject = request("GET", path)
    private fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)

    private fun request(method: String, path: String, body: JSONObject? = null): JSONObject {
        val conn = URI(base + path).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        check(conn.responseCode in 200..299) { "Bot-Hosting HTTP ${conn.responseCode}: $text" }
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")
}
