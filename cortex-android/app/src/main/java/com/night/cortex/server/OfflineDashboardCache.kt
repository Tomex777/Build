package com.night.cortex.server

import android.content.Context
import android.util.AtomicFile
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
            Restored(root.optLong("savedAt"), snapshot, pairing)
        }.getOrNull()
    }

    fun save(url: String, token: String, state: ServerPanelState) {
        if (url.isBlank() || token.isBlank() || state.snapshot == null) return
        val root = JSONObject().apply {
            put("schema", 1)
            put("connection", fingerprint(url, token))
            put("savedAt", System.currentTimeMillis())
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
