package com.tomex777.annie

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Durable host-owned state for imported packages. Script code cannot read or edit this registry. */
internal data class InstalledPackageState(
    val localId: String,
    val packageId: String,
    val displayName: String,
    val version: String,
    val apiVersion: String,
    val entryPoint: String,
    val installedAtMillis: Long,
    val enabled: Boolean,
    val requestedPermissions: Set<String> = emptySet(),
    val grantedPermissions: Set<String> = emptySet(),
)

internal class InstalledPackageRegistry(context: Context) {
    private val atomicFile = AtomicFile(File(context.applicationContext.filesDir, "annie-installed-packages.json"))

    fun get(localId: String): InstalledPackageState? = synchronized(lock) {
        readLocked()[localId]
    }

    fun all(): List<InstalledPackageState> = synchronized(lock) {
        readLocked().values.sortedBy { it.displayName.lowercase() }
    }

    fun recordInstalled(localId: String, manifest: AnniePackageManifest, enabled: Boolean = false) = synchronized(lock) {
        val states = readLocked()
        require(localId !in states) { "Package state already exists for $localId" }
        states[localId] = manifest.toState(localId, System.currentTimeMillis(), enabled)
        writeLocked(states)
    }

    fun ensureRecovered(state: InstalledPackageState) = synchronized(lock) {
        val states = readLocked()
        if (state.localId !in states) {
            states[state.localId] = state
            writeLocked(states)
        }
    }

    /** Recover registry rows from on-disk manifests after an upgrade or interrupted installation. */
    fun reconcile(installed: List<InstalledPackageState>) = synchronized(lock) {
        val current = readLocked()
        val liveIds = installed.mapTo(linkedSetOf()) { it.localId }
        val recovered = linkedMapOf<String, InstalledPackageState>()
        installed.forEach { diskState ->
            val previous = current[diskState.localId]
            recovered[diskState.localId] = diskState.copy(
                installedAtMillis = previous?.installedAtMillis?.takeIf { it > 0L } ?: diskState.installedAtMillis,
                enabled = previous?.enabled ?: diskState.enabled,
                grantedPermissions = previous?.grantedPermissions.orEmpty().intersect(diskState.requestedPermissions),
            )
        }
        if (current != recovered || current.keys != liveIds) writeLocked(recovered)
    }

    fun setEnabled(localId: String, enabled: Boolean) = synchronized(lock) {
        val states = readLocked()
        val state = states[localId] ?: return@synchronized
        if (state.enabled == enabled) return@synchronized
        states[localId] = state.copy(enabled = enabled)
        writeLocked(states)
    }

    fun setGrantedPermissions(
        localId: String,
        permissions: Set<String>,
        declaredPermissions: Set<String>? = null,
    ) = synchronized(lock) {
        val states = readLocked()
        val state = states[localId] ?: return@synchronized
        val requested = declaredPermissions ?: state.requestedPermissions
        val granted = permissions.intersect(requested)
        if (state.requestedPermissions == requested && state.grantedPermissions == granted) return@synchronized
        states[localId] = state.copy(
            requestedPermissions = requested,
            grantedPermissions = granted,
        )
        writeLocked(states)
    }

    fun rename(oldLocalId: String, newLocalId: String) = synchronized(lock) {
        if (oldLocalId == newLocalId) return@synchronized
        val states = readLocked()
        val state = states.remove(oldLocalId) ?: return@synchronized
        require(newLocalId !in states) { "Package state already exists for $newLocalId" }
        states[newLocalId] = state.copy(localId = newLocalId)
        writeLocked(states)
    }

    fun remove(localId: String) = synchronized(lock) {
        val states = readLocked()
        if (states.remove(localId) != null) writeLocked(states)
    }

    private fun readLocked(): LinkedHashMap<String, InstalledPackageState> {
        if (!atomicFile.baseFile.exists()) return linkedMapOf()
        return runCatching {
            val root = JSONObject(atomicFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
            val rows = root.optJSONArray("packages") ?: JSONArray()
            linkedMapOf<String, InstalledPackageState>().apply {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val localId = row.optString("localId")
                    val packageId = row.optString("packageId")
                    if (!localId.matches(Regex("[A-Za-z0-9_-]{1,48}")) || packageId.isBlank()) continue
                    put(localId, InstalledPackageState(
                        localId = localId,
                        packageId = packageId,
                        displayName = row.optString("displayName").ifBlank { packageId },
                        version = row.optString("version").ifBlank { "0.0.0-local" },
                        apiVersion = row.optString("apiVersion").ifBlank { AnniePackageManifest.CURRENT_API_VERSION },
                        entryPoint = row.optString("entryPoint"),
                        installedAtMillis = row.optLong("installedAtMillis"),
                        enabled = row.optBoolean("enabled", false),
                        requestedPermissions = row.optJSONArray("requestedPermissions").toStringSet(),
                        grantedPermissions = row.optJSONArray("grantedPermissions").toStringSet(),
                    ))
                }
            }
        }.getOrElse { linkedMapOf() }
    }

    private fun writeLocked(states: Map<String, InstalledPackageState>) {
        val output = atomicFile.startWrite()
        try {
            val json = JSONObject().put("schemaVersion", 1).put("packages", JSONArray().apply {
                states.values.forEach { state ->
                    put(JSONObject()
                        .put("localId", state.localId)
                        .put("packageId", state.packageId)
                        .put("displayName", state.displayName)
                        .put("version", state.version)
                        .put("apiVersion", state.apiVersion)
                        .put("entryPoint", state.entryPoint)
                        .put("installedAtMillis", state.installedAtMillis)
                        .put("enabled", state.enabled)
                        .put("requestedPermissions", JSONArray(state.requestedPermissions.sorted()))
                        .put("grantedPermissions", JSONArray(state.grantedPermissions.intersect(state.requestedPermissions).sorted())))
                }
            })
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (failure: Throwable) {
            atomicFile.failWrite(output)
            throw failure
        }
    }

    private fun AnniePackageManifest.toState(localId: String, installedAtMillis: Long, enabled: Boolean) =
        InstalledPackageState(localId, packageId, displayName, version, apiVersion, entryPoint, installedAtMillis, enabled, permissions)

    private fun JSONArray?.toStringSet(): Set<String> = this?.let { array ->
        buildSet { for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add) }
    }.orEmpty()

    private companion object {
        val lock = Any()
    }
}
