/*
 * Omni Browser - Debounced, atomic, versioned SessionState persistence.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.tomex777.annie.browser.engine

import android.content.Context
import android.util.Log
import android.util.AtomicFile
import kotlinx.coroutines.*
import org.mozilla.geckoview.GeckoSession
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Maintains durable recoverable state for every eligible (non-incognito) tab.
 *
 * Architecture:
 * ```
 * GeckoSession
 *   ↓ onSessionStateChange()
 *   ↓ latest in-memory SessionState
 *   ↓ debounce / coalesce (500 ms)
 *   ↓ atomic file write (temp → rename)
 *   ↓ durable app-private storage
 * ```
 *
 * Two concepts are maintained:
 * - **latestInMemorySessionState** — what Gecko last reported (may be lost on crash)
 * - **latestDurableSessionState** — what was successfully written to disk (survives process death)
 *
 * Important: incognito tabs are NEVER persisted. Their SessionState lives only in memory.
 *
 * @param baseDir Directory where the serialized SessionState file is written.
 *                Callers should pass [Context.getFilesDir] (app-private, encrypted-at-rest).
 */
class SessionStatePersistence(private val baseDir: File) {

    companion object {
        private const val TAG = "SessionStatePersistence"
        private const val SESSION_STATE_FILE = "browser_session_states.json"
        private const val DEBOUNCE_MS = 500L
        private const val FORCE_CHECKPOINT_TIMEOUT_MS = 3_000L
    }

    // Original upstream code read/modified a shared JSON file on multiple IO
    // threads. Serialize file transactions so concurrent tab checkpoints cannot
    // overwrite each other, lose history, or resurrect a just-closed tab.
    private val diskLock = Any()
    private val forgottenTabs = ConcurrentHashMap.newKeySet<String>()

    /** In-flight debounce jobs per tab. */
    private val pendingJobs = ConcurrentHashMap<String, Job>()

    /** Scope for all persistence work (off main thread). */
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Latest durable state per tab (read from disk or set after successful write). */
    private val durableStateCache = ConcurrentHashMap<String, AnnieSessionState>()

    /**
     * Request a debounced persistence write for the given tab.
     * Multiple rapid calls for the same tab are coalesced into a single disk write.
     */
    fun requestPersist(tabId: String, sessionState: GeckoSession.SessionState, metadata: AnnieSessionState.TabMetadata) {
        if (metadata.isIncognito) {
            // Privacy: never persist incognito state to disk.
            return
        }

        // UUID identities are never reused. Late callbacks from a closed
        // GeckoSession must not resurrect a closed tab or its private history.
        if (forgottenTabs.contains(tabId)) return
        // Cancel any in-flight job for this tab.
        pendingJobs[tabId]?.cancel()

        val job = persistenceScope.launch {
            delay(DEBOUNCE_MS)
            if (!isActive) return@launch

            val bytes = sessionState.toString().toByteArray(Charsets.UTF_8)
            val state = AnnieSessionState(
                tabId = tabId,
                sessionStateBytes = bytes,
                metadata = metadata
            )
            performAtomicWrite(state)
        }
        pendingJobs[tabId] = job
    }

