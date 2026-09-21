package com.example.whatsapp.data.night

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NightSummaryCheckpointResult(
    val created: Boolean,
    val summary: String,
    val messageCount: Int,
    val fromMessageAt: Long? = null,
    val toMessageAt: Long? = null,
)

class NightSummaryCoordinator private constructor(
    private val repository: NightRepository,
    private val aiGateway: NightAiGateway,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun checkpoint(
        chatId: String,
        displayName: String,
    ): NightSummaryCheckpointResult {
        val lock = locks.getOrPut(chatId) { Mutex() }

        return lock.withLock {
            val pending = repository.unsummarizedMessages(chatId)
            if (pending.isEmpty()) {
                return@withLock NightSummaryCheckpointResult(
                    created = false,
                    summary = repository.latestSummary(chatId),
                    messageCount = 0,
                )
            }

            val from = pending.first().createdAt
            val to = pending.last().createdAt
            val summary = aiGateway.summarizePending(
                chatId = chatId,
                displayName = displayName,
                pending = pending,
            ).getOrElse {
                repository.latestSummary(chatId)
            }.trim()

            if (summary.isBlank()) {
                return@withLock NightSummaryCheckpointResult(
                    created = false,
                    summary = "",
                    messageCount = pending.size,
                    fromMessageAt = from,
                    toMessageAt = to,
                )
            }

            repository.commitSummary(
                chatId = chatId,
                summary = summary,
                fromMessageAt = from,
                toMessageAt = to,
            )

            NightSummaryCheckpointResult(
                created = true,
                summary = summary,
                messageCount = pending.size,
                fromMessageAt = from,
                toMessageAt = to,
            )
        }
    }

    companion object {
        @Volatile private var instance: NightSummaryCoordinator? = null

        fun get(context: Context): NightSummaryCoordinator =
            instance ?: synchronized(this) {
                instance ?: NightSummaryCoordinator(
                    repository = NightRepository.get(context.applicationContext),
                    aiGateway = NightAiGateway.get(context.applicationContext),
                ).also { instance = it }
            }
    }
}
