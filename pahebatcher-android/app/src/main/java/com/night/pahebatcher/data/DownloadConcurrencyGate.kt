package com.night.pahebatcher.data

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object DownloadConcurrencyGate {
    private val mutex = Mutex()
    private val active = linkedSetOf<String>()

    suspend fun <T> withPermit(
        context: Context,
        taskId: String,
        block: suspend () -> T,
    ): T {
        var acquired = false
        try {
            while (!acquired) {
                mutex.withLock {
                    val limit = DownloadPreferencesStore(context)
                        .global()
                        .parallelDownloads
                        .coerceIn(1, 8)
                    if (taskId in active || active.size < limit) {
                        active += taskId
                        acquired = true
                    }
                }
                if (!acquired) delay(400)
            }
            return block()
        } finally {
            if (acquired) {
                mutex.withLock {
                    active -= taskId
                }
            }
        }
    }
}
