package com.tomex777.annie

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONArray
import org.json.JSONObject

internal enum class ScriptTaskState(val wireName: String) {
    QUEUED("queued"),
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    CANCELLED("cancelled");

    companion object {
        fun fromWire(value: String): ScriptTaskState =
            entries.firstOrNull { it.wireName == value.lowercase() } ?: QUEUED
    }
}

internal data class ScriptTaskEntry(
    val id: String,
    val scriptId: String,
    val chatId: String,
    val title: String,
    val action: String,
    val payloadJson: String,
    val state: ScriptTaskState,
    val attempts: Int,
    val lastError: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

internal object ScriptTaskStore {
    private const val PREFS = "annie_script_tasks_v1"
    private const val KEY_ENTRIES = "entries"

    @Synchronized
    fun list(context: Context): List<ScriptTaskEntry> = read(context)

    @Synchronized
    fun get(context: Context, scriptId: String, id: String): ScriptTaskEntry? =
        read(context).firstOrNull { it.scriptId == scriptId && it.id == id }

    @Synchronized
    fun put(context: Context, entry: ScriptTaskEntry) {
        val entries = read(context).filterNot { it.scriptId == entry.scriptId && it.id == entry.id }.toMutableList()
        entries += entry
        write(context, entries)
    }

    @Synchronized
    fun remove(context: Context, scriptId: String, id: String): Boolean {
        val before = read(context)
        val after = before.filterNot { it.scriptId == scriptId && it.id == id }
        if (after.size == before.size) return false
        write(context, after)
        return true
    }

    @Synchronized
    fun removeAllForScript(context: Context, scriptId: String) {
        val before = read(context)
        val doomed = before.filter { it.scriptId == scriptId }
        doomed.forEach { ScriptTaskManager.cancelWork(context, it) }
        write(context, before.filterNot { it.scriptId == scriptId })
    }

    private fun read(context: Context): List<ScriptTaskEntry> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENTRIES, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val scriptId = item.optString("scriptId")
                val chatId = item.optString("chatId")
                val action = item.optString("action")
                if (id.isBlank() || scriptId.isBlank() || chatId.isBlank() || action.isBlank()) continue
                add(
                    ScriptTaskEntry(
                        id = id,
                        scriptId = scriptId,
                        chatId = chatId,
                        title = item.optString("title").ifBlank { id },
                        action = action,
                        payloadJson = item.optString("payloadJson", "{}"),
                        state = ScriptTaskState.fromWire(item.optString("state")),
                        attempts = item.optInt("attempts", 0).coerceAtLeast(0),
                        lastError = item.optString("lastError").takeIf(String::isNotBlank),
                        createdAtMillis = item.optLong("createdAtMillis", System.currentTimeMillis()),
                        updatedAtMillis = item.optLong("updatedAtMillis", System.currentTimeMillis()),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun write(context: Context, entries: List<ScriptTaskEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("scriptId", entry.scriptId)
                    .put("chatId", entry.chatId)
                    .put("title", entry.title)
                    .put("action", entry.action)
                    .put("payloadJson", entry.payloadJson)
                    .put("state", entry.state.wireName)
                    .put("attempts", entry.attempts)
                    .put("lastError", entry.lastError ?: JSONObject.NULL)
                    .put("createdAtMillis", entry.createdAtMillis)
                    .put("updatedAtMillis", entry.updatedAtMillis)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, array.toString())
            .apply()
    }
}

internal object ScriptTaskManager {
    private const val MAX_PAYLOAD_CHARS = 32_000

    fun start(context: Context, scriptId: String, chatId: String, spec: JSONObject): JSONObject {
        val id = spec.optString("id").trim()
        require(id.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}"))) { "Task id must start with a letter" }
        val action = spec.optString("action").trim()
        require(action.isNotBlank() && action.length <= 128) { "Task requires an action" }
        require(chatId.isNotBlank()) { "Task requires an active chat" }
        val payloadValue = spec.opt("payload").takeUnless { it == null || it == JSONObject.NULL } ?: JSONObject()
        val payloadJson = encodeScriptJsonValue(payloadValue)
        require(payloadJson.length <= MAX_PAYLOAD_CHARS) { "Task payload is too large" }
        val now = System.currentTimeMillis()
        val entry = ScriptTaskEntry(
            id = id,
            scriptId = scriptId,
            chatId = chatId,
            title = spec.optString("title").ifBlank { id },
            action = action,
            payloadJson = payloadJson,
            state = ScriptTaskState.QUEUED,
            attempts = 0,
            lastError = null,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        ScriptTaskStore.put(context, entry)
        enqueue(context, entry)
        return publicJson(entry)
    }

    fun list(context: Context, scriptId: String): JSONArray =
        JSONArray().apply {
            ScriptTaskStore.list(context)
                .filter { it.scriptId == scriptId }
                .sortedByDescending { it.updatedAtMillis }
                .forEach { put(publicJson(it)) }
        }

    fun cancel(context: Context, scriptId: String, id: String): Boolean {
        val entry = ScriptTaskStore.get(context, scriptId, id) ?: return false
        cancelWork(context, entry)
        ScriptTaskStore.put(
            context,
            entry.copy(
                state = ScriptTaskState.CANCELLED,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
        return true
    }

    fun retry(context: Context, scriptId: String, id: String): Boolean {
        val entry = ScriptTaskStore.get(context, scriptId, id) ?: return false
        val reset = entry.copy(
            state = ScriptTaskState.QUEUED,
            lastError = null,
            updatedAtMillis = System.currentTimeMillis(),
        )
        ScriptTaskStore.put(context, reset)
        enqueue(context, reset)
        return true
    }

    internal fun enqueue(context: Context, entry: ScriptTaskEntry) {
        val request = OneTimeWorkRequestBuilder<AnnieScriptTaskWorker>()
            .setInputData(
                workDataOf(
                    AnnieScriptTaskWorker.KEY_SCRIPT_ID to entry.scriptId,
                    AnnieScriptTaskWorker.KEY_TASK_ID to entry.id,
                )
            )
            .addTag("annie-script-task")
            .addTag(uniqueWorkName(entry))
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(uniqueWorkName(entry), ExistingWorkPolicy.REPLACE, request)
    }

    internal fun cancelWork(context: Context, entry: ScriptTaskEntry) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(uniqueWorkName(entry))
    }

    private fun uniqueWorkName(entry: ScriptTaskEntry): String =
        "annie-script-task-" + entry.scriptId.replace(Regex("[^A-Za-z0-9_.-]"), "_") +
            "-" + entry.id.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    internal fun publicJson(entry: ScriptTaskEntry): JSONObject =
        JSONObject()
            .put("id", entry.id)
            .put("title", entry.title)
            .put("action", entry.action)
            .put("state", entry.state.wireName)
            .put("attempts", entry.attempts)
            .put("lastError", entry.lastError ?: JSONObject.NULL)
            .put("createdAtMillis", entry.createdAtMillis)
            .put("updatedAtMillis", entry.updatedAtMillis)
}

internal class AnnieScriptTaskWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val scriptId = inputData.getString(KEY_SCRIPT_ID).orEmpty()
        val taskId = inputData.getString(KEY_TASK_ID).orEmpty()
        if (scriptId.isBlank() || taskId.isBlank()) return Result.failure()

        val current = ScriptTaskStore.get(applicationContext, scriptId, taskId) ?: return Result.success()
        if (current.state == ScriptTaskState.CANCELLED) return Result.success()
        val running = current.copy(
            state = ScriptTaskState.RUNNING,
            attempts = current.attempts + 1,
            lastError = null,
            updatedAtMillis = System.currentTimeMillis(),
        )
        ScriptTaskStore.put(applicationContext, running)

        val workspace = ScriptWorkspace(applicationContext)
        return try {
            workspace.reload()
            val dispatch = workspace.executeAction(
                scriptId = scriptId,
                actionId = running.action,
                payloadJson = running.payloadJson,
                chatId = running.chatId,
                messageId = System.nanoTime(),
            )
            if (dispatch == null) {
                val failed = running.copy(
                    state = ScriptTaskState.FAILED,
                    lastError = "Script is disabled or unavailable",
                    updatedAtMillis = System.currentTimeMillis(),
                )
                ScriptTaskStore.put(applicationContext, failed)
                Result.failure()
            } else {
                ChatHistoryStore.appendScriptResult(
                    context = applicationContext,
                    chatId = running.chatId,
                    resultJson = dispatch.resultJson,
                    scriptId = dispatch.scriptId,
                    channel = "task:${running.id}",
                )
                ScriptTaskStore.put(
                    applicationContext,
                    running.copy(
                        state = ScriptTaskState.SUCCEEDED,
                        lastError = null,
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                )
                Result.success()
            }
        } catch (_: Throwable) {
            val message = "Task execution failed"
            if (runAttemptCount < 2) {
                ScriptTaskStore.put(
                    applicationContext,
                    running.copy(
                        state = ScriptTaskState.QUEUED,
                        lastError = message,
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                )
                Result.retry()
            } else {
                ScriptTaskStore.put(
                    applicationContext,
                    running.copy(
                        state = ScriptTaskState.FAILED,
                        lastError = message,
                        updatedAtMillis = System.currentTimeMillis(),
                    ),
                )
                Result.failure()
            }
        } finally {
            workspace.close()
        }
    }

    companion object {
        const val KEY_SCRIPT_ID = "script_id"
        const val KEY_TASK_ID = "task_id"
    }
}
