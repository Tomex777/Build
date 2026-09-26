package com.night.cortex.server

import android.util.Base64
import com.night.cortex.hosting.HostingFileEntry
import com.night.cortex.hosting.HostingPowerAction
import com.night.cortex.hosting.HostingRuntime
import com.night.cortex.hosting.HostingSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class CortexServerApi(
    baseUrl: String,
    private val token: String,
) {
    private val base = baseUrl.trim().removeSuffix("/")

    fun snapshot(): HostingSnapshot {
        val json = getJson("/api/cortex/host/status")
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

    fun logs(limit: Int = 250): List<String> {
        val json = getJson("/api/cortex/host/logs?limit=${limit.coerceIn(20, 1000)}")
        return json.optJSONArray("lines").strings()
    }

    fun power(action: HostingPowerAction) {
        val value = when (action) {
            HostingPowerAction.START -> "start"
            HostingPowerAction.RESTART -> "restart"
            HostingPowerAction.STOP -> "stop"
        }
        postJson("/api/cortex/host/power", JSONObject().put("action", value))
    }

    fun listFiles(path: String): List<HostingFileEntry> {
        val json = getJson("/api/cortex/host/files?path=${encode(path)}")
        val items = json.optJSONArray("entries") ?: JSONArray()
        return buildList {
            for (i in 0 until items.length()) {
                val row = items.optJSONObject(i) ?: continue
                add(
                    HostingFileEntry(
                        name = row.optString("name"),
                        type = row.optString("type", "file"),
                        sizeBytes = row.optLong("sizeBytes", 0L),
                        modifiedAt = row.optString("modifiedAt").takeIf { it.isNotBlank() && it != "null" },
                    )
                )
            }
        }
    }

    fun readText(path: String): String =
        getJson("/api/cortex/host/files/content?path=${encode(path)}").optString("content")

    fun downloadFile(path: String): ByteArray =
        requestBytes("GET", "/api/cortex/host/files/raw?path=${encode(path)}")

    fun writeText(path: String, content: String) {
        postJson("/api/cortex/host/files/content", JSONObject().put("path", path).put("content", content))
    }

    fun upload(path: String, bytes: ByteArray) {
        postJson(
            "/api/cortex/host/files/binary",
            JSONObject()
                .put("path", path)
                .put("contentBase64", Base64.encodeToString(bytes, Base64.NO_WRAP)),
        )
    }

    fun makeDirectory(path: String) {
        postJson("/api/cortex/host/files/directory", JSONObject().put("path", path))
    }

    fun rename(from: String, to: String) {
        postJson("/api/cortex/host/files/rename", JSONObject().put("from", from).put("to", to))
    }

    fun delete(path: String) {
        postJson("/api/cortex/host/files/delete", JSONObject().put("path", path))
    }

    fun archive(paths: List<String>, destination: String) {
        postJson(
            "/api/cortex/host/files/archive",
            JSONObject().put("paths", JSONArray(paths)).put("destination", destination),
        )
    }

    fun extract(path: String, destination: String) {
        postJson(
            "/api/cortex/host/files/extract",
            JSONObject().put("path", path).put("destination", destination),
        )
    }

    private fun parseStartup(json: JSONObject): StartupInfo =
        StartupInfo(
            runtime = json.optString("runtime", "Node.js"),
            version = json.optString("version"),
            entryFile = json.optString("entryFile", "index.js"),
            startCommand = json.optString("startCommand", "node index.js"),
            projectRoot = json.optString("projectRoot"),
            service = json.optString("service"),
            startupMode = json.optString("startupMode", "unknown"),
            gitRepository = json.optString("gitRepository"),
            gitBranch = json.optString("gitBranch"),
            additionalNodePackages = json.optJSONArray("additionalNodePackages").strings(),
        )

    fun startup(): StartupInfo =
        parseStartup(getJson("/api/cortex/host/startup"))

    fun setStartupEnabled(enabled: Boolean): StartupInfo =
        parseStartup(
            postJson(
                "/api/cortex/host/startup",
                JSONObject().put("enabled", enabled),
            )
        )

    fun activity(limit: Int = 200): List<ActivityEntry> {
        val rows = getJson("/api/cortex/host/activity?limit=${limit.coerceIn(10, 500)}")
            .optJSONArray("entries") ?: JSONArray()
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(
                    ActivityEntry(
                        id = row.optString("id", i.toString()),
                        at = row.optString("at"),
                        action = row.optString("action"),
                        detail = row.optJSONObject("detail")?.toString(2).orEmpty(),
                    )
                )
            }
        }
    }

    fun backups(): List<BackupEntry> {
        val rows = getJson("/api/cortex/host/backups").optJSONArray("entries") ?: JSONArray()
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(
                    BackupEntry(
                        name = row.optString("name"),
                        sizeBytes = row.optLong("sizeBytes", 0L),
                        createdAt = row.optString("createdAt"),
                        privateBackup = row.optBoolean("private", false),
                    )
                )
            }
        }
    }

    fun commandSettings(): List<CommandSetting> {
        val rows = getJson("/api/cortex/host/settings").optJSONArray("entries") ?: JSONArray()
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(
                    CommandSetting(
                        key = row.optString("key"),
                        label = row.optString("label"),
                        description = row.optString("description"),
                        command = row.optString("command"),
                        enabled = row.optBoolean("enabled", false),
                    )
                )
            }
        }
    }

    fun setCommandSetting(key: String, enabled: Boolean): List<CommandSetting> {
        val rows = postJson(
            "/api/cortex/host/settings",
            JSONObject().put("key", key).put("enabled", enabled),
        ).optJSONArray("entries") ?: JSONArray()
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(
                    CommandSetting(
                        key = row.optString("key"),
                        label = row.optString("label"),
                        description = row.optString("description"),
                        command = row.optString("command"),
                        enabled = row.optBoolean("enabled", false),
                    )
                )
            }
        }
    }

    fun runtimeRegistry(): RuntimeRegistry {
        val json = getJson("/api/cortex/mscc/registry")
        val moduleRows = json.optJSONArray("modules") ?: JSONArray()
        val modules = buildList {
            for (i in 0 until moduleRows.length()) {
                val row = moduleRows.optJSONObject(i) ?: continue
                val configRows = row.optJSONArray("configuration") ?: JSONArray()
                val configuration = buildList {
                    for (j in 0 until configRows.length()) {
                        val config = configRows.optJSONObject(j)
                        if (config != null) {
                            add(
                                RuntimeConfigField(
                                    key = config.optString("key"),
                                    label = config.optString("label"),
                                    type = config.optString("type"),
                                    description = config.optString("description"),
                                )
                            )
                        } else {
                            val key = configRows.optString(j)
                            if (key.isNotBlank()) add(RuntimeConfigField(key = key))
                        }
                    }
                }
                add(
                    RuntimeModule(
                        id = row.optString("id"),
                        displayName = row.optString("displayName", row.optString("id")),
                        version = row.optString("version"),
                        status = row.optString("status", "unknown"),
                        enabled = row.optBoolean("enabled", true),
                        commands = row.optJSONArray("commands").strings(),
                        configuration = configuration,
                        loadError = row.optString("loadError"),
                        lastReload = row.optString("lastReload"),
                        moduleDirectory = row.optString("moduleDirectory"),
                        dependencies = row.optJSONArray("dependencies").strings(),
                        permissions = row.optJSONArray("permissions").strings(),
                    )
                )
            }
        }

        val commandRows = json.optJSONArray("commands") ?: JSONArray()
        val commands = buildList {
            for (i in 0 until commandRows.length()) {
                val row = commandRows.optJSONObject(i) ?: continue
                add(
                    RuntimeCommand(
                        name = row.optString("name"),
                        moduleId = row.optString("moduleId"),
                        description = row.optString("description"),
                        aliases = row.optJSONArray("aliases").strings(),
                        enabled = row.optBoolean("enabled", true),
                        permission = row.optString("permission"),
                        usage = row.optString("usage"),
                        error = row.optString("error"),
                    )
                )
            }
        }
        return RuntimeRegistry(
            version = json.optInt("version", 1),
            generatedAt = json.optString("generatedAt"),
            source = json.optString("source", "unknown"),
            modules = modules,
            commands = commands,
        )
    }

    fun reloadModule(id: String) {
        postJson("/api/cortex/mscc/modules/${encodeAccount(id)}/reload", JSONObject())
    }

    fun pairingState(): PairingState {
        val json = getJson("/api/cortex/mscc/pairing")
        val rows = json.optJSONArray("accounts") ?: JSONArray()
        val accounts = buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                add(
                    PairingAccount(
                        id = row.optString("id"),
                        enabled = row.optBoolean("enabled", false),
                        connected = row.optBoolean("connected", false),
                        status = row.optString("status", "offline"),
                        numberMasked = row.optString("numberMasked", "Not configured"),
                        indexCount = row.optInt("indexCount", 0),
                        indexLimit = row.optInt("indexLimit", 5000),
                        pairingMode = row.optString("pairingMode"),
                        pairingCode = row.optString("pairingCode"),
                        pairingQr = row.optString("pairingQr"),
                        pairingError = row.optString("pairingError"),
                        displayName = row.optString("displayName"),
                    )
                )
            }
        }
        val maxAccounts = json.optJSONObject("entitlements")
            ?.optInt("maxAccounts", 0)
            ?.takeIf { it > 0 }
            ?: json.optInt("maxAccounts", 0).takeIf { it > 0 }
        val canAddAccount = json.optJSONObject("capabilities")
            ?.optBoolean("addAccount", false)
            ?: json.optBoolean("canAddAccount", false)
        return PairingState(
            version = json.optString("version"),
            destination = json.optString("destination", "A"),
            accounts = accounts,
            maxAccounts = maxAccounts,
            canAddAccount = canAddAccount,
        )
    }

    fun addAccount(phoneNumber: String, displayName: String) {
        postJson(
            "/api/cortex/mscc/accounts",
            JSONObject()
                .put("phoneNumber", phoneNumber)
                .put("displayName", displayName),
        )
    }

    fun setDestination(id: String) {
        postJson(
            "/api/cortex/mscc/destination",
            JSONObject().put("account", encodeAccount(id)),
        )
    }

    fun reloadCommands(): List<String> =
        postJson("/api/cortex/mscc/commands/reload", JSONObject()).optJSONArray("commands").strings()

    fun pairAccount(id: String, mode: String) {
        postJson(
            "/api/cortex/mscc/accounts/${encodeAccount(id)}/pair",
            JSONObject().put("mode", if (mode == "qr") "qr" else "code"),
        )
    }

    fun reconnectAccount(id: String) {
        postJson("/api/cortex/mscc/accounts/${encodeAccount(id)}/reconnect", JSONObject())
    }

    fun repairAccount(id: String, mode: String) {
        postJson(
            "/api/cortex/mscc/accounts/${encodeAccount(id)}/repair",
            JSONObject().put("mode", if (mode == "qr") "qr" else "code"),
        )
    }

    fun createBackup(privateBackup: Boolean): BackupEntry {
        val row = postJson("/api/cortex/host/backups", JSONObject().put("private", privateBackup))
        return BackupEntry(
            name = row.optString("name"),
            sizeBytes = row.optLong("sizeBytes", 0L),
            createdAt = row.optString("createdAt"),
            privateBackup = row.optBoolean("private", privateBackup),
        )
    }

    fun downloadBackup(name: String): ByteArray =
        requestBytes("GET", "/api/cortex/host/backups/content?name=${encode(name)}")

    fun deleteBackup(name: String) {
        requestJson("DELETE", "/api/cortex/host/backups?name=${encode(name)}", null)
    }

    fun streamLogs(onLine: (String) -> Unit) {
        require(base.startsWith("https://")) { "Cortex Agent URL must use HTTPS" }
        require(token.isNotBlank()) { "Cortex Agent token is missing" }
        var conn: HttpURLConnection? = null
        try {
            conn = URI(base + "/api/cortex/host/logs/stream?initial=0").toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 12_000
            conn.readTimeout = 45_000
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.setRequestProperty("Cache-Control", "no-cache")
            conn.setRequestProperty("Authorization", "Bearer $token")
            val code = conn.responseCode
            if (code !in 200..299) {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw CortexHttpException(code, "Cortex Agent HTTP $code: " + errorText.take(800))
            }
            conn.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    if (raw.startsWith("data:")) {
                        val payload = raw.removePrefix("data:").trim()
                        if (payload.isNotBlank()) {
                            val line = runCatching { JSONObject(payload).optString("line") }.getOrDefault("")
                            if (line.isNotBlank()) onLine(line)
                        }
                    }
                }
            }
        } catch (error: CortexHttpException) {
            throw error
        } catch (error: IOException) {
            throw CortexTransportException(
                "Cortex log stream unavailable: " + (error.message ?: "network error"),
                error,
            )
        } finally {
            conn?.disconnect()
        }
    }

    fun installDependencies(): String =
        postJson("/api/cortex/host/dependencies/install", JSONObject()).optString("message", "Dependencies installed")

    private fun getJson(path: String): JSONObject = requestJson("GET", path, null)
    private fun postJson(path: String, body: JSONObject): JSONObject = requestJson("POST", path, body)

    private fun requestJson(method: String, path: String, body: JSONObject?): JSONObject {
        val bytes = request(method, path, body)
        val text = bytes.toString(Charsets.UTF_8)
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun requestBytes(method: String, path: String): ByteArray = request(method, path, null)

    private fun request(method: String, path: String, body: JSONObject?): ByteArray {
        require(base.startsWith("https://")) { "Cortex Agent URL must use HTTPS" }
        require(token.isNotBlank()) { "Cortex Agent token is missing" }
        try {
            val conn = URI(base + path).toURL().openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 12_000
                conn.readTimeout = 10 * 60_000
                conn.setRequestProperty("Accept", "*/*")
                conn.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val out = ByteArrayOutputStream()
                stream?.use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                    }
                }
                val bytes = out.toByteArray()
                if (code !in 200..299) {
                    throw CortexHttpException(
                        statusCode = code,
                        message = "Cortex Agent HTTP $code: " + bytes.toString(Charsets.UTF_8).take(800),
                    )
                }
                return bytes
            } finally {
                conn.disconnect()
            }
        } catch (error: CortexHttpException) {
            throw error
        } catch (error: IOException) {
            throw CortexTransportException("Cortex Agent is unreachable: " + (error.message ?: "network error"), error)
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")

    private fun encodeAccount(value: String): String {
        val id = value.trim()
        require(ACCOUNT_ID.matches(id)) { "Invalid account ID" }
        return encode(id)
    }

    private companion object {
        val ACCOUNT_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
    }
}

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) add(optString(i))
    }
}

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name).takeUnless { it.isNaN() } else null


class CortexHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message)

class CortexTransportException(
    message: String,
    cause: Throwable,
) : IOException(message, cause)
