package com.example.whatsapp.data.night

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class NightScheduledTaskWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString("task_id") ?: return Result.failure()
        val repository = NightRepository.get(applicationContext)
        val scheduler = NightScheduleManager.get(applicationContext)
        val gateway = NightAiGateway.get(applicationContext)

        val task = repository.getScheduledTask(taskId) ?: return Result.success()
        if (task.state != "scheduled") return Result.success()

        return runCatching {
            val profile = repository.ensureProfile()
            repository.ensureChat(task.chatId, "Night")

            repository.appendText(
                chatId = task.chatId,
                role = "user",
                text = task.prompt,
            )

            val reply = gateway.reply(task.chatId, profile.displayName)
                .getOrElse { error ->
                    throw IllegalStateException(error.message ?: "Scheduled AI request failed.")
                }

            repository.appendText(
                chatId = task.chatId,
                role = "assistant",
                text = reply,
            )
            NightNotificationHelper.notifyScheduledResult(
                context = applicationContext,
                task = task,
                reply = reply,
            )

            val now = System.currentTimeMillis()
            val repeat = task.repeatMinutes

            if (repeat != null && repeat > 0L) {
                val next = task.copy(
                    runAt = now + repeat * 60_000L,
                    state = "scheduled",
                    updatedAt = now,
                )
                repository.upsertScheduledTask(next)
                scheduler.enqueue(next)
            } else {
                repository.upsertScheduledTask(
                    task.copy(
                        state = "completed",
                        updatedAt = now,
                    )
                )
            }

            Result.success()
        }.getOrElse {
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                repository.upsertScheduledTask(
                    task.copy(
                        state = "failed",
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                Result.failure()
            }
        }
    }
}
