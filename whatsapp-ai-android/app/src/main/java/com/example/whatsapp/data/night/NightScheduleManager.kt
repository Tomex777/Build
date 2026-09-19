package com.example.whatsapp.data.night

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.UUID
import java.util.concurrent.TimeUnit

class NightScheduleManager private constructor(
    private val context: Context,
    private val repository: NightRepository,
) {
    suspend fun create(
        chatId: String,
        prompt: String,
        runAt: Long,
        repeatMinutes: Long? = null,
    ): NightScheduledTaskEntity {
        require(prompt.isNotBlank()) { "Scheduled prompt cannot be empty." }

        val now = System.currentTimeMillis()
        val task = NightScheduledTaskEntity(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            prompt = prompt.trim(),
            runAt = runAt.coerceAtLeast(now),
            repeatMinutes = repeatMinutes,
            state = "scheduled",
            createdAt = now,
            updatedAt = now,
        )
        repository.upsertScheduledTask(task)
        enqueue(task)
        return task
    }

    fun enqueue(task: NightScheduledTaskEntity) {
        val delayMs = (task.runAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val data = Data.Builder()
            .putString("task_id", task.id)
            .build()

        val request = OneTimeWorkRequestBuilder<NightScheduledTaskWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("night_schedule")
            .addTag("night_schedule_" + task.id)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "night_schedule_" + task.id,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    suspend fun cancel(task: NightScheduledTaskEntity) {
        WorkManager.getInstance(context)
            .cancelUniqueWork("night_schedule_" + task.id)
        repository.deleteScheduledTask(task.id)
    }

    companion object {
        @Volatile private var instance: NightScheduleManager? = null

        fun get(context: Context): NightScheduleManager =
            instance ?: synchronized(this) {
                val app = context.applicationContext
                instance ?: NightScheduleManager(
                    context = app,
                    repository = NightRepository.get(app),
                ).also { instance = it }
            }
    }
}
