package com.tomex777.annie

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

internal data class ScriptScheduleEntry(
    val id: String,
    val scriptId: String,
    val chatId: String,
    val action: String,
    val payloadJson: String,
    val every: String?,
    val at: String?,
    val delayMinutes: Long,
    val enabled: Boolean,
    val createdAtMillis: Long,
)

internal object ScriptScheduleStore {
    private const val PREFS = "annie_script_schedules_v1"
    private const val KEY_ENTRIES = "entries"

    @Synchronized
    fun list(context: Context): List<ScriptScheduleEntry> = read(context)

    @Synchronized
    fun get(context: Context, scriptId: String, id: String): ScriptScheduleEntry? =
        read(context).firstOrNull { it.scriptId == scriptId && it.id == id }

    @Synchronized
    fun put(context: Context, entry: ScriptScheduleEntry) {
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

    private fun read(context: Context): List<ScriptScheduleEntry> = runCatching {
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
                    ScriptScheduleEntry(
                        id = id,
                        scriptId = scriptId,
                        chatId = chatId,
                        action = action,
                        payloadJson = item.optString("payloadJson", "{}"),
                        every = item.optString("every").takeIf(String::isNotBlank),
                        at = item.optString("at").takeIf(String::isNotBlank),
                        delayMinutes = item.optLong("delayMinutes", 0L).coerceAtLeast(0L),
                        enabled = item.optBoolean("enabled", true),
                        createdAtMillis = item.optLong("createdAtMillis", System.currentTimeMillis()),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun write(context: Context, entries: List<ScriptScheduleEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("scriptId", entry.scriptId)
                    .put("chatId", entry.chatId)
                    .put("action", entry.action)
                    .put("payloadJson", entry.payloadJson)
                    .put("every", entry.every ?: JSONObject.NULL)
                    .put("at", entry.at ?: JSONObject.NULL)
                    .put("delayMinutes", entry.delayMinutes)
                    .put("enabled", entry.enabled)
                    .put("createdAtMillis", entry.createdAtMillis)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, array.toString())
            .apply()
    }
}

internal object ScriptScheduler {
    private const val MIN_PERIODIC_MINUTES = 15L
    private const val MAX_PAYLOAD_CHARS = 32_000

    fun create(
        context: Context,
        scriptId: String,
        chatId: String,
        spec: JSONObject,
    ): JSONObject {
        val id = spec.optString("id").trim()
        require(id.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}"))) { "Schedule id must start with a letter" }
        val action = spec.optString("action").trim()
        require(action.isNotBlank() && action.length <= 128) { "Schedule requires an action" }
        require(chatId.isNotBlank()) { "Schedule requires an active chat" }

        val every = normalizeEvery(spec.optString("every").trim().takeIf(String::isNotBlank))
        val at = spec.optString("at").trim().takeIf(String::isNotBlank)
        if (at != null) {
            parseClock(at)
            require(every == null || every == "day") { "The at field is supported for one-time or daily schedules" }
        }
        val delayMinutes = spec.optLong("delayMinutes", 0L).coerceAtLeast(0L)
        val periodMinutes = every?.let(::periodMinutes)
        if (periodMinutes != null) {
            require(periodMinutes >= MIN_PERIODIC_MINUTES) { "Recurring WorkManager schedules must be at least 15 minutes apart" }
        }

        val payloadValue = spec.opt("payload").takeUnless { it == null || it == JSONObject.NULL } ?: JSONObject()
        val payloadJson = encodeScriptJsonValue(payloadValue)
        require(payloadJson.length <= MAX_PAYLOAD_CHARS) { "Schedule payload is too large" }

        val entry = ScriptScheduleEntry(
            id = id,
            scriptId = scriptId,
            chatId = chatId,
            action = action,
            payloadJson = payloadJson,
            every = every,
            at = at,
            delayMinutes = delayMinutes,
            enabled = spec.optBoolean("enabled", true),
            createdAtMillis = System.currentTimeMillis(),
        )
        ScriptScheduleStore.put(context, entry)
        if (entry.enabled) enqueue(context, entry) else cancelWork(context, entry)
        return publicJson(entry)
    }

    fun list(context: Context, scriptId: String): JSONArray =
        JSONArray().apply {
            ScriptScheduleStore.list(context)
                .filter { it.scriptId == scriptId }
                .sortedBy { it.id.lowercase() }
                .forEach { put(publicJson(it)) }
        }

    fun cancel(context: Context, scriptId: String, id: String): Boolean {
        val entry = ScriptScheduleStore.get(context, scriptId, id) ?: return false
        cancelWork(context, entry)
        return ScriptScheduleStore.remove(context, scriptId, id)
    }

    fun setEnabled(context: Context, scriptId: String, id: String, enabled: Boolean): Boolean {
        val current = ScriptScheduleStore.get(context, scriptId, id) ?: return false
        if (current.enabled == enabled) return true
        val updated = current.copy(enabled = enabled)
        ScriptScheduleStore.put(context, updated)
        if (enabled) enqueue(context, updated) else cancelWork(context, updated)
        return true
    }

    fun cancelAllForScript(context: Context, scriptId: String) {
        ScriptScheduleStore.list(context)
            .filter { it.scriptId == scriptId }
            .forEach { entry ->
                cancelWork(context, entry)
                ScriptScheduleStore.remove(context, scriptId, entry.id)
            }
    }

    internal fun enqueue(context: Context, entry: ScriptScheduleEntry) {
        if (!entry.enabled) return
        val manager = WorkManager.getInstance(context.applicationContext)
        val input = workDataOf(
            AnnieScriptScheduleWorker.KEY_SCRIPT_ID to entry.scriptId,
            AnnieScriptScheduleWorker.KEY_SCHEDULE_ID to entry.id,
        )
        val uniqueName = uniqueWorkName(entry)
        val period = entry.every?.let(::periodMinutes)

        if (period != null) {
            val initialDelay = initialDelayMillis(entry, period)
            val request = PeriodicWorkRequestBuilder<AnnieScriptScheduleWorker>(period, TimeUnit.MINUTES)
                .setInputData(input)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag("annie-script-schedule")
                .addTag(uniqueName)
                .build()
            manager.enqueueUniquePeriodicWork(
                uniqueName,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                request,
            )
        } else {
            val initialDelay = initialDelayMillis(entry, null)
            val request = OneTimeWorkRequestBuilder<AnnieScriptScheduleWorker>()
                .setInputData(input)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .addTag("annie-script-schedule")
                .addTag(uniqueName)
                .build()
            manager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
        }
    }

    private fun cancelWork(context: Context, entry: ScriptScheduleEntry) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(uniqueWorkName(entry))
    }

    private fun initialDelayMillis(entry: ScriptScheduleEntry, periodMinutes: Long?): Long {
        entry.at?.let { clock ->
            val time = parseClock(clock)
            val now = ZonedDateTime.now()
            var target = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
            if (!target.isAfter(now)) target = target.plusDays(1)
            return Duration.between(now, target).toMillis().coerceAtLeast(0L)
        }
        if (entry.delayMinutes > 0) return TimeUnit.MINUTES.toMillis(entry.delayMinutes)
        return periodMinutes?.let(TimeUnit.MINUTES::toMillis) ?: 0L
    }

    private fun normalizeEvery(raw: String?): String? = when (raw?.lowercase()) {
        null, "", "once" -> null
        "daily", "day" -> "day"
        "hourly", "hour" -> "hour"
        else -> raw.lowercase()
    }

    private fun periodMinutes(every: String): Long = when (every) {
        "day" -> 24L * 60L
        "hour" -> 60L
        else -> {
            val match = Regex("^(\\d+)(m|h|d)$").matchEntire(every)
                ?: error("Unsupported schedule cadence: $every")
            val amount = match.groupValues[1].toLong()
            require(amount > 0) { "Schedule cadence must be positive" }
            when (match.groupValues[2]) {
                "m" -> amount
                "h" -> Math.multiplyExact(amount, 60L)
                else -> Math.multiplyExact(amount, 24L * 60L)
            }
        }
    }

    private fun parseClock(raw: String): LocalTime = runCatching {
        val parts = raw.split(":")
        require(parts.size == 2)
        LocalTime.of(parts[0].toInt(), parts[1].toInt())
    }.getOrElse { error("Schedule time must use HH:mm") }

    private fun uniqueWorkName(entry: ScriptScheduleEntry): String =
        "annie-script-schedule-" + entry.scriptId.replace(Regex("[^A-Za-z0-9_.-]"), "_") +
            "-" + entry.id.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    internal fun publicJson(entry: ScriptScheduleEntry): JSONObject =
        JSONObject()
            .put("id", entry.id)
            .put("action", entry.action)
            .put("payload", runCatching { org.json.JSONTokener(entry.payloadJson).nextValue() }.getOrNull() ?: JSONObject.NULL)
            .put("every", entry.every ?: JSONObject.NULL)
            .put("at", entry.at ?: JSONObject.NULL)
            .put("delayMinutes", entry.delayMinutes)
            .put("enabled", entry.enabled)
            .put("approximate", true)
            .put("scheduler", "workmanager")
}

internal class AnnieScriptScheduleWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val scriptId = inputData.getString(KEY_SCRIPT_ID).orEmpty()
        val scheduleId = inputData.getString(KEY_SCHEDULE_ID).orEmpty()
        if (scriptId.isBlank() || scheduleId.isBlank()) return Result.failure()
        val entry = ScriptScheduleStore.get(applicationContext, scriptId, scheduleId) ?: return Result.success()
        if (!entry.enabled) return Result.success()

        val workspace = ScriptWorkspace(applicationContext)
        return try {
            workspace.reload()
            val dispatch = workspace.executeAction(
                scriptId = scriptId,
                actionId = entry.action,
                payloadJson = entry.payloadJson,
                chatId = entry.chatId,
                messageId = System.nanoTime(),
            )
            if (dispatch != null) {
                ChatHistoryStore.appendScriptResult(
                    context = applicationContext,
                    chatId = entry.chatId,
                    resultJson = dispatch.resultJson,
                    scriptId = dispatch.scriptId,
                    channel = "schedule:${entry.id}",
                )
            }
            if (entry.every == null) {
                ScriptScheduleStore.put(applicationContext, entry.copy(enabled = false))
            }
            Result.success()
        } catch (_: Throwable) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            workspace.close()
        }
    }

    companion object {
        const val KEY_SCRIPT_ID = "script_id"
        const val KEY_SCHEDULE_ID = "schedule_id"
    }
}
