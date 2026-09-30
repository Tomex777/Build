package com.tomex.relay.data

import java.nio.charset.StandardCharsets
import java.util.Base64

object RelayStateCodec {
    private const val HEADER = "RELAY_STATE_V1"
    private const val NULL_LONG = "-"

    fun encode(state: RelayPersistedState): String = buildString {
        appendLine(HEADER)
        append("S\t")
        append(state.settings.themeMode.name)
        append('\t')
        append(if (state.settings.showCompleted) "1" else "0")
        appendLine()

        state.tasks.forEach { task ->
            append("T\t")
            append(text(task.id)); append('\t')
            append(text(task.title)); append('\t')
            append(text(task.notes)); append('\t')
            append(if (task.isCompleted) "1" else "0"); append('\t')
            append(task.createdAtEpochMs); append('\t')
            append(task.updatedAtEpochMs); append('\t')
            append(task.completedAtEpochMs ?: NULL_LONG)
            appendLine()
        }

        state.activity.forEach { activity ->
            append("A\t")
            append(text(activity.id)); append('\t')
            append(text(activity.taskId)); append('\t')
            append(activity.kind.name); append('\t')
            append(text(activity.taskTitle)); append('\t')
            append(activity.timestampEpochMs)
            appendLine()
        }
    }

    fun decode(raw: String): RelayPersistedState? {
        val lines = raw.lineSequence().toList()
        if (lines.firstOrNull()?.trim() != HEADER) return null

        var settings = RelaySettings()
        val tasks = mutableListOf<RelayTask>()
        val activity = mutableListOf<RelayActivity>()

        lines.drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val fields = line.split('\t')
            runCatching {
                when (fields.firstOrNull()) {
                    "S" -> if (fields.size >= 3) {
                        settings = RelaySettings(
                            themeMode = ThemeMode.valueOf(fields[1]),
                            showCompleted = fields[2] == "1",
                        )
                    }
                    "T" -> if (fields.size >= 8) {
                        tasks += RelayTask(
                            id = plain(fields[1]),
                            title = plain(fields[2]),
                            notes = plain(fields[3]),
                            isCompleted = fields[4] == "1",
                            createdAtEpochMs = fields[5].toLong(),
                            updatedAtEpochMs = fields[6].toLong(),
                            completedAtEpochMs = fields[7].takeUnless { it == NULL_LONG }?.toLong(),
                        )
                    }
                    "A" -> if (fields.size >= 6) {
                        activity += RelayActivity(
                            id = plain(fields[1]),
                            taskId = plain(fields[2]),
                            kind = ActivityKind.valueOf(fields[3]),
                            taskTitle = plain(fields[4]),
                            timestampEpochMs = fields[5].toLong(),
                        )
                    }
                }
            }
        }

        return RelayPersistedState(
            tasks = tasks,
            activity = activity,
            settings = settings,
        )
    }

    private fun text(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun plain(value: String): String = String(
        Base64.getUrlDecoder().decode(value),
        StandardCharsets.UTF_8,
    )
}
