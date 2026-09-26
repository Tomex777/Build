package com.night.cortex.server

import android.util.Base64
import com.night.cortex.hosting.HostingFileEntry
import com.night.cortex.hosting.HostingPowerAction
import com.night.cortex.hosting.HostingRuntime
import com.night.cortex.hosting.HostingSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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

    fun startup(): StartupInfo {
        val json = getJson("/api/cortex/host/startup")
        return StartupInfo(
            runtime = json.optString("runtime", "Node.js"),
            version = json.optString("version"),
            entryFile = json.optString("entryFile", "index.js"),
            startCommand = json.optString("startCommand", "node index.js"),
            projectRoot = json.optString("projectRoot"),
            service = json.optString("service"),
            gitRepository = json.optString("gitRepository"),
            gitBranch = json.optString("gitBranch"),
            additionalNodePackages = json.optJSONArray("additionalNodePackages").strings(),
        )
    }

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
                    )
                )
            }
        }
        return PairingState(
            version = json.optString("version"),
            destination = json.optString("destination", "A"),
            accounts = accounts,
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
        val conn = URI(base + path).toURL().openConnection() as HttpURLConnection
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
        check(code in 200..299) {
            "Cortex Agent HTTP $code: " + bytes.toString(Charsets.UTF_8).take(800)
        }
        return bytes
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
