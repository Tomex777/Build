package app.nami.android

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Independent concurrency budget per extension/source.
 *
 * A busy source never consumes another source's download slots.
 */
internal class PerSourceDownloadGate(
    private val maxPerSource: Int,
) {
    init {
        require(maxPerSource > 0) { "maxPerSource must be positive" }
    }

    private val permits = ConcurrentHashMap<String, Semaphore>()

    suspend fun <T> withPermit(
        sourceId: String,
        block: suspend () -> T,
    ): T {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        val semaphore = permits.computeIfAbsent(sourceId) {
            Semaphore(maxPerSource)
        }
        return semaphore.withPermit {
            block()
        }
    }
}
