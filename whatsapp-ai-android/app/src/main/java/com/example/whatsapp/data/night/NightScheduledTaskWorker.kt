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

            val runKey = task.id + "_" + task.runAt
            val userMessageId = "scheduled_user_" + runKey
            val assistantMessageId = "scheduled_assistant_" + runKey

            if (repository.getMessage(userMessageId) == null) {
                repository.appendMessage(
                    NightMessageEntity(
                        id = userMessageId,
                        chatId = task.chatId,
                        role = "user",
                        type = "text",
                        text = task.prompt,
                        createdAt = task.runAt,
                    )
                )
            }

            val reply = gateway.reply(task.chatId, profile.displayName)
                .getOrElse { error ->
                    if (error is NightNonRetryableAgentFailure) throw error
                    throw IllegalStateException(error.message ?: "Scheduled AI request failed.", error)
                }

            repository.appendMessage(
                NightMessageEntity(
                    id = assistantMessageId,
                    chatId = task.chatId,
                    role = "assistant",
                    type = "text",
                    text = reply,
                    createdAt = System.currentTimeMillis(),
                )
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
        }.getOrElse { failure ->
            if (failure is NightNonRetryableAgentFailure || runAttemptCount >= 2) {
                repository.upsertScheduledTask(
                    task.copy(
                        state = "failed",
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }
}