    /**
     * Force an immediate checkpoint of the given tab's state.
     * Blocks (with timeout) until the write completes or fails.
     * Use before critical boundaries: external app launch, onStop, etc.
     */
    fun forceCheckpoint(tabId: String, sessionState: GeckoSession.SessionState, metadata: AnnieSessionState.TabMetadata): Boolean {
        if (metadata.isIncognito) return false

        // Cancel debounce and write immediately.
        pendingJobs[tabId]?.cancel()

        return runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(FORCE_CHECKPOINT_TIMEOUT_MS) {
                val bytes = sessionState.toString().toByteArray(Charsets.UTF_8)
                val state = AnnieSessionState(
                    tabId = tabId,
                    sessionStateBytes = bytes,
                    metadata = metadata
                )
                performAtomicWrite(state)
                true
            } ?: false
        }
    }

    /**
     * Checkpoint multiple tabs at once. Used before process-death-prone transitions.
     */
    fun forceCheckpointBatch(states: Map<String, Pair<GeckoSession.SessionState, AnnieSessionState.TabMetadata>>) {
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()

        persistenceScope.launch {
            val validStates = states.filter { !it.value.second.isIncognito }.map { (tabId, pair) ->
                val (sessionState, metadata) = pair
                AnnieSessionState(
                    tabId = tabId,
                    sessionStateBytes = sessionState.toString().toByteArray(Charsets.UTF_8),
                    metadata = metadata
                )
            }
            if (validStates.isEmpty()) return@launch
            performAtomicWriteBatch(validStates)
        }
    }

    /**
     * Read the latest durable SessionState for a tab.
     * Returns null if no durable state exists or if the state is too stale.
     */
    fun readDurableState(tabId: String, expectedUrl: String? = null): GeckoSession.SessionState? {
        // 1. Check in-memory cache.
        if (forgottenTabs.contains(tabId)) return null
        durableStateCache[tabId]?.let { cached ->
            if (!canRestore(cached, expectedUrl)) return null
            return try {
                GeckoSession.SessionState.fromString(String(cached.sessionStateBytes, Charsets.UTF_8))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to deserialize cached SessionState for $tabId", e)
                null
            }
        }

        // 2. Read from disk.
        return readFromDisk(tabId, expectedUrl)
    }

    /**
     * Read all durable states. Used during process-restart recovery.
     */
    fun readAllDurableStates(): Map<String, GeckoSession.SessionState> {
        val file = File(baseDir, SESSION_STATE_FILE)
        if (!file.exists()) return emptyMap()

        return try {
            val json = synchronized(diskLock) { readStateDocument() }
            val statesObj = json.optJSONObject("states") ?: return emptyMap()
            val result = mutableMapOf<String, GeckoSession.SessionState>()
            statesObj.keys().forEach { tabId ->
                try {
                    val stateJson = statesObj.getJSONObject(tabId)
                    val omniState = AnnieSessionState.fromJson(stateJson)
                    if (!canRestore(omniState, null)) return@forEach
                    val geckoState = GeckoSession.SessionState.fromString(String(omniState.sessionStateBytes, Charsets.UTF_8))
                        ?: return@forEach
                    durableStateCache[tabId] = omniState
                    result[tabId] = geckoState
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping corrupt SessionState for $tabId", e)
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read all durable states", e)
            emptyMap()
        }
    }

    /**
     * Remove durable state for a tab (e.g., when tab is closed).
     */
    fun removeDurableState(tabId: String) {
        // Port fix: a pending debounce must never resurrect a just-closed tab.
        forgottenTabs.add(tabId)
        pendingJobs.remove(tabId)?.cancel()
        durableStateCache.remove(tabId)
        persistenceScope.launch {
            synchronized(diskLock) {
            try {
                val file = File(baseDir, SESSION_STATE_FILE)
                if (!file.exists() && !File(file.path + ".bak").exists()) return@launch
                val json = readStateDocument()
                val statesObj = json.optJSONObject("states") ?: return@launch
                if (statesObj.has(tabId)) {
                    statesObj.remove(tabId)
                    atomicWriteFile(json.toString())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove durable state for $tabId", e)
            }
            }
        }
    }

    /**
     * Clear all durable session states (e.g., on "clear all data").
     */
    fun clearAll() {
        durableStateCache.clear()
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()
        persistenceScope.launch {
            synchronized(diskLock) {
                try {
                    AtomicFile(File(baseDir, SESSION_STATE_FILE)).delete()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear all durable states", e)
                }
            }
        }
    }

    fun shutdown() {
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()
        persistenceScope.cancel()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private implementation
    // ─────────────────────────────────────────────────────────────────────────

    private fun performAtomicWrite(state: AnnieSessionState) {
        synchronized(diskLock) {
        if (forgottenTabs.contains(state.tabId) || state.metadata.isIncognito) return
        try {
            val existingJson = readStateDocument()
            val statesObj = existingJson.optJSONObject("states") ?: JSONObject()
            statesObj.put(state.tabId, state.toJson())
            existingJson.put("states", statesObj)
            existingJson.put("version", AnnieSessionState.CURRENT_SCHEMA_VERSION)
            existingJson.put("lastWritten", System.currentTimeMillis())

            atomicWriteFile(existingJson.toString())
            durableStateCache[state.tabId] = state
            Log.d(TAG, "Persisted SessionState for ${state.tabId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist SessionState for ${state.tabId}", e)
        }
        }
    }

    private fun performAtomicWriteBatch(states: List<AnnieSessionState>) {
        synchronized(diskLock) {
        val validStates = states.filterNot { forgottenTabs.contains(it.tabId) || it.metadata.isIncognito }
        if (validStates.isEmpty()) return
        try {
            val existingJson = readStateDocument()
            val statesObj = existingJson.optJSONObject("states") ?: JSONObject()
            validStates.forEach { state ->
                statesObj.put(state.tabId, state.toJson())
                durableStateCache[state.tabId] = state
            }
            existingJson.put("states", statesObj)
            existingJson.put("version", AnnieSessionState.CURRENT_SCHEMA_VERSION)
            existingJson.put("lastWritten", System.currentTimeMillis())

            atomicWriteFile(existingJson.toString())
            Log.d(TAG, "Batch-persisted ${validStates.size} SessionStates")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to batch-persist SessionStates", e)
        }
        }
    }

    /** A corrupt checkpoint must not wipe unrelated tabs on the next write. */
    private fun readStateDocument(): JSONObject {
        val path = File(baseDir, SESSION_STATE_FILE)
        if (!path.exists() && !File(path.path + ".bak").exists()) return JSONObject()
        return AtomicFile(path).openRead().bufferedReader(Charsets.UTF_8).use {
            JSONObject(it.readText())
        }
    }

    private fun atomicWriteFile(content: String) {
        // Never fall back to truncating the only recovery copy on failure.
        // AtomicFile uses the previous complete state if the process dies mid-write.
        val file = AtomicFile(File(baseDir, SESSION_STATE_FILE))
        val stream = file.startWrite()
        try {
            stream.write(content.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (failure: Throwable) {
            file.failWrite(stream)
            throw failure
        }
    }

    /** No private or unrelated URL may be restored as an existing normal tab. */
    private fun canRestore(state: AnnieSessionState, expectedUrl: String?): Boolean =
        state.schemaVersion == AnnieSessionState.CURRENT_SCHEMA_VERSION &&
        !state.metadata.isIncognito &&
        (expectedUrl == null || state.metadata.url == expectedUrl)

    private fun readFromDisk(tabId: String, expectedUrl: String?): GeckoSession.SessionState? {
        if (forgottenTabs.contains(tabId)) return null
        val file = File(baseDir, SESSION_STATE_FILE)
        if (!file.exists()) return null

        return try {
            val json = synchronized(diskLock) { readStateDocument() }
            val statesObj = json.optJSONObject("states") ?: return null
            if (!statesObj.has(tabId)) return null
            val stateJson = statesObj.getJSONObject(tabId)
            val omniState = AnnieSessionState.fromJson(stateJson)
            if (!canRestore(omniState, expectedUrl)) return null
            durableStateCache[tabId] = omniState
            GeckoSession.SessionState.fromString(String(omniState.sessionStateBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read durable SessionState for $tabId", e)
            null
        }
    }
}
