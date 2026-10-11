package com.night.cortex.server

import android.content.Context
import android.util.AtomicFile
import com.night.cortex.hosting.HostingFileEntry
import com.night.cortex.hosting.HostingRuntime
import com.night.cortex.hosting.HostingSnapshot
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small, device-private, read-only offline snapshot. No credentials, console output,
 * message content, pairing codes or QR images are ever written to this cache.
 *
 * The cache is bound to both the server URL and the currently configured token,
 * preventing stale account data from appearing after switching connections.
 */
internal class OfflineDashboardCache(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "cortex-dashboard-v1.json"))

    internal data class Restored(
        val savedAt: Long,
        val snapshot: HostingSnapshot?,
        val pairing: PairingState?,
        val startup: StartupInfo?,
        val commandSettings: List<CommandSetting>,
        val registry: RuntimeRegistry?,
        val currentPath: String,
        val files: List<HostingFileEntry>,
        val activity: List<ActivityEntry>,
    )

    fun clear() = file.delete()

    fun load(url: String, token: String): Restored? {
        if (url.isBlank() || token.isBlank()) return null
        return runCatching {
            val root = JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
            if (root.optInt("schema") != 1 || root.optString("connection") != fingerprint(url, token)) {
                return null
            }
            val snapshotJson = root.optJSONObject("snapshot")
            val runtime = snapshotJson?.optJSONObject("runtime")
            val snapshot = snapshotJson?.let { json ->
                HostingSnapshot(
                    state = json.optString("state", "unknown"),
                    cpuPercent = json.optionalDouble("cpuPercent"),
                    memoryUsedBytes = json.optionalLong("memoryUsedBytes"),
                    memoryLimitBytes = json.optionalLong("memoryLimitBytes"),
                    diskUsedBytes = json.optionalLong("diskUsedBytes"),
                    diskLimitBytes = json.optionalLong("diskLimitBytes"),
                    uptimeMs = json.optionalLong("uptimeMs"),
                    runtime = runtime?.let {
                        HostingRuntime(
                            runtime = it.optString("runtime"),
                            version = it.optString("version"),
                            entryFile = it.optString("entryFile"),
                            startCommand = it.optString("startCommand"),
                        )
                    },
                )
            }
            val pairingJson = root.optJSONObject("pairing")
            val pairing = pairingJson?.let { json ->
                val rows = json.optJSONArray("accounts") ?: JSONArray()
                val accounts = buildList {
                    for (index in 0 until rows.length()) {
                        val row = rows.optJSONObject(index) ?: continue
                        add(
                            PairingAccount(
                                id = row.optString("id"),
                                enabled = row.optBoolean("enabled"),
                                connected = row.optBoolean("connected"),
                                status = row.optString("status", "offline"),
                                numberMasked = row.optString("numberMasked"),
                                indexCount = row.optInt("indexCount"),
                                indexLimit = row.optInt("indexLimit"),
                                pairingMode = "",
                                pairingCode = "",
                                pairingQr = "",
                                pairingError = "",
                                displayName = row.optString("displayName"),
                                role = row.optString("role", "linked"),
                                profile = row.optString("profile"),
                                profileDisplayName = row.optString("profileDisplayName"),
                                paused = row.optBoolean("paused"),
                                registered = row.optBoolean("registered"),
                                reconnectAttempts = row.optInt("reconnectAttempts"),
                                nextReconnectAt = row.optLong("nextReconnectAt"),
                                lastConnectedAt = row.optLong("lastConnectedAt"),
                                lastDisconnectedAt = row.optLong("lastDisconnectedAt"),
                                lastDisconnectCode = row.optionalInt("lastDisconnectCode"),
                                disconnectReason = row.optString("disconnectReason"),
                            )
                        )
                    }
                }
                val profileArray = json.optJSONArray("profiles") ?: JSONArray()
                val profiles = buildList {
                    for (i in 0 until profileArray.length()) {
                        val p = profileArray.optJSONObject(i) ?: continue
                        add(BotProfileOption(p.optString("id"), p.optString("displayName"), p.optBoolean("universal")))
                    }
                }
                PairingState(
                    version = json.optString("version"),
                    destination = json.optString("destination", "A"),
                    accounts = accounts,
                    maxAccounts = json.optionalInt("maxAccounts"),
                    canAddAccount = json.optBoolean("canAddAccount"),
                    profiles = profiles,
                )
            }
            val startup = root.optJSONObject("startup")?.let { json ->
                StartupInfo(
                    runtime = json.optString("runtime", "Node.js"),
                    version = json.optString("version"),
                    entryFile = json.optString("entryFile", "index.js"),
                    startupMode = json.optString("startupMode", "unknown"),
                )
            }
            val settings = buildList {
                val rows = root.optJSONArray("settings") ?: JSONArray()
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    add(CommandSetting(
                        key = row.optString("key"),
                        label = row.optString("label"),
                        description = row.optString("description"),
                        command = row.optString("command"),
                        enabled = row.optBoolean("enabled"),
                    ))
                }
            }
            val registry = root.optJSONObject("registry")?.let { json ->
                val moduleRows = json.optJSONArray("modules") ?: JSONArray()
                val modules = buildList {
                    for (i in 0 until moduleRows.length()) {
                        val row = moduleRows.optJSONObject(i) ?: continue
                        add(RuntimeModule(
                            id = row.optString("id"),
                            displayName = row.optString("displayName"),
                            version = row.optString("version"),
                            status = row.optString("status"),
                            enabled = row.optBoolean("enabled"),
                            commands = row.optJSONArray("commands").strings(),
                            configuration = emptyList(),
                            loadError = "",
                            lastReload = row.optString("lastReload"),
                            moduleDirectory = "",
                            dependencies = emptyList(),
                            permissions = row.optJSONArray("permissions").strings(),
                        ))
                    }
                }
                val commandRows = json.optJSONArray("commands") ?: JSONArray()
                val commands = buildList {
                    for (i in 0 until commandRows.length()) {
                        val row = commandRows.optJSONObject(i) ?: continue
                        add(RuntimeCommand(
                            name = row.optString("name"),
                            moduleId = row.optString("moduleId"),
                            description = row.optString("description"),
                            aliases = row.optJSONArray("aliases").strings(),
                            enabled = row.optBoolean("enabled"),
                            permission = row.optString("permission"),
                            usage = row.optString("usage"),
                            error = "",
                        ))
                    }
                }
                RuntimeRegistry(
                    version = json.optInt("version"),
                    generatedAt = json.optString("generatedAt"),
                    source = "offline-cache",
                    modules = modules,
                    commands = commands,
                )
            }
            // Store filenames and sanitized audit action types only. Never persist
            // file contents, log lines or activity detail strings.
            val currentPath = root.optString("currentPath", "/").takeIf {
                it.startsWith("/") && !it.contains("..") && it.length < 512
            } ?: "/"
            val files = buildList {
                val rows = root.optJSONArray("files") ?: JSONArray()
                for (index in 0 until minOf(rows.length(), 150)) {
                    val row = rows.optJSONObject(index) ?: continue
                    val name = row.optString("name")
                    if (name.isBlank() || name.length > 255 || name.contains('/')) continue
                    add(HostingFileEntry(
                        name = name,
                        type = row.optString("type"),
                        sizeBytes = row.optLong("sizeBytes"),
                        modifiedAt = row.optString("modifiedAt"),
                    ))
                }
            }
            val activity = buildList {
                val rows = root.optJSONArray("activity") ?: JSONArray()
                for (index in 0 until minOf(rows.length(), 50)) {
                    val row = rows.optJSONObject(index) ?: continue
                    add(ActivityEntry(
                        id = row.optString("id"),
                        at = row.optString("at"),
                        action = row.optString("action"),
                        detail = "", // Never restore arbitrary detail payloads.
                    ))
                }
            }
            Restored(root.optLong("savedAt"), snapshot, pairing, startup, settings, registry, currentPath, files, activity)
        }.getOrNull()
    }

    fun save(url: String, token: String, state: ServerPanelState) {
        if (url.isBlank() || token.isBlank() || state.snapshot == null) return
        val root = JSONObject().apply {
            put("schema", 1)
            put("connection", fingerprint(url, token))
            put("savedAt", System.currentTimeMillis())
            put("currentPath", state.currentPath.take(500))
            put("files", JSONArray().apply {
                state.files.take(150).filter { file ->
                    file.name.isNotBlank() && !file.name.contains('/') &&
                        !file.name.contains("..") && file.name.length <= 255
                }.forEach { item ->
                    put(JSONObject().apply {
                        put("name", item.name)
                        put("type", item.type)
                        put("sizeBytes", item.sizeBytes)
                        put("modifiedAt", item.modifiedAt)
                    })
                }
            })
            put("activity", JSONArray().apply {
                state.activity.takeLast(50).forEach { event ->
                    put(JSONObject().apply {
                        put("id", event.id.take(128))
                        put("at", event.at.take(64))
                        put("action", event.action.take(100))
                        // Deliberately exclude event.detail, which may contain
                        // names, phone numbers or raw API errors.
                    })
                }
            })
            put("snapshot", state.snapshot.let { snap ->
                JSONObject().apply {
                    put("state", snap.state)
                    put("cpuPercent", snap.cpuPercent)
                    put("memoryUsedBytes", snap.memoryUsedBytes)
                    put("memoryLimitBytes", snap.memoryLimitBytes)
                    put("diskUsedBytes", snap.diskUsedBytes)
                    put("diskLimitBytes", snap.diskLimitBytes)
                    put("uptimeMs", snap.uptimeMs)
                    put("runtime", snap.runtime?.let { runtime ->
                        JSONObject().apply {
                            put("runtime", runtime.runtime)
                            put("version", runtime.version)
                            put("entryFile", runtime.entryFile)
                            // Do not persist start commands: they may contain secrets.
                        }
                    })
                }
            })
            put("startup", state.startup?.let { startup ->
                JSONObject().apply {
                    put("runtime", startup.runtime)
                    put("version", startup.version)
                    put("entryFile", startup.entryFile)
                    put("startupMode", startup.startupMode)
                    // Do not cache start commands or environment variables.
                }
            })
            put("settings", JSONArray().apply {
                state.commandSettings.forEach { setting ->
                    put(JSONObject().apply {
                        put("key", setting.key)
                        put("label", setting.label)
                        put("description", setting.description)
                        put("command", setting.command)
                        put("enabled", setting.enabled)
                    })
                }
            })
            put("registry", state.runtimeRegistry?.let { registry ->
                JSONObject().apply {
                    put("version", registry.version)
                    put("generatedAt", registry.generatedAt)
                    put("modules", JSONArray().apply {
                        registry.modules.forEach { module ->
                            put(JSONObject().apply {
                                put("id", module.id)
                                put("displayName", module.displayName)
                                put("version", module.version)
                                put("status", module.status)
                                put("enabled", module.enabled)
                                put("commands", JSONArray(module.commands))
                                put("lastReload", module.lastReload)
                                put("permissions", JSONArray(module.permissions))
                            })
                        }
                    })
                    put("commands", JSONArray().apply {
                        registry.commands.forEach { command ->
                            put(JSONObject().apply {
                                put("name", command.name)
                                put("moduleId", command.moduleId)
                                put("description", command.description)
                                put("aliases", JSONArray(command.aliases))
                                put("enabled", command.enabled)
                                put("permission", command.permission)
                                put("usage", command.usage)
                            })
                        }
                    })
                }
            })
            put("pairing", state.pairing?.let { pairing ->
                JSONObject().apply {
                    put("version", pairing.version)
                    put("destination", pairing.destination)
                    put("maxAccounts", pairing.maxAccounts)
                    put("canAddAccount", pairing.canAddAccount)
                    put("profiles", JSONArray().apply {
                        pairing.profiles.forEach { profile ->
                            put(JSONObject().apply {
                                put("id", profile.id)
                                put("displayName", profile.displayName)
                                put("universal", profile.universal)
                            })
                        }
                    })
                    put("accounts", JSONArray().apply {
                        pairing.accounts.forEach { account ->
                            put(JSONObject().apply {
                                put("id", account.id)
                                put("enabled", account.enabled)
                                put("connected", account.connected)
                                put("status", account.status)
                                put("numberMasked", account.numberMasked)
                                put("indexCount", account.indexCount)
                                put("indexLimit", account.indexLimit)
                                put("displayName", account.displayName)
                                put("role", account.role)
                                put("profile", account.profile)
                                put("profileDisplayName", account.profileDisplayName)
                                put("paused", account.paused)
                                put("registered", account.registered)
                                put("reconnectAttempts", account.reconnectAttempts)
                                put("nextReconnectAt", account.nextReconnectAt)
                                put("lastConnectedAt", account.lastConnectedAt)
                                put("lastDisconnectedAt", account.lastDisconnectedAt)
                                put("lastDisconnectCode", account.lastDisconnectCode)
                                put("disconnectReason", account.disconnectReason)
                            })
                        }
                    })
                }
            })
        }
        val stream = file.startWrite()
        try {
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun JSONArray?.strings(): List<String> {
        val array = this ?: return emptyList()
        return List(array.length()) { array.optString(it) }
    }

    private fun fingerprint(url: String, token: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest((url + "\u0000" + token).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun JSONObject.optionalLong(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun JSONObject.optionalInt(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optionalDouble(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null
}
